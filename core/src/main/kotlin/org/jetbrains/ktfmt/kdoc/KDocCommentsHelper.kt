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
 * Copyright 2015 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

/*
 * This was copied from https://github.com/google/google-java-format and modified extensively to
 * work for Kotlin formatting
 */

package org.jetbrains.ktfmt.kdoc

import com.google.common.base.CharMatcher
import com.google.common.base.Strings
import com.google.googlejavaformat.CommentsHelper
import com.google.googlejavaformat.Input.Tok
import com.google.googlejavaformat.Newlines
import java.util.ArrayList
import java.util.regex.Pattern

/** `KDocCommentsHelper` extends [CommentsHelper] to rewrite KDoc comments. */
class KDocCommentsHelper(private val lineSeparator: String, private val maxLineLength: Int) :
    CommentsHelper {

  private val kdocFormatter = KDocFormatter(
      KDocFormattingOptions(maxLineLength, maxLineLength).apply {
        allowParamBrackets = true // TODO Do we want this?
        convertMarkup = false
        nestedListIndent = 4
        optimal = false // Use greedy line breaking for predictability.
      },
  )

  override fun rewrite(tok: Tok, maxWidth: Int, column0: Int): String {
    if (!tok.isComment) {
      return tok.originalText
    }
    var text = tok.originalText
    if (tok.isJavadocComment) {
      text = kdocFormatter.reformatComment(text, " ".repeat(column0))
    }
    val lines = ArrayList<String>()
    val it = Newlines.lineIterator(text)
    while (it.hasNext()) {
      lines.add(CharMatcher.whitespace().trimTrailingFrom(it.next()))
    }
    return if (tok.isSlashSlashComment) {
      indentLineComments(lines, column0)
    } else if (javadocShaped(lines)) {
      indentJavadoc(lines, column0)
    } else {
      preserveIndentation(lines, column0)
    }
  }

  // For non-javadoc-shaped block comments, shift the entire block to the correct
  // column, but do not adjust relative indentation.
  private fun preserveIndentation(lines: List<String>, column0: Int): String {
    val builder = StringBuilder()

    // find the leftmost non-whitespace character in all trailing lines
    var startCol = -1
    for (i in 1 until lines.size) {
      val lineIdx = CharMatcher.whitespace().negate().indexIn(lines[i])
      if (lineIdx >= 0 && (startCol == -1 || lineIdx < startCol)) {
        startCol = lineIdx
      }
    }

    // output the first line at the current column
    builder.append(lines[0])

    // output all trailing lines with plausible indentation
    for (i in 1 until lines.size) {
      builder.append(lineSeparator).append(Strings.repeat(" ", column0))
      // check that startCol is valid index, e.g. for blank lines
      if (lines[i].length >= startCol) {
        builder.append(lines[i].substring(startCol))
      } else {
        builder.append(lines[i])
      }
    }
    return builder.toString()
  }

  // Wraps and re-indents line comments.
  private fun indentLineComments(lines: List<String>, column0: Int): String {
    val wrappedLines = wrapLineComments(lines, column0)
    val builder = StringBuilder()
    builder.append(wrappedLines[0].trim())
    val indentString = Strings.repeat(" ", column0)
    for (i in 1 until wrappedLines.size) {
      builder.append(lineSeparator).append(indentString).append(wrappedLines[i].trim())
    }
    return builder.toString()
  }

  private fun wrapLineComments(lines: List<String>, column0: Int): List<String> {
    val result = ArrayList<String>()
    for (originalLine in lines) {
      var line = originalLine
      // Add missing leading spaces to line comments: `//foo` -> `// foo`.
      val matcher = LINE_COMMENT_MISSING_SPACE_PREFIX.matcher(line)
      if (matcher.find()) {
        val length = matcher.group(1).length
        line = Strings.repeat("/", length) + " " + line.substring(length)
      }
      if (line.startsWith("// MOE:")) {
        // don't wrap comments for https://github.com/google/MOE
        result.add(line)
        continue
      }
      while (line.length + column0 > maxLineLength) {
        var idx = maxLineLength - column0
        // only break on whitespace characters, and ignore the leading `// `
        while (idx >= 2 && !CharMatcher.whitespace().matches(line[idx])) {
          idx--
        }
        if (idx <= 2) {
          break
        }
        result.add(line.substring(0, idx))
        line = "//" + line.substring(idx)
      }
      result.add(line)
    }
    return result
  }

  // Remove leading whitespace (trailing was already removed), and re-indent.
  // Add a +1 indent before '*', and add the '*' if necessary.
  private fun indentJavadoc(lines: List<String>, column0: Int): String {
    val builder = StringBuilder()
    builder.append(lines[0].trim())
    val indent = column0 + 1
    val indentString = Strings.repeat(" ", indent)
    for (i in 1 until lines.size) {
      builder.append(lineSeparator).append(indentString)
      val line = lines[i].trim()
      if (!line.startsWith("*")) {
        builder.append("* ")
      }
      builder.append(line)
    }
    return builder.toString()
  }

  // Preserve special `//noinspection` and `//$NON-NLS-x$` comments used by IDEs, which cannot
  // contain leading spaces.
  private val LINE_COMMENT_MISSING_SPACE_PREFIX =
      Pattern.compile("^(//+)(?!noinspection|\\\$NON-NLS-\\d+\\$)[^\\s/]")

  // Returns true if the comment looks like javadoc
  private fun javadocShaped(lines: List<String>): Boolean {
    val it = lines.iterator()
    if (!it.hasNext()) {
      return false
    }
    val first = it.next().trim()
    // if it's actually javadoc, we're done
    if (first.startsWith("/**")) {
      return true
    }
    // if it's a block comment, check all trailing lines for '*'
    if (!first.startsWith("/*")) {
      return false
    }
    while (it.hasNext()) {
      if (!it.next().trim().startsWith("*")) {
        return false
      }
    }
    return true
  }
}
