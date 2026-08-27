package org.jetbrains.ktfmt.format.visitor

import com.google.googlejavaformat.OpsBuilder
import org.jetbrains.kotlin.com.intellij.psi.PsiComment
import org.jetbrains.kotlin.com.intellij.psi.PsiElement
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtScript
import org.jetbrains.kotlin.psi.KtScriptInitializer

/** Handles formatting of file-level PSI nodes */
interface FileFormatter : KotlinAstFormatter {
  override fun formatKtFile(file: KtFile) {
    formatFile(file)
  }

  override fun formatKtScript(script: KtScript) {
    formatFile(script.blockExpression)
  }

  private fun formatFile(file: KtElement) {
    markForPartialFormat()
    var lastChildHadBlankLineBefore = false
    var lastChildIsContextReceiver = false
    var first = true
    for (child in file.children) {
      if (child.text.isBlank()) continue
      if (child is PsiComment) continue
      builder.forcedBreak()
      val childGetsBlankLineBefore = child !is KtProperty
      if (first) {
        builder.blankLineWanted(OpsBuilder.BlankLineWanted.PRESERVE)
      } else if (lastChildIsContextReceiver) {
        builder.blankLineWanted(OpsBuilder.BlankLineWanted.NO)
      } else if (
          child !is PsiComment && (childGetsBlankLineBefore || lastChildHadBlankLineBefore)
      ) {
        builder.blankLineWanted(OpsBuilder.BlankLineWanted.YES)
      }
      builder.markForPartialFormat()
      format(child)
      builder.guessToken(";")
      builder.markForPartialFormat()
      lastChildHadBlankLineBefore = childGetsBlankLineBefore
      lastChildIsContextReceiver =
          child is KtScriptInitializer &&
              child.firstChild?.firstChild?.firstChild?.text == "context"
      first = false
    }
    markForPartialFormat()
  }

  override fun formatStatement(statement: PsiElement) {
    builder.block { format(statement) }
    builder.guessToken(";")
  }

  override fun formatStatements(statements: Array<PsiElement>) {
    var first = true
    builder.guessToken(";")
    for (statement in statements) {
      builder.forcedBreak()
      if (!first) {
        builder.blankLineWanted(OpsBuilder.BlankLineWanted.PRESERVE)
      }
      first = false
      markForPartialFormat()
      formatStatement(statement)
      markForPartialFormat()
    }
  }
}
