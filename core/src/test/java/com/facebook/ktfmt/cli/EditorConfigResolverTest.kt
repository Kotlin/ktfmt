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

package com.facebook.ktfmt.cli

import com.facebook.ktfmt.format.Formatter
import com.facebook.ktfmt.format.TrailingCommaManagementStrategy
import com.facebook.ktfmt.format.TrailingCommaManagementStrategy.ONLY_ADD
import com.google.common.truth.Truth.assertThat
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import kotlin.io.path.createTempDirectory
import kotlin.text.Charsets.UTF_8
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@Suppress("FunctionNaming")
class EditorConfigResolverTest {
  private val root = createTempDirectory().toFile()
  private val testCharset = StandardCharsets.UTF_16

  @BeforeEach
  fun setUp() {
    assertThat(Charset.defaultCharset()).isEqualTo(testCharset) // Verify the test JVM flags
  }

  @AfterEach
  fun tearDown() {
    root.deleteRecursively()
  }

  @Test
  fun `resolves base properties when no editorconfig file`() {
    val src = root.resolve("src/main/kotlin/Example.kt")
    src.parentFile.mkdirs()
    src.writeText("", UTF_8)
    val resolved = EditorConfigResolver.resolveFormattingOptions(src, Formatter.GOOGLE_FORMAT)
    assertThat(resolved).isEqualTo(Formatter.GOOGLE_FORMAT)
  }

  @Test
  fun `resolves base properties editorconfig file doesn't match`() {
    val conf = root.resolve(".editorconfig")
    conf.writeText(
        """
        root = true
        [*.c]
        max_line_length = 80
        """
            .trimIndent(),
    )

    val file = root.resolve("src/main/kotlin/Example.kt")
    val resolved = EditorConfigResolver.resolveFormattingOptions(file, Formatter.GOOGLE_FORMAT)
    assertThat(resolved).isEqualTo(Formatter.GOOGLE_FORMAT)
  }

  @Test
  fun `overrides maxWidth based on editorconfig max_line_length`() {
    val conf = root.resolve(".editorconfig")
    conf.writeText(
        """
        root = true
        [*.kt]
        max_line_length = 80
        """
            .trimIndent(),
    )

    val file = root.resolve("src/main/kotlin/Example.kt")
    val resolved = EditorConfigResolver.resolveFormattingOptions(file, Formatter.GOOGLE_FORMAT)
    assertThat(resolved).isEqualTo(Formatter.GOOGLE_FORMAT.copy(maxWidth = 80))
  }

  @Test
  fun `doesn't override maxWidth based when editorconfig max_line_length = off`() {
    val conf = root.resolve(".editorconfig")
    conf.writeText(
        """
        root = true
        [*.kt]
        max_line_length = off
        """
            .trimIndent(),
    )

    val file = root.resolve("src/main/kotlin/Example.kt")
    val resolved = EditorConfigResolver.resolveFormattingOptions(file, Formatter.GOOGLE_FORMAT)
    assertThat(resolved).isEqualTo(Formatter.GOOGLE_FORMAT)
  }

  @Test
  fun `overrides blockIndent based on editorconfig indent_size`() {
    val conf = root.resolve(".editorconfig")
    conf.writeText(
        """
        root = true
        [*.kt]
        indent_size = 3
        """
            .trimIndent(),
    )

    val file = root.resolve("src/main/kotlin/Example.kt")
    val resolved = EditorConfigResolver.resolveFormattingOptions(file, Formatter.GOOGLE_FORMAT)
    assertThat(resolved).isEqualTo(Formatter.GOOGLE_FORMAT.copy(blockIndent = 3))
  }

  @Test
  fun `doesn't override blockIndent when indent_size = tab and no tab_width`() {
    val conf = root.resolve(".editorconfig")
    conf.writeText(
        """
        root = true
        [*.kt]
        indent_size = tab
        """
            .trimIndent(),
    )

    val file = root.resolve("src/main/kotlin/Example.kt")
    val resolved = EditorConfigResolver.resolveFormattingOptions(file, Formatter.GOOGLE_FORMAT)
    assertThat(resolved).isEqualTo(Formatter.GOOGLE_FORMAT)
  }

  @Test
  fun `overrides blockIndent with tab_width when indent_size = tab`() {
    val conf = root.resolve(".editorconfig")
    conf.writeText(
        """
        root = true
        [*.kt]
        indent_size = tab
        tab_width = 8
        """
            .trimIndent(),
    )

    val file = root.resolve("src/main/kotlin/Example.kt")
    val resolved = EditorConfigResolver.resolveFormattingOptions(file, Formatter.GOOGLE_FORMAT)
    assertThat(resolved).isEqualTo(Formatter.GOOGLE_FORMAT.copy(blockIndent = 8))
  }

  @Test
  fun `overrides blockIndent based on editorconfig ij_kotlin_indent_size`() {
    val conf = root.resolve(".editorconfig")
    conf.writeText(
        """
        root = true
        [*.kt]
        ij_kotlin_indent_size = 3
        """
            .trimIndent(),
    )

    val file = root.resolve("src/main/kotlin/Example.kt")
    val resolved = EditorConfigResolver.resolveFormattingOptions(file, Formatter.GOOGLE_FORMAT)
    assertThat(resolved).isEqualTo(Formatter.GOOGLE_FORMAT.copy(blockIndent = 3))
  }

  @Test
  fun `ij_kotlin_indent_size takes priority over indent_size`() {
    val conf = root.resolve(".editorconfig")
    conf.writeText(
        """
        root = true
        [*.kt]
        indent_size = 2
        ij_kotlin_indent_size = 3
        """
            .trimIndent(),
    )

    val file = root.resolve("src/main/kotlin/Example.kt")
    val resolved = EditorConfigResolver.resolveFormattingOptions(file, Formatter.GOOGLE_FORMAT)
    assertThat(resolved).isEqualTo(Formatter.GOOGLE_FORMAT.copy(blockIndent = 3))
  }

  @Test
  fun `overrides continuationIndent based on editorconfig ij_continuation_indent_size`() {
    val conf = root.resolve(".editorconfig")
    conf.writeText(
        """
        root = true
        [*.kt]
        ij_continuation_indent_size = 3
        """
            .trimIndent(),
    )

    val file = root.resolve("src/main/kotlin/Example.kt")
    val resolved = EditorConfigResolver.resolveFormattingOptions(file, Formatter.GOOGLE_FORMAT)
    assertThat(resolved).isEqualTo(Formatter.GOOGLE_FORMAT.copy(continuationIndent = 3))
  }

  @Test
  fun `overrides continuationIndent based on editorconfig ij_kotlin_continuation_indent_size`() {
    val conf = root.resolve(".editorconfig")
    conf.writeText(
        """
        root = true
        [*.kt]
        ij_kotlin_continuation_indent_size = 3
        """
            .trimIndent(),
    )

    val file = root.resolve("src/main/kotlin/Example.kt")
    val resolved = EditorConfigResolver.resolveFormattingOptions(file, Formatter.GOOGLE_FORMAT)
    assertThat(resolved).isEqualTo(Formatter.GOOGLE_FORMAT.copy(continuationIndent = 3))
  }

  @Test
  fun `ij_kotlin_continuation_indent_size takes precedence over ij_continuation_indent_size`() {
    val conf = root.resolve(".editorconfig")
    conf.writeText(
        """
        root = true
        [*.kt]
        ij_continuation_indent_size = 6
        ij_kotlin_continuation_indent_size = 3
        """
            .trimIndent(),
    )

    val file = root.resolve("src/main/kotlin/Example.kt")
    val resolved = EditorConfigResolver.resolveFormattingOptions(file, Formatter.GOOGLE_FORMAT)
    assertThat(resolved).isEqualTo(Formatter.GOOGLE_FORMAT.copy(continuationIndent = 3))
  }

  @Test
  fun `overrides trailingCommaManagementStrategy based on editorconfig ktfmt_trailing_comma_management_strategy`() {
    val conf = root.resolve(".editorconfig")
    conf.writeText(
        """
        root = true
        [*.kt]
        ktfmt_trailing_comma_management_strategy = only_add
        """
            .trimIndent(),
    )

    val file = root.resolve("src/main/kotlin/Example.kt")
    val resolved = EditorConfigResolver.resolveFormattingOptions(file, Formatter.GOOGLE_FORMAT)
    assertThat(resolved)
        .isEqualTo(Formatter.GOOGLE_FORMAT.copy(trailingCommaManagementStrategy = ONLY_ADD))
  }

  @Test
  fun `ignores invalid ktfmt_trailing_comma_management_strategy`() {
    val conf = root.resolve(".editorconfig")
    conf.writeText(
        """
        root = true
        [*.kt]
        ktfmt_trailing_comma_management_strategy = whatever
        """
            .trimIndent(),
    )

    val file = root.resolve("src/main/kotlin/Example.kt")
    val resolved = EditorConfigResolver.resolveFormattingOptions(file, Formatter.GOOGLE_FORMAT)
    assertThat(resolved).isEqualTo(Formatter.GOOGLE_FORMAT)
  }

  @Test
  fun `combines multiple matching configs`() {
    val rootConf = root.resolve(".editorconfig")
    rootConf.writeText(
        """
        root = true
        [{*.kt,*.kts}]
        indent_size = 3
        ktfmt_trailing_comma_management_strategy = none
        [src/**/*.kts]
        max_line_length = 120
        """
            .trimIndent(),
    )
    val rootOptions =
        Formatter.GOOGLE_FORMAT.copy(
            blockIndent = 3,
            trailingCommaManagementStrategy = TrailingCommaManagementStrategy.NONE,
        )

    val mainConf = root.resolve("src/main/.editorconfig")
    mainConf.parentFile.mkdirs()
    mainConf.writeText(
        """
        [*.kt]
        ij_continuation_indent_size = 3
        max_line_length = 200
        ktfmt_trailing_comma_management_strategy = only_add
        """
            .trimIndent(),
    )
    val mainOptions =
        Formatter.GOOGLE_FORMAT.copy(
            maxWidth = 200,
            blockIndent = 3, // inherited from root .editorconfig
            continuationIndent = 3,
            trailingCommaManagementStrategy = ONLY_ADD, // overridden from root .editorconfig
        )

    val testConf = root.resolve("src/test/.editorconfig")
    testConf.parentFile.mkdirs()
    testConf.writeText(
        """
        root = true
        [*.kt]
        indent_size = 4
        ij_continuation_indent_size = 2
        max_line_length = 300
        """
            .trimIndent(),
    )
    val testOptions =
        Formatter.GOOGLE_FORMAT.copy(
            maxWidth = 300,
            blockIndent = 4,
            continuationIndent = 2,
            // trailing comma not inherited from root as test/.editorconfig marked root=true
        )

    val fileInRoot = root.resolve("build.gradle.kts")
    assertThat(EditorConfigResolver.resolveFormattingOptions(fileInRoot, Formatter.GOOGLE_FORMAT))
        .isEqualTo(rootOptions)

    val fileInMain = root.resolve("src/main/kotlin/Example.kt")
    assertThat(EditorConfigResolver.resolveFormattingOptions(fileInMain, Formatter.GOOGLE_FORMAT))
        .isEqualTo(mainOptions)

    val fileInTest = root.resolve("src/test/kotlin/ExampleTest.kt")
    assertThat(EditorConfigResolver.resolveFormattingOptions(fileInTest, Formatter.GOOGLE_FORMAT))
        .isEqualTo(testOptions)

    val ktsInMain = root.resolve("src/main/kotlin/ExampleTest.kts")
    assertThat(EditorConfigResolver.resolveFormattingOptions(ktsInMain, Formatter.GOOGLE_FORMAT))
        .isEqualTo(rootOptions.copy(maxWidth = 120))

    val ktsInTest = root.resolve("src/test/kotlin/ExampleTest.kts")
    assertThat(EditorConfigResolver.resolveFormattingOptions(ktsInTest, Formatter.GOOGLE_FORMAT))
        .isEqualTo(Formatter.GOOGLE_FORMAT) // root=true stops even non-matching fall-through
  }
}
