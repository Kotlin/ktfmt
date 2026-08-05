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
import com.facebook.ktfmt.testutil.assertContains
import com.google.common.collect.Range
import com.google.common.collect.RangeSet
import com.google.common.collect.TreeRangeSet
import java.io.FileNotFoundException
import kotlin.io.path.createTempDirectory
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
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
    assertInstanceOf(ParseResult.Error::class.java, result)
  }

  @Test
  fun `unknown flags starting with '@' return an error`() {
    val result = parseOptions("@unknown")
    assertInstanceOf(ParseResult.Error::class.java, result)
  }

  @Test
  fun `parseOptions uses default values when args are empty`() {
    val parsed = assertSucceeds(parseOptions("foo.kt"))

    val formattingOptions = parsed.formattingOptions

    val defaultFormattingOptions = Formatter.META_FORMAT
    assertEquals(defaultFormattingOptions, formattingOptions)
  }

  @Test
  fun `parseOptions recognizes --meta-style`() {
    val parsed = assertSucceeds(parseOptions("--meta-style", "foo.kt"))
    assertEquals(Formatter.META_FORMAT, parsed.formattingOptions)
  }

  @Test
  fun `parseOptions recognizes --google-style`() {
    val parsed = assertSucceeds(parseOptions("--google-style", "foo.kt"))
    assertEquals(Formatter.GOOGLE_FORMAT, parsed.formattingOptions)
  }

  @Test
  fun `parseOptions recognizes --dry-run`() {
    val parsed = assertSucceeds(parseOptions("--dry-run", "foo.kt"))
    assertTrue(parsed.dryRun)
  }

  @Test
  fun `parseOptions recognizes -n as --dry-run`() {
    val parsed = assertSucceeds(parseOptions("-n", "foo.kt"))
    assertTrue(parsed.dryRun)
  }

  @Test
  fun `parseOptions recognizes --set-exit-if-changed`() {
    val parsed = assertSucceeds(parseOptions("--set-exit-if-changed", "foo.kt"))
    assertTrue(parsed.setExitIfChanged)
  }

  @Test
  fun `parseOptions defaults to removing imports`() {
    val parsed = assertSucceeds(parseOptions("foo.kt"))
    assertTrue(parsed.formattingOptions.removeUnusedImports)
  }

  @Test
  fun `parseOptions recognizes --do-not-remove-unused-imports to removing imports`() {
    val parsed = assertSucceeds(parseOptions("--do-not-remove-unused-imports", "foo.kt"))
    assertFalse(parsed.formattingOptions.removeUnusedImports)
  }

  @Test
  fun `parseOptions recognizes --enable-editorconfig`() {
    val parsed = assertSucceeds(parseOptions("--enable-editorconfig", "foo.kt"))
    assertEquals(true, parsed.editorConfig)
  }

  @Test
  fun `parseOptions recognizes --quiet`() {
    val parsed = assertSucceeds(parseOptions("--quiet", "foo.kt"))
    assertTrue(parsed.quiet)
  }

  @Test
  fun `parseOptions recognizes --stdin-name`() {
    val parsed = assertSucceeds(parseOptions("--stdin-name=my/foo.kt", "-"))
    assertEquals("my/foo.kt", parsed.stdinName)
  }

  @Test
  fun `parseOptions recognizes --lines ranges`() {
    val parsed = assertSucceeds(parseOptions("--lines=1:3,5", "--lines", "7", "foo.kt"))

    assertEquals(
        ranges(
            Range.closedOpen(0, 3),
            Range.closedOpen(4, 5),
            Range.closedOpen(6, 7),
        ),
        parsed.lineRanges,
    )
  }

  @Test
  fun `parseOptions recognizes --line alias`() {
    val parsed = assertSucceeds(parseOptions("--line=1", "foo.kt"))
    assertEquals(listOf("foo.kt"), parsed.fileNames)
    assertEquals(ranges(Range.closedOpen(0, 1)), parsed.lineRanges)

    assertEquals(
        ranges(Range.closedOpen(1, 2)),
        assertSucceeds(parseOptions("--line", "2", "foo.kt")).lineRanges,
    )
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

    assertEquals(
        ranges(
            Range.closedOpen(10, 15),
            Range.closedOpen(20, 21),
        ),
        parsed.characterRanges,
    )
  }

  @Test
  fun `parseOptions rejects --lines without value`() {
    val parseResult = parseOptions("--lines")
    assertEquals(ParseResult.Error("required value was not provided for: --lines"), parseResult)
  }

  @Test
  fun `parseOptions rejects invalid --lines range`() {
    val parseResult = parseOptions("--lines=not-a-line", "foo.kt")
    assertEquals(ParseResult.Error("invalid line range for --lines: not-a-line"), parseResult)
  }

  @Test
  fun `parseOptions rejects --offset without value`() {
    val parseResult = parseOptions("--offset")
    assertEquals(ParseResult.Error("required value was not provided for: --offset"), parseResult)
  }

  @Test
  fun `parseOptions rejects invalid --offset`() {
    val parseResult = parseOptions("--offset=not-an-offset", "--length=1", "foo.kt")
    assertEquals(
        ParseResult.Error("invalid integer value for --offset: not-an-offset"),
        parseResult,
    )
  }

  @Test
  fun `parseOptions rejects mismatched --offset and --length counts`() {
    val parseResult = parseOptions("--offset=1", "foo.kt")
    assertEquals(
        ParseResult.Error("--offset and --length flags must be provided in matching pairs"),
        parseResult,
    )
  }

  @Test
  fun `parseOptions rejects --lines with multiple files`() {
    val parseResult = parseOptions("--lines=1", "foo.kt", "bar.kt")
    assertEquals(
        ParseResult.Error("partial formatting is only supported for a single file"),
        parseResult,
    )
  }

  @Test
  fun `parseOptions rejects --offset with multiple files`() {
    val parseResult = parseOptions("--offset=1", "--length=1", "foo.kt", "bar.kt")
    assertEquals(
        ParseResult.Error("partial formatting is only supported for a single file"),
        parseResult,
    )
  }

  @Test
  fun `parseOptions accepts --stdin-name with empty value`() {
    val parsed = assertSucceeds(parseOptions("--stdin-name=", "-"))
    assertEquals("", parsed.stdinName)
  }

  @Test
  fun `parseOptions rejects --stdin-name without value`() {
    val parseResult = parseOptions("--stdin-name")
    assertInstanceOf(ParseResult.Error::class.java, parseResult)
  }

  @Test
  fun `parseOptions rejects '-' and files at the same time`() {
    val parseResult = parseOptions("-", "File.kt")
    assertInstanceOf(ParseResult.Error::class.java, parseResult)
  }

  @Test
  fun `parseOptions rejects --stdin-name when not reading from stdin`() {
    val parseResult = parseOptions("--stdin-name=foo", "file1.kt")
    assertInstanceOf(ParseResult.Error::class.java, parseResult)
  }

  @Test
  fun `parseOptions recognises --help`() {
    val parseResult = parseOptions("--help")
    assertInstanceOf(ParseResult.ShowMessage::class.java, parseResult)
  }

  @Test
  fun `parseOptions recognises -h`() {
    val parseResult = parseOptions("-h")
    assertInstanceOf(ParseResult.ShowMessage::class.java, parseResult)
  }

  @Test
  fun `arg --help overrides all others`() {
    val parseResult = parseOptions("--style=google", "@unknown", "--help", "file.kt")
    assertInstanceOf(ParseResult.ShowMessage::class.java, parseResult)
  }

  @Test
  fun `parseOptions recognises --version`() {
    val parseResult = parseOptions("--version")
    assertInstanceOf(ParseResult.ShowMessage::class.java, parseResult)
  }

  @Test
  fun `parseOptions recognises -v`() {
    val parseResult = parseOptions("-v")
    assertInstanceOf(ParseResult.ShowMessage::class.java, parseResult)
  }

  @Test
  fun `arg --version overrides all others`() {
    val parseResult = parseOptions("--style=google", "@unknown", "--version", "file.kt")
    assertInstanceOf(ParseResult.ShowMessage::class.java, parseResult)
  }

  @Test
  fun `processArgs use the @file option with non existing file`() {
    val e =
        assertThrows<FileNotFoundException> {
          ParsedArgs.processArgs(arrayOf("@non-existing-file"))
        }
    assertContains(e.message, "non-existing-file")
  }

  @Test
  fun `processArgs use the @file option with file containing arguments`() {
    val file = root.resolve("existing-file")
    file.writeText("--google-style\n--dry-run\n--set-exit-if-changed\nFile1.kt\nFile2.kt\n")

    val result = ParsedArgs.processArgs(arrayOf("@" + file.canonicalPath))
    assertInstanceOf(ParseResult.Ok::class.java, result)

    val parsed = (result as ParseResult.Ok).parsedValue

    assertEquals(Formatter.GOOGLE_FORMAT, parsed.formattingOptions)
    assertTrue(parsed.dryRun)
    assertTrue(parsed.setExitIfChanged)
    assertEquals(listOf("File1.kt", "File2.kt"), parsed.fileNames)
  }

  @Test
  fun `parses multiple args successfully`() {
    val testResult = parseOptions(
        "--google-style",
        "--dry-run",
        "--set-exit-if-changed",
        "File.kt",
    )
    assertEquals(
        parseResultOk(
            fileNames = listOf("File.kt"),
            formattingOptions = Formatter.GOOGLE_FORMAT,
            dryRun = true,
            setExitIfChanged = true,
        ),
        testResult,
    )
  }

  @Test
  fun `last style in args wins`() {
    val testResult = parseOptions("--google-style", "--kotlinlang-style", "File.kt")
    assertEquals(
        parseResultOk(
            fileNames = listOf("File.kt"),
            formattingOptions = Formatter.KOTLINLANG_FORMAT,
        ),
        testResult,
    )
  }

  @Test
  fun `error when parsing multiple args and one is unknown`() {
    val testResult = parseOptions("@unknown", "--google-style", "File.kt")
    assertEquals(ParseResult.Error("Unexpected option: @unknown"), testResult)
  }

  private fun parseOptions(vararg options: String): ParseResult = ParsedArgs.parseOptions(options)

  private fun assertSucceeds(parseResult: ParseResult): ParsedArgs {
    assertInstanceOf(ParseResult.Ok::class.java, parseResult)
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
