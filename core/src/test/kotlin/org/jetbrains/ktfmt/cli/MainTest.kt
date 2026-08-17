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

package org.jetbrains.ktfmt.cli

import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Files
import java.util.concurrent.ForkJoinPool
import kotlin.io.path.createTempDirectory
import org.jetbrains.ktfmt.testutil.assertContains
import org.jetbrains.ktfmt.testutil.assertDoesNotContain
import org.jetbrains.ktfmt.testutil.assertStartsWith
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@Suppress("FunctionNaming")
class MainTest {

  private val root = createTempDirectory().toFile()

  private val emptyInput = "".byteInputStream()
  private val out = ByteArrayOutputStream()
  private val err = ByteArrayOutputStream()

  private val testCharset = StandardCharsets.UTF_16

  @BeforeEach
  fun setUp() {
    assertEquals(testCharset, Charset.defaultCharset()) // Verify the test JVM flags
  }

  @AfterEach
  fun tearDown() {
    root.deleteRecursively()
  }

  /**
   * Scenario: someone _really_ wants to format this file, regardless of its extension. When a
   * single argument file is given, it is used as is without filtering by extension.
   */
  @Test
  fun `expandArgsToFileNames - single file arg is used as is`() {
    val fooBar = root.resolve("foo.bar")
    fooBar.writeText("hi", UTF_8)
    assertEquals(listOf(fooBar), Main.expandArgsToFileNames(listOf(fooBar.toString())))
  }

  @Test
  fun `expandArgsToFileNames - single arg which is not a file is not returned`() {
    val fooBar = root.resolve("foo.bar")
    assertTrue(Main.expandArgsToFileNames(listOf(fooBar.toString())).isEmpty())
  }

  @Test
  fun `expandArgsToFileNames - single arg which is a directory is resolved to its recursively contained kt files`() {
    val dir = root.resolve("dir")
    dir.mkdirs()
    val foo = dir.resolve("foo.kt")
    foo.writeText("", UTF_8)
    val bar = dir.resolve("bar.kt")
    bar.writeText("", UTF_8)
    assertEquals(setOf(foo, bar), Main.expandArgsToFileNames(listOf(dir.toString())).toSet())
  }

  @Test
  fun `expandArgsToFileNames - multiple directory args are resolved to their recursively contained kt files`() {
    val dir1 = root.resolve("dir1")
    dir1.mkdirs()
    val foo1 = dir1.resolve("foo1.kt")
    foo1.writeText("", UTF_8)
    val bar1 = dir1.resolve("bar1.kt")
    bar1.writeText("", UTF_8)

    val dir2 = root.resolve("dir2")
    dir1.mkdirs()
    val foo2 = dir1.resolve("foo2.kt")
    foo2.writeText("", UTF_8)
    val bar2 = dir1.resolve("bar2.kt")
    bar2.writeText("", UTF_8)

    assertEquals(
        setOf(foo1, bar1, foo2, bar2),
        Main.expandArgsToFileNames(listOf(dir1.toString(), dir2.toString())).toSet(),
    )
  }

  @Test
  fun `Using '-' as the filename formats an InputStream`() {
    val code = "fun    f1 (  ) :    Int =    0"
    Main(code.byteInputStream(), PrintStream(out), PrintStream(err), arrayOf("-")).run()

    val expected = "fun f1(): Int = 0\n"
    assertEquals(expected, out.toString(UTF_8))
  }

  @Test
  fun `Parsing errors are reported (stdin)`() {
    val code = "fun    f1 (  "
    val returnValue =
        Main(code.byteInputStream(), PrintStream(out), PrintStream(err), arrayOf("-")).run()

    assertEquals(1, returnValue)
    assertStartsWith(err.toString(testCharset), "<stdin>:1:14: error: ")
  }

  @Test
  fun `Parsing errors are reported (stdin-name)`() {
    val code = "fun    f1 (  "
    val returnValue = Main(
        code.byteInputStream(),
        PrintStream(out),
        PrintStream(err),
        arrayOf("--stdin-name=file/Foo.kt", "-"),
    )
        .run()

    assertEquals(1, returnValue)
    assertStartsWith(err.toString(testCharset), "file/Foo.kt:1:14: error: ")
  }

  @Test
  fun `Parsing errors are reported (file)`() {
    val fooBar = root.resolve("foo.kt")
    fooBar.writeText("fun    f1 (  ", UTF_8)
    val returnValue =
        Main(emptyInput, PrintStream(out), PrintStream(err), arrayOf(fooBar.toString())).run()

    assertEquals(1, returnValue)
    assertContains(err.toString(testCharset), "foo.kt:1:14: error: ")
  }

  @Test
  fun `Parsing error for multiple trailing lambdas`() {
    val fooBar = root.resolve("foo.kt")
    fooBar.writeText("val x = foo(bar { } { zap = 2 })")
    val returnValue =
        Main(emptyInput, PrintStream(out), PrintStream(err), arrayOf(fooBar.toString())).run()

    assertEquals(1, returnValue)
    assertContains(
        err.toString(testCharset),
        "foo.kt:1:21: error: Maximum one trailing lambda is allowed",
    )
  }

  @Test
  fun `all files in args are processed, even if one of them has an error`() {
    val file1 = root.resolve("file1.kt")
    val file2Broken = root.resolve("file2.kt")
    val file3 = root.resolve("file3.kt")
    file1.writeText("fun    f1 ()  ", UTF_8)
    file2Broken.writeText("fun    f1 (  ", UTF_8)
    file3.writeText("fun    f1 ()  ", UTF_8)

    // Make Main() process files serially.
    val forkJoinPool = ForkJoinPool(1)

    val returnValue: Int =
        forkJoinPool
            .submit<Int> {
              Main(
                  emptyInput,
                  PrintStream(out),
                  PrintStream(err),
                  arrayOf(file1.toString(), file2Broken.toString(), file3.toString()),
              )
                  .run()
            }
            .get()

    assertEquals(1, returnValue)
    assertContains(err.toString(testCharset), "Done formatting $file1")
    assertContains(err.toString(testCharset), "file2.kt:1:14: error: ")
    assertContains(err.toString(testCharset), "Done formatting $file3")
  }

  @Test
  fun `file is not modified if it is already formatted`() {
    val code = """fun f() = println("hello, world")""" + "\n"
    val formattedFile = root.resolve("formatted_file.kt")
    formattedFile.writeText(code, UTF_8)
    val formattedFilePath = formattedFile.toPath()

    val lastModifiedTimeBeforeRunningFormatter =
        Files.getLastModifiedTime(formattedFilePath).toMillis()
    Main(emptyInput, PrintStream(out), PrintStream(err), arrayOf(formattedFile.toString())).run()
    val lastModifiedTimeAfterRunningFormatter =
        Files.getLastModifiedTime(formattedFilePath).toMillis()

    assertEquals(lastModifiedTimeAfterRunningFormatter, lastModifiedTimeBeforeRunningFormatter)
  }

  @Test
  fun `file is modified if it is not formatted`() {
    val code = """fun f() =   println(  "hello, world")""" + "\n"
    val unformattedFile = root.resolve("unformatted_file.kt")
    unformattedFile.writeText(code, UTF_8)
    val unformattedFilePath = unformattedFile.toPath()

    val lastModifiedTimeBeforeRunningFormatter =
        Files.getLastModifiedTime(unformattedFilePath).toMillis()
    // The test may run under 1ms, and we need to make sure the new file timestamp will be different
    Thread.sleep(100)
    Main(emptyInput, PrintStream(out), PrintStream(err), arrayOf(unformattedFile.toString())).run()
    val lastModifiedTimeAfterRunningFormatter =
        Files.getLastModifiedTime(unformattedFilePath).toMillis()

    assertTrue(lastModifiedTimeBeforeRunningFormatter < lastModifiedTimeAfterRunningFormatter)
  }

  @Test
  fun `kotlinlang-style is passed to formatter (file)`() {
    val code =
        """fun f() {
    for (child in
        node.next.next.next.next.next.next.next.next.next.next.next.next.next.next.data()) {
        println(child)
    }
}
"""
    val fooBar = root.resolve("foo.kt")
    fooBar.writeText(code, UTF_8)

    Main(
        emptyInput,
        PrintStream(out),
        PrintStream(err),
        arrayOf("--kotlinlang-style", fooBar.toString()),
    )
        .run()

    assertEquals(code, fooBar.readText())
  }

  @Test
  fun `kotlinlang-style is passed to formatter (stdin)`() {
    val code =
        """
        |fun f() {
        |for (child in
        |node.next.next.next.next.next.next.next.next.next.next.next.next.next.next.data()) {
        |println(child)
        |}
        |}
        |"""
            .trimMargin()
    val formatted =
        """
        |fun f() {
        |    for (child in
        |        node.next.next.next.next.next.next.next.next.next.next.next.next.next.next.data()) {
        |        println(child)
        |    }
        |}
        |"""
            .trimMargin()
    Main(
        code.byteInputStream(),
        PrintStream(out),
        PrintStream(err),
        arrayOf("--kotlinlang-style", "-"),
    )
        .run()

    assertEquals(formatted, out.toString(UTF_8))
  }

  @Test
  fun `expandArgsToFileNames - resolves 'kt' and 'kts' filenames only (recursively)`() {
    val f1 = root.resolve("1.kt")
    val f2 = root.resolve("2.kt")
    val f3 = root.resolve("3")
    val f4 = root.resolve("4.dummyext")
    val f5 = root.resolve("5.kts")

    val dir = root.resolve("foo")
    dir.mkdirs()
    val f6 = root.resolve("foo/1.kt")
    val f7 = root.resolve("foo/2.kts")
    val f8 = root.resolve("foo/3.dummyext")
    val files = listOf(f1, f2, f3, f4, f5, f6, f7, f8)
    for (f in files) {
      f.createNewFile()
    }
    assertEquals(
        setOf(f1, f2, f5, f6, f7),
        Main.expandArgsToFileNames(files.map { it.toString() }).toSet(),
    )
  }

  @Test
  fun `formatting from stdin prints formatted code to stdout regardless of whether it was already formatted`() {
    val expected = """fun f() = println("hello, world")""" + "\n"

    Main(
        """fun f (   ) =    println("hello, world")""".byteInputStream(),
        PrintStream(out),
        PrintStream(err),
        arrayOf("-"),
    )
        .run()
    assertEquals(expected, out.toString(UTF_8))

    out.reset()

    Main(
        """fun f () = println("hello, world")""".byteInputStream(),
        PrintStream(out),
        PrintStream(err),
        arrayOf("-"),
    )
        .run()
    assertEquals(expected, out.toString(UTF_8))
  }

  @Test
  fun `--dry-run prints filename and does not change file`() {
    val code = """fun f () =    println( "hello, world" )"""
    val file = root.resolve("foo.kt")
    file.writeText(code, UTF_8)

    Main(emptyInput, PrintStream(out), PrintStream(err), arrayOf("--dry-run", file.toString()))
        .run()

    assertEquals(code, file.readText())
    assertContains(out.toString(testCharset), file.toString())
  }

  @Test
  fun `--dry-run prints 'stdin' and does not reformat code from stdin`() {
    val code = """fun f () =    println( "hello, world" )"""

    Main(code.byteInputStream(), PrintStream(out), PrintStream(err), arrayOf("--dry-run", "-"))
        .run()

    assertDoesNotContain(out.toString(UTF_8), "hello, world")
    assertEquals("<stdin>${System.lineSeparator()}", out.toString(testCharset))
  }

  @Test
  fun `--dry-run prints nothing when there are no changes needed (file)`() {
    val code = """fun f() = println("hello, world")\n"""
    val file = root.resolve("foo.kt")
    file.writeText(code, UTF_8)

    Main(emptyInput, PrintStream(out), PrintStream(err), arrayOf("--dry-run", file.toString()))
        .run()

    assertEquals("", out.toString(UTF_8))
  }

  @Test
  fun `--dry-run prints nothing when there are no changes needed (stdin)`() {
    val code = """fun f() = println("hello, world")\n"""

    Main(code.byteInputStream(), PrintStream(out), PrintStream(err), arrayOf("--dry-run", "-"))
        .run()

    assertEquals("", out.toString(UTF_8))
  }

  @Test
  fun `Exit code is 0 when there are changes (file)`() {
    val code = """fun f () =    println( "hello, world" )"""
    val file = root.resolve("foo.kt")
    file.writeText(code, UTF_8)

    val exitCode =
        Main(emptyInput, PrintStream(out), PrintStream(err), arrayOf(file.toString())).run()

    assertEquals(0, exitCode)
  }

  @Test
  fun `Exits with 0 when there are changes (stdin)`() {
    val code = """fun f () =    println( "hello, world" )"""

    val exitCode =
        Main(code.byteInputStream(), PrintStream(out), PrintStream(err), arrayOf("-")).run()

    assertEquals(0, exitCode)
  }

  @Test
  fun `Exit code is 1 when there are changes and --set-exit-if-changed is set (file)`() {
    val code = """fun f () =    println( "hello, world" )"""
    val file = root.resolve("foo.kt")
    file.writeText(code, UTF_8)

    val exitCode = Main(
        emptyInput,
        PrintStream(out),
        PrintStream(err),
        arrayOf("--set-exit-if-changed", file.toString()),
    )
        .run()

    assertEquals(1, exitCode)
  }

  @Test
  fun `Exit code is 1 when there are changes and --set-exit-if-changed is set (stdin)`() {
    val code = """fun f () =    println( "hello, world" )"""

    val exitCode = Main(
        code.byteInputStream(),
        PrintStream(out),
        PrintStream(err),
        arrayOf("--set-exit-if-changed", "-"),
    )
        .run()

    assertEquals(1, exitCode)
  }

  @Test
  fun `--set-exit-if-changed and --dry-run changes nothing, prints filenames, and exits with 1 (file)`() {
    val code = """fun f () =    println( "hello, world" )"""
    val file = root.resolve("foo.kt")
    file.writeText(code, UTF_8)

    val exitCode = Main(
        emptyInput,
        PrintStream(out),
        PrintStream(err),
        arrayOf("--dry-run", "--set-exit-if-changed", file.toString()),
    )
        .run()

    assertEquals(code, file.readText())
    assertContains(out.toString(testCharset), file.toString())
    assertEquals(1, exitCode)
  }

  @Test
  fun `--set-exit-if-changed and --dry-run changes nothing, prints filenames, and exits with 1 (stdin)`() {
    val code = """fun f () =    println( "hello, world" )"""

    val exitCode = Main(
        code.byteInputStream(),
        PrintStream(out),
        PrintStream(err),
        arrayOf("--dry-run", "--set-exit-if-changed", "-"),
    )
        .run()

    assertDoesNotContain(out.toString(UTF_8), "hello, world")
    assertEquals("<stdin>${System.lineSeparator()}", out.toString(testCharset))
    assertEquals(1, exitCode)
  }

  @Test
  fun `Always use UTF8 encoding (stdin, stdout)`() {
    val code = """fun f () =    println( "hello, world" )"""
    val expected = """fun f() = println("hello, world")""" + "\n"

    val exitCode = Main(
        code.byteInputStream(UTF_8),
        PrintStream(out, true, testCharset),
        PrintStream(err),
        arrayOf("-"),
    )
        .run()

    assertEquals(0, exitCode)
    assertEquals(expected, out.toString(UTF_8))
  }

  @Test
  fun `Always use UTF8 encoding (file)`() {
    val code = """fun f() =   println(  "hello, world")""" + "\n"
    val file = root.resolve("unformatted_file.kt")
    file.writeText(code, UTF_8)

    val exitCode = Main(
        emptyInput,
        PrintStream(out),
        PrintStream(err),
        arrayOf(file.toString()),
    )
        .run()

    assertEquals(0, exitCode)
    assertEquals("""fun f() = println("hello, world")""" + "\n", file.readText(UTF_8))
  }

  @Test
  fun `UTF-8 BOM is ignored when formatting file`() {
    val code = "\uFEFFfun f () =    println( \"hello, world\" )"
    val file = root.resolve("bom.kt")
    file.writeText(code, UTF_8)

    val exitCode = Main(
        emptyInput,
        PrintStream(out),
        PrintStream(err),
        arrayOf(file.toString()),
    )
        .run()

    assertEquals(0, exitCode)
    assertEquals("""fun f() = println("hello, world")""" + "\n", file.readText(UTF_8))
  }

  @Test
  fun `--help gives return code of 0`() {
    val exitCode = Main(
        emptyInput,
        PrintStream(out),
        PrintStream(err),
        arrayOf("--help"),
    )
        .run()

    assertEquals(0, exitCode)
  }

  @Test
  fun `--quiet suppresses 'Done formatting' output`() {
    val code = """fun f () =    println( "hello, world" )"""
    val file = root.resolve("foo.kt")
    file.writeText(code, UTF_8)

    Main(emptyInput, PrintStream(out), PrintStream(err), arrayOf("--quiet", file.toString())).run()

    assertDoesNotContain(err.toString(testCharset), "Done formatting")
  }

  @Test
  fun `--quiet still reports errors`() {
    val fooBar = root.resolve("foo.kt")
    fooBar.writeText("fun    f1 (  ", UTF_8)
    val returnValue =
        Main(emptyInput, PrintStream(out), PrintStream(err), arrayOf("--quiet", fooBar.toString()))
            .run()

    assertEquals(1, returnValue)
    assertContains(err.toString(testCharset), "foo.kt:1:14: error: ")
  }

  @Test
  fun `--lines formats the selected file statement`() {
    val code =
        """
        |fun untouched ( ) =   1
        |
        |fun test() {
        |  val selected    =   2
        |  val adjacent    =   3
        |}
        |"""
            .trimMargin()
    val file = root.resolve("foo.kt")
    file.writeText(code, UTF_8)

    val exitCode =
        Main(emptyInput, PrintStream(out), PrintStream(err), arrayOf("--lines=4", file.toString()))
            .run()

    assertEquals(0, exitCode)
    assertEquals(
        """
        |fun untouched ( ) =   1
        |
        |fun test() {
        |  val selected = 2
        |  val adjacent    =   3
        |}
        |"""
            .trimMargin(),
        file.readText(UTF_8),
    )
  }

  @Test
  fun `--lines formats the selected stdin statement`() {
    val code =
        """
        |fun untouched ( ) =   1
        |
        |fun test() {
        |  val selected    =   2
        |  val adjacent    =   3
        |}
        |"""
            .trimMargin()

    val exitCode =
        Main(code.byteInputStream(), PrintStream(out), PrintStream(err), arrayOf("--lines=4", "-"))
            .run()

    assertEquals(0, exitCode)
    assertEquals(
        """
        |fun untouched ( ) =   1
        |
        |fun test() {
        |  val selected = 2
        |  val adjacent    =   3
        |}
        |"""
            .trimMargin(),
        out.toString(UTF_8),
    )
  }

  @Test
  fun `--lines formats the selected class member statement`() {
    val code =
        """
        |class Sample {
        |  fun untouched ( ) =   1
        |
        |  fun test() {
        |    val selected    =   2
        |    val adjacent    =   3
        |  }
        |}
        |"""
            .trimMargin()

    val exitCode =
        Main(code.byteInputStream(), PrintStream(out), PrintStream(err), arrayOf("--lines=5", "-"))
            .run()

    assertEquals(0, exitCode)
    assertEquals(
        """
        |class Sample {
        |  fun untouched ( ) =   1
        |
        |  fun test() {
        |    val selected = 2
        |    val adjacent    =   3
        |  }
        |}
        |"""
            .trimMargin(),
        out.toString(UTF_8),
    )
  }

  @Test
  fun `--lines applies import cleanup after selected formatting`() {
    val code =
        """
        |import com.unused.Sample
        |import com.used.FooBarBaz as Baz
        |import com.used.bar
        |
        |fun untouched ( ) =   1
        |
        |fun test() {
        |  val selected    =   2
        |  Baz(bar)
        |}
        |"""
            .trimMargin()

    val exitCode =
        Main(code.byteInputStream(), PrintStream(out), PrintStream(err), arrayOf("--lines=8", "-"))
            .run()

    assertEquals(0, exitCode)
    assertEquals(
        """
        |import com.used.FooBarBaz as Baz
        |import com.used.bar
        |
        |fun untouched ( ) =   1
        |
        |fun test() {
        |  val selected = 2
        |  Baz(bar)
        |}
        |"""
            .trimMargin(),
        out.toString(UTF_8),
    )
  }

  @Test
  fun `--lines applies multiline string cleanup after selected formatting`() {
    val code =
        """
        |val indent =
        |    ""${'"'}     
        |         example
        |          of
        |            a
        |
        |         multiline
        |           string
        |         ""${'"'}
        |         .trimIndent()
        |
        |fun untouched ( ) =   1
        |
        |fun test() {
        |  val selected    =   2
        |}
        |"""
            .trimMargin()

    val exitCode =
        Main(code.byteInputStream(), PrintStream(out), PrintStream(err), arrayOf("--lines=15", "-"))
            .run()

    assertEquals(0, exitCode)
    assertEquals(
        """
        |val indent =
        |    ""${'"'}
        |    example
        |     of
        |       a
        |
        |    multiline
        |      string
        |    ""${'"'}
        |        .trimIndent()
        |
        |fun untouched ( ) =   1
        |
        |fun test() {
        |  val selected = 2
        |}
        |"""
            .trimMargin(),
        out.toString(UTF_8),
    )
  }

  @Test
  fun `--offset and --length format the selected file cursor line`() {
    val code =
        """
        |fun untouched ( ) =   1
        |
        |fun test() {
        |  val selected    =   2
        |  val adjacent    =   3
        |}
        |"""
            .trimMargin()
    val file = root.resolve("foo.kt")
    file.writeText(code, UTF_8)

    val exitCode = Main(
        emptyInput,
        PrintStream(out),
        PrintStream(err),
        arrayOf(
            "--offset=${code.indexOf("selected")}",
            "--length=0",
            file.toString(),
        ),
    )
        .run()

    assertEquals(0, exitCode)
    assertEquals(
        """
        |fun untouched ( ) =   1
        |
        |fun test() {
        |  val selected = 2
        |  val adjacent    =   3
        |}
        |"""
            .trimMargin(),
        file.readText(UTF_8),
    )
  }

  @Test
  fun `--offset and --length format the selected stdin cursor line`() {
    val code =
        """
        |fun untouched ( ) =   1
        |
        |fun test() {
        |  val selected    =   2
        |  val adjacent    =   3
        |}
        |"""
            .trimMargin()

    val exitCode = Main(
        code.byteInputStream(),
        PrintStream(out),
        PrintStream(err),
        arrayOf("--offset=${code.indexOf("selected")}", "--length=0", "-"),
    )
        .run()

    assertEquals(0, exitCode)
    assertEquals(
        """
        |fun untouched ( ) =   1
        |
        |fun test() {
        |  val selected = 2
        |  val adjacent    =   3
        |}
        |"""
            .trimMargin(),
        out.toString(UTF_8),
    )
  }

  @Test
  fun `--range-start and --range-end format the selected file statement`() {
    val code =
        """
        |fun untouched ( ) =   1
        |
        |fun test() {
        |  val selected    =   2
        |  val adjacent    =   3
        |}
        |"""
            .trimMargin()
    val file = root.resolve("foo.kt")
    file.writeText(code, UTF_8)

    val startOffset = code.indexOf("val selected")
    val endOffset = code.indexOf("val adjacent")
    val exitCode = Main(
        emptyInput,
        PrintStream(out),
        PrintStream(err),
        arrayOf(
            "--range-start=$startOffset",
            "--range-end=$endOffset",
            file.toString(),
        ),
    )
        .run()

    assertEquals(0, exitCode)
    assertEquals(
        """
        |fun untouched ( ) =   1
        |
        |fun test() {
        |  val selected = 2
        |  val adjacent    =   3
        |}
        |"""
            .trimMargin(),
        file.readText(UTF_8),
    )
  }

  @Test
  fun `--range-start and --range-end format the selected stdin statement`() {
    val code =
        """
        |fun untouched ( ) =   1
        |
        |fun test() {
        |  val selected    =   2
        |  val adjacent    =   3
        |}
        |"""
            .trimMargin()

    val startOffset = code.indexOf("val selected")
    val endOffset = code.indexOf("val adjacent")
    val exitCode = Main(
        code.byteInputStream(),
        PrintStream(out),
        PrintStream(err),
        arrayOf("--range-start=$startOffset", "--range-end=$endOffset", "-"),
    )
        .run()

    assertEquals(0, exitCode)
    assertEquals(
        """
        |fun untouched ( ) =   1
        |
        |fun test() {
        |  val selected = 2
        |  val adjacent    =   3
        |}
        |"""
            .trimMargin(),
        out.toString(UTF_8),
    )
  }

  @Test
  fun `--range-start without --range-end formats to end of file`() {
    val code =
        """
        |fun untouched ( ) =   1
        |
        |fun test() {
        |  val selected    =   2
        |  val adjacent    =   3
        |}
        |"""
            .trimMargin()
    val file = root.resolve("foo.kt")
    file.writeText(code, UTF_8)

    val startOffset = code.indexOf("fun test()")
    val exitCode = Main(
        emptyInput,
        PrintStream(out),
        PrintStream(err),
        arrayOf("--range-start=$startOffset", file.toString()),
    )
        .run()

    assertEquals(0, exitCode)
    assertEquals(
        """
        |fun untouched ( ) =   1
        |
        |fun test() {
        |  val selected = 2
        |  val adjacent = 3
        |}
        |"""
            .trimMargin(),
        file.readText(UTF_8),
    )
  }

  @Test
  fun `--range-end without --range-start formats from start of file`() {
    val code =
        """
        |fun untouched ( ) =   1
        |
        |fun test() {
        |  val selected    =   2
        |  val adjacent    =   3
        |}
        |"""
            .trimMargin()
    val file = root.resolve("foo.kt")
    file.writeText(code, UTF_8)

    val endOffset = code.indexOf("fun test()")
    val exitCode = Main(
        emptyInput,
        PrintStream(out),
        PrintStream(err),
        arrayOf("--range-end=$endOffset", file.toString()),
    )
        .run()

    assertEquals(0, exitCode)
    assertEquals(
        """
        |fun untouched() = 1
        |
        |fun test() {
        |  val selected    =   2
        |  val adjacent    =   3
        |}
        |"""
            .trimMargin(),
        file.readText(UTF_8),
    )
  }

  @Test
  fun `--range-start and --range-end with --dry-run prints filename on changes`() {
    val code =
        """
        |fun untouched ( ) =   1
        |
        |fun test() {
        |  val selected    =   2
        |}
        |"""
            .trimMargin()
    val file = root.resolve("foo.kt")
    file.writeText(code, UTF_8)

    val startOffset = code.indexOf("val selected")
    val exitCode = Main(
        emptyInput,
        PrintStream(out),
        PrintStream(err),
        arrayOf(
            "--dry-run",
            "--range-start=$startOffset",
            "--range-end=${startOffset + 10}",
            file.toString(),
        ),
    )
        .run()

    assertEquals(0, exitCode)
    assertEquals(code, file.readText(UTF_8))
    assertContains(out.toString(testCharset), file.toString())
  }

  @Test
  fun `--range-start and --range-end with --set-exit-if-changed returns 1 on changes`() {
    val code =
        """
        |fun untouched ( ) =   1
        |
        |fun test() {
        |  val selected    =   2
        |}
        |"""
            .trimMargin()
    val file = root.resolve("foo.kt")
    file.writeText(code, UTF_8)

    val startOffset = code.indexOf("val selected")
    val exitCode = Main(
        emptyInput,
        PrintStream(out),
        PrintStream(err),
        arrayOf(
            "--set-exit-if-changed",
            "--range-start=$startOffset",
            "--range-end=${startOffset + 10}",
            file.toString(),
        ),
    )
        .run()

    assertEquals(1, exitCode)
  }

  @Test
  fun `--range-start and --range-end adjust properly for shebang`() {
    val code =
        """
        |#!/usr/bin/env kotlin
        |fun untouched ( ) =   1
        |
        |fun test() {
        |  val selected    =   2
        |  val adjacent    =   3
        |}
        |"""
            .trimMargin()
    val file = root.resolve("foo.kt")
    file.writeText(code, UTF_8)

    val startOffset = code.indexOf("val selected")
    val endOffset = code.indexOf("val adjacent")
    val exitCode = Main(
        emptyInput,
        PrintStream(out),
        PrintStream(err),
        arrayOf(
            "--range-start=$startOffset",
            "--range-end=$endOffset",
            file.toString(),
        ),
    )
        .run()

    assertEquals(0, exitCode)
    assertEquals(
        """
        |#!/usr/bin/env kotlin
        |fun untouched ( ) =   1
        |
        |fun test() {
        |  val selected = 2
        |  val adjacent    =   3
        |}
        |"""
            .trimMargin(),
        file.readText(UTF_8),
    )
  }

  @Test
  fun `--range-start equal to --range-end leaves file untouched`() {
    val code =
        """
        |fun untouched ( ) =   1
        |
        |fun test() {
        |  val selected    =   2
        |  val adjacent    =   3
        |}
        |"""
            .trimMargin()
    val file = root.resolve("foo.kt")
    file.writeText(code, UTF_8)

    val offset = code.indexOf("selected")
    val exitCode = Main(
        emptyInput,
        PrintStream(out),
        PrintStream(err),
        arrayOf(
            "--range-start=$offset",
            "--range-end=$offset",
            file.toString(),
        ),
    )
        .run()

    assertEquals(0, exitCode)
    assertEquals(code, file.readText(UTF_8))
  }

  @Test
  fun `--range-end beyond file length formats up to end of file without error`() {
    val code =
        """
        |fun untouched ( ) =   1
        |
        |fun test() {
        |  val selected    =   2
        |}
        |"""
            .trimMargin()
    val file = root.resolve("foo.kt")
    file.writeText(code, UTF_8)

    val startOffset = code.indexOf("fun test()")
    val exitCode = Main(
        emptyInput,
        PrintStream(out),
        PrintStream(err),
        arrayOf(
            "--range-start=$startOffset",
            "--range-end=999999",
            file.toString(),
        ),
    )
        .run()

    assertEquals(0, exitCode)
    assertEquals(
        """
        |fun untouched ( ) =   1
        |
        |fun test() {
        |  val selected = 2
        |}
        |"""
            .trimMargin(),
        file.readText(UTF_8),
    )
  }

  @Test
  fun `--range-start rejects directories that expand to multiple files`() {
    val dir = root.resolve("dir")
    dir.mkdirs()
    dir.resolve("foo.kt").writeText("fun foo () = 1", UTF_8)
    dir.resolve("bar.kt").writeText("fun bar () = 1", UTF_8)

    val exitCode = Main(
        emptyInput,
        PrintStream(out),
        PrintStream(err),
        arrayOf("--range-start=0", dir.toString()),
    )
        .run()

    assertEquals(1, exitCode)
    assertContains(
        err.toString(testCharset),
        "partial formatting is only supported for a single file",
    )
  }

  @Test
  fun `--range-end rejects directories that expand to multiple files`() {
    val dir = root.resolve("dir")
    dir.mkdirs()
    dir.resolve("foo.kt").writeText("fun foo () = 1", UTF_8)
    dir.resolve("bar.kt").writeText("fun bar () = 1", UTF_8)

    val exitCode = Main(
        emptyInput,
        PrintStream(out),
        PrintStream(err),
        arrayOf("--range-end=10", dir.toString()),
    )
        .run()

    assertEquals(1, exitCode)
    assertContains(
        err.toString(testCharset),
        "partial formatting is only supported for a single file",
    )
  }

  @Test
  fun `--lines rejects directories that expand to multiple files`() {
    val dir = root.resolve("dir")
    dir.mkdirs()
    dir.resolve("foo.kt").writeText("fun foo () = 1", UTF_8)
    dir.resolve("bar.kt").writeText("fun bar () = 1", UTF_8)

    val exitCode =
        Main(emptyInput, PrintStream(out), PrintStream(err), arrayOf("--lines=1", dir.toString()))
            .run()

    assertEquals(1, exitCode)
    assertContains(
        err.toString(testCharset),
        "partial formatting is only supported for a single file",
    )
  }

  @Test
  fun `--offset rejects directories that expand to multiple files`() {
    val dir = root.resolve("dir")
    dir.mkdirs()
    dir.resolve("foo.kt").writeText("fun foo () = 1", UTF_8)
    dir.resolve("bar.kt").writeText("fun bar () = 1", UTF_8)

    val exitCode = Main(
        emptyInput,
        PrintStream(out),
        PrintStream(err),
        arrayOf("--offset=0", "--length=5", dir.toString()),
    )
        .run()

    assertEquals(1, exitCode)
    assertContains(
        err.toString(testCharset),
        "partial formatting is only supported for a single file",
    )
  }

  @Test
  fun `--range-start accepts directory that expands to a single kt file`() {
    val dir = root.resolve("dir")
    dir.mkdirs()
    val file = dir.resolve("single.kt")
    val code =
        """
        |fun untouched ( ) =   1
        |
        |fun test() {
        |  val selected    =   2
        |}
        |"""
            .trimMargin()
    file.writeText(code, UTF_8)

    val startOffset = code.indexOf("val selected")
    val exitCode = Main(
        emptyInput,
        PrintStream(out),
        PrintStream(err),
        arrayOf("--range-start=$startOffset", dir.toString()),
    )
        .run()

    assertEquals(0, exitCode)
    assertEquals(
        """
        |fun untouched ( ) =   1
        |
        |fun test() {
        |  val selected = 2
        |}
        |"""
            .trimMargin(),
        file.readText(UTF_8),
    )
  }

  @Test
  fun `--range-start and --range-end with stdin and --dry-run prints stdin name when modified`() {
    val code =
        """
        |fun untouched ( ) =   1
        |
        |fun test() {
        |  val selected    =   2
        |}
        |"""
            .trimMargin()
    val startOffset = code.indexOf("val selected")
    val exitCode = Main(
        code.byteInputStream(UTF_8),
        PrintStream(out),
        PrintStream(err),
        arrayOf(
            "--dry-run",
            "--range-start=$startOffset",
            "--range-end=${startOffset + 10}",
            "-",
        ),
    )
        .run()

    assertEquals(0, exitCode)
    assertEquals("<stdin>${System.lineSeparator()}", out.toString(testCharset))
  }

  @Test
  fun `--range-start and --range-end with stdin and --dry-run prints nothing when unmodified`() {
    val code =
        """
        |fun untouched ( ) =   1
        |
        |fun test() {
        |  val selected = 2
        |}
        |"""
            .trimMargin()
    val startOffset = code.indexOf("val selected")
    val exitCode = Main(
        code.byteInputStream(UTF_8),
        PrintStream(out),
        PrintStream(err),
        arrayOf(
            "--dry-run",
            "--range-start=$startOffset",
            "--range-end=${startOffset + 10}",
            "-",
        ),
    )
        .run()

    assertEquals(0, exitCode)
    assertEquals("", out.toString(testCharset))
  }

  @Test
  fun `--range-start and --range-end with stdin, --stdin-name, and --dry-run prints custom name`() {
    val code =
        """
        |fun untouched ( ) =   1
        |
        |fun test() {
        |  val selected    =   2
        |}
        |"""
            .trimMargin()
    val startOffset = code.indexOf("val selected")
    val exitCode = Main(
        code.byteInputStream(UTF_8),
        PrintStream(out),
        PrintStream(err),
        arrayOf(
            "--dry-run",
            "--stdin-name=custom/Path.kt",
            "--range-start=$startOffset",
            "--range-end=${startOffset + 10}",
            "-",
        ),
    )
        .run()

    assertEquals(0, exitCode)
    assertEquals("custom/Path.kt${System.lineSeparator()}", out.toString(testCharset))
  }

  @Test
  fun `--range-start and --range-end with stdin and --set-exit-if-changed returns 1 when modified`() {
    val code =
        """
        |fun untouched ( ) =   1
        |
        |fun test() {
        |  val selected    =   2
        |}
        |"""
            .trimMargin()
    val startOffset = code.indexOf("val selected")
    val exitCode = Main(
        code.byteInputStream(UTF_8),
        PrintStream(out),
        PrintStream(err),
        arrayOf(
            "--set-exit-if-changed",
            "--range-start=$startOffset",
            "--range-end=${startOffset + 10}",
            "-",
        ),
    )
        .run()

    assertEquals(1, exitCode)
  }

  @Test
  fun `--range-start and --range-end with stdin and --set-exit-if-changed returns 0 when unmodified`() {
    val code =
        """
        |fun untouched ( ) =   1
        |
        |fun test() {
        |  val selected = 2
        |}
        |"""
            .trimMargin()
    val startOffset = code.indexOf("val selected")
    val exitCode = Main(
        code.byteInputStream(UTF_8),
        PrintStream(out),
        PrintStream(err),
        arrayOf(
            "--set-exit-if-changed",
            "--range-start=$startOffset",
            "--range-end=${startOffset + 10}",
            "-",
        ),
    )
        .run()

    assertEquals(0, exitCode)
  }

  @Test
  fun `--range-start and --range-end with file and --dry-run prints nothing when unmodified`() {
    val code =
        """
        |fun untouched ( ) =   1
        |
        |fun test() {
        |  val selected = 2
        |}
        |"""
            .trimMargin()
    val file = root.resolve("foo.kt")
    file.writeText(code, UTF_8)

    val startOffset = code.indexOf("val selected")
    val exitCode = Main(
        emptyInput,
        PrintStream(out),
        PrintStream(err),
        arrayOf(
            "--dry-run",
            "--range-start=$startOffset",
            "--range-end=${startOffset + 10}",
            file.toString(),
        ),
    )
        .run()

    assertEquals(0, exitCode)
    assertEquals("", out.toString(testCharset))
  }

  @Test
  fun `--range-start and --range-end with file and --set-exit-if-changed returns 0 when unmodified`() {
    val code =
        """
        |fun untouched ( ) =   1
        |
        |fun test() {
        |  val selected = 2
        |}
        |"""
            .trimMargin()
    val file = root.resolve("foo.kt")
    file.writeText(code, UTF_8)

    val startOffset = code.indexOf("val selected")
    val exitCode = Main(
        emptyInput,
        PrintStream(out),
        PrintStream(err),
        arrayOf(
            "--set-exit-if-changed",
            "--range-start=$startOffset",
            "--range-end=${startOffset + 10}",
            file.toString(),
        ),
    )
        .run()

    assertEquals(0, exitCode)
  }

  @Test
  fun `--range-start and --range-end with file, --dry-run, and --set-exit-if-changed returns 1 and prints filename`() {
    val code =
        """
        |fun untouched ( ) =   1
        |
        |fun test() {
        |  val selected    =   2
        |}
        |"""
            .trimMargin()
    val file = root.resolve("foo.kt")
    file.writeText(code, UTF_8)

    val startOffset = code.indexOf("val selected")
    val exitCode = Main(
        emptyInput,
        PrintStream(out),
        PrintStream(err),
        arrayOf(
            "--dry-run",
            "--set-exit-if-changed",
            "--range-start=$startOffset",
            "--range-end=${startOffset + 10}",
            file.toString(),
        ),
    )
        .run()

    assertEquals(1, exitCode)
    assertEquals(code, file.readText(UTF_8))
    assertContains(out.toString(testCharset), file.toString())
  }

  @Test
  fun `--range-start and --range-end with stdin, --dry-run, and --set-exit-if-changed returns 1 and prints stdin name`() {
    val code =
        """
        |fun untouched ( ) =   1
        |
        |fun test() {
        |  val selected    =   2
        |}
        |"""
            .trimMargin()
    val startOffset = code.indexOf("val selected")
    val exitCode = Main(
        code.byteInputStream(UTF_8),
        PrintStream(out),
        PrintStream(err),
        arrayOf(
            "--dry-run",
            "--set-exit-if-changed",
            "--range-start=$startOffset",
            "--range-end=${startOffset + 10}",
            "-",
        ),
    )
        .run()

    assertEquals(1, exitCode)
    assertEquals("<stdin>${System.lineSeparator()}", out.toString(testCharset))
  }

  @Test
  fun `--range-start and --range-end with stdin, --dry-run, and --set-exit-if-changed returns 0 and prints nothing when unchanged`() {
    val code =
        """
        |fun untouched ( ) =   1
        |
        |fun test() {
        |  val selected = 2
        |}
        |"""
            .trimMargin()
    val startOffset = code.indexOf("val selected")
    val exitCode = Main(
        code.byteInputStream(UTF_8),
        PrintStream(out),
        PrintStream(err),
        arrayOf(
            "--dry-run",
            "--set-exit-if-changed",
            "--range-start=$startOffset",
            "--range-end=${startOffset + 10}",
            "-",
        ),
    )
        .run()

    assertEquals(0, exitCode)
    assertEquals("", out.toString(testCharset))
  }

  @Test
  fun `--range-start and --range-end with --quiet suppresses output but modifies file`() {
    val code =
        """
        |fun untouched ( ) =   1
        |
        |fun test() {
        |  val selected    =   2
        |}
        |"""
            .trimMargin()
    val file = root.resolve("foo.kt")
    file.writeText(code, UTF_8)

    val startOffset = code.indexOf("val selected")
    val exitCode = Main(
        emptyInput,
        PrintStream(out),
        PrintStream(err),
        arrayOf(
            "--quiet",
            "--range-start=$startOffset",
            "--range-end=${startOffset + 10}",
            file.toString(),
        ),
    )
        .run()

    assertEquals(0, exitCode)
    assertDoesNotContain(err.toString(testCharset), "Done formatting")
    assertEquals(
        """
        |fun untouched ( ) =   1
        |
        |fun test() {
        |  val selected = 2
        |}
        |"""
            .trimMargin(),
        file.readText(UTF_8),
    )
  }

  @Test
  fun `--range-start and --range-end with --enable-editorconfig applies editorconfig formatting options`() {
    val editorConfig = root.resolve(".editorconfig")
    editorConfig.writeText(
        """
        |root = true
        |[*.kt]
        |indent_size = 4
        |"""
            .trimMargin(),
        UTF_8,
    )

    val code =
        """
        |fun untouched ( ) =   1
        |
        |fun test() {
        |  val selected    =   2
        |}
        |"""
            .trimMargin()
    val file = root.resolve("foo.kt")
    file.writeText(code, UTF_8)

    val startOffset = code.indexOf("val selected")
    val exitCode = Main(
        emptyInput,
        PrintStream(out),
        PrintStream(err),
        arrayOf(
            "--enable-editorconfig",
            "--range-start=$startOffset",
            "--range-end=${startOffset + 10}",
            file.toString(),
        ),
    )
        .run()

    assertEquals(0, exitCode)
    assertEquals(
        """
        |fun untouched ( ) =   1
        |
        |fun test() {
        |    val selected = 2
        |}
        |"""
            .trimMargin(),
        file.readText(UTF_8),
    )
  }

  @Test
  fun `--range-start and --range-end preserves CRLF line endings in file`() {
    val code = "fun untouched ( ) =   1\r\n\r\nfun test() {\r\n  val selected    =   2\r\n}\r\n"
    val file = root.resolve("foo.kt")
    file.writeText(code, UTF_8)

    val startOffset = code.indexOf("val selected")
    val exitCode = Main(
        emptyInput,
        PrintStream(out),
        PrintStream(err),
        arrayOf(
            "--range-start=$startOffset",
            "--range-end=${startOffset + 10}",
            file.toString(),
        ),
    )
        .run()

    assertEquals(0, exitCode)
    assertEquals(
        "fun untouched ( ) =   1\r\n\r\nfun test() {\r\n  val selected = 2\r\n}\r\n",
        file.readText(UTF_8),
    )
  }

  @Test
  fun `--range-start and --range-end preserves CRLF line endings with shebang`() {
    val code =
        "#!/usr/bin/env kotlin\r\nfun untouched ( ) =   1\r\n\r\nfun test() {\r\n  val selected    =   2\r\n}\r\n"
    val file = root.resolve("foo.kt")
    file.writeText(code, UTF_8)

    val startOffset = code.indexOf("val selected")
    val exitCode = Main(
        emptyInput,
        PrintStream(out),
        PrintStream(err),
        arrayOf(
            "--range-start=$startOffset",
            "--range-end=${startOffset + 10}",
            file.toString(),
        ),
    )
        .run()

    assertEquals(0, exitCode)
    assertEquals(
        "#!/usr/bin/env kotlin\r\nfun untouched ( ) =   1\r\n\r\nfun test() {\r\n  val selected = 2\r\n}\r\n",
        file.readText(UTF_8),
    )
  }

  @Test
  fun `--range-start and --range-end ignores UTF-8 BOM in file`() {
    val code = "\uFEFFfun untouched ( ) =   1\n\nfun test() {\n  val selected    =   2\n}\n"
    val file = root.resolve("foo.kt")
    file.writeText(code, UTF_8)

    val startOffset = code.indexOf("val selected")
    val exitCode = Main(
        emptyInput,
        PrintStream(out),
        PrintStream(err),
        arrayOf(
            "--range-start=$startOffset",
            "--range-end=${startOffset + 10}",
            file.toString(),
        ),
    )
        .run()

    assertEquals(0, exitCode)
    assertEquals(
        "fun untouched ( ) =   1\n\nfun test() {\n  val selected = 2\n}\n",
        file.readText(UTF_8),
    )
  }

  @Test
  fun `--range-start and --range-end reports syntax error with filename and position`() {
    val code = "fun test( {\n"
    val file = root.resolve("broken.kt")
    file.writeText(code, UTF_8)

    val exitCode = Main(
        emptyInput,
        PrintStream(out),
        PrintStream(err),
        arrayOf("--range-start=0", "--range-end=5", file.toString()),
    )
        .run()

    assertEquals(1, exitCode)
    assertContains(err.toString(testCharset), "broken.kt:1:")
  }

  @Test
  fun `@argfile containing range-start and range-end options formats selected statement`() {
    val file = root.resolve("foo.kt")
    val code =
        """
        |fun untouched ( ) =   1
        |
        |fun test() {
        |  val selected    =   2
        |}
        |"""
            .trimMargin()
    file.writeText(code, UTF_8)

    val startOffset = code.indexOf("val selected")
    val endOffset = startOffset + 15
    val argfile = root.resolve("argfile")
    argfile.writeText(
        "--range-start=$startOffset\n--range-end=$endOffset\n${file.canonicalPath}\n",
        UTF_8,
    )

    val exitCode = Main(
        emptyInput,
        PrintStream(out),
        PrintStream(err),
        arrayOf("@" + argfile.canonicalPath),
    )
        .run()

    assertEquals(0, exitCode)
    assertEquals(
        """
        |fun untouched ( ) =   1
        |
        |fun test() {
        |  val selected = 2
        |}
        |"""
            .trimMargin(),
        file.readText(UTF_8),
    )
  }

  @Test
  fun `Multiple disjoint --offset and --length ranges format only targeted regions`() {
    val code =
        """
        |fun test1() {
        |  val first    =   1
        |  val untouched    =   2
        |  val second    =   3
        |}
        |"""
            .trimMargin()
    val file = root.resolve("foo.kt")
    file.writeText(code, UTF_8)

    val firstOffset = code.indexOf("val first")
    val secondOffset = code.indexOf("val second")
    val exitCode = Main(
        emptyInput,
        PrintStream(out),
        PrintStream(err),
        arrayOf(
            "--offset=$firstOffset",
            "--length=10",
            "--offset=$secondOffset",
            "--length=10",
            file.toString(),
        ),
    )
        .run()

    assertEquals(0, exitCode)
    assertEquals(
        """
        |fun test1() {
        |  val first = 1
        |  val untouched    =   2
        |  val second = 3
        |}
        |"""
            .trimMargin(),
        file.readText(UTF_8),
    )
  }

  @Test
  fun `Combined --range-start, --range-end, and --lines options format union of selections`() {
    val code =
        """
        |fun test() {
        |  val first    =   1
        |  val untouched    =   2
        |  val third    =   3
        |}
        |"""
            .trimMargin()
    val file = root.resolve("foo.kt")
    file.writeText(code, UTF_8)

    val thirdOffset = code.indexOf("val third")
    val exitCode = Main(
        emptyInput,
        PrintStream(out),
        PrintStream(err),
        arrayOf(
            "--lines=2",
            "--range-start=$thirdOffset",
            "--range-end=${thirdOffset + 10}",
            file.toString(),
        ),
    )
        .run()

    assertEquals(0, exitCode)
    assertEquals(
        """
        |fun test() {
        |  val first = 1
        |  val untouched    =   2
        |  val third = 3
        |}
        |"""
            .trimMargin(),
        file.readText(UTF_8),
    )
  }
}
