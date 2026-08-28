package org.jetbrains.ktfmt.format.visitor.kotlinlang

import com.google.googlejavaformat.Doc
import org.jetbrains.kotlin.psi.KtAnnotatedExpression
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtReturnExpression
import org.jetbrains.ktfmt.format.visitor.AnnotationFormatterImpl
import org.jetbrains.ktfmt.format.visitor.FormatterStateHolder
import org.jetbrains.ktfmt.format.visitor.Indentation.Companion.ZERO
import org.jetbrains.ktfmt.format.visitor.block
import org.jetbrains.ktfmt.format.visitor.breakOp
import org.jetbrains.ktfmt.format.visitor.builder
import org.jetbrains.ktfmt.format.visitor.format
import org.jetbrains.ktfmt.format.visitor.isBinaryExpression
import org.jetbrains.ktfmt.format.visitor.sync

/**
 * Custom annotation expression formatter for KotlinLang style. For annotations on declarations and
 * types see [KotlinLangListFormatterImpl].
 *
 * General rule for annotations on an expression: keep annotations on the same line as the
 * expression; if the line does not fit, force each annotation into a new line.
 *
 * Exceptions:
 * - Return expressions always force a break
 * - Lambda expressions never force a break, annotation is always glued to `{`
 * - Binary expressions in a block are break sensitive w.r.t. annotations. Therefore, we have to
 *   preserve the original formatting
 *
 * ```
 * @Anno a + b = (@Anno a) + b
 *
 * @Anno
 * a + b = @Anno (a + b)
 * ```
 */
internal class KotlinLangAnnotationFormatterImpl : AnnotationFormatterImpl() {
  context(_: FormatterStateHolder)
  override fun formatAnnotatedExpression(expression: KtAnnotatedExpression) {
    builder.sync(expression)
    val baseExpression = expression.baseExpression

    builder.block {
      val annotationEntries = expression.annotationEntries
      for ((index, annotationEntry) in annotationEntries.withIndex()) {
        if (index > 0) {
          builder.breakOp(Doc.FillMode.UNIFIED, " ", ZERO)
        }
        format(annotationEntry)
      }

      when {
        baseExpression.isBinaryExpression && expression.parent is KtBlockExpression ->
            builder.forcedBreak()
        baseExpression is KtLambdaExpression -> builder.space()
        baseExpression is KtReturnExpression -> builder.forcedBreak()
        else -> builder.breakOp(Doc.FillMode.UNIFIED, " ", ZERO)
      }

      format(expression.baseExpression)
    }
  }
}
