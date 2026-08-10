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

import com.google.common.collect.ImmutableList
import com.google.googlejavaformat.Doc
import com.google.googlejavaformat.Doc.Level
import com.google.googlejavaformat.DocBuilder
import com.google.googlejavaformat.Op
import com.google.googlejavaformat.OpsBuilder

/**
 * A dummy [Op] that prevents comments from being moved ahead of it, into parent [Level]s.
 *
 * If a comment is the first thing in a [Level], [OpsBuilder] moves it outside of that level during
 * [Doc] building. It does so recursively, until the comment is not the first element of the level.
 * This behaviour can be very confusing, where comments seem to absorb or ignore expected
 * indentation.
 */
object FenceCommentsOp : Op {
  val AS_LIST: ImmutableList<Op> = ImmutableList.of(FenceCommentsOp)

  override fun add(builder: DocBuilder) {
    // Do nothing. This Op simply needs to be in the OpsBuilder.
  }

  override fun toString(): String = "FenceComments"
}
