package org.jetbrains.ktfmt

import org.gradle.api.Project
import org.gradle.api.provider.Provider

internal val Project.currentOs: Os
  get() =
      providers.systemProperty("os.name").get().let { osName ->
        when {
          osName.lowercase().contains("windows") -> Os.WINDOWS
          osName.lowercase().contains("mac") -> Os.MACOS
          else -> Os.LINUX
        }
      }

internal val Project.currentArch: Arch
  get() =
      when (val arch = providers.systemProperty("os.arch").get()) {
        "aarch64",
        "arm64" -> Arch.AARCH64
        "x86_64",
        "amd64" -> Arch.X64
        else -> error("Unsupported native-image host architecture: $arch")
      }

internal enum class Os(val osName: String) {
  WINDOWS("windows"),
  MACOS("macos"),
  LINUX("linux"),
}

internal enum class Arch(val archName: String) {
  AARCH64("aarch64"),
  X64("x86_64"),
}

internal fun Project.configurationProperty(name: String): Provider<String> {
  return provider { findProperty(name) as? String ?: System.getenv(name) }
}
