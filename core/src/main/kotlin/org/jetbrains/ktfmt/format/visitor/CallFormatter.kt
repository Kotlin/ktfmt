package org.jetbrains.ktfmt.format.visitor

import com.google.googlejavaformat.Doc
import com.google.googlejavaformat.OpsBuilder
import com.google.googlejavaformat.Output.BreakTag
import java.util.Optional
import org.jetbrains.kotlin.com.intellij.psi.PsiComment
import org.jetbrains.kotlin.com.intellij.psi.PsiElement
import org.jetbrains.kotlin.psi.KtArrayAccessExpression
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtLambdaArgument
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtPostfixExpression
import org.jetbrains.kotlin.psi.KtQualifiedExpression
import org.jetbrains.kotlin.psi.KtReturnExpression
import org.jetbrains.kotlin.psi.KtSimpleNameExpression
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.psi.KtSuperExpression
import org.jetbrains.kotlin.psi.KtThisExpression
import org.jetbrains.kotlin.psi.KtTypeArgumentList
import org.jetbrains.kotlin.psi.KtValueArgument
import org.jetbrains.kotlin.psi.KtValueArgumentList
import org.jetbrains.kotlin.psi.KtWhenExpression
import org.jetbrains.kotlin.psi.psiUtil.children
import org.jetbrains.kotlin.psi.psiUtil.startsWithComment
import org.jetbrains.ktfmt.format.ParseError
import org.jetbrains.ktfmt.format.visitor.Indentation.Companion.ZERO

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

  override fun formatFunctionCall(
      callee: KtExpression?,
      typeArgumentList: KtTypeArgumentList?,
      argumentList: KtValueArgumentList?,
      lambdaArguments: List<KtLambdaArgument>,
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
          builder.block(ZERO) { format(typeArgumentList) }
          if (argumentList != null) {
            brokeBeforeBrace = formatValueArgumentList(argumentList)
          }
        }
      }
      when (lambdaArguments.size) {
        0 -> {}
        1 -> {
          builder.space()
          formatArgument(
              lambdaArguments.single(),
              wrapInBlock = false,
              brokeBeforeBrace = brokeBeforeBrace,
          )
        }
        else -> throw ParseError("Maximum one trailing lambda is allowed", lambdaArguments[1])
      }
    }
  }

  override fun formatLambdaExpression(
      lambdaExpression: KtLambdaExpression,
      brokeBeforeBrace: BreakTag?,
  ) {
    builder.sync(lambdaExpression)

    val valueParams = lambdaExpression.valueParameters
    val hasParams = valueParams.isNotEmpty()
    val bodyExpression = lambdaExpression.bodyExpression ?: fail()
    val expressionStatements = bodyExpression.children
    val hasStatements = expressionStatements.isNotEmpty()
    val hasComments = bodyExpression.children().any { it is PsiComment }
    val hasArrow = lambdaExpression.functionLiteral.arrow != null

    fun ifBrokeBeforeBrace(onTrue: Indentation, onFalse: Indentation): Indentation {
      if (brokeBeforeBrace == null) return onFalse
      return Indentation.Conditional(brokeBeforeBrace, onTrue, onFalse)
    }

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
    val bracePlusBlockIndent = ifBrokeBeforeBrace(blockIndent + expressionBreakIndent, blockIndent)
    val bracePlusExpressionIndent =
        ifBrokeBeforeBrace(expressionBreakIndent * 2, expressionBreakIndent)
    val bracePlusZeroIndent = ifBrokeBeforeBrace(expressionBreakIndent, ZERO)

    builder.token("{")

    if (hasParams || hasArrow) {
      builder.space()
      builder.block(bracePlusExpressionIndent) { formatCommaSeparatedList(valueParams) }
      builder.block(bracePlusBlockIndent) {
        if (lambdaExpression.functionLiteral.valueParameterList?.trailingComma != null) {
          builder.token(",")
          builder.forcedBreak()
        } else if (hasParams) {
          builder.breakOp(Doc.FillMode.INDEPENDENT, " ", ZERO)
        }
        builder.token("->")
      }
    }

    if (hasParams || hasArrow || hasStatements || hasComments) {
      builder.breakOp(Doc.FillMode.UNIFIED, " ", bracePlusZeroIndent)
    }

    if (hasStatements) {
      builder.breakOp(Doc.FillMode.UNIFIED, "", bracePlusBlockIndent)
      builder.block(bracePlusBlockIndent) {
        builder.blankLineWanted(OpsBuilder.BlankLineWanted.NO)

        val shouldForceMultiline =
            options.preserveLambdaBreaks && lambdaExpression.hasSourceNewlineInLambdaBody

        if (
            !shouldForceMultiline &&
                expressionStatements.size == 1 &&
                expressionStatements.first() !is KtReturnExpression &&
                !bodyExpression.startsWithComment()
        ) {
          formatStatement(expressionStatements[0])
        } else {
          formatStatements(expressionStatements)
        }
        builder.breakOp(Doc.FillMode.UNIFIED, " ", bracePlusZeroIndent)
      }
    } else if (hasComments) {
      val blockComments =
          bodyExpression.children().filter { it is PsiComment && it.text.startsWith("/*") }.toList()
      builder.breakOp(Doc.FillMode.UNIFIED, "", bracePlusBlockIndent)
      builder.block(bracePlusBlockIndent) {
        builder.fenceComments()
        builder.blankLineWanted(OpsBuilder.BlankLineWanted.NO)
        if (blockComments.size == 1) {
          builder.token(blockComments[0].text)
        } else {
          for ((i, comment) in blockComments.withIndex()) {
            if (i > 0) {
              builder.forcedBreak()
            }
            builder.token(comment.text)
          }
        }
        builder.breakOp(Doc.FillMode.UNIFIED, " ", bracePlusZeroIndent)
      }
    }

    if (hasParams || hasArrow || hasStatements || hasComments) {
      // If we had to break in the body, ensure there is a break before the closing brace
      builder.breakOp(Doc.FillMode.UNIFIED, "", bracePlusZeroIndent)
    }
    builder.block(bracePlusZeroIndent) {
      builder.fenceComments()
      builder.token("}", blockIndent)
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
              selectorExpression.lambdaArguments,
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
              selectorExpression.lambdaArguments,
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
        builder.block(ZERO) {
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

  private fun emitQualifiedExpression(expression: KtExpression) {
    val parts = expression.chainParts
    // whether we want to make a lambda look like a block, this make Kotlin DSLs look as expected
    val useBlockLikeLambdaStyle = parts.last().isLambda && parts.count { it.isLambda } == 1
    val groupingInfos = computeGroupingInfo(parts, useBlockLikeLambdaStyle)
    builder.block(expressionBreakIndent) {
      val nameTag = BreakTag() // allows adjusting arguments indentation if a break will be made
      for ((index, ktExpression) in parts.withIndex()) {
        if (ktExpression is KtQualifiedExpression) {
          builder.breakOp(Doc.FillMode.UNIFIED, "", ZERO, Optional.of(nameTag))
        }
        repeat(groupingInfos[index].groupOpenCount) { builder.open(ZERO) }
        when (ktExpression) {
          is KtQualifiedExpression -> {
            builder.token(ktExpression.operationSign.value)
            val selectorExpression = ktExpression.selectorExpression
            if (selectorExpression !is KtCallExpression) {
              // selector is a simple field access
              format(selectorExpression)
              if (groupingInfos[index].shouldCloseGroup) {
                builder.close()
              }
            } else {
              // selector is a function call, we may close a group after its name
              // emit `doIt` from `doIt(1, 2) { it }`
              format(selectorExpression.calleeExpression)
              // close groups according to instructions
              if (groupingInfos[index].shouldCloseGroup) {
                builder.close()
              }
              // close group due to last lambda to allow block-like style in `as.forEach { ... }`
              val isTrailingLambda = useBlockLikeLambdaStyle && index == parts.size - 1
              if (isTrailingLambda) {
                builder.close()
              }
              // A block-like (exploded) selector call is laid out like the last part: its
              // arguments are indented once relative to the call itself, and its closing paren
              // returns to the call's indent, even when chained selectors follow it. This only
              // applies when trailing commas are preserved (the block-like style); when ktfmt
              // manages trailing commas, exploded chained calls keep the regular extra indent.
              val isLastPartOrBlockLikeCall =
                  index == parts.size - 1 ||
                      !options.manageTrailingCommas && selectorExpression.isBlockLikeCall
              val argsIndentElse = if (isLastPartOrBlockLikeCall) ZERO else expressionBreakIndent
              val lambdaIndentElse = if (isTrailingLambda) -expressionBreakIndent else ZERO

              // emit `(1, 2) { it }` from `doIt(1, 2) { it }`
              formatFunctionCall(
                  null,
                  selectorExpression.typeArgumentList,
                  selectorExpression.valueArgumentList,
                  selectorExpression.lambdaArguments,
                  argumentsIndent =
                      Indentation.Conditional(nameTag, expressionBreakIndent, argsIndentElse),
                  lambdaIndent = Indentation.Conditional(nameTag, ZERO, lambdaIndentElse),
              )
            }
          }
          is KtArrayAccessExpression -> {
            formatArrayAccessBrackets(ktExpression)
            builder.close()
          }
          is KtPostfixExpression -> {
            builder.token(ktExpression.operationReference.text)
            builder.close()
          }
          else -> {
            check(index == 0)
            format(ktExpression)
          }
        }
      }
    }
  }

  /** Extra data to help [emitQualifiedExpression] know when to open and close a group */
  private class GroupingInfo {
    var groupOpenCount = 0
    var shouldCloseGroup = false
  }

  /**
   * Generates the [GroupingInfo] array to go with an array of [KtQualifiedExpression] parts
   *
   * For example, the expression `a.b[2].c.d()` is made of four expressions:
   * 1. [KtQualifiedExpression] `a.b[2].c . d()` (this will be `parts[4]`)
   * 1. [KtQualifiedExpression] `a.b[2] . c` (this will be `parts[3]`)
   * 2. [KtArrayAccessExpression] `a.b [2]` (this will be `parts[2]`)
   * 3. [KtQualifiedExpression] `a . b` (this will be `parts[1]`)
   * 4. [KtSimpleNameExpression] `a` (this will be `parts[0]`)
   *
   * Once in parts, these are in the reverse order. To render the array correct we need to make sure
   * `b` and [2] are in a group so we avoid splitting them. To do so we need to open a group for `b`
   * (that will be done in part 2), and always close a group for an array.
   *
   * Here is the same expression, with justified braces marking the groupings it will get:
   * ```
   *  a . b [2] . c . d ()
   * {a . b} --> Grouping `a.b` because it can be a package name or simple field access so we add 1
   *             to the number of groups to open at groupingInfos[0], and mark to close a group at
   *             groupingInfos[1]
   * {a . b [2]} --> Grouping `a.b` with `[2]`, since otherwise we may break inside the brackets
   *                 instead of preferring breaks before dots. So we open a group at [0], but since
   *                 we always close a group after brackets, we don't store that information.
   *             {c . d} --> another group to attach the first function name to the fields before it
   *                         this time we don't start the group in the beginning, and use
   *                         lastIndexToOpen to track the spot after the last time we stopped
   *                         grouping.
   * ```
   *
   * The final expression with groupings:
   * ```
   * {{a.b}[2]}.{c.d}()
   * ```
   */
  private fun computeGroupingInfo(
      parts: List<KtExpression>,
      useBlockLikeLambdaStyle: Boolean,
  ): List<GroupingInfo> {
    val groupingInfos = List(parts.size) { GroupingInfo() }
    var lastIndexToOpen = 0
    for ((index, part) in parts.withIndex()) {
      when (part) {
        is KtQualifiedExpression -> {
          val receiverExpression = part.receiverExpression
          val previous =
              (receiverExpression as? KtQualifiedExpression)?.selectorExpression
                  ?: receiverExpression
          val current = checkNotNull(part.selectorExpression)
          if (
              lastIndexToOpen == 0 &&
                  shouldGroupPartWithPrevious(parts, part, index, previous, current)
          ) {
            // this and the previous items should be grouped for better style
            // we add another group to open in index 0
            groupingInfos[0].groupOpenCount++
            // we don't always close a group when emitting this node, so we need this flag to
            // mark if we need to close a group
            groupingInfos[index].shouldCloseGroup = true
          } else {
            // use this index in to open future groups for arrays and postfixes
            // we will also stop grouping field access to the beginning of the expression
            lastIndexToOpen = index
          }
        }
        is KtArrayAccessExpression,
        is KtPostfixExpression -> {
          // we group these with the last item with a name, and we always close them
          groupingInfos[lastIndexToOpen].groupOpenCount++
        }
      }
    }
    if (useBlockLikeLambdaStyle) {
      // a trailing lambda adds a group that we stop before emitting the lambda
      groupingInfos[0].groupOpenCount++
    }
    return groupingInfos
  }

  /** Decide whether a [KtQualifiedExpression] part should be grouped with the previous part */
  private fun shouldGroupPartWithPrevious(
      parts: List<KtExpression>,
      part: KtExpression,
      index: Int,
      previous: KtExpression,
      current: KtExpression,
  ): Boolean {
    // this is the second, and the first is short, avoid `.` "hanging in air"
    if (index == 1 && previous.text.length < options.continuationIndent) {
      return true
    }
    // the previous part is `this` or `super`
    if (previous is KtSuperExpression || previous is KtThisExpression) {
      return true
    }
    // this and the previous part are a package name, type name, or property
    if (
        previous is KtSimpleNameExpression &&
            current is KtSimpleNameExpression &&
            part is KtDotQualifiedExpression
    ) {
      return true
    }
    // this is `Foo` in `com.facebook.Foo`, so everything before it is a package name
    if (
        current.text.first().isUpperCase() &&
            current is KtSimpleNameExpression &&
            part is KtDotQualifiedExpression
    ) {
      return true
    }
    // this is the `foo()` in `com.facebook.Foo.foo()` or in `Foo.foo()`
    if (
        current is KtCallExpression &&
            (previous !is KtCallExpression) &&
            previous.text?.firstOrNull()?.isUpperCase() == true
    ) {
      return true
    }
    // this is an invocation and the last item, and the previous it not, i.e. `a.b.c()`
    // keeping it grouped and splitting the arguments makes `a.b(...)` feel like `aab()`
    return current is KtCallExpression &&
        previous !is KtCallExpression &&
        index == parts.indices.last
  }

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
