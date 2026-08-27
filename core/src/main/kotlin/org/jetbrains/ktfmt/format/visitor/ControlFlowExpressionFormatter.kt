package org.jetbrains.ktfmt.format.visitor

import com.google.googlejavaformat.Doc
import com.google.googlejavaformat.OpsBuilder
import java.util.Optional
import org.jetbrains.kotlin.lexer.KtTokens
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
interface ControlFlowExpressionFormatter : KotlinAstFormatter {

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

  override fun formatWhenExpression(expression: KtWhenExpression) {
    builder.sync(expression)
    builder.block(ZERO) {
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
          builder.block(ZERO) {
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

  override fun formatWhenConditionWithExpression(condition: KtWhenConditionWithExpression) {
    builder.sync(condition)
    format(condition.expression)
  }

  override fun formatWhenConditionIsPattern(condition: KtWhenConditionIsPattern) {
    builder.sync(condition)
    builder.token(if (condition.isNegated) "!is" else "is")
    builder.space()
    format(condition.typeReference)
  }

  /** Example `in 1..2` as part of a when expression */
  override fun formatWhenConditionInRange(condition: KtWhenConditionInRange) {
    builder.sync(condition)
    // TODO: replace with 'condition.isNegated' once https://youtrack.jetbrains.com/issue/KT-34395
    // is fixed.
    val isNegated = condition.firstChild?.node?.findChildByType(KtTokens.NOT_IN) != null
    builder.token(if (isNegated) "!in" else "in")
    builder.space()
    format(condition.rangeExpression)
  }

  override fun formatIfExpression(expression: KtIfExpression) {
    builder.sync(expression)
    builder.block(ZERO) {
      emitKeywordWithCondition("if", expression.condition)

      if (expression.then is KtBlockExpression) {
        builder.space()
        builder.block(ZERO) { format(expression.then) }
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

        builder.block(ZERO) {
          builder.token("else")
          if (expression.`else` is KtBlockExpression || expression.`else` is KtIfExpression) {
            builder.space()
            builder.block(ZERO) { format(expression.`else`) }
          } else {
            builder.breakOp(Doc.FillMode.INDEPENDENT, " ", expressionBreakIndent)
            builder.block(expressionBreakIndent) { format(expression.`else`) }
          }
        }
      }
    }
  }

  /** Example `for (i in items) { ... }` */
  override fun formatForExpression(expression: KtForExpression) {
    builder.sync(expression)
    builder.block(ZERO) {
      builder.token("for")
      builder.space()
      builder.token("(")
      format(expression.loopParameter)
      builder.space()
      builder.token("in")
      builder.block(ZERO) {
        builder.breakOp(Doc.FillMode.UNIFIED, " ", expressionBreakIndent)
        builder.block(expressionBreakIndent) { format(expression.loopRange) }
      }
      builder.token(")")
      builder.space()
      format(expression.body)
    }
  }

  /** Example `while (a < b) { ... }` */
  override fun formatWhileExpression(expression: KtWhileExpression) {
    builder.sync(expression)
    emitKeywordWithCondition("while", expression.condition)
    builder.space()
    format(expression.body)
  }

  /** Example `do { ... } while (a < b)` */
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

  /** Example `break` or `break@foo` in a loop */
  override fun formatBreakExpression(expression: KtBreakExpression) {
    builder.sync(expression)
    builder.token("break")
    format(expression.labelQualifier)
  }

  /** Example `continue` or `continue@foo` in a loop */
  override fun formatContinueExpression(expression: KtContinueExpression) {
    builder.sync(expression)
    builder.token("continue")
    format(expression.labelQualifier)
  }

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

  override fun formatCatchSection(catchClause: KtCatchClause) {
    builder.sync(catchClause)
    builder.space()
    builder.token("catch")
    builder.space()
    builder.block(ZERO) {
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

  override fun formatFinallySection(finallySection: KtFinallySection) {
    builder.sync(finallySection)
    builder.space()
    builder.token("finally")
    builder.space()
    format(finallySection.finalExpression)
  }

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
  private fun emitKeywordWithCondition(
      keyword: String,
      condition: KtExpression?,
      surroundConditionWithParens: Boolean = true,
  ) {
    if (condition == null) {
      builder.token(keyword)
      return
    }

    builder.block(ZERO) {
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
        builder.block(ZERO) { format(condition) }
      }
    }
    if (surroundConditionWithParens) {
      builder.token(")")
    }
  }
}
