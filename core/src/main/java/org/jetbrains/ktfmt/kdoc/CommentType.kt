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

enum class CommentType(
    /** The opening string of the comment. */
    val prefix: String,
    /** The closing string of the comment. */
    val suffix: String,
    /** For multi line comments, the prefix at each comment line after the first one. */
    val linePrefix: String,
) {
  KDOC("/**", "*/", " * "),
  BLOCK("/*", "*/", ""),
  LINE("//", "", "// ");

  /**
   * The number of characters needed to fit a comment on a line: the prefix, suffix and a single
   * space padding inside these.
   */
  fun singleLineOverhead(): Int {
    return prefix.length + suffix.length + 1 + if (suffix.isEmpty()) 0 else 1
  }

  /**
   * The number of characters required in addition to the line comment for each line in a multi line
   * comment.
   */
  fun lineOverhead(): Int {
    return linePrefix.length
  }
}

fun String.isKDocComment(): Boolean = startsWith("/**")

fun String.isBlockComment(): Boolean = startsWith("/*") && !startsWith("/**")

fun String.isLineComment(): Boolean = startsWith("//")

fun String.commentType(): CommentType {
  return if (isKDocComment()) {
    CommentType.KDOC
  } else if (isBlockComment()) {
    CommentType.BLOCK
  } else if (isLineComment()) {
    CommentType.LINE
  } else {
    error("Not a comment: $this")
  }
}
