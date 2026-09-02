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
import org.jetbrains.kotlin.kdoc.psi.impl.KDocImpl
import org.jetbrains.kotlin.kdoc.psi.impl.KDocLink
import org.jetbrains.kotlin.kdoc.psi.impl.KDocName
import org.jetbrains.kotlin.kdoc.psi.impl.KDocSection
import org.jetbrains.kotlin.kdoc.psi.impl.KDocTag
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtImportDirective
import org.jetbrains.kotlin.psi.KtImportList
import org.jetbrains.kotlin.psi.KtPackageDirective
import org.jetbrains.kotlin.psi.KtReferenceExpression
import org.jetbrains.kotlin.psi.psiUtil.getChildrenOfType

internal class RedundantImportDetector(val enabled: Boolean) {
  companion object {
    private val OPERATORS =
        setOf(
            // Unary prefix operators
            "unaryPlus",
            "unaryMinus",
            "not",
            // Increments and decrements
            "inc",
            "dec",
            // Arithmetic operators
            "plus",
            "minus",
            "times",
            "div",
            "rem",
            "mod", // deprecated
            "rangeTo",
            // 'In' operator
            "contains",
            // Indexed access operator
            "get",
            "set",
            // Invoke operator
            "invoke",
            // Augmented assignments
            "plusAssign",
            "minusAssign",
            "timesAssign",
            "divAssign",
            "remAssign",
            "modAssign", // deprecated
            // Equality and inequality operators
            "equals",
            // Comparison operators
            "compareTo",
            // Iterator operators
            "iterator",
            "next",
            "hasNext",
            // Bitwise operators
            "and",
            "or",
            // Property delegation operators
            "getValue",
            "setValue",
            "provideDelegate",
            // assign operator - Gradle compiler plugin
            // https://blog.gradle.org/simpler-kotlin-dsl-property-assignment
            "assign",
        )

    private val COMPONENT_OPERATOR_REGEX = Regex("component\\d+")

    private val KDOC_TAG_SKIP_FIRST_REFERENCE_REGEX = Regex("^@(param|property) (.+)")
  }

  private var thisPackage: FqName? = null

  private val usedReferences = OPERATORS.toMutableSet()

  private lateinit var importCleanUpCandidates: Set<KtImportDirective>

  private var isPackageElement = false
  private var isImportElement = false

  fun takePackageDirective(directive: KtPackageDirective, superBlock: () -> Unit) {
    if (!enabled) {
      return superBlock.invoke()
    }

    thisPackage = directive.fqName

    isPackageElement = true
    superBlock.invoke()
    isPackageElement = false
  }

  fun takeImportList(importList: KtImportList, superBlock: () -> Unit) {
    if (!enabled) {
      return superBlock.invoke()
    }

    importCleanUpCandidates =
        importList.imports
            .filter { import ->
              val identifier = import.identifier ?: return@filter false
              import.isValidImport &&
                  identifier !in OPERATORS &&
                  !COMPONENT_OPERATOR_REGEX.matches(identifier)
            }
            .toSet()

    isImportElement = true
    superBlock.invoke()
    isImportElement = false
  }

  fun takeKdoc(kdoc: KDocImpl) {
    kdoc.getChildrenOfType<KDocSection>().forEach { kdocSection ->
      val tagLinks =
          kdocSection.getChildrenOfType<KDocTag>().flatMap { tag ->
            val tagLinks = tag.getChildrenOfType<KDocLink>().toList()
            when {
              KDOC_TAG_SKIP_FIRST_REFERENCE_REGEX.matches(tag.text) -> tagLinks.drop(1)
              else -> tagLinks
            }
          }

      val links = kdocSection.getChildrenOfType<KDocLink>() + tagLinks

      val references = links.flatMap { link ->
        link.getChildrenOfType<KDocName>().mapNotNull {
          it.getQualifiedName().firstOrNull()?.trim('[', ']')
        }
      }

      usedReferences += references
    }
  }

  fun takeReferenceExpression(expression: KtReferenceExpression) {
    if (!enabled) return

    if (!isPackageElement && !isImportElement && expression.children.isEmpty()) {
      usedReferences += expression.text.trim('`')
    }
  }

  fun getRedundantImportElements(): List<PsiElement> {
    if (!enabled) return emptyList()

    val identifierCounts =
        importCleanUpCandidates.groupBy { it.identifier }.mapValues { it.value.size }

    return importCleanUpCandidates.filter { importCandidate ->
      val isUsed = importCandidate.identifier in usedReferences
      val importedFqName = importCandidate.importedFqName
      // For backtick-escaped full-path imports (e.g., import `foo.bar.baz`), the PSI creates
      // a single-segment FqName whose short name contains dots. Its parent() returns ROOT,
      // which would incorrectly match the default package. Detect and exclude this case.
      val isBracketEscapedPath = importedFqName?.shortName()?.asString()?.contains('.') == true
      val isFromThisPackage = !isBracketEscapedPath && importedFqName?.parent() == thisPackage
      val hasAlias = importCandidate.alias != null
      val isOverload = requireNotNull(identifierCounts[importCandidate.identifier]) > 1
      // Remove if...
      !isUsed || (isFromThisPackage && !hasAlias && !isOverload)
    }
  }

  /** The imported short name, possibly an alias name, if any. */
  private inline val KtImportDirective.identifier: String?
    get() {
      val name = importPath?.importedName?.identifier?.trim('`') ?: return null
      // When the entire import path is backtick-escaped (e.g., import `foo.bar.baz`),
      // the PSI treats it as a single name containing dots rather than a dot-qualified
      // expression. Extract the last segment to match reference expressions in the code body.
      val dotIndex = name.lastIndexOf('.')
      return if (dotIndex >= 0) name.substring(dotIndex + 1) else name
    }
}
