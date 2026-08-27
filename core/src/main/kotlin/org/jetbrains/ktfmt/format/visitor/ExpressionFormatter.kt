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

  /** Example `this` or `this@Foo` */
  override fun formatThisExpression(expression: KtThisExpression) {
    builder.sync(expression)
    builder.token("this")
    format(expression.getTargetLabel())
  }

  /** Example `Foo` or `@Foo` */
  override fun formatSimpleNameExpression(expression: KtSimpleNameExpression) {
    builder.sync(expression)
    when (expression) {
      is KtLabelReferenceExpression -> {
        if (expression.text[0] == '@') {
          builder.token("@")
          builder.token(expression.getIdentifier()?.text ?: fail())
        } else {
          builder.token(expression.getIdentifier()?.text ?: fail())
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

  override fun formatReferenceExpression(expression: KtReferenceExpression) {
    builder.sync(expression)
    builder.token(expression.text)
  }

  /**
   * For example `a + b`, `a + b + c` or `a..b`
   *
   * The extra handling here drills to the left most expression and handles it for long chains of
   * binary expressions that are formatted not accordingly to the associative values That is, we
   * want to think of `a + b + c` as `(a + b) + c`, whereas the AST parses it as `a + (b + c)`
   */
  override fun formatBinaryExpression(expression: KtBinaryExpression) {
    builder.sync(expression)
    val op = expression.operationToken

    if (KtTokens.ALL_ASSIGNMENTS.contains(op) && expression.right.isLambdaOrScopingFunction) {
      // Assignments are statements in Kotlin; we don't have to worry about compound assignment.
      format(expression.left)
      builder.space()
      builder.token(expression.operationReference.text)
      formatLambdaOrScopingFunction(expression.right)
      return
    }

    val parts =
        ArrayDeque<KtBinaryExpression>().apply {
          var current: KtExpression? = expression
          while (current is KtBinaryExpression && current.operationToken == op) {
            addFirst(current)
            current = current.left
          }
        }

    val leftMostExpression = parts.first()
    format(leftMostExpression.left)
    for (leftExpression in parts) {
      val isFirst = leftExpression === leftMostExpression

      when (leftExpression.operationToken) {
        KtTokens.RANGE,
        KtTokens.RANGE_UNTIL -> {
          if (isFirst) {
            builder.open(expressionBreakIndent)
          }
          builder.token(leftExpression.operationReference.text)
        }
        KtTokens.ELVIS -> {
          if (isFirst) {
            builder.open(expressionBreakIndent)
          }
          builder.breakOp(Doc.FillMode.UNIFIED, " ", ZERO)
          builder.token(leftExpression.operationReference.text)
          builder.space()
        }
        else -> {
          builder.space()
          if (isFirst) {
            builder.open(expressionBreakIndent)
          }
          builder.token(leftExpression.operationReference.text)
          val fillMode =
              if (leftExpression.operationReference.hasLineBreakingCommentBefore)
                  Doc.FillMode.INDEPENDENT
              else Doc.FillMode.UNIFIED
          builder.breakOp(fillMode, " ", ZERO)
        }
      }
      format(leftExpression.right)
    }
    builder.close()
  }

  override fun formatPostfixExpression(expression: KtPostfixExpression) {
    builder.sync(expression)
    builder.block(ZERO) {
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

  override fun formatPrefixExpression(expression: KtPrefixExpression) {
    builder.sync(expression)
    builder.block(ZERO) {
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

  override fun formatLabeledExpression(expression: KtLabeledExpression) {
    builder.sync(expression)
    format(expression.labelQualifier)
    if (expression.baseExpression !is KtLambdaExpression) {
      builder.space()
    }
    format(expression.baseExpression)
  }

  override fun formatConstantExpression(expression: KtConstantExpression) {
    builder.sync(expression)
    builder.token(expression.text)
  }

  /** Example `(1 + 1)` */
  override fun formatParenthesizedExpression(expression: KtParenthesizedExpression) {
    builder.sync(expression)
    builder.token("(")
    format(expression.expression)
    builder.token(")")
  }

  /** Example `"Hello $world!"` or `"""Hello world!"""` */
  override fun formatStringTemplateExpression(expression: KtStringTemplateExpression) {
    builder.sync(expression)
    builder.token(WhitespaceTombstones.replaceTrailingWhitespaceWithTombstone(expression.text))
  }

  /** Example `super` in `super.doIt(5)` or `super<Foo>` in `super<Foo>.doIt(5)` */
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

  /** Example `a is Int` or `b !is Int` */
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

  /** Example `a as Int` or `a as? Int` */
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

  /**
   * Example:
   * ```
   * fun f() {
   *   val a: Array<Int> = [1, 2, 3]
   * }
   * ```
   */
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
