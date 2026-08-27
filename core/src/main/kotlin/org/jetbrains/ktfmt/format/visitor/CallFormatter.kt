package org.jetbrains.ktfmt.format.visitor

import com.google.googlejavaformat.Doc
import com.google.googlejavaformat.OpsBuilder
import com.google.googlejavaformat.Output.BreakTag
import java.util.Optional
import org.jetbrains.kotlin.com.intellij.psi.PsiComment
import org.jetbrains.kotlin.com.intellij.psi.PsiElement
import org.jetbrains.kotlin.psi.KtArrayAccessExpression
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtLambdaArgument
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtParameterList
import org.jetbrains.kotlin.psi.KtPostfixExpression
import org.jetbrains.kotlin.psi.KtQualifiedExpression
import org.jetbrains.kotlin.psi.KtReturnExpression
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.psi.KtTypeArgumentList
import org.jetbrains.kotlin.psi.KtValueArgument
import org.jetbrains.kotlin.psi.KtValueArgumentList
import org.jetbrains.kotlin.psi.KtWhenExpression
import org.jetbrains.kotlin.psi.psiUtil.children
import org.jetbrains.kotlin.psi.psiUtil.startsWithComment
import org.jetbrains.ktfmt.format.visitor.Indentation.Companion.ZERO

/**
 * Formatter that handles the formatting of anything related to call expressions:
 * - Function calls
 * - Function arguments
 * - Lambda expressions
 * - Qualified expressions
 * - Array access expressions
 */
interface CallFormatter : KotlinAstFormatter {
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
      builder.token("=")
      if (isLambda) {
        builder.space()
      }
    }
    val indent = if (hasArgName && !isLambda) expressionBreakIndent else ZERO
    builder.block(indent, isEnabled = wrapInBlock) {
      if (hasArgName && !isLambda) {
        builder.breakOp(Doc.FillMode.INDEPENDENT, " ", ZERO)
      }
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

  override fun formatCallExpression(callExpression: KtCallExpression) {
    builder.sync(callExpression)
    with(callExpression) {
      formatFunctionCall(
          calleeExpression,
          typeArgumentList,
          valueArgumentList,
          trailingLambda,
      )
    }
  }

  /**
   * Format a single function call expression.
   *
   * @param callee the function call expression
   * @param typeArgumentList the type arguments of the function call expression
   * @param argumentList the value arguments of the function call expression
   * @param trailingLambda trailing lambda arguments of the call expression
   * @param argumentsIndent how to indent [argumentList], if present
   * @param lambdaIndent how to indent [trailingLambda], if present
   */
  override fun formatFunctionCall(
      callee: KtExpression?,
      typeArgumentList: KtTypeArgumentList?,
      argumentList: KtValueArgumentList?,
      trailingLambda: KtLambdaArgument?,
      argumentsIndent: Indentation,
      lambdaIndent: Indentation,
  ) {
    // Apply the lambda indent to the callee, type args, value args, and the lambda.
    // This is undone for the first three by the negative lambda indent.
    // This way they're in one block, and breaks in the argument list cause a break in the lambda.
    builder.block(lambdaIndent) {

      // Used to keep track of whether or not we need to indent the lambda
      // This is based on if there is a break in the argument list
      var brokeBeforeBrace: BreakTag? = null

      builder.block(-lambdaIndent) {
        format(callee)
        builder.block(argumentsIndent) {
          builder.block { format(typeArgumentList) }
          if (argumentList != null) {
            brokeBeforeBrace = formatValueArgumentList(argumentList)
          }
        }
      }
      trailingLambda?.let {
        builder.space()
        formatArgument(
            it,
            wrapInBlock = false,
            brokeBeforeBrace = brokeBeforeBrace,
        )
      }
    }
  }

  override fun formatLambdaExpression(
      lambdaExpression: KtLambdaExpression,
      brokeBeforeBrace: BreakTag?,
  ) {
    builder.sync(lambdaExpression)

    val bodyExpression = lambdaExpression.bodyExpression ?: fail()
    val hasStatements = bodyExpression.children.isNotEmpty()
    val hasComments = bodyExpression.children().any { it is PsiComment }

    val hasDeclaration =
        lambdaExpression.valueParameters.isNotEmpty() ||
            lambdaExpression.functionLiteral.arrow != null
    val hasBody = hasDeclaration || hasStatements || hasComments

    /**
     * Enable correct formatting of the `fun foo() = scope {` syntax.
     *
     * We can't denote the lambda (+ scope function) as a block, since (for multiline lambdas) the
     * rectangle rule would force the entire lambda onto a lower line. Instead, we conditionally
     * indent all the interior levels of the lambda based on whether we had to break before the
     * opening brace (or scope function). This mimics the look of a block when the break is taken.
     *
     * These conditional indents should not be used inside interior blocks, since that would apply
     * the condition twice.
     */
    val bodyIndent =
        Indentation.If(brokeBeforeBrace, blockIndent + expressionBreakIndent, blockIndent)
    val declarationIndent =
        Indentation.If(brokeBeforeBrace, expressionBreakIndent * 2, expressionBreakIndent)
    val closingBraceIndent = Indentation.If(brokeBeforeBrace, expressionBreakIndent, ZERO)

    builder.token("{")

    if (hasDeclaration) {
      formatLambdaArguments(
          lambdaExpression.functionLiteral.valueParameterList!!,
          declarationIndent,
          bodyIndent,
      )
    }

    if (hasBody) {
      builder.breakOp(Doc.FillMode.UNIFIED, " ", closingBraceIndent)
    }

    formatLambdaBody(lambdaExpression, bodyIndent, closingBraceIndent)

    if (hasBody) {
      // If we had to break in the body, ensure there is a break before the closing brace
      builder.breakOp(Doc.FillMode.UNIFIED, "", closingBraceIndent)
    }
    builder.block(closingBraceIndent) {
      builder.fenceComments()
      builder.token("}", blockIndent)
    }
  }

  private fun formatLambdaArguments(
      valueParameterList: KtParameterList,
      valueParametersIndent: Indentation,
      arrowIndent: Indentation,
  ) {
    builder.space()
    builder.block(valueParametersIndent) { formatCommaSeparatedList(valueParameterList.parameters) }
    builder.block(arrowIndent) {
      if (valueParameterList.trailingComma != null) {
        builder.token(",")
        builder.forcedBreak()
      } else if (valueParameterList.parameters.isNotEmpty()) {
        builder.breakOp(Doc.FillMode.INDEPENDENT, " ", ZERO)
      }
      builder.token("->")
    }
  }

  private fun formatLambdaBody(
      lambdaExpression: KtLambdaExpression,
      bodyIndent: Indentation,
      braceIndent: Indentation,
  ) {
    val bodyExpression = lambdaExpression.bodyExpression ?: fail()
    val expressionStatements = bodyExpression.children
    val blockComments =
        bodyExpression.children().filter { it is PsiComment && it.text.startsWith("/*") }.toList()

    val hasBody = expressionStatements.isNotEmpty() || blockComments.isNotEmpty()

    if (!hasBody) return

    builder.breakOp(Doc.FillMode.UNIFIED, "", bodyIndent)
    builder.block(bodyIndent) {
      if (expressionStatements.isNotEmpty()) {
        builder.blankLineWanted(OpsBuilder.BlankLineWanted.NO)

        val shouldForceMultiline =
            options.preserveLambdaBreaks && lambdaExpression.hasSourceNewlineInLambdaBody

        val singleLineStatement =
            expressionStatements.size == 1 &&
                expressionStatements.first() !is KtReturnExpression &&
                !bodyExpression.startsWithComment()

        if (!shouldForceMultiline && singleLineStatement) {
          formatStatement(expressionStatements[0])
        } else {
          formatStatements(expressionStatements)
        }
      } else {
        builder.fenceComments()
        builder.blankLineWanted(OpsBuilder.BlankLineWanted.NO)
        for ((i, comment) in blockComments.withIndex()) {
          if (i > 0) {
            builder.forcedBreak()
          }
          builder.token(comment.text)
        }
      }
      builder.breakOp(Doc.FillMode.UNIFIED, " ", braceIndent)
    }
  }

  override fun formatChainedBlockLikeCall(
      expression: KtQualifiedExpression,
      emitLeadingBreak: Boolean,
  ) {
    val parts = expression.chainParts
    if (emitLeadingBreak) {
      builder.space()
    }
    format(parts[0])

    builder.block(expressionBreakIndent) {
      for (i in 1 until parts.size) {
        val part = parts[i] as KtQualifiedExpression
        builder.forcedBreak()
        builder.token(part.operationSign.value)
        val selectorExpression = part.selectorExpression
        if (selectorExpression is KtCallExpression) {
          format(selectorExpression.calleeExpression)
          formatFunctionCall(
              null,
              selectorExpression.typeArgumentList,
              selectorExpression.valueArgumentList,
              selectorExpression.trailingLambda,
          )
        } else {
          format(selectorExpression)
        }
      }
    }
  }

  override fun formatChainedScopingFunction(
      expression: KtQualifiedExpression,
      emitLeadingBreak: Boolean,
  ) {
    val parts = expression.chainParts
    val root = parts[0]
    val forceBreakBeforeChain = root.isMultilineScopingFunction

    formatLambdaOrScopingFunction(root, emitLeadingBreak = emitLeadingBreak)

    // The break before each selector must stay outside the block below, at the same level as
    // the lambda, so that it is taken exactly when the lambda breaks. Inside the block it
    // would fire only when the selector itself is too long, so a lambda broken by max width
    // would keep its selector on the closing brace's line — and the next format pass, seeing
    // a multiline lambda in the source, would force the selector onto its own line (#640).
    val fillMode = if (forceBreakBeforeChain) Doc.FillMode.FORCED else Doc.FillMode.UNIFIED
    for (i in 1 until parts.size) {
      val part = parts[i] as KtQualifiedExpression
      builder.breakOp(fillMode, "", expressionBreakIndent)
      builder.block(expressionBreakIndent) {
        builder.token(part.operationSign.value)
        val selectorExpression = part.selectorExpression
        if (selectorExpression is KtCallExpression) {
          format(selectorExpression.calleeExpression)
          formatFunctionCall(
              null,
              selectorExpression.typeArgumentList,
              selectorExpression.valueArgumentList,
              selectorExpression.trailingLambda,
          )
        } else {
          format(selectorExpression)
        }
      }
    }
  }

  override fun formatLambdaOrScopingFunction(expr: PsiElement?, emitLeadingBreak: Boolean) {
    val breakToExpr = BreakTag()
    val breakSpace = if (emitLeadingBreak) " " else ""
    builder.breakOp(
        Doc.FillMode.INDEPENDENT,
        breakSpace,
        expressionBreakIndent,
        Optional.of(breakToExpr),
    )

    val scopingLambda = expr.scopingLambda ?: throw AssertionError(expr)
    scopingLambda.receiverExpression?.let {
      format(it)
      builder.token(scopingLambda.operation!!.value)
    }
    scopingLambda.calleeExpression?.let {
      format(it)
      builder.space()
    }
    scopingLambda.labeledExpression?.let {
      format(it.labelQualifier)
    }
    formatLambdaExpression(scopingLambda.lambdaExpression, breakToExpr)
  }

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

  /**
   * Handles a chain of qualified expressions, i.e. `a[5].b!!.c()[4].f()`
   *
   * This is by far the most complicated part of this formatter. We start by breaking the expression
   * into a list of [GroupInfo]'s, each representing a step in the execution of the expression.
   * [GroupInfo]'s are ordered in the opposite order of how the syntax tree is built.
   *
   * Each group is then emitted one by one to the [builder] while opening and closing groups. Each
   * group is opened **before** a corresponding expression is emitted and closed **after**. However,
   * if an expression represents a function call, e.g. `doIt(1, 2) { it }`, the group is closed
   * after `doIt`, and the `(1, 2) { it }` part is emitted after.
   */
  private fun emitQualifiedExpression(expression: KtExpression) {
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

  private data class DeferredCallArguments(
      val call: KtCallExpression,
      val argumentsIndent: Indentation,
      val lambdaIndent: Indentation,
  )

  override fun formatArrayAccessExpression(expression: KtArrayAccessExpression) {
    builder.sync(expression)
    if (expression.arrayExpression is KtQualifiedExpression) {
      emitQualifiedExpression(expression)
    } else {
      format(expression.arrayExpression)
      formatArrayAccessBrackets(expression)
    }
  }

  private fun formatArrayAccessBrackets(expression: KtArrayAccessExpression) {
    builder.block(expressionBreakIndent) {
      formatCommaSeparatedList(
          expression.indexExpressions,
          forceMultiline = expression.trailingComma != null,
          wrapInBlock = true,
          prefix = "[",
          postfix = "]",
          breakBeforePostfix = false,
      )
    }
  }
}
