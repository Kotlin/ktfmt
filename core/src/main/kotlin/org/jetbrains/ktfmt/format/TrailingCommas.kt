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

import org.jetbrains.kotlin.com.intellij.psi.PsiComment
import org.jetbrains.kotlin.com.intellij.psi.PsiElement
import org.jetbrains.kotlin.com.intellij.psi.PsiWhiteSpace
import org.jetbrains.kotlin.com.intellij.psi.impl.source.tree.LeafPsiElement
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtClassBody
import org.jetbrains.kotlin.psi.KtCollectionLiteralExpression
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtEnumEntry
import org.jetbrains.kotlin.psi.KtFunctionLiteral
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtParameterList
import org.jetbrains.kotlin.psi.KtTypeArgumentList
import org.jetbrains.kotlin.psi.KtTypeParameterList
import org.jetbrains.kotlin.psi.KtValueArgumentList
import org.jetbrains.kotlin.psi.KtWhenEntry

/** Detects trailing commas or elements that should have trailing commas. */
object TrailingCommas {

  class Detector {
    private val trailingCommas = mutableListOf<PsiElement>()

    fun getTrailingCommaElements(): List<PsiElement> = trailingCommas

    /** returns **true** if this element was a traling comma, **false** otherwise. */
    fun takeElement(element: PsiElement) {
      if (isTrailingComma(element)) {
        trailingCommas += element
      }
    }

    private fun isTrailingComma(element: PsiElement): Boolean {
      if (element !is LeafPsiElement || element.elementType != KtTokens.COMMA) {
        return false
      }
      return extractManagedList(element.parent)?.trailingComma == element
    }
  }

  class Suggestor {
    private val suggestionElements = mutableListOf<PsiElement>()

    fun getTrailingCommaSuggestions(): List<PsiElement> = suggestionElements

    /**
     * Record elements which should have trailing commas inserted.
     *
     * This function determines which element type which may need trailing commas, as well as logic
     * for when they shold be inserted.
     *
     * Example:
     * ```
     * fun foo(
     *   x: VeryLongName,
     *   y: MoreThanLineLimit // Record this list
     * ) { }
     *
     * fun bar(x: ShortName, y: FitsOnLine) { } // Ignore this list
     * ```
     */
    fun takeElement(element: KtElement) {
      when (element) {
        is KtEnumEntry, // Only suggest on the KtClassBody container
        is KtWhenEntry -> return
        is KtParameterList -> {
          if (element.parent is KtFunctionLiteral && element.parent.parent is KtLambdaExpression) {
            return // Never add trailing commas to lambda param lists
          }
        }
        is KtClassBody -> {
          EnumEntryList.extractChildList(element)?.also {
            if (it.terminatingSemicolon != null) {
              return // Never add a trailing comma after there is already a terminating semicolon
            }
          }
        }
      }

      val list = extractManagedList(element) ?: return
      if (!element.textContains('\n')) {
        return // Only suggest trailing commas where there is already a line break
      }
      if (list.items.isEmpty()) {
        return // Never insert commas to empty lists
      }
      if (list.trailingComma != null) {
        return // Never insert a comma if there already is one somehow
      }

      suggestionElements.add(list.items.last().leftLeafIgnoringCommentsAndWhitespace())
    }
  }

  private class ManagedList(val items: List<KtElement>, val trailingComma: PsiElement?)

  private fun extractManagedList(element: PsiElement): ManagedList? {
    return when (element) {
      is KtValueArgumentList -> ManagedList(element.arguments, element.trailingComma)
      is KtParameterList -> ManagedList(element.parameters, element.trailingComma)
      is KtTypeArgumentList -> ManagedList(element.arguments, element.trailingComma)
      is KtTypeParameterList -> ManagedList(element.parameters, element.trailingComma)
      is KtCollectionLiteralExpression -> {
        ManagedList(element.getInnerExpressions(), element.trailingComma)
      }
      is KtWhenEntry -> ManagedList(element.conditions.toList(), element.trailingComma)
      is KtEnumEntry -> {
        EnumEntryList.extractParentList(element).let {
          ManagedList(it.enumEntries, it.trailingComma)
        }
      }
      is KtClassBody -> {
        EnumEntryList.extractChildList(element)?.let {
          ManagedList(it.enumEntries, it.trailingComma)
        }
      }
      else -> null
    }
  }

  /**
   * Return the element ahead of the where a comma would be appropriate for a list item.
   *
   * Example:
   * ```
   * fun foo(
   *   x: VeryLongName,
   *   y: MoreThanLineLimit /# Comment #/ = { it } /# Comment #/
   *                                         ^^^^^^ // After this element
   * ) { }
   * ```
   */
  private fun PsiElement.leftLeafIgnoringCommentsAndWhitespace(): PsiElement {
    var child = this.lastChild
    while (child != null) {
      if (child is PsiWhiteSpace || child is PsiComment) {
        child = child.prevSibling
      } else {
        return child.leftLeafIgnoringCommentsAndWhitespace()
      }
    }
    return this
  }
}
