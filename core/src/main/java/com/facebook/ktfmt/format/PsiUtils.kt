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

package com.facebook.ktfmt.format

import java.util.ArrayDeque
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract
import org.jetbrains.kotlin.com.intellij.psi.PsiComment
import org.jetbrains.kotlin.com.intellij.psi.PsiElement
import org.jetbrains.kotlin.com.intellij.psi.PsiWhiteSpace
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtArrayAccessExpression
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtLabeledExpression
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtParameterList
import org.jetbrains.kotlin.psi.KtPostfixExpression
import org.jetbrains.kotlin.psi.KtQualifiedExpression
import org.jetbrains.kotlin.psi.KtSimpleNameExpression
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.psi.KtValueArgumentList
import org.jetbrains.kotlin.psi.KtWhenExpression
import org.jetbrains.kotlin.psi.psiUtil.children
import org.jetbrains.kotlin.psi.psiUtil.getNextSiblingIgnoringWhitespace
import org.jetbrains.kotlin.psi.psiUtil.getPrevSiblingIgnoringWhitespace

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
internal val KtExpression.callExpression: KtCallExpression?
  get() = ((this as? KtQualifiedExpression)?.selectorExpression ?: this) as? KtCallExpression

/** Returns the innermost receiver of a (possibly nested) qualified [KtExpression]. */
internal val KtExpression.chainRoot: KtExpression
  get() {
    var root: KtExpression = this
    while (root is KtQualifiedExpression) {
      root = root.receiverExpression
    }
    return root
  }

/**
 * Whether a comment precedes this element.
 *
 * A leading comment brings its own forced break with it, which throws off any layout that decides
 * indentation from whether the break before the element was taken. Such layouts fall back to the
 * default one when this is true.
 */
internal val PsiElement.hasLeadingComment: Boolean
  get() = getPrevSiblingIgnoringWhitespace() is PsiComment

/**
 * Returns true when [KtExpression] is a call that is forced onto multiple lines regardless of the
 * line width, either because its value argument list has a trailing comma (e.g. `foo(\n 1,\n
 * 2,\n)`) or because one of its arguments is itself a block-like multiline call.
 *
 * Such calls are rendered "block-like": they stay on the same line as the preceding `=`/`by`
 * operator (instead of breaking and indenting after it), and any chained selectors break onto their
 * own line, mirroring how scoping functions and lambdas are handled.
 */
@OptIn(ExperimentalContracts::class)
internal val KtExpression?.isBlockLikeCall: Boolean
  get() {
    contract { returns(true) implies (this@isBlockLikeCall is KtCallExpression) }

    if (this == null) return false
    if (hasLeadingComment) return false

    if (this !is KtCallExpression) return false
    val valueArgumentList = valueArgumentList ?: return false
    return valueArgumentList.trailingComma != null ||
        valueArgumentList.arguments.any { argument ->
          val argumentExpression = argument.getArgumentExpression()
          argumentExpression != null &&
              (argumentExpression.isBlockLikeCall || argumentExpression.isChainedBlockLikeCall)
        }
  }

/**
 * Returns true when [KtExpression] is an infix function call -- `a to b`, `x and y` -- whose
 * right-hand operand ends in a [isBlockLikeCall]
 */
@OptIn(ExperimentalContracts::class)
internal val KtExpression?.isInfixBlockLikeCall: Boolean
  get() {
    contract { returns(true) implies (this@isInfixBlockLikeCall is KtBinaryExpression) }

    if (this !is KtBinaryExpression) return false
    if (hasLeadingComment) return false
    // An identifier as the operator is what distinguishes `a to b` from `a + b`.
    if (operationToken != KtTokens.IDENTIFIER) return false
    val right = right ?: return false
    if (right.hasLeadingComment) return false
    val call = right.callExpression ?: return false
    return call.isBlockLikeCall && call.lambdaArguments.isEmpty()
  }

/** Returns true when [KtExpression] is a chain whose innermost receiver is a [isBlockLikeCall]. */
@OptIn(ExperimentalContracts::class)
internal val KtExpression.isChainedBlockLikeCall: Boolean
  get() {
    contract { returns(true) implies (this@isChainedBlockLikeCall is KtQualifiedExpression) }

    return this is KtQualifiedExpression && this.chainRoot.isBlockLikeCall
  }

/**
 * Returns true when [KtExpression] is a chain of dotted parts that gets the regular chain layout,
 * e.g. `a[5].b!!.c()[4].f()`.
 *
 * Chains whose receiver is special-cased elsewhere -- a string template (`"a".trim()`) or a `when`
 * -- are excluded, as are the block-like and scoping-function chains that have their own layout.
 */
@OptIn(ExperimentalContracts::class)
internal val KtExpression.isPlainQualifiedChain: Boolean
  get() {
    contract { returns(true) implies (this@isPlainQualifiedChain is KtQualifiedExpression) }

    if (this !is KtQualifiedExpression) return false
    if (receiverExpression is KtStringTemplateExpression) return false
    if (receiverExpression is KtWhenExpression) return false
    return !isChainedBlockLikeCall && !isChainedScopingFunction
  }

/**
 * Returns true when [KtExpression] is a chain whose innermost receiver is a scoping function call.
 *
 * For example, this matches `runnnnn { ... }.baz()` (innermost receiver `runnnnn { ... }` is a
 * scoping function). It does not match a chain whose root is a plain identifier or a non-scoping
 * call, since those don't have a block-like opener to anchor the chain against.
 */
@OptIn(ExperimentalContracts::class)
internal val KtExpression.isChainedScopingFunction: Boolean
  get() {
    contract { returns(true) implies (this@isChainedScopingFunction is KtQualifiedExpression) }

    return this is KtQualifiedExpression && this.chainRoot.isLambdaOrScopingFunction
  }

/**
 * Returns whether an expression is a lambda or initializer expression in which case we will want to
 * avoid indenting the lambda block
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
internal val KtExpression?.isLambdaOrScopingFunction: Boolean
  get() {
    if (this == null) return false
    val prev = this.getPrevSiblingIgnoringWhitespace()
    if (prev is PsiComment && prev.text.startsWith("//")) {
      return false // Leading line comments cause weird indentation; block comments are ok.
    }

    var carry = this
    if (carry is KtQualifiedExpression && carry.receiverExpression is KtSimpleNameExpression) {
      carry = carry.selectorExpression
    }
    if (carry is KtCallExpression) {
      if (
          carry.valueArgumentList?.leftParenthesis == null &&
              carry.lambdaArguments.isNotEmpty() &&
              carry.typeArgumentList?.arguments.isNullOrEmpty()
      ) {
        carry = carry.lambdaArguments[0].getArgumentExpression()
      } else {
        return false
      }
    }
    if (carry is KtLabeledExpression) {
      carry = carry.baseExpression
    }
    if (carry is KtLambdaExpression) {
      return true
    }

    return false
  }

/**
 * Returns true when [KtExpression] is a scoping-function call whose lambda body has source-level
 * newlines (i.e. spans multiple lines). Used to decide whether chained selectors after the lambda's
 * closing brace must break onto a new line.
 */
internal val KtExpression.isMultilineScopingFunction: Boolean
  get() {
    var carry: KtExpression? = this
    if (carry is KtQualifiedExpression && carry.receiverExpression is KtSimpleNameExpression) {
      carry = carry.selectorExpression
    }
    if (carry is KtCallExpression) {
      carry = carry.lambdaArguments.firstOrNull()?.getArgumentExpression()
    }
    if (carry is KtLabeledExpression) {
      carry = carry.baseExpression
    }
    if (carry is KtLambdaExpression) {
      return carry.hasSourceNewlineInLambdaBody
    }
    return false
  }

/**
 * Returns true if the source code contains a newline anywhere inside the body of
 * [KtLambdaExpression] — that is, between the opening `{` and the closing `}` of the function
 * literal. Used by [FormattingOptions.preserveLambdaBreaks] to keep user-authored multi-line
 * lambdas multi-line.
 */
internal val KtLambdaExpression.hasSourceNewlineInLambdaBody: Boolean
  get() {
    val functionLiteral = this.functionLiteral
    for (child in functionLiteral.node.children()) {
      if (child.psi is PsiWhiteSpace && child.textContains('\n')) return true
    }
    return false
  }

/**
 * Checks if a line-breaking comment precedes [PsiElement] in the PSI tree.
 *
 * Line comments (`//`) always force a break. Block comments (`/* */`) only count if they are on
 * their own line (preceded by whitespace with a newline). Inline block comments like `x /*tag*/ ||`
 * do not force a break and should not trigger INDEPENDENT fill mode.
 */
internal val PsiElement.hasLineBreakingCommentBefore: Boolean
  get() {
    var prev = this.prevSibling
    while (prev is PsiWhiteSpace) {
      prev = prev.prevSibling
    }
    if (prev !is PsiComment) return false

    // Line comments always force a line break
    if (prev.text.startsWith("//")) return true

    // Block comments force a break only if on their own line
    val beforeComment = prev.prevSibling
    return beforeComment is PsiWhiteSpace && beforeComment.text.contains('\n')
  }

/**
 * Decomposes a qualified expression into parts, so `rainbow.red.orange.yellow` becomes `[rainbow,
 * rainbow.red, rainbow.red.orange, rainbow.orange.yellow]`
 */
internal fun breakIntoParts(expression: KtExpression): List<KtExpression> {
  val parts = ArrayDeque<KtExpression>()

  // use an ArrayDeque and add elements to the beginning so the innermost expression comes first
  // foo.bar.yay -> [yay, bar.yay, foo.bar.yay]

  var node: KtExpression? = expression
  while (node != null) {
    parts.addFirst(node)
    node =
        when (node) {
          is KtQualifiedExpression -> node.receiverExpression
          is KtArrayAccessExpression -> node.arrayExpression
          is KtPostfixExpression -> node.baseExpression
          else -> null
        }
  }

  return parts.toList()
}
