package org.jetbrains.ktfmt.format.visitor.kotlinlang

import com.google.googlejavaformat.Doc
import com.google.googlejavaformat.Indent.Const.ZERO
import org.jetbrains.kotlin.psi.KtAnnotatedExpression
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtBinaryExpressionWithTypeRHS
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtReturnExpression
import org.jetbrains.ktfmt.format.visitor.AnnotationFormatter
import org.jetbrains.ktfmt.format.visitor.block
import org.jetbrains.ktfmt.format.visitor.sync

interface KotlinLangAnnotationFormatter : AnnotationFormatter {
  override fun formatAnnotatedExpression(expression: KtAnnotatedExpression) {
    builder.sync(expression)
    builder.block(ZERO) {
      val baseExpression = expression.baseExpression

      builder.block(ZERO) {
        val annotationEntries = expression.annotationEntries
        for (annotationEntry in annotationEntries) {
          if (annotationEntry !== annotationEntries.first()) {
            builder.breakOp(Doc.FillMode.UNIFIED, " ", ZERO)
          }
          format(annotationEntry)
        }
      }

      // Binary expressions in a block have a different meaning according to their formatting.
      // If they are in the line above, they refer to the entire expression, if they're in the same
      // line then only to the first operand of the operator.
      // We force a break to avoid such semantic changes
      when {
        (baseExpression is KtBinaryExpression || baseExpression is KtBinaryExpressionWithTypeRHS) &&
            expression.parent is KtBlockExpression -> builder.forcedBreak()
        baseExpression is KtLambdaExpression -> builder.space()
        baseExpression is KtReturnExpression -> builder.forcedBreak()
        else -> builder.forcedBreak()
      }

      format(expression.baseExpression)
    }
  }
}
