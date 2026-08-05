/*
 * Portions Copyright (c) Meta Platforms, Inc. and affiliates.
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

package com.facebook.ktfmt.kdoc

import kotlin.io.path.createTempDirectory
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

class KDocFormatterTest {
  private val tempDir = createTempDirectory().toFile()

  private fun checkFormatter(
      task: FormattingTask,
      expected: String,
      verify: Boolean = true,
      verifyDokka: Boolean = true,
  ) {
    val reformatted = reformatComment(task)

    val indent = task.initialIndent
    val options = task.options
    val source = task.comment

    // Because .trimIndent() will remove it:
    val indentedExpected = expected.split("\n").joinToString("\n") { indent + it }

    assertEquals(indentedExpected, reformatted)

    if (verifyDokka && !options.addPunctuation) {
      DokkaVerifier(tempDir).verify(source, reformatted)
    }

    // Make sure that formatting is stable -- format again and make sure it's the same
    if (verify) {
      val again = FormattingTask(
          options,
          reformatted.trim(),
          task.initialIndent,
          task.secondaryIndent,
          task.orderedParameterNames,
      )
      val formattedAgain = reformatComment(again)
      if (reformatted != formattedAgain) {
        assertEquals(
            "$indent// FORMATTED ONCE\n\n$reformatted",
            "$indent// FORMATTED TWICE (implies unstable formatting)\n\n$formattedAgain",
            "Formatting is unstable: if formatted a second time, it changes",
        )
      }
    }
  }

  private fun checkFormatter(
      source: String,
      options: KDocFormattingOptions,
      expected: String,
      indent: String = "    ",
      verify: Boolean = true,
      verifyDokka: Boolean = true,
  ) {
    val task = FormattingTask(options, source.trim(), indent)
    checkFormatter(task, expected, verify, verifyDokka)
  }

  private fun reformatComment(task: FormattingTask): String {
    val formatter = KDocFormatter(task.options)
    val formatted = formatter.reformatComment(task)
    return task.initialIndent + formatted
  }

  @Test
  fun test1() {
    checkFormatter(
        """
        /**
        * Returns whether lint should check all warnings,
         * including those off by default, or null if
         *not configured in this configuration. This is a really really really long sentence which needs to be broken up.
         * And ThisIsALongSentenceWhichCannotBeBrokenUpAndMustBeIncludedAsAWholeWithoutNewlinesInTheMiddle.
         *
         * This is a separate section
         * which should be flowed together with the first one.
         * *bold* should not be removed even at beginning.
         */
        """
            .trimIndent(),
        KDocFormattingOptions(72),
        """
        /**
         * Returns whether lint should check all warnings, including those
         * off by default, or null if not configured in this configuration.
         * This is a really really really long sentence which needs to be
         * broken up. And
         * ThisIsALongSentenceWhichCannotBeBrokenUpAndMustBeIncludedAsAWholeWithoutNewlinesInTheMiddle.
         *
         * This is a separate section which should be flowed together with
         * the first one. *bold* should not be removed even at beginning.
         */
        """
            .trimIndent(),
    )
  }

  @Test
  fun testWithOffset() {
    val source =
        """
        /** Returns whether lint should check all warnings,
         * including those off by default */
        """
            .trimIndent()
    val reformatted =
        """
        /**
         * Returns whether lint should check all warnings, including those
         * off by default
         */
        """
            .trimIndent()
    checkFormatter(source, KDocFormattingOptions(72), reformatted, indent = "    ")
    val initialOffset = source.indexOf("default")
    val newOffset = findSamePosition(source, initialOffset, reformatted)
    assertNotEquals(initialOffset, newOffset)
    assertEquals("default", reformatted.substring(newOffset, newOffset + "default".length))
  }

  @Test
  fun testWordBreaking() {
    // Without special handling, the "-" in the below would be placed at the
    // beginning of line 2, which then implies a list item.
    val source =
        """
        /** Returns whether lint should check all warnings,
         * including aaaaaa - off by default */
        """
            .trimIndent()
    val reformatted =
        """
        /**
         * Returns whether lint should check all warnings, including
         * aaaaaa - off by default
         */
        """
            .trimIndent()
    checkFormatter(source, KDocFormattingOptions(72), reformatted, indent = "    ")
    val initialOffset = source.indexOf("default")
    val newOffset = findSamePosition(source, initialOffset, reformatted)
    assertNotEquals(initialOffset, newOffset)
    assertEquals("default", reformatted.substring(newOffset, newOffset + "default".length))
  }

  @Test
  fun testHeader() {
    val source =
        """
        /**
         * Information about a request to run lint.
         *
         * **NOTE: This is not a public or final API; if you rely on this be prepared
         * to adjust your code for the next tools release.**
         */
        """
            .trimIndent()
    checkFormatter(
        source,
        KDocFormattingOptions(72),
        """
        /**
         * Information about a request to run lint.
         *
         * **NOTE: This is not a public or final API; if you rely on this be
         * prepared to adjust your code for the next tools release.**
         */
        """
            .trimIndent(),
    )

    checkFormatter(
        source,
        KDocFormattingOptions(40),
        """
        /**
         * Information about a request to run
         * lint.
         *
         * **NOTE: This is not a public or final
         * API; if you rely on this be prepared
         * to adjust your code for the next
         * tools release.**
         */
        """
            .trimIndent(),
        indent = "",
    )

    checkFormatter(
        source,
        KDocFormattingOptions(100, 100),
        """
        /**
         * Information about a request to run lint.
         *
         * **NOTE: This is not a public or final API; if you rely on this be prepared to adjust your code
         * for the next tools release.**
         */
        """
            .trimIndent(),
        indent = "",
    )
  }

  @Test
  fun testSingle() {
    val source =
        """
        /**
         * The lint client requesting the lint check
         *
         * @return the client, never null
         */
        """
            .trimIndent()
    checkFormatter(
        source,
        KDocFormattingOptions(72),
        """
        /**
         * The lint client requesting the lint check
         *
         * @return the client, never null
         */
        """
            .trimIndent(),
    )
  }

  @Test
  fun testEmpty() {
    val source =
        """
        /** */
        """
            .trimIndent()
    checkFormatter(
        source,
        KDocFormattingOptions(72),
        """
        /**  */
        """
            .trimIndent(),
    )

    checkFormatter(
        source,
        KDocFormattingOptions(72).apply { collapseSingleLine = false },
        """
        /**
         */
        """
            .trimIndent(),
    )
  }

  @Test
  fun testJavadocParams() {
    val source =
        """
        /**
         * Sets the scope to use; lint checks which require a wider scope set
         * will be ignored
         *
         * @param scope the scope
         *
         * @return this, for constructor chaining
         */
        """
            .trimIndent()
    checkFormatter(
        source,
        KDocFormattingOptions(72),
        """
        /**
         * Sets the scope to use; lint checks which require a wider scope
         * set will be ignored
         *
         * @param scope the scope
         * @return this, for constructor chaining
         */
        """
            .trimIndent(),
    )
  }

  @Test
  fun testBracketParam() {
    // Regression test for https://github.com/tnorbye/kdoc-formatter/issues/72
    val source =
        """
        /**
         * Summary
         * @param [ param1  ] some value
         * @param[param2] another value
         */
        """
            .trimIndent()
    checkFormatter(
        source,
        KDocFormattingOptions(72),
        """
        /**
         * Summary
         *
         * @param param1 some value
         * @param param2 another value
         */
        """
            .trimIndent(),
    )
  }

  @Test
  fun testMultiLineLink() {
    // Regression test for https://github.com/tnorbye/kdoc-formatter/issues/70
    val source =
        """
        /**
         * Single line is converted {@link foo}
         *
         * Multi line is converted {@link
         * foo}
         *
         * Single line with hash is converted {@link #foo}
         *
         * Multi line with has is converted {@link
         * #foo}
         *
         * Don't interpret {@code
         * # This is not a header
         * * this is
         *   * not a nested list
         * }
         */
        """
            .trimIndent()
    checkFormatter(
        source,
        KDocFormattingOptions(72),
        """
        /**
         * Single line is converted [foo]
         *
         * Multi line is converted [foo]
         *
         * Single line with hash is converted [foo]
         *
         * Multi line with has is converted [foo]
         *
         * Don't interpret {@code # This is not a header * this is * not a
         * nested list }
         */
        """
            .trimIndent(),
        // {@link} text is not rendered by dokka when it cannot resolve the symbols
        verifyDokka = false,
    )
  }

  @Test
  fun testPreformattedWithinCode() {
    // Regression test for https://github.com/tnorbye/kdoc-formatter/issues/77
    val source =
        """
        /**
         * Some summary.
         *  {@code
         *
         * foo < bar?}
         *  Done.
         *
         *
         * {@code
         * ```
         *    Some code.
         * ```
         */
        """
            .trimIndent()
    checkFormatter(
        source,
        KDocFormattingOptions(72),
        """
        /**
         * Some summary. {@code
         *
         * foo < bar?} Done.
         *
         * {@code
         *
         * ```
         *    Some code.
         * ```
         */
        """
            .trimIndent(),
    )
  }

  @Test
  fun testPreStability() {
    // Regression test for https://github.com/tnorbye/kdoc-formatter/issues/78
    val source =
        """
        /**
         * Some summary
         *
         * <pre>
         * line one
         * ```
         *     line two
         * ```
         */
        """
            .trimIndent()
    checkFormatter(
        source,
        KDocFormattingOptions(72),
        """
        /**
         * Some summary
         * <pre>
         * line one
         * ```
         *     line two
         * ```
         */
        """
            .trimIndent(),
    )
  }

  @Test
  fun testPreStability2() {
    // Regression test for https://github.com/tnorbye/kdoc-formatter/issues/78
    // (second scenario
    val source =
        """
        /**
         * Some summary
         *
         * <pre>
         * ```
         *     code
         * ```
         * </pre>
         */
        """
            .trimIndent()
    checkFormatter(
        source,
        KDocFormattingOptions(72),
        """
        /**
         * Some summary
         * <pre>
         * ```
         *     code
         * ```
         * </pre>
         */
        """
            .trimIndent(),
    )
  }

  @Test
  fun testConvertParamReference() {
    // Regression test for https://github.com/tnorbye/kdoc-formatter/issues/79
    val source =
        """
        /**
         * Some summary.
         *
         * Another summary about {@param someParam}.
         */
        """
            .trimIndent()
    checkFormatter(
        source,
        KDocFormattingOptions(72),
        """
        /**
         * Some summary.
         *
         * Another summary about [someParam].
         */
        """
            .trimIndent(),
        // {@param reference} text is not rendered by dokka when it cannot resolve the symbols
        verifyDokka = false,
    )
  }

  @Test
  fun testLineWidth1() {
    // Perform in KDocFileFormatter test too to make sure we properly account
    // for indent!
    val source =
        """
        /**
         * 89 123456789 123456789 123456789 123456789 123456789 123456789 123456789 123456789
         *
         *   10        20        30        40        50        60        70        80
         */
        """
            .trimIndent()
    checkFormatter(
        source,
        KDocFormattingOptions(72),
        """
        /**
         * 89 123456789 123456789 123456789 123456789 123456789 123456789
         * 123456789 123456789
         *
         * 10 20 30 40 50 60 70 80
         */
        """
            .trimIndent(),
    )

    checkFormatter(
        source,
        KDocFormattingOptions(40),
        """
        /**
         * 89 123456789 123456789 123456789
         * 123456789 123456789 123456789
         * 123456789 123456789
         *
         * 10 20 30 40 50 60 70 80
         */
        """
            .trimIndent(),
    )
  }

  @Test
  fun testBlockTagsNoSeparators() {
    checkFormatter(
        """
        /**
         * Marks the given warning as "ignored".
         *
        * @param context The scanning context
        * @param issue the issue to be ignored
        * @param location The location to ignore the warning at, if any
        * @param message The message for the warning
        */
        """
            .trimIndent(),
        KDocFormattingOptions(72),
        """
        /**
         * Marks the given warning as "ignored".
         *
         * @param context The scanning context
         * @param issue the issue to be ignored
         * @param location The location to ignore the warning at, if any
         * @param message The message for the warning
         */
        """
            .trimIndent(),
    )
  }

  @Test
  fun testBlockTagsHangingIndents() {
    val options = KDocFormattingOptions(40)
    options.hangingIndent = 6
    checkFormatter(
        """
        /**
         * Creates a list of class entries from the given class path and specific set of files within
         * it.
         *
         * @param client the client to report errors to and to use to read files
         * @param classFiles the specific set of class files to look for
         * @param classFolders the list of class folders to look in (to determine the package root)
         * @param sort if true, sort the results
         * @return the list of class entries, never null.
         */
        """
            .trimIndent(),
        options,
        """
        /**
         * Creates a list of class entries
         * from the given class path and
         * specific set of files within it.
         *
         * @param client the client to
         *       report errors to and to use
         *       to read files
         * @param classFiles the specific
         *       set of class files to look
         *       for
         * @param classFolders the list of
         *       class folders to look in
         *       (to determine the package
         *       root)
         * @param sort if true, sort the
         *       results
         * @return the list of class
         *       entries, never null.
         */
        """
            .trimIndent(),
    )
  }

  @Test
  fun testGreedyBlockIndent() {
    val options = KDocFormattingOptions(100, 72)
    options.hangingIndent = 6
    checkFormatter(
        """
        /**
         * Returns the project resources, if available
         *
         * @param includeModuleDependencies if true, include merged view of
         *     all module dependencies
         * @param includeLibraries if true, include merged view of all
         *     library dependencies (this also requires all module dependencies)
         * @return the project resources, or null if not available
         */
        """
            .trimIndent(),
        options,
        """
        /**
         * Returns the project resources, if available
         *
         * @param includeModuleDependencies if true, include merged view of all
         *       module dependencies
         * @param includeLibraries if true, include merged view of all library
         *       dependencies (this also requires all module dependencies)
         * @return the project resources, or null if not available
         */
        """
            .trimIndent(),
    )
  }

  @Test
  fun testBlockTagsHangingIndents2() {
    checkFormatter(
        """
        /**
         * @param client the client to
         *     report errors to and to use to
         *     read files
         */
        """
            .trimIndent(),
        KDocFormattingOptions(40),
        """
        /**
         * @param client the client to
         *   report errors to and to use to
         *   read files
         */
        """
            .trimIndent(),
    )
  }

  @Test
  fun testSingleLine() {
    // Also tests punctuation feature.
    val source =
        """
        /**
         * This could all fit on one line
         */
        """
            .trimIndent()
    checkFormatter(
        source,
        KDocFormattingOptions(72),
        """
        /** This could all fit on one line */
        """
            .trimIndent(),
    )
    val options = KDocFormattingOptions(72)
    options.collapseSingleLine = false
    options.addPunctuation = true
    checkFormatter(
        source,
        options,
        """
        /**
         * This could all fit on one line.
         */
        """
            .trimIndent(),
    )
  }

  @Test
  fun testPunctuationWithLabelLink() {
    val source =
        """
        /** Default implementation of [MyInterface] */
        """
            .trimIndent()

    val options = KDocFormattingOptions(72)
    options.addPunctuation = true
    checkFormatter(
        source,
        options,
        """
        /** Default implementation of [MyInterface]. */
        """
            .trimIndent(),
    )
  }

  @Test
  fun testWrappingOfLinkText() {
    val source =
        """
        /**
         * Sometimes the text of a link can have spaces, like [this link's text](https://example.com).
         * The following text should wrap like usual.
         */
        """
            .trimIndent()

    val options = KDocFormattingOptions(72)
    checkFormatter(
        source,
        options,
        """
        /**
         * Sometimes the text of a link can have spaces, like
         * [this link's text](https://example.com). The following text
         * should wrap like usual.
         */
        """
            .trimIndent(),
    )
  }

  @Test
  fun testPreformattedTextIndented() {
    val source =
        """
        /**
         * Parser for the list of forward socket connection returned by the
         * `host:forward-list` command.
         *
         * Input example
         *
         *  ```
         *
         * HT75B1A00212 tcp:51222 tcp:5000 HT75B1A00212 tcp:51227 tcp:5001
         * HT75B1A00212 tcp:51232 tcp:5002 HT75B1A00212 tcp:51239 tcp:5003
         * HT75B1A00212 tcp:51244 tcp:5004
         *
         *  ```
         */
        """
            .trimIndent()
    checkFormatter(
        source,
        KDocFormattingOptions(72, 72).apply { convertMarkup = true },
        source,
        indent = "",
    )
  }

  @Test
  fun testPreformattedText() {
    val source =
        """
        /**
         * Code sample:
         *
         *     val s = "hello, and   this is code so should not be line broken at all, it should stay on one line";
         *     println(s);
         *
         * This is not preformatted and can be combined into multiple sentences again.
         */
        """
            .trimIndent()
    checkFormatter(
        source,
        KDocFormattingOptions(40),
        """
        /**
         * Code sample:
         *
         *     val s = "hello, and   this is code so should not be line broken at all, it should stay on one line";
         *     println(s);
         *
         * This is not preformatted and
         * can be combined into multiple
         * sentences again.
         */
        """
            .trimIndent(),
    )
  }

  @Test
  fun testPreformattedText2() {
    val source =
        """
        /**
         * Code sample:
         * ```kotlin
         * val s = "hello, and this is code so should not be line broken at all, it should stay on one line";
         * println(s);
         * ```
         *
         * This is not preformatted and can be combined into multiple sentences again.
         */
        """
            .trimIndent()
    checkFormatter(
        source,
        KDocFormattingOptions(40),
        """
        /**
         * Code sample:
         * ```kotlin
         * val s = "hello, and this is code so should not be line broken at all, it should stay on one line";
         * println(s);
         * ```
         *
         * This is not preformatted and
         * can be combined into multiple
         * sentences again.
         */
        """
            .trimIndent(),
    )
  }

  @Test
  fun testPreformattedText3() {
    val source =
        """
        /**
         * Code sample:
         * <PRE>
         *     val s = "hello, and   this is code so should not be line broken at all, it should stay on one line";
         *     println(s);
         * </pre>
         * This is not preformatted and can be combined into multiple sentences again.
         */
        """
            .trimIndent()
    checkFormatter(
        source,
        KDocFormattingOptions(40),
        """
        /**
         * Code sample:
         * ```
         *     val s = "hello, and   this is code so should not be line broken at all, it should stay on one line";
         *     println(s);
         * ```
         *
         * This is not preformatted and
         * can be combined into multiple
         * sentences again.
         */
        """
            .trimIndent(),
        // <pre> and ``` are rendered differently; this is an intentional diff
        verifyDokka = false,
    )
  }

  @Test
  fun testPreformattedTextWithBlankLines() {
    val source =
        """
        /**
         * Code sample:
         * ```kotlin
         * val s = "hello, and this is code so should not be line broken at all, it should stay on one line";
         *
         * println(s);
         * ```
         */
        """
            .trimIndent()
    checkFormatter(
        source,
        KDocFormattingOptions(40),
        """
        /**
         * Code sample:
         * ```kotlin
         * val s = "hello, and this is code so should not be line broken at all, it should stay on one line";
         *
         * println(s);
         * ```
         */
        """
            .trimIndent(),
    )
  }

  @Test
  fun testPreformattedTextWithBlankLinesAndTrailingSpaces() {
    val source =
        """
        /**
         * Code sample:
         * ```kotlin
         * val s = "hello, and this is code so should not be line broken at all, it should stay on one line";
         *
         * println(s);
         * ```
         */
        """
            .trimIndent()
    checkFormatter(
        source,
        KDocFormattingOptions(40),
        """
        /**
         * Code sample:
         * ```kotlin
         * val s = "hello, and this is code so should not be line broken at all, it should stay on one line";
         *
         * println(s);
         * ```
         */
        """
            .trimIndent(),
    )
  }

  @Test
  fun testPreformattedTextSeparation() {
    val source =
        """
        /**
         * For example,
         *
         *     val s = "hello, and   this is code so should not be line broken at all, it should stay on one line";
         *     println(s);
         * And here's another example:
         *     This is not preformatted text.
         *
         * And a third example,
         *
         * ```
         * Preformatted.
         * ```
         */
        """
            .trimIndent()
    checkFormatter(
        source,
        KDocFormattingOptions(40),
        """
        /**
         * For example,
         *
         *     val s = "hello, and   this is code so should not be line broken at all, it should stay on one line";
         *     println(s);
         *
         * And here's another example: This
         * is not preformatted text.
         *
         * And a third example,
         * ```
         * Preformatted.
         * ```
         */
        """
            .trimIndent(),
    )
  }

  @Test
  fun testSeparateParagraphMarkers1() {
    // If the markup still contains HTML paragraph separators, separate
    // paragraphs
    val source =
        """
        /**
         * Here's paragraph 1.
         *
         * And here's paragraph 2.
         * <p>And here's paragraph 3.
         * <P/>And here's paragraph 4.
         */
        """
            .trimIndent()
    checkFormatter(
        source,
        KDocFormattingOptions(40).apply { convertMarkup = true },
        """
        /**
         * Here's paragraph 1.
         *
         * And here's paragraph 2.
         *
         * And here's paragraph 3.
         *
         * And here's paragraph 4.
         */
        """
            .trimIndent(),
    )
  }

  @Test
  fun testSeparateParagraphMarkers2() {
    // From ktfmt Tokenizer.kt
    val source =
        """
        /**
         * Tokenizer traverses a Kotlin parse tree (which blessedly contains whitespaces and comments,
         * unlike Javac) and constructs a list of 'Tok's.
         *
         * <p>The google-java-format infra expects newline Toks to be separate from maximal-whitespace Toks,
         * but Kotlin emits them together. So, we split them using Java's \R regex matcher. We don't use
         * 'split' et al. because we want Toks for the newlines themselves.
         */
        """
            .trimIndent()
    checkFormatter(
        source,
        KDocFormattingOptions(100, 100).apply {
          convertMarkup = true
          optimal = false
        },
        """
        /**
         * Tokenizer traverses a Kotlin parse tree (which blessedly contains whitespaces and comments,
         * unlike Javac) and constructs a list of 'Tok's.
         *
         * The google-java-format infra expects newline Toks to be separate from maximal-whitespace Toks,
         * but Kotlin emits them together. So, we split them using Java's \R regex matcher. We don't use
         * 'split' et al. because we want Toks for the newlines themselves.
         */
        """
            .trimIndent(),
        indent = "",
    )
  }

  @Test
  fun testConvertMarkup() {
    // If the markup still contains HTML paragraph separators, separate
    // paragraphs
    val source =
        """
        /**
         * This is <b>bold</b>, this is <i>italics</i>, but nothing
         * should be converted in `<b>code</b>` or in
         * ```
         * <i>preformatted text</i>
         * ```
         * And this \` is <b>not code and should be converted</b>.
         */
        """
            .trimIndent()
    checkFormatter(
        source,
        KDocFormattingOptions(40).apply { convertMarkup = true },
        """
        /**
         * This is **bold**, this is
         * *italics*, but nothing should be
         * converted in `<b>code</b>` or in
         *
         * ```
         * <i>preformatted text</i>
         * ```
         *
         * And this \` is **not code and
         * should be converted**.
         */
        """
            .trimIndent(),
    )
  }

  @Test
  fun testFormattingList() {
    val source =
        """
        /**
         * 1. This is a numbered list.
         * 2. This is another item. We should be wrapping extra text under the same item.
         * 3. This is the third item.
         *
         * Unordered list:
         * * First
         * * Second
         * * Third
         *
         * Other alternatives:
         * - First
         * - Second
         */
        """
            .trimIndent()
    checkFormatter(
        source,
        KDocFormattingOptions(40),
        """
        /**
         * 1. This is a numbered list.
         * 2. This is another item. We
         *    should be wrapping extra text
         *    under the same item.
         * 3. This is the third item.
         *
         * Unordered list:
         * * First
         * * Second
         * * Third
         *
         * Other alternatives:
         * - First
         * - Second
         */
        """
            .trimIndent(),
    )
  }

  @Test
  fun testList1() {
    val source =
        """
        /**
         *  * pre.errorlines: General > Text > Default Text
         *  * .prefix: XML > Namespace Prefix
         *  * .attribute: XML > Attribute name
         *  * .value: XML > Attribute value
         *  * .tag: XML > Tag name
         *  * .lineno: For color, General > Code > Line number, Foreground, and for background-color,
         *  Editor > Gutter background
         *  * .error: General > Errors and Warnings > Error
         */
        """
            .trimIndent()
    checkFormatter(
        source,
        KDocFormattingOptions(40),
        """
        /**
         * * pre.errorlines: General >
         *   Text > Default Text
         * * .prefix: XML > Namespace Prefix
         * * .attribute: XML > Attribute
         *   name
         * * .value: XML > Attribute value
         * * .tag: XML > Tag name
         * * .lineno: For color, General >
         *   Code > Line number, Foreground,
         *   and for background-color,
         *   Editor > Gutter background
         * * .error: General > Errors and
         *   Warnings > Error
         */
        """
            .trimIndent(),
    )
  }

  @Test
  fun testIndentedList() {
    val source =
        """
        /**
        * Basic usage:
        *   1. Create a configuration via [UastEnvironment.Configuration.create] and mutate it as needed.
        *   2. Create a project environment via [UastEnvironment.create].
        *      You can create multiple environments in the same process (one for each "module").
        *   3. Call [analyzeFiles] to initialize PSI machinery and precompute resolve information.
        */
        """
            .trimIndent()
    checkFormatter(
        source,
        KDocFormattingOptions(40),
        """
        /**
         * Basic usage:
         * 1. Create a configuration via
         *    [UastEnvironment.Configuration.create]
         *    and mutate it as needed.
         * 2. Create a project environment
         *    via [UastEnvironment.create].
         *    You can create multiple
         *    environments in the same
         *    process (one for each
         *    "module").
         * 3. Call [analyzeFiles] to
         *    initialize PSI machinery and
         *    precompute resolve
         *    information.
         */
        """
            .trimIndent(),
    )
  }

  @Test
  fun testDocTags() {
    val source =
        """
        /**
         * @param configuration the configuration to look up which issues are
         * enabled etc from
         * @param platforms the platforms applying to this analysis
         */
        """
            .trimIndent()
    checkFormatter(
        source,
        KDocFormattingOptions(40),
        """
        /**
         * @param configuration the
         *   configuration to look up which
         *   issues are enabled etc from
         * @param platforms the platforms
         *   applying to this analysis
         */
        """
            .trimIndent(),
    )
  }

  @Test
  fun testAtInMiddle() {
    val source =
        """
        /**
         * If non-null, this issue can **only** be suppressed with one of the
         * given annotations: not with @Suppress, not with @SuppressLint, not
         * with lint.xml, not with lintOptions{} and not with baselines.
         *
         * Test @IntRange and @FloatRange support annotation applied to
         * arrays and vargs.
         */
        """
            .trimIndent()
    checkFormatter(
        source,
        KDocFormattingOptions(72),
        """
        /**
         * If non-null, this issue can **only** be suppressed with
         * one of the given annotations: not with @Suppress, not
         * with @SuppressLint, not with lint.xml, not with lintOptions{} and
         * not with baselines.
         *
         * Test @IntRange and @FloatRange support annotation applied to
         * arrays and vargs.
         */
        """
            .trimIndent(),
    )
  }

  @Test
  fun testMaxCommentWidth() {
    checkFormatter(
        """
        /**
        * Returns whether lint should check all warnings,
         * including those off by default, or null if
         *not configured in this configuration. This is a really really really long sentence which needs to be broken up.
         * This is a separate section
         * which should be flowed together with the first one.
         * *bold* should not be removed even at beginning.
         */
        """
            .trimIndent(),
        KDocFormattingOptions(maxLineWidth = 100, maxCommentWidth = 30),
        """
        /**
         * Returns whether lint should
         * check all warnings, including
         * those off by default, or
         * null if not configured in
         * this configuration. This is
         * a really really really long
         * sentence which needs to be
         * broken up. This is a separate
         * section which should be flowed
         * together with the first one.
         * *bold* should not be removed
         * even at beginning.
         */
        """
            .trimIndent(),
    )
  }

  @Test
  fun testHorizontalRuler() {
    checkFormatter(
        """
        /**
        * This is a header. Should appear alone.
        * --------------------------------------
        *
        * This should not be on the same line as the header.
         */
        """
            .trimIndent(),
        KDocFormattingOptions(maxLineWidth = 100, maxCommentWidth = 30),
        """
        /**
         * This is a header. Should
         * appear alone.
         * --------------------------------------
         * This should not be on the same
         * line as the header.
         */
        """
            .trimIndent(),
        verifyDokka = false,
    )
  }

  @Test
  fun testQuoteOnlyOnFirstLine() {
    checkFormatter(
        """
        /**
        * More:
        * > This whole paragraph should be treated as a block quote.
        * This whole paragraph should be treated as a block quote.
        * This whole paragraph should be treated as a block quote.
        * This whole paragraph should be treated as a block quote.
        * @sample Sample
             */
        """
            .trimIndent(),
        KDocFormattingOptions(maxLineWidth = 100, maxCommentWidth = 30),
        """
        /**
         * More:
         * > This whole paragraph should
         * > be treated as a block quote.
         * > This whole paragraph should
         * > be treated as a block quote.
         * > This whole paragraph should
         * > be treated as a block quote.
         * > This whole paragraph should
         * > be treated as a block quote.
         *
         * @sample Sample
         */
        """
            .trimIndent(),
    )
  }

  @Test
  fun testNoBreakUrl() {
    checkFormatter(
        """
        /**
         *  # Design
         *  The splash screen icon uses the same specifications as
         *  [Adaptive Icons](https://developer.android.com/guide/practices/ui_guidelines/icon_design_adaptive)
         */
        """
            .trimIndent(),
        KDocFormattingOptions(maxLineWidth = 100, maxCommentWidth = 100),
        """
        /**
         * # Design
         * The splash screen icon uses the same specifications as
         * [Adaptive Icons](https://developer.android.com/guide/practices/ui_guidelines/icon_design_adaptive)
         */
        """
            .trimIndent(),
    )
  }

  @Test
  fun testMaxCommentWidthDefaultsToMaxLineWidth() {
    // GH#594: maxCommentWidth should default to maxLineWidth, not min(maxLineWidth, 72).
    // When only maxLineWidth is set to 100, comments should wrap at 100, not 72.
    checkFormatter(
        """
        /**
         * This is a long comment that should not be wrapped at 72 characters. It should wrap at 100 characters instead.
         */
        """
            .trimIndent(),
        KDocFormattingOptions(100),
        """
        /**
         * This is a long comment that should not be wrapped at 72 characters. It should wrap at 100
         * characters instead.
         */
        """
            .trimIndent(),
    )
  }

  @Test
  fun testAsciiArt() {
    // Comment from
    // https://cs.android.com/android-studio/platform/tools/base/+/mirror-goog-studio-master-dev:build-system/integration-test/application/src/test/java/com/android/build/gradle/integration/bundle/DynamicFeatureAndroidTestBuildTest.kt
    checkFormatter(
        """
        /**
         *       Base <------------ Middle DF <------------- DF <--------- Android Test DF
         *      /    \              /       \                |               /        \   \
         *     v      v            v         v               v              v          \   \
         *  appLib  sharedLib   midLib   sharedMidLib    featureLib    testFeatureLib   \   \
         *              ^                      ^_______________________________________/   /
         *              |________________________________________________________________/
         *
         *  DF has a feature-on-feature dep on Middle DF, both depend on Base, Android Test DF is an
         *  android test variant for DF.
         *
         *  Base depends on appLib and sharedLib.
         *  Middle DF depends on midLib and sharedMidLib.
         *  DF depends on featureLib.
         *  DF also has an android test dependency on testFeatureLib, shared and sharedMidLib.
         */
        """
            .trimIndent(),
        KDocFormattingOptions(maxLineWidth = 100, maxCommentWidth = 30),
        """
        /**
         *       Base <------------ Middle DF <------------- DF <--------- Android Test DF
         *      /    \              /       \                |               /        \   \
         *     v      v            v         v               v              v          \   \
         *  appLib  sharedLib   midLib   sharedMidLib    featureLib    testFeatureLib   \   \
         *              ^                      ^_______________________________________/   /
         *              |________________________________________________________________/
         *
         * DF has a feature-on-feature
         * dep on Middle DF, both depend
         * on Base, Android Test DF is an
         * android test variant for DF.
         *
         * Base depends on appLib and
         * sharedLib. Middle DF depends
         * on midLib and sharedMidLib. DF
         * depends on featureLib. DF also
         * has an android test dependency
         * on testFeatureLib, shared and
         * sharedMidLib.
         */
        """
            .trimIndent(),
    )
  }

  @Test
  fun testAsciiArt2() {
    checkFormatter(
        """
        /**
         *                 +-> lib1
         *                 |
         *     feature1 ---+-> javalib1
         *                 |
         *                 +-> baseModule
         */
        """
            .trimIndent(),
        KDocFormattingOptions(maxLineWidth = 100, maxCommentWidth = 30),
        """
        /**
         *                 +-> lib1
         *                 |
         *     feature1 ---+-> javalib1
         *                 |
         *                 +-> baseModule
         */
        """
            .trimIndent(),
    )
  }

  @Test
  fun testAsciiArt3() {
    val source =
        """
        /**
         * This test creates a layout of this shape:
         *
         *  ---------------
         *  | t      |    |
         *  |        |    |
         *  |  |-------|  |
         *  |  | t     |  |
         *  |  |       |  |
         *  |  |       |  |
         *  |--|  |-------|
         *  |  |  | t     |
         *  |  |  |       |
         *  |  |  |       |
         *  |  |--|       |
         *  |     |       |
         *  ---------------
         *
         * There are 3 staggered children and 3 pointers, the first is on child 1, the second is on
         * child 2 in a space that overlaps child 1, and the third is in a space in child 3 that
         * overlaps child 2.
         */
        """
            .trimIndent()
    checkFormatter(
        source,
        KDocFormattingOptions(maxLineWidth = 100, maxCommentWidth = 30),
        """
        /**
         * This test creates a layout of
         * this shape:
         * ---------------
         * | t | | | | | | |-------| | |
         * | t | | | | | | | | | | |--|
         * |-------| | | | t | | | | | |
         * | | | | |--| | | | |
         * ---------------
         * There are 3 staggered children
         * and 3 pointers, the first is
         * on child 1, the second is
         * on child 2 in a space that
         * overlaps child 1, and the
         * third is in a space in child
         * 3 that overlaps child 2.
         */
        """
            .trimIndent(),
        indent = "",
    )
  }

  @Test
  fun testBrokenAsciiArt() {
    // The first illustration has indentation 3, not 4, so isn't preformatted.
    // The formatter will garble this -- but so will Dokka!
    // From androidx' TwoDimensionalFocusTraversalOutTest.kt
    checkFormatter(
        """
        /**
         *    ___________________________
         *   |  grandparent             |
         *   |   _____________________  |
         *   |  |  parent            |  |
         *   |  |   _______________  |  |   ____________
         *   |  |  | focusedItem  |  |  |  | nextItem  |
         *   |  |  |______________|  |  |  |___________|
         *   |  |____________________|  |
         *   |__________________________|
         *
         *      __________________________
         *     |  grandparent            |
         *     |   ____________________  |
         *     |  |  parent           |  |
         *     |  |   ______________  |  |
         *     |  |  | focusedItem |  |  |
         *     |  |  |_____________|  |  |
         *     |  |___________________|  |
         *     |_________________________|
         */
        """
            .trimIndent(),
        KDocFormattingOptions(maxLineWidth = 100, 100),
        """
        /**
         * ___________________________ | grandparent | | _____________________ | | | parent
         * | | | | _______________ | | ____________ | | | focusedItem | | | | nextItem | | |
         * |______________| | | |___________| | |____________________| | |__________________________|
         *
         *      __________________________
         *     |  grandparent            |
         *     |   ____________________  |
         *     |  |  parent           |  |
         *     |  |   ______________  |  |
         *     |  |  | focusedItem |  |  |
         *     |  |  |_____________|  |  |
         *     |  |___________________|  |
         *     |_________________________|
         */
        """
            .trimIndent(),
        verifyDokka = false,
    )
  }

  @Test
  fun testHtmlLists() {
    checkFormatter(
        """
        /**
         * <ul>
         *   <li>Incremental merge will never clean the output.
         *   <li>The inputs must be able to tell which changes to relative files have been made.
         *   <li>Intermediate state must be saved between merges.
         * </ul>
         */
        """
            .trimIndent(),
        KDocFormattingOptions(maxLineWidth = 100, maxCommentWidth = 60),
        """
        /**
         * <ul>
         * <li>Incremental merge will never clean the output.
         * <li>The inputs must be able to tell which changes to
         *   relative files have been made.
         * <li>Intermediate state must be saved between merges.
         * </ul>
         */
        """
            .trimIndent(),
    )
  }

  @Test
  fun testVariousMarkup() {
    val source =
        """
        /**
         * This document contains a bunch of markup examples
         * that I will use
         * to verify that things are handled
         * correctly via markdown.
         *
         * This is a header. Should appear alone.
         * --------------------------------------
         * This should not be on the same line as the header.
         *
         * This is a header. Should appear alone.
         * -
         * This should not be on the same line as the header.
         *
         * This is a header. Should appear alone.
         * ======================================
         * This should not be on the same line as the header.
         *
         * This is a header. Should appear alone.
         * =
         * This should not be on the same line as the header.
         * Note that we don't treat this as a header
         * because it's not on its own line. Instead
         * it's considered a separating line.
         * ---
         * More text. Should not be on the previous line.
         *
         * --- This usage of --- where it's not on its own
         * line should not be used as a header or separator line.
         *
         * List stuff:
         * 1. First item
         * 2. Second item
         * 3. Third item
         *
         * # Text styles #
         * **Bold**, *italics*. \*Not italics\*.
         *
         * ## More text styles
         * ~~strikethrough~~, _underlined_.
         *
         * ### Blockquotes #
         *
         * More:
         * > This whole paragraph should be treated as a block quote.
         * This whole paragraph should be treated as a block quote.
         * This whole paragraph should be treated as a block quote.
         * This whole paragraph should be treated as a block quote.
         *
         * ### Lists
         * Plus lists:
         * + First
         * + Second
         * + Third
         *
         * Dash lists:
         * - First
         * - Second
         * - Third
         *
         * List items with multiple paragraphs:
         *
         * * This is my list item. It has
         *   text on many lines.
         *
         *   This is a continuation of the first bullet.
         * * And this is the second.
         *
         * ### Code blocks in list items
         *
         * Escapes: I should look for cases where I place a number followed
         * by a period (or asterisk) at the beginning of a line and if so,
         * escape it:
         *
         * The meaning of life:
         * 42\. This doesn't seem to work in IntelliJ's markdown formatter.
         *
         * ### Horizontal rules
         * *********
         * ---------
         * ***
         * * * *
         * - - -
         */
        """
            .trimIndent()
    checkFormatter(
        source,
        KDocFormattingOptions(100, 100),
        """
        /**
         * This document contains a bunch of markup examples that I will use to verify that things are
         * handled correctly via markdown.
         *
         * This is a header. Should appear alone.
         * --------------------------------------
         * This should not be on the same line as the header.
         *
         * This is a header. Should appear alone.
         * -
         * This should not be on the same line as the header.
         *
         * This is a header. Should appear alone.
         * ======================================
         * This should not be on the same line as the header.
         *
         * This is a header. Should appear alone.
         * =
         * This should not be on the same line as the header. Note that we don't treat this as a header
         * because it's not on its own line. Instead it's considered a separating line.
         * ---
         * More text. Should not be on the previous line.
         *
         * --- This usage of --- where it's not on its own line should not be used as a header or
         * separator line.
         *
         * List stuff:
         * 1. First item
         * 2. Second item
         * 3. Third item
         *
         * # Text styles #
         * **Bold**, *italics*. \*Not italics\*.
         *
         * ## More text styles
         * ~~strikethrough~~, _underlined_.
         *
         * ### Blockquotes #
         *
         * More:
         * > This whole paragraph should be treated as a block quote. This whole paragraph should be
         * > treated as a block quote. This whole paragraph should be treated as a block quote. This whole
         * > paragraph should be treated as a block quote.
         *
         * ### Lists
         * Plus lists:
         * + First
         * + Second
         * + Third
         *
         * Dash lists:
         * - First
         * - Second
         * - Third
         *
         * List items with multiple paragraphs:
         *
         * * This is my list item. It has text on many lines.
         *
         *   This is a continuation of the first bullet.
         * * And this is the second.
         *
         * ### Code blocks in list items
         *
         * Escapes: I should look for cases where I place a number followed by a period (or asterisk) at
         * the beginning of a line and if so, escape it:
         *
         * The meaning of life: 42\. This doesn't seem to work in IntelliJ's markdown formatter.
         *
         * ### Horizontal rules
         * *********
         * ---------
         * ***
         * * * *
         * - - -
         */
        """
            .trimIndent(),
    )
  }

  @Test
  fun testLineComments() {
    val source =
        """
        //
        // Information about a request to run lint.
        //
        // **NOTE: This is not a public or final API; if you rely on this be prepared
        // to adjust your code for the next tools release.**
        //
        """
            .trimIndent()
    checkFormatter(
        source,
        KDocFormattingOptions(40),
        """
        // Information about a request to
        // run lint.
        //
        // **NOTE: This is not a public or
        // final API; if you rely on this be
        // prepared to adjust your code for
        // the next tools release.**
        """
            .trimIndent(),
    )
  }

  @Test
  fun testMoreLineComments() {
    val source =
        """
        // Do not clean
        // this
        """
            .trimIndent()
    checkFormatter(
        source,
        KDocFormattingOptions(70),
        """
        // Do not clean this
        """
            .trimIndent(),
    )
  }

  @Test
  fun testListContinuations() {
    val source =
        """
        /**
         * * This is my list item. It has
         *   text on many lines.
         *
         *   This is a continuation of the first bullet.
         * * And this is the second.
         */
        """
            .trimIndent()
    checkFormatter(
        source,
        KDocFormattingOptions(40),
        """
        /**
         * * This is my list item. It has
         *   text on many lines.
         *
         *   This is a continuation of the
         *   first bullet.
         * * And this is the second.
         */
        """
            .trimIndent(),
    )
  }

  @Test
  fun testListContinuations2() {
    val source =
        "/**\n" +
            """
            List items with multiple paragraphs:

            * This is my list item. It has
              text on many lines.

              This is a continuation of the first bullet.
            * And this is the second.
            """
                .trimIndent()
                .split("\n")
                .joinToString(separator = "\n") { " * $it".trimEnd() } +
            "\n */"

    checkFormatter(
        source,
        KDocFormattingOptions(100),
        """
        /**
         * List items with multiple paragraphs:
         *
         * * This is my list item. It has text on many lines.
         *
         *   This is a continuation of the first bullet.
         * * And this is the second.
         */
        """
            .trimIndent(),
    )
  }

  @Test
  fun testAccidentalHeader() {
    val source =
        """
        /**
        * Constructs a simplified version of the internal JVM description of the given method. This is
        * in the same format as {@link #getMethodDescription} above, the difference being we don't have
        * the actual PSI for the method type, we just construct the signature from the [method] name,
        * the list of [argumentTypes] and optionally include the [returnType].
        */
        """
            .trimIndent()
    checkFormatter(
        source,
        KDocFormattingOptions(72),
        // Note how this places the "#" in column 0 which will then
        // be re-interpreted as a header next time we format it!
        // Idea: @{link #} should become {@link#} or with a nbsp;
        """
        /**
         * Constructs a simplified version of the internal JVM
         * description of the given method. This is in the same format as
         * [getMethodDescription] above, the difference being we don't
         * have the actual PSI for the method type, we just construct the
         * signature from the [method] name, the list of [argumentTypes] and
         * optionally include the [returnType].
         */
        """
            .trimIndent(),
        // {@link} text is not rendered by dokka when it cannot resolve the symbols
        verifyDokka = false,
    )
  }

  @Test
  fun testTODO() {
    val source =
        """
        /**
         * Adds the given dependency graph (the output of the Gradle dependency task)
         * to be constructed when mocking a Gradle model for this project.
         * <p>
         * To generate this, run for example
         * <pre>
         *     ./gradlew :app:dependencies
         * </pre>
         * and then look at the debugCompileClasspath (or other graph that you want
         * to model).
         * TODO: Adds the given dependency graph (the output of the Gradle dependency task)
         * to be constructed when mocking a Gradle model for this project.
         * TODO: More stuff to do here
         * @param dependencyGraph the graph description
         * @return this for constructor chaining
         * TODO: Consider looking at the localization="suggested" attribute in
         * the platform attrs.xml to catch future recommended attributes.
         * TODO: Also adds the given dependency graph (the output of the Gradle dependency task)
         * to be constructed when mocking a Gradle model for this project.
         * TODO(b/144576310): Cover multi-module search.
         *  Searching in the search bar should show an option to change module if there are resources in it.
         * TODO(myldap): Cover filter usage. Eg: Look for a framework resource by enabling its filter.
         */
        """
            .trimIndent()
    checkFormatter(
        source,
        KDocFormattingOptions(72).apply { orderDocTags = true },
        // Note how this places the "#" in column 0 which will then
        // be re-interpreted as a header next time we format it!
        // Idea: @{link #} should become {@link#} or with a nbsp;
        """
        /**
         * Adds the given dependency graph (the output of the Gradle
         * dependency task) to be constructed when mocking a Gradle model
         * for this project.
         *
         * To generate this, run for example
         *
         * ```
         *     ./gradlew :app:dependencies
         * ```
         *
         * and then look at the debugCompileClasspath (or other graph that
         * you want to model).
         *
         * @param dependencyGraph the graph description
         * @return this for constructor chaining
         *
         * TODO: Adds the given dependency graph (the output of the Gradle
         *   dependency task) to be constructed when mocking a Gradle model
         *   for this project.
         * TODO: More stuff to do here
         * TODO: Consider looking at the localization="suggested" attribute
         *   in the platform attrs.xml to catch future recommended
         *   attributes.
         * TODO: Also adds the given dependency graph (the output of the
         *   Gradle dependency task) to be constructed when mocking a Gradle
         *   model for this project.
         * TODO(b/144576310): Cover multi-module search. Searching in the
         *   search bar should show an option to change module if there are
         *   resources in it.
         * TODO(myldap): Cover filter usage. Eg: Look for a framework
         *   resource by enabling its filter.
         */
        """
            .trimIndent(),
        // We indent TO-DO text deliberately, though this changes the structure to
        // make each item have its own paragraph which doesn't happen by default.
        // Working as intended.
        verifyDokka = false,
    )
  }

  @Test
  fun testReorderTags() {
    val source =
        """
        /**
         * Constructs a new location range for the given file, from start to
         * end. If the length of the range is not known, end may be null.
         *
         * @return Something
         * @sample Other
         * @param file the associated file (but see the documentation for
         * [Location.file] for more information on what the file
         * represents)
         *
         * @param end the ending position, or null
         * @param[ start ]   the starting position, or null
         * @see More
         */
        """
            .trimIndent()
    checkFormatter(
        FormattingTask(
            KDocFormattingOptions(72),
            source,
            "    ",
            orderedParameterNames = listOf("file", "start", "end"),
        ),
        // Note how this places the "#" in column 0 which will then
        // be re-interpreted as a header next time we format it!
        // Idea: @{link #} should become {@link#} or with a nbsp;
        """
        /**
         * Constructs a new location range for the given file, from start to
         * end. If the length of the range is not known, end may be null.
         *
         * @param file the associated file (but see the documentation for
         *   [Location.file] for more information on what the file
         *   represents)
         * @param start the starting position, or null
         * @param end the ending position, or null
         * @return Something
         * @sample Other
         * @see More
         */
        """
            .trimIndent(),
    )
  }

  @Test
  fun testNoReorderSample() {
    val source =
        """
        /**
         * Constructs a new location range for the given file, from start to
         * end. If the length of the range is not known, end may be null.
         *
         * @sample abc
         *
         * You might want to see another sample.
         *
         * @sample xyz
         *
         * Makes sense?
         * @return Something
         * @see more
         * @sample foo
         *
         * Note that samples after another tag don't get special treatment.
         */
        """
            .trimIndent()
    checkFormatter(
        FormattingTask(
            KDocFormattingOptions(72),
            source,
            "    ",
            orderedParameterNames = listOf("file", "start", "end"),
        ),
        """
        /**
         * Constructs a new location range for the given file, from start to
         * end. If the length of the range is not known, end may be null.
         *
         * @sample abc
         *
         * You might want to see another sample.
         *
         * @sample xyz
         *
         * Makes sense?
         *
         * @return Something
         * @sample foo
         *
         * Note that samples after another tag don't get special treatment.
         *
         * @see more
         */
        """
            .trimIndent(),
    )
  }

  @Test
  fun testKDocOrdering() {
    // From AndroidX'
    // frameworks/support/biometric/biometric-ktx/src/main/java/androidx/biometric/auth/CredentialAuthExtensions.kt
    val source =
        """
        /**
         * Shows an authentication prompt to the user.
         *
         * @param host A wrapper for the component that will host the prompt.
         * @param crypto A cryptographic object to be associated with this authentication.
         *
         * @return [AuthenticationResult] for a successful authentication.
         *
         * @throws AuthPromptErrorException  when an unrecoverable error has been encountered and
         * authentication has stopped.
         * @throws AuthPromptFailureException when an authentication attempt by the user has been rejected.
         *
         * @see CredentialAuthPrompt.authenticate(
         *     AuthPromptHost host,
         *     BiometricPrompt.CryptoObject,
         *     AuthPromptCallback
         * )
         *
         * @sample androidx.biometric.samples.auth.credentialAuth
         */
        """
            .trimIndent()
    checkFormatter(
        source,
        KDocFormattingOptions(72, 72),
        """
        /**
         * Shows an authentication prompt to the user.
         *
         * @param host A wrapper for the component that will host the prompt.
         * @param crypto A cryptographic object to be associated with this
         *   authentication.
         * @return [AuthenticationResult] for a successful authentication.
         * @throws AuthPromptErrorException when an unrecoverable error has been
         *   encountered and authentication has stopped.
         * @throws AuthPromptFailureException when an authentication attempt by
         *   the user has been rejected.
         * @sample androidx.biometric.samples.auth.credentialAuth
         * @see CredentialAuthPrompt.authenticate( AuthPromptHost host,
         *   BiometricPrompt.CryptoObject, AuthPromptCallback )
         */
        """
            .trimIndent(),
        indent = "",
    )
  }

  @Test
  fun testHtml() {
    // Comment from lint's SourceCodeScanner class doc. Tests a number of
    // things -- markup conversion (<h2> to ##, <p> to blank lines), list item
    // indentation, trimming blank lines from the end, etc.
    val source =
        """
        /**
        * Interface to be implemented by lint detectors that want to analyze
        * Java source files (or other similar source files, such as Kotlin files.)
        * <p>
        * There are several different common patterns for detecting issues:
        * <ul>
        * <li> Checking calls to a given method. For this see
        * {@link #getApplicableMethodNames()} and
        * {@link #visitMethodCall(JavaContext, UCallExpression, PsiMethod)}</li>
        * <li> Instantiating a given class. For this, see
        * {@link #getApplicableConstructorTypes()} and
        * {@link #visitConstructor(JavaContext, UCallExpression, PsiMethod)}</li>
        * <li> Referencing a given constant. For this, see
        * {@link #getApplicableReferenceNames()} and
        * {@link #visitReference(JavaContext, UReferenceExpression, PsiElement)}</li>
        * <li> Extending a given class or implementing a given interface.
        * For this, see {@link #applicableSuperClasses()} and
        * {@link #visitClass(JavaContext, UClass)}</li>
        * <li> More complicated scenarios: perform a general AST
        * traversal with a visitor. In this case, first tell lint which
        * AST node types you're interested in with the
        * {@link #getApplicableUastTypes()} method, and then provide a
        * {@link UElementHandler} from the {@link #createUastHandler(JavaContext)}
        * where you override the various applicable handler methods. This is
        * done rather than a general visitor from the root node to avoid
        * having to have every single lint detector (there are hundreds) do a full
        * tree traversal on its own.</li>
        * </ul>
        * <p>
        * {@linkplain SourceCodeScanner} exposes the UAST API to lint checks.
        * UAST is short for "Universal AST" and is an abstract syntax tree library
        * which abstracts away details about Java versus Kotlin versus other similar languages
        * and lets the client of the library access the AST in a unified way.
        * <p>
        * UAST isn't actually a full replacement for PSI; it <b>augments</b> PSI.
        * Essentially, UAST is used for the <b>inside</b> of methods (e.g. method bodies),
        * and things like field initializers. PSI continues to be used at the outer
        * level: for packages, classes, and methods (declarations and signatures).
        * There are also wrappers around some of these for convenience.
        * <p>
        * The {@linkplain SourceCodeScanner} interface reflects this fact. For example,
        * when you indicate that you want to check calls to a method named {@code foo},
        * the call site node is a UAST node (in this case, {@link UCallExpression},
        * but the called method itself is a {@link PsiMethod}, since that method
        * might be anywhere (including in a library that we don't have source for,
        * so UAST doesn't make sense.)
        * <p>
        * <h2>Migrating JavaPsiScanner to SourceCodeScanner</h2>
        * As described above, PSI is still used, so a lot of code will remain the
        * same. For example, all resolve methods, including those in UAST, will
        * continue to return PsiElement, not necessarily a UElement. For example,
        * if you resolve a method call or field reference, you'll get a
        * {@link PsiMethod} or {@link PsiField} back.
        * <p>
        * However, the visitor methods have all changed, generally to change
        * to UAST types. For example, the signature
        * {@link JavaPsiScanner#visitMethodCall(JavaContext, JavaElementVisitor, PsiMethodCallExpression, PsiMethod)}
        * should be changed to {@link SourceCodeScanner#visitMethodCall(JavaContext, UCallExpression, PsiMethod)}.
        * <p>
        * Similarly, replace {@link JavaPsiScanner#createPsiVisitor} with {@link SourceCodeScanner#createUastHandler},
        * {@link JavaPsiScanner#getApplicablePsiTypes()} with {@link SourceCodeScanner#getApplicableUastTypes()}, etc.
        * <p>
        * There are a bunch of new methods on classes like {@link JavaContext} which lets
        * you pass in a {@link UElement} to match the existing {@link PsiElement} methods.
        * <p>
        * If you have code which does something specific with PSI classes,
        * the following mapping table in alphabetical order might be helpful, since it lists the
        * corresponding UAST classes.
        * <table>
        *     <caption>Mapping between PSI and UAST classes</caption>
        *     <tr><th>PSI</th><th>UAST</th></tr>
        *     <tr><th>com.intellij.psi.</th><th>org.jetbrains.uast.</th></tr>
        *     <tr><td>IElementType</td><td>UastBinaryOperator</td></tr>
        *     <tr><td>PsiAnnotation</td><td>UAnnotation</td></tr>
        *     <tr><td>PsiAnonymousClass</td><td>UAnonymousClass</td></tr>
        *     <tr><td>PsiArrayAccessExpression</td><td>UArrayAccessExpression</td></tr>
        *     <tr><td>PsiBinaryExpression</td><td>UBinaryExpression</td></tr>
        *     <tr><td>PsiCallExpression</td><td>UCallExpression</td></tr>
        *     <tr><td>PsiCatchSection</td><td>UCatchClause</td></tr>
        *     <tr><td>PsiClass</td><td>UClass</td></tr>
        *     <tr><td>PsiClassObjectAccessExpression</td><td>UClassLiteralExpression</td></tr>
        *     <tr><td>PsiConditionalExpression</td><td>UIfExpression</td></tr>
        *     <tr><td>PsiDeclarationStatement</td><td>UDeclarationsExpression</td></tr>
        *     <tr><td>PsiDoWhileStatement</td><td>UDoWhileExpression</td></tr>
        *     <tr><td>PsiElement</td><td>UElement</td></tr>
        *     <tr><td>PsiExpression</td><td>UExpression</td></tr>
        *     <tr><td>PsiForeachStatement</td><td>UForEachExpression</td></tr>
        *     <tr><td>PsiIdentifier</td><td>USimpleNameReferenceExpression</td></tr>
        *     <tr><td>PsiIfStatement</td><td>UIfExpression</td></tr>
        *     <tr><td>PsiImportStatement</td><td>UImportStatement</td></tr>
        *     <tr><td>PsiImportStaticStatement</td><td>UImportStatement</td></tr>
        *     <tr><td>PsiJavaCodeReferenceElement</td><td>UReferenceExpression</td></tr>
        *     <tr><td>PsiLiteral</td><td>ULiteralExpression</td></tr>
        *     <tr><td>PsiLocalVariable</td><td>ULocalVariable</td></tr>
        *     <tr><td>PsiMethod</td><td>UMethod</td></tr>
        *     <tr><td>PsiMethodCallExpression</td><td>UCallExpression</td></tr>
        *     <tr><td>PsiNameValuePair</td><td>UNamedExpression</td></tr>
        *     <tr><td>PsiNewExpression</td><td>UCallExpression</td></tr>
        *     <tr><td>PsiParameter</td><td>UParameter</td></tr>
        *     <tr><td>PsiParenthesizedExpression</td><td>UParenthesizedExpression</td></tr>
        *     <tr><td>PsiPolyadicExpression</td><td>UPolyadicExpression</td></tr>
        *     <tr><td>PsiPostfixExpression</td><td>UPostfixExpression or UUnaryExpression</td></tr>
        *     <tr><td>PsiPrefixExpression</td><td>UPrefixExpression or UUnaryExpression</td></tr>
        *     <tr><td>PsiReference</td><td>UReferenceExpression</td></tr>
        *     <tr><td>PsiReference</td><td>UResolvable</td></tr>
        *     <tr><td>PsiReferenceExpression</td><td>UReferenceExpression</td></tr>
        *     <tr><td>PsiReturnStatement</td><td>UReturnExpression</td></tr>
        *     <tr><td>PsiSuperExpression</td><td>USuperExpression</td></tr>
        *     <tr><td>PsiSwitchLabelStatement</td><td>USwitchClauseExpression</td></tr>
        *     <tr><td>PsiSwitchStatement</td><td>USwitchExpression</td></tr>
        *     <tr><td>PsiThisExpression</td><td>UThisExpression</td></tr>
        *     <tr><td>PsiThrowStatement</td><td>UThrowExpression</td></tr>
        *     <tr><td>PsiTryStatement</td><td>UTryExpression</td></tr>
        *     <tr><td>PsiTypeCastExpression</td><td>UBinaryExpressionWithType</td></tr>
        *     <tr><td>PsiWhileStatement</td><td>UWhileExpression</td></tr>
        * </table>
        * Note however that UAST isn't just a "renaming of classes"; there are
        * some changes to the structure of the AST as well. Particularly around
        * calls.
        *
        * <h3>Parents</h3>
        * In UAST, you get your parent {@linkplain UElement} by calling
        * {@code getUastParent} instead of {@code getParent}. This is to avoid
        * method name clashes on some elements which are both UAST elements
        * and PSI elements at the same time - such as {@link UMethod}.
        * <h3>Children</h3>
        * When you're going in the opposite direction (e.g. you have a {@linkplain PsiMethod}
        * and you want to look at its content, you should <b>not</b> use
        * {@link PsiMethod#getBody()}. This will only give you the PSI child content,
        * which won't work for example when dealing with Kotlin methods.
        * Normally lint passes you the {@linkplain UMethod} which you should be procesing
        * instead. But if for some reason you need to look up the UAST method
        * body from a {@linkplain PsiMethod}, use this:
        * <pre>
        *     UastContext context = UastUtils.getUastContext(element);
        *     UExpression body = context.getMethodBody(method);
        * </pre>
        * Similarly if you have a {@link PsiField} and you want to look up its field
        * initializer, use this:
        * <pre>
        *     UastContext context = UastUtils.getUastContext(element);
        *     UExpression initializer = context.getInitializerBody(field);
        * </pre>
        *
        * <h3>Call names</h3>
        * In PSI, a call was represented by a PsiCallExpression, and to get to things
        * like the method called or to the operand/qualifier, you'd first need to get
        * the "method expression". In UAST there is no method expression and this
        * information is available directly on the {@linkplain UCallExpression} element.
        * Therefore, here's how you'd change the code:
        * <pre>
        * &lt;    call.getMethodExpression().getReferenceName();
        * ---
        * &gt;    call.getMethodName()
        * </pre>
        * <h3>Call qualifiers</h3>
        * Similarly,
        * <pre>
        * &lt;    call.getMethodExpression().getQualifierExpression();
        * ---
        * &gt;    call.getReceiver()
        * </pre>
        * <h3>Call arguments</h3>
        * PSI had a separate PsiArgumentList element you had to look up before you could
        * get to the actual arguments, as an array. In UAST these are available directly on
        * the call, and are represented as a list instead of an array.
        * <pre>
        * &lt;    PsiExpression[] args = call.getArgumentList().getExpressions();
        * ---
        * &gt;    List<UExpression> args = call.getValueArguments();
        * </pre>
        * Typically you also need to go through your code and replace array access,
        * arg\[i], with list access, {@code arg.get(i)}. Or in Kotlin, just arg\[i]...
        *
        * <h3>Instanceof</h3>
        * You may have code which does something like "parent instanceof PsiAssignmentExpression"
        * to see if something is an assignment. Instead, use one of the many utilities
        * in {@link UastExpressionUtils} - such as {@link UastExpressionUtils#isAssignment(UElement)}.
        * Take a look at all the methods there now - there are methods for checking whether
        * a call is a constructor, whether an expression is an array initializer, etc etc.
        *
        * <h3>Android Resources</h3>
        * Don't do your own AST lookup to figure out if something is a reference to
        * an Android resource (e.g. see if the class refers to an inner class of a class
        * named "R" etc.)  There is now a new utility class which handles this:
        * {@link ResourceReference}. Here's an example of code which has a {@link UExpression}
        * and wants to know it's referencing a R.styleable resource:
        * <pre>
        *        ResourceReference reference = ResourceReference.get(expression);
        *        if (reference == null || reference.getType() != ResourceType.STYLEABLE) {
        *            return;
        *        }
        *        ...
        * </pre>
        *
        * <h3>Binary Expressions</h3>
        * If you had been using {@link PsiBinaryExpression} for things like checking comparator
        * operators or arithmetic combination of operands, you can replace this with
        * {@link UBinaryExpression}. <b>But you normally shouldn't; you should use
        * {@link UPolyadicExpression} instead</b>. A polyadic expression is just like a binary
        * expression, but possibly with more than two terms. With the old parser backend,
        * an expression like "A + B + C" would be represented by nested binary expressions
        * (first A + B, then a parent element which combined that binary expression with C).
        * However, this will now be provided as a {@link UPolyadicExpression} instead. And
        * the binary case is handled trivially without the need to special case it.
        * <h3>Method name changes</h3>
        * The following table maps some common method names and what their corresponding
        * names are in UAST.
        * <table>
        *     <caption>Mapping between PSI and UAST method names</caption></caption>
        *     <tr><th>PSI</th><th>UAST</th></tr>
        *     <tr><td>getArgumentList</td><td>getValueArguments</td></tr>
        *     <tr><td>getCatchSections</td><td>getCatchClauses</td></tr>
        *     <tr><td>getDeclaredElements</td><td>getDeclarations</td></tr>
        *     <tr><td>getElseBranch</td><td>getElseExpression</td></tr>
        *     <tr><td>getInitializer</td><td>getUastInitializer</td></tr>
        *     <tr><td>getLExpression</td><td>getLeftOperand</td></tr>
        *     <tr><td>getOperationTokenType</td><td>getOperator</td></tr>
        *     <tr><td>getOwner</td><td>getUastParent</td></tr>
        *     <tr><td>getParent</td><td>getUastParent</td></tr>
        *     <tr><td>getRExpression</td><td>getRightOperand</td></tr>
        *     <tr><td>getReturnValue</td><td>getReturnExpression</td></tr>
        *     <tr><td>getText</td><td>asSourceString</td></tr>
        *     <tr><td>getThenBranch</td><td>getThenExpression</td></tr>
        *     <tr><td>getType</td><td>getExpressionType</td></tr>
        *     <tr><td>getTypeParameters</td><td>getTypeArguments</td></tr>
        *     <tr><td>resolveMethod</td><td>resolve</td></tr>
        * </table>
        * <h3>Handlers versus visitors</h3>
        * If you are processing a method on your own, or even a full class, you should switch
        * from JavaRecursiveElementVisitor to AbstractUastVisitor.
        * However, most lint checks don't do their own full AST traversal; they instead
        * participate in a shared traversal of the tree, registering element types they're
        * interested with using {@link #getApplicableUastTypes()} and then providing
        * a visitor where they implement the corresponding visit methods. However, from
        * these visitors you should <b>not</b> be calling super.visitX. To remove this
        * whole confusion, lint now provides a separate class, {@link UElementHandler}.
        * For the shared traversal, just provide this handler instead and implement the
        * appropriate visit methods. It will throw an error if you register element types
        * in {@linkplain #getApplicableUastTypes()} that you don't override.
        *
        * <p>
        * <h3>Migrating JavaScanner to SourceCodeScanner</h3>
        * First read the javadoc on how to convert from the older {@linkplain JavaScanner}
        * interface over to {@linkplain JavaPsiScanner}. While {@linkplain JavaPsiScanner} is itself
        * deprecated, it's a lot closer to {@link SourceCodeScanner} so a lot of the same concepts
        * apply; then follow the above section.
        * <p>
        */
        """
            .trimIndent()
    checkFormatter(
        source,
        KDocFormattingOptions(120, 120),
        """
        /**
         * Interface to be implemented by lint detectors that want to analyze Java source files (or other similar source
         * files, such as Kotlin files.)
         *
         * There are several different common patterns for detecting issues:
         * <ul>
         * <li> Checking calls to a given method. For this see [getApplicableMethodNames] and [visitMethodCall]</li>
         * <li> Instantiating a given class. For this, see [getApplicableConstructorTypes] and [visitConstructor]</li>
         * <li> Referencing a given constant. For this, see [getApplicableReferenceNames] and [visitReference]</li>
         * <li> Extending a given class or implementing a given interface. For this, see [applicableSuperClasses] and
         *   [visitClass]</li>
         * <li> More complicated scenarios: perform a general AST traversal with a visitor. In this case, first tell lint
         *   which AST node types you're interested in with the [getApplicableUastTypes] method, and then provide a
         *   [UElementHandler] from the [createUastHandler] where you override the various applicable handler methods. This
         *   is done rather than a general visitor from the root node to avoid having to have every single lint detector
         *   (there are hundreds) do a full tree traversal on its own.</li>
         * </ul>
         *
         * {@linkplain SourceCodeScanner} exposes the UAST API to lint checks. UAST is short for "Universal AST" and is an
         * abstract syntax tree library which abstracts away details about Java versus Kotlin versus other similar languages
         * and lets the client of the library access the AST in a unified way.
         *
         * UAST isn't actually a full replacement for PSI; it **augments** PSI. Essentially, UAST is used for the **inside**
         * of methods (e.g. method bodies), and things like field initializers. PSI continues to be used at the outer level:
         * for packages, classes, and methods (declarations and signatures). There are also wrappers around some of these
         * for convenience.
         *
         * The {@linkplain SourceCodeScanner} interface reflects this fact. For example, when you indicate that you want to
         * check calls to a method named {@code foo}, the call site node is a UAST node (in this case, [UCallExpression],
         * but the called method itself is a [PsiMethod], since that method might be anywhere (including in a library that
         * we don't have source for, so UAST doesn't make sense.)
         *
         * ## Migrating JavaPsiScanner to SourceCodeScanner
         * As described above, PSI is still used, so a lot of code will remain the same. For example, all resolve methods,
         * including those in UAST, will continue to return PsiElement, not necessarily a UElement. For example, if you
         * resolve a method call or field reference, you'll get a [PsiMethod] or [PsiField] back.
         *
         * However, the visitor methods have all changed, generally to change to UAST types. For example, the signature
         * [JavaPsiScanner.visitMethodCall] should be changed to [SourceCodeScanner.visitMethodCall].
         *
         * Similarly, replace [JavaPsiScanner.createPsiVisitor] with [SourceCodeScanner.createUastHandler],
         * [JavaPsiScanner.getApplicablePsiTypes] with [SourceCodeScanner.getApplicableUastTypes], etc.
         *
         * There are a bunch of new methods on classes like [JavaContext] which lets you pass in a [UElement] to match the
         * existing [PsiElement] methods.
         *
         * If you have code which does something specific with PSI classes, the following mapping table in alphabetical
         * order might be helpful, since it lists the corresponding UAST classes.
         * <table>
         * <caption>Mapping between PSI and UAST classes</caption>
         * <tr><th>PSI</th><th>UAST</th></tr>
         * <tr><th>com.intellij.psi.</th><th>org.jetbrains.uast.</th></tr>
         * <tr><td>IElementType</td><td>UastBinaryOperator</td></tr>
         * <tr><td>PsiAnnotation</td><td>UAnnotation</td></tr>
         * <tr><td>PsiAnonymousClass</td><td>UAnonymousClass</td></tr>
         * <tr><td>PsiArrayAccessExpression</td><td>UArrayAccessExpression</td></tr>
         * <tr><td>PsiBinaryExpression</td><td>UBinaryExpression</td></tr>
         * <tr><td>PsiCallExpression</td><td>UCallExpression</td></tr>
         * <tr><td>PsiCatchSection</td><td>UCatchClause</td></tr>
         * <tr><td>PsiClass</td><td>UClass</td></tr>
         * <tr><td>PsiClassObjectAccessExpression</td><td>UClassLiteralExpression</td></tr>
         * <tr><td>PsiConditionalExpression</td><td>UIfExpression</td></tr>
         * <tr><td>PsiDeclarationStatement</td><td>UDeclarationsExpression</td></tr>
         * <tr><td>PsiDoWhileStatement</td><td>UDoWhileExpression</td></tr>
         * <tr><td>PsiElement</td><td>UElement</td></tr>
         * <tr><td>PsiExpression</td><td>UExpression</td></tr>
         * <tr><td>PsiForeachStatement</td><td>UForEachExpression</td></tr>
         * <tr><td>PsiIdentifier</td><td>USimpleNameReferenceExpression</td></tr>
         * <tr><td>PsiIfStatement</td><td>UIfExpression</td></tr>
         * <tr><td>PsiImportStatement</td><td>UImportStatement</td></tr>
         * <tr><td>PsiImportStaticStatement</td><td>UImportStatement</td></tr>
         * <tr><td>PsiJavaCodeReferenceElement</td><td>UReferenceExpression</td></tr>
         * <tr><td>PsiLiteral</td><td>ULiteralExpression</td></tr>
         * <tr><td>PsiLocalVariable</td><td>ULocalVariable</td></tr>
         * <tr><td>PsiMethod</td><td>UMethod</td></tr>
         * <tr><td>PsiMethodCallExpression</td><td>UCallExpression</td></tr>
         * <tr><td>PsiNameValuePair</td><td>UNamedExpression</td></tr>
         * <tr><td>PsiNewExpression</td><td>UCallExpression</td></tr>
         * <tr><td>PsiParameter</td><td>UParameter</td></tr>
         * <tr><td>PsiParenthesizedExpression</td><td>UParenthesizedExpression</td></tr>
         * <tr><td>PsiPolyadicExpression</td><td>UPolyadicExpression</td></tr>
         * <tr><td>PsiPostfixExpression</td><td>UPostfixExpression or UUnaryExpression</td></tr>
         * <tr><td>PsiPrefixExpression</td><td>UPrefixExpression or UUnaryExpression</td></tr>
         * <tr><td>PsiReference</td><td>UReferenceExpression</td></tr>
         * <tr><td>PsiReference</td><td>UResolvable</td></tr>
         * <tr><td>PsiReferenceExpression</td><td>UReferenceExpression</td></tr>
         * <tr><td>PsiReturnStatement</td><td>UReturnExpression</td></tr>
         * <tr><td>PsiSuperExpression</td><td>USuperExpression</td></tr>
         * <tr><td>PsiSwitchLabelStatement</td><td>USwitchClauseExpression</td></tr>
         * <tr><td>PsiSwitchStatement</td><td>USwitchExpression</td></tr>
         * <tr><td>PsiThisExpression</td><td>UThisExpression</td></tr>
         * <tr><td>PsiThrowStatement</td><td>UThrowExpression</td></tr>
         * <tr><td>PsiTryStatement</td><td>UTryExpression</td></tr>
         * <tr><td>PsiTypeCastExpression</td><td>UBinaryExpressionWithType</td></tr>
         * <tr><td>PsiWhileStatement</td><td>UWhileExpression</td></tr> </table> Note however that UAST isn't just a
         * "renaming of classes"; there are some changes to the structure of the AST as well. Particularly around calls.
         *
         * ### Parents
         * In UAST, you get your parent {@linkplain UElement} by calling {@code getUastParent} instead of {@code getParent}.
         * This is to avoid method name clashes on some elements which are both UAST elements and PSI elements at the same
         * time - such as [UMethod].
         *
         * ### Children
         * When you're going in the opposite direction (e.g. you have a {@linkplain PsiMethod} and you want to look at its
         * content, you should **not** use [PsiMethod.getBody]. This will only give you the PSI child content, which won't
         * work for example when dealing with Kotlin methods. Normally lint passes you the {@linkplain UMethod} which you
         * should be procesing instead. But if for some reason you need to look up the UAST method body from a {@linkplain
         * PsiMethod}, use this:
         * ```
         *     UastContext context = UastUtils.getUastContext(element);
         *     UExpression body = context.getMethodBody(method);
         * ```
         *
         * Similarly if you have a [PsiField] and you want to look up its field initializer, use this:
         * ```
         *     UastContext context = UastUtils.getUastContext(element);
         *     UExpression initializer = context.getInitializerBody(field);
         * ```
         *
         * ### Call names
         * In PSI, a call was represented by a PsiCallExpression, and to get to things like the method called or to the
         * operand/qualifier, you'd first need to get the "method expression". In UAST there is no method expression and
         * this information is available directly on the {@linkplain UCallExpression} element. Therefore, here's how you'd
         * change the code:
         * ```
         * &lt;    call.getMethodExpression().getReferenceName();
         * ---
         * &gt;    call.getMethodName()
         * ```
         *
         * ### Call qualifiers
         * Similarly,
         * ```
         * &lt;    call.getMethodExpression().getQualifierExpression();
         * ---
         * &gt;    call.getReceiver()
         * ```
         *
         * ### Call arguments
         * PSI had a separate PsiArgumentList element you had to look up before you could get to the actual arguments, as an
         * array. In UAST these are available directly on the call, and are represented as a list instead of an array.
         *
         * ```
         * &lt;    PsiExpression[] args = call.getArgumentList().getExpressions();
         * ---
         * &gt;    List<UExpression> args = call.getValueArguments();
         * ```
         *
         * Typically you also need to go through your code and replace array access, arg\[i], with list access, {@code
         * arg.get(i)}. Or in Kotlin, just arg\[i]...
         *
         * ### Instanceof
         * You may have code which does something like "parent instanceof PsiAssignmentExpression" to see if
         * something is an assignment. Instead, use one of the many utilities in [UastExpressionUtils] - such
         * as [UastExpressionUtils.isAssignment]. Take a look at all the methods there now - there are methods
         * for checking whether a call is a constructor, whether an expression is an array initializer, etc etc.
         *
         * ### Android Resources
         * Don't do your own AST lookup to figure out if something is a reference to an Android resource (e.g. see if the
         * class refers to an inner class of a class named "R" etc.) There is now a new utility class which handles this:
         * [ResourceReference]. Here's an example of code which has a [UExpression] and wants to know it's referencing a
         * R.styleable resource:
         * ```
         *        ResourceReference reference = ResourceReference.get(expression);
         *        if (reference == null || reference.getType() != ResourceType.STYLEABLE) {
         *            return;
         *        }
         *        ...
         * ```
         *
         * ### Binary Expressions
         * If you had been using [PsiBinaryExpression] for things like checking comparator operators or arithmetic
         * combination of operands, you can replace this with [UBinaryExpression]. **But you normally shouldn't; you should
         * use [UPolyadicExpression] instead**. A polyadic expression is just like a binary expression, but possibly with
         * more than two terms. With the old parser backend, an expression like "A + B + C" would be represented by nested
         * binary expressions (first A + B, then a parent element which combined that binary expression with C). However,
         * this will now be provided as a [UPolyadicExpression] instead. And the binary case is handled trivially without
         * the need to special case it.
         *
         * ### Method name changes
         * The following table maps some common method names and what their corresponding names are in UAST.
         * <table>
         * <caption>Mapping between PSI and UAST method names</caption></caption>
         * <tr><th>PSI</th><th>UAST</th></tr>
         * <tr><td>getArgumentList</td><td>getValueArguments</td></tr>
         * <tr><td>getCatchSections</td><td>getCatchClauses</td></tr>
         * <tr><td>getDeclaredElements</td><td>getDeclarations</td></tr>
         * <tr><td>getElseBranch</td><td>getElseExpression</td></tr>
         * <tr><td>getInitializer</td><td>getUastInitializer</td></tr>
         * <tr><td>getLExpression</td><td>getLeftOperand</td></tr>
         * <tr><td>getOperationTokenType</td><td>getOperator</td></tr>
         * <tr><td>getOwner</td><td>getUastParent</td></tr>
         * <tr><td>getParent</td><td>getUastParent</td></tr>
         * <tr><td>getRExpression</td><td>getRightOperand</td></tr>
         * <tr><td>getReturnValue</td><td>getReturnExpression</td></tr>
         * <tr><td>getText</td><td>asSourceString</td></tr>
         * <tr><td>getThenBranch</td><td>getThenExpression</td></tr>
         * <tr><td>getType</td><td>getExpressionType</td></tr>
         * <tr><td>getTypeParameters</td><td>getTypeArguments</td></tr>
         * <tr><td>resolveMethod</td><td>resolve</td></tr> </table>
         *
         * ### Handlers versus visitors
         * If you are processing a method on your own, or even a full class, you should switch from
         * JavaRecursiveElementVisitor to AbstractUastVisitor. However, most lint checks don't do their own full AST
         * traversal; they instead participate in a shared traversal of the tree, registering element types they're
         * interested with using [getApplicableUastTypes] and then providing a visitor where they implement the
         * corresponding visit methods. However, from these visitors you should **not** be calling super.visitX. To remove
         * this whole confusion, lint now provides a separate class, [UElementHandler]. For the shared traversal, just
         * provide this handler instead and implement the appropriate visit methods. It will throw an error if you register
         * element types in {@linkplain #getApplicableUastTypes()} that you don't override.
         *
         * ### Migrating JavaScanner to SourceCodeScanner
         * First read the javadoc on how to convert from the older {@linkplain JavaScanner} interface over to {@linkplain
         * JavaPsiScanner}. While {@linkplain JavaPsiScanner} is itself deprecated, it's a lot closer to [SourceCodeScanner]
         * so a lot of the same concepts apply; then follow the above section.
         */
        """
            .trimIndent(),
        // {@link} tags are not rendered from [references] when Dokka cannot resolve the symbols
        verifyDokka = false,
    )
  }

  @Test
  fun testPreserveParagraph() {
    // Make sure that when we convert <p>, it's preserved.
    val source =
        """
        /**
        * <ul>
        * <li>test</li>
        * </ul>
        * <p>
        * After.
        */
        """
            .trimIndent()
    checkFormatter(
        source,
        KDocFormattingOptions(120, 120),
        """
        /**
         * <ul>
         * <li>test</li>
         * </ul>
         *
         * After.
         */
        """
            .trimIndent(),
    )
  }

  @Test
  fun testWordJoining() {
    // "-" alone can mean beginning of a list, but not as part of a word
    val source =
        """
        /**
         * which you can render with something like this:
         * `dot -Tpng -o/tmp/graph.png toString.dot`
         */
        """
            .trimIndent()
    checkFormatter(
        source,
        KDocFormattingOptions(65),
        """
        /**
         * which you can render with something like this: `dot -Tpng
         * -o/tmp/graph.png toString.dot`
         */
        """
            .trimIndent(),
    )

    val source2 =
        """
        /**
         * ABCDE which you can render with something like this:
         * `dot - Tpng -o/tmp/graph.png toString.dot`
         */
        """
            .trimIndent()
    checkFormatter(
        source2,
        KDocFormattingOptions(65),
        """
        /**
         * ABCDE which you can render with something like this:
         * `dot - Tpng -o/tmp/graph.png toString.dot`
         */
        """
            .trimIndent(),
    )
  }

  @Test
  fun testEarlyBreakForTodo() {
    // Don't break before a TODO
    val source =
        """
        /**
         * This is a long line that will break a little early to breaking at TODO:
         *
         * This is a long line that wont break a little early to breaking at DODO:
         */
        """
            .trimIndent()
    checkFormatter(
        source,
        KDocFormattingOptions(72, 72).apply { optimal = false },
        """
        /**
         * This is a long line that will break a little early to breaking
         * at TODO:
         *
         * This is a long line that wont break a little early to breaking at
         * DODO:
         */
        """
            .trimIndent(),
    )
  }

  @Test
  fun testPreformat() {
    // Don't join preformatted text with previous TODO comment
    val source =
        """
        /**
         * TODO: Work.
         * ```
         * Preformatted.
         *
         * More preformatted.
         * ```
         */
        """
            .trimIndent()
    checkFormatter(
        source,
        KDocFormattingOptions(72, 72),
        """
        /**
         * TODO: Work.
         *
         * ```
         * Preformatted.
         *
         * More preformatted.
         * ```
         */
        """
            .trimIndent(),
    )
  }

  @Test
  fun testConvertLinks() {
    // Make sure we convert {@link} and NOT {@linkplain} if convertMarkup is true.
    val source =
        """
        /**
         * {@link SourceCodeScanner} exposes the UAST API to lint checks.
         * The {@link SourceCodeScanner} interface reflects this fact.
         *
         * {@linkplain SourceCodeScanner} exposes the UAST API to lint checks.
         * The {@linkplain SourceCodeScanner} interface reflects this fact.
         *
         * It will throw an error if you register element types in
         * {@link #getApplicableUastTypes()} that you don't override.
         *
         * First read the javadoc on how to convert from the older {@link
         * JavaScanner} interface over to {@link JavaPsiScanner}.
         *
         * 1. A file header, which is the exact contents of {@link FILE_HEADER} encoded
         *     as ASCII characters.
         *
         * Given an error message produced by this lint detector for the
         * given issue type, determines whether this corresponds to the
         * warning (produced by {@link #reportBaselineIssues(LintDriver,
         * Project)} above) that one or more issues have been
         * fixed (present in baseline but not in project.)
         *
         * {@link #getQualifiedName(PsiClass)} method.
         */
        """
            .trimIndent()
    checkFormatter(
        source,
        KDocFormattingOptions(72, 72),
        """
        /**
         * [SourceCodeScanner] exposes the UAST API to lint checks. The
         * [SourceCodeScanner] interface reflects this fact.
         *
         * {@linkplain SourceCodeScanner} exposes the UAST API to lint
         * checks. The {@linkplain SourceCodeScanner} interface reflects
         * this fact.
         *
         * It will throw an error if you register element types in
         * [getApplicableUastTypes] that you don't override.
         *
         * First read the javadoc on how to convert from the older
         * [JavaScanner] interface over to [JavaPsiScanner].
         *
         * 1. A file header, which is the exact contents of [FILE_HEADER]
         *    encoded as ASCII characters.
         *
         * Given an error message produced by this lint detector for the
         * given issue type, determines whether this corresponds to the
         * warning (produced by [reportBaselineIssues] above) that one or
         * more issues have been fixed (present in baseline but not in
         * project.)
         *
         * [getQualifiedName] method.
         */
        """
            .trimIndent(),
        // When dokka cannot resolve the links it doesn't render {@link} which makes
        // before and after not match
        verifyDokka = false,
    )
  }

  @Test
  fun testNestedBullets() {
    // Regression test for https://github.com/tnorbye/kdoc-formatter/issues/36
    val source =
        """
        /**
         * Paragraph
         * * Top Bullet
         *    * Sub-Bullet 1
         *    * Sub-Bullet 2
         *       * Sub-Sub-Bullet 1
         * 1. Top level
         *    1. First item
         *    2. Second item
         */
        """
            .trimIndent()

    checkFormatter(
        source,
        KDocFormattingOptions(72, 72),
        """
        /**
         * Paragraph
         * * Top Bullet
         *    * Sub-Bullet 1
         *    * Sub-Bullet 2
         *       * Sub-Sub-Bullet 1
         * 1. Top level
         *    1. First item
         *    2. Second item
         */
        """
            .trimIndent(),
    )

    checkFormatter(
        source,
        KDocFormattingOptions(72, 72).apply { nestedListIndent = 4 },
        """
        /**
         * Paragraph
         * * Top Bullet
         *     * Sub-Bullet 1
         *     * Sub-Bullet 2
         *         * Sub-Sub-Bullet 1
         * 1. Top level
         *     1. First item
         *     2. Second item
         */
        """
            .trimIndent(),
    )
  }

  @Test
  fun testTripledQuotedPrefixNotBreakable() {
    // Corresponds to b/189247595
    val source =
        """
        /**
         * Gets current ABCD Workspace information from the output of ```abcdtools info```.
         *
         * Migrated from
         * http://com.example
         */
        """
            .trimIndent()
    checkFormatter(
        source,
        KDocFormattingOptions(72, 72),
        """
        /**
         * Gets current ABCD Workspace information from the output
         * of ```abcdtools info```.
         *
         * Migrated from http://com.example
         */
        """
            .trimIndent(),
    )
  }

  @Test
  fun testGreedyLineBreak() {
    // Make sure we correctly break at the max line width
    val source =
        """
        /**
         * Handles a chain of qualified expressions, i.e. `a[5].b!!.c()[4].f()`
         *
         * This is by far the most complicated part of this formatter. We start by breaking the expression
         * to the steps it is executed in (which are in the opposite order of how the syntax tree is
         * built).
         *
         * We then calculate information to know which parts need to be groups, and finally go part by
         * part, emitting it to the [builder] while closing and opening groups.
         *
         * @param brokeBeforeBrace used for tracking if a break was taken right before the lambda
         * expression. Useful for scoping functions where we want good looking indentation. For example,
         * here we have correct indentation before `bar()` and `car()` because we can detect the break
         * after the equals:
         */
        """
            .trimIndent()
    checkFormatter(
        source,
        KDocFormattingOptions(100, 100).apply { optimal = false },
        """
        /**
         * Handles a chain of qualified expressions, i.e. `a[5].b!!.c()[4].f()`
         *
         * This is by far the most complicated part of this formatter. We start by breaking the
         * expression to the steps it is executed in (which are in the opposite order of how the syntax
         * tree is built).
         *
         * We then calculate information to know which parts need to be groups, and finally go part by
         * part, emitting it to the [builder] while closing and opening groups.
         *
         * @param brokeBeforeBrace used for tracking if a break was taken right before the lambda
         *   expression. Useful for scoping functions where we want good looking indentation. For
         *   example, here we have correct indentation before `bar()` and `car()` because we can detect
         *   the break after the equals:
         */
        """
            .trimIndent(),
    )
  }

  @Test
  fun test193246766() {
    val source =
        // Nonsensical text derived from the original using the lorem() method and
        // replacing same-length & same capitalization words from lorem ipsum
        """
        /**
         * * Do do occaecat sunt in culpa:
         *   * Id id reprehenderit cillum non `adipiscing` enim enim ad occaecat
         *   * Cupidatat non officia anim adipiscing enim non reprehenderit in officia est:
         *     * Do non officia anim voluptate esse non mollit mollit id tempor, enim u consequat. irure
         *     in occaecat
         *     * Cupidatat, in qui officia anim voluptate esse eu fugiat fugiat in mollit, anim anim id
         *     occaecat
         * * In h anim id laborum:
         *   * Do non sunt voluptate esse non culpa mollit id tempor, enim u consequat. irure in occaecat
         *   * Cupidatat, in qui anim voluptate esse non culpa mollit est do tempor, enim enim ad occaecat
         */
        """
            .trimIndent()
    checkFormatter(
        source,
        KDocFormattingOptions(72, 72),
        """
        /**
         * * Do do occaecat sunt in culpa:
         *    * Id id reprehenderit cillum non `adipiscing` enim enim ad
         *      occaecat
         *    * Cupidatat non officia anim adipiscing enim non reprehenderit
         *      in officia est:
         *       * Do non officia anim voluptate esse non mollit mollit id
         *         tempor, enim u consequat. irure in occaecat
         *       * Cupidatat, in qui officia anim voluptate esse eu fugiat
         *         fugiat in mollit, anim anim id occaecat
         * * In h anim id laborum:
         *    * Do non sunt voluptate esse non culpa mollit id tempor, enim
         *      u consequat. irure in occaecat
         *    * Cupidatat, in qui anim voluptate esse non culpa mollit est
         *      do tempor, enim enim ad occaecat
         */
        """
            .trimIndent(),
        // We indent the last bullets as if they are nested list items; this
        // is likely the intent (though with indent only being 2, dokka would
        // interpret it as top level text.)
        verifyDokka = false,
    )
  }

  @Test
  fun test203584301() {
    // https://github.com/facebook/ktfmt/issues/310
    val source =
        """
        /**
         * This is my SampleInterface interface.
         * @sample com.example.java.sample.library.extra.long.path.MyCustomSampleInterfaceImplementationForTesting
         */
        """
            .trimIndent()
    checkFormatter(
        source,
        KDocFormattingOptions(72, 72),
        """
        /**
         * This is my SampleInterface interface.
         *
         * @sample com.example.java.sample.library.extra.long.path.MyCustomSampleInterfaceImplementationForTesting
         */
        """
            .trimIndent(),
    )
  }

  @Test
  fun test209435082() {
    // b/209435082
    val source =
        // Nonsensical text derived from the original using the lorem() method and
        // replacing same-length & same capitalization words from lorem ipsum
        """
        /**
         * eiusmod.com
         * - - -
         * PARIATUR_MOLLIT
         * - - -
         * Laborum: 1.4
         * - - -
         * Pariatur:
         * https://officia.officia.com
         * https://id.laborum.laborum.com
         * https://sit.eiusmod.com
         * https://non-in.officia.com
         * https://anim.laborum.com
         * https://exercitation.ullamco.com
         * - - -
         * Adipiscing do tempor:
         * - NON: IN/IN
         * - in 2IN officia? EST
         * - do EIUSMOD eiusmod? NON
         * - Mollit est do incididunt Nostrud non? IN
         * - Mollit pariatur pariatur culpa? QUI
         * - - -
         * Lorem eiusmod magna/adipiscing:
         * - Do eiusmod magna/adipiscing
         */
        """
            .trimIndent()
    checkFormatter(
        source,
        KDocFormattingOptions(72, 72),
        """
        /**
         * eiusmod.com
         * - - -
         * PARIATUR_MOLLIT
         * - - -
         * Laborum: 1.4
         * - - -
         * Pariatur: https://officia.officia.com
         * https://id.laborum.laborum.com https://sit.eiusmod.com
         * https://non-in.officia.com https://anim.laborum.com
         * https://exercitation.ullamco.com
         * - - -
         * Adipiscing do tempor:
         * - NON: IN/IN
         * - in 2IN officia? EST
         * - do EIUSMOD eiusmod? NON
         * - Mollit est do incididunt Nostrud non? IN
         * - Mollit pariatur pariatur culpa? QUI
         * - - -
         * Lorem eiusmod magna/adipiscing:
         * - Do eiusmod magna/adipiscing
         */
        """
            .trimIndent(),
    )
  }

  @Test
  fun test236743270() {
    val source =
        // Nonsensical text derived from the original using the lorem() method and
        // replacing same-length & same capitalization words from lorem ipsum
        """
        /**
         * @return Amet do non adipiscing sed consequat duis non Officia ID (amet sed consequat non
         * adipiscing sed eiusmod), magna consequat.
         */
        """
            .trimIndent()
    val lorem = loremize(source)
    assertEquals(source, lorem)
    checkFormatter(
        source,
        KDocFormattingOptions(72, 72),
        """
        /**
         * @return Amet do non adipiscing sed consequat duis non Officia ID
         *   (amet sed consequat non adipiscing sed eiusmod), magna
         *   consequat.
         */
        """
            .trimIndent(),
    )
  }

  @Test
  fun test238279769() {
    val source =
        // Nonsensical text derived from the original using the lorem() method and
        // replacing same-length & same capitalization words from lorem ipsum
        """
        /**
         * @property dataItemOrderRandomizer sit tempor enim pariatur non culpa id [Pariatur]z in qui anim.
         *  Anim id-lorem sit magna [Consectetur] pariatur.
         * @property randomBytesProvider non mollit anim pariatur non culpa qui qui `mollit` lorem amet
         *   consectetur [Pariatur]z in IssuerSignedItem culpa.
         * @property preserveMapOrder officia id pariatur non culpa id lorem pariatur culpa culpa id o est
         *    amet consectetur sed sed do ENIM minim.
         * @property reprehenderit p esse cillum officia est do enim enim nostrud nisi d non sunt mollit id
         *     est tempor enim.
         */
        """
            .trimIndent()
    checkFormatter(
        source,
        KDocFormattingOptions(72, 72),
        """
        /**
         * @property dataItemOrderRandomizer sit tempor enim pariatur non
         *   culpa id [Pariatur]z in qui anim. Anim id-lorem sit magna
         *   [Consectetur] pariatur.
         * @property randomBytesProvider non mollit anim pariatur non culpa
         *   qui qui `mollit` lorem amet consectetur [Pariatur]z in
         *   IssuerSignedItem culpa.
         * @property preserveMapOrder officia id pariatur non culpa id lorem
         *   pariatur culpa culpa id o est amet consectetur sed sed do ENIM
         *   minim.
         * @property reprehenderit p esse cillum officia est do enim enim
         *   nostrud nisi d non sunt mollit id est tempor enim.
         */
        """
            .trimIndent(),
    )
  }

  @Test
  fun testPropertiesAreParams() {
    val source =
        """
        /**
         * @param bar lorem ipsum
         * @property baz dolor sit
         * @property foo amet, consetetur
         */
        """
            .trimIndent()
    checkFormatter(
        FormattingTask(
            KDocFormattingOptions(72, 72),
            source.trim(),
            initialIndent = "    ",
            orderedParameterNames = listOf("foo", "bar", "baz"),
        ),
        """
        /**
         * @property foo amet, consetetur
         * @param bar lorem ipsum
         * @property baz dolor sit
         */
        """
            .trimIndent(),
    )
  }

  @Test
  fun testKnit() {
    // Some tests for the knit plugin -- https://github.com/Kotlin/kotlinx-knit
    val source =
        """
        /**
         * <!--- <directive> [<parameters>] -->
         * <!--- <directive> [<parameters>]
         * Some text here.
         * This should all be merged into one
         * line.
         * -->
         * <!--- super long text here; this not be broken into lines; super long text here super long text here super long text here super long text here -->
         *
         * <!--- INCLUDE
         * import kotlin.system.*
         * -->
         * ```kotlin
         * fun exit(): Nothing = exitProcess(0)
         * ```
         * <!--- PREFIX -->
         * <!--- TEST_NAME BasicTest -->
         * <!--- TEST
         * Hello, world!
         * -->
         * <!--- TEST lines.single().toInt() in 1..100 -->
         * <!--- TOC -->
         * <!--- END -->
         * <!--- MODULE kotlinx-knit-test -->
         * <!--- INDEX kotlinx.knit.test -->
         * [captureOutput]: https://example.com/kotlinx-knit-test/kotlinx.knit.test/capture-output.html
         * <!--- END -->
         *
         * Make sure we never line break <!--- to the beginning a line: <!--- <!--- <!--- end.
         */
        """
            .trimIndent()
    checkFormatter(
        source,
        KDocFormattingOptions(72, 72),
        """
        /**
         * <!--- <directive> [<parameters>] -->
         * <!--- <directive> [<parameters>]
         * Some text here. This should all be merged into one line.
         * -->
         * <!--- super long text here; this not be broken into lines; super long text here super long text here super long text here super long text here -->
         * <!--- INCLUDE
         * import kotlin.system.*
         * -->
         * ```kotlin
         * fun exit(): Nothing = exitProcess(0)
         * ```
         * <!--- PREFIX -->
         * <!--- TEST_NAME BasicTest -->
         * <!--- TEST
         * Hello, world!
         * -->
         * <!--- TEST lines.single().toInt() in 1..100 -->
         * <!--- TOC -->
         * <!--- END -->
         * <!--- MODULE kotlinx-knit-test -->
         * <!--- INDEX kotlinx.knit.test -->
         * [captureOutput]:
         * https://example.com/kotlinx-knit-test/kotlinx.knit.test/capture-output.html
         * <!--- END -->
         *
         * Make sure we never line break <!--- to the beginning a
         * line: <!--- <!--- <!--- end.
         */
        """
            .trimIndent(),
    )
  }

  @Test
  fun testNPE() {
    // Reproduces formatting bug found in androidx' SplashScreen.kt:
    val source =
        """
        /**
         *  ## Specs
         *  - With icon background (`Theme.SplashScreen.IconBackground`)
         *    + Image Size: 240x240 dp
         *    + Inner Circle diameter: 160 dp
         */
        """
            .trimIndent()
    checkFormatter(
        source,
        KDocFormattingOptions(72, 72),
        """
        /**
         * ## Specs
         * - With icon background (`Theme.SplashScreen.IconBackground`)
         *    + Image Size: 240x240 dp
         *    + Inner Circle diameter: 160 dp
         */
        """
            .trimIndent(),
    )
  }

  @Test
  fun testExtraNewlines() {
    // Reproduced a bug which was inserting extra newlines in preformatted text
    val source =
        """
        /**
         * Simple helper class useful for creating a message bundle for your module.
         *
         * It creates a soft reference to an underlying text bundle, which means that it can
         * be garbage collected if needed (although it will be reallocated again if you request
         * a new message from it).
         *
         * You might use it like so:
         *
         * ```
         * # In module 'custom'...
         *
         * # resources/messages/CustomBundle.properties:
         * sample.text.key=This is a sample text value.
         *
         * # src/messages/CustomBundle.kt:
         * private const val BUNDLE_NAME = "messages.CustomBundle"
         * object CustomBundle {
         *   private val bundleRef = MessageBundleReference(BUNDLE_NAME)
         *   fun message(@PropertyKey(resourceBundle = BUNDLE_NAME) key: String, vararg params: Any) = bundleRef.message(key, *params)
         * }
         * ```
         *
         * That's it! Now you can call `CustomBundle.message("sample.text.key")` to fetch the text value.
         */
        """
            .trimIndent()
    checkFormatter(
        source,
        KDocFormattingOptions(72, 72),
        """
        /**
         * Simple helper class useful for creating a message bundle for your
         * module.
         *
         * It creates a soft reference to an underlying text bundle, which
         * means that it can be garbage collected if needed (although it
         * will be reallocated again if you request a new message from it).
         *
         * You might use it like so:
         * ```
         * # In module 'custom'...
         *
         * # resources/messages/CustomBundle.properties:
         * sample.text.key=This is a sample text value.
         *
         * # src/messages/CustomBundle.kt:
         * private const val BUNDLE_NAME = "messages.CustomBundle"
         * object CustomBundle {
         *   private val bundleRef = MessageBundleReference(BUNDLE_NAME)
         *   fun message(@PropertyKey(resourceBundle = BUNDLE_NAME) key: String, vararg params: Any) = bundleRef.message(key, *params)
         * }
         * ```
         *
         * That's it! Now you can call
         * `CustomBundle.message("sample.text.key")`
         * to fetch the text value.
         */
        """
            .trimIndent(),
    )
  }

  @Test
  fun testQuotedBug() {
    // Reproduced a bug which was mishandling quoted strings: when you have
    // *separate* but adjacent quoted lists, make sure we preserve line break
    // between them
    val source =
        """
        /**
         * Eg:
         * > anydpi-v26 &emsp; | &emsp; Adaptive icon - ic_launcher.xml
         *
         *
         * > hdpi &emsp;&emsp;&emsp;&emsp;&nbsp; | &emsp; Mip Map File - ic_launcher.png
         */
        """
            .trimIndent()
    checkFormatter(
        source,
        KDocFormattingOptions(100, 72),
        """
        /**
         * Eg:
         * > anydpi-v26 &emsp; | &emsp; Adaptive icon - ic_launcher.xml
         *
         * > hdpi &emsp;&emsp;&emsp;&emsp;&nbsp; | &emsp; Mip Map File -
         * > ic_launcher.png
         */
        """
            .trimIndent(),
        indent = "  ",
    )
  }

  @Test
  fun testListBreaking() {
    // If we have, in a list, "* very-long-word", we cannot break this line
    // with a bullet on its line by itself. In the below, prior to the bug fix,
    // the "- spec:width..." would get split into "-" and "spec:width..." on
    // its own hanging indent line.
    val source =
        """
        /**
         * In other words, completes the parameters so that either of these declarations can be achieved:
         * - spec:width=...,height=...,dpi=...,isRound=...,chinSize=...,orientation=...
         * - spec:parent=...,orientation=...
         * > spec:width=...,height=...,dpi=...,isRound=...,chinSize=...,orientation=...
         */
        """
            .trimIndent()
    checkFormatter(
        source,
        KDocFormattingOptions(100, 72),
        """
        /**
         * In other words, completes the parameters so that either of these
         * declarations can be achieved:
         * - spec:width=...,height=...,dpi=...,isRound=...,chinSize=...,orientation=...
         * - spec:parent=...,orientation=...
         * > spec:width=...,height=...,dpi=...,isRound=...,chinSize=...,orientation=...
         */
        """
            .trimIndent(),
        indent = "",
    )
  }

  @Test
  fun testNewList() {
    // Make sure we never place "1)" or "+" at the beginning of a new line.
    val source =
        """
        /**
         * Handles both the START_ALLOC_TRACKING and STOP_ALLOC_TRACKING commands in tests. This is responsible for generating a status event.
         * For the start tracking command,  if |trackStatus| is set to be |SUCCESS|, this generates a start event with timestamp matching what is
         * specified in |trackStatus|. For the end tracking command, an event (start timestamp + 1) is only added if a start event already
         * exists in the input event list.
         */
        """
            .trimIndent()
    checkFormatter(
        source,
        KDocFormattingOptions(100, 72),
        """
        /**
         * Handles both the START_ALLOC_TRACKING and STOP_ALLOC_TRACKING commands
         * in tests. This is responsible for generating a status event. For the
         * start tracking command, if |trackStatus| is set to be |SUCCESS|, this
         * generates a start event with timestamp matching what is specified
         * in |trackStatus|. For the end tracking command, an event (start
         * timestamp + 1) is only added if a start event already exists in the
         * input event list.
         */
        """
            .trimIndent(),
        indent = "",
    )
  }

  @Test
  fun testSplashScreen() {
    val source =
        """
        /**
         * Provides control over the splash screen once the application is started.
         *
         * On API 31+ (Android 12+) this class calls the platform methods.
         *
         * Prior API 31, the platform behavior is replicated with the exception of the Animated Vector
         * Drawable support on the launch screen.
         *
         * # Usage of the `core-splashscreen` library:
         *
         * To replicate the splash screen behavior from Android 12 on older APIs the following steps need to
         * be taken:
         *  1. Create a new Theme (e.g `Theme.App.Starting`) and set its parent to `Theme.SplashScreen` or
         *  `Theme.SplashScreen.IconBackground`
         *
         *  2. In your manifest, set the `theme` attribute of the whole `<application>` or just the
         *  starting `<activity>` to `Theme.App.Starting`
         *
         *  3. In the `onCreate` method the starting activity, call [installSplashScreen] just before
         *  `super.onCreate()`. You also need to make sure that `postSplashScreenTheme` is set
         *  to the application's theme. Alternatively, this call can be replaced by [Activity#setTheme]
         *  if a [SplashScreen] instance isn't needed.
         *
         *  ## Themes
         *
         *  The library provides two themes: [R.style.Theme_SplashScreen] and
         *  [R.style.Theme_SplashScreen_IconBackground]. If you wish to display a background right under
         *  your icon, the later needs to be used. This ensure that the scale and masking of the icon are
         *  similar to the Android 12 Splash Screen.
         *
         *  `windowSplashScreenAnimatedIcon`: The splash screen icon. On API 31+ it can be an animated
         *  vector drawable.
         *
         *  `windowSplashScreenAnimationDuration`: Duration of the Animated Icon Animation. The value
         *  needs to be > 0 if the icon is animated.
         *
         *  **Note:** This has no impact on the time during which the splash screen is displayed and is
         *  only used in [SplashScreenViewProvider.iconAnimationDurationMillis]. If you need to display the
         *  splash screen for a longer time, you can use [SplashScreen.setKeepOnScreenCondition]
         *
         *  `windowSplashScreenIconBackgroundColor`: _To be used in with
         *  `Theme.SplashScreen.IconBackground`_. Sets a background color under the splash screen icon.
         *
         *  `windowSplashScreenBackground`: Background color of the splash screen. Defaults to the theme's
         *  `?attr/colorBackground`.
         *
         *  `postSplashScreenTheme`*  Theme to apply to the Activity when [installSplashScreen] is called.
         *
         *  **Known incompatibilities:**
         *  - On API < 31, `windowSplashScreenAnimatedIcon` cannot be animated. If you want to provide an
         *  animated icon for API 31+ and a still icon for API <31, you can do so by overriding the still
         *  icon with an animated vector drawable in `res/drawable-v31`.
         *
         *  - On API < 31, if the value of `windowSplashScreenAnimatedIcon` is an
         *  [adaptive icon](http://developer.android.com/guide/practices/ui_guidelines/icon_design_adaptive)
         *  , it will be cropped and scaled. The workaround is to respectively assign
         *  `windowSplashScreenAnimatedIcon` and `windowSplashScreenIconBackgroundColor` to the values of
         *  the adaptive icon `foreground` and `background`.
         *
         *  - On API 21-22, The icon isn't displayed until the application starts, only the background is
         *  visible.
         *
         *  # Design
         *  The splash screen icon uses the same specifications as
         *  [Adaptive Icons](https://developer.android.com/guide/practices/ui_guidelines/icon_design_adaptive)
         *  . This means that the icon needs to fit within a circle whose diameter is 2/3 the size of the
         *  icon. The actual values don't really matter if you use a vector icon.
         *
         *  ## Specs
         *  - With icon background (`Theme.SplashScreen.IconBackground`)
         *    + Image Size: 240x240 dp
         *    + Inner Circle diameter: 160 dp
         *  - Without icon background  (`Theme.SplashScreen`)
         *     + Image size: 288x288 dp
         *     + Inner circle diameter: 192 dp
         *
         *  _Example:_ if the full size of the image is 300dp*300dp, the icon needs to fit within a
         *  circle with a diameter of 200dp. Everything outside the circle will be invisible (masked).
         *
         */
        """
            .trimIndent()
    checkFormatter(
        source,
        KDocFormattingOptions(72, 72),
        """
        /**
         * Provides control over the splash screen once the application is
         * started.
         *
         * On API 31+ (Android 12+) this class calls the platform methods.
         *
         * Prior API 31, the platform behavior is replicated with the
         * exception of the Animated Vector Drawable support on the launch
         * screen.
         *
         * # Usage of the `core-splashscreen` library:
         *
         * To replicate the splash screen behavior from Android 12 on older
         * APIs the following steps need to be taken:
         * 1. Create a new Theme (e.g `Theme.App.Starting`) and set its
         *    parent to `Theme.SplashScreen` or
         *    `Theme.SplashScreen.IconBackground`
         *
         * 2. In your manifest, set the `theme` attribute of the whole
         *    `<application>` or just the starting `<activity>` to
         *    `Theme.App.Starting`
         *
         * 3. In the `onCreate` method the starting activity, call
         *    [installSplashScreen] just before `super.onCreate()`. You also
         *    need to make sure that `postSplashScreenTheme` is set to the
         *    application's theme. Alternatively, this call can be replaced
         *    by [Activity#setTheme] if a [SplashScreen] instance isn't
         *    needed.
         *
         * ## Themes
         *
         * The library provides two themes: [R.style.Theme_SplashScreen]
         * and [R.style.Theme_SplashScreen_IconBackground]. If you wish to
         * display a background right under your icon, the later needs to
         * be used. This ensure that the scale and masking of the icon are
         * similar to the Android 12 Splash Screen.
         *
         * `windowSplashScreenAnimatedIcon`: The splash screen icon. On API
         * 31+ it can be an animated vector drawable.
         *
         * `windowSplashScreenAnimationDuration`: Duration of the Animated
         * Icon Animation. The value needs to be > 0 if the icon is
         * animated.
         *
         * **Note:** This has no impact on the time during which
         * the splash screen is displayed and is only used in
         * [SplashScreenViewProvider.iconAnimationDurationMillis]. If you
         * need to display the splash screen for a longer time, you can use
         * [SplashScreen.setKeepOnScreenCondition]
         *
         * `windowSplashScreenIconBackgroundColor`: _To be used in with
         * `Theme.SplashScreen.IconBackground`_. Sets a background color
         * under the splash screen icon.
         *
         * `windowSplashScreenBackground`: Background color of the splash
         * screen. Defaults to the theme's `?attr/colorBackground`.
         *
         * `postSplashScreenTheme`* Theme to apply to the Activity when
         * [installSplashScreen] is called.
         *
         * **Known incompatibilities:**
         * - On API < 31, `windowSplashScreenAnimatedIcon` cannot be
         *   animated. If you want to provide an animated icon for API 31+
         *   and a still icon for API <31, you can do so by overriding the
         *   still icon with an animated vector drawable in
         *   `res/drawable-v31`.
         *
         * - On API < 31, if the value of `windowSplashScreenAnimatedIcon`
         *   is an
         *   [adaptive icon](http://developer.android.com/guide/practices/ui_guidelines/icon_design_adaptive)
         *   , it will be cropped and scaled. The workaround is to
         *   respectively assign `windowSplashScreenAnimatedIcon` and
         *   `windowSplashScreenIconBackgroundColor` to the values of the
         *   adaptive icon `foreground` and `background`.
         *
         * - On API 21-22, The icon isn't displayed until the application
         *   starts, only the background is visible.
         *
         * # Design
         * The splash screen icon uses the same specifications as
         * [Adaptive Icons](https://developer.android.com/guide/practices/ui_guidelines/icon_design_adaptive)
         * . This means that the icon needs to fit within a circle whose
         * diameter is 2/3 the size of the icon. The actual values don't
         * really matter if you use a vector icon.
         *
         * ## Specs
         * - With icon background (`Theme.SplashScreen.IconBackground`)
         *    + Image Size: 240x240 dp
         *    + Inner Circle diameter: 160 dp
         * - Without icon background (`Theme.SplashScreen`)
         *       + Image size: 288x288 dp
         *       + Inner circle diameter: 192 dp
         *
         * _Example:_ if the full size of the image is 300dp*300dp, the icon
         * needs to fit within a circle with a diameter of 200dp. Everything
         * outside the circle will be invisible (masked).
         */
        """
            .trimIndent(),
    )
  }

  @Test
  fun testRaggedIndentation() {
    // From Dokka's plugins/base/src/main/kotlin/translators/psi/parsers/JavadocParser.kt
    val source =
        """
        /**
         * We would like to know if we need to have a space after a this tag
         *
         * The space is required when:
         *  - tag spans multiple lines, between every line we would need a space
         *
         *  We wouldn't like to render a space if:
         *  - tag is followed by an end of comment
         *  - after a tag there is another tag (eg. multiple @author tags)
         *  - they end with an html tag like: <a href="...">Something</a> since then the space will be displayed in the following text
         *  - next line starts with a <p> or <pre> token
         */
        """
            .trimIndent()
    checkFormatter(
        source,
        KDocFormattingOptions(72, 72),
        """
        /**
         * We would like to know if we need to have a space after a this tag
         *
         * The space is required when:
         * - tag spans multiple lines, between every line we would need a
         *   space
         *
         * We wouldn't like to render a space if:
         * - tag is followed by an end of comment
         * - after a tag there is another tag (eg. multiple @author tags)
         * - they end with an html tag like: <a href="...">Something</a>
         *   since then the space will be displayed in the following text
         * - next line starts with a <p> or <pre> token
         */
        """
            .trimIndent(),
    )
  }

  @Test
  fun testCustomKDocTag() {
    // From Dokka's core/testdata/comments/multilineSection.kt
    val source =
        """
        /**
         * Summary
         * @one
         *   line one
         *   line two
         */
        """
            .trimIndent()
    checkFormatter(
        source,
        KDocFormattingOptions(72, 72),
        """
        /**
         * Summary
         *
         * @one line one line two
         */
        """
            .trimIndent(),
    )
  }

  @Test
  fun testTables() {
    val source =
        """
        /**
         * ### Tables
         * column 1 | column 2
         * ---------|---------
         * value\| 1  | value 2
         */
        """
            .trimIndent()
    checkFormatter(
        source,
        KDocFormattingOptions(40),
        """
        /**
         * ### Tables
         * | column 1  | column 2 |
         * |-----------|----------|
         * | value\| 1 | value 2  |
         */
        """
            .trimIndent(),
    )
  }

  @Test
  fun testTableMixedWithHtml() {
    // https://stackoverflow.com/questions/19950648/how-to-write-lists-inside-a-markdown-table
    val source =
        """
        /**
         | Tables        | Are           | Cool  |
         | ------------- |:-------------:| -----:|
         | col 3 is      | right-aligned |  1600 |
         | col 2 is      | centered      |    12 |
         | zebra stripes | are neat      |     1 |
         | <ul><li>item1</li><li>item2</li></ul>| See the list | from the first column|
        */
        """
            .trimIndent()
    checkFormatter(
        source,
        KDocFormattingOptions(100),
        """
        /**
         * | Tables                                | Are           | Cool                  |
         * |---------------------------------------|:-------------:|----------------------:|
         * | col 3 is                              | right-aligned |                  1600 |
         * | col 2 is                              |   centered    |                    12 |
         * | zebra stripes                         |   are neat    |                     1 |
         * | <ul><li>item1</li><li>item2</li></ul> | See the list  | from the first column |
         */
        """
            .trimIndent(),
    )

    // Reduce formatting width to 40; table won't fit, but we'll skip the padding
    checkFormatter(
        source,
        KDocFormattingOptions(40),
        """
        /**
         * |Tables                               |Are          |Cool                 |
         * |-------------------------------------|:-----------:|--------------------:|
         * |col 3 is                             |right-aligned|                 1600|
         * |col 2 is                             |  centered   |                   12|
         * |zebra stripes                        |  are neat   |                    1|
         * |<ul><li>item1</li><li>item2</li></ul>|See the list |from the first column|
         */
        """
            .trimIndent(),
    )

    checkFormatter(
        source,
        KDocFormattingOptions(40).apply { alignTableColumns = false },
        """
        /**
         * | Tables        | Are           | Cool  |
         * | ------------- |:-------------:| -----:|
         * | col 3 is      | right-aligned |  1600 |
         * | col 2 is      | centered      |    12 |
         * | zebra stripes | are neat      |     1 |
         * | <ul><li>item1</li><li>item2</li></ul>| See the list | from the first column|
         */
        """
            .trimIndent(),
    )
  }

  @Test
  fun testTableExtraCells() {
    // If there are extra columns in a row (after the header and divider),
    // preserve these (though Dokka will drop them from the rendering); don't
    // widen the table to accommodate it.
    val source =
        """
        /**
         | Tables        | Are           | Cool  |
         | ------------- |:-------------:| -----:|
         | col 3 is      | right-aligned |  1600 |
         | col 2 is      | centered      |    12 | extra
         | zebra stripes | are neat      |     1 |
        */
        """
            .trimIndent()
    checkFormatter(
        source,
        KDocFormattingOptions(100),
        """
        /**
         * | Tables        | Are           | Cool |
         * |---------------|:-------------:|-----:|
         * | col 3 is      | right-aligned | 1600 |
         * | col 2 is      |   centered    |   12 | extra |
         * | zebra stripes |   are neat    |    1 |
         */
        """
            .trimIndent(),
    )
  }

  @Test
  fun testTables2() {
    // See https://github.com/Kotlin/dokka/issues/199
    val source =
        """
        /**
         * | Level | Color |
         * | ----- | ----- |
         * | ERROR | RED |
         * | WARN | YELLOW |
         */
        """
            .trimIndent()
    checkFormatter(
        source,
        KDocFormattingOptions(40),
        """
        /**
         * | Level | Color  |
         * |-------|--------|
         * | ERROR | RED    |
         * | WARN  | YELLOW |
         */
        """
            .trimIndent(),
    )

    // With alignTableColumns=false, leave formatting within table cells alone
    checkFormatter(
        source,
        KDocFormattingOptions(40).apply { alignTableColumns = false },
        """
        /**
         * | Level | Color |
         * | ----- | ----- |
         * | ERROR | RED |
         * | WARN | YELLOW |
         */
        """
            .trimIndent(),
    )
  }

  @Test
  fun testTables3() {
    val source =
        """
        /**
         * Line Before
         * # test
         * |column 1 | column 2 | column3
         * |---|---|---
         * value 1  | value 3
         * this is missing
         * this is more
         */
        """
            .trimIndent()
    checkFormatter(
        source,
        KDocFormattingOptions(40).apply { alignTableColumns = true },
        """
        /**
         * Line Before
         *
         * # test
         * | column 1 | column 2 | column3 |
         * |----------|----------|---------|
         * | value 1  | value 3  |         |
         *
         * this is missing this is more
         */
        """
            .trimIndent(),
    )

    checkFormatter(
        source,
        KDocFormattingOptions(40).apply { alignTableColumns = false },
        """
        /**
         * Line Before
         *
         * # test
         * |column 1 | column 2 | column3
         * |---|---|---
         * value 1  | value 3
         *
         * this is missing this is more
         */
        """
            .trimIndent(),
    )
  }

  @Test
  fun testTables4() {
    // Test short dividers (:--, :-:, --:)
    val source =
        """
        /**
         * ### Tables
         * column 1 | column 2 | column3
         *  :-:|--:|:--
         * cell 1|cell2|cell3
         */
        """
            .trimIndent()
    checkFormatter(
        source,
        KDocFormattingOptions(40),
        """
        /**
         * ### Tables
         * | column 1 | column 2 | column3 |
         * |:--------:|---------:|---------|
         * |  cell 1  |    cell2 | cell3   |
         */
        """
            .trimIndent(),
        // Dokka doesn't actually handle this right; it looks for ---
        verifyDokka = false,
    )
  }

  @Test
  fun testTablesEmptyCells() {
    // Checks what happens with blank cells (here in column 0 on the last row). Test case from
    // Studio's
    // designer/testSrc/com/android/tools/idea/uibuilder/property/testutils/AndroidAttributeTypeLookup.kt
    val source =
        """
        /**
         * | Function                         | Type                            | Notes                                 |
         * | -------------------------------- | ------------------------------- | --------------------------------------|
         * | TypedArray.getDrawable           | NlPropertyType.DRAWABLE         |                                       |
         * | TypedArray.getColor              | NlPropertyType.COLOR            | Make sure this is not a color list !! |
         * | TypedArray.getColorStateList     | NlPropertyType.COLOR_STATE_LIST |                                       |
         * | TypedArray.getDimensionPixelSize | NlPropertyType.DIMENSION        |                                       |
         * | TypedArray.getResourceId         | NlPropertyType.ID               |                                       |
         * | TypedArray.getInt                | NlPropertyType.ENUM             | If attrs.xml defines this as an enum  |
         * |                                  | NlPropertyType.INTEGER          | If this is not an enum                |
         */
        """
            .trimIndent()
    checkFormatter(
        source,
        KDocFormattingOptions(72),
        """
        /**
         * |Function                        |Type                           |Notes                                |
         * |--------------------------------|-------------------------------|-------------------------------------|
         * |TypedArray.getDrawable          |NlPropertyType.DRAWABLE        |                                     |
         * |TypedArray.getColor             |NlPropertyType.COLOR           |Make sure this is not a color list !!|
         * |TypedArray.getColorStateList    |NlPropertyType.COLOR_STATE_LIST|                                     |
         * |TypedArray.getDimensionPixelSize|NlPropertyType.DIMENSION       |                                     |
         * |TypedArray.getResourceId        |NlPropertyType.ID              |                                     |
         * |TypedArray.getInt               |NlPropertyType.ENUM            |If attrs.xml defines this as an enum |
         * |                                |NlPropertyType.INTEGER         |If this is not an enum               |
         */
        """
            .trimIndent(),
    )
  }

  @Test
  fun testTables5() {
    // Test case from Studio's
    // project-system-gradle-upgrade/src/com/android/tools/idea/gradle/project/upgrade/AgpUpgradeRefactoringProcessor.kt
    val source =
        """
        /**
        | 1 | 2 | 3 | 4 | Necessity
        |---|---|---|---|----------
        |v_n|v_o|cur|new| [IRRELEVANT_PAST]
        |cur|new|v_n|v_o| [IRRELEVANT_FUTURE]
        |cur|v_n|v_o|new| [MANDATORY_CODEPENDENT] (must do the refactoring in the same action as the AGP version upgrade)
        |v_n|cur|v_o|new| [MANDATORY_INDEPENDENT] (must do the refactoring, but can do it before the AGP version upgrade)
        |cur|v_n|new|v_o| [OPTIONAL_CODEPENDENT] (need not do the refactoring, but if done must be with or after the AGP version upgrade)
        |v_n|cur|new|v_o| [OPTIONAL_INDEPENDENT] (need not do the refactoring, but if done can be at any point in the process)

        For the possibly-simpler case where we have a discontinuity in behaviour, v_o = v_n = vvv, and the three possible cases are:

        | 1 | 2 | 3 | Necessity
        |---|---|---|----------
        |vvv|cur|new| [IRRELEVANT_PAST]
        |cur|vvv|new| [MANDATORY_CODEPENDENT]
        |cur|new|vvv| [IRRELEVANT_FUTURE]

        (again in case of equality, vvv sorts before cur and new)
        */
        """
            .trimIndent()
    checkFormatter(
        source,
        KDocFormattingOptions(72),
        """
        /**
         * |1  |2  |3  |4  |Necessity                                                                                                      |
         * |---|---|---|---|---------------------------------------------------------------------------------------------------------------|
         * |v_n|v_o|cur|new|[IRRELEVANT_PAST]                                                                                              |
         * |cur|new|v_n|v_o|[IRRELEVANT_FUTURE]                                                                                            |
         * |cur|v_n|v_o|new|[MANDATORY_CODEPENDENT] (must do the refactoring in the same action as the AGP version upgrade)                |
         * |v_n|cur|v_o|new|[MANDATORY_INDEPENDENT] (must do the refactoring, but can do it before the AGP version upgrade)                |
         * |cur|v_n|new|v_o|[OPTIONAL_CODEPENDENT] (need not do the refactoring, but if done must be with or after the AGP version upgrade)|
         * |v_n|cur|new|v_o|[OPTIONAL_INDEPENDENT] (need not do the refactoring, but if done can be at any point in the process)           |
         *
         * For the possibly-simpler case where we have a discontinuity in
         * behaviour, v_o = v_n = vvv, and the three possible cases are:
         *
         * | 1   | 2   | 3   | Necessity               |
         * |-----|-----|-----|-------------------------|
         * | vvv | cur | new | [IRRELEVANT_PAST]       |
         * | cur | vvv | new | [MANDATORY_CODEPENDENT] |
         * | cur | new | vvv | [IRRELEVANT_FUTURE]     |
         *
         * (again in case of equality, vvv sorts before cur and new)
         */
        """
            .trimIndent(),
        indent = "",
    )
  }

  @Test
  fun testTables6() {
    // Test case from IntelliJ's
    // plugins/kotlin/idea/tests/testData/editor/quickDoc/OnFunctionDeclarationWithGFMTable.kt
    val source =
        """
        /**
         * | left  | center | right | default |
         * | :---- | :----: | ----: | ------- |
         * | 1     | 2      | 3     | 4       |
         *
         *
         * | foo | bar | baz |
         * | --- | --- | --- |
         * | 1   | 2   |
         * | 3   | 4   | 5   | 6 |
         *
         * | header | only |
         * | ------ | ---- |
         */
        """
            .trimIndent()
    checkFormatter(
        source,
        KDocFormattingOptions(72),
        """
        /**
         * | left | center | right | default |
         * |------|:------:|------:|---------|
         * | 1    |   2    |     3 | 4       |
         *
         * | foo | bar | baz |
         * |-----|-----|-----|
         * | 1   | 2   |     |
         * | 3   | 4   | 5   | 6   |
         *
         * | header | only |
         * |--------|------|
         */
        """
            .trimIndent(),
        indent = "",
    )
  }

  @Test
  fun testTables7() {
    val source =
        """
        /**
         * This is my code
         * @author Me
         * And here's.
         * Another.
         * Thing.
        *
         * my | table
         * ---|---
         * item 1|item 2
         * item 3|
         * item 4|item 5
         */
        """
            .trimIndent()
    checkFormatter(
        source,
        KDocFormattingOptions(72),
        """
        /**
         * This is my code
         *
         * @author Me And here's. Another. Thing.
         *
         * | my     | table  |
         * |--------|--------|
         * | item 1 | item 2 |
         * | item 3 |        |
         * | item 4 | item 5 |
         */
        """
            .trimIndent(),
        indent = "",
    )
  }

  @Test
  fun testTables7b() {
    val source =
        """
        /**
         * This is my code
         * @author Me
         * Plain text.
        *
         * my | table
         * ---|---
         * item 1|item 2
         * item 3|
         * item 4|item 5
         */
        """
            .trimIndent()
    checkFormatter(
        source,
        KDocFormattingOptions(72).apply {
          orderDocTags = false
          alignTableColumns = false
        },
        """
        /**
         * This is my code
         *
         * @author Me Plain text.
         *
         * my | table
         * ---|---
         * item 1|item 2
         * item 3|
         * item 4|item 5
         */
        """
            .trimIndent(),
        indent = "",
    )
  }

  @Test
  fun testBulletsUnderParamTags() {
    // Regression test for
    // https://github.com/tnorbye/kdoc-formatter/issues/56
    val source =
        """
        /**
         * This supports bullets
         * - one
         * - two
         *
         * @param thisDoesNot
         * Here's some parameter text.
         * - a
         * - b
         * Here's some more text
         *
         * And here's even more parameter doc text.
         *
         * @param another paragraph
         * * With some bulleted items
         *   * Even nested ones
         * ```
         * and some preformatted text
         * ```
         */
        """
            .trimIndent()
    checkFormatter(
        source,
        KDocFormattingOptions(maxLineWidth = 72).apply { orderDocTags = false },
        """
        /**
         * This supports bullets
         * - one
         * - two
         *
         * @param thisDoesNot Here's some parameter text.
         * - a
         * - b Here's some more text
         *
         * And here's even more parameter doc text.
         *
         * @param another paragraph
         * * With some bulleted items
         *    * Even nested ones
         *
         * ```
         * and some preformatted text
         * ```
         */
        """
            .trimIndent(),
    )
  }

  @Test
  fun testLineBreaking() {
    // Regression test for
    // https://github.com/tnorbye/kdoc-formatter/issues/57
    val source =
        """
        /** aa aa aa aa a */
        """
            .trimIndent()
    checkFormatter(
        source,
        KDocFormattingOptions(maxLineWidth = 20, maxCommentWidth = 20).apply { optimal = false },
        """
        /** aa aa aa aa a */
        """
            .trimIndent(),
        indent = "",
    )
  }

  @Test
  fun testPreTag() {
    // Regression test for
    // https://github.com/tnorbye/kdoc-formatter/issues/58
    val source =
        """
        /**
         * This tag messes things up.
         * <pre>
         * This is pre.
         *
         * @return some correct
         * value
         */
        """
            .trimIndent()
    checkFormatter(
        source,
        KDocFormattingOptions(maxLineWidth = 72),
        """
        /**
         * This tag messes things up.
         * <pre>
         * This is pre.
         *
         * @return some correct
         * value
         */
        """
            .trimIndent(),
        verifyDokka = false, // this triggers a bug in the diff lookup; TODO investigate
    )
  }

  @Test
  fun testPreTag2() {
    // Regression test for
    // https://github.com/tnorbye/kdoc-formatter/issues/58
    val source =
        """
        /**
         * Even if it's closed.
         * <pre>My Pre</pre>
         *
         * @return some correct
         * value
         */
        """
            .trimIndent()
    checkFormatter(
        source,
        KDocFormattingOptions(maxLineWidth = 72),
        """
        /**
         * Even if it's closed.
         *
         * ```
         * My Pre
         * ```
         *
         * @return some correct value
         */
        """
            .trimIndent(),
        // <pre> and ``` are rendered differently; this is an intentional diff
        verifyDokka = false,
    )
  }

  @Test
  fun testPreTag3() {
    // From Studio's
    // build-system/builder-model/src/main/java/com/android/builder/model/DataBindingOptions.kt
    val source =
        """
        /**
         * Whether we want tests to be able to use data binding as well.
         *
         * <p>
         * Data Binding classes generated from the application can always be
         * accessed in the test code but test itself cannot introduce new
         * Data Binding layouts, bindables etc unless this flag is turned
         * on.
         *
         * <p>
         * This settings help with an issue in older devices where class
         * verifier throws an exception when the application class is
         * overwritten by the test class. It also makes it easier to run
         * proguarded tests.
         */
        """
            .trimIndent()
    checkFormatter(
        source,
        KDocFormattingOptions(maxLineWidth = 72),
        """
        /**
         * Whether we want tests to be able to use data binding as well.
         *
         * Data Binding classes generated from the application can always be
         * accessed in the test code but test itself cannot introduce new
         * Data Binding layouts, bindables etc unless this flag is turned
         * on.
         *
         * This settings help with an issue in older devices where class
         * verifier throws an exception when the application class is
         * overwritten by the test class. It also makes it easier to run
         * proguarded tests.
         */
        """
            .trimIndent(),
    )
  }

  @Test
  fun testNoConversionInReferences() {
    val source =
        """
        /**
         * A thread safe in-memory cache of [Key&lt;T&gt;][Key] to `T` values whose lifetime is tied
         * to a [CoroutineScope].
         */
        """
            .trimIndent()
    checkFormatter(
        source,
        KDocFormattingOptions(maxLineWidth = 72),
        """
        /**
         * A thread safe in-memory cache of [Key&lt;T&gt;][Key] to `T` values
         * whose lifetime is tied to a [CoroutineScope].
         */
        """
            .trimIndent(),
        indent = "",
    )
  }

  @Test
  fun testCaseSensitiveMarkup() {
    // Regression test for
    // https://github.com/tnorbye/kdoc-formatter/issues/59
    val source =
        """
        /** <A> to <B> should remain intact, not <b>bolded</b> */
        """
            .trimIndent()
    checkFormatter(
        source,
        KDocFormattingOptions(maxLineWidth = 72),
        """
        /** <A> to <B> should remain intact, not **bolded** */
        """
            .trimIndent(),
        // This is a broken comment (unterminated <B> etc) so the behaviors differ
        verifyDokka = false,
    )
  }

  @Test
  fun testAsteriskRemoval() {
    // Regression test for
    // https://github.com/tnorbye/kdoc-formatter/issues/60
    val source =
        """
        /** *** Testing */
        """
            .trimIndent()
    checkFormatter(
        source,
        KDocFormattingOptions(maxLineWidth = 72),
        """
        /** *** Testing */
        """
            .trimIndent(),
    )
  }

  @Test
  fun testParagraphTagRemoval() {
    // Regression test for
    // https://github.com/tnorbye/kdoc-formatter/issues/61
    val source =
        """
        /**
         * Ptag removal should remove extra space
         *
         * <p> Some paragraph
         */
        """
            .trimIndent()
    checkFormatter(
        source,
        KDocFormattingOptions(maxLineWidth = 72),
        """
        /**
         * Ptag removal should remove extra space
         *
         * Some paragraph
         */
        """
            .trimIndent(),
    )
  }

  @Test
  fun testDashedLineIndentation() {
    // Regression test for
    // https://github.com/tnorbye/kdoc-formatter/issues/62
    val source =
        """
        /**
         * Some summary.
         *
         * - Some bullet.
         *
         * ------------------------------------------------------------------------------
         *
         * Some paragraph.
         */
        """
            .trimIndent()
    checkFormatter(
        source,
        KDocFormattingOptions(maxLineWidth = 72),
        """
        /**
         * Some summary.
         *
         * - Some bullet.
         *
         * ------------------------------------------------------------------------------
         *
         * Some paragraph.
         */
        """
            .trimIndent(),
    )
  }

  @Test
  fun testParagraphRemoval() {
    // Regression test for
    // https://github.com/tnorbye/kdoc-formatter/issues/63
    val source =
        """
        /**
         * 1. Test
         *
         * <p>2. Test
         */
        """
            .trimIndent()
    checkFormatter(
        source,
        KDocFormattingOptions(maxLineWidth = 72),
        """
        /**
         * 1. Test
         *
         * 2. Test
         */
        """
            .trimIndent(),
        // Blank line is preserved (source had blank line before <p>2.)
        verifyDokka = false,
    )
  }

  @Test
  fun testParagraphRemoval2() {
    // Regression test for
    // https://github.com/tnorbye/kdoc-formatter/issues/69
    val source =
        """
        /**
         * Some title
         *
         * <p>1. Test
         * 2. Test
         */
        """
            .trimIndent()
    checkFormatter(
        source,
        KDocFormattingOptions(maxLineWidth = 72),
        """
        /**
         * Some title
         *
         * 1. Test
         * 2. Test
         */
        """
            .trimIndent(),
        // Blank line is preserved (source had blank line before <p>1.)
        verifyDokka = false,
    )
  }

  @Test
  fun testAtBreak2() {
    // Regression test for
    // https://github.com/tnorbye/kdoc-formatter/issues/64
    // This behavior is deliberate: we cannot put @aa at the beginning of a new line;
    // if so KDoc will treat it as a doc and silently drop it because it isn't a known
    // custom tag.
    val source =
        """
        /**
         * aa aa aa aa aa @aa
         */
        """
            .trimIndent()
    checkFormatter(
        source,
        KDocFormattingOptions(maxLineWidth = 20, maxCommentWidth = 20),
        """
        /**
         * aa aa aa aa
         * aa @aa
         */
        """
            .trimIndent(),
    )
  }

  @Test
  fun testNoBreakAfterAt() {
    // Regression test for
    // https://github.com/tnorbye/kdoc-formatter/issues/65
    val source =
        """
        /**
         * Weird break
         *
         * alink aaaaaaa
         *
         * @param a aaaaaa
         * @link aaaaaaa
         */
        """
            .trimIndent()
    checkFormatter(
        source,
        KDocFormattingOptions(maxLineWidth = 20, maxCommentWidth = 20),
        """
        /**
         * Weird break
         *
         * alink aaaaaaa
         *
         * @param a aaaaaa
         * @link aaaaaaa
         */
        """
            .trimIndent(),
        indent = "",
    )
  }

  @Test
  fun testPreCodeConversion() {
    val source =
        """
        /**
         * <pre><code>
         * More sample code.
         * </code></pre>
         */
        """
            .trimIndent()
    checkFormatter(
        source,
        KDocFormattingOptions(maxLineWidth = 72),
        """
        /**
         * ```
         * More sample code.
         * ```
         */
        """
            .trimIndent(),
        indent = "        ",
        // <pre> and ``` are rendered differently; this is an intentional diff
        verifyDokka = false,
    )
  }

  @Test
  fun testPreConversion2() {
    // From AndroidX and Studio methods
    val source =
        """
        /**
         * Checks if any of the GL calls since the last time this method was called set an error
         * condition. Call this method immediately after calling a GL method. Pass the name of the GL
         * operation. For example:
         *
         * <pre>
         * mColorHandle = GLES20.glGetUniformLocation(mProgram, "uColor");
         * MyGLRenderer.checkGlError("glGetUniformLocation");</pre>
         *
         * If the operation is not successful, the check throws an exception.
         *
         * <pre>public performItemClick(T item) {
         *   ...
         *   sendEventForVirtualView(item.id, AccessibilityEvent.TYPE_VIEW_CLICKED)
         * }
         * </pre>
         * *Note* This is quite slow so it's best to use it sparingly in production builds.
         * Injector to load associated file. It will create code like:
         * <pre>file = FileUtil.loadLabels(extractor.getAssociatedFile(fileName))</pre>
         */
        """
            .trimIndent()
    checkFormatter(
        source,
        KDocFormattingOptions(maxLineWidth = 72),
        """
        /**
         * Checks if any of the GL calls since the last time this
         * method was called set an error condition. Call this method
         * immediately after calling a GL method. Pass the name of the
         * GL operation. For example:
         * ```
         * mColorHandle = GLES20.glGetUniformLocation(mProgram, "uColor");
         * MyGLRenderer.checkGlError("glGetUniformLocation");
         * ```
         *
         * If the operation is not successful, the check throws an
         * exception.
         *
         * ```
         * public performItemClick(T item) {
         *   ...
         *   sendEventForVirtualView(item.id, AccessibilityEvent.TYPE_VIEW_CLICKED)
         * }
         * ```
         *
         * *Note* This is quite slow so it's best to use it sparingly in
         * production builds. Injector to load associated file. It will
         * create code like:
         * ```
         * file = FileUtil.loadLabels(extractor.getAssociatedFile(fileName))
         * ```
         */
        """
            .trimIndent(),
        indent = "        ",
        // <pre> and ``` are rendered differently; this is an intentional diff
        verifyDokka = false,
    )
  }

  @Test
  fun testOpenRange() {
    // https://github.com/tnorbye/kdoc-formatter/issues/84
    val source =
        """
        /**
         * This is a line that has the length such that this [link gets
         * broken across lines]() which is not valid.
         *
         * Input is a float in range
         * [0, 1) where 0 is fully settled and 1 is dismissed. Will be continue to be called after the user's finger has lifted. Will not be called for if not dismissible.
         */
        """
            .trimIndent()
    checkFormatter(
        source,
        KDocFormattingOptions(maxLineWidth = 72),
        """
        /**
         * This is a line that has the length such that this
         * [link gets broken across lines]() which is not valid.
         *
         * Input is a float in range [0, 1) where 0 is fully settled and 1 is
         * dismissed. Will be continue to be called after the user's finger has
         * lifted. Will not be called for if not dismissible.
         */
        """
            .trimIndent(),
        indent = "",
    )
  }

  @Test
  fun testPropertiesWithBrackets() {
    val source =
        // From AOSP
        // tools/base/build-system/gradle-core/src/main/java/com/android/build/gradle/internal/cxx/prefab/PackageModel.kt
        """
        /**
         * The Android abi.json schema.
         *
         * @property[abi] The ABI name of the described library. These names match the tag field for
         * [com.android.build.gradle.internal.core.Abi].
         * @property[api] The minimum OS version supported by the library. i.e. the
         * library's `minSdkVersion`.
         * @property[ndk] The major version of the NDK that this library was built with.
         * @property[stl] The STL that this library was built with.
         * @property[static] If true then the library is .a, if false then .so.
         */
        """
            .trimIndent()
    checkFormatter(
        source,
        KDocFormattingOptions(maxLineWidth = 72),
        """
        /**
         * The Android abi.json schema.
         *
         * @property[abi] The ABI name of the described library. These names
         *   match the tag field for
         *   [com.android.build.gradle.internal.core.Abi].
         * @property[api] The minimum OS version supported by the library. i.e.
         *   the library's `minSdkVersion`.
         * @property[ndk] The major version of the NDK that this library was
         *   built with.
         * @property[stl] The STL that this library was built with.
         * @property[static] If true then the library is .a, if false then .so.
         */
        """
            .trimIndent(),
        indent = "",
    )
  }

  @Test
  fun testHandingIndent() {
    val source =
        """
            /**
             * @param count this is how many you
             *   can fit in a [Bag]
             * @param weight how heavy this would
             *     be in [Grams]
             */
            """
    checkFormatter(
        source,
        KDocFormattingOptions(maxLineWidth = 72),
        """
        /**
         * @param count this is how many you can fit in a [Bag]
         * @param weight how heavy this would be in [Grams]
         */
        """
            .trimIndent(),
        indent = "",
    )
  }

  @Test
  fun testMarkupAcrossLines() {
    val source =
        """
        /**
         * Broadcast Action: Indicates the Bluetooth scan mode of the local Adapter
         * has changed.
         * <p>Always contains the extra fields {@link #EXTRA_SCAN_MODE} and {@link
         * #EXTRA_PREVIOUS_SCAN_MODE} containing the new and old scan modes
         * respectively.
         */
        """
            .trimIndent()
    checkFormatter(
        source,
        KDocFormattingOptions(maxLineWidth = 72),
        """
        /**
         * Broadcast Action: Indicates the Bluetooth scan mode of the local
         * Adapter has changed.
         *
         * Always contains the extra fields [EXTRA_SCAN_MODE] and
         * [EXTRA_PREVIOUS_SCAN_MODE] containing the new and old scan modes
         * respectively.
         */
        """
            .trimIndent(),
        indent = "",
    )
  }

  @Test
  fun testReferences() {
    val source =
        """
        /**
         * Construct a rectangle from its left and top edges as well as its width and height.
         * @param offset Offset to represent the top and left parameters of the Rect
         * @param size Size to determine the width and height of this [Rect].
         * @return Rect with [Rect.left] and [Rect.top] configured to [Offset.x] and [Offset.y] as
         * [Rect.right] and [Rect.bottom] to [Offset.x] + [Size.width] and [Offset.y] + [Size.height]
         * respectively
         */
        """
            .trimIndent()
    checkFormatter(
        source,
        KDocFormattingOptions(maxLineWidth = 72),
        """
        /**
         * Construct a rectangle from its left and top edges as well as its
         * width and height.
         *
         * @param offset Offset to represent the top and left parameters of the
         *   Rect
         * @param size Size to determine the width and height of this [Rect].
         * @return Rect with [Rect.left] and [Rect.top] configured to [Offset.x]
         *   and [Offset.y] as [Rect.right] and [Rect.bottom] to
         *   [Offset.x] + [Size.width] and [Offset.y] + [Size.height]
         *   respectively
         */
        """
            .trimIndent(),
        indent = "",
    )
  }

  @Test
  fun testDecapitalizeKdocTags() {
    val source =
        """
        /**
         * Represents a component that handles scroll events, so that other components in the hierarchy
         * can adjust their behaviour.
         * @See [provideScrollContainerInfo] and [consumeScrollContainerInfo]
         */
        """
            .trimIndent()
    checkFormatter(
        source,
        KDocFormattingOptions(maxLineWidth = 72).apply { convertMarkup = true },
        """
        /**
         * Represents a component that handles scroll events, so that other
         * components in the hierarchy can adjust their behaviour.
         *
         * @see [provideScrollContainerInfo] and [consumeScrollContainerInfo]
         */
        """
            .trimIndent(),
        indent = "",
    )
  }

  @Test
  fun testLineBreak() {
    // Makes sure a scenario where we used to put "0." at the beginning of a new line.
    // From AOSP's
    // frameworks/support/graphics/graphics-core/src/main/java/androidx/graphics/surface/SurfaceControlWrapper.kt
    val source =
        """
        /**
         * Updates z order index for [SurfaceControlWrapper]. Note that the z order for a
         * surface is relative to other surfaces that are siblings of this surface.
         * Behavior of siblings with the same z order is undefined.
         *
         * Z orders can range from Integer.MIN_VALUE to Integer.MAX_VALUE. Default z order
         * index is 0. [SurfaceControlWrapper] instances are positioned back-to-front. That is
         * lower z order values are rendered below other [SurfaceControlWrapper] instances with
         * higher z order values.
         *
         * @param surfaceControl surface control to set the z order of.
         *
         * @param zOrder desired layer z order to set the surfaceControl.
         */
        """
            .trimIndent()
    checkFormatter(
        source,
        KDocFormattingOptions(maxLineWidth = 72),
        """
        /**
         * Updates z order index for [SurfaceControlWrapper]. Note that
         * the z order for a surface is relative to other surfaces that
         * are siblings of this surface. Behavior of siblings with the
         * same z order is undefined.
         *
         * Z orders can range from Integer.MIN_VALUE
         * to Integer.MAX_VALUE. Default z order index
         * is 0. [SurfaceControlWrapper] instances are positioned
         * back-to-front. That is lower z order values are rendered
         * below other [SurfaceControlWrapper] instances with higher z
         * order values.
         *
         * @param surfaceControl surface control to set the z order of.
         * @param zOrder desired layer z order to set the
         *   surfaceControl.
         */
        """
            .trimIndent(),
        indent = "        ",
    )
  }

  @Test
  fun testDocTagsInsidePreformatted() {
    // Makes sure we don't treat markup inside preformatted text as potential
    // doc tags (with the fix to make us flexible recognize @See as a doctag
    // it revealed we were also looking inside preformatted text and started
    // treating annotations like @Retention as a doc tag.)
    val source =
        """
        /**
         * Denotes that the annotated element of integer type, represents
         * a logical type and that its value should be one of the explicitly
         * named constants. If the IntDef#flag() attribute is set to true,
         * multiple constants can be combined.
         *
         * Example:
         * ```
         * @Retention(SOURCE)
         * @IntDef({NAVIGATION_MODE_STANDARD, NAVIGATION_MODE_LIST, NAVIGATION_MODE_TABS})
         * public @interface NavigationMode {}
         * public static final int NAVIGATION_MODE_STANDARD = 0;
         * public static final int NAVIGATION_MODE_LIST = 1;
         * public static final int NAVIGATION_MODE_TABS = 2;
         * ...
         * public abstract void setNavigationMode(@NavigationMode int mode);
         *
         * @NavigationMode
         * public abstract int getNavigationMode();
         * ```
         *
         * For a flag, set the flag attribute:
         * ```
         * @IntDef(
         * flag = true,
         * value = {NAVIGATION_MODE_STANDARD, NAVIGATION_MODE_LIST, NAVIGATION_MODE_TABS}
         * )
         * ```
         *
         * @see LongDef
         */

        """
            .trimIndent()
    checkFormatter(
        source,
        KDocFormattingOptions(maxLineWidth = 72),
        """
        /**
         * Denotes that the annotated element of integer type, represents a
         * logical type and that its value should be one of the explicitly named
         * constants. If the IntDef#flag() attribute is set to true, multiple
         * constants can be combined.
         *
         * Example:
         * ```
         * @Retention(SOURCE)
         * @IntDef({NAVIGATION_MODE_STANDARD, NAVIGATION_MODE_LIST, NAVIGATION_MODE_TABS})
         * public @interface NavigationMode {}
         * public static final int NAVIGATION_MODE_STANDARD = 0;
         * public static final int NAVIGATION_MODE_LIST = 1;
         * public static final int NAVIGATION_MODE_TABS = 2;
         * ...
         * public abstract void setNavigationMode(@NavigationMode int mode);
         *
         * @NavigationMode
         * public abstract int getNavigationMode();
         * ```
         *
         * For a flag, set the flag attribute:
         * ```
         * @IntDef(
         * flag = true,
         * value = {NAVIGATION_MODE_STANDARD, NAVIGATION_MODE_LIST, NAVIGATION_MODE_TABS}
         * )
         * ```
         *
         * @see LongDef
         */
        """
            .trimIndent(),
        indent = "",
    )
  }

  @Test
  fun testConvertMarkup2() {
    // Bug where the markup conversion around <p></p> wasn't working correctly
    // From AOSP's
    // frameworks/support/bluetooth/bluetooth-core/src/main/java/androidx/bluetooth/core/BluetoothAdapter.kt
    val source =
        """
        /**
         * Fundamentally, this is your starting point for all
         * Bluetooth actions. * </p>
         * <p>This class is thread safe.</p>
         *
         * @hide
         */
        """
            .trimIndent()
    checkFormatter(
        source,
        KDocFormattingOptions(maxLineWidth = 72),
        """
        /**
         * Fundamentally, this is your starting point for all Bluetooth actions.
         *
         * This class is thread safe.
         *
         * @hide
         */
        """
            .trimIndent(),
        indent = "",
    )
  }

  /**
   * Test utility method: from a source kdoc, derive an "equivalent" kdoc (same punctuation,
   * whitespace, capitalization and length of words) with words from Lorem Ipsum. Useful to create
   * test cases for the formatter without checking in original comments.
   */
  private fun loremize(s: String): String {
    val lorem =
        "Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt " +
            "ut labore et dolore magna aliqua. Ut enim ad minim veniam, quis nostrud exercitation ullamco " +
            "laboris nisi ut aliquip ex ea commodo consequat. Duis aute irure dolor in reprehenderit in " +
            "voluptate velit esse cillum dolore eu fugiat nulla pariatur. Excepteur sint occaecat cupidatat " +
            "non proident, sunt in culpa qui officia deserunt mollit anim id est laborum"
    val loremWords = lorem.filter { it.isLetter() || it == ' ' }.lowercase().split(" ")
    var next = 0

    fun adjustCapitalization(word: String, original: String): String {
      return if (original[0].isUpperCase()) {
        if (original.all { it.isUpperCase() }) {
          word.uppercase()
        } else {
          word.replaceFirstChar { it.uppercase() }
        }
      } else {
        word
      }
    }

    fun nextLorem(word: String): String {
      val length = word.length
      val start = next
      while (next < loremWords.size) {
        val nextLorem = loremWords[next]
        if (nextLorem.length == length) {
          return adjustCapitalization(nextLorem, word)
        }
        next++
      }
      next = 0
      while (next < start) {
        val nextLorem = loremWords[next]
        if (nextLorem.length == length) {
          return adjustCapitalization(nextLorem, word)
        }
        next++
      }
      if (length == 1) {
        return ('a' + (start % 26)).toString()
      }
      // No match for this word
      return word
    }

    val sb = StringBuilder()
    var i = 0
    while (i < s.length) {
      val c = s[i]
      if (c.isLetter()) {
        var end = i + 1
        while (end < s.length && s[end].isLetter()) {
          end++
        }
        val word = s.substring(i, end)
        if ((i > 0 && s[i - 1] == '@') || word == "http" || word == "https" || word == "com") {
          // Don't translate URL prefix/suffixes and doc tags
          sb.append(word)
        } else {
          sb.append(nextLorem(word))
        }
        i = end
      } else {
        sb.append(c)
        i++
      }
    }
    return sb.toString()
  }

  @Test
  fun testBlankLineBeforeBlockQuote() {
    // From GH#561: blank line before blockquote should be preserved
    checkFormatter(
        """
        /**
         * Text before blockquote:
         *
         * > This is a blockquote.
         */
        """
            .trimIndent(),
        KDocFormattingOptions(72),
        """
        /**
         * Text before blockquote:
         *
         * > This is a blockquote.
         */
        """
            .trimIndent(),
    )
  }

  @Test
  fun testBlankLineBetweenLists() {
    // From GH#561: blank line between two lists should be preserved
    checkFormatter(
        """
        /**
         * - list item 1
         * - list item 2
         *
         * - second list item A
         * - second list item B
         */
        """
            .trimIndent(),
        KDocFormattingOptions(72),
        """
        /**
         * - list item 1
         * - list item 2
         *
         * - second list item A
         * - second list item B
         */
        """
            .trimIndent(),
    )
  }

  @Test
  fun testBlankLinesInMarkdownElements() {
    // Full example from GH#561
    checkFormatter(
        """
        /**
         * Test paragraph
         *
         * Test paragraph 2
         *
         * - list item 1
         * - list item 2
         *
         * - second list item A
         * - second list item B
         *
         * ```json
         * {"code": "block"}
         * ```
         *
         * > test block quote
         */
        """
            .trimIndent(),
        KDocFormattingOptions(72),
        """
        /**
         * Test paragraph
         *
         * Test paragraph 2
         *
         * - list item 1
         * - list item 2
         *
         * - second list item A
         * - second list item B
         *
         * ```json
         * {"code": "block"}
         * ```
         *
         * > test block quote
         */
        """
            .trimIndent(),
    )
  }

  @Test
  fun testFencedCodeBlockInListItem() {
    checkFormatter(
        """
        /**
         * - Item with code:
         *   ```
         *   val x = 1
         *   ```
         * - Next item
         */
        """
            .trimIndent(),
        KDocFormattingOptions(72),
        """
        /**
         * - Item with code:
         *   ```
         *   val x = 1
         *   ```
         * - Next item
         */
        """
            .trimIndent(),
    )
  }

  @Test
  fun testFencedCodeBlockWithContinuationInListItem() {
    checkFormatter(
        """
        /**
         * - Item with code:
         *   ```
         *   val x = 1
         *   ```
         *
         *   More text after the code block.
         * - Next item
         */
        """
            .trimIndent(),
        KDocFormattingOptions(72),
        """
        /**
         * - Item with code:
         *   ```
         *   val x = 1
         *   ```
         *
         *   More text after the code block.
         * - Next item
         */
        """
            .trimIndent(),
    )
  }

  @Test
  fun testMultipleFencedCodeBlocksInListItem() {
    checkFormatter(
        """
        /**
         * - First do this:
         *   ```
         *   val x = 1
         *   ```
         *
         *   Then do this:
         *   ```
         *   val y = 2
         *   ```
         * - Next item
         */
        """
            .trimIndent(),
        KDocFormattingOptions(72),
        """
        /**
         * - First do this:
         *   ```
         *   val x = 1
         *   ```
         *
         *   Then do this:
         *   ```
         *   val y = 2
         *   ```
         * - Next item
         */
        """
            .trimIndent(),
    )
  }

  @Test
  fun testFencedCodeBlockAtEndOfListItem() {
    checkFormatter(
        """
        /**
         * - Item with code at end:
         *   ```
         *   val x = 1
         *   ```
         */
        """
            .trimIndent(),
        KDocFormattingOptions(72),
        """
        /**
         * - Item with code at end:
         *   ```
         *   val x = 1
         *   ```
         */
        """
            .trimIndent(),
    )
  }

  @Test
  fun testFencedCodeBlockInNumberedListItem() {
    checkFormatter(
        """
        /**
         * 1. First step:
         *    ```kotlin
         *    val config = Config()
         *    ```
         * 2. Second step
         */
        """
            .trimIndent(),
        KDocFormattingOptions(72),
        """
        /**
         * 1. First step:
         *    ```kotlin
         *    val config = Config()
         *    ```
         * 2. Second step
         */
        """
            .trimIndent(),
    )
  }

  // --------------------------------------------------------------------
  // A few failing test cases here for corner cases that aren't handled
  // right yet.
  // --------------------------------------------------------------------

  @Test
  fun testNestedWithinQuoted() {
    val source =
        """
        /**
         * Lists within a block quote:
         * > Here's my quoted text.
         * > 1. First item
         * > 2. Second item
         * > 3. Third item
         */
        """
            .trimIndent()
    checkFormatter(
        source,
        KDocFormattingOptions(40),
        """
        /**
         * Lists within a block quote:
         * > Here's my quoted text.
         * > 1. First item
         * > 2. Second item
         * > 3. Third item
         */
        """
            .trimIndent(),
    )

    checkFormatter(
        """
        /**
         * Here's some text.
         * > Here's some more text that
         * > is indented. More text.
         * > > And here's some even
         * > > more indented text
         * > Back to the top level
         */
        """
            .trimIndent(),
        KDocFormattingOptions(maxLineWidth = 100, maxCommentWidth = 60),
        """
        /**
         * Here's some text.
         * > Here's some more text that is indented. More text.
         * > > And here's some even more indented text
         * > Back to the top level
         */
        """
            .trimIndent(),
    )
  }
}
