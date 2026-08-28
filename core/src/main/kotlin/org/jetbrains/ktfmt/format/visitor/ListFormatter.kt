package org.jetbrains.ktfmt.format.visitor

import com.google.googlejavaformat.Doc
import com.google.googlejavaformat.Output.BreakTag
import java.util.Optional
import org.jetbrains.kotlin.com.intellij.psi.PsiElement
import org.jetbrains.kotlin.com.intellij.psi.PsiWhiteSpace
import org.jetbrains.kotlin.lexer.KtModifierKeywordToken
import org.jetbrains.kotlin.psi.KtContextReceiverList
import org.jetbrains.kotlin.psi.KtFileAnnotationList
import org.jetbrains.kotlin.psi.KtImportList
import org.jetbrains.kotlin.psi.KtModifierList
import org.jetbrains.kotlin.psi.KtParameterList
import org.jetbrains.kotlin.psi.KtSuperTypeList
import org.jetbrains.kotlin.psi.KtTypeArgumentList
import org.jetbrains.kotlin.psi.KtTypeConstraintList
import org.jetbrains.kotlin.psi.KtTypeParameterList
import org.jetbrains.kotlin.psi.KtValueArgumentList
import org.jetbrains.kotlin.psi.psiUtil.children
import org.jetbrains.ktfmt.format.visitor.Indentation.Companion.ZERO
import org.jetbrains.ktfmt.util.listToVisit

/**
 * Handles formatting of all list-like elements. Even though these elements are not semantically
 * related, they are all formatted in a similar way via [formatCommaSeparatedList].
 */
interface ListFormatter {
  context(_: FormatterStateHolder)
  fun formatTypeArgumentList(list: KtTypeArgumentList)

  context(_: FormatterStateHolder)
  fun formatTypeParameterList(list: KtTypeParameterList)

  context(_: FormatterStateHolder)
  fun formatTypeConstraintList(list: KtTypeConstraintList)

  context(_: FormatterStateHolder)
  fun formatSuperTypeList(list: KtSuperTypeList)

  context(_: FormatterStateHolder)
  fun formatValueArgumentList(list: KtValueArgumentList): BreakTag?

  context(_: FormatterStateHolder)
  fun formatModifierList(list: KtModifierList)

  context(_: FormatterStateHolder)
  fun formatContextReceiverList(contextReceiverList: KtContextReceiverList)

  context(_: FormatterStateHolder)
  fun formatParameterList(list: KtParameterList)

  context(_: FormatterStateHolder)
  fun formatImportList(importList: KtImportList)

  context(_: FormatterStateHolder)
  fun formatFileAnnotationList(fileAnnotationList: KtFileAnnotationList)

  /**
   * format each element in [list], with comma (,) {} tokens in-between.
   *
   * Example:
   * ```
   * a, b, c, 3, 4, 5
   * ```
   *
   * Either the entire list fits in one line, or each element is put on its own line:
   * ```
   * a,
   * b,
   * c,
   * 3,
   * 4,
   * 5
   * ```
   *
   * Optionally include a prefix and postfix:
   * ```
   *   (
   *     a,
   *     b,
   *     c,
   * ) {}
   * ```
   *
   * @param forceMultiline if true, each element is placed on its own line (even if they could've
   *   fit in a single line) {}, and a trailing comma is emitted.
   *
   * Example:
   * ```
   * a,
   * b,
   * ```
   *
   * @param wrapInBlock if true, place all the elements in a block. When there's no
   *   [emitLeadingBreak], this will be negatively indented. Note that the [prefix] and [postfix]
   *   aren't included in the block.
   * @param emitLeadingBreak if true, break before the first element.
   * @param prefix if provided, emit this before the first element.
   * @param postfix if provided, emit this after the last element (or trailing comma) {}.
   * @param breakAfterPrefix if true, emit a break after [prefix], but before the start of the
   *   block.
   * @param breakBeforePostfix if true, place a break after the last element. Redundant when
   *   [forceMultiline] is true.
   * @return a [BreakTag] which can tell you if a break was taken, but only when the list doesn't
   *   terminate in a negative closing indent.
   *
   * Example 1, this returns a BreakTag which tells you a break wasn't taken:
   * ```
   * (arg1, arg2) {}
   * ```
   *
   * Example 2, this returns a BreakTag which tells you a break WAS taken:
   * ```
   * (
   *     arg1,
   *     arg2) {}
   * ```
   *
   * Example 3, this returns null:
   * ```
   * (
   *     arg1,
   *     arg2,
   * ) {}
   * ```
   *
   * Example 4, this also returns null (similar to example 2, but Google style) {}:
   * ```
   * (
   *     arg1,
   *     arg2
   * ) {}
   * ```
   */
  context(_: FormatterStateHolder)
  fun formatCommaSeparatedList(
      list: Iterable<PsiElement>,
      forceMultiline: Boolean,
      wrapInBlock: Boolean,
      emitLeadingBreak: Boolean,
      prefix: String?,
      postfix: String?,
      breakAfterPrefix: Boolean,
      breakBeforePostfix: Boolean,
  ): BreakTag?
}

internal open class ListFormatterImpl : ListFormatter {
  context(_: FormatterStateHolder)
  override fun formatTypeArgumentList(list: KtTypeArgumentList) {
    builder.sync(list)
    formatCommaSeparatedList(
        list.arguments,
        forceMultiline = list.trailingComma != null,
        wrapInBlock = !options.manageTrailingCommas,
        prefix = "<",
        postfix = ">",
    )
  }

  context(_: FormatterStateHolder)
  override fun formatTypeParameterList(list: KtTypeParameterList) {
    builder.sync(list)
    builder.block(expressionBreakIndent) {
      formatCommaSeparatedList(
          list.parameters,
          forceMultiline = list.trailingComma != null,
          wrapInBlock = !options.manageTrailingCommas,
          prefix = "<",
          postfix = ">",
      )
    }
  }

  context(_: FormatterStateHolder)
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

  context(_: FormatterStateHolder)
  override fun formatSuperTypeList(list: KtSuperTypeList) {
    builder.sync(list)
    builder.block(expressionBreakIndent) { formatCommaSeparatedList(list.entries) }
  }

  /**
   * @return a [BreakTag] which can tell you if a break was taken, but only when the list doesn't
   *   terminate in a negative closing indent. See [formatCommaSeparatedList] for examples.
   */
  context(_: FormatterStateHolder)
  override fun formatValueArgumentList(list: KtValueArgumentList): BreakTag? {
    builder.sync(list)

    val arguments = list.arguments
    val isSingleUnnamedLambda = arguments.singleOrNull()?.isUnnamedLambda ?: false
    val hasTrailingComma = list.trailingComma != null
    val hasEmptyParens = list.hasEmptyParenthesis

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
      val contentForcesMultiline = !hasTrailingComma && arguments.any { it.isBlockLikeArgument }
      wrapInBlock = !options.manageTrailingCommas
      breakBeforePostfix =
          (options.manageTrailingCommas || contentForcesMultiline) && !hasEmptyParens
      leadingBreak = !hasEmptyParens
      breakAfterPrefix = !hasEmptyParens
    }

    return formatCommaSeparatedList(
        arguments,
        forceMultiline = hasTrailingComma,
        wrapInBlock = wrapInBlock,
        emitLeadingBreak = leadingBreak,
        prefix = "(",
        postfix = ")",
        breakAfterPrefix = breakAfterPrefix,
        breakBeforePostfix = breakBeforePostfix,
    )
  }

  context(_: FormatterStateHolder)
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
  context(_: FormatterStateHolder)
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

  context(_: FormatterStateHolder)
  override fun formatParameterList(list: KtParameterList) {
    formatCommaSeparatedList(
        list.parameters,
        forceMultiline = list.trailingComma != null,
        prefix = "(",
        postfix = ")",
    )
  }

  context(_: FormatterStateHolder)
  override fun formatImportList(importList: KtImportList) {
    builder.sync(importList)
    importList.imports.forEach { format(it) }
  }

  context(_: FormatterStateHolder)
  override fun formatFileAnnotationList(fileAnnotationList: KtFileAnnotationList) {
    for (child in fileAnnotationList.node.children()) {
      if (child is PsiElement) {
        continue
      }
      format(child.psi)
      builder.forcedBreak()
    }
  }

  context(_: FormatterStateHolder)
  override fun formatCommaSeparatedList(
      list: Iterable<PsiElement>,
      forceMultiline: Boolean,
      wrapInBlock: Boolean,
      emitLeadingBreak: Boolean,
      prefix: String?,
      postfix: String?,
      breakAfterPrefix: Boolean,
      breakBeforePostfix: Boolean,
  ): BreakTag? {
    val breakAfterLastElement = forceMultiline || (postfix != null && breakBeforePostfix)
    val nameTag = if (breakAfterLastElement) null else BreakTag()
    val breakType = if (forceMultiline) Doc.FillMode.FORCED else Doc.FillMode.UNIFIED

    if (prefix != null) {
      builder.token(prefix)
      if (breakAfterPrefix) {
        builder.breakOp(Doc.FillMode.UNIFIED, "", ZERO, Optional.ofNullable(nameTag))
      }
    }

    fun emitComma() {
      builder.token(",")
      builder.breakOp(breakType, " ", ZERO)
    }

    val indent = if (emitLeadingBreak) ZERO else -expressionBreakIndent
    builder.block(indent, isEnabled = wrapInBlock) {
      if (emitLeadingBreak) {
        builder.breakOp(breakType, "", ZERO)
      }

      for ((index, value) in list.withIndex()) {
        if (index > 0) emitComma()
        format(value)
      }

      if (forceMultiline) {
        emitComma()
      }
    }

    if (breakAfterLastElement) {
      // a negative closing indent places the postfix to the left of the elements
      // see examples 2 and 4 in the docstring
      builder.breakOp(breakType, "", -expressionBreakIndent)
    }

    if (postfix != null) {
      if (breakAfterLastElement) {
        builder.block(-expressionBreakIndent) {
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
