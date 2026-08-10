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
import org.jetbrains.kotlin.com.intellij.psi.impl.source.tree.LeafPsiElement
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtClassBody
import org.jetbrains.kotlin.psi.KtContainerNodeForControlStructureBody
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtEnumEntry
import org.jetbrains.kotlin.psi.KtIfExpression
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtObjectDeclaration
import org.jetbrains.kotlin.psi.KtStringTemplateEntry
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.psi.KtWhileExpression
import org.jetbrains.kotlin.psi.psiUtil.getNextSiblingIgnoringWhitespaceAndComments
import org.jetbrains.kotlin.psi.psiUtil.getPrevSiblingIgnoringWhitespaceAndComments
import org.jetbrains.kotlin.psi.psiUtil.prevLeaf

internal class RedundantSemicolonDetector {
  private val extraSemicolons = mutableListOf<PsiElement>()

  fun getRedundantSemicolonElements(): List<PsiElement> = extraSemicolons

  fun takeElement(element: PsiElement) {
    if (isExtraSemicolon(element)) {
      extraSemicolons += element
    }
  }

  private fun isLastConcreteChild(element: PsiElement): Boolean {
    val nextSibling = element.getNextSiblingIgnoringWhitespaceAndComments()
    return nextSibling == null ||
        (nextSibling is LeafPsiElement && nextSibling.elementType == KtTokens.RBRACE)
  }

  /** returns **true** if this element was an extra comma, **false** otherwise. */
  private fun isExtraSemicolon(element: PsiElement): Boolean {
    if (element !is LeafPsiElement || element.elementType != KtTokens.SEMICOLON) {
      return false
    }

    val parent = element.parent
    if (parent is KtStringTemplateExpression || parent is KtStringTemplateEntry) {
      return false
    }

    if (parent is KtEnumEntry) {
      val classBody = parent.parent as KtClassBody
      // Terminating semicolon with no other class members.
      return classBody.children.last() == parent
    }

    val prevConcreteSibling = element.getPrevSiblingIgnoringWhitespaceAndComments()
    if (parent is KtClassBody) {
      if (
          prevConcreteSibling is KtObjectDeclaration &&
              prevConcreteSibling.isCompanion() &&
              prevConcreteSibling.nameIdentifier == null &&
              !isLastConcreteChild(element)
      ) {
        // Example: `class Foo { companion object ; init { } }`
        return false
      }

      val enumEntryList = EnumEntryList.extractChildList(parent) ?: return true
      // Is not terminating semicolon or is terminating with no members.
      return element != enumEntryList.terminatingSemicolon || parent.children.isEmpty()
    }

    val prevLeaf = element.prevLeaf(false)
    if (
        (prevConcreteSibling is KtIfExpression || prevConcreteSibling is KtWhileExpression) &&
            prevLeaf is KtContainerNodeForControlStructureBody &&
            prevLeaf.text.isEmpty()
    ) {
      return false
    }

    val nextConcreteSibling = element.getNextSiblingIgnoringWhitespaceAndComments()

    /**
     * Examples:
     * ```
     *   val x = foo(0) ; { dead -> lambda }
     *   val y = foo(1) ; { dead -> lambda }.bar()
     * ```
     *
     * There are a huge number of cases here because the trailing lambda syntax is so flexible.
     * Therefore, we just assume that all semicolons followed by lambdas are meaningful. The cases
     * where they could be removed are too rare to justify the risk of changing behaviour.
     */
    val nextSiblingIsLambda =
        (nextConcreteSibling is KtLambdaExpression ||
            (nextConcreteSibling is KtDotQualifiedExpression &&
                nextConcreteSibling.receiverExpression is KtLambdaExpression))

    return !nextSiblingIsLambda
  }
}
