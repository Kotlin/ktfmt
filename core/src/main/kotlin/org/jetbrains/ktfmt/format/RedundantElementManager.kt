/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.ktfmt.format

import org.jetbrains.kotlin.com.intellij.psi.PsiElement
import org.jetbrains.kotlin.com.intellij.psi.PsiWhiteSpace
import org.jetbrains.kotlin.kdoc.psi.impl.KDocImpl
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtImportList
import org.jetbrains.kotlin.psi.KtPackageDirective
import org.jetbrains.kotlin.psi.KtReferenceExpression
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid
import org.jetbrains.kotlin.psi.psiUtil.endOffset
import org.jetbrains.kotlin.psi.psiUtil.startOffset

/**
 * Adds and removes elements that are not strictly needed in the code, such as semicolons and unused
 * imports.
 */
object RedundantElementManager {
  /** Remove extra semicolons and unused imports, if enabled in the [options] */
  internal fun dropRedundantElements(file: KtFile, options: FormattingOptions): String {
    val code = file.text
    val redundantImportDetector = RedundantImportDetector(enabled = options.removeUnusedImports)
    val redundantSemicolonDetector = RedundantSemicolonDetector()
    val trailingCommaDetector = TrailingCommas.Detector()

    file.accept(
        object : KtTreeVisitorVoid() {
          override fun visitElement(element: PsiElement) {
            if (element is KDocImpl) {
              redundantImportDetector.takeKdoc(element)
              return
            }

            redundantSemicolonDetector.takeElement(element)
            if (options.trailingCommaManagementStrategy.removeRedundantTrailingCommas) {
              trailingCommaDetector.takeElement(element)
            }
            super.visitElement(element)
          }

          override fun visitPackageDirective(directive: KtPackageDirective) {
            redundantImportDetector.takePackageDirective(directive) {
              super.visitPackageDirective(directive)
            }
          }

          override fun visitImportList(importList: KtImportList) {
            redundantImportDetector.takeImportList(importList) { super.visitImportList(importList) }
          }

          override fun visitReferenceExpression(expression: KtReferenceExpression) {
            redundantImportDetector.takeReferenceExpression(expression)
            super.visitReferenceExpression(expression)
          }
        },
    )

    val elementsToRemove =
        redundantSemicolonDetector.getRedundantSemicolonElements() +
            redundantImportDetector.getRedundantImportElements() +
            trailingCommaDetector.getTrailingCommaElements()
    if (elementsToRemove.isEmpty()) return code
    val result = StringBuilder(code)

    for (element in elementsToRemove.sortedByDescending(PsiElement::endOffset)) {
      // Don't insert extra newlines when the semicolon is already a line terminator
      val replacement =
          if (element.text == ";" && !element.nextSibling.containsNewline()) {
            "\n"
          } else {
            ""
          }
      result.replace(element.startOffset, element.endOffset, replacement)
    }

    return result.toString()
  }

  internal fun addRedundantElements(file: KtFile, options: FormattingOptions): String {
    if (!options.manageTrailingCommas) {
      return file.text
    }

    val code = file.text
    val trailingCommaSuggestor = TrailingCommas.Suggestor()

    file.accept(
        object : KtTreeVisitorVoid() {
          override fun visitKtElement(element: KtElement) {
            trailingCommaSuggestor.takeElement(element)
            super.visitElement(element)
          }
        },
    )

    val suggestionElements = trailingCommaSuggestor.getTrailingCommaSuggestions()
    if (suggestionElements.isEmpty()) return code
    val result = StringBuilder(code)

    for (element in suggestionElements.sortedByDescending(PsiElement::endOffset)) {
      result.insert(element.endOffset, ',')
    }

    return result.toString()
  }

  private fun PsiElement?.containsNewline(): Boolean {
    if (this !is PsiWhiteSpace) return false
    return this.textContains('\n')
  }
}
