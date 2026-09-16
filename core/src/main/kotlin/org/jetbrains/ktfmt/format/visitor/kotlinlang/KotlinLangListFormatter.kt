package org.jetbrains.ktfmt.format.visitor.kotlinlang

import com.google.googlejavaformat.Doc
import com.google.googlejavaformat.Indent.Const.ZERO
import com.google.googlejavaformat.Output.BreakTag
import java.util.Optional
import org.jetbrains.kotlin.com.intellij.psi.PsiElement
import org.jetbrains.kotlin.com.intellij.psi.PsiWhiteSpace
import org.jetbrains.kotlin.lexer.KtModifierKeywordToken
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtContextReceiverList
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtModifierList
import org.jetbrains.kotlin.psi.KtPrimaryConstructor
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtPropertyAccessor
import org.jetbrains.kotlin.psi.KtSuperTypeList
import org.jetbrains.kotlin.psi.KtValueArgumentList
import org.jetbrains.kotlin.psi.psiUtil.children
import org.jetbrains.ktfmt.format.visitor.FormatterStateHolder
import org.jetbrains.ktfmt.format.visitor.Indentation
import org.jetbrains.ktfmt.format.visitor.ListFormatterImpl
import org.jetbrains.ktfmt.format.visitor.block
import org.jetbrains.ktfmt.format.visitor.breakOp
import org.jetbrains.ktfmt.format.visitor.builder
import org.jetbrains.ktfmt.format.visitor.expressionBreakIndent
import org.jetbrains.ktfmt.format.visitor.fenceComments
import org.jetbrains.ktfmt.format.visitor.format
import org.jetbrains.ktfmt.format.visitor.formatCommaSeparatedList
import org.jetbrains.ktfmt.format.visitor.hasEmptyParenthesis
import org.jetbrains.ktfmt.format.visitor.isBlockLikeArgument
import org.jetbrains.ktfmt.format.visitor.isUnnamedLambda
import org.jetbrains.ktfmt.format.visitor.options
import org.jetbrains.ktfmt.format.visitor.sync
import org.jetbrains.ktfmt.format.visitor.token

/**
 * Custom formatter for KotlinLang style.
 *
 * Handles annotation formatting for declarations and types by overriding [formatModifierList]. For
 * annotations on expressions see [KotlinLangAnnotationFormatterImpl].
 *
 * General rules for annotations in modifier lists:
 * - One annotation per line (forced breaks) for class-like declarations
 * - One annotation per line for function-like declarations, including:
 *     - Top-level functions
 *     - Class members, including constructors (except primary constructors)
 *     - Property accessors
 * - One annotation per line for properties
 * - No forced breaks for everything else
 *
 * [formatValueArgumentList] implements the behaviour introduced in #634 that is reverted in the
 * default style: force the closing bracket in exploded function calls to the new line.
 *
 * ```
 * foo(
 *    a,
 *    b // previously this was `b)`
 * )
 * ```
 *
 * See [KotlinLangCallFormatterImpl] for more details.
 *
 * [formatCommaSeparatedList] wrapes every list element into its own block.
 *
 * [formatSuperTypeList] formats the supertype list without a leading break. The leading break is
 * managed by [KotlinLangDeclarationFormatterImpl.formatClassOrObject].
 */
internal class KotlinLangListFormatterImpl : ListFormatterImpl() {
  context(_: FormatterStateHolder)
  override fun formatSuperTypeList(list: KtSuperTypeList) {
    builder.block(expressionBreakIndent) {
      formatCommaSeparatedList(list.entries, emitLeadingBreak = false)
    }
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

      val shouldForceBreak =
          list.parent is KtClassOrObject ||
              (list.parent is KtFunction && list.parent !is KtPrimaryConstructor) ||
              (list.parent is KtPropertyAccessor) ||
              (list.parent is KtProperty)
      if (onlyAnnotationsSoFar && shouldForceBreak) {
        builder.forcedBreak()
      } else if (onlyAnnotationsSoFar) {
        builder.breakOp(Doc.FillMode.UNIFIED, " ", ZERO)
      } else {
        builder.space()
      }
    }
  }

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
        builder.breakOp(Doc.FillMode.UNIFIED, "", Indentation.ZERO, Optional.ofNullable(nameTag))
      }
    }

    fun emitComma() {
      builder.token(",")
      builder.breakOp(breakType, " ", Indentation.ZERO)
    }

    val indent = if (emitLeadingBreak) Indentation.ZERO else -expressionBreakIndent
    builder.block(indent, isEnabled = wrapInBlock) {
      if (emitLeadingBreak) {
        builder.breakOp(breakType, "", Indentation.ZERO)
      }

      for ((index, value) in list.withIndex()) {
        if (index > 0) emitComma()
        builder.block {
          format(value)
        }
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
