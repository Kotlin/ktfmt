package org.jetbrains.ktfmt.format.visitor

import com.google.googlejavaformat.OpsBuilder
import org.jetbrains.kotlin.com.intellij.psi.PsiComment
import org.jetbrains.kotlin.com.intellij.psi.PsiElement
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtImportDirective
import org.jetbrains.kotlin.psi.KtPackageDirective
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtScript
import org.jetbrains.kotlin.psi.KtScriptInitializer

/** Handles formatting of file-level PSI nodes */
interface FileFormatter : KotlinAstFormatter {
  override fun formatKtFile(file: KtFile) {
    markForPartialFormat()
    val importListEmpty = file.importList?.text?.isBlank() ?: true

    var isFirst = true
    for (child in file.children) {
      if (child.text.isBlank()) {
        continue
      }

      builder.blankLineWanted(
          when {
            isFirst -> OpsBuilder.BlankLineWanted.NO
            child is PsiComment -> continue
            child is KtScript && importListEmpty -> OpsBuilder.BlankLineWanted.PRESERVE
            else -> OpsBuilder.BlankLineWanted.YES
          },
      )

      builder.markForPartialFormat()
      format(child)
      builder.markForPartialFormat()
      isFirst = false
    }
    markForPartialFormat()
  }

  override fun formatKtScript(script: KtScript) {
    markForPartialFormat()
    var lastChildHadBlankLineBefore = false
    var lastChildIsContextReceiver = false
    var first = true
    for (child in script.blockExpression.children) {
      if (child.text.isBlank()) {
        continue
      }
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

  override fun formatPackageDirective(directive: KtPackageDirective) {
    builder.sync(directive)
    if (directive.packageKeyword == null) {
      return
    }
    builder.token("package")
    builder.space()
    var first = true
    for (packageName in directive.packageNames) {
      if (first) {
        first = false
      } else {
        builder.token(".")
      }
      builder.token(packageName.getIdentifier()?.text ?: packageName.getReferencedName())
    }

    builder.guessToken(";")
    builder.forcedBreak()
  }

  override fun formatImportDirective(directive: KtImportDirective) {
    builder.sync(directive)
    builder.token("import")
    builder.space()

    val importedReference = directive.importedReference
    if (importedReference != null) {
      inImport = true
      format(importedReference)
      inImport = false
    }
    if (directive.isAllUnder) {
      builder.token(".")
      builder.token("*")
    }

    // Possible alias.
    val alias = directive.alias?.nameIdentifier
    if (alias != null) {
      builder.space()
      builder.token("as")
      builder.space()
      builder.token(alias.text ?: fail())
    }

    // Force a newline afterwards.
    builder.guessToken(";")
    builder.forcedBreak()
  }
}
