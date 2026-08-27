package org.jetbrains.ktfmt.format

import com.google.common.collect.Range
import com.google.common.collect.RangeSet
import com.google.common.collect.TreeRangeSet
import com.google.googlejavaformat.Newlines
import java.io.File
import java.io.InputStream
import org.jetbrains.kotlin.com.intellij.openapi.util.text.StringUtil
import org.jetbrains.kotlin.com.intellij.openapi.util.text.StringUtilRt.convertLineSeparators
import org.jetbrains.ktfmt.format.WhitespaceTombstones.indexOfWhitespaceTombstone
import org.jetbrains.ktfmt.kdoc.Escaping

private const val UTF8_BOM = "\uFEFF"

enum class FileType(val extension: String) {
  REGULAR("kt"),
  SCRIPT("kts"),
}

val File.kotlinFileType: FileType
  get() =
      when (extension) {
        FileType.REGULAR.extension -> FileType.REGULAR
        FileType.SCRIPT.extension -> FileType.SCRIPT
        else -> throw IllegalArgumentException("Unsupported file type: $extension")
      }

fun KotlinCode(file: File): KotlinCode = KotlinCode(file.inputStream(), file.kotlinFileType)

fun KotlinCode(input: InputStream, fileType: FileType): KotlinCode =
    KotlinCode(input.bufferedReader().readText().removePrefix(UTF8_BOM), fileType)

fun KotlinCode(code: String, fileType: FileType): KotlinCode = KotlinCode.from(code, fileType)

/**
 * Represents a Kotlin source code file for the formatter
 *
 * @property code source code with normalized line separators (everything is `'\n'`)
 * @property fileType represents whether this is a script or a regular file for the parser
 * @property lineSeparator original line separator used in the source code
 */
class KotlinCode
private constructor(
    val code: String,
    val fileType: FileType,
    val lineSeparator: String,
) {
  fun copy(code: String) = KotlinCode(code, fileType, lineSeparator)

  override fun toString(): String = code.lineSequence().joinToString(separator = lineSeparator)

  fun lineRangesToCharRanges(lineRanges: RangeSet<Int>): RangeSet<Int> {
    val lineOffsets = buildList {
      Newlines.lineOffsetIterator(code).forEach { add(it) }
      add(code.length + 1)
    }

    val characterRanges = TreeRangeSet.create<Int>()
    for (lineRange in
        lineRanges.subRangeSet(Range.closedOpen(0, lineOffsets.size - 1)).asRanges()) {
      val lineStart = lineOffsets[lineRange.lowerEndpoint()]
      val lineEnd = lineOffsets[lineRange.upperEndpoint()] - 1
      val characterRange = Range.closedOpen(lineStart, lineEnd)
      if (!characterRange.isEmpty) {
        characterRanges.add(characterRange)
      }
    }
    return characterRanges
  }

  companion object {
    fun from(code: String, fileType: FileType): KotlinCode {
      checkEscapeSequences(code)
      val originalLineSeparator = Newlines.guessLineSeparator(code)

      val normalizedCode = convertLineSeparators(code)
      return KotlinCode(normalizedCode, fileType, originalLineSeparator)
    }
  }
}

private fun checkEscapeSequences(code: String) {
  var index = code.indexOfWhitespaceTombstone()
  if (index == -1) {
    index = Escaping.indexOfCommentEscapeSequences(code)
  }
  if (index != -1) {
    throw ParseError(
        "ktfmt does not support code which contains one of {\\u0003, \\u0004, \\u0005} character" +
            "; escape it",
        StringUtil.offsetToLineColumn(code, index),
    )
  }
}
