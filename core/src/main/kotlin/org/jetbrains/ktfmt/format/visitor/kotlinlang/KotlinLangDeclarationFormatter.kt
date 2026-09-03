package org.jetbrains.ktfmt.format.visitor.kotlinlang

import com.google.googlejavaformat.Doc
import org.jetbrains.kotlin.psi.KtBackingField
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtModifierList
import org.jetbrains.kotlin.psi.KtPropertyAccessor
import org.jetbrains.kotlin.psi.KtPropertyDelegate
import org.jetbrains.kotlin.psi.KtTypeConstraintList
import org.jetbrains.kotlin.psi.KtTypeParameterList
import org.jetbrains.kotlin.psi.KtTypeReference
import org.jetbrains.kotlin.psi.psiUtil.startOffset
import org.jetbrains.ktfmt.format.visitor.DeclarationFormatterImpl
import org.jetbrains.ktfmt.format.visitor.FormatterStateHolder
import org.jetbrains.ktfmt.format.visitor.Indentation.Companion.ZERO
import org.jetbrains.ktfmt.format.visitor.block
import org.jetbrains.ktfmt.format.visitor.blockIndent
import org.jetbrains.ktfmt.format.visitor.breakOp
import org.jetbrains.ktfmt.format.visitor.builder
import org.jetbrains.ktfmt.format.visitor.expressionBreakIndent
import org.jetbrains.ktfmt.format.visitor.fenceComments
import org.jetbrains.ktfmt.format.visitor.format
import org.jetbrains.ktfmt.format.visitor.formatAssignmentLikeExpression
import org.jetbrains.ktfmt.format.visitor.formatChainedBlockLikeCall
import org.jetbrains.ktfmt.format.visitor.formatChainedScopingFunction
import org.jetbrains.ktfmt.format.visitor.formatTypeConstraintList
import org.jetbrains.ktfmt.format.visitor.formatTypeParameterList
import org.jetbrains.ktfmt.format.visitor.isBlockLikeCall
import org.jetbrains.ktfmt.format.visitor.isChainedBlockLikeCall
import org.jetbrains.ktfmt.format.visitor.isChainedScopingFunction
import org.jetbrains.ktfmt.format.visitor.isLambdaOrScopingFunction
import org.jetbrains.ktfmt.format.visitor.token

/**
 * Custom declaration formatter for KotlinLang style.
 *
 * [emitPropertyDeclaration] implements the behaviour introduced in #634 that is reverted in the
 * default style: don't force new line for block-like calls in property initializers.
 *
 * ```
 * val x = foo(
 *     1,
 *     2,
 * )
 * ```
 *
 * See [KotlinLangCallFormatterImpl] for more details.
 */
internal class KotlinLangDeclarationFormatterImpl : DeclarationFormatterImpl() {
  context(_: FormatterStateHolder)
  override fun emitPropertyDeclaration(
      modifiers: KtModifierList?,
      valOrVarKeyword: String?,
      typeParameters: KtTypeParameterList?,
      receiver: KtTypeReference?,
      name: String?,
      type: KtTypeReference?,
      typeConstraintList: KtTypeConstraintList?,
      initializer: KtExpression?,
      delegate: KtPropertyDelegate?,
      accessors: List<KtPropertyAccessor>?,
      backingField: KtBackingField?,
  ) {
    format(modifiers)
    builder.block {
      builder.block {
        if (valOrVarKeyword != null) {
          builder.token(valOrVarKeyword)
          builder.space()
        }

        if (typeParameters != null) {
          formatTypeParameterList(typeParameters)
          builder.space()
        }

        // conditionally indent the name and initializer +4 if the type spans
        // multiple lines
        if (name != null) {
          if (receiver != null) {
            format(receiver)
            builder.token(".")
          }
          builder.token(name)
        }
      }

      builder.block(expressionBreakIndent, isEnabled = name != null) {
        // For example `: String` in `val thisIsALongName: String` or `fun f(): String`
        if (type != null) {
          if (name != null) {
            builder.token(":")
            builder.breakOp(Doc.FillMode.UNIFIED, " ", ZERO)
          }
          format(type)
        }
      }

      // For example `where T : Int` in a generic method
      if (typeConstraintList != null) {
        formatTypeConstraintList(typeConstraintList)
        builder.space()
      }

      // for example `by lazy { compute() }`
      if (delegate != null) {
        builder.space()
        builder.token("by")
        val delegateExpr = delegate.expression
        if (delegateExpr.isLambdaOrScopingFunction) {
          builder.space()
          format(delegate)
        } else if (delegateExpr != null && delegateExpr.isChainedScopingFunction) {
          formatChainedScopingFunction(delegateExpr, emitLeadingBreak = true)
        } else if (delegateExpr.isBlockLikeCall) {
          builder.space()
          format(delegate)
        } else if (delegateExpr != null && delegateExpr.isChainedBlockLikeCall) {
          formatChainedBlockLikeCall(delegateExpr, emitLeadingBreak = true)
        } else {
          builder.breakOp(Doc.FillMode.UNIFIED, " ", expressionBreakIndent)
          builder.block(expressionBreakIndent) {
            builder.fenceComments()
            format(delegate)
          }
        }
      } else if (initializer != null) {
        builder.space()
        formatAssignmentLikeExpression(initializer)
      }
    }
    // for example `field = value`, `private set`, or `get = 2 * field`
    val propertyComponents = buildList {
      if (backingField != null) {
        add(backingField)
      }
      if (accessors != null) {
        addAll(accessors)
      }
    }
        .sortedBy { it.startOffset }
    if (propertyComponents.isNotEmpty()) {
      builder.block(blockIndent) {
        for (component in propertyComponents) {
          builder.forcedBreak()
          // The semicolon must come after the newline, or the output code will not parse.
          builder.guessToken(";")

          when (component) {
            is KtPropertyAccessor -> {
              builder.block {
                emitFunctionDeclaration(
                    contextReceiverList = null,
                    modifierList = component.modifierList,
                    keyword = component.namePlaceholder.text,
                    typeParameters = null,
                    receiverTypeReference = null,
                    name = null,
                    parameterList = component.parameterList,
                    typeConstraintList = null,
                    bodyExpression = component.bodyBlockExpression ?: component.bodyExpression,
                    typeOrDelegationCall = component.returnTypeReference,
                )
              }
            }
            is KtBackingField -> emitBackingField(component)
            else -> error("Unexpected property component: ${component::class}")
          }
        }
      }
    }

    builder.guessToken(";")
  }
}
