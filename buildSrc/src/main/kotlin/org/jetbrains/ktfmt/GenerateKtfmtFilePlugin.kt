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

package org.jetbrains.ktfmt

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.register

@Suppress("unused")
class GenerateKtfmtFilePlugin : Plugin<Project> {
  override fun apply(project: Project) {
    val propertiesFile = project.rootProject.file("gradle.properties")
    if (!propertiesFile.exists()) {
      error("gradle.properties not found in root project, can't generate Ktfmt.kt")
    }

    val outputDir = project.layout.buildDirectory.dir("generated/main/kotlin").get().asFile
    val outputFile = outputDir.resolve("org/jetbrains/ktfmt/util/Ktfmt.kt")

    project.tasks.register<GenerateKtfmtFileTask>("generateKtfmtFile") {
      this.propertiesFile.set(propertiesFile)
      this.outputFile.set(outputFile)
    }
  }
}
