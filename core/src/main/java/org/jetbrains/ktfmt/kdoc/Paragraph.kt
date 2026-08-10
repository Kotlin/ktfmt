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

class Paragraph(private val task: FormattingTask) {
  private val options: KDocFormattingOptions
    get() = task.options

  var content: StringBuilder = StringBuilder()
  val text: String
    get() = content.toString()

  var prev: Paragraph? = null
  var next: Paragraph? = null

  /** If true, this paragraph should be preceded by a blank line. */
  var separate = false

  /**
   * If true, this paragraph is a continuation of the previous paragraph (so should be indented with
   * the hanging indent, including line 1)
   */
  var continuation = false

  /**
   * Whether this paragraph is allowed to be empty. Paragraphs are normally merged if this is not
   * set. This allows the line breaker to call [ParagraphListBuilder.newParagraph] repeatedly
   * without introducing more than one new paragraph. But for preformatted text we do want to be
   * able to express repeated blank lines.
   */
  var allowEmpty = false

  /** Is this paragraph preformatted? */
  var preformatted = false

  /** Is this paragraph a block paragraph? If so, it must start on its own line. */
  var block = false

  /** Is this paragraph specifying a kdoc tag like @param? */
  var doc = false

  /** The quote depth of this paragraph. 0 = not quoted, 1 = `>`, 2 = `> >`, etc. */
  var quoted = 0

  /** Is this line part of a table? */
  var table = false

  /** Is this a separator line? */
  var separator = false

  /** Should this paragraph use a hanging indent? (Implies [block] as well). */
  var hanging = false
    set(value) {
      block = true
      field = value
    }

  var originalIndent = 0

  // The indent to use for all lines in the paragraph.
  var indent = ""

  // The indent to use for all lines in the paragraph if [hanging] is true,
  // or the second and subsequent lines if [hanging] is false
  var hangingIndent = ""

  fun isEmpty(): Boolean {
    return content.isEmpty()
  }

  fun cleanup() {
    val original = text

    if (preformatted) {
      return
    }

    var s = original
    if (options.convertMarkup) {
      s = convertMarkup(text)
    }
    if (!options.allowParamBrackets) {
      s = rewriteParams(s)
    }

    if (s != original) {
      content.clear()
      content.append(s)
    }
  }

  private fun rewriteParams(s: String): String {
    var start = 0
    val length = s.length
    while (start < length && s[start].isWhitespace()) {
      start++
    }
    if (s.startsWith("@param", start)) {
      start += "@param".length
      while (start < length && s[start].isWhitespace()) {
        start++
      }
      if (start < length && s[start++] == '[') {
        while (start < length && s[start].isWhitespace()) {
          start++
        }
        var end = start
        while (end < length && s[end].isJavaIdentifierPart()) {
          end++
        }
        if (end > start) {
          val name = s.substring(start, end)
          while (end < length && s[end].isWhitespace()) {
            end++
          }
          if (end < length && s[end++] == ']') {
            while (end < length && s[end].isWhitespace()) {
              end++
            }
            return "@param $name ${s.substring(end)}"
          }
        }
      }
    }

    return s
  }

  private fun convertMarkup(s: String): String {
    // Whether the tag starts with a capital letter and needs to be cleaned, e.g. `@See` -> `@see`.
    // (isKDocTag only allows the first letter to be capitalized.)
    val convertKDocTag = s.isKDocTag() && s[1].isUpperCase()

    if (!convertKDocTag && s.none { it == '<' || it == '&' || it == '{' }) {
      return s
    }

    val sb = StringBuilder(s.length)
    var i = 0
    val n = s.length

    if (convertKDocTag) {
      sb.append('@').append(s[1].lowercaseChar())
      i += 2
    }

    var code = false
    var brackets = 0
    while (i < n) {
      val c = s[i++]
      if (c == '\\') {
        sb.append(c)
        if (i < n - 1) {
          sb.append(s[i++])
        }
        continue
      } else if (c == '`') {
        code = !code
        sb.append(c)
        continue
      } else if (c == '[') {
        brackets++
        sb.append(c)
        continue
      } else if (c == ']') {
        brackets--
        sb.append(c)
        continue
      } else if (code || brackets > 0) {
        sb.append(c)
        continue
      } else if (c == '<') {
        if (s.startsWith("b>", i, false) || s.startsWith("/b>", i, false)) {
          // "<b>" or </b> -> "**"
          sb.append('*').append('*')
          if (s[i] == '/') i++
          i += 2
          continue
        }
        if (s.startsWith("i>", i, false) || s.startsWith("/i>", i, false)) {
          // "<i>" or </i> -> "*"
          sb.append('*')
          if (s[i] == '/') i++
          i += 2
          continue
        }
        if (s.startsWith("em>", i, false) || s.startsWith("/em>", i, false)) {
          // "<em>" or </em> -> "_"
          sb.append('_')
          if (s[i] == '/') i++
          i += 3
          continue
        }
        // (We don't convert <pre> here because those tags appear in paragraphs
        // marked preformatted, and preformatted paragraphs are never passed to
        // convertTags)
      } else if (c == '&') {
        if (s.startsWith("lt;", i, true)) { // "&lt;" -> "<"
          sb.append('<')
          i += 3
          continue
        }
        if (s.startsWith("gt;", i, true)) { // "&gt;" -> ">"
          sb.append('>')
          i += 3
          continue
        }
      } else if (c == '{') {
        if (s.startsWith("@param", i, true)) {
          val curr = i + 6
          var end = s.indexOf('}', curr)
          if (end == -1) {
            end = n
          }
          sb.append('[')
          sb.append(s.substring(curr, end).trim())
          sb.append(']')
          i = end + 1
          continue
        } else if (
            s.startsWith("@link", i, true)
            // @linkplain is similar to @link, but kdoc does *not* render a [symbol]
            // into a {@linkplain} in HTML, so converting these would change the output.
            && !s.startsWith("@linkplain", i, true)
        ) {
          // {@link} or {@linkplain}
          sb.append('[')
          var curr = i + 5
          while (curr < n) {
            val ch = s[curr++]
            if (ch.isWhitespace()) {
              break
            }
            if (ch == '}') {
              curr--
              break
            }
          }
          var skip = false
          while (curr < n) {
            val ch = s[curr]
            if (ch == '}') {
              sb.append(']')
              curr++
              break
            } else if (ch == '(') {
              skip = true
            } else if (!skip) {
              if (ch == '#') {
                if (!sb.endsWith('[')) {
                  sb.append('.')
                }
              } else {
                sb.append(ch)
              }
            }
            curr++
          }
          i = curr
          continue
        }
      }
      sb.append(c)
    }

    return sb.toString()
  }

  fun reflow(firstLineMaxWidth: Int, maxLineWidth: Int): List<String> {
    val lineWidth = maxLineWidth - getIndentSize(indent, options)
    val hangingIndentSize =
        getIndentSize(hangingIndent, options) - if (quoted > 0) 2 * quoted else 0
    if (text.length < (firstLineMaxWidth - hangingIndentSize)) {
      return listOf(text.collapseSpaces())
    }
    // Split text into words
    val words: List<String> = computeWords()

    // See divide & conquer algorithm listed here: https://xxyxyz.org/line-breaking/
    if (words.size == 1) {
      return listOf(words[0])
    }

    if (firstLineMaxWidth < maxLineWidth) {
      // We have ragged text. We'll just greedily place the first
      // words on the first line, and then optimize the rest.
      val line = StringBuilder()
      val firstLineWidth = firstLineMaxWidth - getIndentSize(indent, options)
      for (i in words.indices) {
        val word = words[i]
        if (line.isEmpty()) {
          if (word.length + task.type.lineOverhead() > firstLineMaxWidth) {
            // can't fit anything on the first line: just flow to
            // full width and caller will need to insert comment on
            // the next line.
            return reflow(words, lineWidth, hangingIndentSize)
          }
          line.append(word)
        } else if (line.length + word.length + 1 <= firstLineWidth) {
          line.append(' ')
          line.append(word)
        } else {
          // Break the rest
          val remainingWords = words.subList(i, words.size)
          val reflownRemaining = reflow(remainingWords, lineWidth, hangingIndentSize)
          return listOf(line.toString()) + reflownRemaining
        }
      }
      // We fit everything on the first line
      return listOf(line.toString())
    }

    return reflow(words, lineWidth, hangingIndentSize)
  }

  private fun reflow(words: List<String>, lineWidth: Int, hangingIndentSize: Int): List<String> {
    if (
        options.alternate ||
            !options.optimal ||
            (hanging && hangingIndentSize > 0) ||
            // An unbreakable long word may make other lines shorter and won't look good
            words.any { it.length > lineWidth }
    ) {
      // Switch to greedy if explicitly turned on, and for hanging indent
      // paragraphs, since the current implementation doesn't have support
      // for a different maximum length on the first line from the rest
      // and there were various cases where this ended up with bad results.
      // This is typically used in list items (and kdoc sections) which tend
      // to be short -- and for 2-3 lines the gains of optimal line breaking
      // isn't worth the cases where we have really unbalanced looking text
      return reflowGreedy(lineWidth, options, words)
    }

    val lines = reflowOptimal(lineWidth - hangingIndentSize, words)
    if (lines.size <= 2) {
      // Just 2 lines? We prefer long+short instead of half+half.
      return reflowGreedy(lineWidth, options, words)
    } else {
      // We could just return [lines] here, but the straightforward algorithm
      // doesn't do a great job with short paragraphs where the last line is
      // short; it over-corrects and shortens everything else in order to balance
      // out the last line.

      val maxLine: (String) -> Int = {
        // Ignore lines that are unbreakable
        if (it.indexOf(' ') == -1) {
          0
        } else {
          it.length
        }
      }
      val longestLine = lines.maxOf(maxLine)
      var lastWord = words.size - 1
      while (lastWord > 0) {
        // We can afford to do this because we're only repeating it for a single
        // line's worth of words and because comments tend to be relatively short
        // anyway
        val newLines = reflowOptimal(lineWidth - hangingIndentSize, words.subList(0, lastWord))
        if (newLines.size < lines.size) {
          val newLongestLine = newLines.maxOf(maxLine)
          if (
              newLongestLine > longestLine &&
                  newLines.subList(0, newLines.size - 1).any { it.length > longestLine }
          ) {
            return newLines +
                reflowGreedy(
                    lineWidth - hangingIndentSize,
                    options,
                    words.subList(lastWord, words.size),
                )
          }
          break
        }
        lastWord--
      }

      return lines
    }
  }

  /**
   * Returns true if it's okay to break at the current word.
   *
   * We need to check for this, because a word can have a different meaning at the beginning of a
   * line than in the middle somewhere, so if it just so happens to be at the break boundary, we
   * need to make sure we don't make it the first word on the next line since that would change the
   * documentation.
   */
  private fun canBreakAt(prev: String, word: String): Boolean {
    // Can we start a new line with this without interpreting it in a special
    // way?

    if (
        word.startsWith("#") ||
            word.startsWith("```") ||
            word.isDirectiveMarker() ||
            word.startsWith("@") || // interpreted as a tag
            word.isTodo() ||
            word.startsWith(">")
    ) {
      return false
    }

    if (prev == "@sample") {
      return false // https://github.com/Kotlin/ktfmt/issues/310
    }

    if (!word.first().isLetter()) {
      val wordWithSpace = "$word " // for regex matching in below checks
      if ((wordWithSpace.isListItem() && !word.equals("<li>", true)) || wordWithSpace.isQuoted()) {
        return false
      }
    }

    return true
  }

  /**
   * Split [text] up into individual "words"; in the case where some words are not allowed to span
   * lines, it will combine these into single word. For example, if we have a sentence which ends
   * with a number, e.g. "the sum is 5.", we want to make sure "5." is never placed at the beginning
   * of a new line (which would turn it into a list item), so for this we'll compute the word list
   * "the", "sum", "is 5.".
   */
  fun computeWords(): List<String> {
    val words = text.split(Regex("\\s+")).filter { it.isNotBlank() }.map { it.trim() }
    if (words.size == 1) {
      return words
    }

    if (task.type != CommentType.KDOC) {
      // In block comments and line comments we feel free to break anywhere
      // between words; there isn't a special meaning assigned to certain words
      // if they appear first on a line like there is in KDoc/Markdown.
      return words
    }

    // See if any of the words should never be broken up. We do that for list
    // separators and a few others. We never want to put "1." at the beginning
    // of a line as an overflow.

    val combined = ArrayList<String>(words.size)

    var from = 0
    val end = words.size
    while (from < end) {
      val start =
          if (from == 0 && (quoted > 0 || (hanging && !text.isKDocTag()))) {
            from + 2
          } else {
            from + 1
          }
      var to = words.size
      for (i in start until words.size) {
        val next = words[i]
        if (next.startsWith("[") && !next.startsWith("[[")) {
          // find end
          var j = -1
          for (k in i until words.size) {
            if (']' in words[k]) {
              j = k
              break
            }
          }
          if (j != -1) {
            // combine everything in the string; we can't break link text or @sample tags
            if (start == from + 1 && canBreakAt(words[start - 1], words[start])) {
              combined.add(words[from])
              from = start
            }
            // Maybe not break; what if the next word isn't okay?
            to = j + 1
            if (to == words.size || canBreakAt(words[to - 1], words[to])) {
              break
            }
          } // else: unterminated [, ignore
        } else if (canBreakAt(words[i - 1], next)) {
          to = i
          break
        }
      }

      if (to == from + 1) {
        combined.add(words[from])
      } else if (to > from) {
        combined.add(words.subList(from, to).joinToString(" "))
      }
      from = to
    }

    return combined
  }

  private data class Quadruple(val i0: Int, val j0: Int, val i1: Int, val j1: Int)

  private fun reflowOptimal(maxLineWidth: Int, words: List<String>): List<String> {
    val count = words.size
    val lines = ArrayList<String>()

    val offsets = ArrayList<Int>()
    offsets.add(0)

    for (boxWidth in words.map { it.length }.toList()) {
      offsets.add(offsets.last() + min(boxWidth, maxLineWidth))
    }

    val big = 10 shl 20
    val minimum = IntArray(count + 1) { big }
    val breaks = IntArray(count + 1)
    minimum[0] = 0

    fun cost(i: Int, j: Int): Int {
      val width = offsets[j] - offsets[i] + j - i - 1
      return if (width <= maxLineWidth) {
        val squared = (maxLineWidth - width) * (maxLineWidth - width)
        minimum[i] + squared
      } else {
        big
      }
    }

    fun search(pi0: Int, pj0: Int, pi1: Int, pj1: Int) {
      val stack = ArrayDeque<Quadruple>()
      stack.add(Quadruple(pi0, pj0, pi1, pj1))

      while (stack.isNotEmpty()) {
        val (i0, j0, i1, j1) = stack.removeLast()
        if (j0 < j1) {
          val j = (j0 + j1) / 2

          for (i in i0 until i1) {
            val c = cost(i, j)
            if (c <= minimum[j]) {
              minimum[j] = c
              breaks[j] = i
            }
          }
          stack.add(Quadruple(breaks[j], j + 1, i1, j1))
          stack.add(Quadruple(i0, j0, breaks[j] + 1, j))
        }
      }
    }

    var n = count + 1
    var i = 0
    var offset = 0

    while (true) {
      val r = min(n, 1 shl (i + 1))
      val edge = (1 shl i) + offset
      search(0 + offset, edge, edge, r + offset)
      val x = minimum[r - 1 + offset]
      var flag = true
      for (j in (1 shl i) until (r - 1)) {
        val y = cost(j + offset, r - 1 + offset)
        if (y <= x) {
          n -= j
          i = 0
          offset += j
          flag = false
          break
        }
      }
      if (flag) {
        if (r == n) break
        i++
      }
    }

    var j = count
    while (j > 0) {
      i = breaks[j]
      val sb = StringBuilder()
      for (w in i until j) {
        sb.append(words[w])
        if (w < j - 1) {
          sb.append(' ')
        }
      }
      lines.add(sb.toString())
      j = i
    }

    lines.reverse()
    return lines
  }

  private fun reflowGreedy(
      lineWidth: Int,
      options: KDocFormattingOptions,
      words: List<String>,
  ): List<String> {
    // Greedy implementation

    var width = lineWidth
    if (options.hangingIndent > 0 && hanging && continuation) {
      width -= getIndentSize(hangingIndent, options)
    }

    val lines = mutableListOf<String>()
    var column = 0
    val sb = StringBuilder()
    for (word in words) {
      when {
        sb.isEmpty() -> {
          sb.append(word)
          column += word.length
        }
        column + word.length + 1 <= width -> {
          sb.append(' ').append(word)
          column += word.length + 1
        }
        else -> {
          width = lineWidth
          if (options.hangingIndent > 0 && hanging) {
            width -= getIndentSize(hangingIndent, options)
          }
          lines.add(sb.toString())
          sb.setLength(0)
          sb.append(word)
          column = sb.length
        }
      }
    }
    if (sb.isNotEmpty()) {
      lines.add(sb.toString())
    }
    return lines
  }

  override fun toString(): String {
    return "$content, separate=$separate, block=$block, hanging=$hanging, preformatted=$preformatted, quoted=$quoted, continuation=$continuation, allowempty=$allowEmpty, separator=$separator"
  }
}
