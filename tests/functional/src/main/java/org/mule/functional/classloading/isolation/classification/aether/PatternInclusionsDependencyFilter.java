/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */

package org.mule.functional.classloading.isolation.classification.aether;

import static java.util.Arrays.asList;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.DependencyFilter;
import org.eclipse.aether.graph.DependencyNode;

/**
 * TODO
 * [groupId]:[artifactId]:[extension]:[classifier]:[version].
 */
public class PatternInclusionsDependencyFilter implements DependencyFilter {

  private final Set<String> patterns = new HashSet<String>();

  public PatternInclusionsDependencyFilter(final String... coords) {
    this.patterns.addAll(asList(coords));
  }

  public boolean accept(final DependencyNode node, List<DependencyNode> parents) {
    final Dependency dependency = node.getDependency();
    if (dependency == null) {
      return true;
    }
    return accept(dependency.getArtifact());
  }

  protected boolean accept(final Artifact artifact) {
    for (final String pattern : patterns) {
      final boolean matched = accept(artifact, pattern);
      if (matched) {
        return true;
      }
    }
    return false;
  }

  private boolean accept(final Artifact artifact, final String pattern) {
    final String[] tokens =
        new String[] {artifact.getGroupId(), artifact.getArtifactId(), artifact.getExtension(),
            artifact.getClassifier(), artifact.getBaseVersion()};

    final String[] patternTokens = pattern.split(":");

    // fail immediately if pattern tokens outnumber tokens to match
    boolean matched = (patternTokens.length <= tokens.length);

    for (int i = 0; matched && i < patternTokens.length; i++) {
      matched = matches(tokens[i], patternTokens[i]);
    }

    return matched;
  }

  private boolean matches(final String token, final String pattern) {
    boolean matches;

    // support full wildcard and implied wildcard
    if ("*".equals(pattern) || pattern.length() == 0) {
      matches = true;
    }
    // support contains wildcard
    else if (pattern.startsWith("*") && pattern.endsWith("*")) {
      final String contains = pattern.substring(1, pattern.length() - 1);

      matches = (token.contains(contains));
    }
    // support leading wildcard
    else if (pattern.startsWith("*")) {
      final String suffix = pattern.substring(1, pattern.length());

      matches = token.endsWith(suffix);
    }
    // support trailing wildcard
    else if (pattern.endsWith("*")) {
      final String prefix = pattern.substring(0, pattern.length() - 1);

      matches = token.startsWith(prefix);
    }
    // support exact match
    else {
      matches = token.equals(pattern);
    }

    return matches;
  }

  @Override
  public boolean equals(final Object obj) {
    if (this == obj) {
      return true;
    }

    if (obj == null || !getClass().equals(obj.getClass())) {
      return false;
    }

    final PatternInclusionsDependencyFilter that = (PatternInclusionsDependencyFilter) obj;

    return this.patterns.equals(that.patterns);
  }

  @Override
  public int hashCode() {
    int hash = 17;
    hash = hash * 31 + patterns.hashCode();
    return hash;
  }
}