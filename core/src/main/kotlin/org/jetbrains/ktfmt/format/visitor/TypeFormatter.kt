@file:Suppress("DEPRECATION")

package org.jetbrains.ktfmt.format.visitor

import com.google.googlejavaformat.Doc
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtDynamicType
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtFunctionType
import org.jetbrains.kotlin.psi.KtIntersectionType
import org.jetbrains.kotlin.psi.KtModifierList
import org.jetbrains.kotlin.psi.KtNullableType
import org.jetbrains.kotlin.psi.KtProjectionKind
import org.jetbrains.kotlin.psi.KtTypeAlias
import org.jetbrains.kotlin.psi.KtTypeConstraint
import org.jetbrains.kotlin.psi.KtTypeElement
import org.jetbrains.kotlin.psi.KtTypeParameter
import org.jetbrains.kotlin.psi.KtTypeProjection
import org.jetbrains.kotlin.psi.KtTypeReference
import org.jetbrains.kotlin.psi.KtUserType
import org.jetbrains.kotlin.psi.psiUtil.children
import org.jetbrains.ktfmt.format.visitor.Indentation.Companion.ZERO

interface TypeFormatter : KotlinAstFormatter {
  override fun formatTypeReference(type: KtTypeReference) {
    formatType(type, type.modifierList, type.typeElement)
  }

  override fun formatDynamicType(type: KtDynamicType) {
    builder.token("dynamic")
  }

  override fun formatNullableType(type: KtNullableType) {
    formatType(type, type.modifierList, type.innerType)
    builder.token("?")
  }

  override fun formatUserType(type: KtUserType) {
    builder.sync(type)

    if (type.qualifier != null) {
      format(type.qualifier)
      builder.token(".")
    }
    format(type.referenceExpression)
    val typeArgumentList = type.typeArgumentList
    if (typeArgumentList != null) {
      builder.block(expressionBreakIndent) { format(typeArgumentList) }
    }
  }

  override fun formatIntersectionType(type: KtIntersectionType) {
    builder.sync(type)

    // TODO(strulovich): Should this have the same indentation behaviour as `x && y`?
    format(type.getLeftTypeRef())
    builder.space()
    builder.token("&")
    builder.space()
    format(type.getRightTypeRef())
  }

  override fun formatTypeProjection(type: KtTypeProjection) {
    builder.sync(type)
    val typeReference = type.typeReference
    when (type.projectionKind) {
      KtProjectionKind.IN -> {
        builder.token("in")
        builder.space()
        format(typeReference)
      }
      KtProjectionKind.OUT -> {
        builder.token("out")
        builder.space()
        format(typeReference)
      }
      KtProjectionKind.STAR -> builder.token("*")
      KtProjectionKind.NONE -> format(typeReference)
    }
  }

  override fun formatTypeParameter(parameter: KtTypeParameter) {
    builder.sync(parameter)
    format(parameter.modifierList)
    builder.token(parameter.nameIdentifier?.text ?: "")
    val extendsBound = parameter.extendsBound
    if (extendsBound != null) {
      builder.space()
      builder.token(":")
      builder.space()
      format(extendsBound)
    }
  }

  override fun formatTypeConstraint(constraint: KtTypeConstraint) {
    builder.sync(constraint)
    // TODO(nreid260): What about annotations on the type reference? `where @A T : Int`
    format(constraint.subjectTypeParameterName)
    builder.space()
    builder.token(":")
    builder.space()
    format(constraint.boundTypeReference)
  }

  override fun formatFunctionType(type: KtFunctionType) {
    builder.sync(type)

    type.contextReceiverList?.let { functionTypeContextReceiverList ->
      formatContextReceiverList(functionTypeContextReceiverList)
      builder.space()
    }

    val receiver = type.receiver
    if (receiver != null) {
      format(receiver)
      builder.token(".")
    }
    builder.block(expressionBreakIndent) { format(type.parameterList) }
    builder.space()
    builder.token("->")
    builder.space()
    builder.block(expressionBreakIndent) { format(type.returnTypeReference) }
  }

  /** Example `private typealias TextChangedListener = (string: String) -> Unit` */
  override fun formatTypeAlias(typeAlias: KtTypeAlias) {
    builder.sync(typeAlias)
    builder.block(ZERO) {
      format(typeAlias.modifierList)
      builder.token("typealias")
      builder.space()
      builder.token(typeAlias.nameIdentifier?.text ?: fail())
      format(typeAlias.typeParameterList)

      builder.space()
      builder.token("=")
      builder.breakOp(Doc.FillMode.INDEPENDENT, " ", expressionBreakIndent)
      builder.block(expressionBreakIndent) {
        format(typeAlias.getTypeReference())
        format(typeAlias.typeConstraintList)
        builder.guessToken(";")
      }
      builder.forcedBreak()
    }
  }

  fun formatType(type: KtElement, modifierList: KtModifierList?, typeElement: KtTypeElement?) {
    builder.sync(type)
    // Normally we'd visit the children nodes through accessors on 'typeReference', and  we wouldn't
    // loop over children.
    // But, in this case the modifier list can either be inside the parenthesis:
    // ... (@Composable (x) -> Unit)
    // or outside of them:
    // ... @Composable ((x) -> Unit)
    for (child in type.node.children()) {
      when {
        child.psi == modifierList -> format(modifierList)
        child.psi == typeElement -> format(typeElement)
        child.elementType == KtTokens.LPAR -> builder.token("(")
        child.elementType == KtTokens.RPAR -> builder.token(")")
      }
    }
  }
}
