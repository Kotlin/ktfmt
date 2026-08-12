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

package org.jetbrains.ktfmt.format.visitor

import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract
import org.jetbrains.kotlin.com.intellij.psi.PsiComment
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtParameterList
import org.jetbrains.kotlin.psi.KtQualifiedExpression
import org.jetbrains.kotlin.psi.KtValueArgumentList
import org.jetbrains.kotlin.psi.psiUtil.getNextSiblingIgnoringWhitespace
import org.jetbrains.kotlin.psi.psiUtil.getPrevSiblingIgnoringWhitespace

/** Returns true if the expression represents an invocation that is also a lambda */
val KtExpression.isLambda: Boolean
  get() = this.callExpression?.lambdaArguments?.isNotEmpty() ?: false

/** Does this list have parens with only whitespace between them? */
fun KtParameterList.hasEmptyParens(): Boolean {
  val left = this.leftParenthesis ?: return false
  val right = this.rightParenthesis ?: return false
  return left.getNextSiblingIgnoringWhitespace() == right
}

/** Does this list have parens with only whitespace between them? */
val KtValueArgumentList.hasEmptyParens: Boolean
  get() {
    val left = this.leftParenthesis ?: return false
    val right = this.rightParenthesis ?: return false
    return left.getNextSiblingIgnoringWhitespace() == right
  }

/**
 * [KotlinInputAstVisitor.emitQualifiedExpression] formats call expressions that are either part of
 * a qualified expression, or standing alone. This method makes it easier to handle both cases
 * uniformly.
 */
val KtExpression.callExpression: KtCallExpression?
  get() = ((this as? KtQualifiedExpression)?.selectorExpression ?: this) as? KtCallExpression

/**
 * Returns true when [this@isBlockLikeCall] is a call that is forced onto multiple lines regardless
 * of the line width, either because its value argument list has a trailing comma (e.g. `foo(\n 1,\n
 * 2,\n)`) or because one of its arguments is itself a block-like multiline call.
 *
 * Such calls are rendered "block-like": they stay on the same line as the preceding `=`/`by`
 * operator (instead of breaking and indenting after it), and any chained selectors break onto their
 * own line, mirroring how scoping functions and lambdas are handled.
 */
@OptIn(ExperimentalContracts::class)
val KtExpression?.isBlockLikeCall: Boolean
  get() {
    contract { returns(true) implies (this@isBlockLikeCall is KtCallExpression) }

    if (this == null) return false
    val prev = this.getPrevSiblingIgnoringWhitespace()
    if (prev is PsiComment) {
      return false // Leading comments cause weird indentation; keep the default layout.
    }

    if (this !is KtCallExpression) return false
    val valueArgumentList = this.valueArgumentList ?: return false
    return valueArgumentList.trailingComma != null ||
        valueArgumentList.arguments.any { argument ->
          val argumentExpression = argument.getArgumentExpression()
          argumentExpression != null &&
              (argumentExpression.isBlockLikeCall || argumentExpression.isChainedBlockLikeCall)
        }
  }

/**
 * Returns true when [this@isChainedBlockLikeCall] is a chain whose innermost receiver is a
 * [isBlockLikeCall].
 */
@OptIn(ExperimentalContracts::class)
val KtExpression.isChainedBlockLikeCall: Boolean
  get() {
    contract { returns(true) implies (this@isChainedBlockLikeCall is KtQualifiedExpression) }
    return this is KtQualifiedExpression && this.chainRoot.isBlockLikeCall
  }

/** Returns the innermost receiver of a (possibly nested) qualified [this@chainRoot]. */
val KtExpression.chainRoot: KtExpression
  get() {
    var root: KtExpression = this
    while (root is KtQualifiedExpression) {
      root = root.receiverExpression
    }
    return root
  }
