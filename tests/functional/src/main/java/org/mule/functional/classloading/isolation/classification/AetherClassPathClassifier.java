/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */

package org.mule.functional.classloading.isolation.classification;

import static java.util.stream.Collectors.toList;
import static org.eclipse.aether.util.artifact.ArtifactIdUtils.toId;
import static org.eclipse.aether.util.artifact.JavaScopes.COMPILE;
import static org.eclipse.aether.util.artifact.JavaScopes.PROVIDED;
import static org.eclipse.aether.util.artifact.JavaScopes.TEST;
import static org.eclipse.aether.util.filter.DependencyFilterUtils.classpathFilter;
import static org.eclipse.aether.util.filter.DependencyFilterUtils.orFilter;
import org.mule.functional.api.classloading.isolation.ArtifactUrlClassification;
import org.mule.functional.api.classloading.isolation.ClassPathClassifier;
import org.mule.functional.api.classloading.isolation.ClassPathClassifierContext;
import org.mule.functional.api.classloading.isolation.PluginUrlClassification;
import org.mule.functional.classloading.isolation.classification.aether.LocalRepositoryService;
import org.mule.functional.classloading.isolation.classification.aether.PatternInclusionsDependencyFilter;
import org.mule.functional.classloading.isolation.maven.MavenModelFactory;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import java.io.File;
import java.io.FileFilter;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collection;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Optional;

import org.apache.commons.io.filefilter.WildcardFileFilter;
import org.apache.maven.model.Model;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.DependencyFilter;
import org.eclipse.aether.util.filter.PatternExclusionsDependencyFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Creates the {@link ArtifactUrlClassification} based on the Maven dependencies declared by the rootArtifact using Eclipse
 * Aether.
 * <p/>
 * Uses a {@link LocalRepositoryService} to resolve Maven dependencies from the Maven local repository and if a
 * {@link org.mule.functional.api.classloading.isolation.WorkspaceLocationResolver} has been provided it will also resolve first
 * dependency through a {@link org.eclipse.aether.repository.WorkspaceReader}. See
 * {@link org.mule.functional.classloading.isolation.classification.aether.DefaultWorkspaceReader} for more details about
 * workspace dependency resolution.
 * <p/>
 * The classification process classifies the rootArtifact dependencies in three groups: {@code provided}, {@code compile} and
 * {@code test} scopes. It resolves dependencies graph for each group applying filters and exclusions and classifies the list of
 * {@link URL}s that would define each class loader space, container, plugins and application.
 *
 * @since 4.0
 */
public class AetherClassPathClassifier implements ClassPathClassifier {

  public static final String ALL_TESTS_JAR_ARTIFACT_COORDS = "*:*:jar:tests:*";

  public static final String POM = "pom";
  public static final String POM_XML = POM + ".xml";
  public static final String MAVEN_COORDINATES_SEPARATOR = ":";
  public static final String JAR_EXTENSION = "jar";
  public static final String SNAPSHOT_WILCARD_FILE_FILTER = "*-SNAPSHOT*.*";

  protected final Logger logger = LoggerFactory.getLogger(this.getClass());

  /**
   * Classifies {@link URL}s and {@link Dependency}s to define how the container, plugins and application class loaders should be
   * created.
   *
   * @param context {@link ClassPathClassifierContext} to be used during the classification
   * @return {@link ArtifactUrlClassification} as result with the classification
   */
  @Override
  public ArtifactUrlClassification classify(ClassPathClassifierContext context) {
    LocalRepositoryService localRepositoryService =
        new LocalRepositoryService(context.getClassPathURLs(), context.getWorkspaceLocationResolver());

    Artifact rootArtifact = getRootArtifact(context);
    if (logger.isDebugEnabled()) {
      logger.debug("Building class loaders for rootArtifact: {}", rootArtifact);
    }
    List<Dependency> directDependencies = localRepositoryService
        .getDirectDependencies(rootArtifact);

    List<PluginUrlClassification> pluginUrlClassifications =
        buildPluginUrlClassifications(context, rootArtifact, directDependencies, localRepositoryService);

    List<URL> containerUrls =
        buildContainerUrlClassification(context, directDependencies, localRepositoryService, pluginUrlClassifications);
    List<URL> applicationUrls = buildApplicationUrlClassification(context, rootArtifact, directDependencies,
                                                                  localRepositoryService, pluginUrlClassifications);

    return new ArtifactUrlClassification(containerUrls, pluginUrlClassifications,
                                         applicationUrls);
  }

  /**
   * Gets the Maven artifact for located at {@link ClassPathClassifierContext#getRootArtifactClassesFolder()}
   *
   * @param context {@link ClassPathClassifierContext} for classification process
   * @return {@link Artifact} that represents the rootArtifact
   */
  private Artifact getRootArtifact(ClassPathClassifierContext context) {
    File pomFile = new File(context.getRootArtifactClassesFolder().getParentFile().getParentFile(), POM_XML);
    if (logger.isDebugEnabled()) {
      logger.debug("Reading rootArtifact from pom file: {}", pomFile);
    }
    Model model = MavenModelFactory.createMavenProject(pomFile);

    return new DefaultArtifact(model.getGroupId() != null ? model.getGroupId() : model.getParent().getGroupId(),
                               model.getArtifactId(), model.getPackaging(),
                               model.getVersion() != null ? model.getVersion() : model.getParent().getVersion());
  }

  /**
   * Container classification is being done by resolving the {@value org.eclipse.aether.util.artifact.JavaScopes#PROVIDED} direct
   * dependencies of the rootArtifact. Is uses the exclusions defined in
   * {@link ClassPathClassifierContext#getProvidedExclusions()} to filter the dependency graph plus
   * {@value #ALL_TESTS_JAR_ARTIFACT_COORDS} and {@link ClassPathClassifierContext#getExcludedArtifacts()}.
   *
   * @param context {@link ClassPathClassifierContext} with settings for the classification process
   * @param localRepositoryService {@link LocalRepositoryService} to resolve Maven dependencies
   * @param pluginUrlClassifications {@link PluginUrlClassification}s to check if rootArtifact was classified as plugin
   * @return {@link List} of {@link URL}s for the container class loader
   */
  private List<URL> buildContainerUrlClassification(ClassPathClassifierContext context,
                                                    List<Dependency> directDependencies,
                                                    LocalRepositoryService localRepositoryService,
                                                    List<PluginUrlClassification> pluginUrlClassifications) {
    directDependencies = directDependencies.stream()
        .filter(directDep -> directDep.getScope().equals(PROVIDED))
        .map(depToTransform -> depToTransform.setScope(COMPILE)).collect(toList());

    if (logger.isDebugEnabled()) {
      logger.debug("Selected direct dependencies to be used for resolving container dependency graph: {}", directDependencies);
    }

    List<String> excludedFilterPattern = Lists.newArrayList(context.getProvidedExclusions());
    excludedFilterPattern.add(ALL_TESTS_JAR_ARTIFACT_COORDS);
    excludedFilterPattern.addAll(context.getExcludedArtifacts());
    if (!pluginUrlClassifications.isEmpty()) {
      excludedFilterPattern.addAll(pluginUrlClassifications.stream()
          .map(pluginUrlClassification -> pluginUrlClassification.getName()).collect(toList()));
    }

    if (logger.isDebugEnabled()) {
      logger.debug("Resolving dependencies for container using exclusion filter patterns: {}", excludedFilterPattern);
    }
    if (!context.getProvidedInclusions().isEmpty()) {
      if (logger.isDebugEnabled()) {
        logger.debug("Resolving dependencies for container using inclusion filter patterns: {}", context.getProvidedInclusions());
      }
    }

    List<URL> containerUrls = toUrl(localRepositoryService
        .resolveDependencies(null, directDependencies,
                             orFilter(new org.eclipse.aether.util.filter.PatternInclusionsDependencyFilter(context
                                 .getProvidedInclusions()), new PatternExclusionsDependencyFilter(excludedFilterPattern))));

    containerUrls = containerUrls.stream().filter(url -> !url.getFile().endsWith(POM_XML)).collect(toList());

    resolveSnapshotVersionsFromClasspath(containerUrls, context.getClassPathURLs());

    return containerUrls;
  }

  /**
   * Plugin classifications are being done by resolving the dependencies for each plugin coordinates defined at
   * {@link ClassPathClassifierContext#getPluginCoordinates()}. These artifacts should be defined as
   * {@value org.eclipse.aether.util.artifact.JavaScopes#PROVIDED} in the rootArtifact and if these coordinates don't have a
   * version the rootArtifact version would be used to look for the Maven plugin artifact.
   * <p/>
   * While resolving the dependencies for the plugin artifact, only {@value org.eclipse.aether.util.artifact.JavaScopes#COMPILE}
   * dependencies will be selected. {@link ClassPathClassifierContext#getExcludedArtifacts()} will be exluded too.
   * <p/>
   * The resulting {@link PluginUrlClassification} for each plugin will have as name the Maven artifact id coordinates:
   * {@code <groupId>:<artifactId>:<extension>[:<classifier>]:<version>}.
   *
   * @param context {@link ClassPathClassifierContext} with settings for the classification process
   * @param rootArtifact {@link Artifact} that defines the current artifact that requested to build this class loaders
   * @param directDependencies {@link List} of {@link Dependency} with direct dependencies for the rootArtifact
   * @param localRepositoryService {@link LocalRepositoryService} to resolve Maven dependencies
   * @return {@link List} of {@link PluginUrlClassification}s for plugins class loaders
   */
  private List<PluginUrlClassification> buildPluginUrlClassifications(ClassPathClassifierContext context,
                                                                      Artifact rootArtifact,
                                                                      List<Dependency> directDependencies,
                                                                      LocalRepositoryService localRepositoryService) {
    List<PluginUrlClassification> pluginUrlClassifications = Lists.newArrayList();
    if (context.getPluginCoordinates() != null) {
      for (String pluginCoords : context.getPluginCoordinates()) {
        if (logger.isDebugEnabled()) {
          logger.debug("Building plugin classification for coordinates: '{}'", pluginCoords);
        }

        final String[] pluginSplitCoords = pluginCoords.split(MAVEN_COORDINATES_SEPARATOR);
        String pluginGroupId = pluginSplitCoords[0];
        String pluginArtifactId = pluginSplitCoords[1];
        String pluginVersion;

        if (rootArtifact.getGroupId().equals(pluginGroupId) && rootArtifact.getArtifactId().equals(pluginArtifactId)) {
          if (logger.isDebugEnabled()) {
            logger.debug("'{}' declared as plugin, resolving version from pom file", rootArtifact);
          }
          pluginVersion = rootArtifact.getVersion();
        } else {
          if (logger.isDebugEnabled()) {
            logger.debug("Resolving version for '{}' from direct dependencies", pluginCoords);
          }
          Optional<Dependency> pluginDependencyOp = directDependencies.isEmpty() ? Optional.<Dependency>empty()
              : directDependencies.stream().filter(dependency -> dependency.getArtifact().getGroupId().equals(pluginGroupId)
                  && dependency.getArtifact().getArtifactId().equals(pluginArtifactId)).findFirst();
          if (!pluginDependencyOp.isPresent() || !pluginDependencyOp.get().getScope().endsWith(PROVIDED)) {
            throw new IllegalStateException("Plugin '" + pluginCoords
                + "' in order to be resolved has to be declared as provided direct dependency of your Maven project");
          }
          Dependency pluginDependency = pluginDependencyOp.get();
          pluginVersion = pluginDependency.getArtifact().getVersion();
        }

        final DefaultArtifact artifact = new DefaultArtifact(pluginGroupId, pluginArtifactId, JAR_EXTENSION, pluginVersion);
        if (logger.isDebugEnabled()) {
          logger.debug("'{}' plugin coordinates resolved to: '{}'", pluginCoords, artifact);
        }
        List<URL> urls = toUrl(localRepositoryService
            .resolveDependencies(
                                 new Dependency(artifact,
                                                COMPILE),
                                 orFilter(classpathFilter(COMPILE),
                                          new PatternExclusionsDependencyFilter(context.getExcludedArtifacts()))));

        // TODO (gfernandes): How could I check if exported classes belong to this plugin?
        pluginUrlClassifications
            .add(new PluginUrlClassification(toId(artifact), urls, Lists.newArrayList(context.getExportPluginClasses())));
      }
    }
    return pluginUrlClassifications;
  }

  /**
   * Application classification is being done by resolving the direct dependencies with scope
   * {@value org.eclipse.aether.util.artifact.JavaScopes#TEST} for the rootArtifact. Due to Eclipse Aether resolution excludes by
   * {@value org.eclipse.aether.util.artifact.JavaScopes#TEST} dependencies an imaginary pom will be created with these
   * dependencies as {@value org.eclipse.aether.util.artifact.JavaScopes#COMPILE} so the dependency graph can be resolved (with
   * the same results as it will be obtained from Maven).
   * <p/>
   * If the rootArtifact was classified as plugin its {@value org.eclipse.aether.util.artifact.JavaScopes#COMPILE} will be changed
   * to {@value org.eclipse.aether.util.artifact.JavaScopes#PROVIDED} in order to exclude them from the dependency graph.
   * <p/>
   * Filtering logic includes the following pattern to include all the tests-jar dependencies:
   * {@value #ALL_TESTS_JAR_ARTIFACT_COORDS}. It also excludes {@link ClassPathClassifierContext#getExcludedArtifacts()},
   * {@link ClassPathClassifierContext#getTestExclusions()} ()}.
   * <p/>
   * If the application artifact has not been classified as plugin its {@code target/classes/} folder will be included in this
   * classification.
   *
   * @param context {@link ClassPathClassifierContext} with settings for the classification process
   * @param rootArtifact {@link Artifact} that defines the current artifact that requested to build this class loaders
   * @param directDependencies {@link List} of {@link Dependency} with direct dependencies for the rootArtifact
   * @param localRepositoryService {@link LocalRepositoryService} to resolve Maven dependencies
   * @param pluginUrlClassifications {@link PluginUrlClassification}s to check if rootArtifact was classified as plugin
   * @return {@link URL}s for application class loader
   */
  private List<URL> buildApplicationUrlClassification(ClassPathClassifierContext context,
                                                      Artifact rootArtifact,
                                                      List<Dependency> directDependencies,
                                                      LocalRepositoryService localRepositoryService,
                                                      List<PluginUrlClassification> pluginUrlClassifications) {
    if (logger.isDebugEnabled()) {
      logger.debug("Building application classification");
    }
    List<File> applicationFiles = Lists.newArrayList(context.getRootArtifactTestClassesFolder());

    if (logger.isDebugEnabled()) {
      logger.debug("Setting filter for dependency graph to include: '{}'", ALL_TESTS_JAR_ARTIFACT_COORDS);
    }
    DependencyFilter dependencyFilter = new PatternInclusionsDependencyFilter(
                                                                              ALL_TESTS_JAR_ARTIFACT_COORDS);

    boolean isRootArtifactPlugin = !pluginUrlClassifications.isEmpty()
        && pluginUrlClassifications.stream().filter(p -> {
          Artifact plugin = new DefaultArtifact(p.getName());
          return plugin.getGroupId().equals(rootArtifact.getGroupId())
              && plugin.getArtifactId().equals(rootArtifact.getArtifactId());
        }).findFirst().isPresent();

    List<String> exclusionsPatterns = Lists.newArrayList();

    if (!isRootArtifactPlugin && context.getRootArtifactClassesFolder().exists()) {
      if (logger.isDebugEnabled()) {
        logger.debug("RootArtifact is not a plugin so '{}' is added to application classification",
                     context.getRootArtifactClassesFolder());
      }
      applicationFiles.add(context.getRootArtifactClassesFolder());
    } else {
      if (logger.isDebugEnabled()) {
        logger.debug("RootArtifact is a plugin or it doesn't have a target/classes folder (it is the case of a test artifact)");
      }
      exclusionsPatterns.add(rootArtifact.getGroupId() + MAVEN_COORDINATES_SEPARATOR
          + rootArtifact.getArtifactId() + MAVEN_COORDINATES_SEPARATOR +
          JAR_EXTENSION + MAVEN_COORDINATES_SEPARATOR + rootArtifact.getVersion());
    }

    directDependencies = directDependencies.stream()
        .map(toTransform -> {
          if (toTransform.getScope().equals(TEST)) {
            return new Dependency(toTransform.getArtifact(), COMPILE);
          }
          if (isRootArtifactPlugin && toTransform.getScope().equals(COMPILE)) {
            return new Dependency(toTransform.getArtifact(), PROVIDED);
          }
          return toTransform;
        }).collect(toList());

    if (logger.isDebugEnabled()) {
      logger.debug("OR exclude: {}", context.getExcludedArtifacts());
    }
    exclusionsPatterns.addAll(context.getExcludedArtifacts());

    if (!context.getTestExclusions().isEmpty()) {
      if (logger.isDebugEnabled()) {
        logger.debug("OR exclude application specific artifacts: {}", context.getTestExclusions());
      }
      exclusionsPatterns.addAll(context.getTestExclusions());
    }

    if (logger.isDebugEnabled()) {
      logger.debug("Resolving dependency graph for '{}' scope direct dependencies: {}", TEST, directDependencies);
    }
    applicationFiles
        .addAll(localRepositoryService.resolveDependencies(new Dependency(rootArtifact, TEST), directDependencies,
                                                           orFilter(dependencyFilter,
                                                                    new PatternExclusionsDependencyFilter(exclusionsPatterns))));

    return toUrl(applicationFiles);
  }

  /**
   * Converts the {@link List} of {@link File}s to {@link URL}s
   *
   * @param files {@link File} to get {@link URL}s
   * @return {@link List} of {@link URL}s
   */
  private List<URL> toUrl(Collection<File> files) {
    List<URL> urls = Lists.newArrayList();
    for (File file : files) {
      try {
        urls.add(file.toURI().toURL());
      } catch (MalformedURLException e) {
        throw new IllegalArgumentException("Couldn't get URL", e);
      }
    }
    return urls;
  }


  /**
   * As Eclipse Aether resolves SNAPSHOT versions without pointing to a timestamped version (actually they are normalized, in
   * Maven language) and IDEs could have built the class path with timestamped SNAPSHOT versions this method will check for those
   * cases and replace the SNAPSHOT normalized {@link URL} entry in the resolvedURLs parameter with the timestamped version from
   * the classpathURLs entry.
   *
   * @param resolvedURLs {@link URL}s resolved from the dependency graph
   * @param classpathURLs {@link URL}s already provided in class path by IDE or Maven
   */
  private void resolveSnapshotVersionsFromClasspath(List<URL> resolvedURLs, List<URL> classpathURLs) {
    if (logger.isDebugEnabled()) {
      logger.debug("Checking if resolved SNAPSHOT URLs had a timestamped version already included in class path URLs");
    }
    Map<File, List<URL>> classpathFolders = groupArtifactURLsByFolder(classpathURLs);

    FileFilter snapshotFileFilter = new WildcardFileFilter(SNAPSHOT_WILCARD_FILE_FILTER);
    ListIterator<URL> listIterator = resolvedURLs.listIterator();
    while (listIterator.hasNext()) {
      final URL urlResolved = listIterator.next();
      File artifactResolvedFile = new File(urlResolved.getFile());
      if (snapshotFileFilter.accept(artifactResolvedFile)) {
        File artifactResolvedFileParentFile = artifactResolvedFile.getParentFile();
        if (logger.isDebugEnabled()) {
          logger.debug("Checking if resolved SNAPSHOT artifact: '{}' has a timestamped version already in class path",
                       artifactResolvedFile);
        }
        URL urlFromClassPath = null;
        if (classpathFolders.containsKey(artifactResolvedFileParentFile)) {
          urlFromClassPath = findArtifactUrlFromClassPath(classpathFolders, artifactResolvedFile);
        }

        if (urlFromClassPath != null) {
          if (logger.isDebugEnabled()) {
            logger.debug("Replacing resolved URL '{}' from class path URL '{}'", urlResolved, urlFromClassPath);
          }
          listIterator.set(urlFromClassPath);
        } else {
          logger.warn(
                      "'{}' resolved SNAPSHOT version couldn't be matched to a class path URL",
                      artifactResolvedFile);
        }
      }
    }
  }

  /**
   * Creates a {@link Map} that has as key the folder that holds the artifact and value a {@link List} of {@link URL}s. For
   * instance, an artifact in class path that only has its jar packaged output:
   * 
   * <pre>
   *   key=/Users/jdoe/.m2/repository/org/mule/extensions/mule-extensions-api-xml-dsl/1.0.0-SNAPSHOT/
   *   value=[file:/Users/jdoe/.m2/repository/org/mule/extensions/mule-extensions-api-xml-dsl/1.0.0-SNAPSHOT/mule-extensions-api-xml-dsl-1.0.0-20160823.170911-32.jar]
   * </pre>
   * <p/>
   * Another case is for those artifacts that have both packaged versions, the jar and the -tests.jar. For instance:
   * 
   * <pre>
   *   key=/Users/jdoe/Development/mule/extensions/file/target
   *   value=[file:/Users/jdoe/.m2/repository/org/mule/modules/mule-module-file-extension-common/4.0-SNAPSHOT/mule-module-file-extension-common-4.0-SNAPSHOT.jar,
   *          file:/Users/jdoe/.m2/repository/org/mule/modules/mule-module-file-extension-common/4.0-SNAPSHOT/mule-module-file-extension-common-4.0-SNAPSHOT-tests.jar]
   * </pre>
   *
   * @param classpathURLs the class path {@link List} of {@link URL}s to be grouped by folder
   * @return {@link Map} that has as key the folder that holds the artifact and value a {@link List} of {@link URL}s.
   */
  private Map<File, List<URL>> groupArtifactURLsByFolder(List<URL> classpathURLs) {
    Map<File, List<URL>> classpathFolders = Maps.newHashMap();
    classpathURLs.forEach(url -> {
      File folder = new File(url.getFile()).getParentFile();
      if (classpathFolders.containsKey(folder)) {
        classpathFolders.get(folder).add(url);
      } else {
        classpathFolders.put(folder, Lists.newArrayList(url));
      }
    });
    return classpathFolders;
  }

  /**
   * Finds the corresponding {@link URL} in class path grouped by folder {@link Map} for the given artifact {@link File}.
   *
   * @param classpathFolders a {@link Map} that has as entry the folder of the artifacts from class path and value a {@link List}
   *        with the artifacts (jar, tests.jar, etc).
   * @param artifactResolvedFile the {@link Artifact} resolved from the Maven dependencies and resolved as SNAPSHOT
   * @return {@link URL} for the artifact found in the class path or {@code null}
   */
  private URL findArtifactUrlFromClassPath(Map<File, List<URL>> classpathFolders, File artifactResolvedFile) {
    List<URL> urls = classpathFolders.get(artifactResolvedFile.getParentFile());
    if (logger.isDebugEnabled()) {
      logger.debug("URLs found for '{}' in class path are: {}", artifactResolvedFile, urls);
    }
    if (urls.size() == 1) {
      return urls.get(0);
    } else {
      for (URL url : urls) {
        if (artifactResolvedFile.getName().endsWith("-tests.jar")) {
          if (url.getFile().endsWith("-tests.jar")) {
            return url;
          }
        } else {
          if (!url.getFile().endsWith("-tests.jar")) {
            return url;
          }
        }
      }
    }
    return null;
  }

}
