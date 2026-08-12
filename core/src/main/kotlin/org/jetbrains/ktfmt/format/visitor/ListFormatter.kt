package org.jetbrains.ktfmt.format.visitor

import com.google.googlejavaformat.Doc
import com.google.googlejavaformat.Indent.Const.ZERO
import com.google.googlejavaformat.Output.BreakTag
import java.util.Optional
import org.jetbrains.kotlin.com.intellij.psi.PsiElement
import org.jetbrains.kotlin.com.intellij.psi.PsiWhiteSpace
import org.jetbrains.kotlin.lexer.KtModifierKeywordToken
import org.jetbrains.kotlin.psi.KtContextReceiverList
import org.jetbrains.kotlin.psi.KtFileAnnotationList
import org.jetbrains.kotlin.psi.KtImportList
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtModifierList
import org.jetbrains.kotlin.psi.KtParameterList
import org.jetbrains.kotlin.psi.KtSuperTypeList
import org.jetbrains.kotlin.psi.KtTypeArgumentList
import org.jetbrains.kotlin.psi.KtTypeConstraintList
import org.jetbrains.kotlin.psi.KtTypeParameterList
import org.jetbrains.kotlin.psi.KtValueArgumentList
import org.jetbrains.kotlin.psi.psiUtil.children
import org.jetbrains.ktfmt.util.listToVisit

interface ListFormatter : KotlinAstFormatter {
  override fun formatTypeArgumentList(list: KtTypeArgumentList) {
    builder.sync(list)
    formatCommaSeparatedList(
        list.arguments,
        hasTrailingComma = list.trailingComma != null,
        wrapInBlock = !options.manageTrailingCommas,
        prefix = "<",
        postfix = ">",
    )
  }

  override fun formatTypeParameterList(list: KtTypeParameterList) {
    builder.sync(list)
    builder.block(expressionBreakIndent) {
      formatCommaSeparatedList(
          list.parameters,
          hasTrailingComma = list.trailingComma != null,
          prefix = "<",
          postfix = ">",
          wrapInBlock = !options.manageTrailingCommas,
      )
    }
  }

  override fun formatTypeConstraintList(list: KtTypeConstraintList) {
    builder.block(expressionBreakIndent) {
      builder.breakOp(Doc.FillMode.INDEPENDENT, " ", ZERO)
      builder.token("where")
      builder.block(expressionBreakIndent) {
        builder.breakOp(Doc.FillMode.UNIFIED, " ", ZERO)
        builder.sync(list)
        formatCommaSeparatedList(list.constraints, wrapInBlock = false)
      }
    }
  }

  override fun formatSuperTypeList(list: KtSuperTypeList) {
    builder.sync(list)
    builder.block(expressionBreakIndent) { formatCommaSeparatedList(list.entries) }
  }

  /**
   * @return a [BreakTag] which can tell you if a break was taken, but only when the list doesn't
   *   terminate in a negative closing indent. See [formatCommaSeparatedList] for examples.
   */
  override fun formatValueArgumentList(list: KtValueArgumentList): BreakTag? {
    builder.sync(list)

    val arguments = list.arguments
    val isSingleUnnamedLambda =
        arguments.size == 1 &&
            arguments.first().getArgumentExpression() is KtLambdaExpression &&
            arguments.first().getArgumentName() == null
    val hasTrailingComma = list.trailingComma != null
    val hasEmptyParens = list.hasEmptyParens

    val wrapInBlock: Boolean
    val breakBeforePostfix: Boolean
    val leadingBreak: Boolean
    val breakAfterPrefix: Boolean
    if (isSingleUnnamedLambda) {
      wrapInBlock = true
      breakBeforePostfix = false
      leadingBreak = !hasEmptyParens && hasTrailingComma
      breakAfterPrefix = false
    } else {
      // A call without a trailing comma that is nonetheless forced onto multiple lines (because one
      // of its arguments is itself a block-like multiline call) is rendered "exploded", with its
      // closing parenthesis on its own line, just like a call with a trailing comma.
      val contentForcesMultiline =
          !hasTrailingComma &&
              arguments.any { argument ->
                val argumentExpression = argument.getArgumentExpression()
                argumentExpression != null &&
                    (argumentExpression.isBlockLikeCall ||
                        argumentExpression.isChainedBlockLikeCall)
              }
      wrapInBlock = !options.manageTrailingCommas
      breakBeforePostfix =
          (options.manageTrailingCommas || contentForcesMultiline) && !hasEmptyParens
      leadingBreak = !hasEmptyParens
      breakAfterPrefix = !hasEmptyParens
    }

    return formatCommaSeparatedList(
        arguments,
        hasTrailingComma = hasTrailingComma,
        wrapInBlock = wrapInBlock,
        breakBeforePostfix = breakBeforePostfix,
        leadingBreak = leadingBreak,
        prefix = "(",
        postfix = ")",
        breakAfterPrefix = breakAfterPrefix,
    )
  }

  override fun formatModifierList(list: KtModifierList) {
    builder.sync(list)
    var onlyAnnotationsSoFar = true

    for (child in list.node.children()) {
      val psi = child.psi
      if (psi is PsiWhiteSpace) {
        continue
      }

      // In Kotlin 2.3+, context receiver lists are children of the modifier list.
      // Handle them directly to avoid issues with the visitor dispatch.
      if (psi is KtContextReceiverList) {
        formatContextReceiverList(psi)
        builder.forcedBreak()
        continue
      }

      if (child.elementType is KtModifierKeywordToken) {
        onlyAnnotationsSoFar = false
        builder.token(child.text)
      } else {
        format(psi)
      }

      if (onlyAnnotationsSoFar) {
        builder.breakOp(Doc.FillMode.UNIFIED, " ", ZERO)
      } else {
        builder.space()
      }
    }
  }

  /**
   * Note this also supports the legacy receiver format of `context(Logger, Raise<Error>)` for
   * backward compatibility and for function types
   */
  override fun formatContextReceiverList(contextReceiverList: KtContextReceiverList) {
    builder.sync(contextReceiverList)
    builder.token("context")
    formatCommaSeparatedList(
        contextReceiverList.listToVisit(),
        prefix = "(",
        postfix = ")",
        breakAfterPrefix = false,
        breakBeforePostfix = false,
    )
  }

  override fun formatParameterList(list: KtParameterList) {
    formatCommaSeparatedList(list.parameters, list.trailingComma != null, wrapInBlock = false)
  }

  override fun formatImportList(importList: KtImportList) {
    builder.sync(importList)
    importList.imports.forEach { format(it) }
  }

  override fun formatFileAnnotationList(fileAnnotationList: KtFileAnnotationList) {
    for (child in fileAnnotationList.node.children()) {
      if (child is PsiElement) {
        continue
      }
      format(child.psi)
      builder.forcedBreak()
    }
  }

  override fun formatCommaSeparatedList(
      list: Iterable<PsiElement>,
      hasTrailingComma: Boolean,
      wrapInBlock: Boolean,
      leadingBreak: Boolean,
      prefix: String?,
      postfix: String?,
      breakAfterPrefix: Boolean,
      breakBeforePostfix: Boolean,
  ): BreakTag? {
    val breakAfterLastElement = hasTrailingComma || (postfix != null && breakBeforePostfix)
    val nameTag = if (breakAfterLastElement) null else BreakTag()

    if (prefix != null) {
      builder.token(prefix)
      if (breakAfterPrefix) {
        builder.breakOp(Doc.FillMode.UNIFIED, "", ZERO, Optional.ofNullable(nameTag))
      }
    }

    val breakType = if (hasTrailingComma) Doc.FillMode.FORCED else Doc.FillMode.UNIFIED
    fun emitComma() {
      builder.token(",")
      builder.breakOp(breakType, " ", ZERO)
    }

    val indent = if (leadingBreak) ZERO else expressionBreakNegativeIndent
    builder.block(indent, isEnabled = wrapInBlock) {
      if (leadingBreak) {
        builder.breakOp(breakType, "", ZERO)
      }

      for ((index, value) in list.withIndex()) {
        if (index > 0) emitComma()
        format(value)
      }

      if (hasTrailingComma) {
        emitComma()
      }
    }

    if (breakAfterLastElement) {
      // a negative closing indent places the postfix to the left of the elements
      // see examples 2 and 4 in the docstring
      builder.breakOp(breakType, "", expressionBreakNegativeIndent)
    }

    if (postfix != null) {
      if (breakAfterLastElement) {
        builder.block(expressionBreakNegativeIndent) {
          builder.fenceComments()
          builder.token(postfix, expressionBreakIndent)
        }
      } else {
        builder.token(postfix)
      }
    }

    return nameTag
  }
}
