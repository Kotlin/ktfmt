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

package com.facebook.ktfmt.format

import com.google.common.truth.Truth.assertThat
import org.intellij.lang.annotations.Language
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
      assertThat(usesTrimMargin).isTrue()
      assertThat(indentationSuffix).isEqualTo("|")
      assertThat(isDollarString).isFalse()
      assertThat(indentCount).isEqualTo(0)
      assertThat(lines).hasSize(5)
      assertThat(lines)
          .containsExactly(
              TQ,
              "    |line1",
              "    |line2",
              "    $TQ",
              "        .trimMargin()",
          )
          .inOrder()
      assertThat(lineStart).isEqualTo(0)
      assertThat(lineEnd).isEqualTo(4)
      assertThat(lastStringLineIndex).isEqualTo(3)
      assertThat(openStringOffset).isEqualTo(0)
      assertThat(trimMethodCallOffset).isEqualTo(42)
      assertThat(isNestedMultiline).isFalse()
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
      assertThat(usesTrimMargin).isFalse()
      assertThat(indentationSuffix).isEqualTo("")
      assertThat(isDollarString).isTrue()
      assertThat(indentCount).isEqualTo(2)
      assertThat(lines).hasSize(4)
      assertThat(lines)
          .containsExactly(
              "  $$$TQ",
              "    line1 |",
              "    |line2",
              "    $TQ.trimIndent()",
          )
          .inOrder()
      assertThat(lineStart).isEqualTo(1)
      assertThat(lineEnd).isEqualTo(4)
      assertThat(lastStringLineIndex).isEqualTo(3)
      assertThat(openStringOffset).isEqualTo(10)
      assertThat(trimMethodCallOffset).isEqualTo(46)
      assertThat(isNestedMultiline).isFalse()
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

    assertThat(string.minimalIndent).isEqualTo(2)
  }

  @Test
  fun `MultilineTrimmedString hasTemplateExpression`() {
    // simple string without template expression
    assertThat(
        multilineTrimmedStringFromLines(
            TQ,
            "    line1",
            "    line2",
            "    $TQ.trimIndent()",
        )
            .hasTemplateExpression(),
    )
        .isFalse()

    // dollar string without dollar template expression
    assertThat(
        multilineTrimmedStringFromLines(
            "$$$TQ",
            "    line1 \${variable}",
            "    line2",
            "    $TQ.trimIndent()",
        )
            .hasTemplateExpression(),
    )
        .isFalse()

    // simple string with template expression
    assertThat(
        multilineTrimmedStringFromLines(
            TQ,
            "    line1 \${variable}",
            "    line2",
            "    $TQ.trimIndent()",
        )
            .hasTemplateExpression(),
    )
        .isTrue()

    // dollar string with template expression
    assertThat(
        multilineTrimmedStringFromLines(
            "$$$TQ",
            "    line1 $$\${variable}",
            "    line2",
            "    $TQ.trimIndent()",
        )
            .hasTemplateExpression(),
    )
        .isTrue()

    // simple string with multiline template expression
    assertThat(
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
        .isTrue()

    // dollar string with multiline template expression
    assertThat(
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
        .isTrue()
  }

  @Test
  fun `getStringContent handles trimMargin with and without pipe prefix`() {
    assertThat(
        multilineTrimmedStringFromLines(
            "$TQ  ",
            "    |line1",
            "    |line2",
            "    |line3",
            "    $TQ.trimMargin()",
        )
            .getStringContent(),
    )
        .containsExactly(
            "line1",
            "line2",
            "line3",
        )
        .inOrder()

    assertThat(
        multilineTrimmedStringFromLines(
            TQ,
            "    line1",
            "    line2",
            "    line3",
            "    |$TQ.trimMargin()",
        )
            .getStringContent(),
    )
        .containsExactly(
            "    line1",
            "    line2",
            "    line3",
            "",
        )
        .inOrder()
  }

  @Test
  fun `getStringContent handles trimIndent`() {
    assertThat(
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
        .containsExactly(
            "line1",
            "  line2",
            "line3",
            "",
        )
        .inOrder()

    assertThat(
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
        .containsExactly(
            "line1",
            "  line2",
            "line3",
            "",
        )
        .inOrder()
  }

  @Test
  fun `getStringContent includes non-blank first line content`() {
    assertThat(
        multilineTrimmedStringFromLines(
            "${TQ}content",
            "    |line1",
            "  |line2",
            "    $TQ",
            "        .trimMargin()",
        )
            .getStringContent(),
    )
        .containsExactly(
            "content",
            "line1",
            "line2",
        )
        .inOrder()

    assertThat(
        multilineTrimmedStringFromLines(
            "$TQ    content",
            "    line1",
            "    line2",
            "    $TQ.trimIndent()",
        )
            .getStringContent(),
    )
        .containsExactly(
            "content",
            "line1",
            "line2",
        )
        .inOrder()
  }

  private fun multilineTrimmedStringFrom(
      @Language("kts") code: String,
      continuationIndent: Int = 4,
  ): MultilineTrimmedString {
    val strings =
        MultilineStringFormatter(continuationIndent)
            .getMultilineTrimmedStringList(Parser.parse(code))
    assertThat(strings.size).isEqualTo(1)
    return strings.first()
  }

  private fun multilineTrimmedStringFromLines(
      vararg lines: String,
      continuationIndent: Int = 4,
  ): MultilineTrimmedString =
      multilineTrimmedStringFrom(lines.joinToString("\n"), continuationIndent)
}
