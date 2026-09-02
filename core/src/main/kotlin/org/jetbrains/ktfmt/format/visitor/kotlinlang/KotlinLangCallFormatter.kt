package org.jetbrains.ktfmt.format.visitor.kotlinlang

import com.google.googlejavaformat.Doc
import com.google.googlejavaformat.Output.BreakTag
import java.util.Optional
import org.jetbrains.kotlin.psi.KtArrayAccessExpression
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtPostfixExpression
import org.jetbrains.kotlin.psi.KtQualifiedExpression
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.psi.KtWhenExpression
import org.jetbrains.ktfmt.format.visitor.CallFormatterImpl
import org.jetbrains.ktfmt.format.visitor.FormatterStateHolder
import org.jetbrains.ktfmt.format.visitor.Indentation
import org.jetbrains.ktfmt.format.visitor.Indentation.Companion.ZERO
import org.jetbrains.ktfmt.format.visitor.block
import org.jetbrains.ktfmt.format.visitor.breakOp
import org.jetbrains.ktfmt.format.visitor.builder
import org.jetbrains.ktfmt.format.visitor.chainRoot
import org.jetbrains.ktfmt.format.visitor.chainedSelectorsHaveValueArguments
import org.jetbrains.ktfmt.format.visitor.computeGroups
import org.jetbrains.ktfmt.format.visitor.expressionBreakIndent
import org.jetbrains.ktfmt.format.visitor.format
import org.jetbrains.ktfmt.format.visitor.inImport
import org.jetbrains.ktfmt.format.visitor.isBlockLikeCall
import org.jetbrains.ktfmt.format.visitor.isChainedBlockLikeCall
import org.jetbrains.ktfmt.format.visitor.isChainedScopingFunction
import org.jetbrains.ktfmt.format.visitor.isMultilineScopingFunction
import org.jetbrains.ktfmt.format.visitor.open
import org.jetbrains.ktfmt.format.visitor.options
import org.jetbrains.ktfmt.format.visitor.sync
import org.jetbrains.ktfmt.format.visitor.token
import org.jetbrains.ktfmt.format.visitor.trailingLambda

/**
 * Custom call formatter for KotlinLang style that handles indentation of block-like calls with or
 * without chained call (see #633). Currently, it extracts the behaviour introduced in #634 to an
 * experimental engine API. Motivation: we don't want to change the behaviour of the existing
 * formatter while we're also evolving the new Kotlin Lang style.
 */
internal class KotlinLangCallFormatterImpl : CallFormatterImpl() {
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
      expression.isChainedScopingFunction &&
          expression.chainRoot.isMultilineScopingFunction &&
          !chainedSelectorsHaveValueArguments(expression) -> {
        formatChainedScopingFunction(expression, emitLeadingBreak = false)
      }
      expression.isChainedBlockLikeCall -> {
        formatChainedBlockLikeCall(expression, emitLeadingBreak = false)
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
}
