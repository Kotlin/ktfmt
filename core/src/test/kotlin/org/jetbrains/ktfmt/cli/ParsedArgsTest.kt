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

package org.jetbrains.ktfmt.cli

import com.google.common.collect.Range
import com.google.common.collect.RangeSet
import com.google.common.collect.TreeRangeSet
import java.io.FileNotFoundException
import kotlin.io.path.createTempDirectory
import org.jetbrains.ktfmt.format.Formatter
import org.jetbrains.ktfmt.format.FormattingOptions
import org.jetbrains.ktfmt.testutil.assertContains
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
  fun `parseOptions recognizes --range-start and --range-end`() {
    val parsed = assertSucceeds(parseOptions("--range-start=10", "--range-end=20", "foo.kt"))
    assertEquals(ranges(Range.closedOpen(10, 20)), parsed.characterRanges)
    assertTrue(parsed.isPartialFormatting)
  }

  @Test
  fun `parseOptions recognizes --range-start without --range-end`() {
    val parsed = assertSucceeds(parseOptions("--range-start=10", "foo.kt"))
    assertEquals(ranges(Range.closedOpen(10, Int.MAX_VALUE)), parsed.characterRanges)
    assertTrue(parsed.isPartialFormatting)
  }

  @Test
  fun `parseOptions recognizes --range-end without --range-start`() {
    val parsed = assertSucceeds(parseOptions("--range-end=20", "foo.kt"))
    assertEquals(ranges(Range.closedOpen(0, 20)), parsed.characterRanges)
    assertTrue(parsed.isPartialFormatting)
  }

  @Test
  fun `parseOptions recognizes --range-start and --range-end with space separated value`() {
    val parsed = assertSucceeds(parseOptions("--range-start", "15", "--range-end", "25", "foo.kt"))
    assertEquals(ranges(Range.closedOpen(15, 25)), parsed.characterRanges)
    assertTrue(parsed.isPartialFormatting)
  }

  @Test
  fun `parseOptions accepts --range-start equal to --range-end`() {
    val parsed = assertSucceeds(parseOptions("--range-start=10", "--range-end=10", "foo.kt"))
    assertTrue(parsed.characterRanges.isEmpty)
    assertTrue(parsed.isPartialFormatting)
  }

  @Test
  fun `parseOptions rejects --range-start without value`() {
    val parseResult = parseOptions("--range-start")
    assertEquals(
        ParseResult.Error("required value was not provided for: --range-start"),
        parseResult,
    )
  }

  @Test
  fun `parseOptions rejects --range-end without value`() {
    val parseResult = parseOptions("--range-end")
    assertEquals(
        ParseResult.Error("required value was not provided for: --range-end"),
        parseResult,
    )
  }

  @Test
  fun `parseOptions rejects invalid --range-start`() {
    val parseResult = parseOptions("--range-start=not-a-number", "foo.kt")
    assertEquals(
        ParseResult.Error("invalid integer value for --range-start: not-a-number"),
        parseResult,
    )
  }

  @Test
  fun `parseOptions rejects invalid --range-end`() {
    val parseResult = parseOptions("--range-end=not-a-number", "foo.kt")
    assertEquals(
        ParseResult.Error("invalid integer value for --range-end: not-a-number"),
        parseResult,
    )
  }

  @Test
  fun `parseOptions rejects negative --range-start`() {
    val parseResult = parseOptions("--range-start=-5", "foo.kt")
    assertEquals(
        ParseResult.Error("invalid integer value for --range-start: -5"),
        parseResult,
    )
  }

  @Test
  fun `parseOptions rejects negative --range-end`() {
    val parseResult = parseOptions("--range-end=-5", "foo.kt")
    assertEquals(
        ParseResult.Error("invalid integer value for --range-end: -5"),
        parseResult,
    )
  }

  @Test
  fun `parseOptions rejects --range-start greater than --range-end`() {
    val parseResult = parseOptions("--range-start=25", "--range-end=10", "foo.kt")
    assertEquals(
        ParseResult.Error("--range-start (25) cannot be greater than --range-end (10)"),
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
  fun `parseOptions rejects --range-start with multiple files`() {
    val parseResult = parseOptions("--range-start=10", "foo.kt", "bar.kt")
    assertEquals(
        ParseResult.Error("partial formatting is only supported for a single file"),
        parseResult,
    )
  }

  @Test
  fun `parseOptions rejects --range-end with multiple files`() {
    val parseResult = parseOptions("--range-end=10", "foo.kt", "bar.kt")
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

  @Test
  fun `parseOptions rejects --length without value`() {
    val parseResult = parseOptions("--length")
    assertEquals(
        ParseResult.Error("required value was not provided for: --length"),
        parseResult,
    )
  }

  @Test
  fun `parseOptions rejects invalid --length`() {
    val parseResult = parseOptions("--offset=0", "--length=not-a-number", "foo.kt")
    assertEquals(
        ParseResult.Error("invalid integer value for --length: not-a-number"),
        parseResult,
    )
  }

  @Test
  fun `parseOptions rejects unexpected option starting with --offset`() {
    val parseResult = parseOptions("--offset-start=10", "foo.kt")
    assertEquals(
        ParseResult.Error("Unexpected option: --offset-start"),
        parseResult,
    )
  }

  @Test
  fun `parseOptions rejects unexpected option starting with --length`() {
    val parseResult = parseOptions("--length-extra=10", "foo.kt")
    assertEquals(
        ParseResult.Error("Unexpected option: --length-extra"),
        parseResult,
    )
  }

  @Test
  fun `parseOptions rejects unexpected option starting with --range-start`() {
    val parseResult = parseOptions("--range-starter=10", "foo.kt")
    assertEquals(
        ParseResult.Error("Unexpected option: --range-starter"),
        parseResult,
    )
  }

  @Test
  fun `parseOptions rejects unexpected option starting with --range-end`() {
    val parseResult = parseOptions("--range-ender=10", "foo.kt")
    assertEquals(
        ParseResult.Error("Unexpected option: --range-ender"),
        parseResult,
    )
  }

  @Test
  fun `parseOptions rejects unexpected option starting with --line`() {
    val parseResult = parseOptions("--line-extra=10", "foo.kt")
    assertEquals(
        ParseResult.Error("Unexpected option: --line-extra"),
        parseResult,
    )
  }

  @Test
  fun `parseOptions rejects space separated --offset without value at end of args`() {
    val parseResult = parseOptions("--offset")
    assertEquals(
        ParseResult.Error("required value was not provided for: --offset"),
        parseResult,
    )
  }

  @Test
  fun `parseOptions rejects space separated --length without value at end of args`() {
    val parseResult = parseOptions("--offset", "10", "--length")
    assertEquals(
        ParseResult.Error("required value was not provided for: --length"),
        parseResult,
    )
  }

  @Test
  fun `parseOptions rejects empty value for --range-start`() {
    val parseResult = parseOptions("--range-start=", "foo.kt")
    assertEquals(
        ParseResult.Error("invalid integer value for --range-start: "),
        parseResult,
    )
  }

  @Test
  fun `parseOptions rejects empty value for --range-end`() {
    val parseResult = parseOptions("--range-end=", "foo.kt")
    assertEquals(
        ParseResult.Error("invalid integer value for --range-end: "),
        parseResult,
    )
  }

  @Test
  fun `parseOptions handles repeated --range-start and --range-end (last flag wins)`() {
    val parsed = assertSucceeds(
        parseOptions(
            "--range-start=5",
            "--range-start=15",
            "--range-end=50",
            "--range-end=25",
            "foo.kt",
        ),
    )
    assertEquals(ranges(Range.closedOpen(15, 25)), parsed.characterRanges)
    assertTrue(parsed.isPartialFormatting)
  }

  @Test
  fun `parseOptions combines --range-start and --range-end with --offset and --length`() {
    val parsed = assertSucceeds(
        parseOptions(
            "--range-start=10",
            "--range-end=20",
            "--offset=30",
            "--length=5",
            "foo.kt",
        ),
    )
    assertEquals(
        ranges(
            Range.closedOpen(10, 20),
            Range.closedOpen(30, 35),
        ),
        parsed.characterRanges,
    )
    assertTrue(parsed.isPartialFormatting)
  }

  @Test
  fun `parseOptions combines --range-start and --range-end with --lines`() {
    val parsed = assertSucceeds(
        parseOptions(
            "--range-start=10",
            "--range-end=20",
            "--lines=1:3",
            "foo.kt",
        ),
    )
    assertEquals(ranges(Range.closedOpen(10, 20)), parsed.characterRanges)
    assertEquals(ranges(Range.closedOpen(0, 3)), parsed.lineRanges)
    assertTrue(parsed.isPartialFormatting)
  }

  @Test
  fun `parseOptions rejects --range-start with no files provided`() {
    val parseResult = parseOptions("--range-start=10")
    assertEquals(
        ParseResult.Error("partial formatting is only supported for a single file"),
        parseResult,
    )
  }

  @Test
  fun `parseOptions rejects --range-end with no files provided`() {
    val parseResult = parseOptions("--range-end=10")
    assertEquals(
        ParseResult.Error("partial formatting is only supported for a single file"),
        parseResult,
    )
  }

  @Test
  fun `parseOptions rejects --range-start equal to --range-end with multiple files`() {
    val parseResult = parseOptions("--range-start=10", "--range-end=10", "foo.kt", "bar.kt")
    assertEquals(
        ParseResult.Error("partial formatting is only supported for a single file"),
        parseResult,
    )
  }

  @Test
  fun `processArgs parses --range-start and --range-end from argfile`() {
    val file = root.resolve("argfile")
    file.writeText("--range-start=10\n--range-end=20\nfoo.kt\n")

    val result = ParsedArgs.processArgs(arrayOf("@" + file.canonicalPath))
    assertInstanceOf(ParseResult.Ok::class.java, result)

    val parsed = (result as ParseResult.Ok).parsedValue
    assertEquals(ranges(Range.closedOpen(10, 20)), parsed.characterRanges)
    assertEquals(listOf("foo.kt"), parsed.fileNames)
    assertTrue(parsed.isPartialFormatting)
  }

  @Test
  fun `processArgs parses --lines, --offset, and --length from argfile`() {
    val file = root.resolve("argfile")
    file.writeText("--lines=1:5\n--offset=12\n--length=4\nfoo.kt\n")

    val result = ParsedArgs.processArgs(arrayOf("@" + file.canonicalPath))
    assertInstanceOf(ParseResult.Ok::class.java, result)

    val parsed = (result as ParseResult.Ok).parsedValue
    assertEquals(ranges(Range.closedOpen(0, 5)), parsed.lineRanges)
    assertEquals(ranges(Range.closedOpen(12, 16)), parsed.characterRanges)
    assertEquals(listOf("foo.kt"), parsed.fileNames)
    assertTrue(parsed.isPartialFormatting)
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
