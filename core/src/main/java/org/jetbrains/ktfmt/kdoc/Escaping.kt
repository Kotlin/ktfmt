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

package org.jetbrains.ktfmt.kdoc

object Escaping {

  private const val SLASH_STAR_ESCAPE = "\u0004\u0005"

  private const val STAR_SLASH_ESCAPE = "\u0005\u0004"

  fun indexOfCommentEscapeSequences(s: String): Int =
      s.indexOfAny(listOf(SLASH_STAR_ESCAPE, STAR_SLASH_ESCAPE))

  /**
   * kotlin-compiler's KDoc lexer doesn't correctly handle nested slash-star comments, so we escape
   * them into tombstones, format, then unescape.
   */
  fun escapeKDoc(s: String): String {
    val startMarkerIndex = s.indexOf("/*")
    val endMarkerIndex = s.lastIndexOf("*/")

    if (startMarkerIndex == -1 || endMarkerIndex == -1) {
      throw RuntimeException("KDoc with no /** and/or */")
    }

    return s.substring(0, startMarkerIndex + 3) +
        s.substring(startMarkerIndex + 3, endMarkerIndex)
            .replace("/*", SLASH_STAR_ESCAPE)
            .replace("*/", STAR_SLASH_ESCAPE) +
        s.substring(endMarkerIndex)
  }

  /** See [escapeKDoc]. */
  fun unescapeKDoc(s: String): String =
      s.replace(SLASH_STAR_ESCAPE, "/*").replace(STAR_SLASH_ESCAPE, "*/")
}
