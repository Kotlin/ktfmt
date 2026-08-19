/*
 * Portions Copyright (c) Meta Platforms, Inc. and affiliates.
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

/*
 * Copyright (c) Tor Norbye.
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

package org.jetbrains.ktfmt.kdoc

import kotlin.math.min

/** Formatter which can reformat KDoc comments. */
class KDocFormatter(private val options: KDocFormattingOptions) {
  /** Reformats the [comment], which follows the given [initialIndent] string. */
  fun reformatComment(comment: String, initialIndent: String): String {
    return reformatComment(FormattingTask(options, comment, initialIndent))
  }

  fun reformatComment(task: FormattingTask): String {
    val indent = task.secondaryIndent
    val indentSize = getIndentSize(indent, options)
    val firstIndentSize = getIndentSize(task.initialIndent, options)
    val comment = task.comment
    val lineComment = comment.isLineComment()
    val blockComment = comment.isBlockComment()
    val paragraphs = ParagraphListBuilder(comment, options, task).scan(indentSize)
    val commentType = task.type
    val lineSeparator = "\n$indent${commentType.linePrefix}"
    val prefix = commentType.prefix

    // Collapse single line? If alternate is turned on, use the opposite of the
    // setting
    val collapseLine = options.collapseSingleLine.let { if (options.alternate) !it else it }
    if (paragraphs.isSingleParagraph() && collapseLine && !lineComment) {
      // Does the text fit on a single line?
      val trimmed = paragraphs.firstOrNull()?.text?.trim() ?: ""
      // Subtract out space for "/** " and " */" and the indent:
      val width =
          min(
              options.maxLineWidth - firstIndentSize - commentType.singleLineOverhead(),
              options.maxCommentWidth,
          )
      val suffix = if (commentType.suffix.isEmpty()) "" else " ${commentType.suffix}"
      if (trimmed.length <= width) {
        return "$prefix $trimmed$suffix"
      }
      if (indentSize < firstIndentSize) {
        val nextLineWidth =
            min(
                options.maxLineWidth - indentSize - commentType.singleLineOverhead(),
                options.maxCommentWidth,
            )
        if (trimmed.length <= nextLineWidth) {
          return "$prefix $trimmed$suffix"
        }
      }
    }

    val sb = StringBuilder()

    sb.append(prefix)
    if (lineComment) {
      sb.append(' ')
    } else {
      sb.append(lineSeparator)
    }

    for (paragraph in paragraphs) {
      if (paragraph.separate) {
        // Remove trailing spaces which can happen when we have a paragraph
        // separator
        stripTrailingSpaces(lineComment, sb)
        sb.append(lineSeparator)
      }
      val text = paragraph.text
      if (paragraph.preformatted || paragraph.table) {
        sb.append(text)
        // Remove trailing spaces which can happen when we have an empty line in a
        // preformatted paragraph.
        stripTrailingSpaces(lineComment, sb)
        sb.append(lineSeparator)
        continue
      }

      val lineWithoutIndent = options.maxLineWidth - commentType.lineOverhead()
      val quoteAdjustment = if (paragraph.quoted > 0) 2 * paragraph.quoted else 0
      val maxLineWidth =
          min(options.maxCommentWidth, lineWithoutIndent - indentSize) - quoteAdjustment
      val firstMaxLineWidth =
          if (sb.indexOf('\n') == -1) {
            min(options.maxCommentWidth, lineWithoutIndent - firstIndentSize) - quoteAdjustment
          } else {
            maxLineWidth
          }

      val lines = paragraph.reflow(firstMaxLineWidth, maxLineWidth)
      var first = true
      val hangingIndent = paragraph.hangingIndent
      for (line in lines) {
        sb.append(paragraph.indent)
        if (first && !paragraph.continuation) {
          first = false
        } else {
          sb.append(hangingIndent)
        }
        if (paragraph.quoted > 0) {
          for (q in 0 until paragraph.quoted) {
            sb.append("> ")
          }
        }
        if (line.isEmpty()) {
          // Remove trailing spaces which can happen when we have a paragraph
          // separator
          stripTrailingSpaces(lineComment, sb)
        } else {
          sb.append(line)
        }
        sb.append(lineSeparator)
      }
    }
    if (!lineComment) {
      if (sb.endsWith("* ")) {
        sb.setLength(sb.length - 2)
      }
      sb.append("*/")
    } else if (sb.endsWith(lineSeparator)) {
      @Suppress("NoOp", "ReturnValueIgnored") sb.removeSuffix(lineSeparator)
    }

    val formatted =
        if (lineComment) {
          sb.trim().removeSuffix("//").trim().toString()
        } else if (blockComment) {
          sb.toString().replace(lineSeparator + "\n", "\n\n")
        } else {
          sb.toString()
        }

    val separatorIndex = comment.indexOf('\n')
    return if (separatorIndex > 0 && comment[separatorIndex - 1] == '\r') {
      // CRLF separator
      formatted.replace("\n", "\r\n")
    } else {
      formatted
    }
  }

  private fun stripTrailingSpaces(lineComment: Boolean, sb: StringBuilder) {
    if (!lineComment && sb.endsWith("* ")) {
      sb.setLength(sb.length - 1)
    } else if (lineComment && sb.endsWith("// ")) {
      sb.setLength(sb.length - 1)
    }
  }
}
