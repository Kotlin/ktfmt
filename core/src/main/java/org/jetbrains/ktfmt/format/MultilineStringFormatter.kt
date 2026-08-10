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

import com.google.common.annotations.VisibleForTesting
import org.jetbrains.kotlin.com.intellij.psi.PsiComment
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtQualifiedExpression
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType
import org.jetbrains.kotlin.psi.psiUtil.startOffset

private const val TQ = "\"\"\""

/**
 * Adds and removes elements that are not strictly needed in the code, such as semicolons and unused
 * imports.
 */
class MultilineStringFormatter(val continuationIndentSize: Int) {
  internal fun format(file: KtFile): String {
    val code = file.text
    val result = StringBuilder(code)
    val multilineStringList =
        getMultilineTrimmedStringList(file)
            .sortedByDescending(MultilineTrimmedString::openStringOffset)
    for (multilineString in multilineStringList) {
      if (multilineString.stringLineCount < 2) {
        // Single line multiline strings are left alone
        continue
      }
      if (multilineString.hasTemplateExpression()) {
        // If there are any template expressions, we cannot format the string as it's possible that
        // the output of the template affects the result of the trimIndent/trimMargin call
        continue
      }
      if (multilineString.isNestedMultiline) {
        // We currently do not format code inside of template expressions
        continue
      }
      val indentation = " ".repeat(multilineString.indentCount)
      val continuationIndentation = " ".repeat(continuationIndentSize)

      val multiline = StringBuilder()
      // Open string
      if (multilineString.isDollarString) multiline.append("$$")
      multiline.append(TQ)
      multiline.appendLine()

      var isLastLineEmpty = true

      // String content
      multilineString.getStringContent().forEach { lineContent ->
        if (multilineString.usesTrimMargin || lineContent.isNotEmpty()) {
          multiline.append(indentation)
          multiline.append(multilineString.indentationSuffix)
        }
        multiline.appendLine(lineContent)

        isLastLineEmpty = lineContent.isEmpty()
      }

      // Close string
      if (multilineString.usesTrimMargin && isLastLineEmpty) {
        // Remove the last new line character
        multiline.deleteAt(multiline.lastIndex)
      } else {
        multiline.append(indentation)
      }
      multiline.appendLine(TQ)

      // Preserve any comments between the string and the trim method call
      for (comment in multilineString.commentsBetweenStringAndTrimCall) {
        multiline.append(indentation)
        multiline.append(continuationIndentation)
        multiline.appendLine(comment)
      }

      // Trim method call
      multiline.append(indentation)
      multiline.append(continuationIndentation)

      // Now replace the original multiline string with the newly formatted one
      result.replace(
          multilineString.openStringOffset,
          multilineString.trimMethodCallOffset,
          multiline.toString(),
      )
    }

    return result.toString()
  }

  @VisibleForTesting
  internal fun getMultilineTrimmedStringList(file: KtFile): List<MultilineTrimmedString> {
    val code = file.text
    val strings = mutableListOf<MultilineTrimmedString>()
    file.accept(
        object : KtTreeVisitorVoid() {
          override fun visitQualifiedExpression(expression: KtQualifiedExpression) {
            super.visitQualifiedExpression(expression)
            val receiver = expression.receiverExpression
            if (receiver !is KtStringTemplateExpression) return
            val isDollarString = receiver.text.startsWith("$$")
            val selectorExpression = expression.selectorExpression?.text.orEmpty().trim()
            val isTrimMargin = selectorExpression.startsWith("trimMargin()")
            val isTrimIndent = selectorExpression.startsWith("trimIndent()")
            if (isTrimIndent || isTrimMargin) {
              // -1 here to account for the space after the dot
              val trimOffset = checkNotNull(expression.selectorExpression).startOffset - 1
              val stringOffset = receiver.startOffset
              val lineStart = code.substring(0, stringOffset).lines().lastIndex
              val lineEnd = code.substring(0, trimOffset).lines().lastIndex
              val indentCount =
                  code.substring(0, stringOffset).lines().last().substringBefore(TQ).let {
                    it.length - it.trimStart().length
                  }
              // Collect comments between the closing """ and the .trimX() call
              val comments = mutableListOf<String>()
              var child = receiver.nextSibling
              while (child != null && child !== expression.selectorExpression) {
                if (child is PsiComment) {
                  comments.add(child.text)
                }
                child = child.nextSibling
              }
              strings.add(
                  MultilineTrimmedString(
                      usesTrimMargin = isTrimMargin,
                      isDollarString = isDollarString,
                      indentCount = indentCount,
                      lines = code.lines().subList(lineStart, lineEnd + 1),
                      lineStart = lineStart,
                      lineEnd = lineEnd,
                      openStringOffset = stringOffset,
                      trimMethodCallOffset = trimOffset,
                      isNestedMultiline =
                          expression.getParentOfType<KtStringTemplateExpression>(strict = false) !=
                              null,
                      commentsBetweenStringAndTrimCall = comments,
                  ),
              )
            }
          }
        },
    )
    return strings.toList()
  }
}

@VisibleForTesting
internal data class MultilineTrimmedString(
    /* Whether this is a trimMargin or a trimIndent call. */
    val usesTrimMargin: Boolean,
    /* Whether this is a dollar string or a simple string. */
    val isDollarString: Boolean,
    /* The number of spaces to indent the string template. */
    val indentCount: Int,
    /* The lines of the string template, inclunding the trimX call. */
    val lines: List<String>,
    /* The line number of the first line of the string template, 0-indexed. */
    val lineStart: Int,
    /* The line number of the last line of the string template, including the trimX call, 0-indexed. */
    val lineEnd: Int,
    /* The start offset relative to the full code of the string template, which is the starting index of `"""` or `$$"""`. */
    val openStringOffset: Int,
    /* The start offset relative to the full code of the trim method call in this multiline string, right before `.trimX`. */
    val trimMethodCallOffset: Int,
    /* Whether this multiline string is nested in another multiline string. */
    val isNestedMultiline: Boolean,
    /* Comments between the closing """ and the .trimX() call. */
    val commentsBetweenStringAndTrimCall: List<String> = emptyList(),
) {
  companion object {
    private val simpleTemplateExpressionRegex = Regex("""\${'$'}{1}((\{?[A-Za-z_\s])|\{$)""")
    private val dollarTemplateExpressionRegex = Regex("""\${'$'}{2}((\{?[A-Za-z_\s])|\{$)""")
  }

  val usesTrimIndent: Boolean
    get() = !usesTrimMargin

  val lastStringLineIndex: Int = lines.indexOfLast { TQ in it }

  /* The minimal indent level of the string template, useful to adjust trimIndent. */
  val minimalIndent: Int
    get() =
        (lines.subList(1, lastStringLineIndex) +
                // Cannot include anything before the string opening `"""`
                lines.first().substringAfterLast(TQ) +
                // Cannot include anything after the string closing `"""`
                lines[lastStringLineIndex].substringBefore(TQ))
            .minOf { if (it.isBlank()) Int.MAX_VALUE else it.indentLevel() }

  val indentationSuffix: String = if (usesTrimMargin) "|" else ""

  /* The number of lines in the string template, excluding the trimX call. */
  val stringLineCount: Int = lastStringLineIndex + 1

  fun hasTemplateExpression(): Boolean {
    val regex = if (isDollarString) dollarTemplateExpressionRegex else simpleTemplateExpressionRegex
    return lines.any { regex.find(it) != null }
  }

  fun getStringContent(): List<String> {
    return buildList<String> {
      lines.forEachIndexed { i, line ->
        if (i == 0 || i == lastStringLineIndex) {
          val stringContent = if (i == 0) line.substringAfter(TQ) else line.substringBeforeLast(TQ)
          // Ignores first and last line content if they are blank
          if (stringContent.isNotBlank()) {
            add(stringContent.trimmed())
          }
        } else if (i > lastStringLineIndex) {
          // No longer part of the string template, so we can ignore it
        } else {
          add(line.trimmed())
        }
      }
    }
  }

  private fun String.indentLevel(): Int = length - trimStart().length

  private fun String.trimmed(): String {
    if (usesTrimIndent) return drop(minimalIndent)

    if (trimStart().firstOrNull() == '|') {
      return substringAfter("|")
    }
    return this
  }
}
