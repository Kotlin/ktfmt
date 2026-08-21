package org.jetbrains.ktfmt.format

import com.google.common.collect.Range
import com.google.common.collect.TreeRangeSet
import org.jetbrains.ktfmt.testutil.FormatterTestFactory
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

// core/src/test/resources/cases/format
class FormatTest : FormatterTestFactory() {
  @Test
  fun `preserve LF, CRLF and CR line endings`() {
    val lines = listOf("fun main() {", "  println(\"test\")", "}")
    for (ending in listOf("\n", "\r\n", "\r")) {
      val code = lines.joinToString(ending, postfix = ending)

      val reformatted = Formatter.format(DEFAULT_CASE_FORMAT, code)
      assertEquals(code, reformatted)
    }
  }

  @Test
  fun `format with lineRanges and characterRanges both null performs full formatting`() {
    val code = "fun foo ( ) = 1\nfun bar ( ) = 2\n"
    val formatted =
        Formatter.format(DEFAULT_CASE_FORMAT, code, lineRanges = null, characterRanges = null)
    assertEquals("fun foo() = 1\n\nfun bar() = 2\n", formatted)
  }

  @Test
  fun `format with lineRanges non-null and characterRanges null formats only specified lines`() {
    val code = "fun foo ( ) = 1\nfun bar ( ) = 2\n"
    val lineRanges = TreeRangeSet.create<Int>().apply { add(Range.closedOpen(0, 1)) }
    val formatted =
        Formatter.format(DEFAULT_CASE_FORMAT, code, lineRanges = lineRanges, characterRanges = null)
    assertEquals("fun foo() = 1\n\nfun bar ( ) = 2\n", formatted)
  }

  @Test
  fun `format with lineRanges null and characterRanges non-null formats only specified character ranges`() {
    val code = "fun foo ( ) = 1\nfun bar ( ) = 2\n"
    val barOffset = code.indexOf("fun bar")
    val characterRanges =
        TreeRangeSet.create<Int>().apply {
          add(Range.closedOpen(barOffset, barOffset + 15))
        }
    val formatted =
        Formatter.format(
            DEFAULT_CASE_FORMAT,
            code,
            lineRanges = null,
            characterRanges = characterRanges,
        )
    assertEquals("fun foo ( ) = 1\n\nfun bar() = 2\n", formatted)
  }

  @Test
  fun `format with both lineRanges and characterRanges non-null formats union of ranges`() {
    val code = "fun foo ( ) = 1\nfun bar ( ) = 2\nfun baz ( ) = 3\n"
    val lineRanges = TreeRangeSet.create<Int>().apply { add(Range.closedOpen(0, 1)) }
    val bazOffset = code.indexOf("fun baz")
    val characterRanges =
        TreeRangeSet.create<Int>().apply {
          add(Range.closedOpen(bazOffset, bazOffset + 15))
        }
    val formatted =
        Formatter.format(
            DEFAULT_CASE_FORMAT,
            code,
            lineRanges = lineRanges,
            characterRanges = characterRanges,
        )
    assertEquals("fun foo() = 1\n\nfun bar ( ) = 2\n\nfun baz() = 3\n", formatted)
  }

  @Test
  fun `format with empty lineRanges and characterRanges leaves code untouched but cleans imports and multiline strings`() {
    val code =
        """
        |val s = ""${'"'}
        |        hello
        |        ""${'"'}.trimIndent()
        |fun foo ( ) = 1
        |"""
            .trimMargin()
    val emptyRanges = TreeRangeSet.create<Int>()
    val formatted =
        Formatter.format(
            DEFAULT_CASE_FORMAT,
            code,
            lineRanges = emptyRanges,
            characterRanges = emptyRanges,
        )
    assertEquals(
        """
        |val s = ""${'"'}
        |hello
        |""${'"'}
        |    .trimIndent()
        |fun foo ( ) = 1
        |"""
            .trimMargin(),
        formatted,
    )
  }

  @Test
  fun `format with range entirely within shebang leaves kotlin code untouched and preserves shebang`() {
    val code = "#!/usr/bin/env kotlin\nfun foo ( ) = 1\n"
    val characterRanges = TreeRangeSet.create<Int>().apply { add(Range.closedOpen(0, 10)) }
    val formatted = Formatter.format(DEFAULT_CASE_FORMAT, code, characterRanges = characterRanges)
    assertEquals(code, formatted)
  }

  @Test
  fun `format with range crossing shebang boundary formats only kotlin code portion`() {
    val code = "#!/usr/bin/env kotlin\nfun foo ( ) = 1\nfun bar ( ) = 2\n"
    val characterRanges = TreeRangeSet.create<Int>().apply { add(Range.closedOpen(10, 35)) }
    val formatted = Formatter.format(DEFAULT_CASE_FORMAT, code, characterRanges = characterRanges)
    assertEquals("#!/usr/bin/env kotlin\nfun foo() = 1\n\nfun bar ( ) = 2\n", formatted)
  }

  @Test
  fun `format with range after shebang adjusts offsets and formats target code`() {
    val code = "#!/usr/bin/env kotlin\nfun foo ( ) = 1\nfun bar ( ) = 2\n"
    val barOffset = code.indexOf("fun bar")
    val characterRanges =
        TreeRangeSet.create<Int>().apply {
          add(Range.closedOpen(barOffset, barOffset + 15))
        }
    val formatted = Formatter.format(DEFAULT_CASE_FORMAT, code, characterRanges = characterRanges)
    assertEquals("#!/usr/bin/env kotlin\nfun foo ( ) = 1\n\nfun bar() = 2\n", formatted)
  }

  @Test
  fun `format with line range selecting line 0 on code with shebang leaves kotlin code untouched`() {
    val code = "#!/usr/bin/env kotlin\nfun foo ( ) = 1\n"
    val lineRanges = TreeRangeSet.create<Int>().apply { add(Range.closedOpen(0, 1)) }
    val formatted = Formatter.format(DEFAULT_CASE_FORMAT, code, lineRanges = lineRanges)
    assertEquals(code, formatted)
  }

  @Test
  fun `format with line range spanning line 0 and line 1 adjusts line index for shebang`() {
    val code = "#!/usr/bin/env kotlin\nfun foo ( ) = 1\nfun bar ( ) = 2\n"
    val lineRanges = TreeRangeSet.create<Int>().apply { add(Range.closedOpen(0, 2)) }
    val formatted = Formatter.format(DEFAULT_CASE_FORMAT, code, lineRanges = lineRanges)
    assertEquals("#!/usr/bin/env kotlin\nfun foo() = 1\n\nfun bar ( ) = 2\n", formatted)
  }

  @Test
  fun `format with empty string and character range returns empty string`() {
    val characterRanges = TreeRangeSet.create<Int>().apply { add(Range.closedOpen(0, 10)) }
    val formatted = Formatter.format(DEFAULT_CASE_FORMAT, "", characterRanges = characterRanges)
    assertEquals("", formatted)
  }

  @Test
  fun `format with shebang only and character range returns shebang`() {
    val code = "#!/bin/sh\n"
    val characterRanges = TreeRangeSet.create<Int>().apply { add(Range.closedOpen(0, 10)) }
    val formatted = Formatter.format(DEFAULT_CASE_FORMAT, code, characterRanges = characterRanges)
    assertEquals(code, formatted)
  }

  @Test
  fun `format with shebang and CRLF line endings preserves shebang and CRLF`() {
    val code = "#!/usr/bin/env kotlin\r\nfun foo ( ) = 1\r\nfun bar ( ) = 2\r\n"
    val fooOffset = code.indexOf("fun foo")
    val characterRanges =
        TreeRangeSet.create<Int>().apply {
          add(Range.closedOpen(fooOffset, fooOffset + 15))
        }
    val formatted = Formatter.format(DEFAULT_CASE_FORMAT, code, characterRanges = characterRanges)
    assertEquals("#!/usr/bin/env kotlin\r\nfun foo() = 1\r\n\r\nfun bar ( ) = 2\r\n", formatted)
  }

  @Test
  fun `format with character range preserves CRLF and CR line endings`() {
    for (ending in listOf("\r\n", "\r")) {
      val code = "fun foo ( ) = 1${ending}fun bar ( ) = 2${ending}"
      val fooOffset = code.indexOf("fun foo")
      val characterRanges =
          TreeRangeSet.create<Int>().apply {
            add(Range.closedOpen(fooOffset, fooOffset + 15))
          }
      val formatted = Formatter.format(DEFAULT_CASE_FORMAT, code, characterRanges = characterRanges)
      assertEquals("fun foo() = 1${ending}${ending}fun bar ( ) = 2${ending}", formatted)
    }
  }

  @Test
  fun `format overloads format code and handle unused imports option`() {
    val codeWithUnused = "import com.unused.Class\n\nfun foo ( ) = 1\n"
    assertEquals("fun foo() = 1\n", Formatter.format(codeWithUnused))
    assertEquals(
        "import com.unused.Class\n\nfun foo() = 1\n",
        Formatter.format(codeWithUnused, removeUnusedImports = false),
    )
  }

  @Test
  fun `format with out of bounds line ranges leaves code untouched`() {
    val code = "fun foo ( ) = 1\n"
    val lineRanges = TreeRangeSet.create<Int>().apply { add(Range.closedOpen(100, 200)) }
    val formatted = Formatter.format(DEFAULT_CASE_FORMAT, code, lineRanges = lineRanges)
    assertEquals(code, formatted)
  }

  @Test
  fun `format with out of bounds character ranges leaves code untouched`() {
    val code = "fun foo ( ) = 1\n"
    val characterRanges = TreeRangeSet.create<Int>().apply { add(Range.closedOpen(500, 600)) }
    val formatted = Formatter.format(DEFAULT_CASE_FORMAT, code, characterRanges = characterRanges)
    assertEquals(code, formatted)
  }

  @Test
  fun `format handles statement formatting inside a block`() {
    val code =
        """
        |fun test() {
        |  val untouched    =   1
        |  val selected     =   2
        |}
        |"""
            .trimMargin()
    val selectedOffset = code.indexOf("val selected")
    val characterRanges =
        TreeRangeSet.create<Int>().apply {
          add(Range.closedOpen(selectedOffset, selectedOffset + 15))
        }
    val formatted = Formatter.format(Formatter.META_FORMAT, code, characterRanges = characterRanges)
    assertEquals(
        """
        |fun test() {
        |  val untouched    =   1
        |  val selected = 2
        |}
        |"""
            .trimMargin(),
        formatted,
    )
  }
}
