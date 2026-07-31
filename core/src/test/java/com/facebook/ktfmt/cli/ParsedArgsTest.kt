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
import com.facebook.ktfmt.format.FormattingOptions
import com.google.common.collect.Range
import com.google.common.collect.RangeSet
import com.google.common.collect.TreeRangeSet
import com.google.common.truth.Truth.assertThat
import java.io.FileNotFoundException
import kotlin.io.path.createTempDirectory
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

@Suppress("FunctionNaming")
class ParsedArgsTest {

  private val root = createTempDirectory().toFile()

  @AfterEach
  fun tearDown() {
    root.deleteRecursively()
  }

  @Test
  fun `unknown flags return an error`() {
    val result = parseOptions("--unknown")
    assertThat(result).isInstanceOf(ParseResult.Error::class.java)
  }

  @Test
  fun `unknown flags starting with '@' return an error`() {
    val result = parseOptions("@unknown")
    assertThat(result).isInstanceOf(ParseResult.Error::class.java)
  }

  @Test
  fun `parseOptions uses default values when args are empty`() {
    val parsed = assertSucceeds(parseOptions("foo.kt"))

    val formattingOptions = parsed.formattingOptions

    val defaultFormattingOptions = Formatter.META_FORMAT
    assertThat(formattingOptions).isEqualTo(defaultFormattingOptions)
  }

  @Test
  fun `parseOptions recognizes --meta-style`() {
    val parsed = assertSucceeds(parseOptions("--meta-style", "foo.kt"))
    assertThat(parsed.formattingOptions).isEqualTo(Formatter.META_FORMAT)
  }

  @Test
  fun `parseOptions recognizes --google-style`() {
    val parsed = assertSucceeds(parseOptions("--google-style", "foo.kt"))
    assertThat(parsed.formattingOptions).isEqualTo(Formatter.GOOGLE_FORMAT)
  }

  @Test
  fun `parseOptions recognizes --dry-run`() {
    val parsed = assertSucceeds(parseOptions("--dry-run", "foo.kt"))
    assertThat(parsed.dryRun).isTrue()
  }

  @Test
  fun `parseOptions recognizes -n as --dry-run`() {
    val parsed = assertSucceeds(parseOptions("-n", "foo.kt"))
    assertThat(parsed.dryRun).isTrue()
  }

  @Test
  fun `parseOptions recognizes --set-exit-if-changed`() {
    val parsed = assertSucceeds(parseOptions("--set-exit-if-changed", "foo.kt"))
    assertThat(parsed.setExitIfChanged).isTrue()
  }

  @Test
  fun `parseOptions defaults to removing imports`() {
    val parsed = assertSucceeds(parseOptions("foo.kt"))
    assertThat(parsed.formattingOptions.removeUnusedImports).isTrue()
  }

  @Test
  fun `parseOptions recognizes --do-not-remove-unused-imports to removing imports`() {
    val parsed = assertSucceeds(parseOptions("--do-not-remove-unused-imports", "foo.kt"))
    assertThat(parsed.formattingOptions.removeUnusedImports).isFalse()
  }

  @Test
  fun `parseOptions recognizes --enable-editorconfig`() {
    val parsed = assertSucceeds(parseOptions("--enable-editorconfig", "foo.kt"))
    assertThat(parsed.editorConfig).isEqualTo(true)
  }

  @Test
  fun `parseOptions recognizes --quiet`() {
    val parsed = assertSucceeds(parseOptions("--quiet", "foo.kt"))
    assertThat(parsed.quiet).isTrue()
  }

  @Test
  fun `parseOptions recognizes --stdin-name`() {
    val parsed = assertSucceeds(parseOptions("--stdin-name=my/foo.kt", "-"))
    assertThat(parsed.stdinName).isEqualTo("my/foo.kt")
  }

  @Test
  fun `parseOptions recognizes --lines ranges`() {
    val parsed = assertSucceeds(parseOptions("--lines=1:3,5", "--lines", "7", "foo.kt"))

    assertThat(parsed.lineRanges)
        .isEqualTo(
            ranges(
                Range.closedOpen(0, 3),
                Range.closedOpen(4, 5),
                Range.closedOpen(6, 7),
            ),
        )
  }

  @Test
  fun `parseOptions recognizes --line alias`() {
    val parsed = assertSucceeds(parseOptions("--line=1", "foo.kt"))
    assertThat(parsed.fileNames).containsExactly("foo.kt")
    assertThat(parsed.lineRanges).isEqualTo(ranges(Range.closedOpen(0, 1)))

    assertThat(assertSucceeds(parseOptions("--line", "2", "foo.kt")).lineRanges)
        .isEqualTo(ranges(Range.closedOpen(1, 2)))
  }

  @Test
  fun `parseOptions recognizes offset and length pairs`() {
    val parsed = assertSucceeds(
        parseOptions(
            "--offset=10",
            "--length=5",
            "--offset",
            "20",
            "--length",
            "0",
            "foo.kt",
        ),
    )

    assertThat(parsed.characterRanges)
        .isEqualTo(
            ranges(
                Range.closedOpen(10, 15),
                Range.closedOpen(20, 21),
            ),
        )
  }

  @Test
  fun `parseOptions rejects --lines without value`() {
    val parseResult = parseOptions("--lines")
    assertThat(parseResult)
        .isEqualTo(ParseResult.Error("required value was not provided for: --lines"))
  }

  @Test
  fun `parseOptions rejects invalid --lines range`() {
    val parseResult = parseOptions("--lines=not-a-line", "foo.kt")
    assertThat(parseResult)
        .isEqualTo(ParseResult.Error("invalid line range for --lines: not-a-line"))
  }

  @Test
  fun `parseOptions rejects --offset without value`() {
    val parseResult = parseOptions("--offset")
    assertThat(parseResult)
        .isEqualTo(ParseResult.Error("required value was not provided for: --offset"))
  }

  @Test
  fun `parseOptions rejects invalid --offset`() {
    val parseResult = parseOptions("--offset=not-an-offset", "--length=1", "foo.kt")
    assertThat(parseResult)
        .isEqualTo(ParseResult.Error("invalid integer value for --offset: not-an-offset"))
  }

  @Test
  fun `parseOptions rejects mismatched --offset and --length counts`() {
    val parseResult = parseOptions("--offset=1", "foo.kt")
    assertThat(parseResult)
        .isEqualTo(
            ParseResult.Error("--offset and --length flags must be provided in matching pairs"),
        )
  }

  @Test
  fun `parseOptions rejects --lines with multiple files`() {
    val parseResult = parseOptions("--lines=1", "foo.kt", "bar.kt")
    assertThat(parseResult)
        .isEqualTo(ParseResult.Error("partial formatting is only supported for a single file"))
  }

  @Test
  fun `parseOptions rejects --offset with multiple files`() {
    val parseResult = parseOptions("--offset=1", "--length=1", "foo.kt", "bar.kt")
    assertThat(parseResult)
        .isEqualTo(ParseResult.Error("partial formatting is only supported for a single file"))
  }

  @Test
  fun `parseOptions accepts --stdin-name with empty value`() {
    val parsed = assertSucceeds(parseOptions("--stdin-name=", "-"))
    assertThat(parsed.stdinName).isEqualTo("")
  }

  @Test
  fun `parseOptions rejects --stdin-name without value`() {
    val parseResult = parseOptions("--stdin-name")
    assertThat(parseResult).isInstanceOf(ParseResult.Error::class.java)
  }

  @Test
  fun `parseOptions rejects '-' and files at the same time`() {
    val parseResult = parseOptions("-", "File.kt")
    assertThat(parseResult).isInstanceOf(ParseResult.Error::class.java)
  }

  @Test
  fun `parseOptions rejects --stdin-name when not reading from stdin`() {
    val parseResult = parseOptions("--stdin-name=foo", "file1.kt")
    assertThat(parseResult).isInstanceOf(ParseResult.Error::class.java)
  }

  @Test
  fun `parseOptions recognises --help`() {
    val parseResult = parseOptions("--help")
    assertThat(parseResult).isInstanceOf(ParseResult.ShowMessage::class.java)
  }

  @Test
  fun `parseOptions recognises -h`() {
    val parseResult = parseOptions("-h")
    assertThat(parseResult).isInstanceOf(ParseResult.ShowMessage::class.java)
  }

  @Test
  fun `arg --help overrides all others`() {
    val parseResult = parseOptions("--style=google", "@unknown", "--help", "file.kt")
    assertThat(parseResult).isInstanceOf(ParseResult.ShowMessage::class.java)
  }

  @Test
  fun `parseOptions recognises --version`() {
    val parseResult = parseOptions("--version")
    assertThat(parseResult).isInstanceOf(ParseResult.ShowMessage::class.java)
  }

  @Test
  fun `parseOptions recognises -v`() {
    val parseResult = parseOptions("-v")
    assertThat(parseResult).isInstanceOf(ParseResult.ShowMessage::class.java)
  }

  @Test
  fun `arg --version overrides all others`() {
    val parseResult = parseOptions("--style=google", "@unknown", "--version", "file.kt")
    assertThat(parseResult).isInstanceOf(ParseResult.ShowMessage::class.java)
  }

  @Test
  fun `processArgs use the @file option with non existing file`() {
    val e =
        assertThrows<FileNotFoundException> {
          ParsedArgs.processArgs(arrayOf("@non-existing-file"))
        }
    assertThat(e.message).contains("non-existing-file")
  }

  @Test
  fun `processArgs use the @file option with file containing arguments`() {
    val file = root.resolve("existing-file")
    file.writeText("--google-style\n--dry-run\n--set-exit-if-changed\nFile1.kt\nFile2.kt\n")

    val result = ParsedArgs.processArgs(arrayOf("@" + file.canonicalPath))
    assertThat(result).isInstanceOf(ParseResult.Ok::class.java)

    val parsed = (result as ParseResult.Ok).parsedValue

    assertThat(parsed.formattingOptions).isEqualTo(Formatter.GOOGLE_FORMAT)
    assertThat(parsed.dryRun).isTrue()
    assertThat(parsed.setExitIfChanged).isTrue()
    assertThat(parsed.fileNames).containsExactlyElementsIn(listOf("File1.kt", "File2.kt"))
  }

  @Test
  fun `parses multiple args successfully`() {
    val testResult = parseOptions(
        "--google-style",
        "--dry-run",
        "--set-exit-if-changed",
        "File.kt",
    )
    assertThat(testResult)
        .isEqualTo(
            parseResultOk(
                fileNames = listOf("File.kt"),
                formattingOptions = Formatter.GOOGLE_FORMAT,
                dryRun = true,
                setExitIfChanged = true,
            ),
        )
  }

  @Test
  fun `last style in args wins`() {
    val testResult = parseOptions("--google-style", "--kotlinlang-style", "File.kt")
    assertThat(testResult)
        .isEqualTo(
            parseResultOk(
                fileNames = listOf("File.kt"),
                formattingOptions = Formatter.KOTLINLANG_FORMAT,
            ),
        )
  }

  @Test
  fun `error when parsing multiple args and one is unknown`() {
    val testResult = parseOptions("@unknown", "--google-style", "File.kt")
    assertThat(testResult).isEqualTo(ParseResult.Error("Unexpected option: @unknown"))
  }

  private fun parseOptions(vararg options: String): ParseResult = ParsedArgs.parseOptions(options)

  private fun assertSucceeds(parseResult: ParseResult): ParsedArgs {
    assertThat(parseResult).isInstanceOf(ParseResult.Ok::class.java)
    return (parseResult as ParseResult.Ok).parsedValue
  }

  private fun parseResultOk(
      fileNames: List<String> = emptyList(),
      formattingOptions: FormattingOptions = Formatter.META_FORMAT,
      dryRun: Boolean = false,
      setExitIfChanged: Boolean = false,
      removedUnusedImports: Boolean = true,
      stdinName: String? = null,
      editorConfig: Boolean = false,
      quiet: Boolean = false,
  ): ParseResult.Ok {
    val returnedFormattingOptions =
        formattingOptions.copy(removeUnusedImports = removedUnusedImports)
    return ParseResult.Ok(
        ParsedArgs(
            fileNames,
            returnedFormattingOptions,
            dryRun,
            setExitIfChanged,
            stdinName,
            editorConfig,
            quiet,
        ),
    )
  }

  private fun ranges(vararg ranges: Range<Int>): RangeSet<Int> {
    val lineRanges = TreeRangeSet.create<Int>()
    for (range in ranges) {
      lineRanges.add(range)
    }
    return lineRanges
  }
}
