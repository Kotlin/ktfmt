package org.jetbrains.ktfmt.format.visitor.kotlinlang

import com.google.googlejavaformat.Doc
import com.google.googlejavaformat.OpsBuilder
import java.util.Optional
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtExpression
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
import org.jetbrains.ktfmt.format.visitor.forcedBreak
import org.jetbrains.ktfmt.format.visitor.format
import org.jetbrains.ktfmt.format.visitor.hasLineBreakingCommentBefore
import org.jetbrains.ktfmt.format.visitor.options
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
 * - Only add a break before `->` when the condition ends with a trailing comma
 *
 * - Wrap keywords and conditions into a single block with [expressionBreakIndent] in
 *   [emitKeywordWithCondition] to correctly handle line comments
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
          val forceMultiline = whenEntry.trailingComma != null
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
              val lastIndex = conditions.lastIndex
              val lastCommaIndex = if (forceMultiline) lastIndex + 1 else lastIndex
              for ((index, condition) in conditions.withIndex()) {
                format(condition)
                if (index < lastCommaIndex) builder.token(",")
                if (index < lastIndex) {
                  if (forceMultiline) {
                    builder.forcedBreak()
                  } else {
                    builder.breakOp(Doc.FillMode.UNIFIED, " ", ZERO)
                  }
                }
              }
            }
            whenEntry.guard?.let { guard ->
              val guardCondition = guard.getExpression()
              if (guardCondition.hasLineBreakingCommentBefore) {
                builder.space()
              } else {
                builder.breakOp(Doc.FillMode.INDEPENDENT, " ", expressionBreakIndent)
              }
              emitKeywordWithCondition(
                  "if",
                  guardCondition,
                  surroundConditionWithParens = false,
                  breakableBeforeCondition = false,
                  breakableAfterCondition = false,
              )
            }
          }
          val whenExpression = whenEntry.expression
          if (forceMultiline) {
            builder.forcedBreak(expressionBreakIndent)
          } else {
            builder.space()
          }
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

  context(_: FormatterStateHolder)
  override fun emitKeywordWithCondition(
      keyword: String,
      condition: KtExpression?,
      surroundConditionWithParens: Boolean,
      breakableBeforeCondition: Boolean,
      breakableAfterCondition: Boolean,
  ) {
    if (condition == null) {
      builder.token(keyword)
      return
    }

    builder.block(expressionBreakIndent) {
      builder.token(keyword)
      builder.space()
      if (surroundConditionWithParens) {
        builder.token("(")
      }
      if (options.manageTrailingCommas) {
        if (breakableBeforeCondition) {
          builder.breakOp(Doc.FillMode.UNIFIED, "", ZERO)
        }
        format(condition)
        if (breakableAfterCondition) {
          builder.breakOp(Doc.FillMode.UNIFIED, "", -expressionBreakIndent)
        }
      } else {
        builder.block { format(condition) }
      }
    }
    if (surroundConditionWithParens) {
      builder.token(")")
    }
  }
}
