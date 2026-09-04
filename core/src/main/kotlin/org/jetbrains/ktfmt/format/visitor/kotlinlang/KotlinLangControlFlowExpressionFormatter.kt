package org.jetbrains.ktfmt.format.visitor.kotlinlang

import com.google.googlejavaformat.Doc
import com.google.googlejavaformat.OpsBuilder
import java.util.Optional
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtWhenExpression
import org.jetbrains.ktfmt.format.visitor.ControlFlowExpressionFormatterImpl
import org.jetbrains.ktfmt.format.visitor.FormatterStateHolder
import org.jetbrains.ktfmt.format.visitor.Indentation.Companion.ZERO
import org.jetbrains.ktfmt.format.visitor.block
import org.jetbrains.ktfmt.format.visitor.blockIndent
import org.jetbrains.ktfmt.format.visitor.breakOp
import org.jetbrains.ktfmt.format.visitor.builder
import org.jetbrains.ktfmt.format.visitor.expressionBreakIndent
import org.jetbrains.ktfmt.format.visitor.format
import org.jetbrains.ktfmt.format.visitor.sync
import org.jetbrains.ktfmt.format.visitor.token

/**
 * Custom control flow expression formatter for KotlinLang style.
 *
 * Currently only overrides `when` expression formatting, for everything else see
 * [ControlFlowExpressionFormatterImpl]. Changes in `when` expression formatting:
 * - Commas between multiple conditions in one entry do not force a break: the breaks are only taken
 *   if *all* the conditions do not fit on one line:
 * ```
 * when (x) {
 *     1, 2 -> false
 *     is LocalVariableDescriptor,
 *     is ValueParameterDescriptor,
 *     is ReceiverParameterDescriptor -> true
 * }
 * ```
 * - Never add a break before `->`, even when the condition ends with a trailing comma
 */
internal class KotlinLangControlFlowExpressionFormatterImpl : ControlFlowExpressionFormatterImpl() {
  context(_: FormatterStateHolder)
  override fun formatWhenExpression(expression: KtWhenExpression) {
    builder.sync(expression)
    builder.block {
      emitKeywordWithCondition("when", expression.subjectExpression)

      builder.space()
      builder.token(
          "{",
          Doc.Token.RealOrImaginary.REAL,
          blockIndent.indent,
          Optional.of(blockIndent.indent),
      )

      expression.entries.forEachIndexed { index, whenEntry ->
        builder.block(blockIndent) {
          if (index != 0) {
            // preserve new line if there's one
            builder.blankLineWanted(OpsBuilder.BlankLineWanted.PRESERVE)
          }
          builder.forcedBreak()
          builder.block {
            if (whenEntry.elseKeyword != null) {
              builder.token("else")
            } else {
              val conditions = whenEntry.conditions
              for ((index, condition) in conditions.withIndex()) {
                format(condition)
                builder.guessToken(",")
                if (index != conditions.lastIndex) {
                  builder.breakOp(Doc.FillMode.UNIFIED, " ", ZERO)
                }
              }
            }
            whenEntry.guard?.let { guard ->
              builder.breakOp(Doc.FillMode.INDEPENDENT, " ", expressionBreakIndent)
              emitKeywordWithCondition(
                  "if",
                  guard.getExpression(),
                  surroundConditionWithParens = false,
                  breakableBeforeCondition = false,
                  breakableAfterCondition = false,
              )
            }
          }
          val whenExpression = whenEntry.expression
          builder.space()
          builder.token("->")
          if (whenExpression is KtBlockExpression || whenExpression is KtLambdaExpression) {
            builder.space()
            format(whenExpression)
          } else {
            builder.block(expressionBreakIndent) {
              builder.breakOp(Doc.FillMode.INDEPENDENT, " ", ZERO)
              format(whenExpression)
            }
          }
          builder.guessToken(";")
        }
        builder.forcedBreak()
      }
      builder.token("}")
    }
  }
}
