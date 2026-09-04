package org.jetbrains.ktfmt.format.visitor.kotlinlang

import com.google.googlejavaformat.Doc
import com.google.googlejavaformat.Output.BreakTag
import java.util.Optional
import org.jetbrains.kotlin.psi.KtArrayAccessExpression
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtParameterList
import org.jetbrains.kotlin.psi.KtPostfixExpression
import org.jetbrains.kotlin.psi.KtQualifiedExpression
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.psi.KtValueArgument
import org.jetbrains.kotlin.psi.KtWhenExpression
import org.jetbrains.ktfmt.format.visitor.CallFormatterImpl
import org.jetbrains.ktfmt.format.visitor.FormatterStateHolder
import org.jetbrains.ktfmt.format.visitor.Indentation
import org.jetbrains.ktfmt.format.visitor.Indentation.Companion.ZERO
import org.jetbrains.ktfmt.format.visitor.block
import org.jetbrains.ktfmt.format.visitor.breakOp
import org.jetbrains.ktfmt.format.visitor.builder
import org.jetbrains.ktfmt.format.visitor.computeGroups
import org.jetbrains.ktfmt.format.visitor.expressionBreakIndent
import org.jetbrains.ktfmt.format.visitor.format
import org.jetbrains.ktfmt.format.visitor.formatAssignmentLikeExpression
import org.jetbrains.ktfmt.format.visitor.formatCommaSeparatedList
import org.jetbrains.ktfmt.format.visitor.inImport
import org.jetbrains.ktfmt.format.visitor.isBlockLikeCall
import org.jetbrains.ktfmt.format.visitor.open
import org.jetbrains.ktfmt.format.visitor.options
import org.jetbrains.ktfmt.format.visitor.sync
import org.jetbrains.ktfmt.format.visitor.token
import org.jetbrains.ktfmt.format.visitor.trailingLambda

/**
 * Custom call formatter for KotlinLang style.
 *
 * - Overrides formatting of qualified expressions. Removes custom handling of chained calls and
 *   routes them all through [emitQualifiedExpression]. Together with the changes in
 *   [KotlinLangExpressionFormatterImpl.formatAssignmentLikeExpression] handles formatting of
 *   qualified expressions in assignment-like expressions allowing to preserve user-defined input.
 *
 * - Does not allow breaks before `->` in lambda expressions
 *
 * - Overrides formatting of call arguments and reuses [formatAssignmentLikeExpression] for named
 *   arguments
 */
internal class KotlinLangCallFormatterImpl : CallFormatterImpl() {
  context(_: FormatterStateHolder)
  override fun formatArgument(
      argument: KtValueArgument,
      wrapInBlock: Boolean,
      brokeBeforeBrace: BreakTag?,
  ) {
    builder.sync(argument)
    val hasArgName = argument.getArgumentName() != null
    val isLambda = argument.getArgumentExpression() is KtLambdaExpression
    if (hasArgName) {
      format(argument.getArgumentName())
      builder.space()
      argument.getArgumentExpression()?.let { formatAssignmentLikeExpression(it) }
      return
    }
    builder.block(ZERO, isEnabled = wrapInBlock) {
      if (argument.isSpread) {
        builder.token("*")
      }
      if (isLambda) {
        formatLambdaExpression(
            argument.getArgumentExpression() as KtLambdaExpression,
            brokeBeforeBrace = brokeBeforeBrace,
        )
      } else {
        format(argument.getArgumentExpression())
      }
    }
  }

  context(_: FormatterStateHolder)
  override fun formatQualifiedExpression(expression: KtQualifiedExpression) {
    builder.sync(expression)
    val receiver = expression.receiverExpression
    when {
      inImport -> {
        format(receiver)
        val selectorExpression = expression.selectorExpression
        if (selectorExpression != null) {
          builder.token(".")
          format(selectorExpression)
        }
      }
      receiver is KtStringTemplateExpression -> {
        builder.block(expressionBreakIndent) {
          format(receiver)
          builder.breakOp(Doc.FillMode.UNIFIED, "", ZERO)
          builder.token(expression.operationSign.value)
          format(expression.selectorExpression)
        }
      }
      receiver is KtWhenExpression -> {
        builder.block {
          format(receiver)
          builder.token(expression.operationSign.value)
          format(expression.selectorExpression)
        }
      }
      else -> {
        emitQualifiedExpression(expression)
      }
    }
  }

  context(_: FormatterStateHolder)
  override fun emitQualifiedExpression(expression: KtExpression) {
    val groupingInfos = expression.computeGroups(expressionBreakIndent)
    builder.block(expressionBreakIndent) {
      // allows adjusting arguments indentation if a break will be made
      val nameTag = BreakTag()
      for ((ktExpression, openingGroups, closingGroups, isTrailingLambda, isLast) in
          groupingInfos) {
        if (ktExpression is KtQualifiedExpression) {
          builder.breakOp(Doc.FillMode.UNIFIED, "", ZERO, Optional.of(nameTag))
        }

        var deferredCallArguments: DeferredCallArguments? = null
        repeat(openingGroups) { builder.open(ZERO) }
        when (ktExpression) {
          is KtQualifiedExpression if ktExpression.selectorExpression is KtCallExpression -> {
            builder.token(ktExpression.operationSign.value)
            val selectorExpression = ktExpression.selectorExpression as KtCallExpression

            // emit `doIt` from `doIt(1, 2) { it }`
            format(selectorExpression.calleeExpression)

            val isLastPartOrBlockLikeCall =
                isLast || !options.manageTrailingCommas && selectorExpression.isBlockLikeCall
            val argsIndentElse = if (isLastPartOrBlockLikeCall) ZERO else expressionBreakIndent
            val lambdaIndentElse = if (isTrailingLambda) -expressionBreakIndent else ZERO

            // remember to emit `(1, 2) { it }` from `doIt(1, 2) { it }`
            deferredCallArguments =
                DeferredCallArguments(
                    selectorExpression,
                    Indentation.If(nameTag, expressionBreakIndent, argsIndentElse),
                    Indentation.If(nameTag, ZERO, lambdaIndentElse),
                )
          }
          is KtQualifiedExpression -> {
            builder.token(ktExpression.operationSign.value)
            format(ktExpression.selectorExpression)
          }
          is KtArrayAccessExpression -> formatArrayAccessBrackets(ktExpression)
          is KtPostfixExpression -> builder.token(ktExpression.operationReference.text)
          else -> format(ktExpression)
        }
        repeat(closingGroups) { builder.close() }

        deferredCallArguments?.let { (callee, argumentsIndent, lambdaIndent) ->
          formatFunctionCall(
              null,
              callee.typeArgumentList,
              callee.valueArgumentList,
              callee.trailingLambda,
              argumentsIndent = argumentsIndent,
              lambdaIndent = lambdaIndent,
          )
        }
      }
    }
  }

  context(_: FormatterStateHolder)
  override fun formatLambdaArguments(
      valueParameterList: KtParameterList,
      valueParametersIndent: Indentation,
      arrowIndent: Indentation,
  ) {
    builder.space()
    builder.block(valueParametersIndent) { formatCommaSeparatedList(valueParameterList.parameters) }
    builder.block(arrowIndent) {
      if (valueParameterList.trailingComma != null) {
        builder.token(",")
        builder.space()
      } else if (valueParameterList.parameters.isNotEmpty()) {
        builder.space()
      }
      builder.token("->")
    }
  }
}
