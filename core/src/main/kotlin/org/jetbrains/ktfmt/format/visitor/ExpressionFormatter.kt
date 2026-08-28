package org.jetbrains.ktfmt.format.visitor

import com.google.googlejavaformat.Doc
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtBinaryExpressionWithTypeRHS
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtCallableReferenceExpression
import org.jetbrains.kotlin.psi.KtClassLiteralExpression
import org.jetbrains.kotlin.psi.KtCollectionLiteralExpression
import org.jetbrains.kotlin.psi.KtConstantExpression
import org.jetbrains.kotlin.psi.KtContainerNode
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtIsExpression
import org.jetbrains.kotlin.psi.KtLabelReferenceExpression
import org.jetbrains.kotlin.psi.KtLabeledExpression
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtParenthesizedExpression
import org.jetbrains.kotlin.psi.KtPostfixExpression
import org.jetbrains.kotlin.psi.KtPrefixExpression
import org.jetbrains.kotlin.psi.KtQualifiedExpression
import org.jetbrains.kotlin.psi.KtReferenceExpression
import org.jetbrains.kotlin.psi.KtSimpleNameExpression
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.psi.KtSuperExpression
import org.jetbrains.kotlin.psi.KtThisExpression
import org.jetbrains.kotlin.psi.KtValueArgument
import org.jetbrains.ktfmt.format.WhitespaceTombstones
import org.jetbrains.ktfmt.format.visitor.Indentation.Companion.ZERO

/**
 * Formatter that handles formatting of all basic **non-control flow** expressions. Exceptions:
 * - Any function call-related expressions are handled in [CallFormatter]
 */
interface ExpressionFormatter {
  /**
   * Format the right-hand side of an initializer expression, i.e. the expression after `=`
   * (inclusively)
   *
   * @param assignmentOp The symbol that separates the initializer from the expression, see
   *   [org.jetbrains.kotlin.lexer.KtTokens.ALL_ASSIGNMENTS]
   */
  context(_: FormatterStateHolder)
  fun formatInitializerExpression(
      initializer: KtExpression,
      assignmentOp: String,
  )

  context(_: FormatterStateHolder)
  fun formatThisExpression(expression: KtThisExpression)

  context(_: FormatterStateHolder)
  fun formatSimpleNameExpression(expression: KtSimpleNameExpression)

  context(_: FormatterStateHolder)
  fun formatReferenceExpression(expression: KtReferenceExpression)

  context(_: FormatterStateHolder)
  fun formatBinaryExpression(expression: KtBinaryExpression)

  context(_: FormatterStateHolder)
  fun formatPostfixExpression(expression: KtPostfixExpression)

  context(_: FormatterStateHolder)
  fun formatPrefixExpression(expression: KtPrefixExpression)

  context(_: FormatterStateHolder)
  fun formatLabeledExpression(expression: KtLabeledExpression)

  context(_: FormatterStateHolder)
  fun formatConstantExpression(expression: KtConstantExpression)

  context(_: FormatterStateHolder)
  fun formatParenthesizedExpression(expression: KtParenthesizedExpression)

  context(_: FormatterStateHolder)
  fun formatStringTemplateExpression(expression: KtStringTemplateExpression)

  context(_: FormatterStateHolder)
  fun formatSuperExpression(expression: KtSuperExpression)

  context(_: FormatterStateHolder)
  fun formatCallableReferenceExpression(expression: KtCallableReferenceExpression)

  context(_: FormatterStateHolder)
  fun formatClassLiteralExpression(expression: KtClassLiteralExpression)

  context(_: FormatterStateHolder)
  fun formatIsExpression(expression: KtIsExpression)

  context(_: FormatterStateHolder)
  fun formatBinaryWithTypeRHSExpression(expression: KtBinaryExpressionWithTypeRHS)

  context(_: FormatterStateHolder)
  fun formatCollectionLiteralExpression(expression: KtCollectionLiteralExpression)
}

internal class ExpressionFormatterImpl : ExpressionFormatter {
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

  context(_: FormatterStateHolder)
  override fun formatThisExpression(expression: KtThisExpression) {
    builder.sync(expression)
    builder.token("this")
    format(expression.getTargetLabel())
  }

  context(_: FormatterStateHolder)
  override fun formatSimpleNameExpression(expression: KtSimpleNameExpression) {
    builder.sync(expression)
    when (expression) {
      is KtLabelReferenceExpression -> {
        val identifier = expression.getIdentifier()?.text ?: fail()
        if (expression.text[0] == '@') {
          builder.token("@")
          builder.token(identifier)
        } else {
          builder.token(identifier)
          builder.token("@")
        }
      }
      else -> {
        if (expression.text.isNotEmpty()) {
          builder.token(expression.text)
        }
      }
    }
  }

  context(_: FormatterStateHolder)
  override fun formatReferenceExpression(expression: KtReferenceExpression) {
    builder.sync(expression)
    builder.token(expression.text)
  }

  /**
   * We unwrap the left most expression from a chain of binary expressions and format the
   * expressions left to right. For example `a + b + c + d` is parsed as `a + (b + (c + d))`, but we
   * format is as `((a + b) + c) + d`.
   *
   * @see [KtBinaryExpression.fullChain].
   */
  context(_: FormatterStateHolder)
  override fun formatBinaryExpression(expression: KtBinaryExpression) {
    builder.sync(expression)
    val op = expression.operationToken

    if (KtTokens.ALL_ASSIGNMENTS.contains(op) && expression.right.isLambdaOrScopingFunction) {
      // Assignments are statements in Kotlin; we don't have to worry about compound assignment.
      format(expression.left)
      builder.space()
      formatInitializerExpression(expression.right!!, expression.operationReference.text)
      return
    }

    val allExpressions = expression.fullChain
    format(allExpressions.first().left)
    for ((index, currentExpression) in allExpressions.withIndex()) {
      formatBinaryOperationToken(currentExpression, index == 0)
      format(currentExpression.right)
    }
    builder.close()
  }

  context(_: FormatterStateHolder)
  private fun formatBinaryOperationToken(expression: KtBinaryExpression, isFirst: Boolean = false) {
    when (expression.operationToken) {
      KtTokens.RANGE,
      KtTokens.RANGE_UNTIL -> {
        if (isFirst) {
          builder.open(expressionBreakIndent)
        }
        builder.token(expression.operationReference.text)
      }
      KtTokens.ELVIS -> {
        if (isFirst) {
          builder.open(expressionBreakIndent)
        }
        builder.breakOp(Doc.FillMode.UNIFIED, " ", ZERO)
        builder.token(expression.operationReference.text)
        builder.space()
      }
      else -> {
        builder.space()
        if (isFirst) {
          builder.open(expressionBreakIndent)
        }
        builder.token(expression.operationReference.text)
        val fillMode =
            if (expression.operationReference.hasLineBreakingCommentBefore) Doc.FillMode.INDEPENDENT
            else Doc.FillMode.UNIFIED
        builder.breakOp(fillMode, " ", ZERO)
      }
    }
  }

  context(_: FormatterStateHolder)
  override fun formatPostfixExpression(expression: KtPostfixExpression) {
    builder.sync(expression)
    builder.block {
      val baseExpression = expression.baseExpression
      val operator = expression.operationReference.text

      format(baseExpression)
      if (
          baseExpression is KtPostfixExpression &&
              baseExpression.operationReference.text.last() == operator.first()
      ) {
        builder.space()
      }
      builder.token(operator)
    }
  }

  context(_: FormatterStateHolder)
  override fun formatPrefixExpression(expression: KtPrefixExpression) {
    builder.sync(expression)
    builder.block {
      val baseExpression = expression.baseExpression
      val operator = expression.operationReference.text

      builder.token(operator)
      if (
          baseExpression is KtPrefixExpression &&
              operator.last() == baseExpression.operationReference.text.first()
      ) {
        builder.space()
      }
      format(baseExpression)
    }
  }

  context(_: FormatterStateHolder)
  override fun formatLabeledExpression(expression: KtLabeledExpression) {
    builder.sync(expression)
    format(expression.labelQualifier)
    if (expression.baseExpression !is KtLambdaExpression) builder.space()
    format(expression.baseExpression)
  }

  context(_: FormatterStateHolder)
  override fun formatConstantExpression(expression: KtConstantExpression) {
    builder.sync(expression)
    builder.token(expression.text)
  }

  context(_: FormatterStateHolder)
  override fun formatParenthesizedExpression(expression: KtParenthesizedExpression) {
    builder.sync(expression)
    builder.token("(")
    format(expression.expression)
    builder.token(")")
  }

  context(_: FormatterStateHolder)
  override fun formatStringTemplateExpression(expression: KtStringTemplateExpression) {
    builder.sync(expression)
    builder.token(WhitespaceTombstones.replaceTrailingWhitespaceWithTombstone(expression.text))
  }

  context(_: FormatterStateHolder)
  override fun formatSuperExpression(expression: KtSuperExpression) {
    builder.sync(expression)
    builder.token("super")
    val superTypeQualifier = expression.superTypeQualifier
    if (superTypeQualifier != null) {
      builder.token("<")
      format(superTypeQualifier)
      builder.token(">")
    }
    format(expression.labelQualifier)
  }

  /** Example `String::isNullOrEmpty` */
  context(_: FormatterStateHolder)
  override fun formatCallableReferenceExpression(expression: KtCallableReferenceExpression) {
    builder.sync(expression)
    format(expression.receiverExpression)

    // For some reason, expression.receiverExpression doesn't contain the question-mark token in
    // case of a nullable type, e.g., in String?::isNullOrEmpty.
    // Instead, KtCallableReferenceExpression exposes a method that looks for the QUEST token in
    // its children.
    if (expression.hasQuestionMarks) {
      builder.token("?")
    }

    builder.block(expressionBreakIndent) {
      builder.token("::")
      builder.breakOp(Doc.FillMode.INDEPENDENT, "", ZERO)
      format(expression.callableReference)
    }
  }

  context(_: FormatterStateHolder)
  override fun formatClassLiteralExpression(expression: KtClassLiteralExpression) {
    builder.sync(expression)
    val receiverExpression = expression.receiverExpression
    if (receiverExpression is KtCallExpression) {
      formatFunctionCall(
          receiverExpression.calleeExpression,
          receiverExpression.typeArgumentList,
          receiverExpression.valueArgumentList,
          receiverExpression.trailingLambda,
      )
    } else {
      format(receiverExpression)
    }
    builder.token("::")
    builder.token("class")
  }

  context(_: FormatterStateHolder)
  override fun formatIsExpression(expression: KtIsExpression) {
    builder.sync(expression)
    val openGroupBeforeLeft = expression.leftHandSide !is KtQualifiedExpression
    if (openGroupBeforeLeft) builder.open(ZERO)
    format(expression.leftHandSide)
    if (!openGroupBeforeLeft) builder.open(ZERO)
    val parent = expression.parent
    if (
        parent is KtValueArgument ||
            parent is KtParenthesizedExpression ||
            parent is KtContainerNode
    ) {
      builder.breakOp(Doc.FillMode.UNIFIED, " ", expressionBreakIndent)
    } else {
      builder.space()
    }
    format(expression.operationReference)
    builder.breakOp(Doc.FillMode.INDEPENDENT, " ", expressionBreakIndent)
    builder.block(expressionBreakIndent) { format(expression.typeReference) }
    builder.close()
  }

  context(_: FormatterStateHolder)
  override fun formatBinaryWithTypeRHSExpression(expression: KtBinaryExpressionWithTypeRHS) {
    builder.sync(expression)
    val openGroupBeforeLeft = expression.left !is KtQualifiedExpression
    if (openGroupBeforeLeft) builder.open(ZERO)
    format(expression.left)
    if (!openGroupBeforeLeft) builder.open(ZERO)
    builder.breakOp(Doc.FillMode.UNIFIED, " ", expressionBreakIndent)
    format(expression.operationReference)
    builder.breakOp(Doc.FillMode.INDEPENDENT, " ", expressionBreakIndent)
    builder.block(expressionBreakIndent) { format(expression.right) }
    builder.close()
  }

  context(_: FormatterStateHolder)
  override fun formatCollectionLiteralExpression(expression: KtCollectionLiteralExpression) {
    builder.sync(expression)
    builder.block(expressionBreakIndent) {
      formatCommaSeparatedList(
          expression.getInnerExpressions(),
          forceMultiline = expression.trailingComma != null,
          prefix = "[",
          postfix = "]",
          wrapInBlock = !options.manageTrailingCommas,
      )
    }
  }
}
