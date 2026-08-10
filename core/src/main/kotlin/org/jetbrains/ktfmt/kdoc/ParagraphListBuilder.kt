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

class ParagraphListBuilder(
    comment: String,
    private val options: KDocFormattingOptions,
    private val task: FormattingTask,
) {
  private val lineComment: Boolean = comment.isLineComment()
  private val commentPrefix: String =
      if (lineComment) "//" else if (comment.isKDocComment()) "/**" else "/*"
  private val paragraphs: MutableList<Paragraph> = mutableListOf()
  private val lines =
      if (lineComment) {
        comment.split("\n").map { it.trimStart() }
      } else if (!comment.contains("\n")) {
        listOf("* ${comment.removePrefix(commentPrefix).removeSuffix("*/").trim()}")
      } else {
        comment.removePrefix(commentPrefix).removeSuffix("*/").trim().split("\n")
      }

  private fun lineContent(line: String): String {
    val trimmed = line.trim()
    return when {
      lineComment && trimmed.startsWith("// ") -> trimmed.substring(3)
      lineComment && trimmed.startsWith("//") -> trimmed.substring(2)
      trimmed.startsWith("* ") -> trimmed.substring(2)
      trimmed.startsWith("*") -> trimmed.substring(1)
      else -> trimmed
    }
  }

  private fun closeParagraph(): Paragraph {
    val text = paragraph.text
    when {
      paragraph.preformatted -> {}
      text.isKDocTag() -> {
        paragraph.doc = true
        paragraph.hanging = true
      }
      text.isTodo() -> {
        paragraph.hanging = true
      }
      text.isListItem() -> paragraph.hanging = true
      text.isDirectiveMarker() -> {
        paragraph.block = true
        paragraph.preformatted = true
      }
    }
    if (!paragraph.isEmpty() || paragraph.allowEmpty) {
      paragraphs.add(paragraph)
    }
    return paragraph
  }

  private fun newParagraph(): Paragraph {
    closeParagraph()
    val prev = paragraph
    paragraph = Paragraph(task)
    prev.next = paragraph
    paragraph.prev = prev
    return paragraph
  }

  private var paragraph = Paragraph(task)

  private fun appendText(s: String): ParagraphListBuilder {
    paragraph.content.append(s)
    return this
  }

  private fun addLines(
      i: Int,
      includeEnd: Boolean = true,
      until: (Int, String, String) -> Boolean = { _, _, _ -> true },
      customize: (Int, Paragraph) -> Unit = { _, _ -> },
      shouldBreak: (String, String) -> Boolean = { _, _ -> false },
      separator: String = " ",
  ): Int {
    var j = i
    while (j < lines.size) {
      val l = lines[j]
      val lineWithIndentation = lineContent(l)
      val lineWithoutIndentation = lineWithIndentation.trim()

      if (!includeEnd) {
        if (j > i && until(j, lineWithoutIndentation, lineWithIndentation)) {
          stripTrailingBlankLines()
          return j
        }
      }

      if (shouldBreak(lineWithoutIndentation, lineWithIndentation)) {
        newParagraph()
      }

      if (lineWithIndentation.isQuoted()) {
        appendText(lineWithoutIndentation.substring(2).collapseSpaces())
      } else {
        appendText(lineWithoutIndentation.collapseSpaces())
      }
      appendText(separator)
      customize(j, paragraph)
      if (includeEnd) {
        if (j > i && until(j, lineWithoutIndentation, lineWithIndentation)) {
          stripTrailingBlankLines()
          return j + 1
        }
      }

      j++
    }

    stripTrailingBlankLines()
    newParagraph()

    return j
  }

  private fun addPreformatted(
      i: Int,
      includeStart: Boolean = false,
      includeEnd: Boolean = true,
      expectClose: Boolean = false,
      customize: (Int, Paragraph) -> Unit = { _, _ -> },
      until: (String) -> Boolean = { true },
  ): Int {
    newParagraph()
    var j = i
    var foundClose = false
    var allowCustomize = true
    while (j < lines.size) {
      val l = lines[j]
      val lineWithIndentation = lineContent(l)
      if (
          lineWithIndentation.contains("```") && lineWithIndentation.trimStart().startsWith("```")
      ) {
        // Don't convert <pre> tags if we already have nested ``` content; that will lead to
        // trouble
        allowCustomize = false
      }
      val done = (includeStart || j > i) && until(lineWithIndentation)
      if (!includeEnd && done) {
        foundClose = true
        break
      }
      j++
      if (includeEnd && done) {
        foundClose = true
        break
      }
    }

    // Don't convert if there's already a mixture

    // We ran out of lines. This means we had an unterminated preformatted
    // block. This is unexpected(unless it was an indented block) and most
    // likely a documentation error (even Dokka will start formatting return
    // value documentation in preformatted text if you have an opening <pre>
    // without a closing </pre> before a @return comment), but try to backpedal
    // a bit such that we don't apply full preformatted treatment everywhere to
    // things like line breaking.
    if (!foundClose && expectClose) {
      // Just add a single line as preformatted and then treat the rest in the
      // normal way
      allowCustomize = false
      j = lines.size
    }

    for (index in i until j) {
      val l = lines[index]
      val lineWithIndentation = lineContent(l)
      appendText(lineWithIndentation)
      paragraph.preformatted = true
      paragraph.allowEmpty = true
      if (allowCustomize) {
        customize(index, paragraph)
      }
      newParagraph()
    }
    stripTrailingBlankLines()
    newParagraph()

    return j
  }

  private fun stripTrailingBlankLines() {
    for (p in paragraphs.size - 1 downTo 0) {
      val paragraph = paragraphs[p]
      if (!paragraph.isEmpty()) {
        break
      }
      paragraphs.removeAt(p)
    }
  }

  fun scan(indentSize: Int): ParagraphList {
    var i = 0
    while (i < lines.size) {
      val l = lines[i++]
      val lineWithIndentation = lineContent(l)
      val lineWithoutIndentation = lineWithIndentation.trim()

      fun newParagraph(i: Int): Paragraph {
        val paragraph = this.newParagraph()

        if (i >= 0 && i < lines.size) {
          if (lines[i] == l) {
            paragraph.originalIndent = lineWithIndentation.length - lineWithoutIndentation.length
          } else {
            // We've looked ahead, e.g. when adding lists etc
            val line = lineContent(lines[i])
            val trimmed = line.trim()
            paragraph.originalIndent = line.length - trimmed.length
          }
        }
        return paragraph
      }

      if (
          lineWithIndentation.startsWith("    ") && // markdown preformatted text
              (i == 1 || lineContent(lines[i - 2]).isBlank()) && // we've already ++'ed i above
              // Make sure it's not just deeply indented inside a different block
              (paragraph.prev == null ||
                  lineWithIndentation.length - lineWithoutIndentation.length >=
                      checkNotNull(paragraph.prev).originalIndent + 4)
      ) {
        i = addPreformatted(i - 1, includeEnd = false, expectClose = false) { !it.startsWith(" ") }
      } else if (
          lineWithoutIndentation.startsWith("-") &&
              lineWithoutIndentation.containsOnly('-', '|', ' ')
      ) {
        val paragraph = newParagraph(i - 1)
        appendText(lineWithoutIndentation)
        newParagraph(i).block = true
        // Dividers must be surrounded by blank lines
        if (
            lineWithIndentation.isLine() &&
                (i < 2 || lineContent(lines[i - 2]).isBlank()) &&
                (i > lines.size - 1 || lineContent(lines[i]).isBlank())
        ) {
          paragraph.separator = true
        }
      } else if (
          lineWithoutIndentation.startsWith("=") && lineWithoutIndentation.containsOnly('=', ' ')
      ) {
        // Header
        // ======
        newParagraph(i - 1).block = true
        appendText(lineWithoutIndentation)
        newParagraph(i).block = true
      } else if (
          lineWithoutIndentation.startsWith("#")
          // "## X" is a header, "##X" is not
          && lineWithoutIndentation.firstOrNull { it != '#' }?.equals(' ') == true
      ) { // not isHeader() because <h> is handled separately
        // ## Header
        newParagraph(i - 1).block = true
        appendText(lineWithoutIndentation)
        newParagraph(i).block = true
      } else if (
          lineWithoutIndentation.startsWith("*") && lineWithoutIndentation.containsOnly('*', ' ')
      ) {
        // Horizontal rule:
        // *******
        // * * *
        // Unlike --- lines, these aren't required to be preceded by or followed by
        // blank lines.
        newParagraph(i - 1).block = true
        appendText(lineWithoutIndentation)
        newParagraph(i).block = true
      } else if (lineWithoutIndentation.startsWith("```")) {
        i = addPreformatted(i - 1, expectClose = true) { it.trimStart().startsWith("```") }
      } else if (lineWithoutIndentation.startsWith("<pre>", ignoreCase = true)) {
        i =
            addPreformatted(
                i - 1,
                includeStart = true,
                expectClose = true,
                customize = { _, _ ->
                  if (options.convertMarkup) {
                    fun handleTag(tag: String) {
                      val text = paragraph.text
                      val trimmed = text.trim()

                      val index = text.indexOf(tag, ignoreCase = true)
                      if (index == -1) {
                        return
                      }
                      paragraph.content.clear()
                      if (trimmed.equals(tag, ignoreCase = true)) {
                        paragraph.content.append("```")
                        return
                      }

                      // Split paragraphs; these things have to be on their own line
                      // in the ``` form (unless both are in the middle)
                      val before = text.substring(0, index).replace("</code>", "", true).trim()
                      if (before.isNotBlank()) {
                        paragraph.content.append(before)
                        newParagraph()
                        paragraph.preformatted = true
                        paragraph.allowEmpty = true
                      }
                      appendText("```")
                      val after =
                          text.substring(index + tag.length).replace("<code>", "", true).trim()
                      if (after.isNotBlank()) {
                        newParagraph()
                        appendText(after)
                        paragraph.preformatted = true
                        paragraph.allowEmpty = true
                      }
                    }

                    handleTag("<pre>")
                    handleTag("</pre>")
                  }
                },
                until = { it.contains("</pre>", ignoreCase = true) },
            )
      } else if (lineWithoutIndentation.isQuoted()) {
        val hadBlankLine = this.paragraph.isEmpty() && this.paragraph.separate
        i--

        // Process quoted block line by line to handle inner structure
        // (list items, nested quotes)
        var prevDepth = 0
        var isFirst = true

        while (i < lines.size) {
          val ql = lines[i]
          val qLineContent = lineContent(ql)
          val qTrimmed = qLineContent.trim()

          // Check termination conditions
          if (
              qTrimmed.isBlank() ||
                  qTrimmed.isKDocTag() ||
                  qTrimmed.isTodo() ||
                  qTrimmed.isDirectiveMarker() ||
                  qTrimmed.isHeader()
          ) {
            break
          }

          // Non-quoted lines that are list items should end the quoted block
          if (!qTrimmed.isQuoted() && qTrimmed.isListItem()) {
            break
          }

          // Compute quote depth and inner content
          val depth: Int
          val inner: String
          if (qTrimmed.isQuoted()) {
            var d = 0
            var s = qTrimmed
            while (s.startsWith("> ")) {
              d++
              s = s.substring(2).trimStart()
            }
            depth = d
            inner = s
          } else {
            // Continuation line without `> ` prefix - inherits previous depth
            depth = prevDepth
            inner = qTrimmed
          }

          // Decide if we need a new paragraph
          val needsBreak = !isFirst && (depth != prevDepth || inner.isListItem())

          if (isFirst || needsBreak) {
            val p = newParagraph(i)
            if (isFirst && hadBlankLine) {
              p.separate = true
            }
            p.quoted = depth
            p.block = false
          }

          appendText(inner.collapseSpaces())
          appendText(" ")

          prevDepth = depth
          isFirst = false
          i++
        }

        newParagraph(i)
      } else if (
          lineWithoutIndentation.equals("<ul>", true) || lineWithoutIndentation.equals("<ol>", true)
      ) {
        newParagraph(i - 1).block = true
        appendText(lineWithoutIndentation)
        newParagraph(i).hanging = true
        i =
            addLines(
                i,
                includeEnd = true,
                until = { _, w, _ -> w.equals("</ul>", true) || w.equals("</ol>", true) },
                customize = { _, p -> p.block = true },
                shouldBreak = { w, _ ->
                  w.startsWith("<li>", true) ||
                      w.startsWith("</ul>", true) ||
                      w.startsWith("</ol>", true)
                },
            )
        newParagraph(i)
      } else if (
          lineWithoutIndentation.isListItem() ||
              (lineWithoutIndentation.isKDocTag() && task.type == CommentType.KDOC) ||
              lineWithoutIndentation.isTodo()
      ) {
        val hadBlankLine =
            paragraph.isEmpty() && paragraph.separate && lineWithoutIndentation.isListItem()
        i--
        newParagraph(i).hanging = true
        if (hadBlankLine) {
          paragraph.separate = true
        }
        val start = i
        val listItemUntil = { j: Int, w: String, s: String ->
          // See if it's a line continuation
          if (s.isBlank() && j < lines.size - 1 && lineContent(lines[j + 1]).startsWith(" ")) {
            false
          } else {
            s.isBlank() ||
                w.isListItem() ||
                w.isQuoted() ||
                w.isKDocTag() ||
                w.isTodo() ||
                w.startsWith("```") ||
                w.startsWith("<pre>") ||
                w.isDirectiveMarker() ||
                w.isLine() ||
                w.isHeader() ||
                // Not indented by at least two spaces following a blank line?
                (s.length > 2 &&
                    (!s[0].isWhitespace() || !s[1].isWhitespace()) &&
                    j < lines.size - 1 &&
                    lineContent(lines[j - 1]).isBlank())
          }
        }
        val listItemShouldBreak = { w: String, _: String -> w.isBlank() }
        val listItemCustomize = { j: Int, p: Paragraph ->
          if (lineContent(lines[j]).isBlank() && j >= start) {
            p.hanging = true
            p.continuation = true
          }
        }
        i =
            addLines(
                i,
                includeEnd = false,
                until = listItemUntil,
                shouldBreak = listItemShouldBreak,
                customize = listItemCustomize,
            )
        // Handle fenced code blocks within list items
        while (i < lines.size && lineContent(lines[i]).trim().startsWith("```")) {
          i = addPreformatted(i, expectClose = true) { it.trimStart().startsWith("```") }
          // Check if list item continues after the code block
          if (i >= lines.size) break
          val nextLine = lineContent(lines[i])
          val nextTrimmed = nextLine.trim()
          // Continue if next content is indented continuation text or a blank line
          // followed by indented text (standard list item continuation pattern)
          val isContinuation =
              if (nextTrimmed.isBlank()) {
                i + 1 < lines.size && lineContent(lines[i + 1]).startsWith(" ")
              } else {
                nextLine.startsWith(" ") &&
                    !nextTrimmed.isListItem() &&
                    !nextTrimmed.isQuoted() &&
                    !nextTrimmed.isKDocTag() &&
                    !nextTrimmed.isTodo() &&
                    !nextTrimmed.isDirectiveMarker() &&
                    !nextTrimmed.isLine() &&
                    !nextTrimmed.isHeader()
              }
          if (!isContinuation) break
          // Set up continuation paragraph and continue processing
          newParagraph(i).apply {
            hanging = true
            continuation = true
          }
          i =
              addLines(
                  i,
                  includeEnd = false,
                  until = listItemUntil,
                  shouldBreak = listItemShouldBreak,
                  customize = listItemCustomize,
              )
        }
        newParagraph(i)
      } else if (lineWithoutIndentation.isEmpty()) {
        newParagraph(i).separate = true
      } else if (lineWithoutIndentation.isDirectiveMarker()) {
        newParagraph(i - 1)
        appendText(lineWithoutIndentation)
        newParagraph(i).block = true
      } else {
        if (
            lineWithoutIndentation.indexOf('|') != -1 &&
                paragraph.isEmpty() &&
                (i < 2 || !lines[i - 2].contains("---"))
        ) {
          val result = Table.getTable(lines, i - 1, ::lineContent)
          if (result != null) {
            val (table, nextRow) = result
            val content =
                if (options.alignTableColumns) {
                  // Only considering maxLineWidth here, not maxCommentWidth; we
                  // cannot break table lines, only adjust tabbing, and a padded table
                  // seems more readable (maxCommentWidth < maxLineWidth is there to
                  // prevent long lines for readability)
                  table.format(options.maxLineWidth - indentSize - 3)
                } else {
                  table.original()
                }
            for (index in content.indices) {
              val line = content[index]
              appendText(line)
              paragraph.separate = index == 0
              paragraph.block = true
              paragraph.table = true
              newParagraph(-1)
            }
            i = nextRow
            newParagraph(i)
            continue
          }
        }

        // Some common HTML block tags
        if (
            lineWithoutIndentation.startsWith("<") &&
                (lineWithoutIndentation.startsWith("<p>", true) ||
                    lineWithoutIndentation.startsWith("<p/>", true) ||
                    lineWithoutIndentation.startsWith("<h1", true) ||
                    lineWithoutIndentation.startsWith("<h2", true) ||
                    lineWithoutIndentation.startsWith("<h3", true) ||
                    lineWithoutIndentation.startsWith("<h4", true) ||
                    lineWithoutIndentation.startsWith("<table", true) ||
                    lineWithoutIndentation.startsWith("<tr", true) ||
                    lineWithoutIndentation.startsWith("<caption", true) ||
                    lineWithoutIndentation.startsWith("<td", true) ||
                    lineWithoutIndentation.startsWith("<div", true))
        ) {
          newParagraph(i - 1).block = true
          if (
              lineWithoutIndentation.equals("<p>", true) ||
                  lineWithoutIndentation.equals("<p/>", true) ||
                  (options.convertMarkup && lineWithoutIndentation.equals("</p>", true))
          ) {
            if (options.convertMarkup) {
              // Replace <p> with a blank line
              paragraph.separate = true
            } else {
              appendText(lineWithoutIndentation)
              newParagraph(i).block = true
            }
            continue
          } else if (
              lineWithoutIndentation.endsWith("</h1>", true) ||
                  lineWithoutIndentation.endsWith("</h2>", true) ||
                  lineWithoutIndentation.endsWith("</h3>", true) ||
                  lineWithoutIndentation.endsWith("</h4>", true)
          ) {
            if (
                lineWithoutIndentation.startsWith("<h", true) &&
                    options.convertMarkup &&
                    paragraph.isEmpty()
            ) {
              paragraph.separate = true
              val count = lineWithoutIndentation[lineWithoutIndentation.length - 2] - '0'
              for (j in 0 until count.coerceAtLeast(0).coerceAtMost(8)) {
                appendText("#")
              }
              appendText(" ")
              appendText(lineWithoutIndentation.substring(4, lineWithoutIndentation.length - 5))
            } else if (options.collapseSpaces) {
              appendText(lineWithoutIndentation.collapseSpaces())
            } else {
              appendText(lineWithoutIndentation)
            }
            newParagraph(i).block = true
            continue
          }
        }

        i = addPlainText(i, lineWithoutIndentation)
      }
    }

    closeParagraph()
    arrange()
    if (!lineComment) {
      punctuate()
    }

    return ParagraphList(paragraphs)
  }

  private fun convertPrefix(text: String): String {
    return if (
        options.convertMarkup && (text.startsWith("<p>", true) || text.startsWith("<p/>", true))
    ) {
      paragraph.separate = true
      text.substring(text.indexOf('>') + 1).trim()
    } else {
      text
    }
  }

  private fun convertSuffix(trimmedPrefix: String): String {
    return if (
        options.convertMarkup &&
            (trimmedPrefix.endsWith("<p/>", true) || (trimmedPrefix.endsWith("</p>", true)))
    ) {
      trimmedPrefix.substring(0, trimmedPrefix.length - 4).trimEnd().removeSuffix("*").trimEnd()
    } else {
      trimmedPrefix
    }
  }

  private fun addPlainText(i: Int, text: String, braceBalance: Int = 0): Int {
    val trimmed = convertSuffix(convertPrefix(text))
    val s = trimmed.let { if (options.collapseSpaces) it.collapseSpaces() else it }
    appendText(s)
    appendText(" ")

    if (braceBalance > 0) {
      val end = s.indexOf('}')
      if (end == -1 && i < lines.size) {
        val next = lineContent(lines[i]).trim()
        if (breakOutOfTag(next)) {
          return i
        }
        return addPlainText(i + 1, next, 1)
      }
    }

    val index = s.indexOf("{@")
    if (index != -1) {
      // find end
      val end = s.indexOf('}', index)
      if (end == -1 && i < lines.size) {
        val next = lineContent(lines[i]).trim()
        if (breakOutOfTag(next)) {
          return i
        }
        return addPlainText(i + 1, next, 1)
      }
    }

    return i
  }

  private fun breakOutOfTag(next: String): Boolean {
    if (next.isBlank() || next.startsWith("```")) {
      // See https://github.com/tnorbye/kdoc-formatter/issues/77
      // There may be comments which look unusual from a formatting
      // perspective where it looks like you have embedded markup
      // or blank lines; if so, just give up on trying to turn
      // this into paragraph text
      return true
    }
    return false
  }

  private fun docTagRank(tag: String, isPriority: Boolean): Int {
    // Canonical kdoc order -- https://kotlinlang.org/docs/kotlin-doc.html#block-tags
    // Full list in Dokka's sources: plugins/base/src/main/kotlin/parsers/Parser.kt
    return when {
      isPriority -> -1
      tag.startsWith("@param") -> 0
      tag.startsWith("@property") -> 0
      // @param and @property must be sorted by parameter order
      // a @property is dedicated syntax for a main constructor @param that also sets a class
      // property
      tag.startsWith("@return") -> 1
      tag.startsWith("@constructor") -> 2
      tag.startsWith("@receiver") -> 3
      tag.startsWith("@throws") -> 4
      tag.startsWith("@exception") -> 5
      tag.startsWith("@sample") -> 6
      tag.startsWith("@see") -> 7
      tag.startsWith("@author") -> 8
      tag.startsWith("@since") -> 9
      tag.startsWith("@suppress") -> 10
      tag.startsWith("@deprecated") -> 11
      else -> 100 // custom tags
    }
  }

  /**
   * Tags that are "priority" are placed before other tags, with their order unchanged.
   *
   * Note that if a priority tag comes after a regular tag (before formatting), it doesn't get
   * treated as priority.
   *
   * See: https://github.com/Kotlin/ktfmt/issues/406
   */
  private fun docTagIsPriority(tag: String): Boolean {
    return when {
      tag.startsWith("@sample") -> true
      else -> false
    }
  }

  /**
   * Make a pass over the paragraphs and make sure that we (for example) place blank lines around
   * preformatted text.
   */
  private fun arrange() {
    if (paragraphs.isEmpty()) {
      return
    }

    sortDocTags()
    adjustParagraphSeparators()
    adjustIndentation()
    removeBlankParagraphs()
    stripTrailingBlankLines()
  }

  private fun sortDocTags() {
    if (options.orderDocTags && paragraphs.any { it.doc }) {
      val order = paragraphs.mapIndexed { index, paragraph -> paragraph to index }.toMap()
      val firstNonPriorityDocTag = paragraphs.indexOfFirst { it.doc && !docTagIsPriority(it.text) }
      val comparator =
          object : Comparator<List<Paragraph>> {
            override fun compare(l1: List<Paragraph>, l2: List<Paragraph>): Int {
              val p1 = l1.first()
              val p2 = l2.first()
              val o1 = checkNotNull(order[p1])
              val o2 = checkNotNull(order[p2])
              val isPriority1 = p1.doc && docTagIsPriority(p1.text) && o1 < firstNonPriorityDocTag
              val isPriority2 = p2.doc && docTagIsPriority(p2.text) && o2 < firstNonPriorityDocTag

              // Sort TODOs to the end
              if (p1.text.isTodo() != p2.text.isTodo()) {
                return if (p1.text.isTodo()) 1 else -1
              }

              if (p1.doc == p2.doc) {
                if (p1.doc) {
                  // Sort @return after @param etc
                  val r1 = docTagRank(p1.text, isPriority1)
                  val r2 = docTagRank(p2.text, isPriority2)
                  if (r1 != r2) {
                    return r1 - r2
                  }
                  // Within identical tags, preserve current order, except for
                  // parameter names which are sorted by signature order.
                  val orderedParameterNames = task.orderedParameterNames
                  if (orderedParameterNames.isNotEmpty()) {
                    fun Paragraph.parameterRank(): Int {
                      val name = text.getParamName()
                      if (name != null) {
                        val index = orderedParameterNames.indexOf(name)
                        if (index != -1) {
                          return index
                        }
                      }
                      return 1000
                    }

                    val i1 = p1.parameterRank()
                    val i2 = p2.parameterRank()

                    // If the parameter names are not matching, ignore.
                    if (i1 != i2) {
                      return i1 - i2
                    }
                  }
                }
                return o1 - o2
              }
              return if (p1.doc) 1 else -1
            }
          }

      // We don't sort the paragraphs list directly; we have to tie all the
      // paragraphs following a KDoc parameter to that paragraph (until the
      // next KDoc tag). So instead we create a list of lists -- consisting of
      // one list for each paragraph, though with a KDoc parameter it's a list
      // containing first the KDoc parameter paragraph and then all following
      // parameters. We then sort by just the first item in this list of list,
      // and then restore the paragraph list from the result.
      val units = mutableListOf<List<Paragraph>>()
      var tag: MutableList<Paragraph>? = null
      for (paragraph in paragraphs) {
        if (paragraph.doc) {
          tag = mutableListOf()
          units.add(tag)
        }
        if (tag != null && !paragraph.text.isTodo()) {
          tag.add(paragraph)
        } else {
          units.add(listOf(paragraph))
        }
      }
      units.sortWith(comparator)

      var prev: Paragraph? = null
      paragraphs.clear()
      for (paragraph in units.flatten()) {
        paragraphs.add(paragraph)
        prev?.next = paragraph
        paragraph.prev = prev
        prev = paragraph
      }
    }
  }

  private fun adjustParagraphSeparators() {
    var prev: Paragraph? = null

    for (paragraph in paragraphs) {
      paragraph.cleanup()
      val text = paragraph.text
      paragraph.separate =
          when {
            prev == null -> false
            paragraph.preformatted && prev.preformatted -> false
            paragraph.table ->
                paragraph.separate && (!prev.block || prev.text.isKDocTag() || prev.table)
            paragraph.separator || prev.separator -> true
            text.isLine(1) || prev.text.isLine(1) -> false
            paragraph.separate -> true
            // Don't separate kdoc tags, except for the first one
            paragraph.doc -> !prev.doc
            text.isDirectiveMarker() -> false
            text.isTodo() && !prev.text.isTodo() -> true
            text.isHeader() -> true
            // Set preformatted paragraphs off (but not <pre> tags where it's implicit)
            paragraph.preformatted ->
                !prev.preformatted &&
                    !text.startsWith("<pre", true) &&
                    (!text.trimStart().startsWith("```") || !prev.text.isExpectingMore())
            prev.preformatted && prev.text.startsWith("</pre>", true) -> false
            paragraph.continuation -> true
            paragraph.hanging -> false
            paragraph.quoted > 0 -> prev.quoted > 0 && paragraph.quoted == prev.quoted
            text.isHeader() -> true
            text.startsWith("<p>", true) || text.startsWith("<p/>", true) -> true
            else -> !paragraph.block && !paragraph.isEmpty()
          }

      if (paragraph.hanging) {
        if (paragraph.doc || text.startsWith("<li>", true) || text.isTodo()) {
          paragraph.hangingIndent = getIndent(options.hangingIndent)
        } else if (paragraph.continuation && paragraph.prev != null) {
          // Walk back through preformatted and empty predecessors to find the
          // original list item's hanging indent (e.g. when a code block
          // separates the continuation text from its list item).
          var source = checkNotNull(paragraph.prev)
          while ((source.preformatted || source.isEmpty()) && source.prev != null) {
            source = checkNotNull(source.prev)
          }
          paragraph.hangingIndent = source.hangingIndent
          // Dedent to match hanging indent
          val s = paragraph.text.trimStart()
          paragraph.content.clear()
          paragraph.content.append(s)
        } else {
          paragraph.hangingIndent = getIndent(text.indexOf(' ') + 1)
        }
      }
      prev = paragraph
    }
  }

  private fun adjustIndentation() {
    val firstIndent = paragraphs[0].originalIndent
    if (firstIndent > 0) {
      for (paragraph in paragraphs) {
        if (paragraph.originalIndent <= firstIndent) {
          paragraph.originalIndent = 0
        }
      }
    }

    // Handle nested lists
    var inList = paragraphs.firstOrNull()?.hanging ?: false
    var startIndent = 0
    var levels: MutableSet<Int>? = null
    for (i in 1 until paragraphs.size) {
      val paragraph = paragraphs[i]
      if (!inList) {
        if (paragraph.hanging) {
          inList = true
          startIndent = paragraph.originalIndent
        }
      } else {
        if (!paragraph.hanging) {
          inList = false
        } else {
          if (paragraph.originalIndent == startIndent) {
            paragraph.originalIndent = 0
          } else if (paragraph.originalIndent > 0) {
            (levels ?: mutableSetOf<Int>().also { levels = it }).add(paragraph.originalIndent)
          }
        }
      }
    }

    levels?.sorted()?.let { sorted ->
      val assignments = mutableMapOf<Int, Int>()
      for (i in sorted.indices) {
        assignments[sorted[i]] = (i + 1) * options.nestedListIndent
      }
      for (paragraph in paragraphs) {
        if (paragraph.originalIndent > 0) {
          val assigned = assignments[paragraph.originalIndent] ?: continue
          paragraph.originalIndent = assigned
          paragraph.indent = getIndent(paragraph.originalIndent)
        }
      }
    }
  }

  private fun removeBlankParagraphs() {
    // Remove blank lines between list items and from the end as well as around
    // separators
    for (i in paragraphs.size - 2 downTo 0) {
      if (paragraphs[i].isEmpty() && (!paragraphs[i].preformatted || i == paragraphs.size - 1)) {
        // Propagate blank-line intent to the next paragraph
        if (paragraphs[i].separate && i + 1 < paragraphs.size) {
          paragraphs[i + 1].separate = true
        }
        paragraphs.removeAt(i)
        if (i > 0) {
          paragraphs[i - 1].next = null
        }
      }
    }
  }

  private fun punctuate() {
    if (!options.addPunctuation || paragraphs.isEmpty()) {
      return
    }
    val last = paragraphs.last()
    if (last.preformatted || last.doc || (last.hanging && !last.continuation) || last.isEmpty()) {
      return
    }

    val text = last.content
    if (!text.startsWithUpperCaseLetter()) {
      return
    }

    for (i in text.length - 1 downTo 0) {
      val c = text[i]
      if (c.isWhitespace()) {
        continue
      }
      if (c.isLetterOrDigit() || c.isCloseSquareBracket()) {
        text.setLength(i + 1)
        text.append('.')
      }
      break
    }
  }
}

fun String.containsOnly(vararg s: Char): Boolean {
  for (c in this) {
    if (s.none { it == c }) {
      return false
    }
  }
  return true
}

fun StringBuilder.startsWithUpperCaseLetter(): Boolean =
    this.isNotEmpty() && this[0].isUpperCase() && this[0].isLetter()

fun Char.isCloseSquareBracket(): Boolean = this == ']'
