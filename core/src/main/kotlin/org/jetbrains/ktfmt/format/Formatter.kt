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

import com.google.common.collect.ImmutableList
import com.google.common.collect.Range
import com.google.common.collect.RangeSet
import com.google.googlejavaformat.Doc
import com.google.googlejavaformat.DocBuilder
import com.google.googlejavaformat.OpsBuilder
import com.google.googlejavaformat.java.FormatterException
import com.google.googlejavaformat.java.JavaOutput
import org.jetbrains.kotlin.com.intellij.openapi.util.text.StringUtil
import org.jetbrains.kotlin.com.intellij.psi.PsiComment
import org.jetbrains.kotlin.com.intellij.psi.PsiElement
import org.jetbrains.kotlin.com.intellij.psi.PsiElementVisitor
import org.jetbrains.kotlin.com.intellij.psi.PsiWhiteSpace
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtImportDirective
import org.jetbrains.kotlin.psi.psiUtil.endOffset
import org.jetbrains.kotlin.psi.psiUtil.startOffset
import org.jetbrains.ktfmt.debughelpers.printOps
import org.jetbrains.ktfmt.format.RedundantElementManager.addRedundantElements
import org.jetbrains.ktfmt.format.RedundantElementManager.dropRedundantElements
import org.jetbrains.ktfmt.kdoc.KDocCommentsHelper

object Formatter {

  @JvmField
  val META_FORMAT =
      FormattingOptions(
          blockIndent = 2,
          continuationIndent = 4,
          trailingCommaManagementStrategy = TrailingCommaManagementStrategy.ONLY_ADD,
      )

  @JvmField
  val GOOGLE_FORMAT =
      FormattingOptions(
          blockIndent = 2,
          continuationIndent = 2,
      )

  /** A format that attempts to reflect https://kotlinlang.org/docs/coding-conventions.html. */
  @JvmField
  val KOTLINLANG_FORMAT =
      FormattingOptions(
          blockIndent = 4,
          continuationIndent = 4,
      )

  private val MINIMUM_KOTLIN_VERSION = KotlinVersion(1, 4)

  /**
   * format formats the Kotlin code given in 'code' and returns it as a string. This method is
   * accessed through Reflection.
   */
  @JvmStatic
  @Throws(FormatterException::class, ParseError::class)
  fun format(code: String, fileType: FileType): String =
      format(META_FORMAT, KotlinCode(code, fileType))

  /**
   * Formats the Kotlin code given in [code] and returns it as a string.
   *
   * @param characterRanges zero-indexed character ranges to format, using closed-open bounds
   *
   * When [characterRanges] are non-null, only pretty-print replacements are limited to those
   * ranges. Whole-file cleanup passes, such as import cleanup and multiline string formatting,
   * still run afterward, mirroring google-java-format's cleanup-after-selection behavior.
   */
  @JvmStatic
  @JvmOverloads
  @Throws(FormatterException::class, ParseError::class)
  fun format(
      options: FormattingOptions,
      code: KotlinCode,
      characterRanges: RangeSet<Int>? = null,
  ): String {
    val formattedCode =
        if (characterRanges == null) {
          FormatterContext(code)
              .transform { sortedAndDistinctImports(it) }
              .transform { dropRedundantElements(it, options) }
              .transform { addRedundantElements(it, options) }
              .let { prettyPrintAndManageTrailingCommas(it, options, lineSeparator = "\n") }
              .transform { MultilineStringFormatter(options.continuationIndent).format(it) }
              .code
        } else {
          val partiallyFormattedCode =
              if (characterRanges.isEmpty) {
                code
              } else {
                FormatterContext(code)
                    .transform {
                      prettyPrint(
                          it,
                          options,
                          lineSeparator = "\n",
                          characterRanges = characterRanges.asRanges(),
                      )
                    }
                    .code
              }
          FormatterContext(partiallyFormattedCode)
              .transform { dropRedundantElements(it, options) }
              .transform { sortedAndDistinctImports(it, trimLeadingWhitespace = true) }
              .transform { addRedundantElements(it, options) }
              .transform { MultilineStringFormatter(options.continuationIndent).format(it) }
              .code
        }

    return formattedCode.toString()
  }

  /**
   * Pretty-prints & reprints while [addRedundantElements] keeps adding trailing commas, so a comma
   * inserted after layout can't leave a line over the limit.
   */
  private tailrec fun prettyPrintAndManageTrailingCommas(
      context: FormatterContext,
      options: FormattingOptions,
      lineSeparator: String,
  ): FormatterContext {
    val prettyCode = context.transform { prettyPrint(it, options, lineSeparator) }
    val newCode = prettyCode.transform { addRedundantElements(it, options) }
    return if (newCode == prettyCode) prettyCode
    else prettyPrintAndManageTrailingCommas(newCode, options, lineSeparator)
  }

  /** prettyPrint reflows 'code' using google-java-format's engine. */
  private fun prettyPrint(
      file: KtFile,
      options: FormattingOptions,
      lineSeparator: String,
      characterRanges: Collection<Range<Int>> =
          ImmutableList.of(Range.closedOpen(0, file.text.length)),
  ): String {
    val code = file.text
    val kotlinInput = KotlinInput(code, file)
    val javaOutput =
        JavaOutput(lineSeparator, kotlinInput, KDocCommentsHelper(lineSeparator, options.maxWidth))
    val builder = OpsBuilder(kotlinInput, javaOutput)
    file.accept(createAstVisitor(options, builder))
    builder.sync(kotlinInput.text.length)
    builder.drain()
    val ops = builder.build()
    if (options.debuggingPrintOpsAfterFormatting) {
      printOps(ops)
    }
    val doc = DocBuilder().withOps(ops).build()
    doc.computeBreaks(javaOutput.commentsHelper, options.maxWidth, Doc.State(+0, 0))
    doc.write(javaOutput)
    javaOutput.flush()

    val tokenRangeSet = kotlinInput.characterRangesToTokenRanges(characterRanges)
    return WhitespaceTombstones.replaceTombstoneWithTrailingWhitespace(
        JavaOutput.applyReplacements(code, javaOutput.getFormatReplacements(tokenRangeSet)),
    )
  }

  private fun createAstVisitor(options: FormattingOptions, builder: OpsBuilder): PsiElementVisitor {
    if (KotlinVersion.CURRENT < MINIMUM_KOTLIN_VERSION) {
      throw RuntimeException("Unsupported runtime Kotlin version: " + KotlinVersion.CURRENT)
    }
    return if (options.useExperimentalEngine) {
      KotlinLangInputAstVisitor(options, builder)
    } else {
      KotlinInputAstVisitor(options, builder)
    }
  }

  private fun sortedAndDistinctImports(
      file: KtFile,
      trimLeadingWhitespace: Boolean = false,
  ): String {
    val code = file.text

    val importList = file.importList ?: return code
    if (importList.imports.isEmpty()) {
      return code
    }

    val commentList = mutableListOf<PsiElement>()
    // Find non-import elements; comments are moved, in order, to the top of the import list. Other
    // non-import elements throw a ParseError.
    var element = importList.firstChild
    while (element != null) {
      if (element is PsiComment) {
        commentList.add(element)
      } else if (element !is KtImportDirective && element !is PsiWhiteSpace) {
        throw ParseError(
            "Imports not contiguous: " + element.text,
            StringUtil.offsetToLineColumn(code, element.startOffset),
        )
      }
      element = element.nextSibling
    }
    fun canonicalText(importDirective: KtImportDirective) =
        importDirective.importedFqName?.asString() +
            " " +
            importDirective.alias?.text?.replace("`", "") +
            " " +
            if (importDirective.isAllUnder) "*" else ""

    val sortedImports = importList.imports.sortedBy(::canonicalText).distinctBy(::canonicalText)
    val importsWithComments = commentList + sortedImports

    val body = importsWithComments.joinToString(separator = "\n") { imprt -> imprt.text }
    /*
     * Kludge: idempotent formatting.
     * This step optimizes the following goal -- producing **identical** code for already formatted
     * code, as it's important for PSI-reuse.
     * There is exactly one case where this step should add trailing newline -- when an inline
     * comment follows the last import statement. We check for that (note it gives false positives for `/* // */`
     * which is acceptable -- later prettyPrint step will fix that) and avoid extra-append when it is redundant.
     */
    val needsTerminator = body.lastIndexOf('\n').let { it >= 0 && body.indexOf("//", it + 1) >= 0 }
    val replaceStart =
        if (trimLeadingWhitespace && code.substring(0, importList.startOffset).isBlank()) {
          0
        } else {
          importList.startOffset
        }
    return code.replaceRange(
        replaceStart,
        importList.endOffset,
        if (needsTerminator) body + "\n" else body,
    )
  }
}
