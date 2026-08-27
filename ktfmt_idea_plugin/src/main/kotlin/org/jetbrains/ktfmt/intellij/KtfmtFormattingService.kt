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

package org.jetbrains.ktfmt.intellij

import com.google.common.collect.Range
import com.google.common.collect.RangeSet
import com.google.common.collect.TreeRangeSet
import com.google.googlejavaformat.java.FormatterException
import com.intellij.formatting.service.AsyncDocumentFormattingService
import com.intellij.formatting.service.AsyncFormattingRequest
import com.intellij.formatting.service.FormattingService.Feature
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.ktfmt.format.FileType
import org.jetbrains.ktfmt.format.Formatter.format
import org.jetbrains.ktfmt.format.FormattingOptions
import org.jetbrains.ktfmt.format.KotlinCode
import org.jetbrains.ktfmt.format.kotlinFileType

private const val PARSING_ERROR_NOTIFICATION_GROUP: String = "ktfmt parsing error"

private const val PARSING_ERROR_TITLE: String = PARSING_ERROR_NOTIFICATION_GROUP

/** Uses `ktfmt` to reformat code. */
class KtfmtFormattingService : AsyncDocumentFormattingService() {
  override fun createFormattingTask(request: AsyncFormattingRequest): FormattingTask {
    val project = request.context.project
    val settings = KtfmtSettings.getInstance(project)
    val style = settings.uiFormatterStyle
    val formattingOptions =
        if (style == UiFormatterStyle.Custom) {
          settings.customFormattingOptions
        } else {
          UiFormatterStyle.getStandardFormattingOptions(style)
        }
    return KtfmtFormattingTask(request, formattingOptions)
  }

  override fun getNotificationGroupId(): String = PARSING_ERROR_NOTIFICATION_GROUP

  override fun getName(): String = "ktfmt"

  override fun getFeatures(): Set<Feature> = setOf(Feature.FORMAT_FRAGMENTS)

  override fun canFormat(file: PsiFile): Boolean =
      KotlinFileType.INSTANCE.name == file.fileType.name &&
          KtfmtSettings.getInstance(file.project).isEnabled

  private class KtfmtFormattingTask(
      private val request: AsyncFormattingRequest,
      private val formattingOptions: FormattingOptions,
  ) : FormattingTask {
    override fun run() {
      try {
        val fileType = request.ioFile?.kotlinFileType ?: FileType.SCRIPT
        val formattedText =
            if (request.isWholeFileFormatting()) {
              format(formattingOptions, KotlinCode(request.documentText, fileType))
            } else {
              format(
                  formattingOptions,
                  KotlinCode(request.documentText, fileType),
                  characterRanges = request.toRanges(),
              )
            }
        request.onTextReady(formattedText)
      } catch (e: FormatterException) {
        request.onError(
            PARSING_ERROR_TITLE,
            "ktfmt failed. Does ${request.context.containingFile.name} have syntax errors?",
        )
      }
    }

    override fun isRunUnderProgress(): Boolean = true

    override fun cancel(): Boolean = false

    private fun AsyncFormattingRequest.toRanges(): RangeSet<Int> {
      val ranges = TreeRangeSet.create<Int>()
      for (range in formattingRanges) {
        ranges.add(Range.closedOpen(range.startOffset, range.endOffset))
      }
      return ranges
    }

    private fun AsyncFormattingRequest.isWholeFileFormatting(): Boolean {
      val ranges = formattingRanges
      return ranges.size == 1 && ranges.single().isWholeFileRange(documentText.length)
    }

    private fun TextRange.isWholeFileRange(documentLength: Int): Boolean {
      // IntelliJ represents a no-selection whole-file format request as a range covering the file.
      // Match GJF's leniency for end offsets because the IDE can pass slightly inaccurate ranges.
      return startOffset == 0 && endOffset >= documentLength
    }
  }
}
