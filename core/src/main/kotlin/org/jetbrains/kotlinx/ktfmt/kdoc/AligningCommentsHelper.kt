package org.jetbrains.kotlinx.ktfmt.kdoc

import com.google.googlejavaformat.CommentsHelper
import com.google.googlejavaformat.Input
import com.google.googlejavaformat.Input.Tok
import com.google.googlejavaformat.Newlines

/** Aligns comments to the output column without reformatting their contents */
internal class AligningCommentsHelper(private val input: Input) : CommentsHelper {
  override fun rewrite(tok: Tok, maxWidth: Int, column0: Int): String {
    if (!tok.isComment) {
      return tok.originalText
    }
    return if (Newlines.containsBreaks(tok.originalText)) {
      shiftIndentation(tok.originalText, column0 - input.getColumnNumber(tok.position))
    } else {
      tok.originalText
    }
  }

  private fun shiftIndentation(text: String, shift: Int): String {
    if (shift == 0) return text
    val addedIndent = " ".repeat(shift.coerceAtLeast(0))
    return buildString {
      for ((index, line) in Newlines.lineIterator(text).withIndex()) {
        when {
          // first line is already aligned
          index == 0 -> append(line)
          line.isBlank() -> append(line)
          shift > 0 -> {
            append(addedIndent)
            append(line)
          }
          else -> {
            val currentIndent = line.indexOfFirst { !it.isWhitespace() }
            val extraIndentChars = minOf(-shift, currentIndent)
            append(line.drop(extraIndentChars))
          }
        }
      }
    }
  }
}
