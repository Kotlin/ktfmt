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

import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

abstract class GenerateKtfmtFileTask : DefaultTask() {

  @get:Input abstract val properties: MapProperty<String, String>

  @get:OutputFile abstract val outputFile: RegularFileProperty

  init {
    group = "build"
    description = "Generates Ktfmt.kt from gradle.properties"
  }

  @TaskAction
  fun generate() {
    val ktfmtFileSource = generateKtfmtFile(properties.get())
    outputFile.get().asFile.apply {
      parentFile.mkdirs()
      writeText(ktfmtFileSource)
    }
  }

  companion object {
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
        ${properties.map { "    const val ${it.key} = \"${it.value}\"" }.joinToString("\n")}
        }
        """
            .trimIndent()
  }
}
