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

/** Options controlling how the [KDocFormatter] will behave. */
class KDocFormattingOptions(
    /** Right hand side margin to write lines at. */
    var maxLineWidth: Int = 72,
    /**
     * Limit comment to be at most [maxCommentWidth] characters even if more would fit on the line.
     */
    var maxCommentWidth: Int = maxLineWidth,
) {
  /** Whether to collapse multi-line comments that would fit on a single line into a single line. */
  var collapseSingleLine: Boolean = true

  /** Whether to collapse repeated spaces. */
  var collapseSpaces: Boolean = true

  /** Whether to convert basic markup like **bold** into **bold**, < into <, etc. */
  var convertMarkup: Boolean = true

  /**
   * Whether to add punctuation where missing, such as ending sentences with a period. (TODO: Make
   * sure the FIRST sentence ends with one too! Especially if the subsequent sentence is separated.)
   */
  var addPunctuation: Boolean = false

  /**
   * How many spaces to use for hanging indents in numbered lists and after block tags. Using 4 or
   * more here will result in subsequent lines being interpreted as block formatted by IntelliJ (but
   * not Dokka).
   */
  var hangingIndent: Int = 2

  /** When there are nested lists etc, how many spaces to indent by. */
  var nestedListIndent: Int = 3
    set(value) {
      if (value < 3) {
        error(
            "Nested list indent must be at least 3; if list items are only indented 2 spaces they " +
                "will not be rendered as list items",
        )
      }
      field = value
    }

  /**
   * Don't format with tabs! (See
   * https://kotlinlang.org/docs/reference/coding-conventions.html#formatting)
   *
   * But if you do, this is the tab width.
   */
  var tabWidth: Int = 8

  /** Whether to perform optimal line breaking instead of greeding. */
  var optimal: Boolean = true

  /**
   * If true, reformat markdown tables such that the column markers line up. When false, markdown
   * tables are left alone (except for left hand side cleanup.)
   */
  var alignTableColumns: Boolean = true

  /**
   * If true, moves any kdoc tags to the end of the comment and `@return` tags after `@param` tags.
   */
  var orderDocTags: Boolean = true

  /**
   * If true, perform "alternative" formatting. This is only relevant in the IDE. You can invoke the
   * action repeatedly and it will jump between normal formatting an alternative formatting. For
   * single-line comments it will alternate between single and multiple lines. For longer comments
   * it will alternate between optimal line breaking and greedy line breaking.
   */
  var alternate: Boolean = false

  /**
   * KDoc allows param tag to be specified using an alternate bracket syntax. KDoc formatter ties to
   * unify the format of comments, so it will rewrite them into the canonical syntax unless this
   * option is true.
   */
  var allowParamBrackets: Boolean = false

  /** Creates a copy of this formatting object. */
  fun copy(): KDocFormattingOptions {
    val copy = KDocFormattingOptions()
    copy.maxLineWidth = maxLineWidth
    copy.maxCommentWidth = maxCommentWidth
    copy.collapseSingleLine = collapseSingleLine
    copy.collapseSpaces = collapseSpaces
    copy.hangingIndent = hangingIndent
    copy.tabWidth = tabWidth
    copy.alignTableColumns = alignTableColumns
    copy.orderDocTags = orderDocTags
    copy.addPunctuation = addPunctuation
    copy.convertMarkup = convertMarkup
    copy.nestedListIndent = nestedListIndent
    copy.optimal = optimal
    copy.alternate = alternate

    return copy
  }
}
