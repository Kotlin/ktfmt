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

package org.jetbrains.ktfmt.format

import java.util.regex.Pattern
import java.util.regex.Pattern.MULTILINE

object WhitespaceTombstones {
  /** See [replaceTrailingWhitespaceWithTombstone]. */
  const val SPACE_TOMBSTONE = '\u0003'
  private val TRAILING_WHITESPACE_PATTERN = Pattern.compile(" ($)", MULTILINE)

  fun String.indexOfWhitespaceTombstone(): Int = this.indexOf(SPACE_TOMBSTONE)

  /**
   * Google-java-format removes trailing spaces when it emits formatted code, which is a problem for
   * multiline string literals. We trick it by replacing the last trailing space in such cases with
   * a tombstone, a character that's unlikely to be used in a regular program. After formatting, we
   * replace it back to a space.
   */
  fun replaceTrailingWhitespaceWithTombstone(s: String): String {
    return TRAILING_WHITESPACE_PATTERN.matcher(s).replaceAll("$SPACE_TOMBSTONE$1")
  }

  /** See [replaceTrailingWhitespaceWithTombstone]. */
  fun replaceTombstoneWithTrailingWhitespace(s: String): String {
    return s.replace(SPACE_TOMBSTONE, ' ')
  }
}
