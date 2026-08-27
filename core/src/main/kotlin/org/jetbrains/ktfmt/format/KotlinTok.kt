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
import com.google.googlejavaformat.Input
import com.google.googlejavaformat.Newlines
import org.jetbrains.kotlin.lexer.KtToken

class KotlinTok(
    private val index: Int,
    private val originalText: String,
    private val text: String,
    private val position: Int,
    val isToken: Boolean,
    private val kind: KtToken,
) : Input.Tok {

  override fun getIndex(): Int = index

  override fun getText(): String = text

  override fun getOriginalText(): String = originalText

  override fun length(): Int = originalText.length

  override fun getPosition(): Int = position

  // Never used from ktfmt, see also: KotlinInput.getPositionToColumnMap
  override fun getColumn(): Int = 0

  override fun isNewline(): Boolean = Newlines.isNewline(text)

  override fun isSlashSlashComment(): Boolean = text.startsWith("//")

  override fun isSlashStarComment(): Boolean = text.startsWith("/*")

  override fun isJavadocComment(): Boolean = text.startsWith("/**") && text.length > 4

  fun isShebang(): Boolean = text.startsWith("#!")

  /**
   * Shebang is treated like a comment, otherwise it will not be preserved in the output. At the
   * same time we implicitly know that shebang can only be at the start of the script file, because
   * otherwise the parser would fail
   */
  override fun isComment(): Boolean = isSlashSlashComment || isSlashStarComment || isShebang()

  fun kind(): KtToken = kind

  override fun toString(): String {
    return MoreObjects.toStringHelper(this)
        .add("index", index)
        .add("text", text)
        .add("position", position)
        .add("isToken", isToken)
        .toString()
  }
}
