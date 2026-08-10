/*
 * Copyright (c) Tor Norbye.
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

package org.jetbrains.ktfmt.kdoc

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class UtilitiesTest {
  @Test
  fun testFindSamePosition() {
    fun check(newWithCaret: String, oldWithCaret: String) {
      val oldCaretIndex = oldWithCaret.indexOf('|')
      val newCaretIndex = newWithCaret.indexOf('|')
      assertTrue(oldCaretIndex != -1)
      assertTrue(newCaretIndex != -1)
      val old = oldWithCaret.substring(0, oldCaretIndex) + oldWithCaret.substring(oldCaretIndex + 1)
      val new = newWithCaret.substring(0, newCaretIndex) + newWithCaret.substring(newCaretIndex + 1)
      val newPos = findSamePosition(old, oldCaretIndex, new)

      val actual = new.substring(0, newPos) + "|" + new.substring(newPos)
      assertEquals(newWithCaret, actual)
    }

    // Prefix match
    check("|/** Test\n Different Middle End */", "|/** Test2 End */")
    check("/|** Test\n Different Middle End */", "/|** Test2 End */")
    check("/*|* Test\n Different Middle End */", "/*|* Test2 End */")
    check("/**| Test\n Different Middle End */", "/**| Test2 End */")
    check("/** |Test\n Different Middle End */", "/** |Test2 End */")
    check("/** T|est\n Different Middle End */", "/** T|est2 End */")
    check("/** Te|st\n Different Middle End */", "/** Te|st2 End */")
    check("/** Tes|t\n Different Middle End */", "/** Tes|t2 End */")
    check("/** Test|\n Different Middle End */", "/** Test|2 End */")
    // End match
    check("/** Test\n Different Middle| End */", "/** Test2| End */")
    check("/** Test\n Different Middle E|nd */", "/** Test2 E|nd */")
    check("/** Test\n Different Middle En|d */", "/** Test2 En|d */")
    check("/** Test\n Different Middle End| */", "/** Test2 End| */")
    check("/** Test\n Different Middle End |*/", "/** Test2 End |*/")
    check("/** Test\n Different Middle End *|/", "/** Test2 End *|/")
    check("/** Test\n Different Middle End */|", "/** Test2 End */|")

    check("|/**\nTest End\n*/", "|/** Test End */")
    check("/|**\nTest End\n*/", "/|** Test End */")
    check("/*|*\nTest End\n*/", "/*|* Test End */")
    check("/**|\nTest End\n*/", "/**| Test End */")
    check("/**\n|Test End\n*/", "/** |Test End */")
    check("/**\nT|est End\n*/", "/** T|est End */")
    check("/**\nTe|st End\n*/", "/** Te|st End */")
    check("/**\nTes|t End\n*/", "/** Tes|t End */")
    check("/**\nTest| End\n*/", "/** Test| End */")
    check("/**\nTest |End\n*/", "/** Test |End */")
    check("/**\nTest E|nd\n*/", "/** Test E|nd */")
    check("/**\nTest En|d\n*/", "/** Test En|d */")
    check("/**\nTest End|\n*/", "/** Test End| */")
    check("/**\nTest End\n|*/", "/** Test End |*/")
    check("/**\nTest End\n*|/", "/** Test End *|/")
    check("/**\nTest End\n*/|", "/** Test End */|")

    check("|/** Test End */", "|/** Test2 End */")
    check("/|** Test End */", "/|** Test2 End */")
    check("/*|* Test End */", "/*|* Test2 End */")
    check("/**| Test End */", "/**| Test2 End */")
    check("/** |Test End */", "/** |Test2 End */")
    check("/** T|est End */", "/** T|est2 End */")
    check("/** Te|st End */", "/** Te|st2 End */")
    check("/** Tes|t End */", "/** Tes|t2 End */")
    check("/** Test| End */", "/** Test|2 End */")
    check("/** Test |End */", "/** Test2 |End */")
    check("/** Test E|nd */", "/** Test2 E|nd */")
    check("/** Test En|d */", "/** Test2 En|d */")
    check("/** Test End| */", "/** Test2 End| */")
    check("/** Test End |*/", "/** Test2 End |*/")
    check("/** Test End *|/", "/** Test2 End *|/")
    check("/** Test End */|", "/** Test2 End */|")
  }

  @Test
  fun testGetParamName() {
    assertEquals("foo", "@param foo".getParamName())
    assertEquals("foo", "@param foo bar".getParamName())
    assertEquals("foo", "@param foo;".getParamName())
    assertEquals("foo", "  \t@param\t   foo  bar.".getParamName())
    assertEquals("foo", "@param[foo]".getParamName())
    assertEquals("foo", "@param  [foo]".getParamName())
    assertNull("@param ".getParamName())
    assertEquals("foo", "@property foo".getParamName())
  }

  @Test
  fun testComputeWords() {
    fun List<String>.describe(): String {
      return "listOf(${this.joinToString(", ") { "\"$it\"" }})"
    }
    fun check(text: String, expected: List<String>, customizeParagraph: (Paragraph) -> Unit = {}) {
      val task = FormattingTask(KDocFormattingOptions(12), "/** $text */", "")
      val paragraph = Paragraph(task)
      paragraph.content.append(text)
      customizeParagraph(paragraph)
      val words = paragraph.computeWords()

      assertEquals(expected.describe(), words.describe())
    }
    check("Foo", listOf("Foo"))
    check("Foo Bar Baz", listOf("Foo", "Bar", "Baz"))
    check("Foo Bar Baz", listOf("Foo Bar", "Baz")) { it.quoted = 1 }
    check("Foo Bar Baz", listOf("Foo Bar", "Baz")) { it.hanging = true }
    check("1. Foo", listOf("1.", "Foo"))
    // "1." can't start a word; if it ends up at the beginning of a line it becomes
    // a numbered element.
    check("Foo 1.", listOf("Foo 1."))
    check("Foo bar [Link Text] foo bar.", listOf("Foo", "bar", "[Link Text]", "foo", "bar."))
    check("Interval [0, 1) foo bar.", listOf("Interval [0, 1)", "foo", "bar."))

    // ">" cannot start a word; it would become quoted text
    check("if >= 3", listOf("if >=", "3"))
    check("if >= 3.", listOf("if >= 3."))

    check(
        "SDK version - [`Partial(Mode.UseIfAvailable)`](Partial) on API 24+",
        listOf("SDK", "version - [`Partial(Mode.UseIfAvailable)`](Partial)", "on", "API", "24+"),
    )

    check(
        "Z orders can range from Integer.MIN_VALUE to Integer.MAX_VALUE. Default z order " +
            " index is 0. [SurfaceControlWrapper] instances are positioned back-to-front.",
        listOf(
            "Z",
            "orders",
            "can",
            "range",
            "from",
            "Integer.MIN_VALUE",
            "to",
            "Integer.MAX_VALUE.",
            "Default",
            "z",
            "order",
            "index",
            "is 0. [SurfaceControlWrapper]",
            "instances",
            "are",
            "positioned",
            "back-to-front.",
        ),
    )
    check(
        "Equates to `cmd package compile -f -m speed <package>` on API 24+.",
        listOf(
            "Equates",
            "to",
            "`cmd",
            "package",
            "compile",
            "-f",
            "-m",
            "speed",
            "<package>`",
            "on",
            "API",
            "24+.",
        ),
    )
  }
}
