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
import org.jetbrains.kotlin.com.intellij.psi.PsiElement
import org.jetbrains.kotlin.com.intellij.psi.PsiWhiteSpace
import org.jetbrains.kotlin.lexer.KtSingleValueToken
import org.jetbrains.kotlin.psi.KtArrayAccessExpression
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtBinaryExpressionWithTypeRHS
import org.jetbrains.kotlin.psi.KtCallElement
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtLabeledExpression
import org.jetbrains.kotlin.psi.KtLambdaArgument
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtParameterList
import org.jetbrains.kotlin.psi.KtPostfixExpression
import org.jetbrains.kotlin.psi.KtQualifiedExpression
import org.jetbrains.kotlin.psi.KtSimpleNameExpression
import org.jetbrains.kotlin.psi.KtValueArgument
import org.jetbrains.kotlin.psi.KtValueArgumentList
import org.jetbrains.kotlin.psi.psiUtil.children
import org.jetbrains.kotlin.psi.psiUtil.getNextSiblingIgnoringWhitespace
import org.jetbrains.kotlin.psi.psiUtil.getPrevSiblingIgnoringWhitespace
import org.jetbrains.ktfmt.format.FormattingOptions
import org.jetbrains.ktfmt.format.ParseError

/** Returns true if the expression represents an invocation that is also a lambda */
val KtExpression.isLambda: Boolean
  get() = this.callExpression?.lambdaArguments?.isNotEmpty() ?: false

/** @return true when a list has only empty parenthesis with only whitespace between them */
val KtParameterList.hasEmptyParenthesis: Boolean
  get() = onlyEmptyParenthesis(this.leftParenthesis, this.rightParenthesis)

/** @return true when a list has only empty parenthesis with only whitespace between them */
val KtValueArgumentList.hasEmptyParenthesis: Boolean
  get() = onlyEmptyParenthesis(this.leftParenthesis, this.rightParenthesis)

private fun onlyEmptyParenthesis(left: PsiElement?, right: PsiElement?): Boolean =
    left != null && right != null && left.getNextSiblingIgnoringWhitespace() == right

/**
 * [CallFormatter.emitQualifiedExpression] formats call expressions that are either part of a
 * qualified expression, or standing alone. This method makes it easier to handle both cases
 * uniformly.
 */
val KtExpression.callExpression: KtCallExpression?
  get() = ((this as? KtQualifiedExpression)?.selectorExpression ?: this) as? KtCallExpression

/**
 * @return true when [KtExpression] is a call that is forced onto multiple lines regardless of the
 *   line width, either because its value argument list has a trailing comma (e.g. `foo(\n 1,\n
 *   2,\n)`) or because one of its arguments is itself a block-like multiline call.
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
    // Leading comments cause weird indentation; keep the default layout.
    if (this.getPrevSiblingIgnoringWhitespace() is PsiComment) return false

    if (this !is KtCallExpression) return false

    val valueArgumentList = valueArgumentList ?: return false
    return valueArgumentList.trailingComma != null ||
        valueArgumentList.arguments.any { it.isBlockLikeArgument }
  }

val KtValueArgument.isBlockLikeArgument: Boolean
  get() {
    val argumentExpression = getArgumentExpression()
    return argumentExpression != null &&
        (argumentExpression.isBlockLikeCall || argumentExpression.isChainedBlockLikeCall)
  }

val KtValueArgument.isUnnamedLambda: Boolean
  get() = getArgumentExpression() is KtLambdaExpression && getArgumentName() == null

/** Returns true when [KtExpression] is a chain whose innermost receiver is a [isBlockLikeCall]. */
@OptIn(ExperimentalContracts::class)
val KtExpression.isChainedBlockLikeCall: Boolean
  get() {
    contract { returns(true) implies (this@isChainedBlockLikeCall is KtQualifiedExpression) }
    return this is KtQualifiedExpression && this.chainRoot.isBlockLikeCall
  }

/** Returns the innermost receiver of a (possibly nested) qualified [KtExpression]. */
val KtExpression.chainRoot: KtExpression
  get() {
    var root: KtExpression = this
    while (root is KtQualifiedExpression) {
      root = root.receiverExpression
    }
    return root
  }

/**
 * Decomposes a qualified expression into parts, so `rainbow.red.orange.yellow` becomes `[rainbow,
 * rainbow.red, rainbow.red.orange, rainbow.orange.yellow]`
 */
val KtExpression.chainParts: List<KtExpression>
  get() = buildList {
    var node: KtExpression? = this@chainParts
    while (node != null) {
      add(node)
      node =
          when (node) {
            is KtQualifiedExpression -> node.receiverExpression
            is KtArrayAccessExpression -> node.arrayExpression
            is KtPostfixExpression -> node.baseExpression
            else -> null
          }
    }
  }
      .asReversed()

/**
 * Checks if a line-breaking comment precedes [PsiElement] in the PSI tree.
 *
 * Line comments (`//`) always force a break. Block comments (`/* */`) only count if they are on
 * their own line (preceded by whitespace with a newline). Inline block comments like `x /*tag*/ ||`
 * do not force a break and should not trigger INDEPENDENT fill mode.
 */
val PsiElement.hasLineBreakingCommentBefore: Boolean
  get() {
    val comment = getPrevSiblingIgnoringWhiteSpace<PsiComment>() ?: return false

    // Line comments always force a line break
    if (comment.text.startsWith("//")) return true

    // Block comments force a break only if on their own line
    val beforeComment = comment.prevSibling
    return beforeComment is PsiWhiteSpace && beforeComment.text.contains('\n')
  }

inline fun <reified T : PsiElement> PsiElement?.getPrevSiblingIgnoringWhiteSpace(): T? {
  var prev = this?.prevSibling
  while (prev is PsiWhiteSpace) {
    prev = prev.prevSibling
  }
  return prev as? T
}

/**
 * An unwrapped lambda expression or scoping function of an expression
 *
 * Examples:
 * 1. '... = { ... }' is a lambda expression
 * 2. '... = Runnable { ... }' is considered a scoping function
 * 3. '... = scope { ... }' '... = apply { ... }' is a scoping function
 * 4. '... = scope.launch { ... }' is a dot-qualified scoping function
 *
 * but not:
 * 1. '... = foo() { ... }' due to the empty parenthesis
 * 2. '... = Runnable @Annotation { ... }' due to the annotation
 */
internal data class ScopingLambda(
    val receiverExpression: KtExpression?,
    val operation: KtSingleValueToken?,
    val calleeExpression: KtExpression?,
    val labeledExpression: KtLabeledExpression?,
    val lambdaExpression: KtLambdaExpression,
)

internal val PsiElement?.scopingLambda: ScopingLambda?
  get() {
    if (this == null) return null
    var receiverExpression: KtExpression? = null
    var operation: KtSingleValueToken? = null
    var calleeExpression: KtExpression? = null
    var labeledExpression: KtLabeledExpression? = null
    val lambdaExpression: KtLambdaExpression
    var carry = this
    if (carry is KtQualifiedExpression && carry.receiverExpression is KtSimpleNameExpression) {
      receiverExpression = carry.receiverExpression
      operation = carry.operationSign
      carry = carry.selectorExpression
    }
    if (carry is KtCallExpression) {
      calleeExpression = carry.calleeExpression
      if (
          carry.valueArgumentList?.leftParenthesis == null &&
              carry.lambdaArguments.isNotEmpty() &&
              carry.typeArgumentList?.arguments.isNullOrEmpty()
      ) {
        carry = carry.lambdaArguments[0].getArgumentExpression()
      } else {
        return null
      }
    }
    if (carry is KtLabeledExpression) {
      labeledExpression = carry
      carry = carry.baseExpression
    }
    lambdaExpression = carry as? KtLambdaExpression ?: return null
    return ScopingLambda(
        receiverExpression,
        operation,
        calleeExpression,
        labeledExpression,
        lambdaExpression,
    )
  }

/**
 * Returns whether an expression is a lambda or an initializer expression, in which case we will
 * want to avoid indenting the lambda block
 */
val KtExpression?.isLambdaOrScopingFunction: Boolean
  get() {
    if (this == null) return false
    val comment = this.getPrevSiblingIgnoringWhiteSpace<PsiComment>()
    // Leading line comments cause weird indentation; block comments are ok.
    if (comment != null && comment.text.startsWith("//")) return false

    return this.scopingLambda != null
  }

/**
 * Returns true when [KtExpression] is a chain whose innermost receiver is a scoping function call.
 *
 * For example, this matches `runnnnn { ... }.baz()` (innermost receiver `runnnnn { ... }` is a
 * scoping function). It does not match a chain whose root is a plain identifier or a non-scoping
 * call, since those don't have a block-like opener to anchor the chain against.
 */
@OptIn(ExperimentalContracts::class)
val KtExpression.isChainedScopingFunction: Boolean
  get() {
    contract { returns(true) implies (this@isChainedScopingFunction is KtQualifiedExpression) }
    return this is KtQualifiedExpression && this.chainRoot.isLambdaOrScopingFunction
  }

/**
 * Returns true when any chained selector after the innermost scoping-function receiver carries
 * value arguments (i.e. `.foo(a)` or `.fold({ ... }, { ... })`). Used to decide formatting style
 * for property initializers: value-arg chains stay on same line as `=`, while no-arg chains break.
 */
fun chainedSelectorsHaveValueArguments(expression: KtExpression): Boolean {
  var current: KtExpression = expression
  while (current is KtQualifiedExpression) {
    val selector = current.selectorExpression
    if (selector is KtCallExpression && !selector.valueArgumentList?.arguments.isNullOrEmpty()) {
      return true
    }
    current = current.receiverExpression
  }
  return false
}

/**
 * Returns true when [KtExpression] is a scoping-function call whose lambda body has source-level
 * newlines (i.e. spans multiple lines). Used to decide whether chained selectors after the lambda's
 * closing brace must break onto a new line.
 */
val KtExpression.isMultilineScopingFunction: Boolean
  get() = scopingLambda?.lambdaExpression?.hasSourceNewlineInLambdaBody ?: false

/**
 * Returns true if the source code contains a newline anywhere inside the body of
 * [KtLambdaExpression] — that is, between the opening `{` and the closing `}` of the function
 * literal. Used by [FormattingOptions.preserveLambdaBreaks] to keep user-authored multi-line
 * lambdas multi-line.
 */
val KtLambdaExpression.hasSourceNewlineInLambdaBody: Boolean
  get() {
    val functionLiteral = this.functionLiteral
    for (child in functionLiteral.node.children()) {
      if (child.psi is PsiWhiteSpace && child.textContains('\n')) return true
    }
    return false
  }

internal fun KtExpression?.startsWithUpperCase(): Boolean {
  return this?.text?.firstOrNull()?.isUpperCase() ?: false
}

internal val KtExpression?.isBinaryExpression: Boolean
  get() = this is KtBinaryExpression || this is KtBinaryExpressionWithTypeRHS

/**
 * Returns the trailing lambda argument of a function call expression or null if its not present.
 *
 * A function call can't have more than one trailing lambda, but [KtCallElement] and
 * [KtCallExpression] represent them as a list (see [KtCallExpression.getLambdaArguments] for more
 * details).
 */
internal val KtCallElement.trailingLambda: KtLambdaArgument?
  get() {
    val lambdas = lambdaArguments
    if (lambdas.isEmpty()) return null
    if (lambdas.size == 1) return lambdas.first()
    else throw ParseError("Maximum one trailing lambda is allowed", lambdaArguments[1])
  }

/**
 * Returns all parts of a binary expression chain. AST parses multi-operand expressions from right
 * to left: `a + b + c + d == a + (b + (c + d))`, while formatter is interested in the order from
 * left to right: `a + b + c + d == ((a + b) + c) + d`. So for given example this will return a list
 * of three elements:
 * ```
 * 0. a + b
 * 1. (a + b) + c
 * 2. ((a + b) + c) + d
 * ```
 */
internal val KtBinaryExpression.fullChain: List<KtBinaryExpression>
  get() = buildList {
    val op = operationToken
    var current: KtExpression? = this@fullChain
    while (current is KtBinaryExpression && current.operationToken == op) {
      add(current)
      current = current.left
    }
  }
      .asReversed()
