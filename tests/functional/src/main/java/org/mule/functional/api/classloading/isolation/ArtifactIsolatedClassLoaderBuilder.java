/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */

package org.mule.functional.api.classloading.isolation;

import static com.google.common.collect.Lists.newArrayList;
import static org.mule.runtime.core.util.Preconditions.checkNotNull;
import org.mule.functional.classloading.isolation.classloader.IsolatedClassLoaderFactory;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * Builds a class loading model that mimics the class loading model used in a standalone container. Useful for running
 * applications or tests in a lightweight environment with isolation.
 * <p/>
 * The builder could be set with different extension points:
 * <ul>
 * <li>{@link ClassPathUrlProvider}: defines the initial classpath to be classified, it consists in a {@link List} of
 * {@link java.net.URL}'s</li>
 * <li>{@link ClassPathClassifier}: classifies the classpath URLs and builds the {@link List} or {@link java.net.URL}s for each
 * {@link ClassLoader}</li>
 * <p/>
 * The object built by this builder is a {@link ArtifactClassLoaderHolder} that references the
 * {@link org.mule.runtime.module.artifact.classloader.ArtifactClassLoader} for the application, plugins and container.
 *
 * @since 4.0
 */
public class ArtifactIsolatedClassLoaderBuilder {

  private ClassPathClassifier classPathClassifier;
  private ClassPathUrlProvider classPathUrlProvider;

  private IsolatedClassLoaderFactory isolatedClassLoaderFactory = new IsolatedClassLoaderFactory();

  private File rootArtifactClassesFolder;
  private File rootArtifactTestClassesFolder;
  private List<String> providedExclusions = newArrayList();
  private List<String> testExclusions = newArrayList();
  private List<String> testInclusions = newArrayList();
  private List<String> pluginCoordinates = newArrayList();
  private List<Class> exportPluginClasses = newArrayList();
  private List<String> providedInclusions = newArrayList();

  public ArtifactIsolatedClassLoaderBuilder setPluginCoordinates(List<String> pluginCoordinates) {
    this.pluginCoordinates = pluginCoordinates;
    return this;
  }

  /**
   * Sets the {@link ClassPathClassifier} implementation to be used by the builder.
   *
   * @param classPathClassifier {@link ClassPathClassifier} implementation to be used by the builder.
   * @return this
   */
  public ArtifactIsolatedClassLoaderBuilder setClassPathClassifier(final ClassPathClassifier classPathClassifier) {
    this.classPathClassifier = classPathClassifier;
    return this;
  }

  /**
   * Sets the {@link ClassPathUrlProvider} implementation to be used by the builder.
   *
   * @param classPathUrlProvider {@link ClassPathUrlProvider} implementation to be used by the builder.
   * @return this
   */
  public ArtifactIsolatedClassLoaderBuilder setClassPathUrlProvider(final ClassPathUrlProvider classPathUrlProvider) {
    this.classPathUrlProvider = classPathUrlProvider;
    return this;
  }

  /**
   * Sets the {@link File} rootArtifactClassesFolder to be used by the classification process.
   *
   * @param rootArtifactClassesFolder {@link File} rootArtifactClassesFolder to be used by the classification process.
   * @return this
   */
  public ArtifactIsolatedClassLoaderBuilder setRootArtifactClassesFolder(final File rootArtifactClassesFolder) {
    this.rootArtifactClassesFolder = rootArtifactClassesFolder;
    return this;
  }

  /**
   * Sets the {@link File} rootArtifactTestClassesFolder to be used by the classification process.
   *
   * @param rootArtifactTestClassesFolder {@link File} rootArtifactTestClassesFolder to be used by the classification process.
   * @return this
   */
  public ArtifactIsolatedClassLoaderBuilder setRootArtifactTestClassesFolder(final File rootArtifactTestClassesFolder) {
    this.rootArtifactTestClassesFolder = rootArtifactTestClassesFolder;
    return this;
  }

  /**
   * Sets Maven artifacts to be excluded from the {@code provided} scope direct dependencies of the rootArtifact. In format
   * {@code <groupId>:<artifactId>:<extension>:<version>}.
   * <p/>
   * {@link #setPluginCoordinates(List)} Maven artifacts if declared will be considered to be excluded from being added as
   * {@code provided} due to they are going to be added to its class loaders.
   *
   * @param providedExclusions Maven artifacts to be excluded from the {@code provided} scope direct dependencies of the
   *        rootArtifact. In format {@code <groupId>:<artifactId>:[[<extension>]:<version>]}.
   * @return this
   */
  public ArtifactIsolatedClassLoaderBuilder setProvidedExclusions(final List<String> providedExclusions) {
    this.providedExclusions = providedExclusions;
    return this;
  }

  /**
   * Sets Maven artifacts to be explicitly included from the {@code provided} scope direct dependencies of the rootArtifact. In
   * format {@code <groupId>:<artifactId>:[[<extension>]:<version>]}.
   * <p/>
   * This artifacts have to be declared as {@code provided} scope in rootArtifact direct dependencies and no matter if they were
   * excluded or not from {@link #setProvidedExclusions(List)} and {@link #setPluginCoordinates(List)}. Meaning that the same
   * artifact could ended up being added to the container class loader and as plugin.
   *
   * @param providedInclusions
   * @return this
   */
  public ArtifactIsolatedClassLoaderBuilder setProvidedInclusions(List<String> providedInclusions) {
    this.providedInclusions = providedInclusions;
    return this;
  }

  /**
   * Sets the {@link List} of exclusion Maven coordinates to be excluded from test dependencies of rootArtifact. In format
   * {@code <groupId>:<artifactId>:[[<extension>]:<version>]}.
   *
   * @param testExclusions {@link List} of exclusion Maven coordinates to be excluded from test dependencies of rootArtifact. In
   *        format {@code <groupId>:<artifactId>:[[<extension>]:<version>]}.
   * @return this
   */
  public ArtifactIsolatedClassLoaderBuilder setTestExclusions(final List<String> testExclusions) {
    this.testExclusions = testExclusions;
    return this;
  }

  /**
   * Sets the {@link List} of inclusion Maven coordinates to be included from test dependencies of rootArtifact. In format
   * {@code <groupId>:<artifactId>:[[<classifier>]:<version>]}.
   *
   * @param testInclusions {@link List} of inclusion Maven coordinates to be excluded from test dependencies of rootArtifact. In
   *        format {@code <groupId>:<artifactId>:[[<classifier>]:<version>]}.
   * @return this
   */
  public ArtifactIsolatedClassLoaderBuilder setTestInclusions(final List<String> testInclusions) {
    this.testInclusions = testInclusions;
    return this;
  }

  /**
   * Sets the {@link List} of {@link Class}es to be exported by plugins in addition to their APIs, for testing purposes only.
   *
   * @param exportPluginClasses {@link List} of {@link Class}es to be exported by plugins in addition to their APIs, for testing
   *        purposes only.
   * @return this
   */
  public ArtifactIsolatedClassLoaderBuilder setExportPluginClasses(final List<Class> exportPluginClasses) {
    this.exportPluginClasses = exportPluginClasses;
    return this;
  }

  /**
   * Builds the {@link ArtifactClassLoaderHolder} with the
   * {@link org.mule.runtime.module.artifact.classloader.ArtifactClassLoader}s for application, plugins and container.
   *
   * @return a {@link ArtifactClassLoaderHolder} as output of the classification process.
   * @throws {@link IOException} if there was an error while creating the classification context
   * @throws {@link NullPointerException} if any of the required attributes is not set to this builder
   */
  public ArtifactClassLoaderHolder build() {
    checkNotNull(rootArtifactClassesFolder, "rootArtifactClassesFolder has to be set");
    checkNotNull(rootArtifactTestClassesFolder, "rootArtifactTestClassesFolder has to be set");
    checkNotNull(classPathUrlProvider, "classPathUrlProvider has to be set");
    checkNotNull(classPathClassifier, "classPathClassifier has to be set");

    ClassPathClassifierContext context;
    try {
      context =
          new ClassPathClassifierContext(rootArtifactClassesFolder, rootArtifactTestClassesFolder, classPathUrlProvider.getURLs(),
                                         providedExclusions,
                                         providedInclusions,
                                         testExclusions,
                                         testInclusions,
                                         pluginCoordinates, exportPluginClasses);
    } catch (IOException e) {
      throw new RuntimeException("Error while creating the classification context", e);
    }

    ArtifactUrlClassification artifactUrlClassification = classPathClassifier.classify(context);
    return isolatedClassLoaderFactory.createArtifactClassLoader(context.getExtraBootPackages(), artifactUrlClassification);
  }

}
