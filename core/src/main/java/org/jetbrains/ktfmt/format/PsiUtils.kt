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

import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtParameterList
import org.jetbrains.kotlin.psi.KtQualifiedExpression
import org.jetbrains.kotlin.psi.KtValueArgumentList
import org.jetbrains.kotlin.psi.psiUtil.getNextSiblingIgnoringWhitespace

/** Returns true if the expression represents an invocation that is also a lambda */
fun KtExpression.isLambda(): Boolean = this.callExpression?.lambdaArguments?.isNotEmpty() ?: false

/** Does this list have parens with only whitespace between them? */
fun KtParameterList.hasEmptyParens(): Boolean {
  val left = this.leftParenthesis ?: return false
  val right = this.rightParenthesis ?: return false
  return left.getNextSiblingIgnoringWhitespace() == right
}

/** Does this list have parens with only whitespace between them? */
fun KtValueArgumentList.hasEmptyParens(): Boolean {
  val left = this.leftParenthesis ?: return false
  val right = this.rightParenthesis ?: return false
  return left.getNextSiblingIgnoringWhitespace() == right
}

/**
 * [KotlinInputAstVisitor.emitQualifiedExpression] formats call expressions that are either part of
 * a qualified expression, or standing alone. This method makes it easier to handle both cases
 * uniformly.
 */
private val KtExpression.callExpression: KtCallExpression?
  get() = ((this as? KtQualifiedExpression)?.selectorExpression ?: this) as? KtCallExpression
