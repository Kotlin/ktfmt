package org.jetbrains.ktfmt.format.visitor.kotlinlang

import com.google.googlejavaformat.Doc
import org.jetbrains.kotlin.psi.KtBackingField
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtContextReceiverList
import org.jetbrains.kotlin.psi.KtDelegatedSuperTypeEntry
import org.jetbrains.kotlin.psi.KtDestructuringDeclaration
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
import org.jetbrains.ktfmt.format.visitor.forcedBreak
import org.jetbrains.ktfmt.format.visitor.format
import org.jetbrains.ktfmt.format.visitor.formatAssignmentLikeExpression
import org.jetbrains.ktfmt.format.visitor.formatCommaSeparatedList
import org.jetbrains.ktfmt.format.visitor.formatContextReceiverList
import org.jetbrains.ktfmt.format.visitor.formatModifierList
import org.jetbrains.ktfmt.format.visitor.formatSuperTypeList
import org.jetbrains.ktfmt.format.visitor.formatTypeConstraintList
import org.jetbrains.ktfmt.format.visitor.formatTypeParameterList
import org.jetbrains.ktfmt.format.visitor.isPrefixedByLineBreak
import org.jetbrains.ktfmt.format.visitor.sync
import org.jetbrains.ktfmt.format.visitor.token
import org.jetbrains.ktfmt.util.CONTEXT_PARAMETER_LIST

/**
 * Custom declaration formatter for KotlinLang style.
 *
 * - Uses [KotlinLangExpressionFormatterImpl.formatAssignmentLikeExpression] to format **both**
 *   property initializers and property delegates.
 *
 * - Uses [KotlinLangExpressionFormatterImpl.formatAssignmentLikeExpression] to format rhs of
 *   destructuring declarations.
 *
 * - [formatClassOrObject] handles formatting of the supertype lists similar to how
 *   [formatAssignmentLikeExpression] works. General rule: preserve user-defined breaks after the
 *   `:` in the supertype list.
 */
internal class KotlinLangDeclarationFormatterImpl : DeclarationFormatterImpl() {
  context(_: FormatterStateHolder)
  override fun formatClassOrObject(classOrObject: KtClassOrObject) {
    builder.sync(classOrObject)
    val contextReceiverList =
      classOrObject.getStubOrPsiChild(CONTEXT_PARAMETER_LIST) as? KtContextReceiverList
    builder.block {
      contextReceiverList?.let {
        formatContextReceiverList(contextReceiverList)
        builder.forcedBreak()
      }
      classOrObject.modifierList?.let { formatModifierList(it) }
      classOrObject.getDeclarationKeyword()?.let { builder.token(it.text) }

      classOrObject.nameIdentifier?.let { name ->
        builder.space()
        builder.token(name.text)
        format(classOrObject.typeParameterList)
      }
      format(classOrObject.primaryConstructor)

      var forceBreakBeforeTypeConstraints = false
      classOrObject.getSuperTypeList()?.let { superTypes ->
        forceBreakBeforeTypeConstraints =
            superTypes.entries.lastOrNull() is KtDelegatedSuperTypeEntry
        builder.space()
        builder.block {
          builder.token(":")
          builder.breakOp(
              breakAllowed = superTypes.isPrefixedByLineBreak,
              plusIndent = expressionBreakIndent,
          )
          builder.block(expressionBreakIndent) {
            builder.fenceComments()
            formatSuperTypeList(superTypes)
          }
        }
      }

      classOrObject.typeConstraintList?.let { typeConstraintList ->
        if (forceBreakBeforeTypeConstraints) {
          builder.forcedBreak(expressionBreakIndent)
        }
        formatTypeConstraintList(typeConstraintList)
      }

      classOrObject.body?.let {
        builder.space()
        format(it)
      }
    }
    if (classOrObject.nameIdentifier != null) {
      builder.forcedBreak()
    }
  }

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
        formatAssignmentLikeExpression(delegate.expression!!, "by")
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
                    typeOrDelegationCall = component.typeReference,
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

  context(_: FormatterStateHolder)
  override fun formatDestructuringDeclaration(
      destructuringDeclaration: KtDestructuringDeclaration,
  ) {
    builder.sync(destructuringDeclaration)
    val modifierList = destructuringDeclaration.modifierList
    if (modifierList != null) {
      formatModifierList(modifierList)
      builder.forcedBreak()
    }
    val valOrVarKeyword = destructuringDeclaration.valOrVarKeyword
    if (valOrVarKeyword != null) {
      builder.token(valOrVarKeyword.text)
      builder.space()
    }
    val hasTrailingComma = destructuringDeclaration.trailingComma != null
    val openingDelimiter = destructuringDeclaration.lPar?.text
    val closingDelimiter = destructuringDeclaration.rPar?.text
    builder.block(expressionBreakIndent) {
      formatCommaSeparatedList(
          destructuringDeclaration.entries,
          forceMultiline = hasTrailingComma,
          prefix = openingDelimiter,
          postfix = closingDelimiter,
          breakBeforePostfix = false,
      )
    }
    val initializer = destructuringDeclaration.initializer
    if (initializer != null) {
      builder.space()
      formatAssignmentLikeExpression(initializer)
    }
  }
}
