package org.jetbrains.kotlinx.ktfmt.format.visitor

import com.google.googlejavaformat.layout.LayoutBuilder
import java.util.IdentityHashMap
import org.jetbrains.kotlin.com.intellij.psi.PsiElement
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlinx.ktfmt.format.FormattingOptions

interface FormatterStateHolder {
  val state: FormatterState
}

class FormatterState(
    val options: FormattingOptions,
    val builder: LayoutBuilder,
    val formatter: AbstractKotlinFormatter,
) {
  val blockIndent = Indentation.Const(options.blockIndent)
  val expressionBreakIndent = Indentation.Const(options.continuationIndent)

  val inExpressionTracker = mutableListOf(false)
  val inExpression: Boolean
    get() = inExpressionTracker.last()

  var inImport: Boolean = false

  /** Hug points for lambdas that can hug an enclosing construct, keyed by the lambda. */
  private val hugPoints = IdentityHashMap<PsiElement, LayoutBuilder.HugPoint>()

  /** Returns the hug point for [element], which [markHugPoint] will emit. */
  internal fun hugPoint(element: PsiElement): LayoutBuilder.HugPoint =
      hugPoints.computeIfAbsent(element) { LayoutBuilder.newHugPoint() }

  /** Emits the hug point for [element], if one was requested with [hugPoint]. */
  internal fun markHugPoint(element: PsiElement) {
    hugPoints.remove(element)?.let { builder.hugPoint(it) }
  }

  /**
   * markForPartialFormat is used to delineate the smallest areas of code that must be formatted
   * together.
   *
   * When only parts of the code are being formatted, the requested area is expanded until it's
   * covered by an area marked by this method.
   */
  fun markForPartialFormat() {
    if (!inExpression) {
      builder.markForPartialFormat()
    }
  }

  inline fun inImport(body: () -> Unit) {
    inImport = true
    try {
      body()
    } finally {
      inImport = false
    }
  }

  inline fun inElement(element: PsiElement, body: () -> Unit) {
    inExpressionTracker.add(element is KtExpression || inExpressionTracker.last())
    try {
      body()
    } finally {
      inExpressionTracker.removeLast()
    }
  }
}
