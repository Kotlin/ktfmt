package org.jetbrains.ktfmt.format.visitor.kotlinlang

import com.google.googlejavaformat.Doc
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.ktfmt.format.visitor.ExpressionFormatterImpl
import org.jetbrains.ktfmt.format.visitor.FormatterStateHolder
import org.jetbrains.ktfmt.format.visitor.block
import org.jetbrains.ktfmt.format.visitor.breakOp
import org.jetbrains.ktfmt.format.visitor.builder
import org.jetbrains.ktfmt.format.visitor.expressionBreakIndent
import org.jetbrains.ktfmt.format.visitor.fenceComments
import org.jetbrains.ktfmt.format.visitor.format
import org.jetbrains.ktfmt.format.visitor.formatChainedBlockLikeCall
import org.jetbrains.ktfmt.format.visitor.formatChainedScopingFunction
import org.jetbrains.ktfmt.format.visitor.formatLambdaOrScopingFunction
import org.jetbrains.ktfmt.format.visitor.isBlockLikeCall
import org.jetbrains.ktfmt.format.visitor.isChainedBlockLikeCall
import org.jetbrains.ktfmt.format.visitor.isChainedScopingFunction
import org.jetbrains.ktfmt.format.visitor.isLambdaOrScopingFunction
import org.jetbrains.ktfmt.format.visitor.token

/**
 * Custom expression formatter for KotlinLang style that handles formatting of block-like calls with
 * or without chained call (see #633). Currently, it extracts the behaviour introduced in #634 to an
 * experimental engine API. Motivation: we don't want to change the behaviour of the existing
 * formatter while we're also evolving the new Kotlin Lang style.
 */
internal class KotlinLangExpressionFormatterImpl : ExpressionFormatterImpl() {
  context(_: FormatterStateHolder)
  override fun formatInitializerExpression(initializer: KtExpression, assignmentOp: String) {
    builder.token(assignmentOp)
    if (initializer.isLambdaOrScopingFunction) {
      formatLambdaOrScopingFunction(initializer)
    } else if (initializer.isChainedScopingFunction) {
      formatChainedScopingFunction(initializer, emitLeadingBreak = true)
    } else if (initializer.isBlockLikeCall) {
      builder.space()
      format(initializer)
    } else if (initializer.isChainedBlockLikeCall) {
      formatChainedBlockLikeCall(initializer, emitLeadingBreak = true)
    } else {
      builder.breakOp(Doc.FillMode.UNIFIED, " ", expressionBreakIndent)
      builder.block(expressionBreakIndent) {
        builder.fenceComments()
        format(initializer)
      }
    }
  }
}
