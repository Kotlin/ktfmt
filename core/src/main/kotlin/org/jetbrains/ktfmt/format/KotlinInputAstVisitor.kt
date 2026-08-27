/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

@file:Suppress("DEPRECATION")

package org.jetbrains.ktfmt.format

import com.google.common.base.Throwables
import com.google.common.collect.ImmutableList
import com.google.googlejavaformat.Doc
import com.google.googlejavaformat.FormattingError
import com.google.googlejavaformat.OpsBuilder
import com.google.googlejavaformat.Output.BreakTag
import java.util.ArrayDeque
import java.util.Optional
import kotlin.jvm.optionals.getOrNull
import org.jetbrains.kotlin.com.intellij.psi.PsiElement
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtBackingField
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtBreakExpression
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtCatchClause
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtClassBody
import org.jetbrains.kotlin.psi.KtClassInitializer
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtConstructorDelegationCall
import org.jetbrains.kotlin.psi.KtContextReceiverList
import org.jetbrains.kotlin.psi.KtContinueExpression
import org.jetbrains.kotlin.psi.KtDelegatedSuperTypeEntry
import org.jetbrains.kotlin.psi.KtDestructuringDeclaration
import org.jetbrains.kotlin.psi.KtDestructuringDeclarationEntry
import org.jetbrains.kotlin.psi.KtDoWhileExpression
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtEnumEntry
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFinallySection
import org.jetbrains.kotlin.psi.KtForExpression
import org.jetbrains.kotlin.psi.KtIfExpression
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtModifierList
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtParameterList
import org.jetbrains.kotlin.psi.KtPrimaryConstructor
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtPropertyAccessor
import org.jetbrains.kotlin.psi.KtPropertyDelegate
import org.jetbrains.kotlin.psi.KtReturnExpression
import org.jetbrains.kotlin.psi.KtSecondaryConstructor
import org.jetbrains.kotlin.psi.KtSuperTypeCallEntry
import org.jetbrains.kotlin.psi.KtThrowExpression
import org.jetbrains.kotlin.psi.KtTryExpression
import org.jetbrains.kotlin.psi.KtTypeAlias
import org.jetbrains.kotlin.psi.KtTypeConstraintList
import org.jetbrains.kotlin.psi.KtTypeParameterList
import org.jetbrains.kotlin.psi.KtTypeReference
import org.jetbrains.kotlin.psi.KtWhenConditionInRange
import org.jetbrains.kotlin.psi.KtWhenConditionIsPattern
import org.jetbrains.kotlin.psi.KtWhenConditionWithExpression
import org.jetbrains.kotlin.psi.KtWhenExpression
import org.jetbrains.kotlin.psi.KtWhileExpression
import org.jetbrains.kotlin.psi.psiUtil.startOffset
import org.jetbrains.ktfmt.format.visitor.AbstractFormatterVisitor
import org.jetbrains.ktfmt.format.visitor.AnnotationFormatter
import org.jetbrains.ktfmt.format.visitor.CallFormatter
import org.jetbrains.ktfmt.format.visitor.ExpressionFormatter
import org.jetbrains.ktfmt.format.visitor.FileFormatter
import org.jetbrains.ktfmt.format.visitor.Indentation
import org.jetbrains.ktfmt.format.visitor.Indentation.Companion.ZERO
import org.jetbrains.ktfmt.format.visitor.ListFormatter
import org.jetbrains.ktfmt.format.visitor.TypeFormatter
import org.jetbrains.ktfmt.format.visitor.block
import org.jetbrains.ktfmt.format.visitor.breakOp
import org.jetbrains.ktfmt.format.visitor.fenceComments
import org.jetbrains.ktfmt.format.visitor.forcedBreak
import org.jetbrains.ktfmt.format.visitor.hasEmptyParenthesis
import org.jetbrains.ktfmt.format.visitor.isBlockLikeCall
import org.jetbrains.ktfmt.format.visitor.isChainedBlockLikeCall
import org.jetbrains.ktfmt.format.visitor.isChainedScopingFunction
import org.jetbrains.ktfmt.format.visitor.isLambdaOrScopingFunction
import org.jetbrains.ktfmt.format.visitor.sync
import org.jetbrains.ktfmt.format.visitor.token
import org.jetbrains.ktfmt.format.visitor.trailingLambda
import org.jetbrains.ktfmt.util.CONTEXT_PARAMETER_LIST
import org.jetbrains.ktfmt.util.ownValOrVarKeywordText

/** An AST visitor that builds a stream of {@link Op}s to format. */
open class KotlinInputAstVisitor(
    override val options: FormattingOptions,
    override val builder: OpsBuilder,
) :
    AbstractFormatterVisitor(),
    AnnotationFormatter,
    CallFormatter,
    ExpressionFormatter,
    FileFormatter,
    ListFormatter,
    TypeFormatter {

  /** Standard indentation for a block */
  override val blockIndent: Indentation.Const = Indentation.Const(options.blockIndent)

  /**
   * Standard indentation for a long expression or function call, it is different than block
   * indentation on purpose
   */
  override val expressionBreakIndent: Indentation.Const =
      Indentation.Const(options.continuationIndent)

  /** A record of whether we have visited into an expression. */
  override val inExpression = ArrayDeque(ImmutableList.of(false))

  /** Tracks whether we are handling an import directive */
  override var inImport = false

  /** Example: `fun foo(n: Int) { println(n) }` */
  override fun visitNamedFunction(function: KtNamedFunction) {
    builder.sync(function)
    builder.block(ZERO) {
      visitFunctionLikeExpression(
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

  /**
   * @param keyword e.g., "fun" or "class".
   * @param typeOrDelegationCall for functions, the return typeOrDelegationCall; for classes, the
   *   list of supertypes.
   */
  private fun visitFunctionLikeExpression(
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
        visitContextReceiverList(contextReceiverList)
        builder.forcedBreak()
      }
      if (modifierList != null) {
        visitModifierList(modifierList)
      }
      if (keyword != null) {
        builder.token(keyword)
      }
      if (typeParameters != null) {
        builder.space()
        builder.block(ZERO) { visit(typeParameters) }
      }

      if (name != null || receiverTypeReference != null) {
        builder.space()
      }
      builder.block(ZERO) {
        if (receiverTypeReference != null) {
          visit(receiverTypeReference)
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
            builder.block(expressionBreakIndent) { visit(typeOrDelegationCall) }
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
            builder.block(-expressionBreakIndent) { visit(typeOrDelegationCall) }
          }
        }
      }

      if (typeConstraintList != null) {
        visit(typeConstraintList)
      }
      if (bodyExpression is KtBlockExpression) {
        builder.space()
        visit(bodyExpression)
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

  private fun genSym(): BreakTag {
    return BreakTag()
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

  override fun visitProperty(property: KtProperty) {
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

  override fun visitCallExpression(callExpression: KtCallExpression) {
    builder.sync(callExpression)
    with(callExpression) {
      formatFunctionCall(
          calleeExpression,
          typeArgumentList,
          valueArgumentList,
          trailingLambda,
      )
    }
  }

  override fun visitReturnExpression(expression: KtReturnExpression) {
    builder.sync(expression)
    builder.token("return")
    visit(expression.getTargetLabel())
    val returnedExpression = expression.returnedExpression
    if (returnedExpression != null) {
      builder.space()
      visit(returnedExpression)
    }
    builder.guessToken(";")
  }

  internal enum class DeclarationKind {
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
    val verticalAnnotationBreak = genSym()

    val isField = kind == DeclarationKind.FIELD

    if (isField) {
      builder.blankLineWanted(OpsBuilder.BlankLineWanted.conditional(verticalAnnotationBreak))
    }

    visit(modifiers)
    builder.block(ZERO) {
      builder.block(ZERO) {
        if (valOrVarKeyword != null) {
          builder.token(valOrVarKeyword)
          builder.space()
        }

        if (typeParameters != null) {
          visit(typeParameters)
          builder.space()
        }

        // conditionally indent the name and initializer +4 if the type spans
        // multiple lines
        if (name != null) {
          if (receiver != null) {
            visit(receiver)
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
          visit(type)
        }
      }

      // For example `where T : Int` in a generic method
      if (typeConstraintList != null) {
        visit(typeConstraintList)
        builder.space()
      }

      // for example `by lazy { compute() }`
      if (delegate != null) {
        builder.space()
        builder.token("by")
        val delegateExpr = delegate.expression
        if (delegateExpr.isLambdaOrScopingFunction) {
          builder.space()
          visit(delegate)
        } else if (delegateExpr != null && delegateExpr.isChainedScopingFunction) {
          formatChainedScopingFunction(delegateExpr, emitLeadingBreak = true)
        } else if (delegateExpr.isBlockLikeCall) {
          builder.space()
          visit(delegate)
        } else if (delegateExpr != null && delegateExpr.isChainedBlockLikeCall) {
          formatChainedBlockLikeCall(delegateExpr, emitLeadingBreak = true)
        } else {
          builder.breakOp(Doc.FillMode.UNIFIED, " ", expressionBreakIndent)
          builder.block(expressionBreakIndent) {
            builder.fenceComments()
            visit(delegate)
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
                visitFunctionLikeExpression(
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

  private fun emitBackingField(backingField: KtBackingField) {
    builder.sync(backingField)
    builder.block(ZERO) {
      builder.block(ZERO) { builder.token(backingField.namePlaceholder.text) }

      val type = backingField.returnTypeReference
      if (type != null) {
        builder.block(expressionBreakIndent) {
          builder.token(":")
          builder.breakOp(Doc.FillMode.UNIFIED, " ", ZERO)
          visit(type)
        }
      }

      val initializer = backingField.initializer
      if (initializer != null) {
        builder.space()
        formatInitializerExpression(initializer)
      }
    }
  }

  override fun visitClassOrObject(classOrObject: KtClassOrObject) {
    builder.sync(classOrObject)
    val contextReceiverList =
        classOrObject.getStubOrPsiChild(CONTEXT_PARAMETER_LIST) as? KtContextReceiverList
    val modifierList = classOrObject.modifierList
    builder.block(ZERO) {
      if (contextReceiverList != null) {
        visitContextReceiverList(contextReceiverList)
        builder.forcedBreak()
      }
      if (modifierList != null) {
        visitModifierList(modifierList)
      }
      val declarationKeyword = classOrObject.getDeclarationKeyword()
      if (declarationKeyword != null) {
        builder.token(declarationKeyword.text ?: fail())
      }
      val name = classOrObject.nameIdentifier
      if (name != null) {
        builder.space()
        builder.token(name.text)
        visit(classOrObject.typeParameterList)
      }
      visit(classOrObject.primaryConstructor)
      val superTypes = classOrObject.getSuperTypeList()
      if (superTypes != null) {
        builder.space()
        builder.block(ZERO) {
          builder.token(":")
          builder.breakOp(Doc.FillMode.UNIFIED, " ", expressionBreakIndent)
          visit(superTypes)
        }
      }
      val typeConstraintList = classOrObject.typeConstraintList
      if (typeConstraintList != null) {
        if (superTypes?.entries?.lastOrNull() is KtDelegatedSuperTypeEntry) {
          builder.forcedBreak(expressionBreakIndent)
        }
        visit(typeConstraintList)
        builder.space()
      } else if (classOrObject.body != null) {
        builder.space()
      }
      visit(classOrObject.body)
    }
    if (classOrObject.nameIdentifier != null) {
      builder.forcedBreak()
    }
  }

  override fun visitPrimaryConstructor(constructor: KtPrimaryConstructor) {
    builder.sync(constructor)
    builder.block(ZERO) {
      if (constructor.hasConstructorKeyword()) {
        builder.breakOp(Doc.FillMode.UNIFIED, " ", ZERO)
      }
      visitFunctionLikeExpression(
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

  /** Example `private constructor(n: Int) : this(4, 5) { ... }` inside a class's body */
  override fun visitSecondaryConstructor(constructor: KtSecondaryConstructor) {
    builder.sync(constructor)
    builder.block(ZERO) {
      val delegationCall = constructor.getDelegationCall()
      visitFunctionLikeExpression(
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

  override fun visitConstructorDelegationCall(call: KtConstructorDelegationCall) {
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

  override fun visitClassInitializer(initializer: KtClassInitializer) {
    builder.sync(initializer)
    builder.token("init")
    builder.space()
    visit(initializer.body)
  }

  override fun visitSuperTypeCallEntry(call: KtSuperTypeCallEntry) {
    builder.sync(call)
    formatFunctionCall(call.calleeExpression, null, call.valueArgumentList, call.trailingLambda)
  }

  /**
   * Example `Collection<Int> by list` in `class MyList(list: List<Int>) : Collection<Int> by list`
   */
  override fun visitDelegatedSuperTypeEntry(specifier: KtDelegatedSuperTypeEntry) {
    builder.sync(specifier)
    visit(specifier.typeReference)
    builder.space()
    builder.token("by")
    builder.space()
    visit(specifier.delegateExpression)
  }

  override fun visitWhenExpression(expression: KtWhenExpression) {
    builder.sync(expression)
    builder.block(ZERO) {
      emitKeywordWithCondition("when", expression.subjectExpression)

      builder.space()
      builder.token(
          "{",
          Doc.Token.RealOrImaginary.REAL,
          blockIndent.indent,
          Optional.of(blockIndent.indent),
      )

      expression.entries.forEachIndexed { index, whenEntry ->
        builder.block(blockIndent) {
          if (index != 0) {
            // preserve new line if there's one
            builder.blankLineWanted(OpsBuilder.BlankLineWanted.PRESERVE)
          }
          builder.forcedBreak()
          builder.block(ZERO) {
            if (whenEntry.elseKeyword != null) {
              builder.token("else")
            } else {
              val conditions = whenEntry.conditions
              for ((index, condition) in conditions.withIndex()) {
                visit(condition)
                builder.guessToken(",")
                if (index != conditions.lastIndex) {
                  builder.forcedBreak()
                }
              }
            }
            whenEntry.guard?.let { guard ->
              builder.space()
              emitKeywordWithCondition(
                  "if",
                  guard.getExpression(),
                  surroundConditionWithParens = false,
              )
            }
          }
          val whenExpression = whenEntry.expression
          if (whenEntry.trailingComma != null) {
            builder.forcedBreak()
          } else {
            builder.space()
          }
          builder.token("->")
          if (whenExpression is KtBlockExpression || whenExpression is KtLambdaExpression) {
            builder.space()
            visit(whenExpression)
          } else {
            builder.block(expressionBreakIndent) {
              builder.breakOp(Doc.FillMode.INDEPENDENT, " ", ZERO)
              visit(whenExpression)
            }
          }
          builder.guessToken(";")
        }
        builder.forcedBreak()
      }
      builder.token("}")
    }
  }

  override fun visitClassBody(body: KtClassBody) {
    builder.sync(body)
    emitBracedBlock(body) { children ->
      val enumEntryList = EnumEntryList.extractChildList(body)
      val members = children.filter { it !is KtEnumEntry }

      if (enumEntryList != null) {
        builder.block(ZERO) {
          builder.breakOp(Doc.FillMode.UNIFIED, "", ZERO)
          for (value in enumEntryList.enumEntries) {
            visit(value)
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
        builder.block(ZERO) { visit(curr) }
        markForPartialFormat()
        builder.guessToken(";")
        builder.forcedBreak()

        prev = curr
      }
    }
  }

  override fun visitBlockExpression(expression: KtBlockExpression) {
    builder.sync(expression)
    emitBracedBlock(expression) { children -> formatStatements(children) }
  }

  override fun visitWhenConditionWithExpression(condition: KtWhenConditionWithExpression) {
    builder.sync(condition)
    visit(condition.expression)
  }

  override fun visitWhenConditionIsPattern(condition: KtWhenConditionIsPattern) {
    builder.sync(condition)
    builder.token(if (condition.isNegated) "!is" else "is")
    builder.space()
    visit(condition.typeReference)
  }

  /** Example `in 1..2` as part of a when expression */
  override fun visitWhenConditionInRange(condition: KtWhenConditionInRange) {
    builder.sync(condition)
    // TODO: replace with 'condition.isNegated' once https://youtrack.jetbrains.com/issue/KT-34395
    // is fixed.
    val isNegated = condition.firstChild?.node?.findChildByType(KtTokens.NOT_IN) != null
    builder.token(if (isNegated) "!in" else "in")
    builder.space()
    visit(condition.rangeExpression)
  }

  override fun visitIfExpression(expression: KtIfExpression) {
    builder.sync(expression)
    builder.block(ZERO) {
      emitKeywordWithCondition("if", expression.condition)

      if (expression.then is KtBlockExpression) {
        builder.space()
        builder.block(ZERO) { visit(expression.then) }
      } else {
        builder.breakOp(Doc.FillMode.INDEPENDENT, " ", expressionBreakIndent)
        builder.block(expressionBreakIndent) {
          builder.fenceComments()
          visit(expression.then)
        }
      }

      if (expression.elseKeyword != null) {
        if (expression.then is KtBlockExpression) {
          builder.space()
        } else {
          builder.breakOp(Doc.FillMode.UNIFIED, " ", ZERO)
        }

        builder.block(ZERO) {
          builder.token("else")
          if (expression.`else` is KtBlockExpression || expression.`else` is KtIfExpression) {
            builder.space()
            builder.block(ZERO) { visit(expression.`else`) }
          } else {
            builder.breakOp(Doc.FillMode.INDEPENDENT, " ", expressionBreakIndent)
            builder.block(expressionBreakIndent) { visit(expression.`else`) }
          }
        }
      }
    }
  }

  /** Example `val (a, b: Int) = Pair(1, 2)` or `val [a, b] = Pair(1, 2)` */
  override fun visitDestructuringDeclaration(destructuringDeclaration: KtDestructuringDeclaration) {
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
      builder.block(expressionBreakIndent, !hasTrailingComma) { visit(initializer) }
    }
  }

  /** Example `val a: String` or `x = a` which is part of `(val a: String, x = a)` */
  override fun visitDestructuringDeclarationEntry(
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

  /** Example `for (i in items) { ... }` */
  override fun visitForExpression(expression: KtForExpression) {
    builder.sync(expression)
    builder.block(ZERO) {
      builder.token("for")
      builder.space()
      builder.token("(")
      visit(expression.loopParameter)
      builder.space()
      builder.token("in")
      builder.block(ZERO) {
        builder.breakOp(Doc.FillMode.UNIFIED, " ", expressionBreakIndent)
        builder.block(expressionBreakIndent) { visit(expression.loopRange) }
      }
      builder.token(")")
      builder.space()
      visit(expression.body)
    }
  }

  /** Example `while (a < b) { ... }` */
  override fun visitWhileExpression(expression: KtWhileExpression) {
    builder.sync(expression)
    emitKeywordWithCondition("while", expression.condition)
    builder.space()
    visit(expression.body)
  }

  /** Example `do { ... } while (a < b)` */
  override fun visitDoWhileExpression(expression: KtDoWhileExpression) {
    builder.sync(expression)
    builder.token("do")
    builder.space()
    if (expression.body != null) {
      visit(expression.body)
      builder.space()
    }
    emitKeywordWithCondition("while", expression.condition)
  }

  /** Example `break` or `break@foo` in a loop */
  override fun visitBreakExpression(expression: KtBreakExpression) {
    builder.sync(expression)
    builder.token("break")
    visit(expression.labelQualifier)
  }

  /** Example `continue` or `continue@foo` in a loop */
  override fun visitContinueExpression(expression: KtContinueExpression) {
    builder.sync(expression)
    builder.token("continue")
    visit(expression.labelQualifier)
  }

  /** Example `f: String`, or `private val n: Int` or `(a: Int, b: String)` (in for-loops) */
  override fun visitParameter(parameter: KtParameter) {
    builder.sync(parameter)
    builder.block(ZERO) {
      val destructuringDeclaration = parameter.destructuringDeclaration
      val typeReference = parameter.typeReference
      if (destructuringDeclaration != null) {
        builder.block(ZERO) {
          visit(destructuringDeclaration)
          if (typeReference != null) {
            builder.token(":")
            builder.space()
            visit(typeReference)
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

  override fun visitTryExpression(expression: KtTryExpression) {
    builder.sync(expression)
    builder.token("try")
    builder.space()
    visit(expression.tryBlock)
    for (catchClause in expression.catchClauses) {
      visit(catchClause)
    }
    visit(expression.finallyBlock)
  }

  override fun visitCatchSection(catchClause: KtCatchClause) {
    builder.sync(catchClause)
    builder.space()
    builder.token("catch")
    builder.space()
    builder.block(ZERO) {
      builder.token("(")
      builder.block(expressionBreakIndent) {
        builder.breakOp(Doc.FillMode.UNIFIED, "", ZERO)
        visit(catchClause.catchParameter)
        builder.guessToken(",")
      }
    }
    builder.token(")")
    builder.space()
    visit(catchClause.catchBody)
  }

  override fun visitFinallySection(finallySection: KtFinallySection) {
    builder.sync(finallySection)
    builder.space()
    builder.token("finally")
    builder.space()
    visit(finallySection.finalExpression)
  }

  override fun visitThrowExpression(expression: KtThrowExpression) {
    builder.sync(expression)
    builder.token("throw")
    builder.space()
    visit(expression.thrownExpression)
  }

  /** Example `RED(0xFF0000)` in an enum class */
  override fun visitEnumEntry(enumEntry: KtEnumEntry) {
    builder.sync(enumEntry)
    builder.block(ZERO) {
      visit(enumEntry.modifierList)
      builder.token(enumEntry.nameIdentifier?.text ?: fail())
      enumEntry.initializerList?.initializers?.forEach { visit(it) }
      enumEntry.body?.let { enumBody ->
        builder.space()
        visit(enumBody)
      }
    }
  }

  /** Example `private typealias TextChangedListener = (string: String) -> Unit` */
  override fun visitTypeAlias(typeAlias: KtTypeAlias) {
    builder.sync(typeAlias)
    builder.block(ZERO) {
      visit(typeAlias.modifierList)
      builder.token("typealias")
      builder.space()
      builder.token(typeAlias.nameIdentifier?.text ?: fail())
      visit(typeAlias.typeParameterList)

      builder.space()
      builder.token("=")
      builder.breakOp(Doc.FillMode.INDEPENDENT, " ", expressionBreakIndent)
      builder.block(expressionBreakIndent) {
        visit(typeAlias.getTypeReference())
        visit(typeAlias.typeConstraintList)
        builder.guessToken(";")
      }
      builder.forcedBreak()
    }
  }

  /**
   * visitElement is called for almost all types of AST nodes. We use it to keep track of whether
   * we're currently inside an expression or not.
   *
   * @throws FormattingError
   */
  override fun visitElement(element: PsiElement) {
    inExpression.addLast(element is KtExpression || inExpression.last())
    val previous = builder.depth()
    try {
      super.visitElement(element)
    } catch (e: FormattingError) {
      throw e
    } catch (t: Throwable) {
      throw FormattingError(builder.diagnostic(Throwables.getStackTraceAsString(t)))
    } finally {
      inExpression.removeLast()
    }
    builder.checkClosed(previous)
  }

  /** Helper function to improve readability */
  private fun visit(element: PsiElement?) {
    element?.accept(this)
  }

  /**
   * Emits a key word followed by a condition, e.g. `if (b)` or `while (c < d )`
   *
   * @param surroundConditionWithParens a flag to control whether parens surrounds the condition.
   *   For example, guard conditions do not use parens.
   */
  private fun emitKeywordWithCondition(
      keyword: String,
      condition: KtExpression?,
      surroundConditionWithParens: Boolean = true,
  ) {
    if (condition == null) {
      builder.token(keyword)
      return
    }

    builder.block(ZERO) {
      builder.token(keyword)
      builder.space()
      if (surroundConditionWithParens) {
        builder.token("(")
      }
      if (options.manageTrailingCommas) {
        builder.block(expressionBreakIndent) {
          builder.breakOp(Doc.FillMode.UNIFIED, "", ZERO)
          visit(condition)
          builder.breakOp(Doc.FillMode.UNIFIED, "", -expressionBreakIndent)
        }
      } else {
        builder.block(ZERO) { visit(condition) }
      }
    }
    if (surroundConditionWithParens) {
      builder.token(")")
    }
  }
}
