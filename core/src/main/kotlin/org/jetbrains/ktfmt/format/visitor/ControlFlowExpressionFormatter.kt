package org.jetbrains.ktfmt.format.visitor

import com.google.googlejavaformat.Doc
import com.google.googlejavaformat.OpsBuilder
import java.util.Optional
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtBreakExpression
import org.jetbrains.kotlin.psi.KtCatchClause
import org.jetbrains.kotlin.psi.KtContinueExpression
import org.jetbrains.kotlin.psi.KtDoWhileExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFinallySection
import org.jetbrains.kotlin.psi.KtForExpression
import org.jetbrains.kotlin.psi.KtIfExpression
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtReturnExpression
import org.jetbrains.kotlin.psi.KtThrowExpression
import org.jetbrains.kotlin.psi.KtTryExpression
import org.jetbrains.kotlin.psi.KtWhenConditionInRange
import org.jetbrains.kotlin.psi.KtWhenConditionIsPattern
import org.jetbrains.kotlin.psi.KtWhenConditionWithExpression
import org.jetbrains.kotlin.psi.KtWhenExpression
import org.jetbrains.kotlin.psi.KtWhileExpression
import org.jetbrains.ktfmt.format.visitor.Indentation.Companion.ZERO

/**
 * Handles formatting of all control flow expressions: `if`, `when`, `while`, `do`, `for`, `try`,
 * `catch`, `finally`, `return`, `throw`, `continue`, `break`. Formatting of all other expressions
 * is handled by [ExpressionFormatter].
 */
interface ControlFlowExpressionFormatter {
  context(_: FormatterStateHolder)
  fun formatReturnExpression(expression: KtReturnExpression)

  context(_: FormatterStateHolder)
  fun formatWhenExpression(expression: KtWhenExpression)

  context(_: FormatterStateHolder)
  fun formatWhenConditionWithExpression(condition: KtWhenConditionWithExpression)

  context(_: FormatterStateHolder)
  fun formatWhenConditionIsPattern(condition: KtWhenConditionIsPattern)

  context(_: FormatterStateHolder)
  fun formatWhenConditionInRange(condition: KtWhenConditionInRange)

  context(_: FormatterStateHolder)
  fun formatIfExpression(expression: KtIfExpression)

  context(_: FormatterStateHolder)
  fun formatForExpression(expression: KtForExpression)

  context(_: FormatterStateHolder)
  fun formatWhileExpression(expression: KtWhileExpression)

  context(_: FormatterStateHolder)
  fun formatDoWhileExpression(expression: KtDoWhileExpression)

  context(_: FormatterStateHolder)
  fun formatBreakExpression(expression: KtBreakExpression)

  context(_: FormatterStateHolder)
  fun formatContinueExpression(expression: KtContinueExpression)

  context(_: FormatterStateHolder)
  fun formatTryExpression(expression: KtTryExpression)

  context(_: FormatterStateHolder)
  fun formatCatchSection(catchClause: KtCatchClause)

  context(_: FormatterStateHolder)
  fun formatFinallySection(finallySection: KtFinallySection)

  context(_: FormatterStateHolder)
  fun formatThrowExpression(expression: KtThrowExpression)
}

internal class ControlFlowExpressionFormatterImpl : ControlFlowExpressionFormatter {
  context(_: FormatterStateHolder)
  override fun formatReturnExpression(expression: KtReturnExpression) {
    builder.sync(expression)
    builder.token("return")
    format(expression.getTargetLabel())
    val returnedExpression = expression.returnedExpression
    if (returnedExpression != null) {
      builder.space()
      format(returnedExpression)
    }
    builder.guessToken(";")
  }

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
                  builder.forcedBreak()
                }
              }
            }
            whenEntry.guard?.let { guard ->
              builder.space()
              emitKeywordWithCondition(
                  "if",
                  guard.getExpression(),
                  surroundConditionWithParens = false,
              )
            }
          }
          val whenExpression = whenEntry.expression
          if (whenEntry.trailingComma != null) {
            builder.forcedBreak()
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
  override fun formatWhenConditionWithExpression(condition: KtWhenConditionWithExpression) {
    builder.sync(condition)
    format(condition.expression)
  }

  context(_: FormatterStateHolder)
  override fun formatWhenConditionIsPattern(condition: KtWhenConditionIsPattern) {
    builder.sync(condition)
    builder.token(if (condition.isNegated) "!is" else "is")
    builder.space()
    format(condition.typeReference)
  }

  context(_: FormatterStateHolder)
  override fun formatWhenConditionInRange(condition: KtWhenConditionInRange) {
    builder.sync(condition)
    builder.token(if (condition.isNegated) "!in" else "in")
    builder.space()
    format(condition.rangeExpression)
  }

  context(_: FormatterStateHolder)
  override fun formatIfExpression(expression: KtIfExpression) {
    builder.sync(expression)
    builder.block {
      emitKeywordWithCondition("if", expression.condition)

      if (expression.then is KtBlockExpression) {
        builder.space()
        builder.block { format(expression.then) }
      } else {
        builder.breakOp(Doc.FillMode.INDEPENDENT, " ", expressionBreakIndent)
        builder.block(expressionBreakIndent) {
          builder.fenceComments()
          format(expression.then)
        }
      }

      if (expression.elseKeyword != null) {
        if (expression.then is KtBlockExpression) {
          builder.space()
        } else {
          builder.breakOp(Doc.FillMode.UNIFIED, " ", ZERO)
        }

        builder.block {
          builder.token("else")
          if (expression.`else` is KtBlockExpression || expression.`else` is KtIfExpression) {
            builder.space()
            builder.block { format(expression.`else`) }
          } else {
            builder.breakOp(Doc.FillMode.INDEPENDENT, " ", expressionBreakIndent)
            builder.block(expressionBreakIndent) { format(expression.`else`) }
          }
        }
      }
    }
  }

  context(_: FormatterStateHolder)
  override fun formatForExpression(expression: KtForExpression) {
    builder.sync(expression)
    builder.block {
      builder.token("for")
      builder.space()
      builder.token("(")
      format(expression.loopParameter)
      builder.space()
      builder.token("in")
      builder.block {
        builder.breakOp(Doc.FillMode.UNIFIED, " ", expressionBreakIndent)
        builder.block(expressionBreakIndent) { format(expression.loopRange) }
      }
      builder.token(")")
      builder.space()
      format(expression.body)
    }
  }

  context(_: FormatterStateHolder)
  override fun formatWhileExpression(expression: KtWhileExpression) {
    builder.sync(expression)
    emitKeywordWithCondition("while", expression.condition)
    builder.space()
    format(expression.body)
  }

  context(_: FormatterStateHolder)
  override fun formatDoWhileExpression(expression: KtDoWhileExpression) {
    builder.sync(expression)
    builder.token("do")
    builder.space()
    if (expression.body != null) {
      format(expression.body)
      builder.space()
    }
    emitKeywordWithCondition("while", expression.condition)
  }

  context(_: FormatterStateHolder)
  override fun formatBreakExpression(expression: KtBreakExpression) {
    builder.sync(expression)
    builder.token("break")
    format(expression.labelQualifier)
  }

  context(_: FormatterStateHolder)
  override fun formatContinueExpression(expression: KtContinueExpression) {
    builder.sync(expression)
    builder.token("continue")
    format(expression.labelQualifier)
  }

  context(_: FormatterStateHolder)
  override fun formatTryExpression(expression: KtTryExpression) {
    builder.sync(expression)
    builder.token("try")
    builder.space()
    format(expression.tryBlock)
    for (catchClause in expression.catchClauses) {
      format(catchClause)
    }
    format(expression.finallyBlock)
  }

  context(_: FormatterStateHolder)
  override fun formatCatchSection(catchClause: KtCatchClause) {
    builder.sync(catchClause)
    builder.space()
    builder.token("catch")
    builder.space()
    builder.block {
      builder.token("(")
      builder.block(expressionBreakIndent) {
        builder.breakOp(Doc.FillMode.UNIFIED, "", ZERO)
        format(catchClause.catchParameter)
        builder.guessToken(",")
      }
    }
    builder.token(")")
    builder.space()
    format(catchClause.catchBody)
  }

  context(_: FormatterStateHolder)
  override fun formatFinallySection(finallySection: KtFinallySection) {
    builder.sync(finallySection)
    builder.space()
    builder.token("finally")
    builder.space()
    format(finallySection.finalExpression)
  }

  context(_: FormatterStateHolder)
  override fun formatThrowExpression(expression: KtThrowExpression) {
    builder.sync(expression)
    builder.token("throw")
    builder.space()
    format(expression.thrownExpression)
  }

  /**
   * Emits a key word followed by a condition, e.g. `if (b)` or `while (c < d )`
   *
   * @param surroundConditionWithParens a flag to control whether parens surrounds the condition.
   *   For example, guard conditions do not use parens.
   */
  context(_: FormatterStateHolder)
  private fun emitKeywordWithCondition(
      keyword: String,
      condition: KtExpression?,
      surroundConditionWithParens: Boolean = true,
  ) {
    if (condition == null) {
      builder.token(keyword)
      return
    }

    builder.block {
      builder.token(keyword)
      builder.space()
      if (surroundConditionWithParens) {
        builder.token("(")
      }
      if (options.manageTrailingCommas) {
        builder.block(expressionBreakIndent) {
          builder.breakOp(Doc.FillMode.UNIFIED, "", ZERO)
          format(condition)
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
