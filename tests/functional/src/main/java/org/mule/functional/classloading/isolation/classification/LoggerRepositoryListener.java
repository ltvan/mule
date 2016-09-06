/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */

package org.mule.functional.classloading.isolation.classification;

import org.eclipse.aether.AbstractRepositoryListener;
import org.eclipse.aether.RepositoryEvent;
import org.eclipse.aether.RepositoryListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link RepositoryListener} that logs events from repository system.
 *
 * @since 4.0
 */
public class LoggerRepositoryListener extends AbstractRepositoryListener {

  protected final Logger logger = LoggerFactory.getLogger(this.getClass());

  public void artifactDeployed(RepositoryEvent event) {
    logger.info("Deployed {} to {}", event.getArtifact(), event.getRepository());
  }

  public void artifactDeploying(RepositoryEvent event) {
    logger.info("Deploying {} to {}", event.getArtifact(), event.getRepository());
  }

  public void artifactDescriptorInvalid(RepositoryEvent event) {
    logger.info("Invalid artifact descriptor for {}: {}", event.getArtifact(), event.getException().getMessage());
  }

  public void artifactDescriptorMissing(RepositoryEvent event) {
    logger.info("Missing artifact descriptor for {}", event.getArtifact());
  }

  public void artifactInstalled(RepositoryEvent event) {
    logger.info("Installed {} to {}", event.getArtifact(), event.getFile());
  }

  public void artifactInstalling(RepositoryEvent event) {
    logger.info("Installing {} to {}", event.getArtifact(), event.getFile());
  }

  public void artifactResolved(RepositoryEvent event) {
    logger.info("Resolved artifact {} from {}", event.getArtifact(), event.getRepository());
  }

  public void artifactDownloading(RepositoryEvent event) {
    logger.info("Downloading artifact {} from {}", event.getArtifact(), event.getRepository());
  }

  public void artifactDownloaded(RepositoryEvent event) {
    logger.info("Downloaded artifact {} from {}", event.getArtifact(), event.getRepository());
  }

  public void artifactResolving(RepositoryEvent event) {
    logger.info("Resolving artifact {}", event.getArtifact());
  }

  public void metadataDeployed(RepositoryEvent event) {
    logger.info("Deployed {} to {}", event.getMetadata(), event.getRepository());
  }

  public void metadataDeploying(RepositoryEvent event) {
    logger.info("Deploying {} to {}", event.getMetadata(), event.getRepository());
  }

  public void metadataInstalled(RepositoryEvent event) {
    logger.info("Installed {} to {}", event.getMetadata(), event.getFile());
  }

  public void metadataInstalling(RepositoryEvent event) {
    logger.info("Installing {} to {}", event.getMetadata(), event.getFile());
  }

  public void metadataInvalid(RepositoryEvent event) {
    logger.info("Invalid metadata {}", event.getMetadata());
  }

  public void metadataResolved(RepositoryEvent event) {
    logger.info("Resolved metadata {} from {}", event.getMetadata(), event.getRepository());
  }

  public void metadataResolving(RepositoryEvent event) {
    logger.info("Resolving metadata {} from {}", event.getMetadata(), event.getRepository());
  }

}
