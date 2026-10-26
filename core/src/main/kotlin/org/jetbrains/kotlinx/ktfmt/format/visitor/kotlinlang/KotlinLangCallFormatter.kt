package org.jetbrains.kotlinx.ktfmt.format.visitor.kotlinlang

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
import org.jetbrains.kotlin.psi.KtValueArgument
import org.jetbrains.kotlin.psi.KtWhenExpression
import org.jetbrains.kotlinx.ktfmt.format.visitor.CallFormatterImpl
import org.jetbrains.kotlinx.ktfmt.format.visitor.FormatterStateHolder
import org.jetbrains.kotlinx.ktfmt.format.visitor.GroupInfo
import org.jetbrains.kotlinx.ktfmt.format.visitor.Indentation
import org.jetbrains.kotlinx.ktfmt.format.visitor.Indentation.Companion.ZERO
import org.jetbrains.kotlinx.ktfmt.format.visitor.assignmentBreaks
import org.jetbrains.kotlinx.ktfmt.format.visitor.block
import org.jetbrains.kotlinx.ktfmt.format.visitor.breakOp
import org.jetbrains.kotlinx.ktfmt.format.visitor.builder
import org.jetbrains.kotlinx.ktfmt.format.visitor.computeGroups
import org.jetbrains.kotlinx.ktfmt.format.visitor.expressionBreakIndent
import org.jetbrains.kotlinx.ktfmt.format.visitor.format
import org.jetbrains.kotlinx.ktfmt.format.visitor.formatAssignmentLikeExpression
import org.jetbrains.kotlinx.ktfmt.format.visitor.formatCommaSeparatedList
import org.jetbrains.kotlinx.ktfmt.format.visitor.inImport
import org.jetbrains.kotlinx.ktfmt.format.visitor.isBlockLikeCall
import org.jetbrains.kotlinx.ktfmt.format.visitor.isBlockLikeChainPart
import org.jetbrains.kotlinx.ktfmt.format.visitor.open
import org.jetbrains.kotlinx.ktfmt.format.visitor.options
import org.jetbrains.kotlinx.ktfmt.format.visitor.startsWithBlockLikeCall
import org.jetbrains.kotlinx.ktfmt.format.visitor.sync
import org.jetbrains.kotlinx.ktfmt.format.visitor.token
import org.jetbrains.kotlinx.ktfmt.format.visitor.trailingLambda

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
    val hasBlockLikeChainParts = groupingInfos.any { it.expression.startsWithBlockLikeCall }
    val layout = computeChainLayout(expression, groupingInfos)

    /**
     * If [ChainLayout.hasAssignmentBreakTag] is true, selector breaks are always enabled.
     * Otherwise, we only enable them only after we encountered the first non-block call in the
     * chain. This allows us to avoid unconventional breaks in the call chains with block-like
     * calls:
     * ```
     * val a = listOf(
     *    10,
     * ).filter {
     *    val square = it * it
     *    square > 99
     * }.count() // first non-block like call, selector breaks are enabled
     *    .bar()
     *    .baz()
     * ```
     */
    var selectorBreaksEnabled = !hasBlockLikeChainParts

    builder.block(layout.chainIndent) {
      for (group in groupingInfos) {
        val expression = group.expression
        if (expression.isNonBlockCall()) {
          selectorBreaksEnabled = true
        }
        emitSelectorBreak(group, selectorBreaksEnabled, layout)
        if (expression is KtQualifiedExpression && expression.selectorExpression.isNonBlockCall()) {
          selectorBreaksEnabled = true
        }

        repeat(group.openingGroups) { builder.open(ZERO) }
        val deferredCallArguments = emitGroup(group, layout)
        repeat(group.closingGroups) { builder.close() }

        deferredCallArguments?.let { emitDeferredCallArguments(it, layout.deferredCallIndent) }
      }
    }
  }

  /**
   * Helper class that stores the layout properties of a qualified expression:
   *
   * @property hasAssignmentBreakTag denotes whether this qualified expression chain is a part of an
   *   assignment-like expression with a line break before it
   * @property selectorBreakTag stores whether a break was taken after the selector, allows
   *   adjusting arguments indentation if a break will be made
   * @property chainIndent indentation level for this qualified expression
   * @property deferredCallIndent indentation level for the deferred call arguments
   */
  private data class ChainLayout(
      val hasAssignmentBreakTag: Boolean,
      val selectorBreakTag: BreakTag,
      val chainIndent: Indentation,
      val deferredCallIndent: Indentation,
  )

  context(_: FormatterStateHolder)
  private fun computeChainLayout(
      expression: KtExpression,
      groupingInfos: List<GroupInfo>,
  ): ChainLayout {
    val assignmentLineBreakTag = assignmentBreaks[expression]
    val inAssignmentWithLineBreak = assignmentLineBreakTag != null

    /**
     * If this expression is a call chain RHS of an assignment-like expression, and we already have
     * a line break after the assignment operator, we don't need to add an extra indent level:
     * ```
     * val x =
     *     foo(
     *         // don't need extra indent
     *         a = 10,
     *         b = 20
     *     )
     *
     * val x = foo(
     *         // need extra indent
     *         a = 10,
     *         b = 20
     *     )
     * ```
     *
     * When the expression starts on the same line as the assignment operator, selector breaks
     * provide the continuation indent instead.
     */
    val chainIndent =
        when {
          !inAssignmentWithLineBreak -> ZERO
          groupingInfos.first().expression is KtCallExpression ->
              Indentation.If(assignmentLineBreakTag, ZERO, expressionBreakIndent)
          else -> expressionBreakIndent
        }

    val nameTag = BreakTag()
    val deferredCallIndent =
        if (inAssignmentWithLineBreak) ZERO
        else Indentation.If(nameTag, expressionBreakIndent, ZERO)
    return ChainLayout(
        hasAssignmentBreakTag = inAssignmentWithLineBreak,
        selectorBreakTag = nameTag,
        chainIndent = chainIndent,
        deferredCallIndent = deferredCallIndent,
    )
  }

  private fun KtExpression?.isNonBlockCall(): Boolean =
      this is KtCallExpression && !isBlockLikeChainPart

  context(_: FormatterStateHolder)
  private fun emitSelectorBreak(
      group: GroupInfo,
      selectorBreaksEnabled: Boolean,
      layout: ChainLayout,
  ) {
    val expression = group.expression
    if (expression !is KtQualifiedExpression) return

    if (layout.hasAssignmentBreakTag) {
      builder.breakOp(Doc.FillMode.UNIFIED, "", ZERO, Optional.of(layout.selectorBreakTag))
    } else if (selectorBreaksEnabled) {
      builder.breakOp(
          Doc.FillMode.UNIFIED,
          "",
          expressionBreakIndent,
          Optional.of(layout.selectorBreakTag),
      )
    }
  }

  context(_: FormatterStateHolder)
  private fun emitGroup(group: GroupInfo, layout: ChainLayout): DeferredCallArguments? {
    when (val ktExpression = group.expression) {
      is KtQualifiedExpression if ktExpression.selectorExpression is KtCallExpression -> {
        builder.token(ktExpression.operationSign.value)
        val selectorExpression = ktExpression.selectorExpression as KtCallExpression

        // emit `doIt` from `doIt(1, 2) { it }`
        format(selectorExpression.calleeExpression)
        val isLastPartOrBlockLikeCall =
            group.isLast || !options.manageTrailingCommas && selectorExpression.isBlockLikeCall
        val argsIndentElse =
            if (layout.hasAssignmentBreakTag && isLastPartOrBlockLikeCall) {
              ZERO
            } else {
              expressionBreakIndent
            }
        val lambdaIndentElse = if (group.isTrailingLambda) -layout.chainIndent else ZERO

        // remember to emit `(1, 2) { it }` from `doIt(1, 2) { it }`
        return DeferredCallArguments(
            selectorExpression,
            Indentation.If(layout.selectorBreakTag, expressionBreakIndent, argsIndentElse),
            if (layout.hasAssignmentBreakTag) {
              Indentation.If(layout.selectorBreakTag, ZERO, lambdaIndentElse)
            } else {
              ZERO
            },
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
    return null
  }

  context(_: FormatterStateHolder)
  private fun emitDeferredCallArguments(
      deferredCallArguments: DeferredCallArguments,
      deferredCallIndent: Indentation,
  ) {
    val (callee, argumentsIndent, lambdaIndent) = deferredCallArguments
    builder.block(deferredCallIndent) {
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

  context(_: FormatterStateHolder)
  override fun formatLambdaParameters(
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
