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
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ModifierSorterTest {
  @Test
  fun `sort modifiers and move annotations before them`() {
    val code =
        """
        final @Magic public class Foo

        data operator infix inline @Magic(1) suspend override internal class Foo

        fun interface Foo

        final @get:Rule @field:[Inject Named("WEB_VIEW")] private val property = 1

        inline fun consume(noinline @Magic block: () -> Unit) {}
        """
            .trimIndent()

    val expected =
        """
        @Magic public final class Foo

        @Magic(1) internal override suspend inline infix operator data class Foo

        fun interface Foo

        @get:Rule
        @field:[Inject Named("WEB_VIEW")]
        private final val property = 1

        inline fun consume(noinline @Magic block: () -> Unit) {}
        """
            .trimIndent()
            .plus("\n")

    assertEquals(expected, Formatter.format(code))
  }

  @Test
  fun `comments stay attached while modifiers are sorted`() {
    val code =
        """
        override /* override explanation */ public fun one() {}

        override
        // public explanation
        public fun two() {}

        override // override explanation
        public fun three() {}

        override public
        /* declaration explanation */ fun four() {}
        """
            .trimIndent()

    val expected =
        """
        public override /* override explanation */ fun one() {}

        // public explanation
        public override fun two() {}

        public override // override explanation
        fun three() {}

        public override /* declaration explanation */ fun four() {}
        """
            .trimIndent()
            .plus("\n")

    assertEquals(expected, Formatter.format(code))
  }

  @Test
  fun `sort every modifier convention group`() {
    val code =
        """
        actual public typealias ActualName = String
        sealed internal class SealedClass
        const private val constant = 1
        external public fun externalFunction()
        suspend override protected fun overriddenFunction()
        lateinit internal var property: String
        tailrec private fun recursiveFunction() {}
        inner protected class InnerClass
        enum public class EnumClass
        class Container { companion private object }
        value public class ValueClass(val value: Int)
        operator inline fun plus(other: ValueClass) = this
        infix inline fun combine(other: ValueClass) = this
        data internal class DataClass(val value: Int)
        """
            .trimIndent()

    val expected =
        """
        public actual typealias ActualName = String

        internal sealed class SealedClass

        private const val constant = 1

        public external fun externalFunction()

        protected override suspend fun overriddenFunction()

        internal lateinit var property: String

        private tailrec fun recursiveFunction() {}

        protected inner class InnerClass

        public enum class EnumClass

        class Container {
          private companion object
        }

        public value class ValueClass(val value: Int)

        inline operator fun plus(other: ValueClass) = this

        inline infix fun combine(other: ValueClass) = this

        internal data class DataClass(val value: Int)
        """
            .trimIndent()
            .plus("\n")

    assertEquals(expected, Formatter.format(code))
  }

  @Test
  fun `modifier sorting is a whole-file cleanup for every style`() {
    val code =
        """
        override public fun outsideSelection() {}
        fun insideSelection() { println( 1 ) }
        """
            .trimIndent()
    val expected =
        """
        public override fun outsideSelection() {}

        fun insideSelection() {
          println(1)
        }
        """
            .trimIndent()
            .plus("\n")
    val selectedSecondLine = TreeRangeSet.create<Int>().apply { add(Range.closedOpen(1, 2)) }

    for (options in listOf(Formatter.META_FORMAT, Formatter.GOOGLE_FORMAT)) {
      assertEquals(expected, Formatter.format(options, code, lineRanges = selectedSecondLine))
    }

    assertEquals(
        expected.replace("  println", "    println"),
        Formatter.format(
            Formatter.KOTLINLANG_FORMAT,
            code,
            lineRanges = selectedSecondLine,
        ),
    )
  }

  @Test
  fun `modifier sorting preserves line separators and context receivers`() {
    val code = "context(Something)\r\noverride public fun f() {}\r\n"
    val expected = "context(Something)\r\npublic override fun f() {}\r\n"

    assertEquals(expected, Formatter.format(code))
  }
}
