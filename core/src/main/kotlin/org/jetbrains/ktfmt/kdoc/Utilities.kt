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

import java.util.regex.Pattern
import kotlin.math.min

fun getIndent(width: Int): String {
  val sb = StringBuilder()
  for (i in 0 until width) {
    sb.append(' ')
  }
  return sb.toString()
}

fun getIndentSize(indent: String, options: KDocFormattingOptions): Int {
  var size = 0
  for (c in indent) {
    if (c == '\t') {
      size += options.tabWidth
    } else {
      size++
    }
  }
  return size
}

/** Returns line number (1-based) */
fun getLineNumber(source: String, offset: Int, startLine: Int = 1, startOffset: Int = 0): Int {
  var line = startLine
  for (i in startOffset until offset) {
    val c = source[i]
    if (c == '\n') {
      line++
    }
  }
  return line
}

private val numberPattern = Pattern.compile("^\\d+([.)]) ")

fun String.isListItem(): Boolean {
  return startsWith("- ") ||
      startsWith("* ") ||
      startsWith("+ ") ||
      (firstOrNull()?.isDigit() == true && numberPattern.matcher(this).find()) ||
      startsWith("<li>", ignoreCase = true)
}

fun String.collapseSpaces(): String {
  if (indexOf("  ") == -1) {
    return this.trimEnd()
  }
  val sb = StringBuilder()
  var prev: Char = this[0]
  for (i in indices) {
    if (prev == ' ') {
      if (this[i] == ' ') {
        continue
      }
    }
    sb.append(this[i])
    prev = this[i]
  }
  return sb.trimEnd().toString()
}

fun String.isTodo(): Boolean {
  return startsWith("TODO:") || startsWith("TODO(")
}

fun String.isHeader(): Boolean {
  return startsWith("#") || startsWith("<h", true)
}

fun String.isQuoted(): Boolean {
  return startsWith("> ")
}

fun String.isDirectiveMarker(): Boolean {
  return startsWith("<!--") || startsWith("-->")
}

/**
 * Returns true if the string ends with a symbol that implies more text is coming, e.g. ":" or ","
 */
fun String.isExpectingMore(): Boolean {
  val last = lastOrNull { !it.isWhitespace() } ?: return false
  return last == ':' || last == ','
}

/**
 * Does this String represent a divider line? (Markdown also requires it to be surrounded by empty
 * lines which has to be checked by the caller)
 */
fun String.isLine(minCount: Int = 3): Boolean {
  return (startsWith('-') && containsOnly('-', ' ') && count { it == '-' } >= minCount) ||
      (startsWith('_') && containsOnly('_', ' ') && count { it == '_' } >= minCount)
}

fun String.isKDocTag(): Boolean {
  // Not using a hardcoded list here since tags can change over time
  if (startsWith("@") && length > 1) {
    for (i in 1 until length) {
      val c = this[i]
      if (c.isWhitespace()) {
        return i > 2
      } else if (!c.isLetter() || !c.isLowerCase()) {
        if (c == '[' && (startsWith("@param") || startsWith("@property"))) {
          // @param is allowed to use brackets -- see
          // https://kotlinlang.org/docs/kotlin-doc.html#param-name
          // Example: @param[foo] The description of foo
          return true
        } else if (i == 1 && c.isLetter() && c.isUpperCase()) {
          // Allow capitalized tgs, such as @See -- this is normally a typo; convertMarkup
          // should also fix these.
          return true
        }
        return false
      }
    }
    return true
  }
  return false
}

/**
 * If this String represents a KDoc tag named [tag], returns the corresponding parameter name,
 * otherwise null.
 */
fun String.getTagName(tag: String): String? {
  val length = this.length
  var start = 0
  while (start < length && this[start].isWhitespace()) {
    start++
  }
  if (!this.startsWith(tag, start)) {
    return null
  }
  start += tag.length

  while (start < length) {
    if (this[start].isWhitespace()) {
      start++
    } else {
      break
    }
  }

  if (start < length && this[start] == '[') {
    start++
    while (start < length) {
      if (this[start].isWhitespace()) {
        start++
      } else {
        break
      }
    }
  }

  var end = start
  while (end < length) {
    if (!this[end].isJavaIdentifierPart()) {
      break
    }
    end++
  }

  if (end > start) {
    return this.substring(start, end)
  }

  return null
}

/**
 * If this String represents a KDoc `@param` or `@property` tag, returns the corresponding parameter
 * name, otherwise null.
 */
fun String.getParamName(): String? = getTagName("@param") ?: getTagName("@property")

private fun getIndent(start: Int, lookup: (Int) -> Char): String {
  var i = start - 1
  while (i >= 0 && lookup(i) != '\n') {
    i--
  }
  val sb = StringBuilder()
  for (j in i + 1 until start) {
    sb.append(lookup(j))
  }
  return sb.toString()
}

/**
 * Given a character [lookup] function in a document of [max] characters, for a comment starting at
 * offset [start], compute the effective indent on the first line and on subsequent lines.
 *
 * For a comment starting on its own line, the two will be the same. But for a comment that is at
 * the end of a line containing code, the first line indent will not be the indentation of the
 * earlier code, it will be the full indent as if all the code characters were whitespace characters
 * (which lets the formatter figure out how much space is available on the first line).
 */
fun computeIndents(start: Int, lookup: (Int) -> Char, max: Int): Pair<String, String> {
  val originalIndent = getIndent(start, lookup)
  val suffix = !originalIndent.all { it.isWhitespace() }
  val indent =
      if (suffix) {
        originalIndent.map { if (it.isWhitespace()) it else ' ' }.joinToString(separator = "")
      } else {
        originalIndent
      }

  val secondaryIndent =
      if (suffix) {
        // We don't have great heuristics to figure out what the indent should be
        // following a source line -- e.g. it can be implied by things like whether
        // the line ends with '{' or an operator, but it's more complicated than
        // that. So we'll cheat and just look to see what the existing code does!
        var offset = start
        while (offset < max && lookup(offset) != '\n') {
          offset++
        }
        offset++
        val sb = StringBuilder()
        while (offset < max) {
          if (lookup(offset) == '\n') {
            sb.clear()
          } else {
            val c = lookup(offset)
            if (c.isWhitespace()) {
              sb.append(c)
            } else {
              if (c == '*') {
                // in a comment, the * is often one space indented
                // to line up with the first * in the opening /** and
                // the actual indent should be aligned with the /
                sb.setLength(sb.length - 1)
              }
              break
            }
          }
          offset++
        }
        sb.toString()
      } else {
        originalIndent
      }

  return Pair(indent, secondaryIndent)
}

/**
 * Attempt to preserve the caret position across reformatting. Returns the delta in the new comment.
 */
fun findSamePosition(comment: String, delta: Int, reformattedComment: String): Int {
  // First see if the two comments are identical up to the delta; if so, same
  // new position
  for (i in 0 until min(comment.length, reformattedComment.length)) {
    if (i == delta) {
      return delta
    } else if (comment[i] != reformattedComment[i]) {
      break
    }
  }

  var i = comment.length - 1
  var j = reformattedComment.length - 1
  if (delta == i + 1) {
    return j + 1
  }
  while (i >= 0 && j >= 0) {
    if (i == delta) {
      return j
    }
    if (comment[i] != reformattedComment[j]) {
      break
    }
    i--
    j--
  }

  fun isSignificantChar(c: Char): Boolean = c.isWhitespace() || c == '*'

  // Finally it's somewhere in the middle; search by character skipping over
  // insignificant characters (space, *, etc)
  fun nextSignificantChar(s: String, from: Int): Int {
    var curr = from
    while (curr < s.length) {
      val c = s[curr]
      if (isSignificantChar(c)) {
        curr++
      } else {
        break
      }
    }
    return curr
  }

  var offset = 0
  var reformattedOffset = 0
  while (offset < delta && reformattedOffset < reformattedComment.length) {
    offset = nextSignificantChar(comment, offset)
    reformattedOffset = nextSignificantChar(reformattedComment, reformattedOffset)
    if (offset == delta) {
      return reformattedOffset
    }
    offset++
    reformattedOffset++
  }
  return reformattedOffset
}

// Until stdlib version is no longer experimental
fun <T, R : Comparable<R>> Iterable<T>.maxOf(selector: (T) -> R): R {
  val iterator = iterator()
  if (!iterator.hasNext()) throw NoSuchElementException()
  var maxValue = selector(iterator.next())
  while (iterator.hasNext()) {
    val v = selector(iterator.next())
    if (maxValue < v) {
      maxValue = v
    }
  }
  return maxValue
}
