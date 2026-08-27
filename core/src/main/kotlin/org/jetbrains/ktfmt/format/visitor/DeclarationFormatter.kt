package org.jetbrains.ktfmt.format.visitor

import com.google.googlejavaformat.Doc
import com.google.googlejavaformat.OpsBuilder
import com.google.googlejavaformat.Output
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

interface DeclarationFormatter : KotlinAstFormatter {
  /** Example: `fun foo(n: Int) { println(n) }` */
  override fun formatNamedFunction(function: KtNamedFunction) {
    builder.sync(function)
    builder.block(ZERO) {
      formatFunctionLikeExpression(
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
    builder.block(ZERO) {
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
        builder.block(ZERO) {
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
    builder.block(ZERO) {
      if (constructor.hasConstructorKeyword()) {
        builder.breakOp(Doc.FillMode.UNIFIED, " ", ZERO)
      }
      formatFunctionLikeExpression(
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

  /** Example `val a: String` or `x = a` which is part of `(val a: String, x = a)` */
  override fun formatProperty(property: KtProperty) {
    builder.sync(property)
    builder.block(ZERO) {
      declareOne(
          kind = DeclarationKind.FIELD,
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

  /** Example `private constructor(n: Int) : this(4, 5) { ... }` inside a class's body */
  override fun formatSecondaryConstructor(constructor: KtSecondaryConstructor) {
    builder.sync(constructor)
    builder.block(ZERO) {
      val delegationCall = constructor.getDelegationCall()
      formatFunctionLikeExpression(
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
    builder.block(ZERO) {
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
        builder.block(ZERO) {
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
        builder.block(ZERO) { format(curr) }
        markForPartialFormat()
        builder.guessToken(";")
        builder.forcedBreak()

        prev = curr
      }
    }
  }

  /** Example `RED(0xFF0000)` in an enum class */
  override fun formatEnumEntry(enumEntry: KtEnumEntry) {
    builder.sync(enumEntry)
    builder.block(ZERO) {
      format(enumEntry.modifierList)
      builder.token(enumEntry.nameIdentifier?.text ?: fail())
      enumEntry.initializerList?.initializers?.forEach { format(it) }
      enumEntry.body?.let { enumBody ->
        builder.space()
        format(enumBody)
      }
    }
  }

  /** Example `f: String`, or `private val n: Int` or `(a: Int, b: String)` (in for-loops) */
  override fun formatParameter(parameter: KtParameter) {
    builder.sync(parameter)
    builder.block(ZERO) {
      val destructuringDeclaration = parameter.destructuringDeclaration
      val typeReference = parameter.typeReference
      if (destructuringDeclaration != null) {
        builder.block(ZERO) {
          format(destructuringDeclaration)
          if (typeReference != null) {
            builder.token(":")
            builder.space()
            format(typeReference)
          }
        }
      } else {
        declareOne(
            kind = DeclarationKind.PARAMETER,
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

  /** Example `val (a, b: Int) = Pair(1, 2)` or `val [a, b] = Pair(1, 2)` */
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
    declareOne(
        initializer = multiDeclarationEntry.initializer,
        kind = DeclarationKind.PARAMETER,
        modifiers = multiDeclarationEntry.modifierList,
        name = multiDeclarationEntry.nameIdentifier?.text ?: fail(),
        type = multiDeclarationEntry.typeReference,
        valOrVarKeyword = multiDeclarationEntry.ownValOrVarKeywordText,
    )
  }

  enum class DeclarationKind {
    FIELD,
    PARAMETER,
  }

  /**
   * Declare one variable or variable-like thing.
   *
   * Examples:
   * - `var a: Int = 5`
   * - `a: Int`
   * - `private val b:
   */
  private fun declareOne(
      kind: DeclarationKind,
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
  ): Int {
    val verticalAnnotationBreak = Output.BreakTag()

    val isField = kind == DeclarationKind.FIELD

    if (isField) {
      builder.blankLineWanted(OpsBuilder.BlankLineWanted.conditional(verticalAnnotationBreak))
    }

    format(modifiers)
    builder.block(ZERO) {
      builder.block(ZERO) {
        if (valOrVarKeyword != null) {
          builder.token(valOrVarKeyword)
          builder.space()
        }

        if (typeParameters != null) {
          format(typeParameters)
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
        format(typeConstraintList)
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
              builder.block(ZERO) {
                formatFunctionLikeExpression(
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

    if (isField) {
      builder.blankLineWanted(OpsBuilder.BlankLineWanted.conditional(verticalAnnotationBreak))
    }

    return 0
  }

  /**
   * @param keyword e.g., "fun" or "class".
   * @param typeOrDelegationCall for functions, the return typeOrDelegationCall; for classes, the
   *   list of supertypes.
   */
  private fun formatFunctionLikeExpression(
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
        builder.block(ZERO) {
          if (typeOrDelegationCall is KtConstructorDelegationCall) {
            builder.space()
          }
          builder.token(":")
          block()
        }
      }
    }

    val forceTrailingBreak = name != null
    builder.block(ZERO, isEnabled = forceTrailingBreak) {
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
        builder.block(ZERO) { format(typeParameters) }
      }

      if (name != null || receiverTypeReference != null) {
        builder.space()
      }
      builder.block(ZERO) {
        if (receiverTypeReference != null) {
          format(receiverTypeReference)
          builder.breakOp(Doc.FillMode.INDEPENDENT, "", expressionBreakIndent)
          builder.token(".")
        }
        if (name != null) {
          builder.token(name)
        }
      }

      if (parameterList != null && parameterList.hasEmptyParenthesis) {
        builder.block(ZERO) {
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
        builder.block(ZERO) {
          formatInitializerExpression(bodyExpression)
        }
      }
      builder.guessToken(";")
    }
    if (forceTrailingBreak) {
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
    builder.block(ZERO) {
      builder.block(ZERO) { builder.token(backingField.namePlaceholder.text) }

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
