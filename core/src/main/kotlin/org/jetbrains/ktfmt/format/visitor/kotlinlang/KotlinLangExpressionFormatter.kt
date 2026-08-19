package org.jetbrains.ktfmt.format.visitor.kotlinlang

import com.google.googlejavaformat.Doc
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.ktfmt.format.visitor.ExpressionFormatter
import org.jetbrains.ktfmt.format.visitor.block
import org.jetbrains.ktfmt.format.visitor.breakOp
import org.jetbrains.ktfmt.format.visitor.fenceComments
import org.jetbrains.ktfmt.format.visitor.isBlockLikeCall
import org.jetbrains.ktfmt.format.visitor.isChainedBlockLikeCall
import org.jetbrains.ktfmt.format.visitor.isChainedScopingFunction
import org.jetbrains.ktfmt.format.visitor.isLambdaOrScopingFunction
import org.jetbrains.ktfmt.format.visitor.token

/**
 * Custom expression formatter for Kotlin language that handles formatting of block-like calls with
 * or without chained call (see #633). Currently, it extracts the behaviour introduced in #634 to an
 * experimental engine API. Motivation: we don't want to change the behaviour of the existing
 * formatter while we're also evolving the new Kotlin Lang style.
 */
interface KotlinLangExpressionFormatter : ExpressionFormatter {
  override fun formatInitializerExpression(initializer: KtExpression) {
    builder.token("=")
    if (initializer.isLambdaOrScopingFunction) {
      formatLambdaOrScopingFunction(initializer)
    } else if (initializer.isChainedScopingFunction) {
      formatChainedScopingFunction(initializer, emitLeadingBreak = true)
    } else if (initializer.isBlockLikeCall) {
      builder.space()
      format(initializer)
    } else if (initializer.isChainedBlockLikeCall) {
      builder.space()
      formatChainedBlockLikeCall(initializer, emitLeadingBreak = false)
    } else {
      builder.breakOp(Doc.FillMode.UNIFIED, " ", expressionBreakIndent)
      builder.block(expressionBreakIndent) {
        builder.fenceComments()
        format(initializer)
      }
    }
  }
}
