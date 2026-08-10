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

import com.google.common.collect.DiscreteDomain
import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableMap
import com.google.common.collect.ImmutableRangeMap
import com.google.common.collect.Iterables.getLast
import com.google.common.collect.Range
import com.google.common.collect.RangeSet
import com.google.common.collect.TreeRangeSet
import com.google.googlejavaformat.Input
import com.google.googlejavaformat.Newlines
import com.google.googlejavaformat.java.FormatterException
import com.google.googlejavaformat.java.JavaOutput
import org.jetbrains.kotlin.com.intellij.openapi.util.text.StringUtil
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtFile

// TODO: share the code with JavaInput instead of copy-pasting here.
/**
 * KotlinInput is for Kotlin what JavaInput is for Java.
 *
 * <p>KotlinInput is duplicating most of JavaInput's code, but uses the Kotlin compiler as a lexer
 * instead of Javac. This is required because some valid Kotlin programs are not valid Java
 * programs, e.g., "a..b".
 *
 * <p>See javadoc for JavaInput.
 */
class KotlinInput(private val text: String, file: KtFile) : Input() {
  private val tokens: ImmutableList<Token> // The Tokens for this input.
  private val positionTokenMap: ImmutableRangeMap<Int, Token> // Map position to Token.
  private var kN = 0 // The number of numbered toks (tokens or comments), excluding the EOF.
  private val kToToken: Array<Token?>

  init {
    setLines(ImmutableList.copyOf(Newlines.lineIterator(text)))
    val toks = buildToks(file, text)
    tokens = buildTokens(toks)
    positionTokenMap = buildTokenPositionsMap(tokens)

    // adjust kN for EOF
    kToToken = arrayOfNulls(kN + 1)
    for (token in tokens) {
      for (tok in token.toksBefore) {
        if (tok.index < 0) {
          continue
        }
        kToToken[tok.index] = token
      }
      kToToken[token.tok.index] = token
      for (tok in token.toksAfter) {
        if (tok.index < 0) {
          continue
        }
        kToToken[tok.index] = token
      }
    }
  }

  @Throws(FormatterException::class)
  fun characterRangesToTokenRanges(characterRanges: Collection<Range<Int>>): RangeSet<Int> {
    val tokenRangeSet = TreeRangeSet.create<Int>()
    for (characterRange0 in characterRanges) {
      val characterRange = characterRange0.canonical(DiscreteDomain.integers())
      tokenRangeSet.add(
          characterRangeToTokenRange(
              characterRange.lowerEndpoint(),
              characterRange.upperEndpoint() - characterRange.lowerEndpoint(),
          ),
      )
    }
    return tokenRangeSet
  }

  /**
   * Convert from an offset and length flag pair to a token range.
   *
   * @param offset the `0`-based offset in characters
   * @param length the length in characters
   * @return the `0`-based [Range] of tokens
   * @throws FormatterException
   */
  @Throws(FormatterException::class)
  internal fun characterRangeToTokenRange(offset: Int, length: Int): Range<Int> {
    val requiredLength = offset + length
    if (requiredLength > text.length) {
      throw FormatterException(
          String.format(
              "error: invalid length %d, offset + length (%d) is outside the file",
              length,
              requiredLength,
          ),
      )
    }
    val expandedLength =
        when {
          length < 0 -> return EMPTY_RANGE
          length == 0 -> 1 // 0 stands for "format the line under the cursor"
          else -> length
        }
    val enclosed =
        getPositionTokenMap()
            .subRangeMap(Range.closedOpen(offset, offset + expandedLength))
            .asMapOfRanges()
            .values
    return if (enclosed.isEmpty()) {
      EMPTY_RANGE
    } else
        Range.closedOpen(
            enclosed.iterator().next().tok.index,
            getLast(enclosed).getTok().getIndex() + 1,
        )
  }

  private fun buildToks(file: KtFile, fileText: String): ImmutableList<KotlinTok> {
    val tokenizer = Tokenizer(fileText, file)
    file.accept(tokenizer)
    val toks = tokenizer.toks
    toks.add(KotlinTok(tokenizer.index, "", "", fileText.length, true, KtTokens.EOF))
    kN = tokenizer.index
    computeRanges(toks)
    return ImmutableList.copyOf(toks)
  }

  private fun buildTokens(toks: List<KotlinTok>): ImmutableList<Token> {
    val tokens = ImmutableList.builder<Token>()
    var k = 0
    val kN = toks.size

    // Remaining non-tokens before the token go here.
    var toksBefore: ImmutableList.Builder<KotlinTok> = ImmutableList.builder()

    OUTERMOST@ while (k < kN) {
      while (!toks[k].isToken) {
        val tok = toks[k++]
        toksBefore.add(tok)
        if (isParamComment(tok)) {
          while (toks[k].isNewline) {
            // drop newlines after parameter comments
            k++
          }
        }
      }
      val tok = toks[k++]

      // Non-tokens starting on the same line go here too.
      val toksAfter = ImmutableList.builder<KotlinTok>()
      OUTER@ while (k < kN && !toks[k].isToken) {
        // Don't attach inline comments to certain leading tokens, e.g. for `f(/*flag1=*/true).
        //
        // Attaching inline comments to the right token is hard, and this barely
        // scratches the surface. But it's enough to do a better job with parameter
        // name comments.
        //
        // TODO(cushon): find a better strategy.
        if (toks[k].isSlashStarComment && (tok.text == "(" || tok.text == "<" || tok.text == "."))
            break@OUTER
        if (toks[k].isJavadocComment && tok.text == ";") break@OUTER
        if (isParamComment(toks[k])) {
          tokens.add(KotlinToken(toksBefore.build(), tok, toksAfter.build()))
          toksBefore = ImmutableList.builder<KotlinTok>().add(toks[k++])
          // drop newlines after parameter comments
          while (toks[k].isNewline) {
            k++
          }
          continue@OUTERMOST
        }
        val nonTokenAfter = toks[k++]
        toksAfter.add(nonTokenAfter)
        if (Newlines.containsBreaks(nonTokenAfter.text)) {
          break
        }
      }
      tokens.add(KotlinToken(toksBefore.build(), tok, toksAfter.build()))
      toksBefore = ImmutableList.builder()
    }
    return tokens.build()
  }

  private fun buildTokenPositionsMap(tokens: ImmutableList<Token>): ImmutableRangeMap<Int, Token> {
    val tokenLocations = ImmutableRangeMap.builder<Int, Token>()
    for (token in tokens) {
      val end = JavaOutput.endTok(token)
      val endPosition = end.position + (if (end.text.isNotEmpty()) end.length() - 1 else 0)
      tokenLocations.put(Range.closed(JavaOutput.startTok(token).position, endPosition), token)
    }

    return tokenLocations.build()
  }

  private fun isParamComment(tok: Tok): Boolean {
    return tok.isSlashStarComment && tok.text.matches("/\\*[A-Za-z0-9\\s_\\-]+=\\s*\\*/".toRegex())
  }

  override fun getkN(): Int = kN

  override fun getToken(k: Int): Token? = kToToken[k]

  override fun getTokens(): ImmutableList<out Token> = tokens

  override fun getPositionTokenMap(): ImmutableRangeMap<Int, out Token> = positionTokenMap

  override fun getPositionToColumnMap(): ImmutableMap<Int, Int> =
      TODO("Only used by GJF's tabular alignment (JavaInputAstVisitor.argumentsAreTabular)")

  override fun getText(): String = text

  override fun getLineNumber(inputPosition: Int): Int =
      StringUtil.offsetToLineColumn(text, inputPosition).line + 1

  override fun getColumnNumber(inputPosition: Int): Int =
      StringUtil.offsetToLineColumn(text, inputPosition).column
}
