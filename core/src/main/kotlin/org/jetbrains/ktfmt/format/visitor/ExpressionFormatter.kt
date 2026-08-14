package org.jetbrains.ktfmt.format.visitor

import com.google.googlejavaformat.Doc
import org.jetbrains.kotlin.psi.KtExpression

interface ExpressionFormatter : KotlinAstFormatter {
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
