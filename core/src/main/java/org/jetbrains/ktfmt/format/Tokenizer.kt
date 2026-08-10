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

import java.util.regex.Pattern
import org.jetbrains.kotlin.com.intellij.openapi.util.text.StringUtil
import org.jetbrains.kotlin.com.intellij.psi.PsiComment
import org.jetbrains.kotlin.com.intellij.psi.PsiElement
import org.jetbrains.kotlin.com.intellij.psi.PsiWhiteSpace
import org.jetbrains.kotlin.com.intellij.psi.impl.source.tree.LeafPsiElement
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtFunctionLiteral
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid
import org.jetbrains.kotlin.psi.psiUtil.endOffset
import org.jetbrains.kotlin.psi.psiUtil.startOffset

/**
 * Tokenizer traverses a Kotlin parse tree (which blessedly contains whitespaces and comments,
 * unlike Javac) and constructs a list of 'Tok's.
 *
 * <p>The google-java-format infra expects newline Toks to be separate from maximal-whitespace Toks,
 * but Kotlin emits them together. So, we split them using Java's \R regex matcher. We don't use
 * 'split' et al. because we want Toks for the newlines themselves.
 */
class Tokenizer(private val fileText: String, val file: KtFile) : KtTreeVisitorVoid() {

  companion object {
    private val WHITESPACE_NEWLINE_REGEX: Pattern = Pattern.compile("\\R|( )+")
  }

  val toks: MutableList<KotlinTok> = mutableListOf()
  var index: Int = 0
    private set

  override fun visitBinaryExpression(expression: KtBinaryExpression) {
    // In Kotlin 2.2.0+ this implementation in KtVisitor was changed to only visit
    // the inner KtStringTemplateExpression expressions, omitting the operator tokens
    // This overrides it to restore the previous impl and visit all parts of the expression,
    // which we need in order to populate expected toks correctly
    // See
    // https://github.com/JetBrains/kotlin/commit/f33566e0d7883ca3c56d2552cc4da0cbf6ad966a#diff-5693f3044b6ae4210db1194385fdb3dd8efd8751a49f8a72deaccc025ad8c48e
    return visitExpression(expression)
  }

  override fun visitElement(element: PsiElement) {
    // Do not materialize text/text ranges when it's non needed -- e.g. for KtFile, composite
    // PsiElement(...) etc.
    val elementText by lazy(LazyThreadSafetyMode.NONE) { element.text }
    val originalText by
        lazy(LazyThreadSafetyMode.NONE) {
          fileText.substring(element.startOffset, element.endOffset)
        }
    when (element) {
      is PsiComment -> {
        if (elementText.startsWith("/*") && !elementText.endsWith("*/")) {
          throw ParseError(
              "Unclosed comment",
              StringUtil.offsetToLineColumn(fileText, element.startOffset),
          )
        }
        // Treat block comments inside statement-less lambda bodies as tokens so the
        // visitor can position them with proper break structure. Without this, the
        // comment attaches as toksAfter of `{` and stays on the same line when the
        // lambda breaks.
        val isBlockComment = elementText.startsWith("/*")
        val parentBlock = element.parent as? KtBlockExpression
        val isInLambdaBody = parentBlock?.parent is KtFunctionLiteral
        val bodyHasNoStatements = parentBlock != null && parentBlock.children.isEmpty()
        val treatAsToken = isBlockComment && isInLambdaBody && bodyHasNoStatements
        toks.add(
            KotlinTok(
                index = index,
                originalText = originalText,
                text = elementText,
                position = element.startOffset,
                isToken = treatAsToken,
                kind = KtTokens.EOF,
            ),
        )
        index++
        return
      }
      is KtStringTemplateExpression -> {
        toks.add(
            KotlinTok(
                index = index,
                originalText =
                    WhitespaceTombstones.replaceTrailingWhitespaceWithTombstone(
                        originalText,
                    ),
                text = elementText,
                position = element.startOffset,
                isToken = true,
                kind = KtTokens.EOF,
            ),
        )
        index++
        return
      }
      is LeafPsiElement -> {
        if (element is PsiWhiteSpace) {
          val matcher = WHITESPACE_NEWLINE_REGEX.matcher(elementText)
          while (matcher.find()) {
            val text = matcher.group()
            toks.add(
                KotlinTok(
                    index = -1,
                    originalText =
                        fileText.substring(
                            element.startOffset + matcher.start(),
                            element.startOffset + matcher.end(),
                        ),
                    text = text,
                    position = element.startOffset + matcher.start(),
                    isToken = false,
                    kind = KtTokens.EOF,
                ),
            )
          }
        } else {
          toks.add(
              KotlinTok(
                  index = index,
                  originalText = originalText,
                  text = elementText,
                  position = element.startOffset,
                  isToken = true,
                  kind = KtTokens.EOF,
              ),
          )
          index++
        }
      }
    }
    super.visitElement(element)
  }
}
