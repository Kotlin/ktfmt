package org.jetbrains.ktfmt

import org.gradle.api.Project
import org.gradle.api.provider.Provider

internal fun Project.configurationProperty(name: String): Provider<String> {
  return provider { findProperty(name) as? String ?: System.getenv(name) }
}
