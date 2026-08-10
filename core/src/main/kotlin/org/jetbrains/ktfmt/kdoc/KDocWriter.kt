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
 * Copyright 2016 Google Inc.
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

import com.google.common.base.Strings
import com.google.common.collect.Ordering
import com.google.common.collect.Sets.immutableEnumSet
import org.jetbrains.ktfmt.kdoc.KDocToken.Type.CODE_BLOCK_MARKER
import org.jetbrains.ktfmt.kdoc.KDocToken.Type.HEADER_OPEN_TAG
import org.jetbrains.ktfmt.kdoc.KDocToken.Type.LIST_ITEM_OPEN_TAG
import org.jetbrains.ktfmt.kdoc.KDocToken.Type.PARAGRAPH_OPEN_TAG
import org.jetbrains.ktfmt.kdoc.KDocWriter.RequestedWhitespace.BLANK_LINE
import org.jetbrains.ktfmt.kdoc.KDocWriter.RequestedWhitespace.CONDITIONAL_WHITESPACE
import org.jetbrains.ktfmt.kdoc.KDocWriter.RequestedWhitespace.NEWLINE
import org.jetbrains.ktfmt.kdoc.KDocWriter.RequestedWhitespace.NONE
import org.jetbrains.ktfmt.kdoc.KDocWriter.RequestedWhitespace.WHITESPACE

/**
 * Stateful object that accepts "requests" and "writes," producing formatted Javadoc.
 *
 * Our Javadoc formatter doesn't ever generate a parse tree, only a stream of tokens, so the writer
 * must compute and store the answer to questions like "How many levels of nested HTML list are we
 * inside?"
 */
internal class KDocWriter(blockIndentCount: Int, private val maxLineLength: Int) {

  /**
   * Tokens that are always pinned to the following token. For example, `<p>` in `<p>Foo bar` (never
   * `<p> Foo bar` or `<p>\nFoo bar`).
   *
   * This is not the only kind of "pinning" that we do: See also the joining of LITERAL tokens done
   * by the lexer. The special pinning here is necessary because these tokens are not of type
   * LITERAL (because they require other special handling).
   */
  private val START_OF_LINE_TOKENS =
      immutableEnumSet(LIST_ITEM_OPEN_TAG, PARAGRAPH_OPEN_TAG, HEADER_OPEN_TAG)

  private val output = StringBuilder()
  private val blockIndent = Strings.repeat(" ", blockIndentCount + 1)

  private var remainingOnLine: Int = 0
  private var atStartOfLine: Boolean = false
  private var inCodeBlock: Boolean = false
  private var requestedWhitespace = NONE

  /**
   * Requests whitespace between the previously written token and the next written token. The
   * request may be honored, or it may be overridden by a request for "more significant" whitespace,
   * like a newline.
   */
  fun requestWhitespace() {
    requestWhitespace(WHITESPACE)
  }

  fun writeBeginJavadoc() {
    /*
     * JavaCommentsHelper will make sure this is indented right. But it seems sensible enough that,
     * if our input starts with ∕✱✱, so too does our output.
     */
    appendTrackingLength("/**")
  }

  fun writeEndJavadoc() {
    requestCloseCodeBlockMarker()
    output.append("\n")
    appendTrackingLength(blockIndent)
    appendTrackingLength("*/")
  }

  fun writeListItemOpen(token: KDocToken) {
    requestCloseCodeBlockMarker()
    requestNewline()
    writeToken(token)
  }

  fun writePreOpen(token: KDocToken) {
    requestBlankLine()

    writeToken(token)
  }

  fun writePreClose(token: KDocToken) {
    writeToken(token)

    requestBlankLine()
  }

  fun writeCodeOpen(token: KDocToken) {
    writeToken(token)
  }

  fun writeCodeClose(token: KDocToken) {
    writeToken(token)
  }

  fun writeTableOpen(token: KDocToken) {
    requestBlankLine()

    writeToken(token)
  }

  fun writeTableClose(token: KDocToken) {
    writeToken(token)

    requestBlankLine()
  }

  fun writeTag(token: KDocToken) {
    requestNewline()
    writeToken(token)
  }

  fun writeCodeLine(token: KDocToken) {
    requestOpenCodeBlockMarker()
    requestNewline()
    if (token.value.isNotEmpty()) {
      writeToken(token)
    }
  }

  /** Adds a code block marker if we are not in a code block currently */
  private fun requestCloseCodeBlockMarker() {
    if (inCodeBlock) {
      this.requestedWhitespace = NEWLINE
      writeExplicitCodeBlockMarker(KDocToken(CODE_BLOCK_MARKER, "```"))
    }
  }

  /** Adds a code block marker if we are in a code block currently */
  private fun requestOpenCodeBlockMarker() {
    if (!inCodeBlock) {
      this.requestedWhitespace = NEWLINE
      writeExplicitCodeBlockMarker(KDocToken(CODE_BLOCK_MARKER, "```"))
    }
  }

  fun writeExplicitCodeBlockMarker(token: KDocToken) {
    requestNewline()
    writeToken(token)
    requestNewline()
    inCodeBlock = !inCodeBlock
  }

  fun writeLiteral(token: KDocToken) {
    requestCloseCodeBlockMarker()

    writeToken(token)
  }

  fun writeMarkdownLink(token: KDocToken) {
    writeToken(token)
  }

  override fun toString(): String {
    return output.toString()
  }

  fun requestBlankLine() {
    requestWhitespace(BLANK_LINE)
  }

  fun requestNewline() {
    requestWhitespace(NEWLINE)
  }

  private fun requestWhitespace(requestedWhitespace: RequestedWhitespace) {
    this.requestedWhitespace =
        Ordering.natural<Comparable<*>>().max(requestedWhitespace, this.requestedWhitespace)
  }

  /**
   * The kind of whitespace that has been requested between the previous and next tokens. The order
   * of the values is significant: It goes from lowest priority to highest. For example, if the
   * previous token requests [.BLANK_LINE] after it but the next token requests only [ ][.NEWLINE]
   * before it, we insert [.BLANK_LINE].
   */
  internal enum class RequestedWhitespace {
    NONE,

    /**
     * Add one space, only if the next token seems like a word In contrast, punctuation like a dot
     * does need a space before it.
     */
    CONDITIONAL_WHITESPACE,

    /** Add one space, e.g. " " */
    WHITESPACE,

    /** Break to the next line */
    NEWLINE,

    /** Add a whole blank line between the two lines of content */
    BLANK_LINE,
  }

  private fun writeToken(token: KDocToken) {
    if (requestedWhitespace == BLANK_LINE) {
      writeBlankLine()
      requestedWhitespace = NONE
    } else if (requestedWhitespace == NEWLINE) {
      writeNewline()
      requestedWhitespace = NONE
    }

    val needWhitespace =
        when (requestedWhitespace) {
          WHITESPACE -> true
          CONDITIONAL_WHITESPACE -> token.value.first().isLetterOrDigit()
          else -> false
        }
    /*
     * Write a newline if necessary to respect the line limit. (But if we're at the beginning of the
     * line, a newline won't help. Or it might help but only by separating "<p>veryverylongword,"
     * which goes against our style.)
     */
    if (!atStartOfLine && token.length() + (if (needWhitespace) 1 else 0) > remainingOnLine) {
      writeNewline()
    }
    if (!atStartOfLine && needWhitespace) {
      appendTrackingLength(" ")
    }

    appendTrackingLength(token.value)
    requestedWhitespace = NONE

    if (!START_OF_LINE_TOKENS.contains(token.type)) {
      atStartOfLine = false
    }
  }

  private fun writeBlankLine() {
    output.append("\n")
    appendTrackingLength(blockIndent)
    appendTrackingLength("*")
    writeNewline()
  }

  private fun writeNewline() {
    output.append("\n")
    remainingOnLine = maxLineLength
    appendTrackingLength(blockIndent)
    appendTrackingLength("* ")
    atStartOfLine = true
  }

  /*
   * TODO(cpovirk): We really want the number of "characters," not chars. Figure out what the
   * right way of measuring that is (grapheme count (with BreakIterator?)? sum of widths of all
   * graphemes? I don't think that our style guide is specific about this.). Moreover, I am
   * probably brushing other problems with surrogates, etc. under the table. Hopefully I mostly
   * get away with it by joining all non-space, non-tab characters together.
   *
   * Possibly the "width" question has no right answer:
   * http://denisbider.blogspot.com/2015/09/when-monospace-fonts-arent-unicode.html
   */
  private fun appendTrackingLength(str: String) {
    output.append(str)
    remainingOnLine -= str.length
  }
}
