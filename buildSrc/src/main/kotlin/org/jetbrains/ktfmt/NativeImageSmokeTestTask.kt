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

import javax.inject.Inject
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations

/**
 * Runs the Native Image binary against the project sources.
 *
 * The binary and the sources are declared as inputs instead of being baked into the task as
 * absolute paths at configuration time, so the task takes part in up-to-date checking and nothing
 * location-dependent ends up in the configuration cache entry.
 */
abstract class NativeImageSmokeTestTask : DefaultTask() {
  @get:InputFile @get:PathSensitive(PathSensitivity.NONE) abstract val binary: RegularFileProperty

  @get:InputDirectory
  @get:PathSensitive(PathSensitivity.RELATIVE)
  abstract val sources: DirectoryProperty

  @get:OutputFile abstract val report: RegularFileProperty

  @get:Inject abstract val execOperations: ExecOperations

  @TaskAction
  fun smokeTest() {
    val binaryFile = binary.get().asFile
    if (!binaryFile.canExecute()) {
      throw GradleException("$binaryFile is not executable")
    }

    execOperations.exec {
      executable(binaryFile.absolutePath)
      args(sources.get().asFile.absolutePath, "--dry-run", "--set-exit-if-changed")
    }

    report.get().asFile.writeText("$binaryFile reported no changes\n")
  }
}
