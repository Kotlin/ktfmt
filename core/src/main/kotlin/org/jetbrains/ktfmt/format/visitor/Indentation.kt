package org.jetbrains.ktfmt.format.visitor

import com.google.googlejavaformat.Indent
import com.google.googlejavaformat.Output

/**
 * A wrapper over GJF's [Indent] that provides utility operator functions.
 *
 * - A constant value [Const] that represents plain indent of [Const.value] space chars
 *
 * ```
 *     fun foo(): Int = 10
 * ^^^^ // Indentantion.Const(value = 4)
 * ```
 *
 * - A conditional indent [If] whose value depends on whether [Indentation.If.condition] break tag
 *   has been taken.
 *
 * ```
 * // Indentantion.If(condition = condition, thenIndent = Const(8), elseIndent = Const(1))
 *
 * fun foo(): Int = 10
 *                 ^  // [condition] is not taken, render with [elseIndent]
 *
 * fun foo(): Int =
 *         10
 * ^^^^^^^^  // [condition] is taken, render with [trueIndent]
 *
 * ```
 */
sealed class Indentation {
  internal abstract val indent: Indent

  class Const(val value: Int) : Indentation() {
    override val indent: Indent = Indent.Const.make(value, 1)

    operator fun plus(other: Const): Const = Const(value + other.value)

    operator fun minus(other: Const): Const = Const(value - other.value)

    override operator fun unaryMinus(): Const = Const(-value)

    operator fun times(other: Int): Const = Const(value * other)
  }

  class If
  private constructor(
      val condition: Output.BreakTag,
      val thenIndent: Indentation,
      val elseIndent: Indentation,
  ) : Indentation() {
    override val indent: Indent = Indent.If.make(condition, thenIndent.indent, elseIndent.indent)

    override operator fun unaryMinus(): If = If(condition, -thenIndent, -elseIndent)

    companion object {
      operator fun invoke(
          condition: Output.BreakTag?,
          thenIndent: Indentation,
          elseIndent: Indentation,
      ): Indentation =
          when {
            condition == null -> elseIndent
            else -> If(condition, thenIndent, elseIndent)
          }
    }
  }

  abstract operator fun unaryMinus(): Indentation

  companion object {
    val ZERO = Const(0)
  }
}
