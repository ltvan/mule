/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */

package org.mule.functional.classloading.isolation.maven;

import static java.lang.Thread.currentThread;
import static org.apache.commons.lang.StringUtils.isEmpty;
import static org.mule.runtime.core.util.PropertiesUtils.loadProperties;

import org.mule.functional.api.classloading.isolation.MavenMultiModuleArtifactMapping;

import java.io.IOException;
import java.net.URL;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Mule default implementation for getting modules based on artifactIds.
 *
 * @since 4.0
 */
public class DefaultMavenMultiModuleArtifactMapping implements MavenMultiModuleArtifactMapping {

  protected transient Logger logger = LoggerFactory.getLogger(getClass());

  public static final String MAVEN_MODULE_MAPPING_PROPERTIES = "maven-module-mapping.properties";
  private Properties mappings;

  /**
   * Creates a {@link DefaultMavenMultiModuleArtifactMapping} and loads the mappings from
   * {@link DefaultMavenMultiModuleArtifactMapping#MAVEN_MODULE_MAPPING_PROPERTIES}
   *
   * @throws RuntimeException if an error ocurred while reading the resource file
   */
  public DefaultMavenMultiModuleArtifactMapping() {
    try {
      // TODO: MULE-10086 - Add support for defining multiple mappings for artifactIds-folder on ArtifactClassLoaderRunner
      final URL mappingsResource = currentThread().getContextClassLoader().getResource(MAVEN_MODULE_MAPPING_PROPERTIES);
      logger.info("Loading maven module mappings from " + mappingsResource.toString());
      this.mappings = loadProperties(mappingsResource);
    } catch (IOException e) {
      throw new RuntimeException("Error while loading '" + MAVEN_MODULE_MAPPING_PROPERTIES + "' properties");
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public String getFolderName(String artifactId) throws IllegalArgumentException {
    String folder = mappings.getProperty(artifactId);
    if (isEmpty(folder)) {
      logger.error("Contents of maven module mappings are: " + mappings.toString());
      throw new IllegalArgumentException("No folder mapped in multi-module for artifactId: " + artifactId);
    }
    return folder;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public String getArtifactId(String path) throws IllegalArgumentException {
    for (Object propertyName : mappings.keySet()) {
      String relativeFolder = (String) mappings.get(propertyName);
      if (path.endsWith(relativeFolder)) {
        return (String) propertyName;
      }
    }
    throw new IllegalArgumentException("Couldn't find a mapping multi-module folder to get the artifactId for path: " + path);
  }

}
