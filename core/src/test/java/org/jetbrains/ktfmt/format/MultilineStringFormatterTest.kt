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

import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MultilineStringFormatterTest {
  private val TQ = "\"\"\""

  @Test
  fun `MultilineTrimmedString validate basic properties`() {
    with(
        multilineTrimmedStringFromLines(
            TQ,
            "    |line1",
            "    |line2",
            "    $TQ",
            "        .trimMargin()",
        ),
    ) {
      assertTrue(usesTrimMargin)
      assertEquals("|", indentationSuffix)
      assertFalse(isDollarString)
      assertEquals(0, indentCount)
      assertEquals(5, lines.size)
      assertEquals(
          listOf(
              TQ,
              "    |line1",
              "    |line2",
              "    $TQ",
              "        .trimMargin()",
          ),
          lines,
      )
      assertEquals(0, lineStart)
      assertEquals(4, lineEnd)
      assertEquals(3, lastStringLineIndex)
      assertEquals(0, openStringOffset)
      assertEquals(42, trimMethodCallOffset)
      assertFalse(isNestedMultiline)
    }

    with(
        multilineTrimmedStringFromLines(
            "val x =",
            "  $$$TQ",
            "    line1 |",
            "    |line2",
            "    $TQ.trimIndent()",
        ),
    ) {
      assertFalse(usesTrimMargin)
      assertEquals("", indentationSuffix)
      assertTrue(isDollarString)
      assertEquals(2, indentCount)
      assertEquals(4, lines.size)
      assertEquals(
          listOf(
              "  $$$TQ",
              "    line1 |",
              "    |line2",
              "    $TQ.trimIndent()",
          ),
          lines,
      )
      assertEquals(1, lineStart)
      assertEquals(4, lineEnd)
      assertEquals(3, lastStringLineIndex)
      assertEquals(10, openStringOffset)
      assertEquals(46, trimMethodCallOffset)
      assertFalse(isNestedMultiline)
    }
  }

  @Test
  fun `MultilineTrimmedString minimalIndent calculation`() {
    val string = multilineTrimmedStringFromLines(
        " $TQ  ", // whitespace after opening quotes (should be ignored)
        "    line1", // 4 spaces
        " ", // blank line (should be ignored)
        "      line2", // 6 spaces
        "  line3", // 2 spaces (minimal)
        " $TQ.trimIndent()", // blank final line (should be ignored)
    )

    assertEquals(2, string.minimalIndent)
  }

  @Test
  fun `MultilineTrimmedString hasTemplateExpression`() {
    // simple string without template expression
    assertFalse(
        multilineTrimmedStringFromLines(
            TQ,
            "    line1",
            "    line2",
            "    $TQ.trimIndent()",
        )
            .hasTemplateExpression(),
    )

    // dollar string without dollar template expression
    assertFalse(
        multilineTrimmedStringFromLines(
            "$$$TQ",
            "    line1 \${variable}",
            "    line2",
            "    $TQ.trimIndent()",
        )
            .hasTemplateExpression(),
    )

    // simple string with template expression
    assertTrue(
        multilineTrimmedStringFromLines(
            TQ,
            "    line1 \${variable}",
            "    line2",
            "    $TQ.trimIndent()",
        )
            .hasTemplateExpression(),
    )

    // dollar string with template expression
    assertTrue(
        multilineTrimmedStringFromLines(
            "$$$TQ",
            "    line1 $$\${variable}",
            "    line2",
            "    $TQ.trimIndent()",
        )
            .hasTemplateExpression(),
    )

    // simple string with multiline template expression
    assertTrue(
        multilineTrimmedStringFromLines(
            TQ,
            "    line1",
            "    $$\${",
            "      if (condition) variable else $TQ hello $TQ",
            "    }",
            "    line2",
            "    $TQ.trimIndent()",
        )
            .hasTemplateExpression(),
    )

    // dollar string with multiline template expression
    assertTrue(
        multilineTrimmedStringFromLines(
            "$$$TQ",
            "    line1",
            "    $$\${",
            "      if (condition) variable else \"\"",
            "    }",
            "    line2",
            "    $TQ.trimIndent()",
        )
            .hasTemplateExpression(),
    )
  }

  @Test
  fun `getStringContent handles trimMargin with and without pipe prefix`() {
    assertEquals(
        listOf(
            "line1",
            "line2",
            "line3",
        ),
        multilineTrimmedStringFromLines(
            "$TQ  ",
            "    |line1",
            "    |line2",
            "    |line3",
            "    $TQ.trimMargin()",
        )
            .getStringContent(),
    )

    assertEquals(
        listOf(
            "    line1",
            "    line2",
            "    line3",
            "",
        ),
        multilineTrimmedStringFromLines(
            TQ,
            "    line1",
            "    line2",
            "    line3",
            "    |$TQ.trimMargin()",
        )
            .getStringContent(),
    )
  }

  @Test
  fun `getStringContent handles trimIndent`() {
    assertEquals(
        listOf(
            "line1",
            "  line2",
            "line3",
            "",
        ),
        multilineTrimmedStringFromLines(
            "$TQ ",
            "    line1",
            "      line2", // 6 spaces
            "    line3", // 4 spaces
            "",
            "    $TQ.trimIndent()",
        )
            .getStringContent(),
    )

    assertEquals(
        listOf(
            "line1",
            "  line2",
            "line3",
            "",
        ),
        multilineTrimmedStringFromLines(
            "$TQ ",
            "    line1",
            "      line2", // 6 spaces
            "    line3", // 4 spaces
            "",
            "    $TQ",
            "    .trimIndent()",
        )
            .getStringContent(),
    )
  }

  @Test
  fun `getStringContent includes non-blank first line content`() {
    assertEquals(
        listOf(
            "content",
            "line1",
            "line2",
        ),
        multilineTrimmedStringFromLines(
            "${TQ}content",
            "    |line1",
            "  |line2",
            "    $TQ",
            "        .trimMargin()",
        )
            .getStringContent(),
    )

    assertEquals(
        listOf(
            "content",
            "line1",
            "line2",
        ),
        multilineTrimmedStringFromLines(
            "$TQ    content",
            "    line1",
            "    line2",
            "    $TQ.trimIndent()",
        )
            .getStringContent(),
    )
  }

  private fun multilineTrimmedStringFrom(
      @Language("kts") code: String,
      continuationIndent: Int = 4,
  ): MultilineTrimmedString {
    val strings =
        MultilineStringFormatter(continuationIndent)
            .getMultilineTrimmedStringList(Parser.parse(code))
    assertEquals(1, strings.size)
    return strings.first()
  }

  private fun multilineTrimmedStringFromLines(
      vararg lines: String,
      continuationIndent: Int = 4,
  ): MultilineTrimmedString =
      multilineTrimmedStringFrom(lines.joinToString("\n"), continuationIndent)
}
