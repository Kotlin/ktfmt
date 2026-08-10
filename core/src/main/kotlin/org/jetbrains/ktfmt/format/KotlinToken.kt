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

import com.google.common.base.MoreObjects
import com.google.common.collect.ImmutableList
import com.google.googlejavaformat.Input

class KotlinToken(
    private val toksBefore: ImmutableList<KotlinTok>,
    private val kotlinTok: KotlinTok,
    private val toksAfter: ImmutableList<KotlinTok>,
) : Input.Token {

  override fun getTok(): KotlinTok = kotlinTok

  override fun getToksBefore(): ImmutableList<out Input.Tok> = toksBefore

  override fun getToksAfter(): ImmutableList<out Input.Tok> = toksAfter

  override fun toString(): String {
    return MoreObjects.toStringHelper(this)
        .add("tok", kotlinTok)
        .add("toksBefore", toksBefore)
        .add("toksAfter", toksAfter)
        .toString()
  }
}
