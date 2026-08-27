package org.jetbrains.ktfmt.format.visitor

import com.google.googlejavaformat.OpsBuilder
import org.jetbrains.kotlin.com.intellij.psi.PsiComment
import org.jetbrains.kotlin.com.intellij.psi.PsiElement
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtImportDirective
import org.jetbrains.kotlin.psi.KtPackageDirective
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
    var prev: PsiElement? = null
    for (child in file.children) {
      if (child.text.isBlank()) continue
      if (child is PsiComment) continue
      builder.forcedBreak()
      builder.blankLineWanted(shouldPreserveLineBreak(prev, child))
      builder.markForPartialFormat()
      format(child)
      builder.guessToken(";")
      builder.markForPartialFormat()
      prev = child
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
      format(importedReference)
    }
    if (directive.isAllUnder) {
      builder.token(".")
      builder.token("*")
    }

    val alias = directive.alias?.nameIdentifier
    if (alias != null) {
      builder.space()
      builder.token("as")
      builder.space()
      builder.token(alias.text ?: fail())
    }

    builder.guessToken(";")
    builder.forcedBreak()
  }

  private fun shouldPreserveLineBreak(
      prev: PsiElement?,
      curr: PsiElement,
  ): OpsBuilder.BlankLineWanted =
      when {
        prev == null -> OpsBuilder.BlankLineWanted.PRESERVE
        /**
         * This is a special case for context receivers on top-level functions. Parser does not
         * attach the context receivers to the declaration node, because `context(Something)` can
         * theoretically be a function invocation. We need to manually detect this and don't allow
         * blank lines in between.
         *
         * ```
         * context(Something) // KtScriptInitializer
         * fun foo() {} // KtNamedFunction; KtNamedFunction.contextReceivers == null
         *
         * class C {
         *     context(Something)
         *     fun foo() {} // KtNamedFunction; KtNamedFunction.contextReceivers != null
         * }
         * ```
         */
        prev is KtScriptInitializer && prev.firstChild?.firstChild?.firstChild?.text == "context" ->
            OpsBuilder.BlankLineWanted.NO
        // preserve blank lines between properties and before comments
        prev is KtProperty && curr is KtProperty -> OpsBuilder.BlankLineWanted.PRESERVE
        curr is PsiComment -> OpsBuilder.BlankLineWanted.PRESERVE
        // force blank line between everything else
        else -> OpsBuilder.BlankLineWanted.YES
      }
}
