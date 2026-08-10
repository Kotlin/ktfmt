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

/**
 * KDoc token. Our idea of what constitutes a token is often larger or smaller than what you'd
 * naturally expect. The decision is usually pragmatic rather than theoretical. Most of the details
 * are in [KDocFormatter].
 */
internal class KDocToken(val type: Type, val value: String) {
  /**
   * KDoc token type.
   *
   * The general idea is that every token that requires special handling (extra line breaks,
   * indentation, forcing or forbidding whitespace) from [KDocWriter] gets its own type. But I
   * haven't been super careful about it, so I'd imagine that we could merge or remove some of these
   * if we wanted. (For example, PARAGRAPH_CLOSE_TAG and LIST_ITEM_CLOSE_TAG could share a common
   * IGNORABLE token type. But their corresponding OPEN tags exist, so I've kept the CLOSE tags.)
   *
   * Note, though, that tokens of the same type may still have been handled differently by [ ] when
   * it created them. For example, LITERAL is used for both plain text and inline tags, even though
   * the two affect the lexer's state differently.
   */
  internal enum class Type {
    /** ∕✱✱ */
    BEGIN_KDOC,
    /** ✱∕ */
    END_KDOC,
    LIST_ITEM_OPEN_TAG,
    HEADER_OPEN_TAG,
    PARAGRAPH_OPEN_TAG,
    PRE_OPEN_TAG,
    PRE_CLOSE_TAG,
    CODE_OPEN_TAG,
    CODE_CLOSE_TAG,
    TABLE_OPEN_TAG,
    TABLE_CLOSE_TAG,
    /** Things such as `@param` or `@see` */
    TAG,
    /** Code between two markers of three backticks */
    CODE,
    /** three backticks */
    CODE_BLOCK_MARKER,
    /** A link in brackets such as [KDocToken] */
    MARKDOWN_LINK,
    BLANK_LINE,
    /**
     * Whitespace that is not in a `<pre>` or `<table>` section. Whitespace includes leading
     * newlines, asterisks, and tabs and spaces. In the output, it is translated to newlines (with
     * leading spaces and asterisks) or spaces.
     */
    WHITESPACE,
    /**
     * Anything else: `foo`, `<b>`, `{ foo}` etc. [KDocFormatter] sometimes creates adjacent literal
     * tokens, which it then merges into a single, larger literal token before returning its output.
     *
     * This also includes whitespace in a `<pre>` or `<table>` section. We preserve user formatting
     * in these sections, including arbitrary numbers of spaces. By treating such whitespace as a
     * literal, we can merge it with adjacent literals, preventing us from autowrapping inside these
     * sections -- and doing so naively, to boot. The wrapped line would have no indentation after
     * "* " or, possibly worse, it might begin with an arbitrary amount of whitespace that didn't
     * fit on the previous line. Of course, by doing this, we're potentially creating lines of more
     * than 100 characters. But it seems fair to call in the humans to resolve such problems.
     */
    LITERAL,
  }

  fun length(): Int = value.length

  override fun toString(): String = "KDocToken{$type: \"$value\"}"
}
