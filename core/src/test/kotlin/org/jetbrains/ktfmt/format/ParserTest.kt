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

import org.jetbrains.ktfmt.testutil.assertContains
import org.jetbrains.ktfmt.testutil.assertContainsMatch
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Test

class ParserTest {
  /**
   * [Parser.env] is initialized lazily, so the `idea.use.native.fs.for.win` property (which
   * suppresses a noisy IntelliJ filesystem warning on Windows) is now set on first parse rather
   * than at class load. This guards that the property is still applied before the environment is
   * built, regardless of platform, so the Windows code path keeps working.
   */
  @Test
  fun `parsing sets idea_use_native_fs_for_win to false`() {
    Parser.parse(KotlinCode("val a = 1", fileType = FileType.REGULAR))
    assertEquals("false", System.getProperty("idea.use.native.fs.for.win"))
  }

  @Test
  fun `ParseError contains correct line and column numbers`() {
    val code =
        """
        |// Foo
        |fun good() {
        |  //
        |}
        |
        |fn (
        |"""
            .trimMargin()
    try {
      Formatter.format(code, FileType.REGULAR)
      fail()
    } catch (e: ParseError) {
      assertEquals(5, e.lineColumn.line)
      assertEquals(0, e.lineColumn.column)
      assertContainsMatch(e.errorDescription, "Expecting a top level declaration")
    }
  }

  @Test
  fun `Code with tombstones is not supported`() {
    val code =
        """
        |fun good() {
        |  // ${'\u0003'}
        |}
        |"""
            .trimMargin()
    try {
      Formatter.format(code, FileType.REGULAR)
      fail()
    } catch (e: ParseError) {
      assertContains(e.errorDescription, "\\u0003")
      assertEquals(1, e.lineColumn.line)
      assertEquals(5, e.lineColumn.column)
    }
  }

  @Test
  fun `fail() reports line+column number`() {
    val code =
        """
        |// Foo
        |fun good() {
        |  return@ 5
        |}
        |"""
            .trimMargin()
    try {
      Formatter.format(code, FileType.REGULAR)
      fail()
    } catch (e: ParseError) {
      assertEquals(2, e.lineColumn.line)
      assertEquals(8, e.lineColumn.column)
    }
  }
}
