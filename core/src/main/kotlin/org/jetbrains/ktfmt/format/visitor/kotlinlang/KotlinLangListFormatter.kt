package org.jetbrains.ktfmt.format.visitor.kotlinlang

import com.google.googlejavaformat.Doc
import com.google.googlejavaformat.Indent.Const.ZERO
import org.jetbrains.kotlin.com.intellij.psi.PsiWhiteSpace
import org.jetbrains.kotlin.lexer.KtModifierKeywordToken
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtContextReceiverList
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtModifierList
import org.jetbrains.kotlin.psi.KtPrimaryConstructor
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtPropertyAccessor
import org.jetbrains.kotlin.psi.psiUtil.children
import org.jetbrains.ktfmt.format.visitor.FormatterStateHolder
import org.jetbrains.ktfmt.format.visitor.ListFormatterImpl
import org.jetbrains.ktfmt.format.visitor.builder
import org.jetbrains.ktfmt.format.visitor.format
import org.jetbrains.ktfmt.format.visitor.sync
import org.jetbrains.ktfmt.format.visitor.token

/**
 * Custom formatter for KotlinLang style. Handles annotation formatting for declarations and types
 * by overriding [formatModifierList]. For annotations on expressions see
 * [KotlinLangAnnotationFormatterImpl].
 *
 * General rules for annotations in modifier lists:
 * - One annotation per line (forced breaks) for class-like declarations
 * - One annotation per line for function-like declarations, including:
 *     - Top-level functions
 *     - Class members, including constructors (except primary constructors)
 *     - Property accessors
 * - One annotation per line for properties
 * - No forced breaks for everything else
 */
internal class KotlinLangListFormatterImpl : ListFormatterImpl() {
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
}
