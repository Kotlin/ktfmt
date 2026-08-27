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

import com.google.common.collect.Range
import com.google.common.collect.TreeRangeSet
import com.google.googlejavaformat.java.FormatterException
import org.jetbrains.ktfmt.testutil.assertContains
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class KotlinInputTest {
  @Test
  fun `Comments are toks not tokens`() {
    val code = "/** foo */ class F {}"
    val input = KotlinInput(code, Parser.parse(code))
    assertEquals(listOf("class", "F", "{", "}", ""), input.getTokens().map { it.tok.text })
    assertEquals(listOf("/** foo */", " "), input.getTokens()[0].toksBefore.map { it.text })
  }

  @Test
  fun `characterRangesToTokenRanges ignores empty and out-of-bounds ranges`() {
    val code = "class F {}"
    val input = KotlinInput(code, Parser.parse(code))
    val tokenRanges =
        input.characterRangesToTokenRanges(
            listOf(
                Range.closedOpen(2, 2),
                Range.closedOpen(100, 200),
                Range.closedOpen(0, 5),
            ),
        )
    assertEquals(
        TreeRangeSet.create<Int>().apply {
          add(Range.closedOpen(0, 1))
        },
        tokenRanges,
    )
  }

  @Test
  fun `characterRangesToTokenRanges returns empty range set for empty input`() {
    val code = "fun foo() = 1"
    val input = KotlinInput(code, Parser.parse(code))
    val tokenRanges = input.characterRangesToTokenRanges(emptyList())
    assertTrue(tokenRanges.isEmpty)
  }

  @Test
  fun `characterRangesToTokenRanges handles multiple disjoint ranges`() {
    val code = "fun foo() = 1\nfun bar() = 2"
    val input = KotlinInput(code, Parser.parse(code))
    val fooOffset = code.indexOf("foo")
    val barOffset = code.indexOf("bar")
    val tokenRanges =
        input.characterRangesToTokenRanges(
            listOf(
                Range.closedOpen(fooOffset, fooOffset + 3),
                Range.closedOpen(barOffset, barOffset + 3),
            ),
        )
    val ranges = tokenRanges.asRanges()
    assertEquals(2, ranges.size)
  }

  @Test
  fun `characterRangesToTokenRanges merges overlapping character ranges`() {
    val code = "fun testFunction() = 42"
    val input = KotlinInput(code, Parser.parse(code))
    val tokenRanges =
        input.characterRangesToTokenRanges(
            listOf(
                Range.closedOpen(0, 10),
                Range.closedOpen(5, 18),
            ),
        )
    assertEquals(1, tokenRanges.asRanges().size)
  }

  @Test
  fun `characterRangesToTokenRanges canonicalizes closed, open, and openClosed ranges`() {
    val code = "class FooBarBaz {}"
    val input = KotlinInput(code, Parser.parse(code))

    val fromClosedOpen = input.characterRangesToTokenRanges(listOf(Range.closedOpen(0, 5)))
    val fromClosed = input.characterRangesToTokenRanges(listOf(Range.closed(0, 4)))
    val fromOpenClosed = input.characterRangesToTokenRanges(listOf(Range.openClosed(0, 5)))

    assertEquals(fromClosedOpen, fromClosed)
    assertEquals(
        TreeRangeSet.create<Int>().apply {
          add(Range.closedOpen(0, 1))
        },
        fromClosedOpen,
    )
    assertEquals(
        TreeRangeSet.create<Int>().apply {
          add(Range.closedOpen(0, 1))
        },
        fromOpenClosed,
    )
  }

  @Test
  fun `characterRangesToTokenRanges clamps range that extends past EOF`() {
    val code = "class F {}"
    val input = KotlinInput(code, Parser.parse(code))
    val tokenRanges =
        input.characterRangesToTokenRanges(
            listOf(Range.closedOpen(0, 99999)),
        )
    assertFalse(tokenRanges.isEmpty)
  }

  @Test
  fun `characterRangesToTokenRanges ignores range starting at or after text length`() {
    val code = "val x = 1"
    val input = KotlinInput(code, Parser.parse(code))
    val tokenRanges =
        input.characterRangesToTokenRanges(
            listOf(
                Range.closedOpen(code.length, code.length + 5),
                Range.closedOpen(code.length + 10, code.length + 20),
            ),
        )
    assertTrue(tokenRanges.isEmpty)
  }

  @Test
  fun `characterRangeToTokenRange with length 0 expands to format line under cursor`() {
    val code = "val x = 1"
    val input = KotlinInput(code, Parser.parse(code))
    val tokenRange = input.characterRangeToTokenRange(0, 0)
    assertEquals(Range.closedOpen(0, 1), tokenRange)
  }

  @Test
  fun `characterRangeToTokenRange with negative length returns empty range`() {
    val code = "val x = 1"
    val input = KotlinInput(code, Parser.parse(code))
    val tokenRange = input.characterRangeToTokenRange(0, -1)
    assertTrue(tokenRange.isEmpty)
  }

  @Test
  fun `characterRangeToTokenRange throws FormatterException when range exceeds text length`() {
    val code = "val x = 1"
    val input = KotlinInput(code, Parser.parse(code))
    val e =
        assertThrows<FormatterException> {
          input.characterRangeToTokenRange(5, 100)
        }
    assertContains(e.message, "is outside the file")
  }

  @Test
  fun `characterRangeToTokenRange returns empty range when range falls entirely in whitespace without tokens`() {
    val code = "val   x = 1"
    val input = KotlinInput(code, Parser.parse(code))
    // Offset 4 is whitespace between 'val' and 'x'
    val tokenRange = input.characterRangeToTokenRange(4, 1)
    assertTrue(tokenRange.isEmpty)
  }

  @Test
  fun `KotlinInput inspection methods return correct metadata`() {
    val code = "package test\n\nval x = 1\n"
    val input = KotlinInput(code, Parser.parse(code))

    assertEquals(code, input.getText())
    assertTrue(input.getkN() > 0)
    assertNotNull(input.getToken(0))
    assertTrue(input.getTokens().isNotEmpty())
    assertNotNull(input.getPositionTokenMap())

    assertEquals(1, input.getLineNumber(0))
    assertEquals(0, input.getColumnNumber(0))
    val line2Pos = code.indexOf("val")
    assertEquals(3, input.getLineNumber(line2Pos))
    assertEquals(0, input.getColumnNumber(line2Pos))
  }

  @Test
  fun `KotlinInput handles parameter comments and inline comments`() {
    val code = "fun test(/*flag=*/ enabled: Boolean) {}"
    val input = KotlinInput(code, Parser.parse(code))
    val tokens = input.getTokens()
    assertTrue(tokens.any { it.tok.text == "enabled" })
  }
}
