/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.runtime.module.deployment.internal;

import static java.util.Collections.emptyList;
import static org.apache.commons.io.FileUtils.copyFile;
import static org.apache.commons.io.IOUtils.copy;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mule.runtime.container.api.MuleFoldersUtil.getAppFolder;
import static org.mule.runtime.container.api.MuleFoldersUtil.getAppPluginsFolder;
import static org.mule.runtime.core.api.config.MuleProperties.MULE_HOME_DIRECTORY_PROPERTY;
import static org.mule.runtime.module.artifact.classloader.DefaultArtifactClassLoaderFilter.EXPORTED_CLASS_PACKAGES_PROPERTY;
import static org.mule.runtime.module.artifact.classloader.DefaultArtifactClassLoaderFilter.NULL_CLASSLOADER_FILTER;

import org.mule.runtime.container.api.MuleFoldersUtil;
import org.mule.runtime.core.util.IOUtils;
import org.mule.runtime.module.deployment.internal.application.DuplicateExportedPackageException;
import org.mule.runtime.module.deployment.internal.builder.ArtifactPluginFileBuilder;
import org.mule.runtime.module.deployment.internal.descriptor.ApplicationDescriptor;
import org.mule.runtime.module.artifact.classloader.ArtifactClassLoaderFilterFactory;
import org.mule.runtime.module.artifact.classloader.DefaultArtifactClassLoaderFilter;
import org.mule.runtime.module.deployment.internal.plugin.ArtifactPluginDescriptor;
import org.mule.runtime.module.deployment.internal.plugin.ArtifactPluginDescriptorFactory;
import org.mule.runtime.module.deployment.internal.plugin.ArtifactPluginDescriptorLoader;
import org.mule.runtime.module.deployment.internal.plugin.ArtifactPluginRepository;
import org.mule.tck.junit4.AbstractMuleTestCase;
import org.mule.tck.junit4.rule.SystemPropertyTemporaryFolder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class ApplicationDescriptorFactoryTestCase extends AbstractMuleTestCase {

  public static final String APP_NAME = "testApp";
  public static final String JAR_FILE_NAME = "test.jar";

  @Rule
  public TemporaryFolder muleHome = new SystemPropertyTemporaryFolder(MULE_HOME_DIRECTORY_PROPERTY);
  private ArtifactPluginRepository applicationPluginRepository;

  @Before
  public void setUp() throws Exception {
    applicationPluginRepository = mock(ArtifactPluginRepository.class);
    when(applicationPluginRepository.getContainerArtifactPluginDescriptors()).thenReturn(emptyList());
  }

  @Test
  public void readsPlugin() throws Exception {
    File pluginDir = getAppPluginsFolder(APP_NAME);
    pluginDir.mkdirs();
    final File pluginFile = new ArtifactPluginFileBuilder("plugin").usingLibrary("lib/echo-test.jar").getArtifactFile();
    copyFile(pluginFile, new File(pluginDir, "plugin1.zip"));
    copyFile(pluginFile, new File(pluginDir, "plugin2.zip"));

    final ArtifactPluginDescriptorFactory pluginDescriptorFactory = mock(ArtifactPluginDescriptorFactory.class);

    final ApplicationDescriptorFactory applicationDescriptorFactory =
        new ApplicationDescriptorFactory(new ArtifactPluginDescriptorLoader(pluginDescriptorFactory),
                                         applicationPluginRepository);
    final ArtifactPluginDescriptor expectedPluginDescriptor1 = mock(ArtifactPluginDescriptor.class);
    when(expectedPluginDescriptor1.getName()).thenReturn("plugin1");
    when(expectedPluginDescriptor1.getClassLoaderFilter())
        .thenReturn(NULL_CLASSLOADER_FILTER);
    final ArtifactPluginDescriptor expectedPluginDescriptor2 = mock(ArtifactPluginDescriptor.class);
    when(expectedPluginDescriptor2.getName()).thenReturn("plugin2");
    when(expectedPluginDescriptor2.getClassLoaderFilter())
        .thenReturn(NULL_CLASSLOADER_FILTER);
    when(pluginDescriptorFactory.create(any())).thenReturn(expectedPluginDescriptor1)
        .thenReturn(expectedPluginDescriptor2);

    ApplicationDescriptor desc = applicationDescriptorFactory.create(getAppFolder(APP_NAME));

    Set<ArtifactPluginDescriptor> plugins = desc.getPlugins();
    assertThat(plugins.size(), equalTo(2));
    assertThat(plugins, hasItem(equalTo(expectedPluginDescriptor1)));
    assertThat(plugins, hasItem(equalTo(expectedPluginDescriptor2)));
  }

  @Test
  public void readsSharedPluginLibs() throws Exception {
    File pluginLibDir = MuleFoldersUtil.getAppSharedPluginLibsFolder(APP_NAME);
    pluginLibDir.mkdirs();

    File sharedLibFile = new File(pluginLibDir, JAR_FILE_NAME);
    copyResourceAs("lib/mule-module-service-echo-default-4.0-SNAPSHOT.jar", sharedLibFile);

    final ApplicationDescriptorFactory applicationDescriptorFactory =
        new ApplicationDescriptorFactory(new ArtifactPluginDescriptorLoader(new ArtifactPluginDescriptorFactory(new ArtifactClassLoaderFilterFactory())),
                                         applicationPluginRepository);
    ApplicationDescriptor desc = applicationDescriptorFactory.create(getAppFolder(APP_NAME));

    assertThat(desc.getSharedRuntimeLibs().length, equalTo(1));
    assertThat(desc.getSharedRuntimeLibs()[0].getFile(), equalTo(sharedLibFile.toString()));
    assertThat(desc.getClassLoaderFilter().getExportedClassPackages(), contains("org.mule.echo"));
    assertThat(desc.getClassLoaderFilter().getExportedResources(),
               containsInAnyOrder("META-INF/MANIFEST.MF",
                                  "META-INF/maven/org.mule.modules/mule-module-service-echo-default/pom.properties",
                                  "META-INF/maven/org.mule.modules/mule-module-service-echo-default/pom.xml"));
  }

  @Test
  public void readsRuntimeLibs() throws Exception {
    File libDir = MuleFoldersUtil.getAppLibFolder(APP_NAME);
    libDir.mkdirs();

    File libFile = new File(libDir, JAR_FILE_NAME);
    copyResourceAs("test-jar-with-resources.jar", libFile);

    final ApplicationDescriptorFactory applicationDescriptorFactory =
        new ApplicationDescriptorFactory(new ArtifactPluginDescriptorLoader(new ArtifactPluginDescriptorFactory(new ArtifactClassLoaderFilterFactory())),
                                         applicationPluginRepository);
    ApplicationDescriptor desc = applicationDescriptorFactory.create(getAppFolder(APP_NAME));

    assertThat(desc.getRuntimeLibs().length, equalTo(1));
    assertThat(desc.getRuntimeLibs()[0].getFile(), equalTo(libFile.toString()));
  }

  @Test
  @Ignore("MULE-9649")
  public void validatesExportedPackageDuplication() throws Exception {
    File pluginDir = getAppPluginsFolder(APP_NAME);
    pluginDir.mkdirs();

    final File pluginFile = createApplicationPluginFile();
    copyFile(pluginFile, new File(pluginDir, "plugin1.zip"));
    copyFile(pluginFile, new File(pluginDir, "plugin2.zip"));

    doPackageValidationTest(applicationPluginRepository);
  }

  @Test
  @Ignore("MULE-9649")
  public void validatesExportedPackageDuplicationAgainstContainerPlugin() throws Exception {
    File pluginDir = getAppPluginsFolder(APP_NAME);
    pluginDir.mkdirs();
    copyFile(createApplicationPluginFile(), new File(pluginDir, "plugin1.zip"));

    final ArtifactPluginRepository applicationPluginRepository = mock(ArtifactPluginRepository.class);
    final ArtifactPluginDescriptor plugin2Descriptor = new ArtifactPluginDescriptor();
    plugin2Descriptor.setName("plugin2");
    final Set<String> exportedPackages = new HashSet<>();
    exportedPackages.add("org.foo");
    exportedPackages.add("org.bar");
    plugin2Descriptor.setClassLoaderFilter(new DefaultArtifactClassLoaderFilter(exportedPackages, Collections.emptySet()));
    when(applicationPluginRepository.getContainerArtifactPluginDescriptors())
        .thenReturn(Collections.singletonList(plugin2Descriptor));

    doPackageValidationTest(applicationPluginRepository);
  }

  private File createApplicationPluginFile() throws Exception {
    return new ArtifactPluginFileBuilder("plugin").configuredWith(EXPORTED_CLASS_PACKAGES_PROPERTY, "org.foo, org.bar")
        .getArtifactFile();
  }

  private void doPackageValidationTest(ArtifactPluginRepository applicationPluginRepository) {
    final ArtifactPluginDescriptorFactory pluginDescriptorFactory =
        new ArtifactPluginDescriptorFactory(new ArtifactClassLoaderFilterFactory());
    final ApplicationDescriptorFactory applicationDescriptorFactory =
        new ApplicationDescriptorFactory(new ArtifactPluginDescriptorLoader(pluginDescriptorFactory),
                                         applicationPluginRepository);

    try {
      applicationDescriptorFactory.create(getAppFolder(APP_NAME));
      fail("Descriptor creation was supposed to fail as the same packages are exported by two plugins");
    } catch (DuplicateExportedPackageException e) {
      assertThat(e.getMessage(), containsString("Package org.foo is exported on artifacts: plugin1, plugin2"));
      assertThat(e.getMessage(), containsString("Package org.bar is exported on artifacts: plugin1, plugin2"));
    }
  }

  private void copyResourceAs(String resourceName, File destination) throws IOException {
    final InputStream sourcePlugin = IOUtils.getResourceAsStream(resourceName, getClass());
    copy(sourcePlugin, new FileOutputStream(destination));
  }
}
