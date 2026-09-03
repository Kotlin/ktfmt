package org.jetbrains.ktfmt.format.visitor.kotlinlang

import com.google.googlejavaformat.Doc
import com.google.googlejavaformat.Output
import java.util.Optional
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.ktfmt.format.visitor.ExpressionFormatterImpl
import org.jetbrains.ktfmt.format.visitor.FormatterStateHolder
import org.jetbrains.ktfmt.format.visitor.Indentation
import org.jetbrains.ktfmt.format.visitor.Indentation.Companion.ZERO
import org.jetbrains.ktfmt.format.visitor.block
import org.jetbrains.ktfmt.format.visitor.breakOp
import org.jetbrains.ktfmt.format.visitor.builder
import org.jetbrains.ktfmt.format.visitor.expressionBreakIndent
import org.jetbrains.ktfmt.format.visitor.fenceComments
import org.jetbrains.ktfmt.format.visitor.format
import org.jetbrains.ktfmt.format.visitor.isPrefixedByLineBreak
import org.jetbrains.ktfmt.format.visitor.token

/**
 * Custom expression formatter for KotlinLang style.
 *
 * Implements a new unopinionated initializers formatting rule that preserves the user input:
 * - If there is a line break after the assignment operator, the initializer is formatted on a new
 *   line with an expression indent (same behaviour as
 *   [ExpressionFormatterImpl.formatAssignmentLikeExpression])
 * - If there is no line break after the assignment operator, the initializer is formatted on the
 *   same line as the assignment operator
 *
 * ```
 * fun exprBody1() = compute(alpha)
 *     ?: fallbackValue
 *
 * fun exprBody2() =
 *     compute(alpha) ?: fallbackValue
 * ```
 */
internal class KotlinLangExpressionFormatterImpl : ExpressionFormatterImpl() {
  context(_: FormatterStateHolder)
  override fun formatAssignmentLikeExpression(assignment: KtExpression, assignmentOp: String) {
    builder.token(assignmentOp)

    var movedToOwnLine: Output.BreakTag? = null
    if (assignment.isPrefixedByLineBreak) {
      movedToOwnLine = Output.BreakTag()
      builder.breakOp(Doc.FillMode.UNIFIED, " ", expressionBreakIndent, Optional.of(movedToOwnLine))
    } else {
      builder.space()
    }

    val indent = Indentation.If(movedToOwnLine, expressionBreakIndent, ZERO)
    builder.block(indent) {
      builder.fenceComments()
      format(assignment)
    }
  }
}
