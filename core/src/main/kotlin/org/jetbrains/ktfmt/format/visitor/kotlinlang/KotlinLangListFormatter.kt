package org.jetbrains.ktfmt.format.visitor.kotlinlang

import com.google.googlejavaformat.Doc
import com.google.googlejavaformat.Indent.Const.ZERO
import org.jetbrains.kotlin.com.intellij.psi.PsiWhiteSpace
import org.jetbrains.kotlin.lexer.KtModifierKeywordToken
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtContextReceiverList
import org.jetbrains.kotlin.psi.KtModifierList
import org.jetbrains.kotlin.psi.psiUtil.children
import org.jetbrains.ktfmt.format.visitor.ListFormatter
import org.jetbrains.ktfmt.format.visitor.sync
import org.jetbrains.ktfmt.format.visitor.token

interface KotlinLangListFormatter : ListFormatter {
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

      if (onlyAnnotationsSoFar && psi is KtAnnotationEntry) {
        builder.forcedBreak()
      } else if (onlyAnnotationsSoFar) {
        builder.breakOp(Doc.FillMode.UNIFIED, " ", ZERO)
      } else {
        builder.space()
      }
    }
  }
}
