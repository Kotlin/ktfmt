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
import org.jetbrains.kotlin.lexer.KtModifierKeywordToken
import org.jetbrains.kotlin.psi.KtAnnotation
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtModifierList
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid
import org.jetbrains.kotlin.psi.psiUtil.endOffset
import org.jetbrains.kotlin.psi.psiUtil.startOffset

/** Sorts declaration modifiers according to the Kotlin coding conventions. */
internal object ModifierSorter {
  private val modifierRanks = listOf(
      setOf("public", "protected", "private", "internal"),
      setOf("expect", "actual"),
      setOf("final", "open", "abstract", "sealed"),
      setOf("const"),
      setOf("external"),
      setOf("override"),
      setOf("lateinit"),
      setOf("tailrec"),
      setOf("vararg"),
      setOf("suspend"),
      setOf("inner"),
      setOf("enum", "annotation", "fun"),
      setOf("companion"),
      setOf("inline", "value"),
      setOf("infix"),
      setOf("operator"),
      setOf("data"),
  )
      .flatMapIndexed { rank, modifiers -> modifiers.map { it to rank } }
      .toMap()

  internal fun sort(file: KtFile): String {
    val replacements = mutableListOf<Replacement>()
    file.accept(
        object : KtTreeVisitorVoid() {
          override fun visitModifierList(list: KtModifierList) {
            sortedText(list)?.let { replacements.add(Replacement(list, it)) }
            super.visitModifierList(list)
          }
        },
    )

    if (replacements.isEmpty()) return file.text
    val innermostReplacements = replacements.filter { candidate ->
      replacements.none { other ->
        other !== candidate &&
            other.element.startOffset >= candidate.element.startOffset &&
            other.element.endOffset <= candidate.element.endOffset
      }
    }
    val result = StringBuilder(file.text)
    for (replacement in innermostReplacements.sortedByDescending { it.element.endOffset }) {
      result.replace(
          replacement.element.startOffset,
          replacement.element.endOffset,
          replacement.text,
      )
    }
    val sortedCode = result.toString()
    return if (innermostReplacements.size == replacements.size) sortedCode
    else sort(Parser.parse(sortedCode))
  }

  private fun sortedText(list: KtModifierList): String? {
    val significantChildren =
        generateSequence(list.node.firstChildNode) { it.treeNext }
            .map { it.psi }
            .filterNot { it is PsiWhiteSpace }
            .toList()
    val parts = mutableListOf<Part>()
    val sortableSegment = mutableListOf<PsiElement>()

    fun flushSortableSegment() {
      if (sortableSegment.isEmpty()) return
      parts.add(Part.Sortable(sortSegment(list, sortableSegment)))
      sortableSegment.clear()
    }

    for (child in significantChildren) {
      if (child is PsiComment || child.isAnnotation() || child.sortRank() != null) {
        sortableSegment.add(child)
      } else {
        flushSortableSegment()
        parts.add(Part.Fixed(child.text))
      }
    }
    flushSortableSegment()

    val sorted = parts.map { it.text }.joinWithSpaces()
    return sorted.takeIf { it != list.text }
  }

  private fun sortSegment(list: KtModifierList, elements: List<PsiElement>): String {
    val units = mutableListOf<SortableUnit>()
    val leadingComments = mutableListOf<PsiComment>()

    for (element in elements) {
      if (element is PsiComment) {
        val previous = units.lastOrNull()
        if (previous != null && list.hasNoNewlineBetween(previous.base, element)) {
          previous.trailingComments.add(element)
        } else {
          leadingComments.add(element)
        }
        continue
      }

      units.add(
          SortableUnit(
              base = element,
              rank = element.sortRank(),
              isAnnotation = element.isAnnotation(),
              leadingComments = leadingComments.toMutableList(),
          ),
      )
      leadingComments.clear()
    }

    if (leadingComments.isNotEmpty()) {
      units.lastOrNull()?.followingComments?.addAll(leadingComments)
    }

    val sortedUnits =
        units.filter { it.isAnnotation } + units.filterNot { it.isAnnotation }.sortedBy { it.rank }
    if (sortedUnits.map { it.base } == units.map { it.base }) {
      return list.text.substring(
          elements.first().startOffset - list.startOffset,
          elements.last().endOffset - list.startOffset,
      )
    }
    return sortedUnits.map { it.render() }.joinWithSpaces()
  }

  private fun PsiElement.isAnnotation(): Boolean = this is KtAnnotation || this is KtAnnotationEntry

  private fun PsiElement.sortRank(): Int? =
      if (node.elementType is KtModifierKeywordToken) modifierRanks[text] else null

  private fun KtModifierList.hasNoNewlineBetween(first: PsiElement, second: PsiElement): Boolean =
      text.substring(first.endOffset - startOffset, second.startOffset - startOffset).none {
        it == '\n' || it == '\r'
      }

  private fun SortableUnit.render(): String = buildString {
    for (comment in leadingComments) {
      append(comment.text)
      append('\n')
    }
    append(base.text)
    for (comment in trailingComments) {
      append(' ')
      append(comment.text)
      if (comment.text.startsWith("//")) append('\n')
    }
    for (comment in followingComments) {
      append('\n')
      append(comment.text)
      append('\n')
    }
  }

  private fun Iterable<String>.joinWithSpaces(): String = buildString {
    for (text in this@joinWithSpaces) {
      if (isNotEmpty() && !endsWith('\n') && !endsWith('\r')) append(' ')
      append(text)
    }
  }

  private data class Replacement(val element: PsiElement, val text: String)

  private data class SortableUnit(
      val base: PsiElement,
      val rank: Int?,
      val isAnnotation: Boolean,
      val leadingComments: MutableList<PsiComment>,
      val trailingComments: MutableList<PsiComment> = mutableListOf(),
      val followingComments: MutableList<PsiComment> = mutableListOf(),
  )

  private sealed interface Part {
    val text: String

    data class Sortable(override val text: String) : Part

    data class Fixed(override val text: String) : Part
  }
}
