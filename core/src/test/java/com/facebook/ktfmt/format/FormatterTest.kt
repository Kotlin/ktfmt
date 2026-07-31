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

import com.facebook.ktfmt.format.Formatter.META_FORMAT
import com.facebook.ktfmt.testutil.assertFormatted
import com.facebook.ktfmt.testutil.assertThatFormatting
import com.facebook.ktfmt.testutil.defaultTestFormattingOptions
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

@Suppress("FunctionNaming")
class FormatterTest {

  @Test
  fun `support script (kts) files`() = assertFormatted(
      """
      |package foo
      |
      |import java.io.File
      |
      |val one: String
      |
      |val two: String
      |
      |fun f() {
      |  println("asd")
      |}
      |
      |println("Called with args:")
      |
      |args.forEach { println(File + "-") }
      |"""
          .trimMargin(),
  )

  @Test
  fun `support script (kts) files with a shebang`() = assertFormatted(
      """
      |#!/usr/bin/env kscript
      |package foo
      |
      |println("Called")
      |"""
          .trimMargin(),
  )

  @Test
  fun `call chains`() = assertFormatted(
      """
      |////////////////////////////////////////////////////////
      |fun f() {
      |  // Static method calls are attached to the class name.
      |  ImmutableList.newBuilder()
      |      .add(1)
      |      .add(1)
      |      .add(1)
      |      .add(1)
      |      .add(1)
      |      .add(1)
      |      .add(1)
      |      .add(1)
      |      .add(1)
      |      .add(1)
      |      .build()
      |
      |  // Multiple call expressions --> each on its own line.
      |  ImmutableList()
      |      .newBuilder()
      |      .add(1)
      |      .add(1)
      |      .add(1)
      |      .add(1)
      |      .add(1)
      |      .add(1)
      |      .add(1)
      |      .add(1)
      |      .add(1)
      |      .add(1)
      |      .build()
      |}
      |"""
          .trimMargin(),
      deduceMaxWidth = true,
  )

  @Test
  fun `line breaks in function arguments`() = assertFormatted(
      """
      |//////////////////////////////////////////////////
      |fun f() {
      |  computeBreaks(
      |      javaOutput.commentsHelper,
      |      maxWidth,
      |      Doc.State(+0, 0))
      |  computeBreaks(
      |      output.commentsHelper, maxWidth, State(0))
      |  doc.computeBreaks(
      |      javaOutput.commentsHelper,
      |      maxWidth,
      |      Doc.State(+0, 0))
      |  doc.computeBreaks(
      |      output.commentsHelper, maxWidth, State(0))
      |}
      |"""
          .trimMargin(),
      deduceMaxWidth = true,
  )

  @Test
  fun `parameters and return type in function definitions`() = assertFormatted(
      """
      |////////////////////////////////////////
      |fun format(
      |    code: String,
      |    maxWidth: Int =
      |        DEFAULT_MAX_WIDTH_VERY_LONG
      |): String {
      |  val a = 0
      |}
      |
      |fun print(
      |    code: String,
      |    maxWidth: Int =
      |        DEFAULT_MAX_WIDTH_VERY_LONG
      |) {
      |  val a = 0
      |}
      |"""
          .trimMargin(),
      deduceMaxWidth = true,
  )

  @Test
  fun `kitchen sink of tests`() {
    // Don't add more tests here
    val code =
        """
        |fun
        |f  (
        |a : Int
        | , b: Double , c:String) {           var result = 0
        | val aVeryLongLongLongLongLongLongLongLongLongLongLongLongLongLongLongLongLongLongLongLongLongLongVar = 43
        |          foo.bar.zed.accept(
        |
        |          )
        |
        |          foo(
        |
        |          )
        |
        |          foo.bar.zed.accept(
        |                DoSomething.bar()
        |          )
        |
        |          bar(
        |          ImmutableList.newBuilder().add(1).add(1).add(1).add(1).add(1).add(1).add(1).add(1).add(1).add(1).build())
        |
        |
        |          ImmutableList.newBuilder().add(1).add(1).add(1).add(1).add(1).add(1).add(1).add(1).add(1).add(1).build()
        |     }
        |"""
            .trimMargin()

    val expected =
        """
        |fun f(a: Int, b: Double, c: String) {
        |  var result = 0
        |  val aVeryLongLongLongLongLongLongLongLongLongLongLongLongLongLongLongLongLongLongLongLongLongLongVar =
        |      43
        |  foo.bar.zed.accept()
        |
        |  foo()
        |
        |  foo.bar.zed.accept(DoSomething.bar())
        |
        |  bar(
        |      ImmutableList.newBuilder()
        |          .add(1)
        |          .add(1)
        |          .add(1)
        |          .add(1)
        |          .add(1)
        |          .add(1)
        |          .add(1)
        |          .add(1)
        |          .add(1)
        |          .add(1)
        |          .build())
        |
        |  ImmutableList.newBuilder()
        |      .add(1)
        |      .add(1)
        |      .add(1)
        |      .add(1)
        |      .add(1)
        |      .add(1)
        |      .add(1)
        |      .add(1)
        |      .add(1)
        |      .add(1)
        |      .build()
        |}
        |"""
            .trimMargin()

    assertThatFormatting(code).isEqualTo(expected)
    // Don't add more tests here
  }

  @Test
  fun `spacing around variable declarations`() = assertFormatted(
      """
      |fun f() {
      |  var x: Int = 4
      |  val y = 0
      |}
      |"""
          .trimMargin(),
  )

  @Test fun `class without a body nor properties`() = assertFormatted("class Foo\n")

  @Test fun `interface without a body nor properties`() = assertFormatted("interface Foo\n")

  @Test fun `preserve empty primary constructor`() = assertFormatted("class Foo()\n")

  @Test
  fun `simple fun interface`() = assertFormatted(
      """
      |fun interface MyRunnable {
      |  fun runIt()
      |}
      |"""
          .trimMargin(),
  )

  @Test
  fun `handle complex fun interface without body`() =
      assertFormatted("public fun interface Function<T : List<*>> : (Int, T?) -> T?\n")

  @Test
  fun `class without a body, with explicit ctor params`() =
      assertFormatted("class Foo(a: Int, var b: Double, val c: String)\n")

  @Test
  fun `class with a body and explicit ctor params`() = assertFormatted(
      """
      |class Foo(a: Int, var b: Double, val c: String) {
      |  val x = 2
      |
      |  fun method() {}
      |
      |  class Bar
      |}
      |"""
          .trimMargin(),
  )

  @Test
  fun `properties and fields with modifiers`() = assertFormatted(
      """
      |class Foo(public val p1: Int, private val p2: Int, open val p3: Int, final val p4: Int) {
      |  private var f1 = 0
      |
      |  public var f2 = 0
      |
      |  open var f3 = 0
      |
      |  final var f4 = 0
      |}
      |"""
          .trimMargin(),
  )

  @Test
  fun `properties with multiple modifiers`() = assertFormatted(
      """
      |class Foo(public open inner val p1: Int) {
      |  public open inner var f2 = 0
      |}
      |"""
          .trimMargin(),
  )

  @Test
  fun `spaces around binary operations`() = assertFormatted(
      """
      |fun foo() {
      |  a = 5
      |  x + 1
      |}
      |"""
          .trimMargin(),
  )

  @Test
  fun `breaking long binary operations`() = assertFormatted(
      """
      |////////////////////
      |fun foo() {
      |  val finalWidth =
      |      value1 +
      |          value2 +
      |          (value3 +
      |              value4 +
      |              value5) +
      |          foo(v) +
      |          (1 + 2) +
      |          function(
      |              value7,
      |              value8) +
      |          value9
      |}
      |"""
          .trimMargin(),
      deduceMaxWidth = true,
  )

  @Test
  fun `prioritize according to associativity`() = assertFormatted(
      """
      |//////////////////////////////////////
      |fun foo() {
      |  return expression1 != expression2 ||
      |      expression2 != expression1
      |}
      |"""
          .trimMargin(),
      deduceMaxWidth = true,
  )

  @Test
  fun `once a binary expression is broken, split on every line`() = assertFormatted(
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
          .trimMargin(),
      deduceMaxWidth = true,
  )

  @Test
  fun `long binary expressions with ranges in the middle`() = assertFormatted(
      """
      |//////////////////////////////////////
      |fun foo() {
      |  val sentence =
      |      "The" +
      |          "quick" +
      |          ("brown".."fox") +
      |          ("brown"..<"fox") +
      |          "jumps" +
      |          "over" +
      |          "the".."lazy" + "dog"
      |}
      |"""
          .trimMargin(),
      deduceMaxWidth = true,
  )

  @Test
  fun `line comment before binary operator keeps operator with operand`() = assertFormatted(
      """
      |//////////////////////////////////////////////////
      |fun foo() {
      |  val result =
      |      firstCond &&
      |          secondCond
      |          // comment about thirdCond.
      |          && thirdCond
      |}
      |"""
          .trimMargin(),
      deduceMaxWidth = true,
  )

  @Test
  fun `line comment before operator in multi-operand chain`() = assertFormatted(
      """
      |//////////////////////////////////////////////////
      |fun foo() {
      |  val result =
      |      firstCond &&
      |          secondCond
      |          // comment about thirdCond.
      |          && thirdCond &&
      |          fourthCond
      |}
      |"""
          .trimMargin(),
      deduceMaxWidth = true,
  )

  @Test
  fun `block comment before binary operator keeps operator with operand`() = assertFormatted(
      """
      |//////////////////////////////////////////////////
      |fun foo() {
      |  val result =
      |      firstCond &&
      |          secondCond
      |          /* comment. */
      |          && thirdCond
      |}
      |"""
          .trimMargin(),
      deduceMaxWidth = true,
  )

  @Test
  fun `line comment before or operator keeps operator with operand`() = assertFormatted(
      """
      |//////////////////////////////////////////////////
      |fun foo() {
      |  val result =
      |      firstCond ||
      |          secondCond
      |          // comment about thirdCond.
      |          || thirdCond
      |}
      |"""
          .trimMargin(),
      deduceMaxWidth = true,
  )

  @Test
  fun `inline block comment before binary operator does not break chain`() = assertFormatted(
      """
      |//////////////////////////////////////////////////////
      |fun foo() {
      |  val result =
      |      firstCondition ||
      |          secondCondition /*tag*/ ||
      |          thirdCondition
      |}
      |"""
          .trimMargin(),
      deduceMaxWidth = true,
  )

  @Test
  fun `assignment expressions with scoping functions are block-like`() = assertFormatted(
      """
      |///////////////////////////
      |fun f() {
      |  name.sub = scope { x ->
      |    //
      |  }
      |  name.sub += scope { x ->
      |    //
      |  }
      |  name.sub -= scope { x ->
      |    //
      |  }
      |  name.sub *= scope { x ->
      |    //
      |  }
      |  name.sub /= scope { x ->
      |    //
      |  }
      |  name.sub %= scope { x ->
      |    //
      |  }
      |}
      |
      |fun h() {
      |  long.name.sub =
      |      scope { x ->
      |        //
      |      }
      |  long.name.sub +=
      |      scope { x ->
      |        //
      |      }
      |  long.name.sub -=
      |      scope { x ->
      |        //
      |      }
      |  long.name.sub *=
      |      scope { x ->
      |        //
      |      }
      |  long.name.sub /=
      |      scope { x ->
      |        //
      |      }
      |  long.name.sub %=
      |      scope { x ->
      |        //
      |      }
      |}
      |"""
          .trimMargin(),
      deduceMaxWidth = true,
  )

  @Test
  fun `don't keep adding newlines between these two comments when they're at end of file`() {
    assertFormatted(
        """
        |package foo
        |// a
        |
        |/* Another comment */
        |"""
            .trimMargin(),
    )

    assertFormatted(
        """
        |// Comment as first element
        |package foo
        |// a
        |
        |/* Another comment */
        |"""
            .trimMargin(),
    )

    assertFormatted(
        """
        |// Comment as first element then blank line
        |
        |package foo
        |// a
        |
        |/* Another comment */
        |"""
            .trimMargin(),
    )

    assertFormatted(
        """
        |// Comment as first element
        |package foo
        |// Adjacent line comments
        |// Don't separate
        |"""
            .trimMargin(),
    )
  }

  @Test
  fun `properties with line comment above initializer`() = assertFormatted(
      """
      |class Foo {
      |  var x: Int =
      |      // Comment
      |      0
      |
      |  var y: Int =
      |      // Comment
      |      scope {
      |        0 //
      |      }
      |
      |  var z: Int =
      |      // Comment
      |      if (cond) {
      |        0
      |      } else {
      |        1
      |      }
      |}
      |"""
          .trimMargin(),
  )

  @Test
  fun `properties with line comment above delegate`() = assertFormatted(
      """
      |class Foo {
      |  var x: Int by
      |      // Comment
      |      0
      |
      |  var y: Int by
      |      // Comment
      |      scope {
      |        0 //
      |      }
      |
      |  var z: Int by
      |      // Comment
      |      if (cond) {
      |        0
      |      } else {
      |        1
      |      }
      |}
      |"""
          .trimMargin(),
  )

  @Test
  fun `properties with accessors`() = assertFormatted(
      """
      |class Foo {
      |  var x: Int
      |    get() = field
      |
      |  var y: Boolean
      |    get() = x.equals(123)
      |    set(value) {
      |      field = value
      |    }
      |
      |  var z: Boolean
      |    get() {
      |      x.equals(123)
      |    }
      |
      |  var zz = false
      |    private set
      |}
      |"""
          .trimMargin(),
  )

  @Test
  fun `properties with accessors and semicolons on same line`() {
    val code =
        """
        |class Foo {
        |  var x = false; private set
        |
        |  internal val a by lazy { 5 }; internal get
        |
        |  var foo: Int; get() = 6; set(x) {};
        |}
        |"""
            .trimMargin()

    val expected =
        """
        |class Foo {
        |  var x = false
        |    private set
        |
        |  internal val a by lazy { 5 }
        |    internal get
        |
        |  var foo: Int
        |    get() = 6
        |    set(x) {}
        |}
        |"""
            .trimMargin()

    assertThatFormatting(code).isEqualTo(expected)
  }

  @Test
  fun `a property with a too long name being broken on multiple lines`() = assertFormatted(
      """
      |////////////////////
      |class Foo {
      |  val thisIsALongName:
      |      String =
      |      "Hello there this is long"
      |    get() = field
      |}
      |"""
          .trimMargin(),
      deduceMaxWidth = true,
  )

  @Test
  fun `multi-character unary and binary operators such as ==`() = assertFormatted(
      """
      |fun f() {
      |  3 == 4
      |  true && false
      |  a++
      |  a === b
      |}
      |"""
          .trimMargin(),
  )

  @Test
  fun `package names stay in one line`() {
    val code =
        """
        | package  com  .example. subexample
        |
        |fun f() = 1
        |"""
            .trimMargin()
    val expected =
        """
        |package com.example.subexample
        |
        |fun f() = 1
        |"""
            .trimMargin()

    assertThatFormatting(code).isEqualTo(expected)
  }

  @Test
  fun `handle package name and imports with escapes and spaces`() = assertFormatted(
      """
      |package com.`fun times`.`with package names`
      |
      |import `nothing stops`.`us`.`from doing this`
      |
      |fun f() = `from doing this`()
      |"""
          .trimMargin(),
  )

  @Test
  fun `safe dot operator expression`() = assertFormatted(
      """
      |fun f() {
      |  node?.name
      |}
      |"""
          .trimMargin(),
  )

  @Test
  fun `safe dot operator expression with normal`() = assertFormatted(
      """
      |fun f() {
      |  node?.name.hello
      |}
      |"""
          .trimMargin(),
  )

  @Test
  fun `safe dot operator expression chain in expression function`() = assertFormatted(
      """
      |//////////////////////////////////////////////////
      |fun f(number: Int) =
      |    Something.doStuff(number)?.size
      |"""
          .trimMargin(),
      deduceMaxWidth = true,
  )

  @Test
  fun `avoid breaking suspected package names`() = assertFormatted(
      """
      |///////////////////////
      |fun f() {
      |  com.facebook.Foo
      |      .format()
      |  org.facebook.Foo
      |      .format()
      |  java.lang.stuff.Foo
      |      .format()
      |  javax.lang.stuff.Foo
      |      .format()
      |  kotlin.lang.Foo
      |      .format()
      |  foo.facebook.Foo
      |      .format()
      |}
      |"""
          .trimMargin(),
      deduceMaxWidth = true,
  )

  @Test
  fun `an assortment of tests for emitQualifiedExpression`() = assertFormatted(
      """
      |/////////////////////////////////////
      |fun f() {
      |  // Regression test:
      |  // https://github.com/facebook/ktfmt/issues/56
      |  kjsdfglkjdfgkjdfkgjhkerjghkdfj
      |      ?.methodName1()
      |
      |  // a series of field accesses
      |  // followed by a single call
      |  // expression is kept together.
      |  abcdefghijkl.abcdefghijkl
      |      ?.methodName2()
      |
      |  // Similar to above.
      |  abcdefghijkl.abcdefghijkl
      |      ?.methodName3
      |      ?.abcdefghijkl()
      |
      |  // Multiple call expressions cause
      |  // each part of the expression
      |  // to be placed on its own line.
      |  abcdefghijkl
      |      ?.abcdefghijkl
      |      ?.methodName4()
      |      ?.abcdefghijkl()
      |
      |  // Don't break first call
      |  // expression if it fits.
      |  foIt(something.something.happens())
      |      .thenReturn(result)
      |
      |  // Break after `longerThanFour(`
      |  // because it's longer than 4 chars
      |  longerThanFour(
      |          something.something
      |              .happens())
      |      .thenReturn(result)
      |
      |  // Similarly to above, when part of
      |  // qualified expression.
      |  foo.longerThanFour(
      |          something.something
      |              .happens())
      |      .thenReturn(result)
      |
      |  // Keep 'super' attached to the
      |  // method name
      |  super.abcdefghijkl
      |      .methodName4()
      |      .abcdefghijkl()
      |}
      |"""
          .trimMargin(),
      deduceMaxWidth = true,
  )

  @Test
  fun `an assortment of tests for emitQualifiedExpression with lambdas`() = assertFormatted(
      """
      |//////////////////////////////////////////////////////////////////////////////
      |fun f() {
      |  val items =
      |      items.toMutableList.apply {
      |        //
      |        foo
      |      }
      |
      |  val items =
      |      items.toMutableList().apply {
      |        //
      |        foo
      |      }
      |
      |  // All dereferences are on one line (because they fit), even though
      |  // the apply() at the end requires a line break.
      |  val items =
      |      items.toMutableList.sdfkjsdf.sdfjksdflk.sdlfkjsldfj.apply {
      |        //
      |        foo
      |      }
      |
      |  // All method calls are on one line (because they fit), even though
      |  // the apply() at the end requires a line break.
      |  val items =
      |      items.toMutableList().sdfkjsdf().sdfjksdflk().sdlfkjsldfj().apply {
      |        //
      |        foo
      |      }
      |
      |  // All method calls with lambdas could fit, but we avoid a block like syntax
      |  // and break to one call per line
      |  val items =
      |      items
      |          .map { it + 1 }
      |          .filter { it > 0 }
      |          .apply {
      |            //
      |            foo
      |          }
      |
      |  // the lambda is indented properly with the break before it
      |  val items =
      |      items.fieldName.sdfkjsdf.sdfjksdflk.sdlfkjsldfj.sdfjksdflk.sdlfkjsldfj
      |          .sdlfkjsldfj
      |          .apply {
      |            //
      |            foo
      |          }
      |  items.fieldName.sdfkjsdf.sdfjksdflk.sdlfkjsldfj.sdfjksdflk.sdlfkjsldfj
      |      .apply {
      |        //
      |        foo
      |      }
      |
      |  // When there are multiple method calls, and they don't fit on one
      |  // line, put each on a new line.
      |  val items =
      |      items
      |          .toMutableList()
      |          .sdfkjsdf()
      |          .sdfjksdflk()
      |          .sdlfkjsldfj()
      |          .sdfjksdflk()
      |          .sdlfkjsldfj()
      |          .apply {
      |            //
      |            foo
      |          }
      |}
      |"""
          .trimMargin(),
      deduceMaxWidth = true,
  )

  @Test
  fun `when two lambdas are in a chain, avoid block syntax`() = assertFormatted(
      """
      |class Foo : Bar() {
      |  fun doIt() {
      |    fruit.onlyBananas().forEach { banana ->
      |      val seeds = banana.find { it.type == SEED }
      |      println(seeds)
      |    }
      |
      |    fruit
      |        .filter { isBanana(it, Bananas.types) }
      |        .forEach { banana ->
      |          val seeds = banana.find { it.type == SEED }
      |          println(seeds)
      |        }
      |  }
      |}
      |"""
          .trimMargin(),
  )

  @Test
  fun `don't one-line lambdas following argument breaks`() = assertFormatted(
      """
      |///////////////////////////////////////////////////////////////////////////////
      |class Foo : Bar() {
      |  fun doIt() {
      |    // don't break in lambda, no argument breaks found
      |    fruit.forEach { eat(it) }
      |
      |    // break in the lambda, without comma
      |    fruit.forEach(
      |        someVeryLongParameterNameThatWillCauseABreak,
      |        evenWithoutATrailingCommaOnTheParameterListSoLetsSeeIt) {
      |          eat(it)
      |        }
      |
      |    // break in the lambda, with comma
      |    fruit.forEach(
      |        fromTheVine = true,
      |    ) {
      |      eat(it)
      |    }
      |
      |    // don't break in the inner lambda, as nesting doesn't respect outer levels
      |    fruit.forEach(
      |        fromTheVine = true,
      |    ) {
      |      fruit.forEach { eat(it) }
      |    }
      |
      |    // don't break in the lambda, as breaks don't propagate
      |    fruit
      |        .onlyBananas(
      |            fromTheVine = true,
      |        )
      |        .forEach { eat(it) }
      |
      |    // don't break in the inner lambda, as breaks don't propagate to parameters
      |    fruit.onlyBananas(
      |        fromTheVine = true,
      |        processThem = { eat(it) },
      |    ) {
      |      eat(it)
      |    }
      |
      |    // don't break in the inner lambda, as breaks don't propagate to the body
      |    fruit.onlyBananas(
      |        fromTheVine = true,
      |    ) {
      |      val anon = { eat(it) }
      |    }
      |  }
      |}
      |"""
          .trimMargin(),
      deduceMaxWidth = true,
  )

  @Test
  fun `indent parameters after a break when there's a lambda afterwards`() = assertFormatted(
      """
      |///////////////////////////
      |class C {
      |  fun method() {
      |    Foo.FooBar(
      |            param1, param2)
      |        .apply {
      |          //
      |          foo
      |        }
      |  }
      |}
      |"""
          .trimMargin(),
      deduceMaxWidth = true,
  )

  @Test
  fun `no forward propagation of breaks in call expressions (at trailing lambda)`() = assertFormatted(
      """
      |//////////////////////////
      |fun test() {
      |  foo_bar_baz__zip<A>(b) {
      |    c
      |  }
      |  foo.bar(baz).zip<A>(b) {
      |    c
      |  }
      |}
      |"""
          .trimMargin(),
      deduceMaxWidth = true,
  )

  @Test
  fun `forward propagation of breaks in call expressions (at value args)`() = assertFormatted(
      """
      |//////////////////////
      |fun test() {
      |  foo_bar_baz__zip<A>(
      |      b) {
      |        c
      |      }
      |}
      |
      |fun test() {
      |  foo.bar(baz).zip<A>(
      |      b) {
      |        c
      |      }
      |}
      |"""
          .trimMargin(),
      deduceMaxWidth = true,
  )

  @Test
  fun `forward propagation of breaks in call expressions (at type args)`() = assertFormatted(
      """
      |///////////////////
      |fun test() {
      |  foo_bar_baz__zip<
      |      A>(
      |      b) {
      |        c
      |      }
      |  foo.bar(baz).zip<
      |      A>(
      |      b) {
      |        c
      |      }
      |}
      |"""
          .trimMargin(),
      deduceMaxWidth = true,
  )

  @Test
  fun `expected indent in methods following single-line strings`() = assertFormatted(
      """
      |/////////////////////////
      |"Hello %s"
      |    .format(expression)
      |"""
          .trimMargin(),
      deduceMaxWidth = true,
  )

  @Test
  fun `forced break between multi-line strings and their selectors`() = assertFormatted(
      """
          |/////////////////////////
          |val STRING =
          |    $TQ
          |    |foo
          |    |$TQ
          |        .wouldFit()
          |
          |val STRING =
          |    $TQ
          |    |foo
          |    |//////////////////////////////////$TQ
          |        .wouldntFit()
          |
          |val STRING =
          |    $TQ
          |    |foo
          |    |$TQ
          |        .firstLink()
          |        .secondLink()
          |"""
          .trimMargin(),
      deduceMaxWidth = true,
  )

  @Test
  fun `import list`() {
    val code =
        """
        | import  com .example.common.reality. FooBar
        |  import  com .example.common.reality. FooBar2  as  foosBars
        |   import com .example.common.reality. *
        | import  foo.bar // Test
        |  import  abc.def /*
        |                  test */
        |
        |val x = FooBar.def { foosBars(bar) }
        |"""
            .trimMargin()
    val expected =
        """
        |import abc.def /*
        |               test */
        |import com.example.common.reality.*
        |import com.example.common.reality.FooBar
        |import com.example.common.reality.FooBar2 as foosBars
        |import foo.bar // Test
        |
        |val x = FooBar.def { foosBars(bar) }
        |"""
            .trimMargin()
    assertThatFormatting(code).isEqualTo(expected)
  }

  @Test
  fun `imports with trailing comments and expressions`() {
    val code =
        """
        |import com.example.zab // test
        |import com.example.foo ; val x = Sample(foo, zab)
        |"""
            .trimMargin()

    val expected =
        """
        |import com.example.foo
        |import com.example.zab // test
        |
        |val x = Sample(foo, zab)
        |"""
            .trimMargin()

    assertThatFormatting(code).isEqualTo(expected)
  }

  @Test
  fun `imports with trailing block comment and expression`() {
    val code =
        """
        |import com.example.zab /* heya */
        |import com.example.foo ; val x = Sample(foo, zab)
        |"""
            .trimMargin()

    val expected =
        """
        |import com.example.foo
        |import com.example.zab /* heya */
        |
        |val x = Sample(foo, zab)
        |"""
            .trimMargin()

    assertThatFormatting(code).isEqualTo(expected)
  }

  @Test
  fun `imports with trailing block comment and expression with false positive double slash`() {
    val code =
        """
        |import com.example.zab /* // */
        |import com.example.foo ; val x = Sample(foo, zab)
        |"""
            .trimMargin()

    val expected =
        """
        |import com.example.foo
        |import com.example.zab /* // */
        |
        |val x = Sample(foo, zab)
        |"""
            .trimMargin()

    assertThatFormatting(code).isEqualTo(expected)
  }

  @Test
  fun `backticks are ignored in import sort order`() = assertFormatted(
      """
      |import com.example.`if`
      |import com.example.we
      |import com.example.`when`
      |import com.example.wow
      |
      |val x = `if` { we.`when`(wow) }
      |"""
          .trimMargin(),
  )

  @Test
  fun `backticks are ignored in import sort order ('as' directory)`() = assertFormatted(
      """
      |import com.example.a as `if`
      |import com.example.a as we
      |import com.example.a as `when`
      |import com.example.a as wow
      |
      |val x = `if` { we.`when`(wow) }
      |"""
          .trimMargin(),
  )

  @Test
  fun `imports are deduplicated`() {
    val code =
        """
        |import com.example.b.*
        |import com.example.b
        |import com.example.a as `if`
        |import com.example.a as we
        |import com.example.a as `when`
        |import com.example.a as wow
        |import com.example.a as `when`
        |
        |val x = `if` { we.`when`(wow) } ?: b
        |"""
            .trimMargin()
    val expected =
        """
        |import com.example.a as `if`
        |import com.example.a as we
        |import com.example.a as `when`
        |import com.example.a as wow
        |import com.example.b
        |import com.example.b.*
        |
        |val x = `if` { we.`when`(wow) } ?: b
        |"""
            .trimMargin()
    assertThatFormatting(code).isEqualTo(expected)
  }

  @Test
  fun `unused imports are removed`() {
    val code =
        """
        |import com.unused.Sample
        |import com.used.FooBarBaz as Baz
        |import com.used.bar // test
        |import com.used.`class`
        |import com.used.a.*
        |import com.used.b as `if`
        |import com.used.b as we
        |import com.unused.a as `when`
        |import com.unused.a as wow
        |
        |fun test(input: we) {
        |  Baz(`class`)
        |  `if` { bar }
        |  val x = unused()
        |}
        |"""
            .trimMargin()
    val expected =
        """
        |import com.used.FooBarBaz as Baz
        |import com.used.a.*
        |import com.used.b as `if`
        |import com.used.b as we
        |import com.used.bar // test
        |import com.used.`class`
        |
        |fun test(input: we) {
        |  Baz(`class`)
        |  `if` { bar }
        |  val x = unused()
        |}
        |"""
            .trimMargin()
    assertThatFormatting(code).isEqualTo(expected)
  }

  @Test
  fun `unused import removal handles backtick-escaped full-path imports`() {
    val code =
        """
        |import `com.example.Foo.bar`
        |import `com.example.Foo.baz` as qux
        |import `com.example.unused.Quux`
        |
        |fun test() = bar + qux
        |"""
            .trimMargin()
    val expected =
        """
        |import `com.example.Foo.bar`
        |import `com.example.Foo.baz` as qux
        |
        |fun test() = bar + qux
        |"""
            .trimMargin()
    assertThatFormatting(code).isEqualTo(expected)
  }

  @Test
  fun `used imports from this package are removed`() {
    val code =
        """
        |package com.example
        |
        |import com.example.Sample
        |import com.example.Sample.CONSTANT
        |import com.example.a.foo
        |
        |fun test() {
        |  foo(CONSTANT, Sample())
        |}
        |"""
            .trimMargin()
    val expected =
        """
        |package com.example
        |
        |import com.example.Sample.CONSTANT
        |import com.example.a.foo
        |
        |fun test() {
        |  foo(CONSTANT, Sample())
        |}
        |"""
            .trimMargin()
    assertThatFormatting(code).isEqualTo(expected)
  }

  @Test
  fun `potentially unused imports from this package are kept if they are overloaded`() {
    val code =
        """
        |package com.example
        |
        |import com.example.a
        |import com.example.b
        |import com.example.c
        |import com.notexample.a
        |import com.notexample.b
        |import com.notexample.notC as c
        |
        |fun test() {
        |  a("hello")
        |  c("hello")
        |}
        |"""
            .trimMargin()
    val expected =
        """
        |package com.example
        |
        |import com.example.a
        |import com.example.c
        |import com.notexample.a
        |import com.notexample.notC as c
        |
        |fun test() {
        |  a("hello")
        |  c("hello")
        |}
        |"""
            .trimMargin()
    assertThatFormatting(code).isEqualTo(expected)
  }

  @Test
  fun `used imports from this package are kept if they are aliased`() {
    val code =
        """
        |package com.example
        |
        |import com.example.b as a
        |import com.example.c
        |
        |fun test() {
        |  a("hello")
        |}
        |"""
            .trimMargin()
    val expected =
        """
        |package com.example
        |
        |import com.example.b as a
        |
        |fun test() {
        |  a("hello")
        |}
        |"""
            .trimMargin()
    assertThatFormatting(code).isEqualTo(expected)
  }

  @Test
  fun `unused imports are computed using only the alias name if present`() {
    val code =
        """
        |package com.example
        |
        |import com.notexample.a as b
        |
        |fun test() {
        |  a("hello")
        |}
        |"""
            .trimMargin()
    val expected =
        """
        |package com.example
        |
        |fun test() {
        |  a("hello")
        |}
        |"""
            .trimMargin()
    assertThatFormatting(code).isEqualTo(expected)
  }

  @Test
  fun `keep import elements only mentioned in kdoc`() {
    val code =
        """
        |package com.example.kdoc
        |
        |import com.example.Bar
        |import com.example.Example
        |import com.example.Foo
        |import com.example.JavaDocLink
        |import com.example.Param
        |import com.example.R
        |import com.example.ReturnedValue
        |import com.example.Sample
        |import com.example.unused
        |import com.example.exception.AnException
        |import com.example.kdoc.Doc
        |
        |/**
        | * [Foo] is something only mentioned here, just like [R.layout.test] and [Doc].
        | *
        | * Old {@link JavaDocLink} that gets removed.
        | *
        | * @throws AnException
        | * @exception Sample.SampleException
        | * @param unused [Param]
        | * @property JavaDocLink [Param]
        | * @return [Unit] as [ReturnedValue]
        | * @sample Example
        | * @see Bar for more info
        | * @throws AnException
        | */
        |class Dummy
        |"""
            .trimMargin()
    val expected =
        """
        |package com.example.kdoc
        |
        |import com.example.Bar
        |import com.example.Example
        |import com.example.Foo
        |import com.example.Param
        |import com.example.R
        |import com.example.ReturnedValue
        |import com.example.Sample
        |import com.example.exception.AnException
        |
        |/**
        | * [Foo] is something only mentioned here, just like [R.layout.test] and [Doc].
        | *
        | * Old {@link JavaDocLink} that gets removed.
        | *
        | * @param unused [Param]
        | * @property JavaDocLink [Param]
        | * @return [Unit] as [ReturnedValue]
        | * @throws AnException
        | * @throws AnException
        | * @exception Sample.SampleException
        | * @sample Example
        | * @see Bar for more info
        | */
        |class Dummy
        |"""
            .trimMargin()
    assertThatFormatting(code).isEqualTo(expected)
  }

  @Test
  fun `keep import elements only mentioned in kdoc, single line`() {
    assertFormatted(
        """
        |import com.shopping.Bag
        |
        |/**
        | * Some summary.
        | *
        | * @param count you can fit this many in a [Bag]
        | */
        |fun fetchBananas(count: Int)
        |"""
            .trimMargin(),
    )
  }

  @Test
  fun `keep import elements only mentioned in kdoc, multiline`() {
    assertFormatted(
        """
        |import com.shopping.Bag
        |
        |/**
        | * Some summary.
        | *
        | * @param count this is how many of these wonderful fruit you can fit into the useful object that
        | *   you may refer to as a [Bag]
        | */
        |fun fetchBananas(count: Int)
        |"""
            .trimMargin(),
    )
  }

  @Test
  fun `keep component imports`() = assertFormatted(
      """
      |import com.example.component1
      |import com.example.component10
      |import com.example.component120
      |import com.example.component2
      |import com.example.component3
      |import com.example.component4
      |import com.example.component5
      |"""
          .trimMargin(),
  )

  @Test
  fun `keep operator imports`() = assertFormatted(
      """
      |import com.example.and
      |import com.example.compareTo
      |import com.example.contains
      |import com.example.dec
      |import com.example.div
      |import com.example.divAssign
      |import com.example.equals
      |import com.example.get
      |import com.example.getValue
      |import com.example.hasNext
      |import com.example.inc
      |import com.example.invoke
      |import com.example.iterator
      |import com.example.minus
      |import com.example.minusAssign
      |import com.example.mod
      |import com.example.modAssign
      |import com.example.next
      |import com.example.not
      |import com.example.or
      |import com.example.plus
      |import com.example.plusAssign
      |import com.example.provideDelegate
      |import com.example.rangeTo
      |import com.example.rem
      |import com.example.remAssign
      |import com.example.set
      |import com.example.setValue
      |import com.example.times
      |import com.example.timesAssign
      |import com.example.unaryMinus
      |import com.example.unaryPlus
      |import org.gradle.kotlin.dsl.assign
      |"""
          .trimMargin(),
  )

  @Test
  fun `keep unused imports when formatting options has feature turned off`() {
    val code =
        """
        |import com.unused.FooBarBaz as Baz
        |import com.unused.Sample
        |import com.unused.a as `when`
        |import com.unused.a as wow
        |import com.unused.a.*
        |import com.unused.b as `if`
        |import com.unused.b as we
        |import com.unused.bar // test
        |import com.unused.`class`
        |"""
            .trimMargin()

    assertThatFormatting(code)
        .withOptions(defaultTestFormattingOptions.copy(removeUnusedImports = false))
        .isEqualTo(code)
  }

  @Test
  fun `comments between imports are moved above import list`() {
    val code =
        """
        |package com.facebook.ktfmt
        |
        |/* leading comment */
        |import com.example.abc
        |/* internal comment 1 */
        |import com.example.bcd
        |// internal comment 2
        |import com.example.Sample
        |// trailing comment
        |
        |val x = Sample(abc, bcd)
        |"""
            .trimMargin()
    val expected =
        """
        |package com.facebook.ktfmt
        |
        |/* leading comment */
        |/* internal comment 1 */
        |// internal comment 2
        |import com.example.Sample
        |import com.example.abc
        |import com.example.bcd
        |
        |// trailing comment
        |
        |val x = Sample(abc, bcd)
        |"""
            .trimMargin()
    assertThatFormatting(code).isEqualTo(expected)
  }

  @Test
  fun `no redundant newlines when there are no imports`() = assertFormatted(
      """
      |package foo123
      |
      |/*
      |bar
      |*/
      |"""
          .trimMargin(),
  )

  @Test
  fun `basic annotations`() = assertFormatted(
      """
      |@Fancy
      |class Foo {
      |  @Fancy
      |  fun baz(@Fancy foo: Int) {
      |    @Fancy val a = 1 + foo
      |  }
      |}
      |"""
          .trimMargin(),
  )

  @Test
  fun `function calls with multiple arguments`() = assertFormatted(
      """
      |fun f() {
      |  foo(1, 2, 3)
      |
      |  foo(
      |      123456789012345678901234567890,
      |      123456789012345678901234567890,
      |      123456789012345678901234567890)
      |}
      |"""
          .trimMargin(),
  )

  @Test
  fun `function calls with multiple named arguments`() = assertFormatted(
      """
      |fun f() {
      |  foo(1, b = 2, c = 3)
      |
      |  foo(
      |      123456789012345678901234567890,
      |      b = 23456789012345678901234567890,
      |      c = 3456789012345678901234567890)
      |}
      |"""
          .trimMargin(),
  )

  @Test
  fun `named arguments indent their value expression`() = assertFormatted(
      """
      |fun f() =
      |    Bar(
      |        tokens =
      |            mutableListOf<Token>().apply {
      |              // Printing
      |              print()
      |            },
      |        duration = duration)
      |"""
          .trimMargin(),
  )

  @Test
  fun `Trailing comma forces variable value in list onto new line with manageTrailingCommas turned off`() = assertFormatted(
      """
      val aVar = setOf(
          Env.Dev,
          Env.Prod,
      )
      val aVar = setOf(Env.Dev, Env.Prod)

      """
          .trimIndent(),
      deduceMaxWidth = false,
  )

  @Test
  fun `nested functions, maps, and statements in named parameters - default`() = assertFormatted(
      """
      |////////////////////////////////////////////////////////////////////
      |function(
      |    param =
      |        (rate downTo min step step).drop(1).map {
      |          nestedFun(
      |              rate =
      |                  rate(
      |                      value =
      |                          firstArg<Input>().info.get(0).rate.value))
      |        })
      |"""
          .trimMargin(),
      deduceMaxWidth = true,
  )

  @Test
  fun `nested functions, maps, and statements in named parameters kotlin lang formats differently than default`() = assertFormatted(
      """
      |/////////////////////////////////////////////////////////////////////
      |function(
      |    param =
      |        (rate downTo min step step).drop(1).map {
      |            nestedFun(
      |                rate =
      |                    rate(
      |                        value =
      |                            firstArg<Input>().info.get(0).rate.value,
      |                    ),
      |            )
      |        },
      |)
      |"""
          .trimMargin(),
      formattingOptions = Formatter.KOTLINLANG_FORMAT,
      deduceMaxWidth = true,
  )

  @Test
  fun `complex calls and calculation in named parameters without wrapping`() = assertFormatted(
      """
      calculateMath(
          r = apr.sc(10) / BigDecimal(100) / BigDecimal(12),
          n = 12 * term,
          numerator = ((BigDecimal.ONE + r).pow(n)) - BigDecimal.ONE,
          denominator = r * (BigDecimal.ONE + r).pow(n),
      )

      """
          .trimIndent(),
      deduceMaxWidth = false,
  )

  @Test
  fun `Arguments are blocks`() = assertFormatted(
      """
      |//////////////////////////////////////////////////
      |override fun visitProperty(property: KtProperty) {
      |  builder.sync(property)
      |  builder.block(ZERO) {
      |    declareOne(
      |        kind = DeclarationKind.FIELD,
      |        modifiers = property.modifierList,
      |        valOrVarKeyword =
      |            property.valOrVarKeyword.text,
      |        typeParameters =
      |            property.typeParameterList,
      |        receiver = property.receiverTypeReference,
      |        name = property.nameIdentifier?.text,
      |        type = property.typeReference,
      |        typeConstraintList =
      |            property.typeConstraintList,
      |        delegate = property.delegate,
      |        initializer = property.initializer)
      |  }
      |}
      |"""
          .trimMargin(),
      deduceMaxWidth = true,
  )

  @Test
  fun `anonymous function`() = assertFormatted(
      """
      |fun f() {
      |  setListener(
      |      fun(number: Int) {
      |        println(number)
      |      })
      |}
      |"""
          .trimMargin(),
  )

  @Test
  fun `anonymous function with receiver`() = assertFormatted(
      """
      |fun f() {
      |  setListener(
      |      fun View.() {
      |        println(this)
      |      })
      |}
      |"""
          .trimMargin(),
  )

  @Test
  fun `newlines between clauses of when() are preserved`() {
    assertThatFormatting(
        """
        |fun f(x: Int) {
        |  when (x) {
        |
        |
        |    1 -> print(1)
        |    2 -> print(2)
        |
        |
        |    3 ->
        |        // Comment
        |        print(3)
        |
        |    else -> {
        |      print("else")
        |    }
        |
        |  }
        |}
        |"""
            .trimMargin(),
    )
        .isEqualTo(
            """
            |fun f(x: Int) {
            |  when (x) {
            |    1 -> print(1)
            |    2 -> print(2)
            |
            |    3 ->
            |        // Comment
            |        print(3)
            |
            |    else -> {
            |      print("else")
            |    }
            |  }
            |}
            |"""
                .trimMargin(),
        )
  }

  @Test
  fun `when() with a subject expression`() = assertFormatted(
      """
      |fun f(x: Int) {
      |  when (x) {
      |    1 -> print(1)
      |    2 -> print(2)
      |    3 ->
      |        // Comment
      |        print(3)
      |    else -> {
      |      print("else")
      |    }
      |  }
      |}
      |"""
          .trimMargin(),
  )

  @Test
  fun `when() expression with complex predicates`() = assertFormatted(
      """
      |fun f(x: Int) {
      |  when {
      |    x == 1 || x == 2 -> print(1)
      |    x == 3 && x != 4 -> print(2)
      |    else -> {
      |      print(3)
      |    }
      |  }
      |}
      |"""
          .trimMargin(),
  )

  @Test
  fun `when() expression with several conditions`() = assertFormatted(
      """
      |fun f(x: Int) {
      |  when {
      |    0,
      |    1 -> print(1)
      |    else -> print(0)
      |  }
      |}
      |"""
          .trimMargin(),
  )

  @Test
  fun `when() expression with is and in`() = assertFormatted(
      """
      |fun f(x: Int) {
      |  when (x) {
      |    is String -> print(1)
      |    !is String -> print(2)
      |    in 1..3 -> print()
      |    in a..b -> print()
      |    in a..3 -> print()
      |    in 1..b -> print()
      |    !in 1..b -> print()
      |    in 1..<b -> print()
      |    else -> print(3)
      |  }
      |}
      |"""
          .trimMargin(),
  )

  @Test
  fun `when() expression with enum values`() = assertFormatted(
      """
      |fun f(x: Color) {
      |  when (x) {
      |    is Color.Red -> print(1)
      |    is Color.Green -> print(2)
      |    else -> print(3)
      |  }
      |}
      |"""
          .trimMargin(),
  )

  @Test
  fun `when() expression with generic matcher and exhaustive`() = assertFormatted(
      """
      |fun f(x: Result) {
      |  when (x) {
      |    is Success<*> -> print(1)
      |    is Failure -> print(2)
      |  }.exhaustive
      |}
      |"""
          .trimMargin(),
  )

  @Test
  fun `when() expression with multiline condition`() = assertFormatted(
      """
      |//////////////////////////
      |fun foo() {
      |  when (expressions1 +
      |      expression2 +
      |      expression3) {
      |    1 -> print(1)
      |    2 -> print(2)
      |  }
      |
      |  when (foo(
      |      expressions1 &&
      |          expression2 &&
      |          expression3)) {
      |    1 -> print(1)
      |    2 -> print(2)
      |  }
      |}
      |"""
          .trimMargin(),
      deduceMaxWidth = true,
  )

  @Test
  fun `lambda assigned to variable does not break before brace`() = assertFormatted(
      """
      |fun doIt() {
      |  val lambda = {
      |    doItOnce()
      |    doItTwice()
      |  }
      |}
      |
      |fun foo() = {
      |  doItOnce()
      |  doItTwice()
      |}
      |"""
          .trimMargin(),
  )

  @Test
  fun `when() expression storing in local variable`() = assertFormatted(
      """
      |fun f(x: Result) {
      |  when (val y = x.improved()) {
      |    is Success<*> -> print(y)
      |    is Failure -> print(2)
      |  }
      |}
      |"""
          .trimMargin(),
  )

  @Test
  fun `line breaks inside when expressions and conditions`() = assertFormatted(
      """
      |fun f() {
      |  return Text.create(c)
      |      .onTouch {
      |        when (it.motionEvent.action) {
      |          ACTION_DOWN ->
      |              Toast.makeText(it.view.context, "Down!", Toast.LENGTH_SHORT, blablabla).show()
      |          ACTION_UP -> Toast.makeText(it.view.context, "Up!", Toast.LENGTH_SHORT).show()
      |          ACTION_DOWN ->
      |              Toast.makeText(
      |                      it.view.context, "Down!", Toast.LENGTH_SHORT, blablabla, blablabl, blabla)
      |                  .show()
      |        }
      |      }
      |      .build()
      |}
      |"""
          .trimMargin(),
  )

  @Test
  fun `when() expression with lambda body`() = assertFormatted(
      """
      |fun f(x: ModifierType) {
      |  when (x) {
      |    is FirstModifierType -> { myObject ->
      |      doCustomModification(x, myObject)
      |    }
      |    is SecondModifierType -> { myObject ->
      |      doOtherModification(x, myObject)
      |    }
      |  }
      |}
      |"""
          .trimMargin(),
  )

  @Test
  fun `when() expression with no-param lambda body`() = assertFormatted(
      """
      |fun f(x: Int) {
      |  when (x) {
      |    0 -> {
      |      doSomething()
      |    }
      |    1 -> print("hello")
      |  }
      |}
      |"""
          .trimMargin(),
  )

  @Test
  fun `when() expression with lambda body containing multiple statements`() = assertFormatted(
      """
      |fun f(x: ModifierType) {
      |  when (x) {
      |    is FirstModifierType -> { myObject ->
      |      doFirstThing(myObject)
      |      doSecondThing(myObject)
      |      doThirdThing(myObject)
      |    }
      |  }
      |}
      |"""
          .trimMargin(),
  )

  @Test
  fun `function return types`() = assertFormatted(
      """
      |fun f1(): Int = 0
      |
      |fun f2(): Int {}
      |"""
          .trimMargin(),
  )

  @Test
  fun `multi line function without a block body`() = assertFormatted(
      """
      |/////////////////////////
      |fun longFunctionNoBlock():
      |    Int =
      |    1234567 + 1234567
      |
      |fun shortFun(): Int =
      |    1234567 + 1234567
      |"""
          .trimMargin(),
      deduceMaxWidth = true,
  )

  @Test
  fun `return type doesn't fit in one line`() = assertFormatted(
      """
      |//////////////////////////////////////////////////
      |interface X {
      |  fun f(
      |      arg1: Arg1Type,
      |      arg2: Arg2Type
      |  ): Map<String, Map<String, Double>>? {
      |    //
      |  }
      |
      |  fun functionWithGenericReturnType(
      |      arg1: Arg1Type,
      |      arg2: Arg2Type
      |  ): Map<String, Map<String, Double>>? {
      |    //
      |  }
      |}
      |"""
          .trimMargin(),
      deduceMaxWidth = true,
  )

  @Test
  fun `list of superclasses`() = assertFormatted(
      """
      |class Derived2 : Super1, Super2 {}
      |
      |class Derived1 : Super1, Super2
      |
      |class Derived3(a: Int) : Super1(a)
      |
      |class Derived4 : Super1()
      |
      |class Derived5 : Super3<Int>()
      |"""
          .trimMargin(),
  )

  @Test
  fun `list of superclasses over multiple lines`() = assertFormatted(
      """
      |////////////////////
      |class Derived2 :
      |    Super1,
      |    Super2 {}
      |
      |class Derived1 :
      |    Super1, Super2
      |
      |class Derived3(
      |    a: Int
      |) : Super1(a)
      |
      |class Derived4 :
      |    Super1()
      |
      |class Derived5 :
      |    Super3<Int>()
      |"""
          .trimMargin(),
      deduceMaxWidth = true,
  )

  @Test
  fun `annotations with parameters`() = assertFormatted(
      """
      |@AnnWithArrayValue(1, 2, 3) class C
      |"""
          .trimMargin(),
  )

  @Test
  fun `method modifiers`() = assertFormatted(
      """
      |override internal fun f() {}
      |"""
          .trimMargin(),
  )

  @Test
  fun `class modifiers`() = assertFormatted(
      """
      |abstract class Foo
      |
      |inner class Foo
      |
      |final class Foo
      |
      |open class Foo
      |"""
          .trimMargin(),
  )

  @Test
  fun `kdoc comments`() {
    val code =
        """
        |/**
        | * foo
        | */ class F {
        |
        | }
        |"""
            .trimMargin()
    val expected =
        """
        |/** foo */
        |class F {}
        |"""
            .trimMargin()
    assertThatFormatting(code).isEqualTo(expected)
  }

  @Test
  fun `nested kdoc comments`() {
    val code =
        """
        |/**
        | * foo /* bla */
        | */ class F {
        |
        | }
        |"""
            .trimMargin()
    val expected =
        """
        |/** foo /* bla */ */
        |class F {}
        |"""
            .trimMargin()
    assertThatFormatting(code).isEqualTo(expected)
  }

  @Test
  fun `nested kdoc inside code block`() = assertFormatted(
      """
      |/**
      | * ```
      | * edit -> { /* open edit screen */ }
      | * ```
      | */
      |fun foo() {}
      |"""
          .trimMargin(),
  )

  @Test
  fun `formatting kdoc doesn't add p HTML tags`() = assertFormatted(
      """
      |/**
      | * Bla bla bla bla
      | *
      | * This is an inferred paragraph, and as you can see, we don't add a p tag to it, even though bla
      | * bla.
      | *
      | * <p>On the other hand, we respect existing tags, and don't remove them.
      | */
      |"""
          .trimMargin(),
  )

  @Test
  fun `formatting kdoc preserves lists`() = assertFormatted(
      """
      |/**
      | * Here are some fruit I like:
      | * - Banana
      | * - Apple
      | *
      | * This is another paragraph
      | */
      |"""
          .trimMargin(),
  )

  @Test
  fun `formatting kdoc lists with line wraps breaks and merges correctly`() {
    val code =
        """
        |/**
        | * Here are some fruit I like:
        | * - Banana Banana Banana Banana Banana Banana Banana Banana Banana Banana Banana Banana Banana Banana Banana Banana Banana Banana
        | * - Apple Apple Apple Apple
        | *   Apple Apple
        | *
        | * This is another paragraph
        | */
        |"""
            .trimMargin()
    val expected =
        """
        |/**
        | * Here are some fruit I like:
        | * - Banana Banana Banana Banana Banana Banana Banana Banana Banana Banana Banana Banana Banana
        | *   Banana Banana Banana Banana Banana
        | * - Apple Apple Apple Apple Apple Apple
        | *
        | * This is another paragraph
        | */
        |"""
            .trimMargin()
    assertThatFormatting(code).isEqualTo(expected)
  }

  @Test
  fun `formatting kdoc preserves lists of asterisks`() = assertFormatted(
      """
      |/**
      | * Here are some fruit I like:
      | * * Banana
      | * * Apple
      | *
      | * This is another paragraph
      | */
      |"""
          .trimMargin(),
  )

  @Test
  fun `formatting kdoc preserves numbered`() = assertFormatted(
      """
      |/**
      | * Here are some fruit I like:
      | * 1. Banana
      | * 2. Apple
      | *
      | * This is another paragraph
      | */
      |"""
          .trimMargin(),
  )

  @Test
  fun `formatting kdoc with markdown errors`() = assertFormatted(
      """
      |/** \[ */
      |fun markdownError() = Unit
      |"""
          .trimMargin(),
  )

  @Test
  fun `return statement with value`() = assertFormatted(
      """
      |fun random(): Int {
      |  return 4
      |}
      |"""
          .trimMargin(),
  )

  @Test
  fun `return statement without value`() = assertFormatted(
      """
      |fun print(b: Boolean) {
      |  print(b)
      |  return
      |}
      |"""
          .trimMargin(),
  )

  @Test
  fun `return expression without value`() = assertFormatted(
      """
      |fun print(b: Boolean?) {
      |  print(b ?: return)
      |}
      |"""
          .trimMargin(),
  )

  @Test
  fun `if statement without else`() = assertFormatted(
      """
      |fun maybePrint(b: Boolean) {
      |  if (b) {
      |    println(b)
      |  }
      |}
      |"""
          .trimMargin(),
  )

  @Test
  fun `if statement with else`() = assertFormatted(
      """
      |fun maybePrint(b: Boolean) {
      |  if (b) {
      |    println(2)
      |  } else {
      |    println(1)
      |  }
      |}
      |"""
          .trimMargin(),
  )

  @Test
  fun `if expression with else`() = assertFormatted(
      """
      |fun maybePrint(b: Boolean) {
      |  println(if (b) 1 else 2)
      |  println(
      |      if (b) {
      |        val a = 1 + 1
      |        2 * a
      |      } else 2)
      |  return if (b) 1 else 2
      |}
      |"""
          .trimMargin(),
  )

  @Test
  fun `if expression with break before else`() = assertFormatted(
      """
      |//////////////////////////////
      |fun compute(b: Boolean) {
      |  val c =
      |      if (a + b < 20) a + b
      |      else a
      |  return if (a + b < 20) a + b
      |  else c
      |}
      |"""
          .trimMargin(),
      deduceMaxWidth = true,
  )

  @Test
  fun `if expression with break before expressions`() = assertFormatted(
      """
      |//////////////////////////
      |fun compute(b: Boolean) {
      |  val c =
      |      if (a + b < 20)
      |          a + b
      |      else if (a < 20) a
      |      else
      |          a + b + b + 1000
      |  return if (a + b < 20)
      |      a + b
      |  else c
      |}
      |"""
          .trimMargin(),
      deduceMaxWidth = true,
  )

  @Test
  fun `blocky expressions in if-else`() = assertFormatted(
      """
      |fun numbers() {
      |  if (true)
      |      do {
      |        eat("is")
      |        matches += type()
      |      } while (eat(","))
      |  else
      |      while (1 < 2) {
      |        println("Everything is okay")
      |      }
      |}
      |"""
          .trimMargin(),
  )

  @Test
  fun `if expression with multiline condition`() = assertFormatted(
      """
      |////////////////////////////
      |fun foo() {
      |  if (expressions1 &&
      |      expression2 &&
      |      expression3) {
      |    bar()
      |  }
      |
      |  if (foo(
      |      expressions1 &&
      |          expression2 &&
      |          expression3)) {
      |    bar()
      |  }
      |}
      |"""
          .trimMargin(),
      deduceMaxWidth = true,
  )

  @Test
  fun `if expression with comment`() = assertFormatted(
      """
      |////////////////////////////
      |fun foo() {
      |  if (expressions1)
      |      // comment
      |      bar()
      |}
      |"""
          .trimMargin(),
      deduceMaxWidth = true,
  )

  @Test
  fun `assignment expression on multiple lines`() = assertFormatted(
      """
      |//////////////////////////////////////////////////
      |fun f() {
      |  var myVariable = 5
      |  myVariable =
      |      function1(4, 60, 8) + function2(57, 39, 20)
      |}
      |"""
          .trimMargin(),
      deduceMaxWidth = true,
  )

  @Test
  fun `A program that tickled a bug in KotlinInput`() = assertFormatted(
      """
      |val x = 2
      |"""
          .trimMargin(),
  )

  @Test
  fun `a few variations of constructors`() = assertFormatted(
      """
      |//////////////////////////////////////////////////////
      |class Foo constructor(number: Int) {}
      |
      |class Foo2 private constructor(number: Int) {}
      |
      |class Foo3 @Inject constructor(number: Int) {}
      |
      |class Foo4 @Inject private constructor(number: Int) {}
      |
      |class Foo5
      |@Inject
      |private constructor(
      |    number: Int,
      |    number2: Int,
      |    number3: Int,
      |    number4: Int,
      |    number5: Int,
      |    number6: Int
      |) {}
      |
      |class Foo6
      |@Inject
      |private constructor(hasSpaceForAnnos: Innnt) {
      |  //                                           @Inject
      |}
      |
      |class FooTooLongForCtorAndSupertypes
      |@Inject
      |private constructor(x: Int) : NoooooooSpaceForAnnos {}
      |"""
          .trimMargin(),
      deduceMaxWidth = true,
  )

  @Test
  fun `a primary constructor without a class body `() = assertFormatted(
      """
      |/////////////////////////
      |data class Foo(
      |    val number: Int = 0
      |)
      |"""
          .trimMargin(),
      deduceMaxWidth = true,
  )

  @Test
  fun `a secondary constructor without a body`() = assertFormatted(
      """
      |///////////////////////////
      |data class Foo {
      |  constructor(
      |      val number: Int = 0
      |  )
      |}
      |"""
          .trimMargin(),
      deduceMaxWidth = true,
  )

  @Test
  fun `a secondary constructor with a body breaks before closing parenthesis`() = assertFormatted(
      """
      |///////////////////////////
      |data class Foo {
      |  constructor(
      |      val number: Int = 0
      |  ) {}
      |}
      |"""
          .trimMargin(),
      deduceMaxWidth = true,
  )

  @Test
  fun `a constructor with many arguments over breaking to next line`() = assertFormatted(
      """
      |data class Foo(
      |    val number: Int,
      |    val name: String,
      |    val age: Int,
      |    val title: String,
      |    val offspring2: List<Foo>
      |) {}
      |"""
          .trimMargin(),
  )

  @Test
  fun `a constructor with keyword and many arguments over breaking to next line`() = assertFormatted(
      """
      |data class Foo
      |constructor(
      |    val name: String,
      |    val age: Int,
      |    val title: String,
      |    val offspring: List<Foo>,
      |    val foo: String
      |) {}
      |"""
          .trimMargin(),
  )

  @Test
  fun `a constructor with many arguments over multiple lines`() = assertFormatted(
      """
      |//////////////////////////////////////////////////
      |data class Foo
      |constructor(
      |    val number: Int,
      |    val name: String,
      |    val age: Int,
      |    val title: String,
      |    val offspring: List<Foo>
      |) {}
      |"""
          .trimMargin(),
      deduceMaxWidth = true,
  )

  @Test
  fun `handle secondary constructors`() = assertFormatted(
      """
      |class Foo private constructor(number: Int) {
      |  private constructor(n: Float) : this(1)
      |
      |  private constructor(n: Double) : this(1) {
      |    println("built")
      |  }
      |}
      |"""
          .trimMargin(),
  )

  @Test
  fun `a secondary constructor with many arguments over multiple lines`() = assertFormatted(
      """
      |//////////////////////////////////////////////////
      |data class Foo {
      |  constructor(
      |      val number: Int,
      |      val name: String,
      |      val age: Int,
      |      val title: String,
      |      val offspring: List<Foo>
      |  )
      |}
      |"""
          .trimMargin(),
      deduceMaxWidth = true,
  )

  @Test
  fun `a secondary constructor with many arguments passed to delegate`() = assertFormatted(
      """
      |//////////////////////////////////////////////////
      |data class Foo {
      |  constructor(
      |      val number: Int,
      |      val name: String,
      |      val age: Int,
      |      val title: String,
      |      val offspring: List<Foo>
      |  ) : this(
      |      number,
      |      name,
      |      age,
      |      title,
      |      offspring,
      |      offspring)
      |}
      |"""
          .trimMargin(),
      deduceMaxWidth = true,
  )

  @Test
  fun `a secondary constructor with no arguments passed to delegate`() = assertFormatted(
      """
      |//////////////////////////////////////////////////
      |data class Foo {
      |  constructor() :
      |      this(
      |          Foo.createSpeciallyDesignedParameter(),
      |          Foo.createSpeciallyDesignedParameter(),
      |      )
      |}
      |"""
          .trimMargin(),
      deduceMaxWidth = true,
  )

  @Test
  fun `secondary constructor with param list that fits in one line, with delegate`() = assertFormatted(
      """
      |class C {
      |  constructor(
      |      context: Context?,
      |      attrs: AttributeSet?,
      |      defStyleAttr: Int,
      |      defStyleRes: Int
      |  ) : super(context, attrs, defStyleAttr, defStyleRes) {
      |    init(attrs)
      |  }
      |}
      |"""
          .trimMargin(),
  )

  @Test
  fun `handle calling super constructor in secondary constructor`() = assertFormatted(
      """
      |class Foo : Bar {
      |  internal constructor(number: Int) : super(number) {}
      |}
      |"""
          .trimMargin(),
  )

  @Test
  fun `handle super statement with with type argument`() = assertFormatted(
      """
      |class Foo : Bar(), FooBar {
      |  override fun doIt() {
      |    super<FooBar>.doIt()
      |  }
      |}
      |"""
          .trimMargin(),
  )

  @Test
  fun `handle super statement with with label argument`() = assertFormatted(
      """
      |class Foo : Bar(), FooBar {
      |  override fun doIt() {
      |    foo.doThat {
      |      super<FooBar>@Foo.doIt()
      |
      |      // this one is actually generics on the call expression, not super
      |      super@Foo<FooBar>.doIt()
      |    }
      |  }
      |}
      |"""
          .trimMargin(),
  )

  @Test
  fun `primary constructor without parameters with a KDoc`() = assertFormatted(
      """
      |class Class
      |/** A comment */
      |constructor() {}
      |"""
          .trimMargin(),
  )

  @Test
  fun `handle objects`() = assertFormatted(
      """
      |object Foo(n: Int) {}
      |"""
          .trimMargin(),
  )

  @Test
  fun `handle object expression`() = assertFormatted(
      """
      |fun f(): Any {
      |  return object : Adapter() {}
      |}
      |"""
          .trimMargin(),
  )

  @Test
  fun `handle object expression in parenthesis`() = assertFormatted(
      """
      |fun f(): Any {
      |  return (object : Adapter() {})
      |}
      |"""
          .trimMargin(),
  )

  @Test
  fun `handle array indexing operator`() = assertFormatted(
      """
      |fun f(a: Magic) {
      |  a[3]
      |  b[3, 4]
      |}
      |"""
          .trimMargin(),
  )

  @Test
  fun `keep array indexing grouped with expression is possible`() = assertFormatted(
      """
      |///////////////////////
      |fun f(a: Magic) {
      |  foo.bar()
      |      .foobar[1, 2, 3]
      |  foo.bar()
      |      .foobar[
      |          1,
      |          2,
      |          3,
      |          4,
      |          5]
      |  foo.bar()
      |      .foobar[1, 2, 3]
      |      .barfoo[3, 2, 1]
      |}
      |"""
          .trimMargin(),
      deduceMaxWidth = true,
  )

  @Test
  fun `mixed chains`() = assertFormatted(
      """
      |///////////////////////
      |fun f(a: Magic) {
      |  foo.bar()
      |      .foobar[1, 2, 3]
      |  foo.bar()
      |      .foobar[
      |          1,
      |          2,
      |          3,
      |          4,
      |          5]
      |  foo.bar()
      |      .foobar[1, 2, 3]
      |      .barfoo[3, 2, 1]
      |}
      |"""
          .trimMargin(),
      deduceMaxWidth = true,
  )

  @Test
  fun `handle destructuring declaration`() = assertFormatted(
      """
      |///////////////////////////////////////////////
      |fun f() {
      |  val (a, b: Int) = listOf(1, 2)
      |  val (asd, asd, asd, asd, asd, asd, asd) =
      |      foo.bar(asdasd, asdasd)
      |
      |  val (accountType, accountId) =
      |      oneTwoThreeFourFiveSixSeven(
      |          foo, bar, zed, boo)
      |}
      |"""
          .trimMargin(),
      deduceMaxWidth = true,
  )

  @Test
  fun `handle name based destructuring declaration`() {
    if (KotlinVersion.CURRENT < KotlinVersion(2, 3)) return

    assertThatFormatting(
        """
        |fun f(d: D) {
        |  val [a, b         ] = d
        |  val (a, x = b         ) = d
        |  var [a, _     ] = d
        |  [var a, var b     ] = d
        |  [val a: Int, val _: String    ] = d
        |  (var x = a, var y = b     ) = d
        |  (val x: Int = a, var y: String = b    ) = d
        |}
        |"""
            .trimMargin(),
    )
        .isEqualTo(
            """
            |fun f(d: D) {
            |  val [a, b] = d
            |  val (a, x = b) = d
            |  var [a, _] = d
            |  [var a, var b] = d
            |  [val a: Int, val _: String] = d
            |  (var x = a, var y = b) = d
            |  (val x: Int = a, var y: String = b) = d
            |}
            |"""
                .trimMargin(),
        )
  }

  @Test
  fun `chains with derferences and array indexing`() = assertFormatted(
      """
      |///////////////////////
      |fun f() {
      |  foo.bam()
      |      .uber!![0, 1, 2]
      |      .boom()[1, 3, 5]
      |      .lah
      |      .doo { it }
      |      .feep[1]
      |      as Boo
      |}
      |"""
          .trimMargin(),
      deduceMaxWidth = true,
  )

  @Test
  fun `block like syntax after dereferences and indexing with short lines`() = assertFormatted(
      """
      |///////////////////////
      |fun f() {
      |  foo.bam()
      |      .uber!![0, 1, 2]
      |      .forEach {
      |        println(it)
      |      }
      |}
      |"""
          .trimMargin(),
      deduceMaxWidth = true,
  )

  @Test
  fun `block like syntax after dereferences and indexing with long lines`() = assertFormatted(
      """
      |//////////////////////////////////
      |fun f() {
      |  foo.uber!![0, 1, 2].forEach {
      |    println(it)
      |  }
      |}
      |"""
          .trimMargin(),
      deduceMaxWidth = true,
  )

  @Test
  fun `try to keep type names together`() = assertFormatted(
      """
      |///////////////////////
      |fun f() {
      |  com.facebook.foo.Foo(
      |      1, 2)
      |  com.facebook.foo
      |      .Foo(1, 2)
      |      .andAlsoThis()
      |  com.facebook.Foo.foo(
      |      1, 2)
      |  com.facebook
      |      .foobarFoo
      |      .foo(1, 2)
      |  foo.invoke(
      |      foo, bar, bar)
      |  foo.invoke(foo, bar)
      |      .invoke()
      |  FooFoo.foooooooo()
      |      .foooooooo()
      |}
      |"""
          .trimMargin(),
      deduceMaxWidth = true,
  )

  @Test
  fun `avoid breaking brackets and keep them with array name`() = assertFormatted(
      """
      |/////////////////////////////////////////////////////////////////////////
      |fun f() {
      |  val a =
      |      invokeIt(context.packageName)
      |          .getInternalMutablePackageInfo(context.packageName)
      |          .someItems[0]
      |          .getInternalMutablePackageInfo(context.packageName)
      |          .someItems[0]
      |          .doIt()
      |}
      |"""
          .trimMargin(),
      deduceMaxWidth = true,
  )

  @Test
  fun `keep function call with type name even if array expression is next`() = assertFormatted(
      """
      |class f {
      |  private val somePropertyWithBackingOne
      |    get() =
      |        _somePropertyWithBackingOne
      |            ?: Classname.getStuff<SomePropertyRelatedClassProvider>(requireContext())[
      |                    somePropertiesProvider, somePropertyCallbacks]
      |                .also { _somePropertyWithBackingOne = it }
      |}
      |"""
          .trimMargin(),
  )

  @Test
  fun `array access in middle of chain and end of it behaves similarly`() = assertFormatted(
      """
      |//////////////////////////////////////
      |fun f() {
      |  if (aaaaa == null ||
      |      aaaaa.bbbbb[0] == null ||
      |      aaaaa.bbbbb[0].cc == null ||
      |      aaaaa.bbbbb[0].dddd == null) {
      |    println()
      |  }
      |}
      |"""
          .trimMargin(),
      deduceMaxWidth = true,
  )

  @Test
  fun `handle qmark for nullable types`() = assertFormatted(
      """
      |var x: Int? = null
      |var x: (Int)? = null
      |var x: (Int?) = null
      |var x: ((Int))? = null
      |var x: ((Int?)) = null
      |var x: ((Int)?) = null
      |
      |var x: @Anno Int? = null
      |var x: @Anno() (Int)? = null
      |var x: @Anno (Int?) = null
      |var x: (@Anno Int)? = null
      |var x: (@Anno Int?) = null
      |var x: (@Anno() (Int))? = null
      |var x: (@Anno (Int?)) = null
      |var x: (@Anno() (Int)?) = null
      |"""
          .trimMargin(),
  )

  @Test
  fun `nullable function type`() = assertFormatted(
      """
      |var listener: ((Boolean) -> Unit)? = null
      |"""
          .trimMargin(),
  )

  @Test
  fun `redundant parenthesis in function types`() = assertFormatted(
      """
      |val a: (Int) = 7
      |
      |var listener: ((Boolean) -> Unit) = foo
      |"""
          .trimMargin(),
  )

  @Test
  fun `handle string literals`() = assertFormatted(
      """
      |fun doIt(world: String) {
      |  println("Hello world!")
      |  println("Hello! ${'$'}world")
      |  println("Hello! ${'$'}{"wor" + "ld"}")
      |}
      |"""
          .trimMargin(),
  )

  @Test
  fun `handle multiline string literals`() = assertFormatted(
      """
          |fun doIt(world: String) {
          |  println(
          |      ${TQ}Hello
          |      world!${TQ})
          |  println(
          |      ${TQ}Hello
          |      world!${TQ},
          |      ${TQ}Goodbye
          |      world!${TQ})
          |}
          |"""
          .trimMargin(),
  )

  @Test
  fun `Trailing whitespaces are preserved in multiline strings`() {
    val code = listOf(
        "fun doIt(world: String) {",
        "  println(",
        "      ${TQ}This line has trailing whitespace         ",
        "      world!${TQ})",
        "  println(",
        "      ${TQ}This line has trailing whitespace \$s     ",
        "      world!${TQ})",
        "  println(",
        "      ${TQ}This line has trailing whitespace \${s}   ",
        "      world!${TQ})",
        "  println(",
        "      ${TQ}This line has trailing whitespace \$      ",
        "      world!${TQ})",
        "}",
        "",
    )
        .joinToString("\n")
    assertThatFormatting(code).allowTrailingWhitespace().isEqualTo(code)
  }

  @Test
  fun `Consecutive line breaks in multiline strings are preserved`() = assertFormatted(
      """
          |val x =
          |    $TQ
          |
          |
          |
          |Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do
          |$TQ
          |"""
          .trimMargin(),
  )

  @Test
  fun `multiline trimMargin special handling`() {
    val before =
        """
        |val margin =
        |    ${TQ}example
        |    | of
        |  |   a
        |     |multiline
        |   |  string
        |    |$TQ
        |        .trimMargin()
        |"""
            .trimMargin()
    val after =
        """
        |val margin =
        |    $TQ
        |    |example
        |    | of
        |    |   a
        |    |multiline
        |    |  string
        |    |$TQ
        |        .trimMargin()
        |"""
            .trimMargin()

    assertThatFormatting(before).isEqualTo(after)
  }

  @Test
  fun `multiline trimIndent special handling`() {
    val before =
        """
        |val indent =
        |    $TQ     
        |         example
        |          of
        |            a
        |
        |         multiline
        |           string
        |         $TQ
        |         .trimIndent()
        |"""
            .trimMargin()
    val after =
        """
        |val indent =
        |    $TQ
        |    example
        |     of
        |       a
        |
        |    multiline
        |      string
        |    $TQ
        |        .trimIndent()
        |"""
            .trimMargin()

    assertThatFormatting(before).isEqualTo(after)
  }

  @Test
  fun `multiline trimIndent and trimMargin inside of function call`() {
    assertThatFormatting(
        """
            |foo(
            |    ${TQ}example
            |         | of
            |       |   a
            |
            |          |multiline
            |        |  string
            |         |$TQ
            |         .trimMargin()
            |)
            |   .bar(
            |    ${TQ}
            |         example
            |          of
            |            a
            |
            |         multiline
            |           string
            |         $TQ
            |         .trimIndent()
            |   )
            |"""
            .trimMargin(),
    )
        .withOptions(META_FORMAT)
        .isEqualTo(
            """
            |foo(
            |    $TQ
            |    |example
            |    | of
            |    |   a
            |    |
            |    |multiline
            |    |  string
            |    |$TQ
            |        .trimMargin(),
            |)
            |    .bar(
            |        $TQ
            |        example
            |         of
            |           a
            |
            |        multiline
            |          string
            |        $TQ
            |            .trimIndent(),
            |    )
            |"""
                .trimMargin(),
        )
  }

  @Test
  fun `trimIndent and trimMargin formatting does not add new lines`() {
    assertThatFormatting(
        """
            |val margin =
            |    $TQ${"   "}
            |     |is this the end of the line?$TQ
            |        .trimMargin()
            |"""
            .trimMargin(),
    )
        .isEqualTo(
            """
            |val margin =
            |    $TQ
            |    |is this the end of the line?
            |    $TQ
            |        .trimMargin()
            |"""
                .trimMargin(),
        )

    assertThatFormatting(
        """
            |val margin =
            |    $TQ${"   "}
            |     is this the end of the line?$TQ
            |        .trimIndent()
            |"""
            .trimMargin(),
    )
        .isEqualTo(
            """
            |val margin =
            |    $TQ
            |    is this the end of the line?
            |    $TQ
            |        .trimIndent()
            |"""
                .trimMargin(),
        )
  }

  @Test
  fun `properly handles trimMargin blank lines at the end of multiline string`() = assertThatFormatting(
      """
              |val margin =
              |    $TQ
              |     |test
              |     string
              |     |
              |     $TQ
              |        .trimMargin()
              |"""
          .trimMargin(),
  )
      .isEqualTo(
          """
              |val margin =
              |    $TQ
              |    |test
              |    |     string
              |    |$TQ
              |        .trimMargin()
              |"""
              .trimMargin(),
      )

  @Test
  fun `properly handles trimMargin that has margin in the first line`() = assertThatFormatting(
      """
              |val margin =
              |    $TQ |test
              |    |     string
              |    |$TQ
              |        .trimMargin()
              |"""
          .trimMargin(),
  )
      .isEqualTo(
          """
              |val margin =
              |    $TQ
              |    |test
              |    |     string
              |    |$TQ
              |        .trimMargin()
              |"""
              .trimMargin(),
      )

  @Test
  fun `handles multiline trim with template expressions inside of it`() {
    assertFormatted(
        """
        |val margin =
        |    $TQ
        |    |my math = ${'$'}{
        |      if (condition) {
        |      "}" + (1 + 2)
        |        .toString()
        |      } else {
        |        $TQ
        |        my own multiline trimmed string
        |        is here
        |        $TQ
        |            .trimIndent()
        |      }
        |    }
        |    |     string
        |    |$TQ
        |        .trimMargin()
        |"""
            .trimMargin(),
    )

    assertFormatted(
        """
        |val margin =
        |    ${"$$"}$TQ
        |    |my math = ${"$$"}{
        |      if (condition) {
        |      "}" + (1 + 2)
        |        .toString()
        |      }else{
        |        $TQ
        |        my own multiline trimmed string
        |        is here
        |        $TQ.trimIndent()
        |      }
        |    }
        |    |     string
        |    |$TQ
        |        .trimMargin()
        |"""
            .trimMargin(),
    )
  }

  @Test
  fun `handles multiline trimMargin with pipe not being the margin character`() {
    assertThatFormatting(
        """
            |val margin =
            |    $TQ
            |   echo hello | wc -c
            |   cat hay_stack.txt | grep needle
            |   {myList.joinToString("|")}
            |    $TQ
            |        .trimMargin()
            |"""
            .trimMargin(),
    )
        .isEqualTo(
            """
            |val margin =
            |    $TQ
            |    |   echo hello | wc -c
            |    |   cat hay_stack.txt | grep needle
            |    |   {myList.joinToString("|")}
            |    $TQ
            |        .trimMargin()
            |"""
                .trimMargin(),
        )
  }

  @Test
  fun `comments between multiline string and trimMargin are preserved`() = assertFormatted(
      """
          |val bar =
          |    $TQ
          |    |content
          |    $TQ
          |        // This comment should be preserved
          |        .trimMargin()
          |"""
          .trimMargin(),
  )

  @Test
  fun `comments between multiline string and trimIndent are preserved`() = assertFormatted(
      """
          |val bar =
          |    $TQ
          |    content
          |    $TQ
          |        // This comment should be preserved
          |        .trimIndent()
          |"""
          .trimMargin(),
  )

  @Test
  fun `multiple comments between multiline string and trimMargin are preserved`() = assertFormatted(
      """
          |val bar =
          |    $TQ
          |    |content
          |    $TQ
          |        // First comment
          |        // Second comment
          |        .trimMargin()
          |"""
          .trimMargin(),
  )

  @Test
  fun `comment between multiline string and trimMargin is not deleted in string concatenation`() {
    val before =
        """
        |val bar =
        |    $TQ
        |    |    a
        |    $TQ +
        |        $TQ
        |        |    b
        |        $TQ
        |      // This comment will not be deleted
        |.trimMargin()
        |"""
            .trimMargin()
    val after =
        """
        |val bar =
        |    $TQ
        |    |    a
        |    $TQ +
        |        $TQ
        |        |    b
        |        $TQ
        |            // This comment will not be deleted
        |            .trimMargin()
        |"""
            .trimMargin()

    assertThatFormatting(before).isEqualTo(after)
  }

  @Test
  fun `handles multiline trim formatting with template expressions`() {
    assertFormatted(
        """
        |val margin1 =
        |    ${TQ}my math = ${'$'}{ "}" + (1 + 2).toString() }
        |       | checks
        |    |    out
        |        |$TQ
        |        .trimMargin()
        |
        |val margin2 =
        |    ${"$$"}${TQ}my math = ${"$$"}{ "}" + (1 + 2).toString() }
        |       | checks
        |    |    out
        |        |$TQ
        |        .trimMargin()
        |"""
            .trimMargin(),
    )

    assertThatFormatting(
        """
            |val margin1 =
            |    $TQ
            |not_a_var$
            |$1
            |$\{
            |$}
            |$TQ
            |        .trimIndent()
            |
            |val margin2 =
            |    ${"$$"}$TQ
            |not_a_var$$
            |$$1
            |$$\{
            |$$}
            |        |$TQ
            |        .trimMargin()
            |"""
            .trimMargin(),
    )
        .isEqualTo(
            """
            |val margin1 =
            |    $TQ
            |    not_a_var$
            |    $1
            |    $\{
            |    $}
            |    $TQ
            |        .trimIndent()
            |
            |val margin2 =
            |    ${"$$"}$TQ
            |    |not_a_var$$
            |    |$$1
            |    |$$\{
            |    |$$}
            |    |$TQ
            |        .trimMargin()
            |"""
                .trimMargin(),
        )
  }

  @Test
  fun `handles multi-dollar string`() = assertThatFormatting(
      """
              |val margin =
              |    ${"$$"}$TQ
              |     |{
              |  "${'$'}test": "string"
              |   |}
              |
              |     $TQ.trimMargin()
              |"""
          .trimMargin(),
  )
      .isEqualTo(
          """
              |val margin =
              |    ${"$$"}$TQ
              |    |{
              |    |  "${'$'}test": "string"
              |    |}
              |    |$TQ
              |        .trimMargin()
              |"""
              .trimMargin(),
      )

  @Test
  fun `Trailing spaces in a comment are not preserved`() {
    val before =
        listOf("// trailing spaces in a comment are not preserved       ", "").joinToString("\n")
    val after = listOf("// trailing spaces in a comment are not preserved", "").joinToString("\n")
    assertThatFormatting(before).allowTrailingWhitespace().isEqualTo(after)
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
      Formatter.format(code)
      fail()
    } catch (e: ParseError) {
      assertThat(e.errorDescription).contains("\\u0003")
      assertThat(e.lineColumn.line).isEqualTo(1)
      assertThat(e.lineColumn.column).isEqualTo(5)
    }
  }

  @Test
  fun `handle some basic generics scenarios`() = assertFormatted(
      """
      |fun <T> doIt(a: List<T>): List<Int>? {
      |  val b: List<Int> = convert<Int>(listOf(5, 4))
      |  return b
      |}
      |
      |class Foo<T>
      |"""
          .trimMargin(),
  )

  @Test
  fun `handle for loops`() = assertFormatted(
      """
      |fun f(a: List<Int>) {
      |  for (i in a.indices) {
      |    println(i)
      |  }
      |}
      |"""
          .trimMargin(),
  )

  @Test
  fun `handle for loops with long dot chains`() = assertFormatted(
      """
      |///////////////////////////////////
      |fun f(a: Node<Int>) {
      |  for (child in node.next.data()) {
      |    println(child)
      |  }
      |  for (child in
      |      node.next.next.data()) {
      |    println(child)
      |  }
      |  for (child in
      |      node.next.next.next.next
      |          .data()) {
      |    println(child)
      |  }
      |}
      |"""
          .trimMargin(),
      deduceMaxWidth = true,
  )

  @Test
  fun `when two lambdas following a call, indent the lambda properly`() = assertFormatted(
      """
      |////////////////////////////
      |fun f() {
      |  doIt()
      |      .apply {
      |        number =
      |            computeNumber1()
      |      }
      |      .apply {
      |        number = 2 * number
      |      }
      |}
      |"""
          .trimMargin(),
      deduceMaxWidth = true,
  )

  @Test
  fun `when two lambdas following a field, indent the lambda properly`() = assertFormatted(
      """
      |////////////////////////////
      |fun f() {
      |  field
      |      .apply {
      |        number =
      |            computeNumber1()
      |      }
      |      .apply {
      |        number = 2 * number
      |      }
      |}
      |"""
          .trimMargin(),
      deduceMaxWidth = true,
  )

  @Test
  fun `break after 'four' (even though it's 4 chars long) because there's a lambda afterwards`() = assertFormatted(
      """
      |fun f() {
      |  four
      |      .let {
      |        //
      |        foo()
      |      }
      |      .methodCall()
      |}
      |"""
          .trimMargin(),
  )

  @Test
  fun `handle where formatting, fits into single line`() = assertFormatted(
      """
      |class Foo<T>(n: Int) where T : Bar, T : FooBar
      |
      |fun <T> foo(n: Int) where T : Bar, T : FooBar {}
      |"""
          .trimMargin(),
  )

  @Test
  fun `handle where formatting, full expression breaks into single line`() = assertFormatted(
      """
      |////////////////////////////////
      |class Foo<T>(n: Int)
      |    where T : Bar, T : FooBar
      |
      |fun <T> foo(n: Int)
      |    where T : Bar, T : FooBar {}
      |"""
          .trimMargin(),
      deduceMaxWidth = true,
  )

  @Test
  fun `handle where formatting, everything breaks into multiple lines`() = assertFormatted(
      """
      |////////////////////////////
      |class Foo<T>(n: Int)
      |    where
      |        T : Bar,
      |        T : FooBar
      |
      |fun <T> foo(n: Int)
      |    where
      |        T : Bar,
      |        T : FooBar {}
      |"""
          .trimMargin(),
      deduceMaxWidth = true,
  )

  @Test
  fun `keep last expression in qualified indented`() = assertFormatted(
      """
      |////////////////////////////
      |fun f() {
      |  Stuff()
      |      .doIt(
      |          Foo.doIt()
      |              .doThat())
      |      .doIt(
      |          Foo.doIt()
      |              .doThat())
      |}
      |"""
          .trimMargin(),
      deduceMaxWidth = true,
  )

  @Test
  fun `properly place lambda arguments into blocks`() = assertFormatted(
      """
      |///////////////////////
      |fun f() {
      |  foo {
      |    red.orange.yellow()
      |  }
      |
      |  foo.bar {
      |    red.orange.yellow()
      |  }
      |}
      |"""
          .trimMargin(),
      deduceMaxWidth = true,
  )

  @Test
  fun `properly handle one statement lambda with comment`() = assertFormatted(
      """
      |////////////////////////
      |fun f() {
      |  foo {
      |    // this is a comment
      |    red.orange.yellow()
      |  }
      |  foo {
      |    /* this is also a comment */
      |    red.orange.yellow()
      |  }
      |  foo.bar {
      |    // this is a comment
      |    red.orange.yellow()
      |  }
      |  foo.bar() {
      |    // this is a comment
      |    red.orange.yellow()
      |  }
      |  foo.bar {
      |    /* this is also a comment */
      |    red.orange.yellow()
      |  }
      |}
      |"""
          .trimMargin(),
      deduceMaxWidth = true,
  )

  @Test
  fun `properly handle one statement lambda with comment after body statements`() = assertFormatted(
      """
      |////////////////////////
      |fun f() {
      |  foo {
      |    red.orange.yellow()
      |    // this is a comment
      |  }
      |  foo {
      |    red.orange.yellow()
      |    /* this is also a comment */
      |  }
      |  foo.bar {
      |    red.orange.yellow()
      |    // this is a comment
      |  }
      |  foo.bar() {
      |    red.orange.yellow()
      |    // this is a comment
      |  }
      |  foo.bar {
      |    red.orange.yellow()
      |    /* this is also a comment */
      |  }
      |  red.orange.yellow()
      |  // this is a comment
      |}
      |"""
          .trimMargin(),
      deduceMaxWidth = true,
  )

  @Test
  fun `try to keep expression in the same line until the first lambda`() = assertFormatted(
      """
      |/////////////////////////
      |fun f() {
      |  foo.bar.bar?.let {
      |    a()
      |  }
      |  foo.bar.bar?.let {
      |    action()
      |    action2()
      |  }
      |  foo.bar.bar.bar.bar
      |      ?.let { a() }
      |  foo.bar.bar.bar.bar
      |      ?.let {
      |        action()
      |        action2()
      |      }
      |}
      |"""
          .trimMargin(),
      deduceMaxWidth = true,
  )

  @Test
  fun `different indentation in chained calls`() = assertFormatted(
      """
      |///////////////////////////
      |fun f() {
      |  fooDdoIt(
      |      foo1, foo2, foo3)
      |  foo.doIt(
      |      foo1, foo2, foo3)
      |  foo.doIt(
      |          foo1, foo2, foo3)
      |      .doThat()
      |}
      |"""
          .trimMargin(),
      deduceMaxWidth = true,
  )

  @Test
  fun `always add a conditional break for a lambda which is not last`() = assertFormatted(
      """
      |////////////////////
      |fun f() {
      |  foofoo
      |      .doIt {
      |        doStuff()
      |      }
      |      .doIt {
      |        doStuff()
      |      }
      |}
      |"""
          .trimMargin(),
      deduceMaxWidth = true,
  )

  @Test
  fun `keep parenthesis and braces together when there's only one lambda argument`() = assertFormatted(
      """
      |fun f() {
      |  doIt({})
      |  doIt({ it + it })
      |  doIt({
      |    val a = it
      |    a + a
      |  })
      |  doIt(functor = { it + it })
      |  doIt(
      |      functor = {
      |        val a = it
      |        a + a
      |      })
      |}
      |"""
          .trimMargin(),
  )

  @Test
  fun `Qualified type`() = assertFormatted(
      """
      |fun f() {
      |  var plusFour: Indent.Const
      |  var x: Map.Entry<String, Integer>
      |  var x: List<String>.Iterator
      |}
      |"""
          .trimMargin(),
  )

  @Test
  fun `handle destructuring declaration in for loop`() = assertFormatted(
      """
      |fun f(a: List<Pair<Int, Int>>) {
      |  for ((x, y: Int) in a) {}
      |}
      |"""
          .trimMargin(),
  )

  @Test
  fun `handle function references`() = assertFormatted(
      """
      |////////////////////////////////
      |fun f(a: List<Int>) {
      |  a.forEach(::println)
      |  a.map(Int::toString)
      |  a.map(String?::isNullOrEmpty)
      |  a.map(
      |      SuperLongClassName?::
      |          functionName)
      |  val f =
      |      SuperLongClassName::
      |          functionName
      |  val g =
      |      invoke(a, b)::functionName
      |  val h =
      |      invoke(a, b, c)::
      |          functionName
      |}
      |"""
          .trimMargin(),
      deduceMaxWidth = true,
  )

  @Test
  fun `handle escaped identifier`() = assertFormatted(
      """
      |import foo as `foo foo`
      |import org.mockito.Mockito.`when` as `yay yay`
      |
      |fun `spaces in functions`() {
      |  val `when` = NEVER
      |  val (`do not`, `ever write`) = SERIOUSLY
      |  val `a a`: Int
      |  `yay yay`(`foo foo`)
      |}
      |
      |class `more spaces`
      |"""
          .trimMargin(),
  )

  @Test
  fun `handle annotations with arguments`() = assertFormatted(
      """
      |@Px fun f(): Int = 5
      |
      |@Dimenstion(unit = DP) fun g(): Int = 5
      |
      |@RunWith(MagicRunner::class)
      |@Px
      |class Test {
      |  //
      |}
      |"""
          .trimMargin(),
  )

  @Test
  fun `no newlines after annotations if entire expr fits in one line`() = assertFormatted(
      """
      |///////////////////////////////////////////////
      |@Px @Px fun f(): Int = 5
      |
      |@Px
      |@Px
      |@Px
      |@Px
      |@Px
      |@Px
      |@Px
      |@Px
      |fun f(): Int = 5
      |
      |@Px
      |@Px
      |fun f(): Int {
      |  return 5
      |}
      |
      |@Dimenstion(unit = DP) @Px fun g(): Int = 5
      |
      |@Dimenstion(unit = DP)
      |@Px
      |fun g(): Int {
      |  return 5
      |}
      |
      |@RunWith @Px class Test
      |
      |@RunWith(MagicRunner::class) @Px class Test
      |
      |@RunWith @Px class Test {}
      |
      |@RunWith(MagicRunner::class) @Px class Test {}
      |
      |@RunWith(MagicRunner::class)
      |@Px
      |@Px
      |class Test {}
      |
      |@RunWith(MagicRunner::class)
      |@Px
      |class Test {
      |  //
      |}
      |
      |fun f() {
      |  if (@Stuff(Magic::class) isGood()) {
      |    println("")
      |  }
      |}
      |"""
          .trimMargin(),
      deduceMaxWidth = true,
  )

  @Test
  fun `no newlines after annotations on properties if entire expression fits in one line`() = assertFormatted(
      """
      |////////////////////////////////////////////
      |@Suppress("UnsafeCast")
      |val ClassA.methodA
      |  get() = foo as Bar
      |"""
          .trimMargin(),
      deduceMaxWidth = true,
  )

  @Test
  fun `when annotations cause line breaks, and constant has no type dont break before value`() = assertFormatted(
      """
      |//////////////////////////////////////////////////////////
      |object Foo {
      |  @LongLongLongLongAnnotation
      |  @LongLongLongLongLongAnnotation
      |  private val ROW_HEIGHT = 72
      |}
      |"""
          .trimMargin(),
      deduceMaxWidth = true,
  )

  @Test
  fun `annotations in literal function types`() = assertFormatted(
      """
      |val callback: (@Anno List<@JvmSuppressWildcards String>) -> Unit = foo
      |"""
          .trimMargin(),
  )

  @Test
  fun `annotations on type parameters`() = assertFormatted(
      """
      |class Foo<@Anno out @Anno T, @Anno in @Anno U> {
      |  inline fun <@Anno reified @Anno X, @Anno reified @Anno Y> bar() {}
      |}
      |"""
          .trimMargin(),
  )

  @Test
  fun `annotations on type constraints`() = assertFormatted(
      """
      |class Foo<T : @Anno Kip, U> where U : @Anno Kip, U : @Anno Qux {
      |  fun <T : @Anno Kip, U> bar() where U : @Anno Kip, U : @Anno Qux {}
      |}
      |"""
          .trimMargin(),
  )

  @Test
  fun `annotations on type arguments`() = assertFormatted(
      """
      |fun foo(x: Foo<in @Anno Int>) {}
      |"""
          .trimMargin(),
  )

  @Test
  fun `annotations on destructuring declaration elements`() = assertFormatted(
      """
      |val x = { (@Anno x, @Anno y) -> x }
      |"""
          .trimMargin(),
  )

  @Test
  fun `annotations on exceptions`() = assertFormatted(
      """
      |fun doIt() {
      |  try {
      |    doItAgain()
      |  } catch (@Nullable e: Exception) {
      |    //
      |  } catch (@Suppress("GeneralException") e: Exception) {}
      |}
      |"""
          .trimMargin(),
  )

  @Test
  fun `annotations on return statements`() = assertFormatted(
      """
      |fun foo(): Map<String, Any> {
      |  @Suppress("AsCollectionCall")
      |  return map.asMap()
      |}
      |"""
          .trimMargin(),
  )

  @Test
  fun `Unary prefix expressions`() = assertFormatted(
      """
      |fun f() {
      |  !a
      |  -4
      |  val x = -foo()
      |  +4
      |  ++a
      |  --a
      |
      |  + +a
      |  +-a
      |  +!a
      |  -+a
      |  - -a
      |  -!a
      |  !+a
      |  !a
      |  ! !a
      |
      |  + ++a
      |  +--a
      |  -++a
      |  - --a
      |  !++a
      |  !--a
      |}
      |"""
          .trimMargin(),
  )

  @Test
  fun `Unary postfix expressions`() = assertFormatted(
      """
      |fun f() {
      |  a!!
      |  a++
      |  a--
      |
      |  a--!!
      |  a++!!
      |
      |  a!! !!
      |}
      |"""
          .trimMargin(),
  )

  @Test
  fun `handle wildcard generics`() = assertFormatted(
      """
      |fun f() {
      |  val l: List<*>
      |  val p: Pair<*, *>
      |}
      |"""
          .trimMargin(),
  )

  @Test
  fun `handle intersection generics`() = assertFormatted(
      """
      |fun f() {
      |  val l: Decl<A & B & C>
      |  val p = Ctor<A & B & C, T & Y & Z>
      |}
      |"""
          .trimMargin(),
  )

  @Test
  fun `handle covariant and contravariant type arguments`() = assertFormatted(
      """
      |val p: Pair<in T, out S>
      |"""
          .trimMargin(),
  )

  @Test
  fun `handle covariant and contravariant type parameters`() = assertFormatted(
      """
      |class Foo<in T, out S>
      |"""
          .trimMargin(),
  )

  @Test
  fun `handle bounds for type parameters`() = assertFormatted(
      """
      |class Foo<in T : List<*>, out S : Any?>
      |"""
          .trimMargin(),
  )

  @Test
  fun `handle compound generic bounds on classes`() = assertFormatted(
      """
      |class Foo<T>(n: Int) where T : Bar, T : FooBar {}
      |"""
          .trimMargin(),
  )

  @Test
  fun `handle compound generic bounds on functions`() = assertFormatted(
      """
      |fun <T> foo(n: Int) where T : Bar, T : FooBar {}
      |"""
          .trimMargin(),
  )

  @Test
  fun `handle compound generic bounds on properties`() = assertFormatted(
      """
      |val <T> List<T>.twiceSum: Int where T : Int
      |  get() {
      |    return 2 * sum()
      |  }
      |"""
          .trimMargin(),
  )

  @Test
  fun `handle compound generic bounds on class with delegate`() = assertFormatted(
      """
      |class Foo<T>() : Bar by bar
      |    where T : Qux
      |"""
          .trimMargin(),
  )

  @Test
  fun `explicit type on property getter`() = assertFormatted(
      """
      |class Foo {
      |  val silly: Int
      |    get(): Int = 1
      |}
      |"""
          .trimMargin(),
  )

  @Test
  fun `explicit backing field`() = assertFormatted(
      """
      |class Foo {
      |  val city: StateFlow<String>
      |    field = MutableStateFlow("")
      |
      |  var numbers: List<Int>
      |    field: MutableList<Int> = mutableListOf()
      |}
      |"""
          .trimMargin(),
  )

  @Test
  fun `explicit backing field without type`() = assertFormatted(
      """
      |class Foo {
      |  val count: Int
      |    field = 0
      |}
      |"""
          .trimMargin(),
  )

  @Test
  fun `explicit backing field with private set accessor`() = assertFormatted(
      """
      |class Foo {
      |  var count: Int
      |    field = 0
      |    private set
      |}
      |"""
          .trimMargin(),
  )

  @Test
  fun `handle method calls with lambda arg only`() = assertFormatted(
      """
      |fun f() {
      |  val a = g { 1 + 1 }
      |}
      |"""
          .trimMargin(),
  )

  @Test
  fun `handle method calls value args and a lambda arg`() = assertFormatted(
      """
      |fun f() {
      |  val a = g(1, 2) { 1 + 1 }
      |}
      |"""
          .trimMargin(),
  )

  @Test
  fun `handle top level constants`() = assertFormatted(
      """
      |/////////////////////////////
      |val a = 5
      |
      |const val b = "a"
      |
      |val a = 5
      |"""
          .trimMargin(),
      deduceMaxWidth = true,
  )

  @Test
  fun `handle lambda arg with named arguments`() = assertFormatted(
      """
      |fun f() {
      |  val b = { x: Int, y: Int -> x + y }
      |}
      |"""
          .trimMargin(),
  )

  @Test
  fun `avoid newline before lambda argument if it is named`() = assertFormatted(
      """
      |private fun f(items: List<Int>) {
      |  doSomethingCool(
      |      items,
      |      lambdaArgument = {
      |        step1()
      |        step2()
      |      }) {
      |        it.doIt()
      |      }
      |}
      |"""
          .trimMargin(),
  )

  @Test
  fun `handle labeled this pointer`() = assertFormatted(
      """
      |class Foo {
      |  fun f() {
      |    g { println(this@Foo) }
      |  }
      |}
      |"""
          .trimMargin(),
  )

  @Test
  fun `handle extension and operator functions`() = assertFormatted(
      """
      |operator fun Point.component1() = x
      |"""
          .trimMargin(),
  )

  @Test
  fun `handle extension methods with very long names`() = assertFormatted(
      """
      |//////////////////////////////////////////
      |fun LongReceiverNameThatRequiresBreaking
      |    .doIt() {}
      |
      |fun LongButNotTooLong.doIt(
      |    n: Int,
      |    f: Float
      |) {}
      |"""
          .trimMargin(),
      deduceMaxWidth = true,
  )

  @Test
  fun `handle extension properties`() = assertFormatted(
      """
      |val Int.isPrime: Boolean
      |  get() = runMillerRabinPrimality(this)
      |"""
          .trimMargin(),
  )

  @Test
  fun `generic extension property`() = assertFormatted(
      """
      |val <T> List<T>.twiceSize = 2 * size()
      |"""
          .trimMargin(),
  )

  @Test
  fun `handle file annotations`() {
    assertFormatted(
        """
        |@file:JvmName("DifferentName")
        |
        |package com.somecompany.example
        |
        |import com.somecompany.example2
        |
        |class Foo {
        |  val a = example2("and 1")
        |}
        |"""
            .trimMargin(),
    )

    assertFormatted(
        """
        |@file:JvmName("DifferentName") // Comment
        |
        |package com.somecompany.example
        |
        |import com.somecompany.example2
        |
        |class Foo {
        |  val a = example2("and 1")
        |}
        |"""
            .trimMargin(),
    )

    assertFormatted(
        """
        |@file:JvmName("DifferentName")
        |
        |// Comment
        |
        |package com.somecompany.example
        |
        |import com.somecompany.example2
        |
        |class Foo {
        |  val a = example2("and 1")
        |}
        |"""
            .trimMargin(),
    )

    assertFormatted(
        """
        |@file:JvmName("DifferentName")
        |
        |// Comment
        |package com.somecompany.example
        |
        |import com.somecompany.example2
        |
        |class Foo {
        |  val a = example2("and 1")
        |}
        |"""
            .trimMargin(),
    )
  }

  @Test
  fun `handle init block`() = assertFormatted(
      """
      |class Foo {
      |  init {
      |    println("Init!")
      |  }
      |}
      |"""
          .trimMargin(),
  )

  @Test
  fun `handle interface delegation`() = assertFormatted(
      """
      |class MyList(impl: List<Int>) : Collection<Int> by impl
      |"""
          .trimMargin(),
  )

  @Test
  fun `handle property delegation`() = assertFormatted(
      """
      |val a by lazy { 1 + 1 }
      |"""
          .trimMargin(),
  )

  @Test
  fun `handle property delegation with type and breaks`() = assertFormatted(
      """
      |/////////////////////////////////
      |val importantValue: Int by lazy {
      |  1 + 1
      |}
      |
      |val importantValue: Int by lazy {
      |  val b = 1 + 1
      |  b + b
      |}
      |
      |val importantValueLonger:
      |    Int by lazy { 1 + 1 }
      |
      |val importantValue: Int by
      |    doIt(1 + 1)
      |"""
          .trimMargin(),
      deduceMaxWidth = true,
  )

  @Test
  fun `handle multi-annotations with use-site targets`() = assertFormatted(
      """
      |class Something {
      |  @field:[Inject Named("WEB_VIEW")]
      |  internal lateinit var httpClient: OkHttpClient
      |
      |  @field:[Inject Named("WEB_VIEW")]
      |  var httpClient: OkHttpClient
      |
      |  @Px
      |  @field:[Inject Named("WEB_VIEW")]
      |  var httpClient: OkHttpClient
      |}
      |"""
          .trimMargin(),
  )

  @Test
  fun `handle parameters with annoations with parameters`() = assertFormatted(
      """
      |class Something {
      |  fun doIt(@Magic(withHat = true) foo: Foo) {
      |    println(foo)
      |  }
      |}
      |"""
          .trimMargin(),
  )

  @Test
  fun `handle lambda types`() = assertFormatted(
      """
      |val listener1: (Boolean) -> Unit = { b -> !b }
      |
      |val listener2: () -> Unit = {}
      |
      |val listener3: (Int, Double) -> Int = { a, b -> a }
      |
      |val listener4: Int.(Int, Boolean) -> Unit
      |"""
          .trimMargin(),
  )

  @Test
  fun `handle unicode in string literals`() = assertFormatted(
      """
      |val a = "\uD83D\uDC4D"
      |"""
          .trimMargin(),
  )

  @Test
  fun `handle casting`() = assertFormatted(
      """
      |fun castIt(o: Object) {
      |  println(o is Double)
      |  println(o !is Double)
      |  doIt(o as Int)
      |  doIt(o as? Int)
      |}
      |"""
          .trimMargin(),
  )

  @Test
  fun `handle casting with breaks`() = assertFormatted(
      """
      |///////////////////////
      |fun castIt(
      |    something: Any
      |) {
      |  doIt(
      |      something
      |          as List<*>)
      |  doIt(
      |      something
      |          is List<*>)
      |  println(
      |      something
      |          is
      |          List<String>)
      |  doIt(
      |      something
      |          as
      |          List<String>)
      |  println(
      |      something
      |          is
      |          PairList<
      |              String,
      |              Int>)
      |  doIt(
      |      something
      |          as
      |          PairList<
      |              String,
      |              Int>)
      |  println(
      |      a is Int &&
      |          b is String)
      |  l.b?.s?.sOrNull() is
      |      SomethingLongEnough
      |}
      |
      |val a =
      |    l.sOrNull() is
      |        SomethingLongEnough
      |"""
          .trimMargin(),
      deduceMaxWidth = true,
  )

  @Test
  fun `handle collection literals in annotations`() = assertFormatted(
      """
      |@Foo(a = [1, 2])
      |fun doIt(o: Object) {
      |  //
      |}
      |"""
          .trimMargin(),
  )

  @Test
  fun `handle try, catch and finally`() = assertFormatted(
      """
      |fun foo() {
      |  try {
      |    bar()
      |  } catch (e: Exception) {
      |    throw e
      |  } finally {
      |    println("finally")
      |  }
      |}
      |"""
          .trimMargin(),
  )

  @Test
  fun `handle infix methods`() = assertFormatted(
      """
      |fun numbers() {
      |  (0 until 100).size
      |}
      |"""
          .trimMargin(),
  )

  @Test
  fun `handle while loops`() = assertFormatted(
      """
      |fun numbers() {
      |  while (1 < 2) {
      |    println("Everything is okay")
      |  }
      |}
      |"""
          .trimMargin(),
  )

  @Test
  fun `handle do while loops`() = assertFormatted(
      """
      |fun numbers() {
      |  do {
      |    println("Everything is okay")
      |  } while (1 < 2)
      |
      |  do while (1 < 2)
      |}
      |"""
          .trimMargin(),
  )

  @Test
  fun `handle break and continue`() = assertFormatted(
      """
      |fun numbers() {
      |  while (1 < 2) {
      |    if (true) {
      |      break
      |    }
      |    if (false) {
      |      continue
      |    }
      |  }
      |}
      |"""
          .trimMargin(),
  )

  @Test
  fun `handle all kinds of labels and jumps`() = assertFormatted(
      """
      |fun f(a: List<Int>) {
      |  a.map {
      |    myloop@ for (i in a) {
      |      if (true) {
      |        break@myloop
      |      } else if (false) {
      |        continue@myloop
      |      } else {
      |        a.map `inner map`@{
      |          return@`inner map`
      |        }
      |      }
      |    }
      |    return@map 2 * it
      |  }
      |}
      |"""
          .trimMargin(),
  )

  @Test
  fun `don't crash on top level statements with semicolons`() {
    val code =
        """
        |val x = { 0 };
        |
        |foo({ 0 });
        |
        |foo { 0 };
        |
        |val fill = 0;
        |"""
            .trimMargin()
    val expected =
        """
        |val x = { 0 }
        |
        |foo({ 0 })
        |
        |foo { 0 }
        |
        |val fill = 0
        |"""
            .trimMargin()
    assertThatFormatting(code).isEqualTo(expected)
  }

  @Test
  fun `preserve semicolons in enums`() {
    val code =
        """
        |enum class SemiColonIsNotRequired {
        |  TRUE, FALSE;
        |}
        |
        |enum class SemiColonIsRequired {
        |  ONE, TWO;
        |
        |  fun isOne(): Boolean = this == ONE
        |}
        |"""
            .trimMargin()
    val expected =
        """
        |enum class SemiColonIsNotRequired {
        |  TRUE,
        |  FALSE
        |}
        |
        |enum class SemiColonIsRequired {
        |  ONE,
        |  TWO;
        |
        |  fun isOne(): Boolean = this == ONE
        |}
        |"""
            .trimMargin()
    assertThatFormatting(code).isEqualTo(expected)
  }

  @Test
  fun `preserve semicolons in comments and strings`() {
    val code =
        """
        |fun f() {
        |  val x = ";"
        |  val x = $TQ  don't touch ; in raw strings $TQ
        |}
        |
        |// Don't touch ; inside comments.
        |
        |/** Don't touch ; inside comments. */
        |"""
            .trimMargin()
    val expected =
        """
        |fun f() {
        |  val x = ";"
        |  val x = $TQ  don't touch ; in raw strings $TQ
        |}
        |
        |// Don't touch ; inside comments.
        |
        |/** Don't touch ; inside comments. */
        |"""
            .trimMargin()
    assertThatFormatting(code).isEqualTo(expected)
  }

  @Test
  fun `preserve semicolons in empty if-s and while-s`() {
    val code =
        """
        |fun f() {
        |  while (true);
        |  while (true) /** a */ ;
        |
        |  if (true);
        |  if (true) /** a */ ;
        |
        |  if (true)
        |    else
        |  ;
        |}
        |"""
            .trimMargin()
    val expected =
        """
        |fun f() {
        |  while (true) ;
        |  while (true)
        |  /** a */
        |  ;
        |
        |  if (true) ;
        |  if (true)
        |  /** a */
        |   ;
        |
        |  if (true)  else ;
        |}
        |"""
            .trimMargin()
    assertThatFormatting(code).isEqualTo(expected)
  }

  @Test
  fun `preserve semicolons between calls and dead lambdas`() {
    val code =
        """
        |fun f() {
        |  foo(0); { dead -> lambda }
        |
        |  foo(0) ; { dead -> lambda }
        |
        |  foo(0) /** a */ ; /** b */ { dead -> lambda }
        |
        |  foo(0) { trailing -> lambda }; { dead -> lambda }
        |
        |  foo { trailing -> lambda }; { dead -> lambda }
        |
        |  val x = foo(); { dead -> lambda }
        |
        |  val x = bar() && foo(); { dead -> lambda }
        |
        |  // `z` has a property and a method both named `bar`
        |  val x = z.bar; { dead -> lambda }
        |
        |  // `this` has a property and a method both named `bar`
        |  val x = bar; { dead -> lambda }
        |
        |  // Literally any callable expression is dangerous
        |  val x = (if (cond) x::foo else x::bar); { dead -> lambda }
        |
        |  funcCall(); { dead -> lambda }.withChained(call)
        |}
        |"""
            .trimMargin()
    val expected =
        """
        |fun f() {
        |  foo(0);
        |  { dead -> lambda }
        |
        |  foo(0);
        |  { dead -> lambda }
        |
        |  foo(0)
        |  /** a */
        |  ;
        |  /** b */
        |  { dead -> lambda }
        |
        |  foo(0) { trailing -> lambda };
        |  { dead -> lambda }
        |
        |  foo { trailing -> lambda };
        |  { dead -> lambda }
        |
        |  val x = foo();
        |  { dead -> lambda }
        |
        |  val x = bar() && foo();
        |  { dead -> lambda }
        |
        |  // `z` has a property and a method both named `bar`
        |  val x = z.bar;
        |  { dead -> lambda }
        |
        |  // `this` has a property and a method both named `bar`
        |  val x = bar;
        |  { dead -> lambda }
        |
        |  // Literally any callable expression is dangerous
        |  val x = (if (cond) x::foo else x::bar);
        |  { dead -> lambda }
        |
        |  funcCall();
        |  { dead -> lambda }.withChained(call)
        |}
        |"""
            .trimMargin()
    assertThatFormatting(code).isEqualTo(expected)
  }

  @Test
  fun `drop redundant semicolons`() {
    val code =
        """
        |package org.examples;
        |import org.examples.wow.MuchWow;
        |import org.examples.wow.ManyAmaze
        |
        |typealias Int2 = Int;
        |
        |fun f() {
        |  val a = 3;
        |  val x = 5 ; val y = ManyAmaze();
        |  myThingMap.forEach { val (key, value) = it; println("mapped ${"$"}MuchWow") }
        |  when {
        |    true -> "1"; false -> "0"
        |  }
        |  someLongVariableName.let {
        |    someReallyLongFunctionNameThatMakesThisNotFitInOneLineWithTheAboveVariable();
        |  }
        |  if (cond) ; else 6
        |} ;
        |
        |"""
            .trimMargin()
    val expected =
        """
        |package org.examples
        |
        |import org.examples.wow.ManyAmaze
        |import org.examples.wow.MuchWow
        |
        |typealias Int2 = Int
        |
        |fun f() {
        |  val a = 3
        |  val x = 5
        |  val y = ManyAmaze()
        |  myThingMap.forEach {
        |    val (key, value) = it
        |    println("mapped ${"$"}MuchWow")
        |  }
        |  when {
        |    true -> "1"
        |    false -> "0"
        |  }
        |  someLongVariableName.let {
        |    someReallyLongFunctionNameThatMakesThisNotFitInOneLineWithTheAboveVariable()
        |  }
        |  if (cond)  else 6
        |}
        |"""
            .trimMargin()
    assertThatFormatting(code).isEqualTo(expected)
  }

  @Test
  fun `pretty-print after dropping redundant semicolons`() {
    val code =
        """
        |fun f() {
        |  val veryLongName = 5;
        |}
        |"""
            .trimMargin()
    val expected =
        """
        |fun f() {
        |  val veryLongName = 5
        |}
        |"""
            .trimMargin()
    assertThatFormatting(code)
        .withOptions(defaultTestFormattingOptions.copy(maxWidth = 22))
        .isEqualTo(expected)
  }

  @Test
  fun `handle no parenthesis in lambda calls`() = assertFormatted(
      """
      |fun f() {
      |  a { println("a") }
      |}
      |"""
          .trimMargin(),
  )

  @Test
  fun `handle multi statement lambdas`() = assertFormatted(
      """
      |fun f() {
      |  a {
      |    println("a")
      |    println("b")
      |  }
      |}
      |"""
          .trimMargin(),
  )

  @Test
  fun `handle multi line one statement lambda`() = assertFormatted(
      """
      |/////////////////////////
      |fun f() {
      |  a {
      |    println(foo.bar.boom)
      |  }
      |}
      |"""
          .trimMargin(),
      deduceMaxWidth = true,
  )

  @Test
  fun `statements are wrapped in blocks`() = assertFormatted(
      """
      |fun f() {
      |  builder.block {
      |    getArgumentName().accept
      |    return
      |  }
      |}
      |"""
          .trimMargin(),
  )

  @Test
  fun `properly break fully qualified nested user types`() = assertFormatted(
      """
      |///////////////////////////////////////////////////////
      |val complicated:
      |    com.example.interesting.SomeType<
      |        com.example.interesting.SomeType<Int, Nothing>,
      |        com.example.interesting.SomeType<
      |            com.example.interesting.SomeType<
      |                Int, Nothing>,
      |            Nothing>> =
      |    DUMMY
      |"""
          .trimMargin(),
      deduceMaxWidth = true,
  )

  @Test
  fun `handle multi-line lambdas within lambdas and calling chains`() = assertFormatted(
      """
      |fun f() {
      |  builder.block(ZERO) {
      |    builder.token("when")
      |    expression1.let { subjectExp ->
      |      builder.token(")")
      |      return
      |    }
      |  }
      |  builder.block(ZERO) {
      |    expression2.subjectExpression.let { subjectExp ->
      |      builder.token(")")
      |      return
      |    }
      |  }
      |  builder.block(ZERO) {
      |    expression2.subjectExpression
      |        .let { subjectExp ->
      |          builder.token(")")
      |          return
      |        }
      |        .sum
      |  }
      |}
      |"""
          .trimMargin(),
  )

  @Test
  fun `handle multi line lambdas with explicit args`() = assertFormatted(
      """
      |////////////////////
      |fun f() {
      |  a { (x, y) ->
      |    x + y
      |  }
      |}
      |"""
          .trimMargin(),
      deduceMaxWidth = true,
  )

  @Test
  fun `handle lambda with destructuring and type`() = assertFormatted(
      """
      |fun f() {
      |  g { (a, b): List<Int> -> a }
      |  g { (a, b): List<Int>, (c, d): List<Int> -> a }
      |}
      |"""
          .trimMargin(),
  )

  @Test
  fun `handle parenthesis in lambda calls for now`() = assertFormatted(
      """
      |fun f() {
      |  a() { println("a") }
      |}
      |"""
          .trimMargin(),
  )

  @Test
  fun `handle chaining of calls with lambdas`() = assertFormatted(
      """
      |fun f() {
      |  bobby
      |      .map { x -> x * x }
      |      .map { x -> x * x }
      |      ?.map { x ->
      |        val y = x * x
      |        y
      |      }
      |      .sum
      |}
      |"""
          .trimMargin(),
  )

  @Test
  fun `handle break of lambda args per line with indentation`() = assertFormatted(
      """
      |///////////
      |fun f() {
      |  a() {
      |      arg1,
      |      arg2,
      |      x ->
      |    doIt()
      |    doIt()
      |  }
      |  a() {
      |      arg1,
      |      arg2,
      |      arg3
      |    ->
      |    doIt()
      |    doIt()
      |  }
      |}
      |"""
          .trimMargin(),
      deduceMaxWidth = true,
  )

  @Test
  fun `handle trailing comma in lambda`() = assertFormatted(
      """
      |///////////
      |fun f() {
      |  a() {
      |      arg1,
      |      arg2,
      |      x,
      |    ->
      |    doIt()
      |    doIt()
      |  }
      |}
      |"""
          .trimMargin(),
      deduceMaxWidth = true,
  )

  @Test
  fun `break before Elvis operator`() = assertFormatted(
      """
      |//////////////////////////////////////////////////
      |fun f() {
      |  someObject
      |      .someMethodReturningCollection()
      |      .map { it.someProperty }
      |      .find { it.contains(someSearchValue) }
      |      ?: someDefaultValue
      |}
      |"""
          .trimMargin(),
      deduceMaxWidth = true,
  )

  @Test
  fun `chain of Elvis operator`() = assertFormatted(
      """
      |///////////////////////////
      |fun f() {
      |  return option1()
      |      ?: option2()
      |      ?: option3()
      |      ?: option4()
      |      ?: option5()
      |}
      |"""
          .trimMargin(),
      deduceMaxWidth = true,
  )

  @Test
  fun `Elvis operator mixed with plus operator breaking on plus`() = assertFormatted(
      """
      |////////////////////////
      |fun f() {
      |  return option1()
      |      ?: option2() +
      |          option3()
      |      ?: option4() +
      |          option5()
      |}
      |"""
          .trimMargin(),
      deduceMaxWidth = true,
  )

  @Test
  fun `Elvis operator mixed with plus operator breaking on elvis`() = assertFormatted(
      """
      |/////////////////////////////////
      |fun f() {
      |  return option1()
      |      ?: option2() + option3()
      |      ?: option4() + option5()
      |}
      |"""
          .trimMargin(),
      deduceMaxWidth = true,
  )

  @Test
  fun `handle comments in the middle of calling chain`() = assertFormatted(
      """
      |///////////////////////////
      |fun f() {
      |  someObject
      |      .letsDoIt()
      |      // this is a comment
      |      .doItOnce()
      |      // this is a comment
      |      .doItTwice()
      |}
      |"""
          .trimMargin(),
      deduceMaxWidth = true,
  )

  @Test
  fun `handle reified types`() = assertFormatted(
      """
      |inline fun <reified T> foo(t: T) {
      |  println(t)
      |}
      |
      |inline fun <reified in T> foo2(t: T) {
      |  println(t)
      |}
      |"""
          .trimMargin(),
  )

  @Test
  fun `handle suspended types`() = assertFormatted(
      """
      |private val reader: suspend (Key) -> Output?
      |
      |private val delete: (suspend (Key) -> Unit)? = null
      |
      |inline fun <R> foo(noinline block: suspend () -> R): suspend () -> R
      |
      |inline fun <R> bar(noinline block: (suspend () -> R)?): (suspend () -> R)?
      |"""
          .trimMargin(),
  )

  @Test
  fun `handle simple enum classes`() = assertFormatted(
      """
      |enum class BetterBoolean {
      |  TRUE,
      |  FALSE,
      |  FILE_NOT_FOUND,
      |}
      |"""
          .trimMargin(),
  )

  @Test
  fun `handle enum class with functions`() = assertFormatted(
      """
      |enum class BetterBoolean {
      |  TRUE,
      |  FALSE,
      |  FILE_NOT_FOUND;
      |
      |  fun isGood(): Boolean {
      |    return true
      |  }
      |}
      |"""
          .trimMargin(),
  )

  @Test
  fun `handle enum with annotations`() = assertFormatted(
      """
      |enum class BetterBoolean {
      |  @True TRUE,
      |  @False @WhatIsTruth FALSE,
      |}
      |"""
          .trimMargin(),
  )

  @Test
  fun `handle enum constructor calls`() = assertFormatted(
      """
      |enum class BetterBoolean(val name: String, val value: Boolean = true) {
      |  TRUE("true"),
      |  FALSE("false", false),
      |}
      |"""
          .trimMargin(),
  )

  @Test
  fun `handle enum entries with body`() = assertFormatted(
      """
      |enum class Animal(canWalk: Boolean = true) {
      |  DOG {
      |    fun speak() = "woof"
      |  },
      |  FISH(false) {},
      |}
      |"""
          .trimMargin(),
  )

  @Test
  fun `handle empty enum`() = assertFormatted(
      """
      |enum class YTho {}
      |"""
          .trimMargin(),
  )

  @Test
  fun `expect enum class`() = assertFormatted(
      """
      |expect enum class ExpectedEnum
      |"""
          .trimMargin(),
  )

  @Test
  fun `enum without trailing comma`() = assertFormatted(
      """
      |enum class Highlander {
      |  ONE
      |}
      |"""
          .trimMargin(),
  )

  @Test
  fun `enum comma and semicolon`() {
    assertThatFormatting(
        """
        |enum class Highlander {
        |  ONE,;
        |}
        |"""
            .trimMargin(),
    )
        .isEqualTo(
            """
            |enum class Highlander {
            |  ONE,
            |}
            |"""
                .trimMargin(),
        )
  }

  @Test
  fun `empty enum with semicolons`() {
    assertThatFormatting(
        """
        |enum class Empty {
        |  ;
        |}
        |"""
            .trimMargin(),
    )
        .isEqualTo(
            """
            |enum class Empty {}
            |"""
                .trimMargin(),
        )

    assertThatFormatting(
        """
        |enum class Empty {
        |  ;
        |  ;
        |  ;
        |}
        |"""
            .trimMargin(),
    )
        .isEqualTo(
            """
            |enum class Empty {}
            |"""
                .trimMargin(),
        )
  }

  @Test
  fun `semicolon is placed on next line when there's a trailing comma in an enum declaration`() = assertFormatted(
      """
      |enum class Highlander {
      |  ONE,
      |  TWO,
      |  ;
      |
      |  fun f() {}
      |}
      |"""
          .trimMargin(),
  )

  @Test
  fun `semicolon is removed from empty enum`() {
    val code =
        """
        |enum class SingleSemi {
        |  ;
        |}
        |
        |enum class MultSemi {
        |  // a
        |  ;
        |  // b
        |  ;
        |  // c
        |  ;
        |}
        |"""
            .trimMargin()
    val expected =
        """
        |enum class SingleSemi {}
        |
        |enum class MultSemi {
        |  // a
        |
        |  // b
        |
        |  // c
        |
        |}
        |"""
            .trimMargin()
    assertThatFormatting(code).isEqualTo(expected)
  }

  @Test
  fun `semicolon management in enum with no entries but other members`() {
    val code =
        """
        |enum class Empty {
        |  ;
        |
        |  fun f() {}
        |  ;
        |  fun g() {}
        |}
        |"""
            .trimMargin()
    val expected =
        """
        |enum class Empty {
        |  ;
        |
        |  fun f() {}
        |
        |  fun g() {}
        |}
        |"""
            .trimMargin()
    assertThatFormatting(code).isEqualTo(expected)
  }

  @Test
  fun `handle varargs and spread operator`() = assertFormatted(
      """
      |fun foo(vararg args: String) {
      |  foo2(*args)
      |  foo3(options = *args)
      |}
      |"""
          .trimMargin(),
  )

  @Test
  fun `handle typealias`() = assertFormatted(
      """
      |//////////////////////////////////////////////
      |private typealias TextChangedListener =
      |    (string: String) -> Unit
      |
      |typealias PairPair<X, Y> = Pair<Pair<X, Y>, X>
      |
      |class Foo
      |"""
          .trimMargin(),
      deduceMaxWidth = true,
  )

  @Test
  fun `handle the 'dynamic' type`() = assertFormatted(
      """
      |fun x(): dynamic = "x"
      |
      |val dyn: dynamic = 1
      |"""
          .trimMargin(),
  )

  @Test
  fun `handle class expression with generics`() = assertFormatted(
      """
      |fun f() {
      |  println(Array<String>::class.java)
      |}
      |"""
          .trimMargin(),
  )

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
      Formatter.format(code)
      fail()
    } catch (e: ParseError) {
      assertThat(e.lineColumn.line).isEqualTo(6)
      assertThat(e.lineColumn.column).isEqualTo(0)
      assertThat(e.errorDescription).containsMatch("Expecting an (expression|argument)")
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
      Formatter.format(code)
      fail()
    } catch (e: ParseError) {
      assertThat(e.lineColumn.line).isEqualTo(2)
      assertThat(e.lineColumn.column).isEqualTo(8)
    }
  }

  @Test
  fun `annotations on class, fun, parameters and literals`() = assertFormatted(
      """
      |@Fancy
      |class Foo {
      |  @Fancy
      |  fun baz(@Fancy foo: Int): Int {
      |    return (@Fancy 1)
      |  }
      |}
      |"""
          .trimMargin(),
  )

  @Test
  fun `annotations on function types`() = assertFormatted(
      """
      |fun foo(bar: @StringRes Int) {}
      |
      |fun foo(error: @Composable ((x) -> Unit)) {}
      |
      |fun foo(error: (@Composable (x) -> Unit)) {}
      |
      |fun foo(
      |    error:
      |        @field:[Inject Named("WEB_VIEW")]
      |        ((x) -> Unit)
      |) {}
      |
      |fun foo(
      |    error:
      |        (@field:[Inject Named("WEB_VIEW")]
      |        (x) -> Unit)
      |) {}
      |"""
          .trimMargin(),
  )

  @Test
  fun `handle annotations with use-site targets`() = assertFormatted(
      """
      |class FooTest {
      |  @get:Rule val exceptionRule: ExpectedException = ExpectedException.none()
      |
      |  @set:Magic(name = "Jane") var field: String
      |}
      |"""
          .trimMargin(),
  )

  @Test
  fun `handle annotations mixed with keywords since we cannot reorder them for now`() = assertFormatted(
      """
      |public @Magic final class Foo
      |
      |public @Magic(1) final class Foo
      |
      |@Magic(1) public final class Foo
      |"""
          .trimMargin(),
  )

  @Test
  fun `handle annotations more`() = assertFormatted(
      """
      |/////////////////////////////////////////////////
      |@Anno1
      |@Anno2(param = Param1::class)
      |@Anno3
      |@Anno4(param = Param2::class)
      |class MyClass {}
      |
      |fun f() {
      |  @Suppress("MagicNumber") add(10)
      |
      |  @Annotation // test a comment after annotations
      |  return 5
      |}
      |"""
          .trimMargin(),
      deduceMaxWidth = true,
  )

  @Test
  fun `annotated expressions`() = assertFormatted(
      """
      |////////////////////////////////////////////////
      |fun f() {
      |  @Suppress("MagicNumber") add(10) && add(20)
      |
      |  @Suppress("MagicNumber")
      |  add(10) && add(20)
      |
      |  @Anno1 @Anno2(param = Param1::class)
      |  add(10) && add(20)
      |
      |  @Anno1
      |  @Anno2(param = Param1::class)
      |  @Anno3
      |  @Anno4(param = Param2::class)
      |  add(10) && add(20)
      |
      |  @Anno1
      |  @Anno2(param = Param1::class)
      |  @Anno3
      |  @Anno4(param = Param2::class)
      |  add(10) && add(20)
      |
      |  @Suppress("MagicNumber") add(10) &&
      |      add(20) &&
      |      add(30)
      |
      |  add(@Suppress("MagicNumber") 10) &&
      |      add(20) &&
      |      add(30)
      |}
      |"""
          .trimMargin(),
      deduceMaxWidth = true,
  )

  @Test
  fun `annotated function declarations`() = assertFormatted(
      """
      |@Anno
      |fun f() {
      |  add(10)
      |}
      |
      |@Anno(param = 1)
      |fun f() {
      |  add(10)
      |}
      |"""
          .trimMargin(),
  )

  @Test
  fun `annotated class declarations`() = assertFormatted(
      """
      |@Anno class F
      |
      |@Anno(param = 1) class F
      |
      |@Anno(P)
      |// Foo
      |@Anno("param")
      |class F
      |"""
          .trimMargin(),
  )

  @Test
  fun `handle type arguments in annotations`() = assertFormatted(
      """
      |@TypeParceler<UUID, UUIDParceler>() class MyClass {}
      |"""
          .trimMargin(),
  )

  @Test
  fun `handle one line KDoc`() = assertFormatted(
      """
      |/** Hi, I am a one line kdoc */
      |class MyClass {}
      |"""
          .trimMargin(),
  )

  @Test
  fun `handle KDoc with Link`() = assertFormatted(
      """
      |/** This links to [AnotherClass] */
      |class MyClass {}
      |"""
          .trimMargin(),
  )

  @Test
  fun `handle KDoc with paragraphs`() = assertFormatted(
      """
      |/**
      | * Hi, I am a two paragraphs kdoc
      | *
      | * There's a space line to preserve between them
      | */
      |class MyClass {}
      |"""
          .trimMargin(),
  )

  @Test
  fun `handle KDoc with blocks`() = assertFormatted(
      """
      |/**
      | * Hi, I am a two paragraphs kdoc
      | *
      | * @param param1 this is param1
      | * @param[param2] this is param2
      | */
      |class MyClass {}
      |"""
          .trimMargin(),
  )

  @Test
  fun `handle KDoc with code examples`() = assertFormatted(
      """
      |/**
      | * This is how you write a simple hello world in Kotlin:
      | * ```
      | * fun main(args: Array<String>) {
      | *   println("Hello World!")
      | * }
      | * ```
      | *
      | * Amazing ah?
      | *
      | * ```
      | * fun `code can be with a blank line above it` () {}
      | * ```
      | *
      | * Or after it!
      | */
      |class MyClass {}
      |"""
          .trimMargin(),
  )

  @Test
  fun `handle KDoc with tagged code examples`() = assertFormatted(
      """
      |/**
      | * ```kotlin
      | * fun main(args: Array<String>) {
      | *   println("Hello World!")
      | * }
      | * ```
      | */
      |class MyClass {}
      |"""
          .trimMargin(),
  )

  @Test
  fun `handle stray code markers in lines and produce stable output`() {
    val code =
        """
        |/**
        | * Look! code: ``` aaa
        | * fun f() = Unit
        | * foo
        | * ```
        | */
        |class MyClass {}
        |"""
            .trimMargin()
    assertFormatted(Formatter.format(code))
  }

  @Test
  fun `code block with triple backtick`() {
    val code =
        """
        |/**
        | * Look! code:
        | * ```
        | * aaa ``` wow
        | * ```
        | */
        |class MyClass {}
        |"""
            .trimMargin()
    val expected =
        """
        |/**
        | * Look! code:
        | * ```
        | * aaa ``` wow
        | * ```
        | */
        |class MyClass {}
        |"""
            .trimMargin()
    assertThatFormatting(code).isEqualTo(expected)
  }

  @Test
  fun `when code closer in mid of line, produce stable output`() {
    val code =
        """
        |/**
        | * Look! code: ``` aaa
        | * fun f() = Unit
        | * foo ``` wow
        | */
        |class MyClass {}
        |"""
            .trimMargin()
    assertFormatted(Formatter.format(code))
  }

  @Test
  fun `handle KDoc with link reference`() = assertFormatted(
      """
      |/** Doc line with a reference to [AnotherClass] in the middle of a sentence */
      |class MyClass {}
      |"""
          .trimMargin(),
  )

  @Test
  fun `handle KDoc with links one after another`() = assertFormatted(
      """
      |/** Here are some links [AnotherClass] [AnotherClass2] */
      |class MyClass {}
      |"""
          .trimMargin(),
  )

  @Test
  fun `don't add spaces after links in Kdoc`() = assertFormatted(
      """
      |/** Here are some links [AnotherClass][AnotherClass2]hello */
      |class MyClass {}
      |"""
          .trimMargin(),
  )

  @Test
  fun `don't remove spaces after links in Kdoc`() = assertFormatted(
      """
      |/** Please see [onNext] (which has more details) */
      |class MyClass {}
      |"""
          .trimMargin(),
  )

  @Test
  fun `link anchor in KDoc are preserved`() = assertFormatted(
      """
      |/** [link anchor](the URL for the link anchor goes here) */
      |class MyClass {}
      |"""
          .trimMargin(),
  )

  @Test
  fun `don't add spaces between links in KDoc (because they're actually references)`() = assertFormatted(
      """
      |/** Here are some links [AnotherClass][AnotherClass2] */
      |class MyClass {}
      |
      |/** The final produced value may have [size][ByteString.size] < [bufferSize]. */
      |class MyClass {}
      |"""
          .trimMargin(),
  )

  @Test
  fun `collapse spaces after links in KDoc`() {
    val code =
        """
        |/** Here are some links [Class1], [Class2]   [Class3]. hello */
        |class MyClass {}
        |"""
            .trimMargin()
    val expected =
        """
        |/** Here are some links [Class1], [Class2] [Class3]. hello */
        |class MyClass {}
        |"""
            .trimMargin()
    assertThatFormatting(code).isEqualTo(expected)
  }

  @Test
  fun `collapse newlines after links in KDoc`() {
    val code =
        """
        |/**
        | * Here are some links [Class1]
        | * [Class2]
        | */
        |class MyClass {}
        |"""
            .trimMargin()
    val expected =
        """
        |/** Here are some links [Class1] [Class2] */
        |class MyClass {}
        |"""
            .trimMargin()
    assertThatFormatting(code).isEqualTo(expected)
  }

  @Test
  fun `do not crash because of malformed KDocs and produce stable output`() {
    val code =
        """
        |/** Surprise ``` */
        |class MyClass {}
        |"""
            .trimMargin()
    assertFormatted(Formatter.format(code))
  }

  @Test
  fun `Respect spacing of text after link`() = assertFormatted(
      """
      |/** Enjoy this link [linkstuff]. */
      |class MyClass {}
      |
      |/** There are many [FooObject]s. */
      |class MyClass {}
      |"""
          .trimMargin(),
  )

  @Test
  fun `handle KDoc with multiple separated param tags, breaking and merging lines and missing asterisk`() {
    val code =
        """
        |/**
        | * Trims leading whitespace characters followed by [marginPrefix] from every line of a source string and removes
        | * the first and the last lines if they are blank (notice difference blank vs empty).
        |
        | * Doesn't affect a line if it doesn't contain [marginPrefix] except the first and the last blank lines.
        | *
        | * Doesn't preserve the original line endings.
        | *
        | * @param marginPrefix non-blank string, which is used as a margin delimiter. Default is `|` (pipe character).
        | *
        | * @sample samples.text.Strings.trimMargin
        | * @see trimIndent
        | * @see kotlin.text.isWhitespace
        | */
        |class ThisWasCopiedFromTheTrimMarginMethod {}
        |"""
            .trimMargin()
    val expected =
        """
        |/**
        | * Trims leading whitespace characters followed by [marginPrefix] from every line of a source string
        | * and removes the first and the last lines if they are blank (notice difference blank vs empty).
        | *
        | * Doesn't affect a line if it doesn't contain [marginPrefix] except the first and the last blank
        | * lines.
        | *
        | * Doesn't preserve the original line endings.
        | *
        | * @param marginPrefix non-blank string, which is used as a margin delimiter. Default is `|` (pipe
        | *   character).
        | * @sample samples.text.Strings.trimMargin
        | * @see trimIndent
        | * @see kotlin.text.isWhitespace
        | */
        |class ThisWasCopiedFromTheTrimMarginMethod {}
        |"""
            .trimMargin()
    assertThatFormatting(code).isEqualTo(expected)
  }

  @Test
  fun `KDoc is reflowed`() {
    val code =
        """
        |/** Lorem ipsum dolor sit amet, consectetur */
        |class MyClass {}
        |"""
            .trimMargin()
    val expected =
        """
        |/**
        | * Lorem ipsum dolor sit amet,
        | * consectetur
        | */
        |class MyClass {}
        |"""
            .trimMargin()
    assertThatFormatting(code)
        .withOptions(defaultTestFormattingOptions.copy(maxWidth = 33))
        .isEqualTo(expected)
  }

  @Test
  fun `sanity - block and continuation indents are 4`() {
    val code =
        """
        |fun f() {
        |    for (child in
        |        node.next.next.next.next
        |            .data()) {
        |        println(child)
        |    }
        |}
        |"""
            .trimMargin()
    assertThatFormatting(code)
        .withOptions(
            FormattingOptions(
                maxWidth = 35,
                blockIndent = 4,
                continuationIndent = 4,
                trailingCommaManagementStrategy = TrailingCommaManagementStrategy.NONE,
            ),
        )
        .isEqualTo(code)
  }

  @Test
  fun `comment after a block is stable and does not add space lines`() = assertFormatted(
      """
      |fun doIt() {}
      |
      |/* this is the first comment */
      |"""
          .trimMargin(),
  )

  @Test
  fun `preserve LF, CRLF and CR line endings`() {
    val lines = listOf("fun main() {", "  println(\"test\")", "}")
    for (ending in listOf("\n", "\r\n", "\r")) {
      val code = lines.joinToString(ending) + ending
      assertThatFormatting(code).isEqualTo(code)
    }
  }

  @Test
  fun `handle trailing commas (constructors)`() = assertFormatted(
      """
      |////////////////////
      |class Foo(
      |    a: Int,
      |)
      |
      |class Foo(
      |    a: Int,
      |    b: Int,
      |)
      |
      |class Foo(
      |    a: Int,
      |    b: Int
      |)
      |"""
          .trimMargin(),
      deduceMaxWidth = true,
  )

  @Test
  fun `handle trailing commas (explicit constructors)`() = assertFormatted(
      """
      |////////////////////////
      |class Foo
      |constructor(
      |    a: Int,
      |)
      |
      |class Foo
      |constructor(
      |    a: Int,
      |    b: Int,
      |)
      |
      |class Foo
      |constructor(
      |    a: Int,
      |    b: Int
      |)
      |"""
          .trimMargin(),
      deduceMaxWidth = true,
  )

  @Test
  fun `handle trailing commas (secondary constructors)`() = assertFormatted(
      """
      |////////////////////////
      |class Foo {
      |  constructor(
      |      a: Int,
      |  )
      |}
      |
      |class Foo {
      |  constructor(
      |      a: Int,
      |      b: Int,
      |  )
      |}
      |
      |class Foo {
      |  constructor(
      |      a: Int,
      |      b: Int
      |  )
      |}
      |"""
          .trimMargin(),
      deduceMaxWidth = true,
  )

  @Test
  fun `handle trailing commas (function definitions)`() = assertFormatted(
      """
      |////////////////////////
      |fun <
      |    T,
      |> foo() {}
      |
      |fun <
      |    T,
      |    S,
      |> foo() {}
      |
      |fun foo(
      |    a: Int,
      |) {}
      |
      |fun foo(
      |    a: Int,
      |    b: Int
      |) {}
      |
      |fun foo(
      |    a: Int,
      |    b: Int,
      |) {}
      |
      |fun foo(
      |    a: Int,
      |    b: Int,
      |    c: Int,
      |) {}
      |"""
          .trimMargin(),
      deduceMaxWidth = true,
  )

  @Test
  fun `handle trailing commas (function calls)`() = assertFormatted(
      """
      |////////////////////////
      |fun main() {
      |  foo(
      |      3,
      |  )
      |
      |  foo<Int>(
      |      3,
      |  )
      |
      |  foo<
      |      Int,
      |  >(
      |      3,
      |  )
      |
      |  foo<Int>(
      |      "asdf", "asdf")
      |
      |  foo<
      |      Int,
      |  >(
      |      "asd",
      |      "asd",
      |  )
      |
      |  foo<
      |      Int,
      |      Boolean,
      |  >(
      |      3,
      |  )
      |}
      |"""
          .trimMargin(),
      deduceMaxWidth = true,
  )

  @Test
  fun `handle trailing commas (proprties)`() = assertFormatted(
      """
      |//////////////////////////
      |val foo: String
      |  set(
      |      value,
      |  ) {}
      |"""
          .trimMargin(),
      deduceMaxWidth = true,
  )

  @Test
  fun `handle trailing commas (higher-order functions)`() = assertFormatted(
      """
      |//////////////////////////
      |fun foo(
      |    x:
      |        (
      |            Int,
      |        ) -> Unit
      |) {}
      |"""
          .trimMargin(),
      deduceMaxWidth = true,
  )

  @Test
  fun `handle trailing commas (after lambda arg)`() = assertFormatted(
      """
      |//////////////////////////
      |fun foo() {
      |  foo(
      |      { it },
      |  )
      |}
      |"""
          .trimMargin(),
      deduceMaxWidth = true,
  )

  @Test
  fun `handle trailing commas (other)`() = assertFormatted(
      """
      |//////////////////////////
      |fun main() {
      |  val (
      |      x: Int,
      |  ) = foo()
      |  val (
      |      x: Int,
      |      y: Int,
      |  ) = foo()
      |
      |  val (
      |      x: Int,
      |  ) = foo(
      |      blablablablFoobar,
      |      alskdjasld)
      |
      |  val (
      |      x: Int,
      |      y: Int,
      |  ) = foo(
      |      blablablablFoobar,
      |      asldkalsd)
      |
      |  a[
      |      0,
      |  ] = 43
      |  a[
      |      0,
      |      1,
      |  ] = 43
      |
      |  [
      |      0,
      |  ]
      |  [
      |      0,
      |      1,
      |  ]
      |
      |  when (foo) {
      |    'x',
      |    -> 43
      |    'x',
      |    'y',
      |    -> 43
      |    'x',
      |    'y',
      |    'z',
      |    'w',
      |    'a',
      |    'b',
      |    -> 43
      |  }
      |
      |  try {
      |    //
      |  } catch (e: Error,) {
      |    //
      |  }
      |}
      |"""
          .trimMargin(),
      deduceMaxWidth = true,
  )

  @Test
  fun `assignment of a scoping function`() = assertFormatted(
      """
      |////////////////////////////
      |val foo = coroutineScope {
      |  foo()
      |  //
      |}
      |
      |fun foo() = coroutineScope {
      |  foo()
      |  //
      |}
      |
      |fun foo() = use { x ->
      |  foo()
      |  //
      |}
      |
      |fun foo() = scope label@{
      |  foo()
      |  //
      |}
      |
      |fun foo() =
      |    coroutineScope { x ->
      |      foo()
      |      //
      |    }
      |
      |fun foo() =
      |    coroutineScope label@{
      |      foo()
      |      //
      |    }
      |
      |fun foo() =
      |    Runnable @Px {
      |      foo()
      |      //
      |    }
      |
      |fun longName() =
      |    coroutineScope {
      |      foo()
      |      //
      |    }
      |"""
          .trimMargin(),
      deduceMaxWidth = true,
  )

  @Test
  fun `dot-qualified scoping functions are block-like`() = assertFormatted(
      """
      |/////////////////////////////////
      |fun f() {
      |  val fn = scope.launch {
      |    doThing()
      |    doAnother()
      |  }
      |}
      |
      |fun g() {
      |  val longVariableName =
      |      scope.launch {
      |        doThing()
      |        doAnother()
      |      }
      |}
      |
      |fun h() = scope.launch {
      |  doThing()
      |  doAnother()
      |}
      |
      |fun longFunctionName() =
      |    scope.launch {
      |      doThing()
      |      doAnother()
      |    }
      |
      |fun j() {
      |  x = scope.launch {
      |    doThing()
      |    doAnother()
      |  }
      |}
      |
      |fun k() {
      |  longVariableName =
      |      scope.launch {
      |        doThing()
      |        doAnother()
      |      }
      |}
      |
      |fun l() {
      |  val fn = scope?.launch {
      |    doThing()
      |    doAnother()
      |  }
      |}
      |
      |fun m() {
      |  val longVariableName =
      |      scope?.launch {
      |        doThing()
      |        doAnother()
      |      }
      |}
      |"""
          .trimMargin(),
      deduceMaxWidth = true,
  )

  @Test
  fun `dot-qualified scoping functions single-line`() = assertFormatted(
      """
      |///////////////////////////////////////////
      |fun f() {
      |  val fn = scope.launch { doThing() }
      |}
      |"""
          .trimMargin(),
      deduceMaxWidth = true,
  )

  @Test
  fun `top level properties with other types preserve newline spacing`() {
    assertFormatted(
        """
        |/////////////////////////////////////////
        |fun something() {
        |  println("hi")
        |}
        |
        |const val SOME_CONST = 1
        |val SOME_STR = "hi"
        |// Single comment
        |val SOME_INT = 1
        |
        |// Intentional space above single comment
        |val SOME_INT2 = 1
        |
        |val FOO = 2
        |const val BAR = 3
        |
        |fun baz() = 1
        |
        |val d = 1
        |
        |class Bar {}
        |
        |val e = 1
        |/** Doc block */
        |val f = 1
        |
        |/** Intent. space above doc */
        |val g = 1
        |
        |data class Qux(val foo: String)
        |"""
            .trimMargin(),
        deduceMaxWidth = true,
    )

    assertThatFormatting(
        """
        |import com.example.foo
        |import com.example.bar
        |const val SOME_CONST = foo.a
        |val SOME_STR = bar.a
        |"""
            .trimMargin(),
    )
        .isEqualTo(
            """
            |import com.example.bar
            |import com.example.foo
            |
            |const val SOME_CONST = foo.a
            |val SOME_STR = bar.a
            |"""
                .trimMargin(),
        )
  }

  @Test
  fun `first line is never empty`() = assertThatFormatting(
      """
      |
      |fun f() {}
      |"""
          .trimMargin(),
  )
      .isEqualTo(
          """
          |fun f() {}
          |"""
              .trimMargin(),
      )

  @Test
  fun `at most one newline between any adjacent top-level elements`() = assertThatFormatting(
      """
      |import com.Bar
      |
      |
      |import com.Foo
      |
      |
      |fun f() {}
      |
      |
      |fun f() {}
      |
      |
      |class C {}
      |
      |
      |class C {}
      |
      |
      |val x = Foo()
      |
      |
      |val x = Bar()
      |"""
          .trimMargin(),
  )
      .isEqualTo(
          """
          |import com.Bar
          |import com.Foo
          |
          |fun f() {}
          |
          |fun f() {}
          |
          |class C {}
          |
          |class C {}
          |
          |val x = Foo()
          |
          |val x = Bar()
          |"""
              .trimMargin(),
      )

  @Test
  fun `at least one newline between any adjacent top-level elements, unless it's a property`() = assertThatFormatting(
      """
      |import com.Bar
      |import com.Foo
      |fun f() {}
      |fun f() {}
      |class C {}
      |class C {}
      |val x = Foo()
      |val x = Bar()
      |"""
          .trimMargin(),
  )
      .isEqualTo(
          """
          |import com.Bar
          |import com.Foo
          |
          |fun f() {}
          |
          |fun f() {}
          |
          |class C {}
          |
          |class C {}
          |
          |val x = Foo()
          |val x = Bar()
          |"""
              .trimMargin(),
      )

  @Test
  fun `handle array of annotations with field prefix`() {
    val code: String =
        """
        |class MyClass {
        |  @field:[JvmStatic Volatile]
        |  var myVar: String? = null
        |}
        |"""
            .trimMargin()
    assertThatFormatting(code).isEqualTo(code)
  }

  @Test
  fun `handle array of annotations without field prefix`() {
    val code: String =
        """
        |class MyClass {
        |  @[JvmStatic Volatile]
        |  var myVar: String? = null
        |}
        |"""
            .trimMargin()
    assertThatFormatting(code).isEqualTo(code)
  }

  // Regression test against https://github.com/facebook/ktfmt/issues/243
  @Test
  fun `regression test against Issue 243`() {
    val code =
        """
        |class Foo {
        |  companion object {
        |    var instance: Foo? = null
        |
        |    fun getInstance() {
        |      return instance ?: synchronized(Foo::class) {
        |        Foo().also { instance = it }
        |      }
        |    }
        |  }
        |}
        |"""
            .trimMargin()

    // Don't throw.
    Formatter.format(code)
  }

  // Regression test against https://github.com/facebook/ktfmt/issues/557
  @Test
  fun `empty companion object`() {
    assertFormatted(
        """
        |class Foo {
        |  val a: String
        |
        |  companion object;
        |
        |  init {
        |    a = "Hello"
        |  }
        |}
        |"""
            .trimMargin(),
    )
  }

  // Regression test against https://github.com/facebook/ktfmt/issues/557
  @Test
  fun `empty companion object with nothing after`() {
    val code =
        """
        |class Foo {
        |  companion object;
        |}
        |"""
            .trimMargin()

    val expected =
        """
        |class Foo {
        |  companion object
        |}
        |"""
            .trimMargin()

    assertThatFormatting(code).isEqualTo(expected)
  }

  @Test
  fun `lambda with required arrow`() = assertFormatted(
      """
      |val a = { x: Int -> }
      |val b = { x: Int -> 0 }
      |val c = { x: Int ->
      |  val y = 0
      |  y
      |}
      |"""
          .trimMargin(),
  )

  @Test
  fun `lambda with optional arrow`() = assertFormatted(
      """
      |val a = { -> }
      |val b = { -> 0 }
      |val c = { ->
      |  val y = 0
      |  y
      |}
      |"""
          .trimMargin(),
  )

  @Test
  fun `lambda missing optional arrow`() = assertFormatted(
      """
      |val a = {}
      |val b = { 0 }
      |val c = {
      |  val y = 0
      |  y
      |}
      |"""
          .trimMargin(),
  )

  @Test
  fun `lambda with only comments`() {
    assertFormatted(
        """
        |val a = { /* do nothing */ }
        |val b = {
        |  /* do nothing */
        |  /* also do nothing */
        |}
        |val c = { -> /* do nothing */ }
        |val d = { _ -> /* do nothing */ }
        |private val e = Runnable {
        |  // do nothing
        |}
        |private val f: () -> Unit = {
        |  // no-op
        |}
        |private val g: () -> Unit = { /* no-op */ }
        |"""
            .trimMargin(),
    )

    assertFormatted(
        """
        |//////////////////////////////
        |val a = { /* do nothing */ }
        |val b = {
        |  /* do nothing */
        |  /* also do nothing */
        |}
        |val c = { ->
        |  /* do nothing */
        |}
        |val d = { _ ->
        |  /* do nothing */
        |}
        |private val e = Runnable {
        |  // do nothing
        |}
        |private val f: () -> Unit = {
        |  // no-op
        |}
        |private val g: () -> Unit = {
        |  /* no-op */
        |}
        |"""
            .trimMargin(),
        deduceMaxWidth = true,
    )
  }

  @Test
  fun `lambda block with single and multiple statements`() = assertFormatted(
      """
      |private val a = Runnable {
      |  foo()
      |  TODO("implement me")
      |}
      |
      |private val b = Runnable { TODO("implement me") }
      |
      |private val c: () -> Unit = {
      |  foo()
      |  TODO("implement me")
      |}
      |
      |private val d: () -> Unit = { TODO("implement me") }
      |"""
          .trimMargin(),
  )

  @Test
  fun `lambda block with comments and statements mix`() = assertFormatted(
      """
      |private val a = Runnable {
      |  // no-op
      |  TODO("implement me")
      |}
      |
      |private val b = Runnable {
      |  TODO("implement me")
      |  // no-op
      |}
      |
      |private val c: () -> Unit = {
      |  /* no-op */ TODO("implement me")
      |}
      |
      |private val d: () -> Unit = { ->
      |  /* no-op */ TODO("implement me")
      |}
      |
      |private val e: (String, Int) -> Unit = { _, i -> foo(i) /* do nothing ... */ }
      |"""
          .trimMargin(),
  )

  @Test
  fun `lambda block with comments and with statements have same formatting treatment`() = assertFormatted(
      """
      |private val a = Runnable { /* no-op */ }
      |private val A = Runnable { TODO("...") }
      |
      |private val b = Runnable {
      |  /* no-op 1 */
      |  /* no-op 2 */
      |}
      |private val B = Runnable {
      |  TODO("no-op")
      |  TODO("no-op")
      |}
      |
      |private val c: () -> Unit = { /* no-op */ }
      |private val C: () -> Unit = { TODO("...") }
      |
      |private val d: () -> Unit = {
      |  /*.*/
      |  /* do nothing ... */
      |}
      |private val D: () -> Unit = {
      |  foo()
      |  TODO("implement me")
      |}
      |"""
          .trimMargin(),
  )

  @Test
  fun `last parameter with comment and with statements have same formatting treatment`() {
    assertFormatted(
        """
        |private val a =
        |    call(param) {
        |      // no-op
        |      /* comment */
        |    }
        |private val A =
        |    call(param) {
        |      a.run()
        |      TODO("implement me")
        |    }
        |
        |private val b = call(param) { /* no-op */ }
        |private val B = call(param) { TODO("implement me") }
        |
        |private val c = firstCall().prop.call(param) { /* no-op */ }
        |private val C = firstCall().prop.call(param) { TODO("implement me") }
        |"""
            .trimMargin(),
    )

    assertFormatted(
        """
        |////////////////////////////////////////
        |private val a =
        |    firstCall().prop.call(
        |        mySuperInterestingParameter) {
        |          /* no-op */
        |        }
        |private val A =
        |    firstCall().prop.call(
        |        mySuperInterestingParameter) {
        |          TODO("...")
        |        }
        |
        |fun b() {
        |  myProp.funCall(param) { /* 12345 */ }
        |  myProp.funCall(param) { TODO("123") }
        |
        |  myProp.funCall(param) { /* 123456 */ }
        |  myProp.funCall(param) { TODO("1234") }
        |
        |  myProp.funCall(param) {
        |    /* 1234567 */
        |  }
        |  myProp.funCall(param) {
        |    TODO("12345")
        |  }
        |
        |  myProp.funCall(param) {
        |    /* 12345678 */
        |  }
        |  myProp.funCall(param) {
        |    TODO("123456")
        |  }
        |
        |  myProp.funCall(param) {
        |    /* 123456789 */
        |  }
        |  myProp.funCall(param) {
        |    TODO("1234567")
        |  }
        |
        |  myProp.funCall(param) {
        |    /* very_very_long_comment_that_should_go_on_its_own_line */
        |  }
        |  myProp.funCall(param) {
        |    TODO(
        |        "_a_very_long_comment_that_should_go_on_its_own_line")
        |  }
        |}
        |
        |private val c =
        |    firstCall().prop.call(param) {
        |      /* no-op */
        |    }
        |private val C =
        |    firstCall().prop.call(param) {
        |      TODO("...")
        |    }
        |"""
            .trimMargin(),
        deduceMaxWidth = true,
    )
  }

  @Test
  fun `chaining - many dereferences`() = assertFormatted(
      """
      |/////////////////////////
      |rainbow.red.orange.yellow
      |    .green
      |    .blue
      |    .indigo
      |    .violet
      |    .cyan
      |    .magenta
      |    .key
      |"""
          .trimMargin(),
      deduceMaxWidth = true,
  )

  @Test
  fun `chaining - many dereferences, fit on one line`() = assertFormatted(
      """
      |///////////////////////////////////////////////////////////////////////////
      |rainbow.red.orange.yellow.green.blue.indigo.violet.cyan.magenta.key
      |"""
          .trimMargin(),
      deduceMaxWidth = true,
  )

  @Test
  fun `chaining - many dereferences, one invocation at end`() = assertFormatted(
      """
      |/////////////////////////
      |rainbow.red.orange.yellow
      |    .green
      |    .blue
      |    .indigo
      |    .violet
      |    .cyan
      |    .magenta
      |    .key
      |    .build()
      |"""
          .trimMargin(),
      deduceMaxWidth = true,
  )

  @Test
  fun `chaining - many dereferences, one invocation at end, fit on one line`() = assertFormatted(
      """
      |///////////////////////////////////////////////////////////////////////////
      |rainbow.red.orange.yellow.green.blue.indigo.violet.cyan.magenta.key.build()
      |"""
          .trimMargin(),
      deduceMaxWidth = true,
  )

  @Test
  fun `chaining - many dereferences, two invocations at end`() = assertFormatted(
      """
      |/////////////////////////
      |rainbow.red.orange.yellow
      |    .green
      |    .blue
      |    .indigo
      |    .violet
      |    .cyan
      |    .magenta
      |    .key
      |    .build()
      |    .shine()
      |"""
          .trimMargin(),
      deduceMaxWidth = true,
  )

  @Test
  fun `chaining - many dereferences, one invocation in the middle`() = assertFormatted(
      """
      |/////////////////////////
      |rainbow.red.orange.yellow
      |    .green
      |    .blue
      |    .shine()
      |    .indigo
      |    .violet
      |    .cyan
      |    .magenta
      |    .key
      |"""
          .trimMargin(),
      deduceMaxWidth = true,
  )

  @Test
  fun `chaining - many dereferences, two invocations in the middle`() = assertFormatted(
      """
      |/////////////////////////
      |rainbow.red.orange.yellow
      |    .green
      |    .blue
      |    .indigo
      |    .shine()
      |    .bright()
      |    .violet
      |    .cyan
      |    .magenta
      |    .key
      |"""
          .trimMargin(),
      deduceMaxWidth = true,
  )

  @Test
  fun `chaining - many dereferences, one lambda at end`() = assertFormatted(
      """
      |/////////////////////////
      |rainbow.red.orange.yellow
      |    .green
      |    .blue
      |    .indigo
      |    .violet
      |    .cyan
      |    .magenta
      |    .key
      |    .build { it.appear }
      |"""
          .trimMargin(),
      deduceMaxWidth = true,
  )

  @Test
  fun `chaining - many dereferences, one short lambda at end`() = assertFormatted(
      """
      |/////////////////////////
      |rainbow.red.orange.yellow
      |    .green
      |    .blue
      |    .indigo
      |    .violet
      |    .cyan
      |    .magenta
      |    .key
      |    .z { it }
      |"""
          .trimMargin(),
      deduceMaxWidth = true,
  )

  @Test
  fun `chaining - many dereferences, one multiline lambda at end`() = assertFormatted(
      """
      |/////////////////////////
      |rainbow.red.orange.yellow
      |    .green
      |    .blue
      |    .indigo
      |    .violet
      |    .cyan
      |    .magenta
      |    .key
      |    .z {
      |      it
      |      it
      |    }
      |"""
          .trimMargin(),
      deduceMaxWidth = true,
  )

  @Test
  fun `chaining - many dereferences, one short lambda in the middle`() = assertFormatted(
      """
      |/////////////////////////
      |rainbow.red.orange.yellow
      |    .green
      |    .blue
      |    .z { it }
      |    .indigo
      |    .violet
      |    .cyan
      |    .magenta
      |    .key
      |"""
          .trimMargin(),
      deduceMaxWidth = true,
  )

  @Test
  fun `chaining - many dereferences, one multiline lambda in the middle`() = assertFormatted(
      """
      |/////////////////////////
      |rainbow.red.orange.yellow
      |    .green
      |    .blue
      |    .z {
      |      it
      |      it
      |    }
      |    .indigo
      |    .violet
      |    .cyan
      |    .magenta
      |    .key
      |"""
          .trimMargin(),
      deduceMaxWidth = true,
  )

  @Test
  fun `chaining - many dereferences, one multiline lambda in the middle, remainder could fit on one line`() = assertFormatted(
      """
      |/////////////////////////////////////////////////////////////////////////////////////////
      |rainbow.red.orange.yellow.green.blue
      |    .z {
      |      it
      |      it
      |    }
      |    .indigo
      |    .violet
      |    .cyan
      |    .magenta
      |    .key
      |"""
          .trimMargin(),
      deduceMaxWidth = true,
  )

  @Test
  fun `chaining - many dereferences, one multiline lambda and two invocations in the middle, remainder could fit on one line`() = assertFormatted(
      """
      |/////////////////////////////////////////////////////////////////////////////////////////
      |rainbow.red.orange.yellow.green.blue
      |    .z {
      |      it
      |      it
      |    }
      |    .shine()
      |    .bright()
      |    .indigo
      |    .violet
      |    .cyan
      |    .magenta
      |    .key
      |"""
          .trimMargin(),
      deduceMaxWidth = true,
  )

  @Test
  fun `chaining - many dereferences, one lambda and invocation at end`() = assertFormatted(
      """
      |/////////////////////////
      |rainbow.red.orange.yellow
      |    .green
      |    .blue
      |    .indigo
      |    .violet
      |    .cyan
      |    .magenta
      |    .key
      |    .z { it }
      |    .shine()
      |"""
          .trimMargin(),
      deduceMaxWidth = true,
  )

  @Test
  fun `chaining - many dereferences, one multiline lambda and invocation at end`() = assertFormatted(
      """
      |/////////////////////////
      |rainbow.red.orange.yellow
      |    .green
      |    .blue
      |    .indigo
      |    .violet
      |    .cyan
      |    .magenta
      |    .key
      |    .z {
      |      it
      |      it
      |    }
      |    .shine()
      |"""
          .trimMargin(),
      deduceMaxWidth = true,
  )

  @Test
  fun `chaining - many dereferences, one invocation and lambda at end`() = assertFormatted(
      """
      |/////////////////////////
      |rainbow.red.orange.yellow
      |    .green
      |    .blue
      |    .indigo
      |    .violet
      |    .cyan
      |    .magenta
      |    .key
      |    .shine()
      |    .z { it }
      |"""
          .trimMargin(),
      deduceMaxWidth = true,
  )

  @Test
  fun `chaining - many dereferences, one short lambda and invocation in the middle`() = assertFormatted(
      """
      |/////////////////////////
      |rainbow.red.orange.yellow
      |    .green
      |    .blue
      |    .indigo
      |    .z { it }
      |    .shine()
      |    .violet
      |    .cyan
      |    .magenta
      |    .key
      |"""
          .trimMargin(),
      deduceMaxWidth = true,
  )

  @Test
  fun `chaining - many dereferences, invocation and one short lambda in the middle`() = assertFormatted(
      """
      |/////////////////////////
      |rainbow.red.orange.yellow
      |    .green
      |    .blue
      |    .indigo
      |    .shine()
      |    .z { it }
      |    .violet
      |    .cyan
      |    .magenta
      |    .key
      |"""
          .trimMargin(),
      deduceMaxWidth = true,
  )

  @Test
  fun `chaining - many dereferences, starting with this`() = assertFormatted(
      """
      |/////////////////////////
      |this.red.orange.yellow
      |    .green
      |    .blue
      |    .indigo
      |    .violet
      |    .cyan
      |    .magenta
      |    .key
      |"""
          .trimMargin(),
      deduceMaxWidth = true,
  )

  @Test
  fun `chaining - many dereferences, starting with this, one invocation at end`() = assertFormatted(
      """
      |/////////////////////////
      |this.red.orange.yellow
      |    .green
      |    .blue
      |    .indigo
      |    .violet
      |    .cyan
      |    .magenta
      |    .key
      |    .build()
      |"""
          .trimMargin(),
      deduceMaxWidth = true,
  )

  @Test
  fun `chaining - many dereferences, starting with super`() = assertFormatted(
      """
      |/////////////////////////
      |super.red.orange.yellow
      |    .green
      |    .blue
      |    .indigo
      |    .violet
      |    .cyan
      |    .magenta
      |    .key
      |"""
          .trimMargin(),
      deduceMaxWidth = true,
  )

  @Test
  fun `chaining - many dereferences, starting with super, one invocation at end`() = assertFormatted(
      """
      |/////////////////////////
      |super.red.orange.yellow
      |    .green
      |    .blue
      |    .indigo
      |    .violet
      |    .cyan
      |    .magenta
      |    .key
      |    .build()
      |"""
          .trimMargin(),
      deduceMaxWidth = true,
  )

  @Test
  fun `chaining - many dereferences, starting with short variable`() = assertFormatted(
      """
      |/////////////////////////
      |z123.red.orange.yellow
      |    .green
      |    .blue
      |    .indigo
      |    .violet
      |    .cyan
      |    .magenta
      |    .key
      |"""
          .trimMargin(),
      deduceMaxWidth = true,
  )

  @Test
  fun `chaining - many dereferences, starting with short variable, one invocation at end`() = assertFormatted(
      """
      |/////////////////////////
      |z123.red.orange.yellow
      |    .green
      |    .blue
      |    .indigo
      |    .violet
      |    .cyan
      |    .magenta
      |    .key
      |    .build()
      |"""
          .trimMargin(),
      deduceMaxWidth = true,
  )

  @Test
  fun `chaining - many dereferences, starting with short variable and lambda, invocation at end`() = assertFormatted(
      """
      |/////////////////////////
      |z12.z { it }
      |    .red
      |    .orange
      |    .yellow
      |    .green
      |    .blue
      |    .indigo
      |    .violet
      |    .cyan
      |    .magenta
      |    .key
      |    .shine()
      |"""
          .trimMargin(),
      deduceMaxWidth = true,
  )

  @Test
  fun `chaining - many dereferences, starting with this and lambda, invocation at end`() = assertFormatted(
      """
      |/////////////////////////
      |this.z { it }
      |    .red
      |    .orange
      |    .yellow
      |    .green
      |    .blue
      |    .indigo
      |    .violet
      |    .cyan
      |    .magenta
      |    .key
      |    .shine()
      |"""
          .trimMargin(),
      deduceMaxWidth = true,
  )

  @Test
  fun `chaining - many invocations`() = assertFormatted(
      """
      |/////////////////////////
      |rainbow.a().b().c()
      |"""
          .trimMargin(),
      deduceMaxWidth = true,
  )

  @Test
  fun `chaining - many invocations, with multiline lambda at end`() = assertFormatted(
      """
      |/////////////////////////
      |rainbow.a().b().c().zz {
      |  it
      |  it
      |}
      |"""
          .trimMargin(),
      deduceMaxWidth = true,
  )

  @Test
  fun `chaining - many dereferences, starting type name`() = assertFormatted(
      """
      |/////////////////////////
      |com.sky.Rainbow.red
      |    .orange
      |    .yellow
      |    .green
      |    .blue
      |    .indigo
      |    .violet
      |    .cyan
      |    .magenta
      |    .key
      |"""
          .trimMargin(),
      deduceMaxWidth = true,
  )

  @Test
  fun `chaining - many invocations, starting with short variable, lambda at end`() = assertFormatted(
      """
      |/////////////
      |z12.shine()
      |    .bright()
      |    .z { it }
      |"""
          .trimMargin(),
      deduceMaxWidth = true,
  )

  @Test
  fun `chaining - start with invocation, lambda at end`() = assertFormatted(
      """
      |/////////////////////
      |getRainbow(
      |        aa, bb, cc)
      |    .z { it }
      |"""
          .trimMargin(),
      deduceMaxWidth = true,
  )

  @Test
  fun `chaining - many invocations, start with lambda`() = assertFormatted(
      """
      |/////////////////////
      |z { it }
      |    .shine()
      |    .bright()
      |"""
          .trimMargin(),
      deduceMaxWidth = true,
  )

  @Test
  fun `chaining - start with type name, end with invocation`() = assertFormatted(
      """
      |/////////////////////////
      |com.sky.Rainbow
      |    .colorFactory
      |    .build()
      |"""
          .trimMargin(),
      deduceMaxWidth = true,
  )

  @Test
  fun `chaining (indentation) - multiline lambda`() = assertFormatted(
      """
      |/////////////////////////
      |rainbow.z {
      |  it
      |  it
      |}
      |"""
          .trimMargin(),
      deduceMaxWidth = true,
  )

  @Test
  fun `chaining (indentation) - multiline lambda with trailing dereferences`() = assertFormatted(
      """
      |/////////////////////////
      |rainbow
      |    .z {
      |      it
      |      it
      |    }
      |    .red
      |"""
          .trimMargin(),
      deduceMaxWidth = true,
  )

  @Test
  fun `chaining (indentation) - multiline lambda with long name`() = assertFormatted(
      """
      |/////////////////////////
      |rainbow
      |    .someLongLambdaName {
      |      it
      |      it
      |    }
      |"""
          .trimMargin(),
      deduceMaxWidth = true,
  )

  @Test
  fun `chaining (indentation) - multiline lambda with long name and trailing dereferences`() = assertFormatted(
      """
      |/////////////////////////
      |rainbow
      |    .someLongLambdaName {
      |      it
      |      it
      |    }
      |    .red
      |"""
          .trimMargin(),
      deduceMaxWidth = true,
  )

  @Test
  fun `chaining (indentation) - multiline lambda with prefix`() = assertFormatted(
      """
      |/////////////////////////
      |rainbow.red.z {
      |  it
      |  it
      |}
      |"""
          .trimMargin(),
      deduceMaxWidth = true,
  )

  @Test
  fun `chaining (indentation) - multiline lambda with prefix, forced to next line`() = assertFormatted(
      """
      |/////////////////////////
      |rainbow.red.orange.yellow
      |    .longLambdaName {
      |      it
      |      it
      |    }
      |"""
          .trimMargin(),
      deduceMaxWidth = true,
  )

  @Test
  fun `chaining (indentation) - multiline lambda with prefix, forced to next line with another expression`() = assertFormatted(
      """
      |/////////////////////////
      |rainbow.red.orange.yellow
      |    .key
      |    .longLambdaName {
      |      it
      |      it
      |    }
      |"""
          .trimMargin(),
      deduceMaxWidth = true,
  )

  @Test
  fun `chaining (indentation) - multiline arguments`() = assertFormatted(
      """
      |/////////////////////////
      |rainbow.shine(
      |    infrared,
      |    ultraviolet,
      |)
      |"""
          .trimMargin(),
      deduceMaxWidth = true,
  )

  @Test
  fun `chaining (indentation) - multiline arguments with trailing dereferences`() = assertFormatted(
      """
      |/////////////////////////
      |rainbow
      |    .shine(
      |        infrared,
      |        ultraviolet,
      |    )
      |    .red
      |"""
          .trimMargin(),
      deduceMaxWidth = true,
  )

  @Test
  fun `chaining (indentation) - multiline arguments, forced to next line`() = assertFormatted(
      """
      |/////////////////////////
      |rainbow.red.orange.yellow
      |    .shine(
      |        infrared,
      |        ultraviolet,
      |    )
      |"""
          .trimMargin(),
      deduceMaxWidth = true,
  )

  @Test
  fun `chaining (indentation) - multiline arguments, forced to next line with another expression`() = assertFormatted(
      """
      |/////////////////////////
      |rainbow.red.orange.yellow
      |    .key
      |    .shine(
      |        infrared,
      |        ultraviolet,
      |    )
      |"""
          .trimMargin(),
      deduceMaxWidth = true,
  )

  @Test
  fun `chaining (indentation) - multiline arguments, forced to next line with another expression, with trailing dereferences`() = assertFormatted(
      """
      |/////////////////////////
      |rainbow.red.orange.yellow
      |    .key
      |    .shine(
      |        infrared,
      |        ultraviolet,
      |    )
      |    .red
      |"""
          .trimMargin(),
      deduceMaxWidth = true,
  )

  @Test
  fun `chaining (indentation) - multiline arguments, with trailing invocation`() = assertFormatted(
      """
      |/////////////////////////
      |rainbow
      |    .shine(
      |        infrared,
      |        ultraviolet,
      |    )
      |    .bright()
      |"""
          .trimMargin(),
      deduceMaxWidth = true,
  )

  @Test
  fun `chaining (indentation) - multiline arguments, with trailing lambda`() = assertFormatted(
      """
      |/////////////////////////
      |rainbow
      |    .shine(
      |        infrared,
      |        ultraviolet,
      |    )
      |    .z { it }
      |"""
          .trimMargin(),
      deduceMaxWidth = true,
  )

  @Test
  fun `chaining (indentation) - multiline arguments, prefixed with super, with trailing invocation`() = assertFormatted(
      """
      |/////////////////////////
      |super.shine(
      |    infrared,
      |    ultraviolet,
      |)
      |    .bright()
      |"""
          .trimMargin(),
      deduceMaxWidth = true,
  )

  @Test
  fun `chaining (indentation) - multiline arguments, starting with short variable, with trailing invocation`() = assertFormatted(
      """
      |/////////////////////////
      |z12.shine(
      |    infrared,
      |    ultraviolet,
      |)
      |    .bright()
      |"""
          .trimMargin(),
      deduceMaxWidth = true,
  )

  @Test
  fun `chaining (indentation) - start with multiline arguments`() = assertFormatted(
      """
      |/////////////////////////
      |getRainbow(
      |    infrared,
      |    ultraviolet,
      |)
      |"""
          .trimMargin(),
      deduceMaxWidth = true,
  )

  @Test
  fun `chaining (indentation) - start with multiline arguments, with trailing invocation`() = assertFormatted(
      """
      |/////////////////////////
      |getRainbow(
      |    infrared,
      |    ultraviolet,
      |)
      |    .z { it }
      |"""
          .trimMargin(),
      deduceMaxWidth = true,
  )

  @Test
  fun `annotations for expressions`() = assertFormatted(
      """
      |fun f() {
      |  var b
      |  @Suppress("UNCHECKED_CAST") b = f(1) as Int
      |  @Suppress("UNCHECKED_CAST")
      |  b = f(1) as Int
      |
      |  @Suppress("UNCHECKED_CAST") b = f(1) to 5
      |  @Suppress("UNCHECKED_CAST")
      |  b = f(1) to 5
      |
      |  @Suppress("UNCHECKED_CAST") f(1) as Int + 5
      |  @Suppress("UNCHECKED_CAST")
      |  f(1) as Int + 5
      |
      |  @Anno1 /* comment */ @Anno2 f(1) as Int
      |}
      |"""
          .trimMargin(),
  )

  @Test
  fun `annotations for expressions 2`() {
    val code =
        """
        |fun f() {
        |  @Suppress("UNCHECKED_CAST") f(1 + f(1) as Int)
        |  @Suppress("UNCHECKED_CAST")
        |  f(1 + f(1) as Int)
        |}
        |"""
            .trimMargin()

    val expected =
        """
        |fun f() {
        |  @Suppress("UNCHECKED_CAST") f(1 + f(1) as Int)
        |  @Suppress("UNCHECKED_CAST") f(1 + f(1) as Int)
        |}
        |"""
            .trimMargin()

    assertThatFormatting(code).isEqualTo(expected)
  }

  @Test
  fun `function call following long multiline string`() = assertFormatted(
      """
          |////////////////////////////////
          |fun stringFitsButNotMethod() {
          |  val str1 =
          |      $TQ Some string $TQ
          |          .trimIndent()
          |
          |  val str2 =
          |      $TQ Some string $TQ
          |          .trimIndent(someArg)
          |}
          |
          |fun stringTooLong() {
          |  val str1 =
          |      $TQ
          |      Some very long string that might mess things up
          |      $TQ
          |          .trimIndent()
          |
          |  val str2 =
          |      $TQ
          |      Some very long string that might mess things up
          |      $TQ
          |          .trimIndent(someArg)
          |}
          |"""
          .trimMargin(),
      deduceMaxWidth = true,
  )

  @Test
  fun `array-literal in annotation`() = assertFormatted(
      """
      |////////////////////////////////
      |@Anno(
      |    array =
      |        [
      |            someItem,
      |            andAnother,
      |            noTrailingComma])
      |class Host
      |
      |@Anno(
      |    array =
      |        [
      |            someItem,
      |            andAnother,
      |            withTrailingComma,
      |        ])
      |class Host
      |
      |@Anno(
      |    array =
      |        [
      |            // Comment
      |            someItem,
      |            // Comment
      |            andAnother,
      |            // Comment
      |            withTrailingComment
      |            // Comment
      |            // Comment
      |            ])
      |class Host
      |"""
          .trimMargin(),
      deduceMaxWidth = true,
  )

  @Test
  fun `force blank line between class members`() {
    val code =
        """
        |class Foo {
        |  val x = 0
        |  fun foo() {}
        |  class Bar {}
        |  enum class Enum {
        |    A {
        |      val x = 0
        |      fun foo() {}
        |    };
        |    abstract fun foo(): Unit
        |  }
        |}
        |"""
            .trimMargin()

    val expected =
        """
        |class Foo {
        |  val x = 0
        |
        |  fun foo() {}
        |
        |  class Bar {}
        |
        |  enum class Enum {
        |    A {
        |      val x = 0
        |
        |      fun foo() {}
        |    };
        |
        |    abstract fun foo(): Unit
        |  }
        |}
        |"""
            .trimMargin()

    assertThatFormatting(code).isEqualTo(expected)
  }

  @Test
  fun `preserve blank line between class members between properties`() {
    val code =
        """
        |class Foo {
        |  val x = 0
        |  val x = 0
        |
        |  val x = 0
        |}
        |"""
            .trimMargin()

    assertThatFormatting(code).isEqualTo(code)
  }

  @Test
  fun `force blank line between class members preserved between properties with accessors`() {
    val code =
        """
        |class Foo {
        |  val _x = 0
        |  val x = 0
        |    private get
        |  val y = 0
        |}
        |
        |class Foo {
        |  val _x = 0
        |  val x = 0
        |    private set
        |  val y = 0
        |}
        |"""
            .trimMargin()

    val expected =
        """
        |class Foo {
        |  val _x = 0
        |  val x = 0
        |    private get
        |
        |  val y = 0
        |}
        |
        |class Foo {
        |  val _x = 0
        |  val x = 0
        |    private set
        |
        |  val y = 0
        |}
        |"""
            .trimMargin()

    assertThatFormatting(code).isEqualTo(expected)
  }

  @Test
  fun `context receivers`() {
    val code =
        """
        |context(Something)
        |
        |class A {
        |  context(
        |  // Test comment.
        |  Logger, Raise<Error>)
        |
        |  @SomeAnnotation
        |
        |  fun doNothing() {}
        |
        |  context(SomethingElse)
        |
        |  private class NestedClass {}
        |
        |  fun <T> testSuspend(
        |    mock: T,
        |    block: suspend context(SomeContext) T.() -> Unit,
        |  ) = startCoroutine {
        |    T.block()
        |  }
        |}
        |"""
            .trimMargin()

    val expected =
        """
        |context(Something)
        |class A {
        |  context(
        |  // Test comment.
        |  Logger,
        |  Raise<Error>)
        |  @SomeAnnotation
        |  fun doNothing() {}
        |
        |  context(SomethingElse)
        |  private class NestedClass {}
        |
        |  fun <T> testSuspend(
        |      mock: T,
        |      block: suspend context(SomeContext) T.() -> Unit,
        |  ) = startCoroutine {
        |    T.block()
        |  }
        |}
        |"""
            .trimMargin()

    assertThatFormatting(code).isEqualTo(expected)
  }

  @Test
  fun `context parameters`() {
    if (KotlinVersion.CURRENT < KotlinVersion(2, 2)) return
    val code =
        """
        |context(something: Something)
        |
        |class A {
        |  context(
        |  // Test comment.
        |  logger: Logger, raise: Raise<Error>, _: Ignored)
        |
        |  @SomeAnnotation
        |
        |  fun doNothing() {}
        |
        |  context(somethingElse: SomethingElse)
        |
        |  private class NestedClass {}
        |
        |  fun <T> testSuspend(
        |    mock: T,
        |    block: suspend context(someContext: SomeContext) T.() -> Unit,
        |  ) = startCoroutine {
        |    T.block()
        |  }
        |}
        |"""
            .trimMargin()

    val expected =
        """
        |context(something: Something)
        |class A {
        |  context(
        |  // Test comment.
        |  logger: Logger,
        |  raise: Raise<Error>,
        |  _: Ignored)
        |  @SomeAnnotation
        |  fun doNothing() {}
        |
        |  context(somethingElse: SomethingElse)
        |  private class NestedClass {}
        |
        |  fun <T> testSuspend(
        |      mock: T,
        |      block: suspend context(someContext: SomeContext) T.() -> Unit,
        |  ) = startCoroutine {
        |    T.block()
        |  }
        |}
        |"""
            .trimMargin()

    assertThatFormatting(code).isEqualTo(expected)
  }

  @Test
  fun `context receivers on secondary constructor`() {
    val code =
        """
        |class A(val x: Int) {
        |  context(Something)
        |  constructor() : this(0)
        |}
        |"""
            .trimMargin()

    val expected =
        """
        |class A(val x: Int) {
        |  context(Something)
        |  constructor() : this(0)
        |}
        |"""
            .trimMargin()

    assertThatFormatting(code).isEqualTo(expected)
  }

  @Test
  fun `trailing comment after function in class`() = assertFormatted(
      """
      |class Host {
      |  fun fooBlock() {
      |    return
      |  } // Trailing after fn
      |  // Hanging after fn
      |
      |  // End of class
      |}
      |
      |class Host {
      |  fun fooExpr() = 0 // Trailing after fn
      |  // Hanging after fn
      |
      |  // End of class
      |}
      |
      |class Host {
      |  constructor() {} // Trailing after fn
      |  // Hanging after fn
      |
      |  // End of class
      |}
      |
      |class Host
      |// Primary constructor
      |constructor() // Trailing after fn
      |  // Hanging after fn
      |{
      |  // End of class
      |}
      |
      |class Host {
      |  fun fooBlock() {
      |    return
      |  }
      |
      |  // Between elements
      |
      |  fun fooExpr() = 0
      |
      |  // Between elements
      |
      |  fun fooBlock() {
      |    return
      |  }
      |}
      |"""
          .trimMargin(),
  )

  @Test
  fun `trailing comment after function top-level`() {
    assertFormatted(
        """
        |fun fooBlock() {
        |  return
        |} // Trailing after fn
        |// Hanging after fn
        |
        |// End of file
        |"""
            .trimMargin(),
    )

    assertFormatted(
        """
        |fun fooExpr() = 0 // Trailing after fn
        |// Hanging after fn
        |
        |// End of file
        |"""
            .trimMargin(),
    )

    assertFormatted(
        """
        |fun fooBlock() {
        |  return
        |}
        |
        |// Between elements
        |
        |fun fooExpr() = 0
        |
        |// Between elements
        |
        |fun fooBlock() {
        |  return
        |}
        |"""
            .trimMargin(),
    )
  }

  @Test
  fun `line break on base class`() = assertFormatted(
      """
      |///////////////////////////
      |class Basket<T>() :
      |    WovenObject {
      |  // some body
      |}
      |"""
          .trimMargin(),
      deduceMaxWidth = true,
  )

  @Test
  fun `line break on type specifier`() = assertFormatted(
      """
      |///////////////////////////
      |class Basket<T>()
      |    where T : Fruit {
      |  // some body
      |}
      |"""
          .trimMargin(),
      deduceMaxWidth = true,
  )

  @Test
  fun `don't crash on empty enum with semicolons`() {
    assertFormatted(
        """
        |///////////////////////////
        |enum class Foo {
        |  ;
        |
        |  fun foo(): Unit
        |}
        |"""
            .trimMargin(),
        deduceMaxWidth = true,
    )

    assertFormatted(
        """
        |///////////////////////////
        |enum class Foo {
        |  ;
        |
        |  companion object Bar
        |}
        |"""
            .trimMargin(),
        deduceMaxWidth = true,
    )

    assertThatFormatting(
        """
        |enum class Foo {
        |  ;
        |  ;
        |  ;
        |
        |  fun foo(): Unit
        |}
        |"""
            .trimMargin(),
    )
        .isEqualTo(
            """
            |enum class Foo {
            |  ;
            |
            |  fun foo(): Unit
            |}
            |"""
                .trimMargin(),
        )
  }

  @Test
  fun `comment stable test`() {
    // currently unstable
    val first =
        """
        |class Foo { // This is a very long comment that is very long and needs to be line broken because it is long
        |}
        |"""
            .trimMargin()
    val second =
        """
        |class Foo { // This is a very long comment that is very long and needs to be line broken because it
        |            // is long
        |}
        |"""
            .trimMargin()
    val third =
        """
        |class Foo { // This is a very long comment that is very long and needs to be line broken because it
        |  // is long
        |}
        |"""
            .trimMargin()

    assertThatFormatting(first).isEqualTo(second)
    assertThatFormatting(second).isEqualTo(third)
    assertFormatted(third)
  }

  @Test
  fun `comment stable test - no block`() {
    val first =
        """
        |class Fooez // This is a very long comment that is very long and needs to be line broken because it is long
        |"""
            .trimMargin()
    val second =
        """
        |class Fooez // This is a very long comment that is very long and needs to be line broken because it
        |            // is long
        |"""
            .trimMargin()

    assertThatFormatting(first).isEqualTo(second)
    assertFormatted(second)
  }

  @Test
  fun `comment stable test - two blocks`() {
    // currently unstable
    val first =
        """
        |class Fooez // This is a very long comment that is very long and needs to be line broken because it is long
        |class Bar
        |"""
            .trimMargin()
    val second =
        """
        |class Fooez // This is a very long comment that is very long and needs to be line broken because it
        |            // is long
        |
        |class Bar
        |"""
            .trimMargin()
    val third =
        """
        |class Fooez // This is a very long comment that is very long and needs to be line broken because it
        |
        |// is long
        |
        |class Bar
        |"""
            .trimMargin()

    assertThatFormatting(first).isEqualTo(second)
    assertThatFormatting(second).isEqualTo(third)
    assertFormatted(third)
  }

  @Test
  fun `comment stable test - within block`() {
    // currently unstable
    val first =
        """
        |class Foo {
        |  class Bar // This is a very long comment that is very long and needs to be line broken because it is long
        |}
        |"""
            .trimMargin()
    val second =
        """
        |class Foo {
        |  class Bar // This is a very long comment that is very long and needs to be line broken because it
        |            // is long
        |}
        |"""
            .trimMargin()
    val third =
        """
        |class Foo {
        |  class Bar // This is a very long comment that is very long and needs to be line broken because it
        |  // is long
        |}
        |"""
            .trimMargin()

    assertThatFormatting(first).isEqualTo(second)
    assertThatFormatting(second).isEqualTo(third)
    assertFormatted(third)
  }

  @Test
  fun `comment formatting respects max width`() {
    val code =
        """
        |// This is a very long comment that is very long but does not need to be line broken as it is within maxWidth
        |class MyClass {}
        |"""
            .trimMargin()
    assertThatFormatting(code)
        .withOptions(defaultTestFormattingOptions.copy(maxWidth = 120))
        .isEqualTo(code)
  }

  @Test
  fun `guard conditions with subject`() {
    val code =
        """
        |fun feedAnimal(animal: Animal) {
        |    when (animal) {
        |        is Animal.Dog -> animal.feedDog()
        |        is Animal.Cat if !animal.mouseHunter -> animal.feedCat()
        |        is Animal.Cat           if     !animal.birdHunter -> animal.feedCat()
        |        is Animal.Cat if
        |          !animal.birdHunter -> animal.feedCat()
        |        is Animal.Cat if     (!animal.birdHunter) -> animal.feedCat()
        |          else  if animal
        |              .eatsPlants -> animal.giveLettuce()   
        |        else -> println("Unknown animal")
        |    }
        |}
        |"""
            .trimMargin()

    val expected =
        """
        |fun feedAnimal(animal: Animal) {
        |  when (animal) {
        |    is Animal.Dog -> animal.feedDog()
        |    is Animal.Cat if !animal.mouseHunter -> animal.feedCat()
        |    is Animal.Cat if !animal.birdHunter -> animal.feedCat()
        |    is Animal.Cat if !animal.birdHunter -> animal.feedCat()
        |    is Animal.Cat if (!animal.birdHunter) -> animal.feedCat()
        |    else if animal.eatsPlants -> animal.giveLettuce()
        |    else -> println("Unknown animal")
        |  }
        |}
        |"""
            .trimMargin()

    assertThatFormatting(code).isEqualTo(expected)
  }

  @Test
  fun `line with max length that needs a trailing comma`() {
    val code =
        """
        |fun foo(a: String, b: String) {
        |  foo(
        |    a = "this is a very very very very very very veryy long line that has precisely 100 characters",
        |    b = "also is a very very very very very very veryyy long line that has precisely 100 characters"
        |  )
        |}
        |"""
            .trimMargin()

    val expected =
        """
        |fun foo(a: String, b: String) {
        |  foo(
        |    a = "this is a very very very very very very veryy long line that has precisely 100 characters",
        |    b =
        |      "also is a very very very very very very veryyy long line that has precisely 100 characters",
        |  )
        |}
        |"""
            .trimMargin()

    assertThatFormatting(code)
        .withOptions(
            defaultTestFormattingOptions.copy(
                maxWidth = 100,
                blockIndent = 2,
                continuationIndent = 2,
                trailingCommaManagementStrategy = TrailingCommaManagementStrategy.ONLY_ADD,
            ),
        )
        .isEqualTo(expected)
  }

  @Test
  fun `single-line parameter list breaking to multi-line should add trailing comma in one pass`() {
    val code =
        """
        |fun foo(param1: String, param2: String, param3: String) {
        |  functionCall(param1 = "value1", param2 = "value2", param3 = "value3", param4 = "value4", param5 = "value5")
        |}
        |"""
            .trimMargin()

    val expected =
        """
        |fun foo(param1: String, param2: String, param3: String) {
        |  functionCall(
        |    param1 = "value1",
        |    param2 = "value2",
        |    param3 = "value3",
        |    param4 = "value4",
        |    param5 = "value5",
        |  )
        |}
        |"""
            .trimMargin()

    assertThatFormatting(code)
        .withOptions(
            defaultTestFormattingOptions.copy(
                maxWidth = 100,
                blockIndent = 2,
                continuationIndent = 2,
                trailingCommaManagementStrategy = TrailingCommaManagementStrategy.ONLY_ADD,
            ),
        )
        .isEqualTo(expected)
  }

  @Test
  fun `single-line parameter list breaking to multi-line when a parameter spans multiple lines`() {
    val code =
        """
        |fun foo(param1: String, param2: String, param3: String) {
        |  functionCall(param1 = "value1", param2 = "value2", param3 = "this one is very long and will have to sit on its own line otherwise the line would overflow")
        |}
        |"""
            .trimMargin()

    val expected =
        """
        |fun foo(param1: String, param2: String, param3: String) {
        |  functionCall(
        |    param1 = "value1",
        |    param2 = "value2",
        |    param3 =
        |      "this one is very long and will have to sit on its own line otherwise the line would overflow",
        |  )
        |}
        |"""
            .trimMargin()

    assertThatFormatting(code)
        .withOptions(
            defaultTestFormattingOptions.copy(
                maxWidth = 100,
                blockIndent = 2,
                continuationIndent = 2,
                trailingCommaManagementStrategy = TrailingCommaManagementStrategy.ONLY_ADD,
            ),
        )
        .isEqualTo(expected)
  }

  @Test
  fun `a trailing comma added while breaking a list does not push the last line over the max width`() {
    val code =
        """
        |functionCall(shortArg, namedArgument = "string sized so the trailing comma ktfmt adds tips it one char over the limit.")
        |"""
            .trimMargin()

    val expected =
        """
        |functionCall(
        |    shortArg,
        |    namedArgument =
        |        "string sized so the trailing comma ktfmt adds tips it one char over the limit.",
        |)
        |"""
            .trimMargin()

    assertThatFormatting(code).withOptions(Formatter.KOTLINLANG_FORMAT).isEqualTo(expected)
  }

  @Test
  fun `formatting a list whose trailing comma lands at the max width is idempotent`() {
    val code =
        """
        |functionCall(shortArg, namedArgument = "string sized so the trailing comma ktfmt adds tips it one char over the limit.")
        |"""
            .trimMargin()

    val formattedOnce = Formatter.format(Formatter.KOTLINLANG_FORMAT, code)
    assertThatFormatting(formattedOnce)
        .withOptions(Formatter.KOTLINLANG_FORMAT)
        .isEqualTo(formattedOnce)
  }

  @Test
  fun `preserve lambda breaks - keeps multi-line lambda multi-line`() {
    val code =
        """
        |fun compose() {
        |  App {
        |    SelectableCard {
        |      Button { Text("Hello") }
        |    }
        |  }
        |}
        |"""
            .trimMargin()

    assertThatFormatting(code)
        .withOptions(
            FormattingOptions(
                preserveLambdaBreaks = true,
                blockIndent = 2,
                continuationIndent = 4,
            ),
        )
        .isEqualTo(code)
  }

  @Test
  fun `preserve lambda breaks - keeps single-line lambda single-line`() {
    // The classic Gradle/Compose single-line case the option must NOT disturb:
    // dependencies { implementation(libs.androidx.activity) }
    // remember { mutableStateOf(false) }
    val code =
        """
        |fun build() {
        |  dependencies { implementation(libs.androidx.activity) }
        |  val state = remember { mutableStateOf(false) }
        |}
        |"""
            .trimMargin()

    assertThatFormatting(code)
        .withOptions(
            FormattingOptions(
                preserveLambdaBreaks = true,
                blockIndent = 2,
                continuationIndent = 4,
            ),
        )
        .isEqualTo(code)
  }

  @Test
  fun `preserve lambda breaks - disabled collapses multi-line lambda to single line`() {
    val code =
        """
        |fun compose() {
        |  App {
        |    Button { Text("Hello") }
        |  }
        |}
        |"""
            .trimMargin()

    val expected =
        """
        |fun compose() {
        |  App { Button { Text("Hello") } }
        |}
        |"""
            .trimMargin()

    assertThatFormatting(code)
        .withOptions(
            FormattingOptions(
                preserveLambdaBreaks = false,
                blockIndent = 2,
                continuationIndent = 4,
            ),
        )
        .isEqualTo(expected)
  }

  @Test
  fun `preserve lambda breaks - mixed single-line and multi-line preserved independently`() {
    // Inner single-line lambdas stay single-line; outer multi-line lambdas stay multi-line.
    val code =
        """
        |fun compose() {
        |  App {
        |    val state = remember { mutableStateOf(0) }
        |    SelectableCard {
        |      Button { Text("Count: ${'$'}{state.value}") }
        |    }
        |  }
        |}
        |"""
            .trimMargin()

    assertThatFormatting(code)
        .withOptions(
            FormattingOptions(
                preserveLambdaBreaks = true,
                blockIndent = 2,
                continuationIndent = 4,
            ),
        )
        .isEqualTo(code)
  }

  @Test
  fun `preserve lambda breaks - applies to non-trailing lambdas too`() {
    // Lambda passed as a non-trailing argument that is multi-line in source stays multi-line.
    val code =
        """
        |fun test() {
        |  withCallback(
        |      onClick = {
        |        log("clicked")
        |      },
        |      label = "Press",
        |  )
        |}
        |"""
            .trimMargin()

    assertThatFormatting(code)
        .withOptions(
            FormattingOptions(
                preserveLambdaBreaks = true,
                blockIndent = 2,
                continuationIndent = 4,
            ),
        )
        .isEqualTo(code)
  }

  @Test
  fun `preserve lambda breaks - empty lambda always collapses even when multi-line in source`() {
    val code =
        """
        |fun test() {
        |  noop {
        |  }
        |}
        |"""
            .trimMargin()

    val expected =
        """
        |fun test() {
        |  noop {}
        |}
        |"""
            .trimMargin()

    assertThatFormatting(code)
        .withOptions(
            FormattingOptions(
                preserveLambdaBreaks = true,
                blockIndent = 2,
                continuationIndent = 4,
            ),
        )
        .isEqualTo(expected)
  }

  @Test
  fun `preserve lambda breaks - single statement broken across lines is kept multi-line`() {
    val code =
        """
        |fun test() {
        |  scope {
        |    doSomething()
        |  }
        |}
        |"""
            .trimMargin()

    assertThatFormatting(code)
        .withOptions(
            FormattingOptions(
                preserveLambdaBreaks = true,
                blockIndent = 2,
                continuationIndent = 4,
            ),
        )
        .isEqualTo(code)
  }

  @Test
  fun `preserve lambda breaks - still fixes a dangling closing brace`() {
    // Preserving breaks must not mean "leave the source untouched". The outer lambda is multi-line
    // in source, so it stays multi-line, but the trailing '}' that was left on the inner lambda's
    // line is still pulled onto its own line
    val code =
        """
        |fun compose() {
        |  App {
        |    Button { Text("Hello") } }
        |}
        |"""
            .trimMargin()

    val expected =
        """
        |fun compose() {
        |  App {
        |    Button { Text("Hello") }
        |  }
        |}
        |"""
            .trimMargin()

    assertThatFormatting(code)
        .withOptions(
            FormattingOptions(
                preserveLambdaBreaks = true,
                blockIndent = 2,
                continuationIndent = 4,
            ),
        )
        .isEqualTo(expected)
  }

  @Test
  fun `single parameter gets trailing comma when wrapped`() = assertFormatted(
      """
      |///////////////////////////////////////////
      |fun foo(
      |    aLongParameterNameThatForcesWrapping:
      |        String,
      |) {}
      |"""
          .trimMargin(),
      formattingOptions = META_FORMAT,
      deduceMaxWidth = true,
  )

  @Test
  fun `single parameter gets wrapped if line limit reached after trailing comma added`() {
    val code =
        """
        |////////////////////////////////////////////////
        |fun foo(
        |    aLongParameterNameThatForcesWrapping: String
        |) {}
        |"""
            .trimMargin()
    val expected =
        """
        |////////////////////////////////////////////////
        |fun foo(
        |    aLongParameterNameThatForcesWrapping:
        |        String,
        |) {}
        |"""
            .trimMargin()

    assertThatFormatting(code)
        .withOptions(
            META_FORMAT.copy(
                maxWidth = code.lineSequence().first().length,
            ),
        )
        .isEqualTo(expected)
  }

  @Test
  fun `correct indentation for lambda with chained function call (#589)`() = assertFormatted(
      """
      |fun quux() = runnnnn {
      |  foo()
      |  bar()
      |}
      |    .baz()
      |"""
          .trimMargin(),
  )

  @Test
  fun `correct indentation for lambda with multiple chained function call (#589)`() = assertFormatted(
      """
      |fun quux() = runnnnn {
      |  foo()
      |  bar()
      |}
      |    .baz {
      |      foo()
      |      bar()
      |    }
      |"""
          .trimMargin(),
  )

  @Test
  fun `correct indentation for lambda with chained function call in block (#589)`() = assertFormatted(
      """
      |fun quux() {
      |  runnnnn {
      |    foo()
      |    bar()
      |  }
      |      .baz()
      |}
      |"""
          .trimMargin(),
  )

  @Test
  fun `correct indentation for lambda with multiple chained function call in block (#589)`() = assertFormatted(
      """
      |fun quux() {
      |  runnnnn {
      |    foo()
      |    bar()
      |  }
      |      .baz {
      |        foo()
      |        bar()
      |      }
      |}
      |"""
          .trimMargin(),
  )

  @Test
  fun `correct indentation for lambda with multiple chained function call in nested block (#589)`() = assertFormatted(
      """
      |fun quux() {
      |  runnnnn {
      |    foo()
      |    runnnnn {
      |      foo()
      |      bar()
      |    }
      |        .baz {
      |          foo()
      |          bar()
      |        }
      |  }
      |      .baz {
      |        foo()
      |        runnnnn {
      |          foo()
      |          bar()
      |        }
      |            .baz {
      |              foo()
      |              bar()
      |            }
      |      }
      |}
      |"""
          .trimMargin(),
  )

  @Test
  fun `correct indentation for lambda with multiple chained function call in declaration (#589)`() = assertFormatted(
      """
      |val baz = runnnnn {
      |  foo()
      |  bar()
      |}
      |    .baz {
      |      foo()
      |      bar()
      |    }
      |"""
          .trimMargin(),
  )

  @Test
  fun `correct indentation for lambda with multiple chained function call in local declaration (#589)`() = assertFormatted(
      """
      |fun quux() {
      |  val baz = runnnnn {
      |    foo()
      |    bar()
      |  }
      |      .baz {
      |        foo()
      |        bar()
      |      }
      |}
      |"""
          .trimMargin(),
  )

  @Test
  fun `property with chained scoping function with value arguments in selector`() = assertFormatted(
      """
      |val foo = runnnnn {
      |  bar()
      |  baz()
      |}
      |    .fold({ a -> a }, { b -> b })
      |"""
          .trimMargin(),
  )

  @Test
  fun `property with single-line lambda chained call`() = assertFormatted(
      """
      |val foo = runnnnn { singleStatement() }.baz()
      |"""
          .trimMargin(),
  )

  @Test
  fun `property with chained scoping function with actual value arguments`() = assertFormatted(
      """
      |val foo = runnnnn {
      |  bar()
      |  baz()
      |}
      |    .someMethod(arg1, arg2)
      |"""
          .trimMargin(),
  )

  @Test
  fun `consistent formatting for chained scoping function in property vs function body`() {
    val propertyCase =
        """
        |val foo = runnnnn { singleLine() }.baz()
        |"""
            .trimMargin()

    val functionCase =
        """
        |fun quux() = runnnnn { singleLine() }.baz()
        |"""
            .trimMargin()

    // Both should format the same way
    assertFormatted(propertyCase)
    assertFormatted(functionCase)
  }

  @Test
  fun `property delegate with chained scoping function and value arguments`() = assertFormatted(
      """
      |val foo by runnnnn {
      |  bar()
      |  baz()
      |}
      |    .someMethod(arg1, arg2)
      |"""
          .trimMargin(),
  )

  @Test
  fun `backing field with chained scoping function and value arguments`() = assertFormatted(
      """
      |var foo: Int
      |  field = runnnnn {
      |    bar()
      |    baz()
      |  }
      |      .fold({ a -> a }, { b -> b })
      |"""
          .trimMargin(),
  )

  @Test
  fun `property with chained scoping function and safe call value arguments`() = assertFormatted(
      """
      |val foo = runnnnn {
      |  bar()
      |  baz()
      |}
      |    ?.someMethod(arg1, arg2)
      |"""
          .trimMargin(),
  )

  @Test
  fun `property delegate with chained scoping function no value arguments breaks after by`() = assertFormatted(
      """
      |val foo by runnnnn {
      |  bar()
      |  baz()
      |}
      |    .baz()
      |"""
          .trimMargin(),
  )

  @Test
  fun `backing field with chained scoping function no value arguments breaks after equals`() = assertFormatted(
      """
      |var foo: Int
      |  field = runnnnn {
      |    bar()
      |    baz()
      |  }
      |      .baz()
      |"""
          .trimMargin(),
  )

  @Test
  fun `property with leading comment before chained scoping falls back to break`() = assertFormatted(
      """
      |val foo = /* comment */ runnnnn {
      |  bar()
      |  baz()
      |}
      |    .someMethod(arg1, arg2)
      |"""
          .trimMargin(),
  )

  @Test
  fun `property with chained scoping mixed value and no-value selectors keeps same line`() = assertFormatted(
      """
      |val foo = runnnnn {
      |  bar()
      |  baz()
      |}
      |    .map { it }
      |    .fold(a, b)
      |"""
          .trimMargin(),
  )

  @Test
  fun `property initializer no-value chain breaks after equals`() = assertFormatted(
      """
      |val foo = runnnnn {
      |  bar()
      |  baz()
      |}
      |    .baz()
      |"""
          .trimMargin(),
  )

  @Test
  fun `property delegate leading comment falls back to break`() = assertFormatted(
      """
      |val foo by /* comment */ runnnnn {
      |  bar()
      |  baz()
      |}
      |    .someMethod(arg1, arg2)
      |"""
          .trimMargin(),
  )

  @Test
  fun `backing field leading comment falls back to break`() = assertFormatted(
      """
      |var foo: Int
      |  field =
      |  /* comment */ runnnnn {
      |    bar()
      |    baz()
      |  }
      |      .fold(a, b)
      |"""
          .trimMargin(),
  )

  @Test
  fun `property with empty parens in chain treats as no-value and breaks`() = assertFormatted(
      """
      |val foo = runnnnn {
      |  bar()
      |  baz()
      |}
      |    .baz()
      |"""
          .trimMargin(),
  )

  @Test
  fun `property with type arguments only in chain treats as no-value and breaks`() = assertFormatted(
      """
      |val foo = runnnnn {
      |  bar()
      |  baz()
      |}
      |    .map<String>()
      |"""
          .trimMargin(),
  )

  // region call with chained call(s) (#633)

  @Test
  fun `multiline call without chained call (#633)`() = assertFormatted(
      """
      |fun f() {
      |  foo(
      |      1,
      |      2,
      |  )
      |}
      |"""
          .trimMargin(),
  )

  @Test
  fun `multiline call with chained call same line closing paren (#633)`() = assertThatFormatting(
      """
      |fun f() {
      |  foo(
      |      1,
      |      2,
      |  ).bar()
      |}
      |"""
          .trimMargin(),
  )
      .isEqualTo(
          """
          |fun f() {
          |  foo(
          |      1,
          |      2,
          |  )
          |      .bar()
          |}
          |"""
              .trimMargin(),
      )

  @Test
  fun `multiline call with chained call next line selector (#633)`() = assertFormatted(
      """
      |fun f() {
      |  foo(
      |      1,
      |      2,
      |  )
      |      .bar()
      |}
      |"""
          .trimMargin(),
  )

  @Test
  fun `multiline call in property initializer (#633)`() = assertFormatted(
      """
      |val x = foo(
      |    1,
      |    2,
      |)
      |"""
          .trimMargin(),
  )

  @Test
  fun `multiline call in property initializer with chain (#633)`() = assertFormatted(
      """
      |val x = foo(
      |    1,
      |    2,
      |)
      |    .bar()
      |"""
          .trimMargin(),
  )

  @Test
  fun `multiline call in property delegate (#633)`() = assertFormatted(
      """
      |val x by foo(
      |    1,
      |    2,
      |)
      |"""
          .trimMargin(),
  )

  @Test
  fun `multiline call in property delegate with chain (#633)`() = assertFormatted(
      """
      |val x by foo(
      |    1,
      |    2,
      |)
      |    .bar()
      |"""
          .trimMargin(),
  )

  @Test
  fun `multiline call in backing field (#633)`() = assertFormatted(
      """
      |var x: Int
      |  field = foo(
      |      1,
      |      2,
      |  )
      |"""
          .trimMargin(),
  )

  @Test
  fun `multiline call in backing field with chain (#633)`() = assertFormatted(
      """
      |var x: Int
      |  field = foo(
      |      1,
      |      2,
      |  )
      |      .bar()
      |"""
          .trimMargin(),
  )

  @Test
  fun `multiline call with leading line comment (#633)`() = assertFormatted(
      """
      |val x = // comment
      |    foo(
      |        1,
      |        2,
      |    )
      |"""
          .trimMargin(),
  )

  @Test
  fun `multiline call with leading block comment (#633)`() = assertFormatted(
      """
      |val y =
      |    /* comment */ foo(
      |        1,
      |        2,
      |    )
      |"""
          .trimMargin(),
  )

  @Test
  fun `inner multiline call (#633)`() = assertFormatted(
      """
      |val x = foo(
      |    bar(
      |        1,
      |        2,
      |    ),
      |    3,
      |)
      |"""
          .trimMargin(),
  )

  @Test
  fun `inner multiline call with chain (#633)`() = assertFormatted(
      """
      |val x = foo(
      |    bar(
      |        1,
      |        2,
      |    )
      |        .baz(),
      |    3,
      |)
      |"""
          .trimMargin(),
  )

  @Test
  fun `expression-body call with nested multiline calls and chains (#633)`() = assertFormatted(
      """
      |fun quux() = listOf(
      |    "a",
      |    "b",
      |    listOf(
      |        "a",
      |        "b",
      |    ),
      |    listOf(
      |        "a",
      |        "b",
      |    )
      |        .baz()
      |        .bar(1, 2)
      |        .fold { it.boom() }
      |)
      |    .baz()
      |"""
          .trimMargin(),
  )

  @Test
  fun `block-body call with nested multiline calls and chains (#633)`() = assertFormatted(
      """
      |fun quux() {
      |  listOf(
      |      "a",
      |      "b",
      |  )
      |
      |  listOf(
      |      "a",
      |      "b",
      |      listOf(
      |          "a",
      |          "b",
      |      ),
      |      listOf(
      |          "a",
      |          "b",
      |      )
      |          .baz()
      |          .bar(1, 2)
      |          .fold { it.boom() }
      |  )
      |      .baz()
      |}
      |"""
          .trimMargin(),
  )

  // endregion

  companion object {
    /** Triple quotes, useful to use within triple-quoted strings. */
    private const val TQ = "\"\"\""

    @JvmStatic
    @BeforeAll
    fun setUp(): Unit {
      defaultTestFormattingOptions =
          META_FORMAT.copy(trailingCommaManagementStrategy = TrailingCommaManagementStrategy.NONE)
    }
  }
}
