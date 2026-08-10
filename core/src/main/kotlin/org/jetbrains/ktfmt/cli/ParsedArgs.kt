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
import java.io.File
import java.nio.charset.StandardCharsets.UTF_8
import org.jetbrains.ktfmt.format.Formatter
import org.jetbrains.ktfmt.format.FormattingOptions
import org.jetbrains.ktfmt.util.Ktfmt

/** ParsedArgs holds the arguments passed to ktfmt on the command-line, after parsing. */
data class ParsedArgs(
    val fileNames: List<String>,
    val formattingOptions: FormattingOptions,
    /**
     * Run the formatter without writing changes to any files. This will print the path of any files
     * that would be changed if the formatter is run normally.
     */
    val dryRun: Boolean,

    /** Return exit code 1 if any formatting changes are detected. */
    val setExitIfChanged: Boolean,
    /** File name to report when formating code from stdin */
    val stdinName: String?,
    val editorConfig: Boolean,
    /** Suppress all non-error output. */
    val quiet: Boolean,
) {
  /** Zero-indexed line ranges to format, using closed-open bounds, e.g. [0, 3) and [6, 7). */
  internal val lineRanges: RangeSet<Int> = TreeRangeSet.create()
  /** Zero-indexed character ranges to format, using closed-open bounds. */
  internal val characterRanges: RangeSet<Int> = TreeRangeSet.create()

  companion object {

    fun processArgs(args: Array<String>): ParseResult {
      val arguments =
          if (args.size == 1 && args[0].startsWith("@")) {
            File(args[0].substring(1)).readLines(UTF_8).toTypedArray()
          } else {
            args
          }
      return parseOptions(arguments)
    }

    val HELP_TEXT: String =
        """
        |ktfmt - command line Kotlin source code pretty-printer
        |
        |Usage:
        |  ktfmt [OPTIONS] <File1.kt> <File2.kt> ...
        |  ktfmt @ARGFILE
        |
        |ktfmt formats Kotlin source code files in-place, reporting for each file whether the
        |formatting succeeded or failed on standard error. If none of the style options are
        |passed, Meta's style is used.
        |
        |Alternatively, ktfmt can read Kotlin source code from standard input and write the 
        |formatted result on standard output.
        |
        |Example:
        |     $ ktfmt --kotlinlang-style Main.kt src/Parser.kt
        |     Done formatting Main.kt
        |     Error formatting src/Parser.kt: @@@ERROR@@@; skipping.
        |    
        |Commands options:
        |  -h, --help                        Show this help message
        |  -v, --version                     Show version
        |  -n, --dry-run                     Don't write to files, only report files which
        |                                        would have changed
        |  --meta-style                      Use 2-space block indenting (default)
        |  --google-style                    Google internal style (2 spaces)
        |  --kotlinlang-style                Kotlin language guidelines style (4 spaces)
        |  --stdin-name=<name>               Name to report when formatting code from stdin
        |  --lines=<lines>                   Line range(s) to format, like 5 or 1:12,14.
        |                                        May be used multiple times.
        |  --offset=<offset>                 Character offset to format, paired with --length.
        |                                        May be used multiple times.
        |  --length=<length>                 Character length to format, paired with --offset.
        |                                        May be used multiple times. 0 formats the whole
        |                                        line containing the given --offset.
        |  --set-exit-if-changed             Sets exit code to 1 if any input file was not
        |                                        formatted/touched
        |  --do-not-remove-unused-imports    Leaves all imports in place, even if not used
        |  --enable-editorconfig             Enable .editorconfig overrides for supported formatting options (limited)
        |                                        see https://github.com/Kotlin/ktfmt/blob/main/README.md
        |  --quiet                           Suppress all non-error output
        |
        |ARGFILE:
        |  If the only argument begins with '@', the remainder of the argument is treated
        |  as the name of a file to read options and arguments from, one per line.
        |  
        |  e.g.
        |      $ cat arg-file.txt
        |      --google-style
        |      -n
        |      File1.kt
        |      File2.kt
        |      $ ktfmt @arg-file1.txt
        |      Done formatting File1.kt
        |      Done formatting File2.kt
        |"""
            .trimMargin()

    /** parseOptions parses command-line arguments passed to ktfmt. */
    fun parseOptions(args: Array<out String>): ParseResult {
      val fileNames = mutableListOf<String>()
      var formattingOptions = Formatter.META_FORMAT
      var dryRun = false
      var setExitIfChanged = false
      var removeUnusedImports = true
      var stdinName: String? = null
      var editorConfig = false
      var quiet = false
      val lineRanges = TreeRangeSet.create<Int>()
      val offsets = mutableListOf<Int>()
      val lengths = mutableListOf<Int>()

      if ("--help" in args || "-h" in args) return ParseResult.ShowMessage(HELP_TEXT)
      if ("--version" in args || "-v" in args) {
        return ParseResult.ShowMessage("ktfmt version ${Ktfmt.version}")
      }

      var i = 0
      while (i < args.size) {
        val arg = args[i]
        val nextValue = {
          i++
          if (i == args.size) {
            null
          } else {
            args[i]
          }
        }
        when {
          arg == "--meta-style" -> formattingOptions = Formatter.META_FORMAT
          arg == "--google-style" -> formattingOptions = Formatter.GOOGLE_FORMAT
          arg == "--kotlinlang-style" -> formattingOptions = Formatter.KOTLINLANG_FORMAT
          arg == "--dry-run" || arg == "-n" -> dryRun = true
          arg == "--set-exit-if-changed" -> setExitIfChanged = true
          arg == "--do-not-remove-unused-imports" -> removeUnusedImports = false
          arg == "--enable-editorconfig" -> editorConfig = true
          arg == "--quiet" -> quiet = true
          arg.startsWith("--stdin-name=") ->
              stdinName =
                  parseKeyValueArg("--stdin-name", arg)
                      ?: return ParseResult.Error(
                          "Found option '${arg}', expected '${"--stdin-name"}=<value>'",
                      )
          arg.startsWith("--line") -> {
            val argSplit = arg.split('=', limit = 2)
            val key = argSplit.first()
            if (key != "--lines" && key != "--line") {
              return ParseResult.Error("Unexpected option: $key")
            }
            val value =
                if (argSplit.size > 1) {
                  argSplit.last()
                } else {
                  nextValue()
                      ?: return ParseResult.Error("required value was not provided for: $key")
                }
            when (val result = parseLineRanges(lineRanges, value)) {
              LineRangeParseResult.Success -> Unit
              is LineRangeParseResult.Error -> return ParseResult.Error(result.message)
            }
          }
          arg.startsWith("--offset") ->
              arg.split('=', limit = 2).let { argSplit ->
                val key = argSplit.first()
                if (key != "--offset") {
                  return ParseResult.Error("Unexpected option: $key")
                }
                val value =
                    if (argSplit.size > 1) {
                      argSplit.last()
                    } else {
                      nextValue()
                          ?: return ParseResult.Error("required value was not provided for: $key")
                    }
                offsets.add(
                    value.toIntOrNull()
                        ?: return ParseResult.Error("invalid integer value for $key: $value"),
                )
              }
          arg.startsWith("--length") ->
              arg.split('=', limit = 2).let { argSplit ->
                val key = argSplit.first()
                if (key != "--length") {
                  return ParseResult.Error("Unexpected option: $key")
                }
                val value =
                    if (argSplit.size > 1) {
                      argSplit.last()
                    } else {
                      nextValue()
                          ?: return ParseResult.Error("required value was not provided for: $key")
                    }
                lengths.add(
                    value.toIntOrNull()
                        ?: return ParseResult.Error("invalid integer value for $key: $value"),
                )
              }
          arg.startsWith("--") -> return ParseResult.Error("Unexpected option: $arg")
          arg.startsWith("@") -> return ParseResult.Error("Unexpected option: $arg")
          else -> fileNames.add(arg)
        }
        i++
      }

      if (fileNames.contains("-")) {
        // We're reading from stdin
        if (fileNames.size > 1) {
          val filesExceptStdin = fileNames - "-"
          return ParseResult.Error(
              "Cannot read from stdin and files in same run. Found stdin specifier '-'" +
                  " and files ${filesExceptStdin.joinToString(", ")} ",
          )
        }
      } else if (stdinName != null) {
        return ParseResult.Error("--stdin-name can only be specified when reading from stdin")
      }

      if (offsets.size != lengths.size) {
        return ParseResult.Error("--offset and --length flags must be provided in matching pairs")
      }

      val characterRanges = TreeRangeSet.create<Int>()
      for (index in offsets.indices) {
        val length = lengths[index].let { if (it == 0) 1 else it }
        characterRanges.add(Range.closedOpen(offsets[index], offsets[index] + length))
      }

      if ((!lineRanges.isEmpty || !characterRanges.isEmpty) && fileNames.size != 1) {
        return ParseResult.Error("partial formatting is only supported for a single file")
      }

      val parsedArgs = ParsedArgs(
          fileNames,
          formattingOptions.copy(removeUnusedImports = removeUnusedImports),
          dryRun,
          setExitIfChanged,
          stdinName,
          editorConfig,
          quiet,
      )
      parsedArgs.lineRanges.addAll(lineRanges)
      parsedArgs.characterRanges.addAll(characterRanges)
      return ParseResult.Ok(parsedArgs)
    }

    private fun parseKeyValueArg(key: String, arg: String): String? {
      val parts = arg.split('=', limit = 2)
      return parts[1].takeIf { parts[0] == key || parts.size == 2 }
    }

    private fun parseLineRanges(
        lineRanges: RangeSet<Int>,
        lineRangesArg: String?,
    ): LineRangeParseResult {
      if (lineRangesArg == null) {
        return LineRangeParseResult.Error("required value was not provided for: --lines")
      }
      return try {
        for (lineRange in lineRangesArg.split(',')) {
          lineRanges.add(parseLineRange(lineRange))
        }
        LineRangeParseResult.Success
      } catch (_: IllegalArgumentException) {
        LineRangeParseResult.Error("invalid line range for --lines: $lineRangesArg")
      }
    }

    private sealed interface LineRangeParseResult {
      data object Success : LineRangeParseResult

      @JvmInline value class Error(val message: String) : LineRangeParseResult
    }

    private fun parseLineRange(arg: String): Range<Int> {
      val parts = arg.split(':')
      return when (parts.size) {
        1 -> {
          val line = parts[0].toInt() - 1
          Range.closedOpen(line, line + 1)
        }
        2 -> {
          val line0 = parts[0].toInt() - 1
          val line1 = parts[1].toInt() - 1
          Range.closedOpen(line0, line1 + 1)
        }
        else -> throw IllegalArgumentException(arg)
      }
    }
  }
}

sealed interface ParseResult {
  data class Ok(val parsedValue: ParsedArgs) : ParseResult

  data class ShowMessage(val message: String) : ParseResult

  data class Error(val errorMessage: String) : ParseResult
}
