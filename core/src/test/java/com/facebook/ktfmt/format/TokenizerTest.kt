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

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class TokenizerTest {
  @Test
  fun `PsiWhiteSpace are split to newlines and maximal-length whitespaces`() {
    val code = listOf(
        "val  a = ", //
        "", //
        "     ", //
        "     15",
    )
        .joinToString("\n")

    val file = Parser.parse(code)
    val tokenizer = Tokenizer(code, file)
    file.accept(tokenizer)

    assertEquals(
        listOf("val", "  ", "a", " ", "=", " ", "\n", "\n", "     ", "\n", "     ", "15"),
        tokenizer.toks.map { it.originalText },
    )
  }

  @Test
  fun `Strings are returns as a single token`() {
    val code = listOf(
        "val a=\"\"\"",
        "  ",
        "   ",
        "    Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do ",
        "    Lorem",
        "    ",
        "     ",
        "      \"\"\"",
        "val b=\"lorem ipsum\"",
        "      ",
        "    ",
    )
        .joinToString("\n")

    val file = Parser.parse(code)
    val tokenizer = Tokenizer(code, file)
    file.accept(tokenizer)

    assertEquals(
        listOf(
            "val",
            " ",
            "a",
            "=",
            listOf(
                "\"\"\"",
                " ${WhitespaceTombstones.SPACE_TOMBSTONE}",
                "  ${WhitespaceTombstones.SPACE_TOMBSTONE}",
                "    Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do${WhitespaceTombstones.SPACE_TOMBSTONE}",
                "    Lorem",
                "   ${WhitespaceTombstones.SPACE_TOMBSTONE}",
                "    ${WhitespaceTombstones.SPACE_TOMBSTONE}",
                "      \"\"\"",
            )
                .joinToString("\n"),
            "\n",
            "val",
            " ",
            "b",
            "=",
            "\"lorem ipsum\"",
            "\n",
            "      ",
            "\n",
            "    ",
        ),
        tokenizer.toks.map { it.originalText },
    )
  }

  @Test
  fun `Token index is advanced after a string token`() {
    val code =
        """
        |val b="a"
        |val a=5
        |"""
            .trimMargin()

    val file = Parser.parse(code)
    val tokenizer = Tokenizer(code, file)
    file.accept(tokenizer)

    assertEquals(
        listOf("val", " ", "b", "=", "\"a\"", "\n", "val", " ", "a", "=", "5", "\n"),
        tokenizer.toks.map { it.originalText },
    )
    assertEquals(listOf(0, -1, 1, 2, 3, -1, 4, -1, 5, 6, 7, -1), tokenizer.toks.map { it.index })
  }

  @Test
  fun `Context receivers are parsed correctly`() {
    val code =
        """
        |context(Something)
        |class A {
        |  context(
        |  // Test comment.
        |  Logger, Raise<Error>)
        |  fun test() {}
        |}
        |"""
            .trimMargin()

    val file = Parser.parse(code)
    val tokenizer = Tokenizer(code, file)
    file.accept(tokenizer)

    assertEquals(
        listOf(
            "context",
            "(",
            "Something",
            ")",
            "\n",
            "class",
            " ",
            "A",
            " ",
            "{",
            "\n",
            "  ",
            "context",
            "(",
            "\n",
            "  ",
            "// Test comment.",
            "\n",
            "  ",
            "Logger",
            ",",
            " ",
            "Raise",
            "<",
            "Error",
            ">",
            ")",
            "\n",
            "  ",
            "fun",
            " ",
            "test",
            "(",
            ")",
            " ",
            "{",
            "}",
            "\n",
            "}",
            "\n",
        ),
        tokenizer.toks.map { it.originalText },
    )
    assertEquals(
        listOf(
            0,
            1,
            2,
            3,
            -1,
            4,
            -1,
            5,
            -1,
            6,
            -1,
            -1,
            7,
            8,
            -1,
            -1,
            9,
            -1,
            -1,
            10,
            11,
            -1,
            12,
            13,
            14,
            15,
            16,
            -1,
            -1,
            17,
            -1,
            18,
            19,
            20,
            -1,
            21,
            22,
            -1,
            23,
            -1,
        ),
        tokenizer.toks.map { it.index },
    )
  }

  @Test
  fun `Guard conditions with subject are parsed correctly`() {
    // language=kotlin
    val code =
        """
        |fun feedAnimal(animal: Animal) {
        |    when (animal) {
        |        is Animal.Cat if !animal.mouseHunter -> animal.feedCat()
        |        else if animal.eatsPlants -> animal.giveLettuce()
        |    }
        |}
        |"""
            .trimMargin()

    val file = Parser.parse(code)
    val tokenizer = Tokenizer(code, file)
    file.accept(tokenizer)

    assertEquals(
        listOf(
            "fun",
            " ",
            "feedAnimal",
            "(",
            "animal",
            ":",
            " ",
            "Animal",
            ")",
            " ",
            "{",
            "\n",
            "    ",
            "when",
            " ",
            "(",
            "animal",
            ")",
            " ",
            "{",
            "\n",
            "        ",
            "is",
            " ",
            "Animal",
            ".",
            "Cat",
            " ",
            "if",
            " ",
            "!",
            "animal",
            ".",
            "mouseHunter",
            " ",
            "->",
            " ",
            "animal",
            ".",
            "feedCat",
            "(",
            ")",
            "\n",
            "        ",
            "else",
            " ",
            "if",
            " ",
            "animal",
            ".",
            "eatsPlants",
            " ",
            "->",
            " ",
            "animal",
            ".",
            "giveLettuce",
            "(",
            ")",
            "\n",
            "    ",
            "}",
            "\n",
            "}",
            "\n",
        ),
        tokenizer.toks.map { it.originalText },
    )
    assertEquals(
        listOf(
            0,
            -1,
            1,
            2,
            3,
            4,
            -1,
            5,
            6,
            -1,
            7,
            -1,
            -1,
            8,
            -1,
            9,
            10,
            11,
            -1,
            12,
            -1,
            -1,
            13,
            -1,
            14,
            15,
            16,
            -1,
            17,
            -1,
            18,
            19,
            20,
            21,
            -1,
            22,
            -1,
            23,
            24,
            25,
            26,
            27,
            -1,
            -1,
            28,
            -1,
            29,
            -1,
            30,
            31,
            32,
            -1,
            33,
            -1,
            34,
            35,
            36,
            37,
            38,
            -1,
            -1,
            39,
            -1,
            40,
            -1,
        ),
        tokenizer.toks.map { it.index },
    )
  }

  @Test
  fun `Long binary expressions are parsed correctly`() {
    // language=kotlin
    val code =
        """
        |//////////////////////////////////////
        |fun foo() {
        |  val sentence =
        |      "The" +
        |          "quick" +
        |          ("brown" + "fox") +
        |          "jumps" +
        |          "over" +
        |          "the" +
        |          "lazy" +
        |          "dog"
        |}
        |"""
            .trimMargin()

    val file = Parser.parse(code)
    val tokenizer = Tokenizer(code, file)
    file.accept(tokenizer)

    assertEquals(
        listOf(
            "//////////////////////////////////////",
            "\n",
            "fun",
            " ",
            "foo",
            "(",
            ")",
            " ",
            "{",
            "\n",
            "  ",
            "val",
            " ",
            "sentence",
            " ",
            "=",
            "\n",
            "      ",
            "\"The\"",
            " ",
            "+",
            "\n",
            "          ",
            "\"quick\"",
            " ",
            "+",
            "\n",
            "          ",
            "(",
            "\"brown\"",
            " ",
            "+",
            " ",
            "\"fox\"",
            ")",
            " ",
            "+",
            "\n",
            "          ",
            "\"jumps\"",
            " ",
            "+",
            "\n",
            "          ",
            "\"over\"",
            " ",
            "+",
            "\n",
            "          ",
            "\"the\"",
            " ",
            "+",
            "\n",
            "          ",
            "\"lazy\"",
            " ",
            "+",
            "\n",
            "          ",
            "\"dog\"",
            "\n",
            "}",
            "\n",
        ),
        tokenizer.toks.map { it.originalText },
    )
    assertEquals(
        listOf(
            0,
            -1,
            1,
            -1,
            2,
            3,
            4,
            -1,
            5,
            -1,
            -1,
            6,
            -1,
            7,
            -1,
            8,
            -1,
            -1,
            9,
            -1,
            10,
            -1,
            -1,
            11,
            -1,
            12,
            -1,
            -1,
            13,
            14,
            -1,
            15,
            -1,
            16,
            17,
            -1,
            18,
            -1,
            -1,
            19,
            -1,
            20,
            -1,
            -1,
            21,
            -1,
            22,
            -1,
            -1,
            23,
            -1,
            24,
            -1,
            -1,
            25,
            -1,
            26,
            -1,
            -1,
            27,
            -1,
            28,
            -1,
        ),
        tokenizer.toks.map { it.index },
    )
  }

  @Test
  fun `Context parameters are parsed correctly`() {
    if (KotlinVersion.CURRENT < KotlinVersion(2, 2)) return
    // language=kotlin
    val code =
        """
        |context(something: Something)
        |class A {
        |  context(
        |  // Test comment.
        |  logger: Logger, raise: Raise<Error>, _: Ignored)
        |  fun test() {}
        |}
        |"""
            .trimMargin()

    val file = Parser.parse(code)
    val tokenizer = Tokenizer(code, file)
    file.accept(tokenizer)

    assertEquals(
        listOf(
            "context",
            "(",
            "something",
            ":",
            " ",
            "Something",
            ")",
            "\n",
            "class",
            " ",
            "A",
            " ",
            "{",
            "\n",
            "  ",
            "context",
            "(",
            "\n",
            "  ",
            "// Test comment.",
            "\n",
            "  ",
            "logger",
            ":",
            " ",
            "Logger",
            ",",
            " ",
            "raise",
            ":",
            " ",
            "Raise",
            "<",
            "Error",
            ">",
            ",",
            " ",
            "_",
            ":",
            " ",
            "Ignored",
            ")",
            "\n",
            "  ",
            "fun",
            " ",
            "test",
            "(",
            ")",
            " ",
            "{",
            "}",
            "\n",
            "}",
            "\n",
        ),
        tokenizer.toks.map { it.originalText },
    )
    assertEquals(
        listOf(
            0,
            1,
            2,
            3,
            -1,
            4,
            5,
            -1,
            6,
            -1,
            7,
            -1,
            8,
            -1,
            -1,
            9,
            10,
            -1,
            -1,
            11,
            -1,
            -1,
            12,
            13,
            -1,
            14,
            15,
            -1,
            16,
            17,
            -1,
            18,
            19,
            20,
            21,
            22,
            -1,
            23,
            24,
            -1,
            25,
            26,
            -1,
            -1,
            27,
            -1,
            28,
            29,
            30,
            -1,
            31,
            32,
            -1,
            33,
            -1,
        ),
        tokenizer.toks.map { it.index },
    )
  }

  @Test
  fun `Unclosed comment obvious`() {
    assertParseError(
        """
        |package a.b
        |/*
        |class A {}
        |"""
            .trimMargin(),
        "2:1: error: Unclosed comment",
    )
  }

  @Test
  fun `Unclosed comment too short`() {
    assertParseError(
        """
        |package a.b
        |/*/
        |class A {}
        |"""
            .trimMargin(),
        "2:1: error: Unclosed comment",
    )
  }

  @Test
  fun `Unclosed comment nested`() {
    assertParseError(
        """
        |package a.b
        |/* /* */
        |class A {}
        |"""
            .trimMargin(),
        "2:1: error: Unclosed comment",
    )
  }

  @Test
  fun `Unclosed comment nested EOF`() {
    // TODO: https://youtrack.jetbrains.com/issue/KT-72887 - This should be an error.
    assertParseError(
        """
        |package a.b
        |class A {}
        |/* /* */
        """
            .trimMargin(),
        null,
    )
  }

  private fun assertParseError(code: String, message: String?) {
    val file = Parser.parse(code)
    val tokenizer = Tokenizer(code, file)
    if (message == null) {
      file.accept(tokenizer)
    } else {
      val e = assertThrows<ParseError> { file.accept(tokenizer) }
      assertEquals(message, e.message)
    }
  }
}
