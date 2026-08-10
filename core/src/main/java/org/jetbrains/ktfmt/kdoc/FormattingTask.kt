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

class FormattingTask(
    /** Options to format with */
    var options: KDocFormattingOptions,

    /** The original comment to be formatted */
    var comment: String,

    /**
     * The initial indentation on the first line of the KDoc. The reformatted comment will prefix
     * each subsequent line with this string.
     */
    var initialIndent: String,

    /**
     * Indent to use after the first line.
     *
     * This is useful when the comment starts the end of an existing code line. For example,
     * something like this:
     * ```
     *     if (foo.bar.baz()) { // This comment started at column 25
     *         // but the second and subsequent lines are indented 8 spaces
     *         // ...
     * ```
     *
     * (This doesn't matter much for KDoc comments, since the formatter will always push these into
     * their own lines so the indents will match, but for line and block comments it can matter.)
     */
    var secondaryIndent: String = initialIndent,

    /**
     * Optional list of parameters associated with this doc; if set, and if
     * [KDocFormattingOptions.orderDocTags] is set, parameter doc tags will be sorted to match this
     * order. (The intent is for the tool invoking KDocFormatter to pass in the parameter names in
     * signature order here.)
     */
    var orderedParameterNames: List<String> = emptyList(),

    /** The type of comment being formatted. */
    val type: CommentType = comment.commentType(),
)
