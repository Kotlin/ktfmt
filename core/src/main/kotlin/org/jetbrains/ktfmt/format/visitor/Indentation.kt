package org.jetbrains.ktfmt.format.visitor

import com.google.googlejavaformat.Indent
import com.google.googlejavaformat.Output

sealed class Indentation {
  internal abstract val indent: Indent

  data class Const(val value: Int) : Indentation() {
    override val indent: Indent = Indent.Const.make(value, 1)

    operator fun plus(other: Const): Const = Const(value + other.value)

    operator fun minus(other: Const): Const = Const(value - other.value)

    operator fun unaryPlus(): Const = Const(+value)

    override operator fun unaryMinus(): Const = Const(-value)

    operator fun times(other: Int): Const = Const(value * other)
  }

  data class Conditional(
      val condition: Output.BreakTag,
      val trueIndent: Indentation,
      val falseIndent: Indentation,
  ) : Indentation() {
    override val indent: Indent = Indent.If.make(condition, trueIndent.indent, falseIndent.indent)

    override operator fun unaryMinus(): Conditional =
        Conditional(condition, -trueIndent, -falseIndent)
  }

  abstract operator fun unaryMinus(): Indentation

  companion object {
    val ZERO = Const(0)

    fun makeCond(
        condition: Output.BreakTag?,
        trueIndent: Indentation,
        falseIndent: Indentation,
    ): Indentation =
        when {
          condition == null -> falseIndent
          else -> Conditional(condition, trueIndent, falseIndent)
        }
  }
}
