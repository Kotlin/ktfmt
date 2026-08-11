/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType.IntellijIdeaCommunity

plugins {
  java
  alias(libs.plugins.dependencyAnalysis)
  alias(libs.plugins.kotlin)
  alias(libs.plugins.intelliJPlatform)
}

val ktfmtVersion = rootProject.version
val pluginVersion = "1.3"

group = "org.jetbrains"

version = "$pluginVersion.$ktfmtVersion"

kotlin {
  val javaVersion: String = rootProject.libs.versions.java.get()
  jvmToolchain(javaVersion.toInt())
}

repositories {
  mavenCentral()
  intellijPlatform { defaultRepositories() }
  mavenLocal()
}

dependencies {
  intellijPlatform {
    create(IntellijIdeaCommunity, "2022.3")
    pluginVerifier()
    zipSigner()
  }

  implementation(project(":ktfmt"))
  implementation(libs.googleJavaformat)
  implementation(libs.guava)
  implementation(libs.kotlin.compilerEmbeddable)
}

intellijPlatform {
  projectName = "ktfmt_idea_plugin"

  pluginConfiguration.ideaVersion {
    sinceBuild = "223.7571.182" // 2022.3
    untilBuild = provider { null }
  }

  publishing { token = System.getenv("JETBRAINS_MARKETPLACE_TOKEN") }

  pluginVerification { ides { recommended() } }
}

intellijPlatformTesting.runIde.register("runIntellij242") {
  type = IntellijIdeaCommunity
  version = "2024.2"
}
