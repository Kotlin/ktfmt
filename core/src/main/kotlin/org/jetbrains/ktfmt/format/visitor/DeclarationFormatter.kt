package org.jetbrains.ktfmt.format.visitor

import com.google.googlejavaformat.Doc
import com.google.googlejavaformat.OpsBuilder
import java.util.Optional
import kotlin.jvm.optionals.getOrNull
import org.jetbrains.kotlin.com.intellij.psi.PsiElement
import org.jetbrains.kotlin.psi.KtBackingField
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtClassBody
import org.jetbrains.kotlin.psi.KtClassInitializer
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtConstructorDelegationCall
import org.jetbrains.kotlin.psi.KtContextReceiverList
import org.jetbrains.kotlin.psi.KtDelegatedSuperTypeEntry
import org.jetbrains.kotlin.psi.KtDestructuringDeclaration
import org.jetbrains.kotlin.psi.KtDestructuringDeclarationEntry
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtEnumEntry
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtModifierList
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtParameterList
import org.jetbrains.kotlin.psi.KtPrimaryConstructor
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtPropertyAccessor
import org.jetbrains.kotlin.psi.KtPropertyDelegate
import org.jetbrains.kotlin.psi.KtSecondaryConstructor
import org.jetbrains.kotlin.psi.KtSuperTypeCallEntry
import org.jetbrains.kotlin.psi.KtTypeConstraintList
import org.jetbrains.kotlin.psi.KtTypeParameterList
import org.jetbrains.kotlin.psi.KtTypeReference
import org.jetbrains.kotlin.psi.KtWhenExpression
import org.jetbrains.kotlin.psi.psiUtil.startOffset
import org.jetbrains.ktfmt.format.EnumEntryList
import org.jetbrains.ktfmt.format.visitor.Indentation.Companion.ZERO
import org.jetbrains.ktfmt.util.CONTEXT_PARAMETER_LIST
import org.jetbrains.ktfmt.util.ownValOrVarKeywordText

/**
 * Formatter that handles all declarations:
 * - Classes
 * - Functions
 * - Properties (class properties, top-level properties and local properties)
 * - Destructuring declarations
 */
interface DeclarationFormatter : KotlinAstFormatter {
  override fun formatNamedFunction(function: KtNamedFunction) {
    builder.sync(function)
    builder.block {
      emitFunctionDeclaration(
          contextReceiverList =
              function.getStubOrPsiChild(CONTEXT_PARAMETER_LIST) as? KtContextReceiverList,
          modifierList = function.modifierList,
          keyword = "fun",
          typeParameters = function.typeParameterList,
          receiverTypeReference = function.receiverTypeReference,
          name = function.nameIdentifier?.text,
          parameterList = function.valueParameterList,
          typeConstraintList = function.typeConstraintList,
          bodyExpression = function.bodyBlockExpression ?: function.bodyExpression,
          typeOrDelegationCall = function.typeReference,
      )
    }
  }

  override fun formatClassOrObject(classOrObject: KtClassOrObject) {
    builder.sync(classOrObject)
    val contextReceiverList =
        classOrObject.getStubOrPsiChild(CONTEXT_PARAMETER_LIST) as? KtContextReceiverList
    val modifierList = classOrObject.modifierList
    builder.block {
      if (contextReceiverList != null) {
        formatContextReceiverList(contextReceiverList)
        builder.forcedBreak()
      }
      if (modifierList != null) {
        formatModifierList(modifierList)
      }
      val declarationKeyword = classOrObject.getDeclarationKeyword()
      if (declarationKeyword != null) {
        builder.token(declarationKeyword.text ?: fail())
      }
      val name = classOrObject.nameIdentifier
      if (name != null) {
        builder.space()
        builder.token(name.text)
        format(classOrObject.typeParameterList)
      }
      format(classOrObject.primaryConstructor)
      val superTypes = classOrObject.getSuperTypeList()
      if (superTypes != null) {
        builder.space()
        builder.block {
          builder.token(":")
          builder.breakOp(Doc.FillMode.UNIFIED, " ", expressionBreakIndent)
          format(superTypes)
        }
      }
      val typeConstraintList = classOrObject.typeConstraintList
      if (typeConstraintList != null) {
        if (superTypes?.entries?.lastOrNull() is KtDelegatedSuperTypeEntry) {
          builder.forcedBreak(expressionBreakIndent)
        }
        format(typeConstraintList)
        builder.space()
      } else if (classOrObject.body != null) {
        builder.space()
      }
      format(classOrObject.body)
    }
    if (classOrObject.nameIdentifier != null) {
      builder.forcedBreak()
    }
  }

  override fun formatPrimaryConstructor(constructor: KtPrimaryConstructor) {
    builder.sync(constructor)
    builder.block {
      if (constructor.hasConstructorKeyword()) {
        builder.breakOp(Doc.FillMode.UNIFIED, " ", ZERO)
      }
      emitFunctionDeclaration(
          contextReceiverList = null,
          modifierList = constructor.modifierList,
          keyword = if (constructor.hasConstructorKeyword()) "constructor" else null,
          typeParameters = null,
          receiverTypeReference = null,
          name = null,
          parameterList = constructor.valueParameterList,
          typeConstraintList = null,
          bodyExpression = constructor.bodyExpression,
          typeOrDelegationCall = null,
      )
    }
  }

  override fun formatProperty(property: KtProperty) {
    builder.sync(property)
    builder.block {
      emitPropertyDeclaration(
          modifiers = property.modifierList,
          valOrVarKeyword = property.valOrVarKeyword.text,
          typeParameters = property.typeParameterList,
          receiver = property.receiverTypeReference,
          name = property.nameIdentifier?.text,
          type = property.typeReference,
          typeConstraintList = property.typeConstraintList,
          delegate = property.delegate,
          initializer = property.initializer,
          accessors = property.accessors,
          backingField = property.fieldDeclaration,
      )
    }
    builder.guessToken(";")
    if (property.parent !is KtWhenExpression) {
      builder.forcedBreak()
    }
  }

  override fun formatSecondaryConstructor(constructor: KtSecondaryConstructor) {
    builder.sync(constructor)
    builder.block {
      val delegationCall = constructor.getDelegationCall()
      emitFunctionDeclaration(
          contextReceiverList =
              constructor.getStubOrPsiChild(CONTEXT_PARAMETER_LIST) as? KtContextReceiverList,
          modifierList = constructor.modifierList,
          keyword = "constructor",
          typeParameters = null,
          receiverTypeReference = null,
          name = null,
          parameterList = constructor.valueParameterList,
          typeConstraintList = null,
          bodyExpression = constructor.bodyExpression,
          typeOrDelegationCall = if (!delegationCall.isImplicit) delegationCall else null,
      )
    }
  }

  override fun formatConstructorDelegationCall(call: KtConstructorDelegationCall) {
    // Work around a misfeature in kotlin-compiler: call.calleeExpression.accept doesn't call
    // visitReferenceExpression, but calls visitElement instead.
    builder.block {
      builder.token(if (call.isCallToThis) "this" else "super")
      formatFunctionCall(
          null,
          call.typeArgumentList,
          call.valueArgumentList,
          call.trailingLambda,
      )
    }
  }

  override fun formatClassInitializer(initializer: KtClassInitializer) {
    builder.sync(initializer)
    builder.token("init")
    builder.space()
    format(initializer.body)
  }

  override fun formatSuperTypeCallEntry(call: KtSuperTypeCallEntry) {
    builder.sync(call)
    formatFunctionCall(call.calleeExpression, null, call.valueArgumentList, call.trailingLambda)
  }

  override fun formatDelegatedSuperTypeEntry(specifier: KtDelegatedSuperTypeEntry) {
    builder.sync(specifier)
    format(specifier.typeReference)
    builder.space()
    builder.token("by")
    builder.space()
    format(specifier.delegateExpression)
  }

  override fun formatClassBody(body: KtClassBody) {
    builder.sync(body)
    emitBracedBlock(body) { children ->
      val enumEntryList = EnumEntryList.extractChildList(body)
      val members = children.filter { it !is KtEnumEntry }

      if (enumEntryList != null) {
        builder.block {
          builder.breakOp(Doc.FillMode.UNIFIED, "", ZERO)
          for (value in enumEntryList.enumEntries) {
            format(value)
            if (builder.peekToken().getOrNull() == ",") {
              builder.token(",")
              builder.forcedBreak()
            }
          }
        }
        builder.guessToken(";")

        if (members.isNotEmpty()) {
          builder.forcedBreak()
          builder.blankLineWanted(OpsBuilder.BlankLineWanted.YES)
        }
      } else {
        val parent = body.parent
        if (parent is KtClass && parent.isEnum() && children.isNotEmpty()) {
          builder.token(";")
          builder.forcedBreak()
        }
      }

      var prev: PsiElement? = null
      for (curr in members) {
        val blankLineBetweenMembers =
            when {
              prev == null -> OpsBuilder.BlankLineWanted.PRESERVE
              prev !is KtProperty -> OpsBuilder.BlankLineWanted.YES
              prev.getter != null || prev.setter != null -> OpsBuilder.BlankLineWanted.YES
              curr is KtProperty -> OpsBuilder.BlankLineWanted.PRESERVE
              else -> OpsBuilder.BlankLineWanted.YES
            }
        builder.blankLineWanted(blankLineBetweenMembers)

        markForPartialFormat()
        builder.block { format(curr) }
        markForPartialFormat()
        builder.guessToken(";")
        builder.forcedBreak()

        prev = curr
      }
    }
  }

  override fun formatEnumEntry(enumEntry: KtEnumEntry) {
    builder.sync(enumEntry)
    builder.block {
      format(enumEntry.modifierList)
      builder.token(enumEntry.nameIdentifier?.text ?: fail())
      format(enumEntry.initializerList)
      enumEntry.body?.let { enumBody ->
        builder.space()
        format(enumBody)
      }
    }
  }

  override fun formatParameter(parameter: KtParameter) {
    builder.sync(parameter)
    builder.block {
      val destructuringDeclaration = parameter.destructuringDeclaration
      val typeReference = parameter.typeReference
      if (destructuringDeclaration != null) {
        builder.block {
          format(destructuringDeclaration)
          if (typeReference != null) {
            builder.token(":")
            builder.space()
            format(typeReference)
          }
        }
      } else {
        emitPropertyDeclaration(
            modifiers = parameter.modifierList,
            valOrVarKeyword = parameter.valOrVarKeyword?.text,
            name = parameter.nameIdentifier?.text,
            type = typeReference,
            initializer = parameter.defaultValue,
        )
      }
    }
  }

  override fun formatBlockExpression(expression: KtBlockExpression) {
    builder.sync(expression)
    emitBracedBlock(expression) { children -> formatStatements(children) }
  }

  override fun formatDestructuringDeclaration(
      destructuringDeclaration: KtDestructuringDeclaration,
  ) {
    builder.sync(destructuringDeclaration)
    val valOrVarKeyword = destructuringDeclaration.valOrVarKeyword
    if (valOrVarKeyword != null) {
      builder.token(valOrVarKeyword.text)
      builder.space()
    }
    val hasTrailingComma = destructuringDeclaration.trailingComma != null
    val openingDelimiter = destructuringDeclaration.lPar?.text ?: "("
    val closingDelimiter = destructuringDeclaration.rPar?.text ?: ")"
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
      builder.token("=")
      if (hasTrailingComma) {
        builder.space()
      } else {
        builder.breakOp(Doc.FillMode.INDEPENDENT, " ", expressionBreakIndent)
      }
      builder.block(expressionBreakIndent, !hasTrailingComma) { format(initializer) }
    }
  }

  override fun formatDestructuringDeclarationEntry(
      multiDeclarationEntry: KtDestructuringDeclarationEntry,
  ) {
    builder.sync(multiDeclarationEntry)
    emitPropertyDeclaration(
        initializer = multiDeclarationEntry.initializer,
        modifiers = multiDeclarationEntry.modifierList,
        name = multiDeclarationEntry.nameIdentifier?.text ?: fail(),
        type = multiDeclarationEntry.typeReference,
        valOrVarKeyword = multiDeclarationEntry.ownValOrVarKeywordText,
    )
  }

  /**
   * Declare one variable or variable-like thing.
   *
   * Examples:
   * - `var a: Int = 5`
   * - `a: Int`
   * - `private val b:
   */
  private fun emitPropertyDeclaration(
      modifiers: KtModifierList?,
      valOrVarKeyword: String?,
      typeParameters: KtTypeParameterList? = null,
      receiver: KtTypeReference? = null,
      name: String?,
      type: KtTypeReference?,
      typeConstraintList: KtTypeConstraintList? = null,
      initializer: KtExpression?,
      delegate: KtPropertyDelegate? = null,
      accessors: List<KtPropertyAccessor>? = null,
      backingField: KtBackingField? = null,
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
        formatInitializerExpression(initializer)
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

  /**
   * @param keyword e.g., "fun" or "class".
   * @param typeOrDelegationCall for functions, the return typeOrDelegationCall; for classes, the
   *   list of supertypes.
   */
  private fun emitFunctionDeclaration(
      contextReceiverList: KtContextReceiverList?,
      modifierList: KtModifierList?,
      keyword: String?,
      typeParameters: KtTypeParameterList?,
      receiverTypeReference: KtTypeReference?,
      name: String?,
      parameterList: KtParameterList?,
      typeConstraintList: KtTypeConstraintList?,
      bodyExpression: KtExpression?,
      typeOrDelegationCall: KtElement?,
  ) {
    fun emitTypeOrDelegationCall(block: () -> Unit) {
      if (typeOrDelegationCall != null) {
        builder.block {
          if (typeOrDelegationCall is KtConstructorDelegationCall) {
            builder.space()
          }
          builder.token(":")
          block()
        }
      }
    }

    val hasName = name != null
    val hasReceiverTypeReference = receiverTypeReference != null
    builder.block(ZERO, isEnabled = hasName) {
      if (contextReceiverList != null) {
        formatContextReceiverList(contextReceiverList)
        builder.forcedBreak()
      }
      if (modifierList != null) {
        formatModifierList(modifierList)
      }
      if (keyword != null) {
        builder.token(keyword)
      }
      if (typeParameters != null) {
        builder.space()
        builder.block { format(typeParameters) }
      }

      if (hasName || hasReceiverTypeReference) {
        builder.space()
      }
      builder.block {
        if (hasReceiverTypeReference) {
          format(receiverTypeReference)
          builder.breakOp(Doc.FillMode.INDEPENDENT, "", expressionBreakIndent)
          builder.token(".")
        }
        if (hasName) {
          builder.token(name)
        }
      }

      if (parameterList != null && parameterList.hasEmptyParenthesis) {
        builder.block {
          builder.token("(")
          builder.token(")")
          emitTypeOrDelegationCall {
            builder.breakOp(Doc.FillMode.INDEPENDENT, " ", expressionBreakIndent)
            builder.block(expressionBreakIndent) { format(typeOrDelegationCall) }
          }
        }
      } else {
        builder.block(expressionBreakIndent) {
          if (parameterList != null) {
            formatCommaSeparatedList(
                list = parameterList.parameters,
                forceMultiline = parameterList.trailingComma != null,
                prefix = "(",
                postfix = ")",
                wrapInBlock = false,
                breakBeforePostfix = true,
            )
          }
          emitTypeOrDelegationCall {
            builder.space()
            builder.block(-expressionBreakIndent) { format(typeOrDelegationCall) }
          }
        }
      }

      if (typeConstraintList != null) {
        format(typeConstraintList)
      }
      if (bodyExpression is KtBlockExpression) {
        builder.space()
        format(bodyExpression)
      } else if (bodyExpression != null) {
        builder.space()
        builder.block {
          formatInitializerExpression(bodyExpression)
        }
      }
      builder.guessToken(";")
    }
    if (hasName) {
      builder.forcedBreak()
    }
  }

  private fun emitBracedBlock(
      bodyBlockExpression: PsiElement,
      emitChildren: (Array<PsiElement>) -> Unit,
  ) {
    builder.token(
        "{",
        Doc.Token.RealOrImaginary.REAL,
        blockIndent.indent,
        Optional.of(blockIndent.indent),
    )
    val statements = bodyBlockExpression.children
    if (statements.isNotEmpty()) {
      builder.block(blockIndent) {
        builder.forcedBreak()
        builder.blankLineWanted(OpsBuilder.BlankLineWanted.PRESERVE)
        emitChildren(statements)
      }
      builder.forcedBreak()
      builder.blankLineWanted(OpsBuilder.BlankLineWanted.NO)
    }
    builder.token("}", blockIndent)
  }

  private fun emitBackingField(backingField: KtBackingField) {
    builder.sync(backingField)
    builder.block {
      builder.block { builder.token(backingField.namePlaceholder.text) }

      val type = backingField.returnTypeReference
      if (type != null) {
        builder.block(expressionBreakIndent) {
          builder.token(":")
          builder.breakOp(Doc.FillMode.UNIFIED, " ", ZERO)
          format(type)
        }
      }

      val initializer = backingField.initializer
      if (initializer != null) {
        builder.space()
        formatInitializerExpression(initializer)
      }
    }
  }
}
