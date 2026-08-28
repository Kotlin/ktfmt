package org.jetbrains.ktfmt.format.visitor

import com.google.googlejavaformat.Doc
import org.jetbrains.kotlin.psi.KtAnnotatedExpression
import org.jetbrains.kotlin.psi.KtAnnotation
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtAnnotationUseSiteTarget
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtReturnExpression
import org.jetbrains.ktfmt.format.visitor.Indentation.Companion.ZERO

interface AnnotationFormatter {
  context(_: FormatterStateHolder)
  fun formatAnnotatedExpression(expression: KtAnnotatedExpression)

  context(_: FormatterStateHolder)
  fun formatAnnotation(annotation: KtAnnotation)

  context(_: FormatterStateHolder)
  fun formatAnnotationUseSiteTarget(annotationTarget: KtAnnotationUseSiteTarget)

  context(_: FormatterStateHolder)
  fun formatAnnotationEntry(annotationEntry: KtAnnotationEntry)
}

internal open class AnnotationFormatterImpl : AnnotationFormatter {
  context(_: FormatterStateHolder)
  override fun formatAnnotatedExpression(expression: KtAnnotatedExpression) {
    builder.sync(expression)
    builder.block {
      val baseExpression = expression.baseExpression

      builder.block {
        val annotationEntries = expression.annotationEntries
        for (annotationEntry in annotationEntries) {
          if (annotationEntry !== annotationEntries.first()) {
            builder.breakOp(Doc.FillMode.UNIFIED, " ", ZERO)
          }
          format(annotationEntry)
        }
      }

      // Binary expressions in a block have a different meaning according to their formatting.
      // If they're in the line above, they refer to the entire expression, if they're in the same
      // line then only to the first operand of the operator.
      // We force a break to avoid such semantic changes
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

  /**
   * A KtAnnotation is used only to group multiple annotations with the same use-site-target. It
   * only appears in a modifier list since annotated expressions do not have use-site-targets.
   */
  context(_: FormatterStateHolder)
  override fun formatAnnotation(annotation: KtAnnotation) {
    builder.sync(annotation)
    builder.block {
      builder.token("@")
      val useSiteTarget = annotation.useSiteTarget
      if (useSiteTarget != null) {
        format(useSiteTarget)
        builder.token(":")
      }
      builder.block(expressionBreakIndent) {
        builder.token("[")

        builder.block {
          var first = true
          builder.breakOp(Doc.FillMode.UNIFIED, "", ZERO)
          for (value in annotation.entries) {
            if (!first) {
              builder.breakOp(Doc.FillMode.UNIFIED, " ", ZERO)
            }
            first = false

            format(value)
          }
        }
      }
      builder.token("]")
    }
    builder.forcedBreak()
  }

  context(_: FormatterStateHolder)
  override fun formatAnnotationUseSiteTarget(annotationTarget: KtAnnotationUseSiteTarget) {
    builder.token(annotationTarget.getAnnotationUseSiteTarget().renderName)
  }

  context(_: FormatterStateHolder)
  override fun formatAnnotationEntry(annotationEntry: KtAnnotationEntry) {
    builder.sync(annotationEntry)
    if (annotationEntry.atSymbol != null) {
      builder.token("@")
    }
    val useSiteTarget = annotationEntry.useSiteTarget
    if (useSiteTarget != null && useSiteTarget.parent == annotationEntry) {
      format(useSiteTarget)
      builder.token(":")
    }
    formatFunctionCall(
        annotationEntry.calleeExpression,
        null, // Type-arguments are included in the annotation's callee expression.
        annotationEntry.valueArgumentList,
        null,
    )
  }
}
