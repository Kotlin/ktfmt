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

// @licenselint-loose-mode

package org.jetbrains.ktfmt

import java.nio.file.Path
import java.util.Properties
import kotlin.io.path.inputStream
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

abstract class GenerateKtfmtFileTask : DefaultTask() {

  @get:InputFile abstract val propertiesFile: RegularFileProperty

  @get:OutputFile abstract val outputFile: RegularFileProperty

  init {
    group = "build"
    description = "Generates Ktfmt.kt from gradle.properties"
  }

  @TaskAction
  fun generate() {
    val properties = parseProperties(propertiesFile.get().asFile.toPath())
    val ktfmtFileSource = generateKtfmtFile(properties)
    outputFile.get().asFile.apply {
      parentFile.mkdirs()
      writeText(ktfmtFileSource)
    }
  }

  companion object {
    private fun parseProperties(file: Path): Map<String, String> =
        Properties()
            .apply { load(file.inputStream()) }
            .map { it.key.toString() to it.value.toString() }
            .filter { it.first.startsWith("ktfmt.") }
            .associate { it.first.removePrefix("ktfmt.") to it.second }

    private fun generateKtfmtFile(properties: Map<String, String>): String =
        """
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

        package org.jetbrains.ktfmt.util

        object Ktfmt {
        %s
        }
        """
            .trimIndent()
            .format(
                properties
                    .map() { (propName, propValue) -> "  const val ${propName} = \"${propValue}\"" }
                    .joinToString("\n"),
            )
  }
}
