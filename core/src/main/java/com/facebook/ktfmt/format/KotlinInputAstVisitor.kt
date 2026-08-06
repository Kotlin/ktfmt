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

package com.facebook.ktfmt.format

import com.facebook.ktfmt.util.CONTEXT_PARAMETER_LIST
import com.facebook.ktfmt.util.listToVisit
import com.facebook.ktfmt.util.ownValOrVarKeywordText
import com.google.common.base.Throwables
import com.google.common.collect.ImmutableList
import com.google.googlejavaformat.Doc
import com.google.googlejavaformat.FormattingError
import com.google.googlejavaformat.Indent
import com.google.googlejavaformat.Indent.Const.ZERO
import com.google.googlejavaformat.OpsBuilder
import com.google.googlejavaformat.OpsBuilder.BlankLineWanted
import com.google.googlejavaformat.Output.BreakTag
import java.util.ArrayDeque
import java.util.Optional
import kotlin.jvm.optionals.getOrNull
import org.jetbrains.kotlin.com.intellij.psi.PsiComment
import org.jetbrains.kotlin.com.intellij.psi.PsiElement
import org.jetbrains.kotlin.com.intellij.psi.PsiWhiteSpace
import org.jetbrains.kotlin.lexer.KtModifierKeywordToken
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtAnnotatedExpression
import org.jetbrains.kotlin.psi.KtAnnotation
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtAnnotationUseSiteTarget
import org.jetbrains.kotlin.psi.KtArrayAccessExpression
import org.jetbrains.kotlin.psi.KtBackingField
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtBinaryExpressionWithTypeRHS
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtBreakExpression
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtCallableReferenceExpression
import org.jetbrains.kotlin.psi.KtCatchClause
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtClassBody
import org.jetbrains.kotlin.psi.KtClassInitializer
import org.jetbrains.kotlin.psi.KtClassLiteralExpression
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtCollectionLiteralExpression
import org.jetbrains.kotlin.psi.KtConstantExpression
import org.jetbrains.kotlin.psi.KtConstructorDelegationCall
import org.jetbrains.kotlin.psi.KtContainerNode
import org.jetbrains.kotlin.psi.KtContextReceiverList
import org.jetbrains.kotlin.psi.KtContinueExpression
import org.jetbrains.kotlin.psi.KtDelegatedSuperTypeEntry
import org.jetbrains.kotlin.psi.KtDestructuringDeclaration
import org.jetbrains.kotlin.psi.KtDestructuringDeclarationEntry
import org.jetbrains.kotlin.psi.KtDoWhileExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtDynamicType
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtEnumEntry
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtFileAnnotationList
import org.jetbrains.kotlin.psi.KtFinallySection
import org.jetbrains.kotlin.psi.KtForExpression
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtFunctionType
import org.jetbrains.kotlin.psi.KtIfExpression
import org.jetbrains.kotlin.psi.KtImportDirective
import org.jetbrains.kotlin.psi.KtImportList
import org.jetbrains.kotlin.psi.KtIntersectionType
import org.jetbrains.kotlin.psi.KtIsExpression
import org.jetbrains.kotlin.psi.KtLabelReferenceExpression
import org.jetbrains.kotlin.psi.KtLabeledExpression
import org.jetbrains.kotlin.psi.KtLambdaArgument
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtModifierList
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtNullableType
import org.jetbrains.kotlin.psi.KtObjectLiteralExpression
import org.jetbrains.kotlin.psi.KtPackageDirective
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtParameterList
import org.jetbrains.kotlin.psi.KtParenthesizedExpression
import org.jetbrains.kotlin.psi.KtPostfixExpression
import org.jetbrains.kotlin.psi.KtPrefixExpression
import org.jetbrains.kotlin.psi.KtPrimaryConstructor
import org.jetbrains.kotlin.psi.KtProjectionKind
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtPropertyAccessor
import org.jetbrains.kotlin.psi.KtPropertyDelegate
import org.jetbrains.kotlin.psi.KtQualifiedExpression
import org.jetbrains.kotlin.psi.KtReferenceExpression
import org.jetbrains.kotlin.psi.KtReturnExpression
import org.jetbrains.kotlin.psi.KtScript
import org.jetbrains.kotlin.psi.KtScriptInitializer
import org.jetbrains.kotlin.psi.KtSecondaryConstructor
import org.jetbrains.kotlin.psi.KtSimpleNameExpression
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.psi.KtSuperExpression
import org.jetbrains.kotlin.psi.KtSuperTypeCallEntry
import org.jetbrains.kotlin.psi.KtSuperTypeList
import org.jetbrains.kotlin.psi.KtThisExpression
import org.jetbrains.kotlin.psi.KtThrowExpression
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid
import org.jetbrains.kotlin.psi.KtTryExpression
import org.jetbrains.kotlin.psi.KtTypeAlias
import org.jetbrains.kotlin.psi.KtTypeArgumentList
import org.jetbrains.kotlin.psi.KtTypeConstraint
import org.jetbrains.kotlin.psi.KtTypeConstraintList
import org.jetbrains.kotlin.psi.KtTypeParameter
import org.jetbrains.kotlin.psi.KtTypeParameterList
import org.jetbrains.kotlin.psi.KtTypeProjection
import org.jetbrains.kotlin.psi.KtTypeReference
import org.jetbrains.kotlin.psi.KtUserType
import org.jetbrains.kotlin.psi.KtValueArgument
import org.jetbrains.kotlin.psi.KtValueArgumentList
import org.jetbrains.kotlin.psi.KtWhenConditionInRange
import org.jetbrains.kotlin.psi.KtWhenConditionIsPattern
import org.jetbrains.kotlin.psi.KtWhenConditionWithExpression
import org.jetbrains.kotlin.psi.KtWhenEntry
import org.jetbrains.kotlin.psi.KtWhenExpression
import org.jetbrains.kotlin.psi.KtWhileExpression
import org.jetbrains.kotlin.psi.psiUtil.children
import org.jetbrains.kotlin.psi.psiUtil.startOffset
import org.jetbrains.kotlin.psi.psiUtil.startsWithComment

/** An AST visitor that builds a stream of {@link Op}s to format. */
open class KotlinInputAstVisitor(
    private val options: FormattingOptions,
    private val builder: OpsBuilder,
) : KtTreeVisitorVoid() {

  internal open val forceAnnotationBreaks: Boolean = false
  internal open val forceLineBreakAfterAssignment: Boolean = true
  internal open val forceLineBreakAfterNamedParameter: Boolean = true
  internal open val forceLineBreakAfterSupertypeColon: Boolean = true
  internal open val forceLineBreakBeforeAccessors: Boolean = true
  internal open val hugBlockLikeInfixCalls: Boolean = false
  internal open val hugCallsWithTrailingLambda: Boolean = false
  internal open val hugChainsAfterTrailingLambda: Boolean = false
  internal open val hugWhenExpressions: Boolean = false
  internal open val indentBooleanConditions: Boolean = true
  internal open val forceLineBreakInWhenConditionList: Boolean = true
  internal open val forceLineBreaksBetweenEmptyMethods: Boolean = true

  /** Standard indentation for a block */
  private val blockIndent: Indent.Const = Indent.Const.make(options.blockIndent, 1)

  /**
   * Standard indentation for a long expression or function call, it is different than block
   * indentation on purpose
   */
  private val expressionBreakIndent: Indent.Const = Indent.Const.make(options.continuationIndent, 1)

  private val blockPlusExpressionBreakIndent: Indent.Const =
      Indent.Const.make(options.blockIndent + options.continuationIndent, 1)

  private val doubleExpressionBreakIndent: Indent.Const =
      Indent.Const.make(options.continuationIndent, 2)

  private val expressionBreakNegativeIndent: Indent.Const =
      Indent.Const.make(-options.continuationIndent, 1)

  /** A record of whether we have visited into an expression. */
  private val inExpression = ArrayDeque(ImmutableList.of(false))

  /** Tracks whether we are handling an import directive */
  private var inImport = false

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

  /** Emits a type together with the parentheses around it, by walking [element]'s children. */
  private fun emitParenthesizedType(
      element: KtElement,
      modifierList: KtModifierList?,
      innerType: PsiElement?,
  ) {
    for (child in element.node.children()) {
      when {
        child.psi == modifierList -> visit(modifierList)
        child.psi == innerType -> visit(innerType)
        child.elementType == KtTokens.LPAR -> builder.token("(")
        child.elementType == KtTokens.RPAR -> builder.token(")")
      }
    }
  }

  /** Example `Int`, `(String)` or `() -> Int` */
  override fun visitTypeReference(typeReference: KtTypeReference) {
    builder.sync(typeReference)
    emitParenthesizedType(typeReference, typeReference.modifierList, typeReference.typeElement)
  }

  override fun visitDynamicType(type: KtDynamicType) {
    builder.token("dynamic")
  }

  /** Example: `String?` or `((Int) -> Unit)?` */
  override fun visitNullableType(nullableType: KtNullableType) {
    builder.sync(nullableType)
    emitParenthesizedType(nullableType, nullableType.modifierList, nullableType.innerType)
    builder.token("?")
  }

  /** Example: `String` or `List<Int>`, */
  override fun visitUserType(type: KtUserType) {
    builder.sync(type)

    if (type.qualifier != null) {
      visit(type.qualifier)
      builder.token(".")
    }
    visit(type.referenceExpression)
    val typeArgumentList = type.typeArgumentList
    if (typeArgumentList != null) {
      builder.block(expressionBreakIndent) { visit(typeArgumentList) }
    }
  }

  /** Example: `A & B`, */
  override fun visitIntersectionType(type: KtIntersectionType) {
    builder.sync(type)

    // TODO(strulovich): Should this have the same indentation behaviour as `x && y`?
    visit(type.getLeftTypeRef())
    builder.spacedToken("&")
    visit(type.getRightTypeRef())
  }

  /** Example `<Int, String>` in `List<Int, String>` */
  override fun visitTypeArgumentList(typeArgumentList: KtTypeArgumentList) {
    builder.sync(typeArgumentList)
    visitEachCommaSeparated(
        typeArgumentList.arguments,
        typeArgumentList.trailingComma != null,
        wrapInBlock = !options.manageTrailingCommas,
        prefix = "<",
        postfix = ">",
    )
  }

  override fun visitTypeProjection(typeProjection: KtTypeProjection) {
    builder.sync(typeProjection)
    val typeReference = typeProjection.typeReference
    when (typeProjection.projectionKind) {
      KtProjectionKind.IN -> {
        builder.tokenThenSpace("in")
        visit(typeReference)
      }
      KtProjectionKind.OUT -> {
        builder.tokenThenSpace("out")
        visit(typeReference)
      }
      KtProjectionKind.STAR -> builder.token("*")
      KtProjectionKind.NONE -> visit(typeReference)
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
          builder.breakToFill(expressionBreakIndent)
          builder.token(".")
        }
        if (name != null) {
          builder.token(name)
        }
      }

      if (parameterList != null && parameterList.hasEmptyParens()) {
        builder.block(ZERO) {
          builder.token("(")
          builder.token(")")
          emitTypeOrDelegationCall {
            builder.breakToFillThenBlock(" ", expressionBreakIndent) { visit(typeOrDelegationCall) }
          }
        }
      } else {
        builder.block(expressionBreakIndent) {
          if (parameterList != null) {
            visitEachCommaSeparated(
                list = parameterList.parameters,
                hasTrailingComma = parameterList.trailingComma != null,
                prefix = "(",
                postfix = ")",
                wrapInBlock = false,
                breakBeforePostfix = true,
            )
          }
          emitTypeOrDelegationCall {
            builder.space()
            builder.block(expressionBreakNegativeIndent) { visit(typeOrDelegationCall) }
          }
        }
      }

      visit(typeConstraintList)
      if (bodyExpression is KtBlockExpression) {
        builder.space()
        visit(bodyExpression)
      } else if (bodyExpression != null) {
        builder.space()
        builder.block(ZERO) {
          builder.token("=")
          if (
              !emitExpressionAfterOperator(bodyExpression) &&
                  !emitChainAfterOperator(bodyExpression)
          ) {
            builder.block(expressionBreakIndent) {
              builder.breakToFill(" ")
              builder.block(ZERO) { visit(bodyExpression) }
            }
          }
        }
      }
      builder.guessSemicolon()
    }
    if (forceTrailingBreak) {
      builder.forcedBreak()
    }
  }

  private fun emitBracedBlock(
      bodyBlockExpression: PsiElement,
      emitChildren: (Array<PsiElement>) -> Unit,
  ) {
    builder.token("{", Doc.Token.RealOrImaginary.REAL, blockIndent, Optional.of(blockIndent))
    val statements = bodyBlockExpression.children
    if (statements.isNotEmpty()) {
      builder.block(blockIndent) {
        builder.forcedBreak()
        builder.blankLineWanted(BlankLineWanted.PRESERVE)
        emitChildren(statements)
      }
      builder.forcedBreak()
      builder.blankLineWanted(BlankLineWanted.NO)
    }
    builder.token("}", blockIndent)
  }

  private fun visitStatement(statement: PsiElement) {
    builder.block(ZERO) { visit(statement) }
    builder.guessSemicolon()
  }

  private fun visitStatements(statements: Array<PsiElement>) {
    builder.guessSemicolon()
    for ((index, statement) in statements.withIndex()) {
      builder.forcedBreak()
      if (index > 0) {
        builder.blankLineWanted(BlankLineWanted.PRESERVE)
      }
      markForPartialFormat()
      visitStatement(statement)
      markForPartialFormat()
    }
  }

  override fun visitProperty(property: KtProperty) {
    builder.sync(property)
    builder.block(ZERO) {
      emitVariableLikeDeclaration(
          isField = true,
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
    builder.guessSemicolon()
    if (property.parent !is KtWhenExpression) {
      builder.forcedBreak()
    }
  }

  /**
   * Example: "com.facebook.bla.bla" in imports or "a.b.c.d" in expressions.
   *
   * There's a few cases that are different. We deal with imports by keeping them on the same line.
   * For regular chained expressions we go the left most descendant so we can start indentation only
   * before the first break (a `.` or `?.`), and keep the seem indentation for this chain of calls.
   */
  override fun visitQualifiedExpression(expression: KtQualifiedExpression) {
    builder.sync(expression)
    val receiver = expression.receiverExpression
    when {
      inImport -> {
        visit(receiver)
        val selectorExpression = expression.selectorExpression
        if (selectorExpression != null) {
          builder.token(".")
          visit(selectorExpression)
        }
      }
      receiver is KtStringTemplateExpression -> {
        builder.block(expressionBreakIndent) {
          visit(receiver)
          builder.breakOp()
          builder.token(expression.operationSign.value)
          visit(expression.selectorExpression)
        }
      }
      receiver is KtWhenExpression -> {
        builder.block(ZERO) {
          visit(receiver)
          builder.token(expression.operationSign.value)
          visit(expression.selectorExpression)
        }
      }
      expression.isChainedScopingFunction &&
          expression.chainRoot.isMultilineScopingFunction &&
          !chainedSelectorsHaveValueArguments(expression) -> {
        visitChainedScopingFunction(expression, emitLeadingBreak = false)
      }
      expression.isChainedBlockLikeCall -> {
        visitChainedBlockLikeCall(expression, emitLeadingBreak = false)
      }
      hugChainsAfterTrailingLambda &&
          visitChainAfterTrailingLambda(expression, emitLeadingBreak = false) -> Unit
      else -> {
        emitQualifiedExpression(expression)
      }
    }
  }

  /** Extra data to help [emitQualifiedExpression] know when to open and close a group */
  private class GroupingInfo {
    var groupOpenCount = 0
    var shouldCloseGroup = false
  }

  /**
   * A chain of qualified expressions, decomposed into everything needed to lay it out.
   *
   * @param useBlockLikeLambdaStyle whether we want to make a lambda look like a block, this makes
   *   Kotlin DSLs look as expected
   */
  private data class ChainLayout(
      val parts: List<KtExpression>,
      val useBlockLikeLambdaStyle: Boolean,
      val groupingInfos: List<GroupingInfo>,
  )

  private fun chainLayout(expression: KtExpression): ChainLayout {
    val parts = breakIntoParts(expression)
    val useBlockLikeLambdaStyle = parts.last().isLambda() && parts.count { it.isLambda() } == 1
    return ChainLayout(
        parts,
        useBlockLikeLambdaStyle,
        computeGroupingInfo(parts, useBlockLikeLambdaStyle),
    )
  }

  /**
   * Handles a chain of qualified expressions, i.e. `a[5].b!!.c()[4].f()`
   *
   * This is by far the most complicated part of this formatter. We start by breaking the expression
   * to the steps it is executed in (which are in the opposite order of how the syntax tree is
   * built).
   *
   * We then calculate information to know which parts need to be groups, and finally go part by
   * part, emitting it to the [builder] while closing and opening groups.
   */
  private fun emitQualifiedExpression(expression: KtExpression) {
    val chain = chainLayout(expression)
    builder.block(expressionBreakIndent) {
      emitQualifiedExpressionParts(chain, range = chain.parts.indices, nameTag = BreakTag())
    }
  }

  /**
   * Lays out expression that follows an operator such as `=` in two parts, deciding whether to
   * break after an operator, of after the head of the expression.
   *
   * @param [emitHead] emits a head of the operator, i.e. something that can fit into the same line
   *   as the operator; e.g. `when (a) {`, `if (cond) {`, etc.
   * @param emitTail emits the rest, given the indents to lay it out at. A tail that aligns with the
   *   head -- the body of a `when` -- uses `headIndent`; one that continues the head's line -- an
   *   argument list -- uses `tailIndent`, or `headIndent` plus a level of its own.
   */
  private fun emitSplitAfterOperator(
      emitHead: () -> Unit,
      emitTail: (headIndent: Indent, tailIndent: Indent) -> Unit,
  ) {
    val brokeAfterOperator = BreakTag()
    val headIndent = Indent.If.make(brokeAfterOperator, expressionBreakIndent, ZERO)
    val tailIndent =
        Indent.If.make(brokeAfterOperator, doubleExpressionBreakIndent, expressionBreakIndent)
    builder.block(expressionBreakIndent) {
      builder.breakOp(" ", ZERO, Optional.of(brokeAfterOperator))
      builder.block(headIndent) { emitHead() }
    }
    emitTail(headIndent, tailIndent)
  }

  /**
   * Lays out a chain of qualified expressions that follows an operator such as `=`, deciding
   * whether to break after that operator from the width of the chain's receiver alone.
   */
  private fun emitQualifiedExpressionAfterOperator(expression: KtExpression): Boolean {
    val chain = chainLayout(expression)
    val receiverEnd = receiverSegmentEnd(chain) ?: return false
    val lastIndex = chain.parts.lastIndex
    val nameTag = BreakTag() // allows adjusting arguments indentation if a break will be made

    emitSplitAfterOperator(
        emitHead = {
          emitQualifiedExpressionParts(chain, range = 0..receiverEnd, nameTag = nameTag)
        },
        emitTail = { _, selectorsIndent ->
          if (receiverEnd < lastIndex) {
            builder.block(selectorsIndent) {
              emitQualifiedExpressionParts(
                  chain,
                  range = receiverEnd + 1..lastIndex,
                  nameTag = nameTag,
              )
            }
          }
        },
    )
    return true
  }

  /**
   * Lays out a call that follows an operator such as the `=` of a named argument, deciding whether
   * to break after that operator from the width of the callee alone.
   */
  private fun emitCallAfterOperator(expression: KtExpression?): Boolean {
    if (expression !is KtCallExpression) return false
    // A leading comment brings its own forced break, which throws off the indents below.
    if (expression.hasLeadingComment) return false
    return emitCallAfterOperator(
        expression.calleeExpression,
        expression.typeArgumentList,
        expression.valueArgumentList,
        expression.lambdaArguments,
    )
  }

  /**
   * The parts-wise version of [emitCallAfterOperator], for callers that aren't a [KtExpression].
   */
  private fun emitCallAfterOperator(
      callee: KtExpression?,
      typeArgumentList: KtTypeArgumentList?,
      argumentList: KtValueArgumentList?,
      lambdaArguments: List<KtLambdaArgument>,
  ): Boolean {
    if (!canEmitCallAfterOperator(callee, argumentList, lambdaArguments)) return false
    callee as KtExpression
    argumentList as KtValueArgumentList

    emitSplitAfterOperator(
        emitHead = { visit(callee) },
        emitTail = { _, argumentsIndent ->
          builder.block(argumentsIndent) {
            builder.block(ZERO) { visit(typeArgumentList) }
            visitValueArgumentListInternal(argumentList)
          }
        },
    )
    return true
  }

  /**
   * Whether [emitCallAfterOperator] can lay out these call parts, so that callers can tell before
   * they [OpsBuilder.sync] past anything.
   */
  private fun canEmitCallAfterOperator(
      callee: KtExpression?,
      argumentList: KtValueArgumentList?,
      lambdaArguments: List<KtLambdaArgument>,
  ): Boolean {
    // A trailing lambda is laid out by visitCallElement, which indents the callee along with it.
    if (lambdaArguments.isNotEmpty()) return false
    if (callee == null) return false
    // Without arguments there is nothing to break at, so keeping the callee here buys nothing.
    return argumentList != null && !argumentList.hasEmptyParens()
  }

  /**
   * Lays out a supertype list whose first entry is a constructor call on the class header line, so
   * that only the call's arguments break and any remaining supertypes trail its closing paren.
   */
  private fun emitSuperTypeCallAfterColon(list: KtSuperTypeList): Boolean {
    if (forceLineBreakAfterSupertypeColon) return false
    val entries = list.entries
    val call = entries.firstOrNull() as? KtSuperTypeCallEntry ?: return false
    // A leading comment brings its own forced break, which throws off the indents below.
    if (call.hasLeadingComment) return false
    if (
        !canEmitCallAfterOperator(
            call.calleeExpression,
            call.valueArgumentList,
            call.lambdaArguments,
        )
    ) {
      return false
    }

    builder.sync(list)
    builder.sync(call)
    // The type arguments are part of the constructor callee, as in visitSuperTypeCallEntry.
    emitCallAfterOperator(call.calleeExpression, null, call.valueArgumentList, call.lambdaArguments)

    // The remaining supertypes trail the call's closing paren, sharing its line when they fit and
    // otherwise taking one line each.
    if (entries.size > 1) {
      builder.block(expressionBreakIndent) {
        for (entry in entries.drop(1)) {
          builder.token(",")
          builder.breakOp(" ")
          visit(entry)
        }
      }
    }
    return true
  }

  /**
   * The index of the last part of the chain's receiver -- the head that is kept together, such as
   * `a.b[2]` in `a.b[2].c.d()` -- or null when every group opened in the chain is still open by the
   * time the last part is emitted, so there is no point at which the chain can be split in two.
   */
  private fun receiverSegmentEnd(chain: ChainLayout): Int? {
    val (parts, useBlockLikeLambdaStyle, groupingInfos) = chain
    // The group of a block-like trailing lambda is opened at the root and closed at the very last
    // part, so it always spans the whole chain.
    if (useBlockLikeLambdaStyle) return null

    var openGroups = 0
    for ((index, part) in parts.withIndex()) {
      openGroups += groupingInfos[index].groupOpenCount
      if (groupingInfos[index].shouldCloseGroup) openGroups--
      if (part is KtArrayAccessExpression || part is KtPostfixExpression) openGroups--
      if (openGroups == 0) return index
    }
    return null
  }

  /** Emits [range] of the parts of a chain, see [emitQualifiedExpression]. */
  private fun emitQualifiedExpressionParts(
      chain: ChainLayout,
      range: IntRange,
      nameTag: BreakTag,
  ) {
    val (parts, useBlockLikeLambdaStyle, groupingInfos) = chain
    for (index in range) {
      val ktExpression = parts[index]
      if (ktExpression is KtQualifiedExpression) {
        builder.breakOp("", ZERO, Optional.of(nameTag))
      }
      repeat(groupingInfos[index].groupOpenCount) { builder.open(ZERO) }
      when (ktExpression) {
        is KtQualifiedExpression -> {
          builder.token(ktExpression.operationSign.value)
          val selectorExpression = ktExpression.selectorExpression
          if (selectorExpression !is KtCallExpression) {
            // selector is a simple field access
            visit(selectorExpression)
            if (groupingInfos[index].shouldCloseGroup) {
              builder.close()
            }
          } else {
            // selector is a function call, we may close a group after its name
            // emit `doIt` from `doIt(1, 2) { it }`
            visit(selectorExpression.calleeExpression)
            // close groups according to instructions
            if (groupingInfos[index].shouldCloseGroup) {
              builder.close()
            }
            // close group due to last lambda to allow block-like style in `as.forEach { ... }`
            val isTrailingLambda = useBlockLikeLambdaStyle && index == parts.size - 1
            if (isTrailingLambda) {
              builder.close()
            }
            // A block-like (exploded) selector call is laid out like the last part: its
            // arguments are indented once relative to the call itself, and its closing paren
            // returns to the call's indent, even when chained selectors follow it. This only
            // applies when trailing commas are preserved (the block-like style); when ktfmt
            // manages trailing commas, exploded chained calls keep the regular extra indent.
            val isLastPartOrBlockLikeCall =
                index == parts.size - 1 ||
                    !options.manageTrailingCommas && selectorExpression.isBlockLikeCall
            val argsIndentElse = if (isLastPartOrBlockLikeCall) ZERO else expressionBreakIndent
            val lambdaIndentElse = if (isTrailingLambda) expressionBreakNegativeIndent else ZERO
            val negativeLambdaIndentElse = if (isTrailingLambda) expressionBreakIndent else ZERO

            // emit `(1, 2) { it }` from `doIt(1, 2) { it }`
            visitCallElement(
                null,
                selectorExpression.typeArgumentList,
                selectorExpression.valueArgumentList,
                selectorExpression.lambdaArguments,
                argumentsIndent = Indent.If.make(nameTag, expressionBreakIndent, argsIndentElse),
                lambdaIndent = Indent.If.make(nameTag, ZERO, lambdaIndentElse),
                negativeLambdaIndent = Indent.If.make(nameTag, ZERO, negativeLambdaIndentElse),
            )
          }
        }
        is KtArrayAccessExpression -> {
          visitArrayAccessBrackets(ktExpression)
          builder.close()
        }
        is KtPostfixExpression -> {
          builder.token(ktExpression.operationReference.text)
          builder.close()
        }
        else -> {
          check(index == 0)
          visit(ktExpression)
        }
      }
    }
  }

  /**
   * Generates the [GroupingInfo] array to go with an array of [KtQualifiedExpression] parts
   *
   * For example, the expression `a.b[2].c.d()` is made of four expressions:
   * 1. [KtQualifiedExpression] `a.b[2].c . d()` (this will be `parts[4]`)
   * 1. [KtQualifiedExpression] `a.b[2] . c` (this will be `parts[3]`)
   * 2. [KtArrayAccessExpression] `a.b [2]` (this will be `parts[2]`)
   * 3. [KtQualifiedExpression] `a . b` (this will be `parts[1]`)
   * 4. [KtSimpleNameExpression] `a` (this will be `parts[0]`)
   *
   * Once in parts, these are in the reverse order. To render the array correct we need to make sure
   * `b` and [2] are in a group so we avoid splitting them. To do so we need to open a group for `b`
   * (that will be done in part 2), and always close a group for an array.
   *
   * Here is the same expression, with justified braces marking the groupings it will get:
   * ```
   *  a . b [2] . c . d ()
   * {a . b} --> Grouping `a.b` because it can be a package name or simple field access so we add 1
   *             to the number of groups to open at groupingInfos[0], and mark to close a group at
   *             groupingInfos[1]
   * {a . b [2]} --> Grouping `a.b` with `[2]`, since otherwise we may break inside the brackets
   *                 instead of preferring breaks before dots. So we open a group at [0], but since
   *                 we always close a group after brackets, we don't store that information.
   *             {c . d} --> another group to attach the first function name to the fields before it
   *                         this time we don't start the group in the beginning, and use
   *                         lastIndexToOpen to track the spot after the last time we stopped
   *                         grouping.
   * ```
   *
   * The final expression with groupings:
   * ```
   * {{a.b}[2]}.{c.d}()
   * ```
   */
  private fun computeGroupingInfo(
      parts: List<KtExpression>,
      useBlockLikeLambdaStyle: Boolean,
  ): List<GroupingInfo> {
    val groupingInfos = List(parts.size) { GroupingInfo() }
    var lastIndexToOpen = 0
    for ((index, part) in parts.withIndex()) {
      when (part) {
        is KtQualifiedExpression -> {
          val receiverExpression = part.receiverExpression
          val previous =
              (receiverExpression as? KtQualifiedExpression)?.selectorExpression
                  ?: receiverExpression
          val current = checkNotNull(part.selectorExpression)
          if (
              lastIndexToOpen == 0 &&
                  shouldGroupPartWithPrevious(parts, part, index, previous, current)
          ) {
            // this and the previous items should be grouped for better style
            // we add another group to open in index 0
            groupingInfos[0].groupOpenCount++
            // we don't always close a group when emitting this node, so we need this flag to
            // mark if we need to close a group
            groupingInfos[index].shouldCloseGroup = true
          } else {
            // use this index in to open future groups for arrays and postfixes
            // we will also stop grouping field access to the beginning of the expression
            lastIndexToOpen = index
          }
        }
        is KtArrayAccessExpression,
        is KtPostfixExpression -> {
          // we group these with the last item with a name, and we always close them
          groupingInfos[lastIndexToOpen].groupOpenCount++
        }
      }
    }
    if (useBlockLikeLambdaStyle) {
      // a trailing lambda adds a group that we stop before emitting the lambda
      groupingInfos[0].groupOpenCount++
    }
    return groupingInfos
  }

  /** Decide whether a [KtQualifiedExpression] part should be grouped with the previous part */
  private fun shouldGroupPartWithPrevious(
      parts: List<KtExpression>,
      part: KtExpression,
      index: Int,
      previous: KtExpression,
      current: KtExpression,
  ): Boolean {
    // this is the second, and the first is short, avoid `.` "hanging in air"
    if (index == 1 && previous.text.length < options.continuationIndent) {
      return true
    }
    // the previous part is `this` or `super`
    if (previous is KtSuperExpression || previous is KtThisExpression) {
      return true
    }
    // this and the previous part are a package name, type name, or property
    if (
        previous is KtSimpleNameExpression &&
            current is KtSimpleNameExpression &&
            part is KtDotQualifiedExpression
    ) {
      return true
    }
    // this is `Foo` in `com.facebook.Foo`, so everything before it is a package name
    if (
        current.text.first().isUpperCase() &&
            current is KtSimpleNameExpression &&
            part is KtDotQualifiedExpression
    ) {
      return true
    }
    // this is the `foo()` in `com.facebook.Foo.foo()` or in `Foo.foo()`
    if (
        current is KtCallExpression &&
            (previous !is KtCallExpression) &&
            previous.text?.firstOrNull()?.isUpperCase() == true
    ) {
      return true
    }
    // this is an invocation and the last item, and the previous it not, i.e. `a.b.c()`
    // keeping it grouped and splitting the arguments makes `a.b(...)` feel like `aab()`
    return current is KtCallExpression &&
        previous !is KtCallExpression &&
        index == parts.indices.last
  }

  override fun visitCallExpression(callExpression: KtCallExpression) {
    builder.sync(callExpression)
    with(callExpression) {
      visitCallElement(
          calleeExpression,
          typeArgumentList,
          valueArgumentList,
          lambdaArguments,
      )
    }
  }

  /**
   * Examples `foo<T>(a, b)`, `foo(a)`, `boo()`, `super(a)`
   *
   * @param lambdaIndent how to indent [lambdaArguments], if present
   * @param negativeLambdaIndent the negative indentation of [lambdaIndent]
   */
  private fun visitCallElement(
      callee: KtExpression?,
      typeArgumentList: KtTypeArgumentList?,
      argumentList: KtValueArgumentList?,
      lambdaArguments: List<KtLambdaArgument>,
      argumentsIndent: Indent = expressionBreakIndent,
      lambdaIndent: Indent = ZERO,
      negativeLambdaIndent: Indent = ZERO,
  ) {
    // Apply the lambda indent to the callee, type args, value args, and the lambda.
    // This is undone for the first three by the negative lambda indent.
    // This way they're in one block, and breaks in the argument list cause a break in the lambda.
    builder.block(lambdaIndent) {

      // Used to keep track of whether or not we need to indent the lambda
      // This is based on if there is a break in the argument list
      var brokeBeforeBrace: BreakTag? = null

      builder.block(negativeLambdaIndent) {
        visit(callee)
        builder.block(argumentsIndent) {
          builder.block(ZERO) { visit(typeArgumentList) }
          if (argumentList != null) {
            brokeBeforeBrace = visitValueArgumentListInternal(argumentList)
          }
        }
      }
      when (lambdaArguments.size) {
        0 -> {}
        1 -> {
          builder.space()
          visitArgumentInternal(
              lambdaArguments.single(),
              wrapInBlock = false,
              brokeBeforeBrace = brokeBeforeBrace,
          )
        }
        else -> throw ParseError("Maximum one trailing lambda is allowed", lambdaArguments[1])
      }
    }
  }

  /** Example (`1, "hi"`) in a function call */
  override fun visitValueArgumentList(list: KtValueArgumentList) {
    visitValueArgumentListInternal(list)
  }

  /**
   * Example (`1, "hi"`) in a function call
   *
   * @return a [BreakTag] which can tell you if a break was taken, but only when the list doesn't
   *   terminate in a negative closing indent. See [visitEachCommaSeparated] for examples.
   */
  private fun visitValueArgumentListInternal(list: KtValueArgumentList): BreakTag? {
    builder.sync(list)

    val arguments = list.arguments
    val isSingleUnnamedLambda =
        arguments.size == 1 &&
            arguments.first().getArgumentExpression() is KtLambdaExpression &&
            arguments.first().getArgumentName() == null
    val hasTrailingComma = list.trailingComma != null
    val hasEmptyParens = list.hasEmptyParens()

    val wrapInBlock: Boolean
    val breakBeforePostfix: Boolean
    val leadingBreak: Boolean
    val breakAfterPrefix: Boolean
    if (isSingleUnnamedLambda) {
      wrapInBlock = true
      breakBeforePostfix = false
      // The lambda itself sits between the parens, so they are never empty here.
      leadingBreak = hasTrailingComma
      breakAfterPrefix = false
    } else {
      // A call without a trailing comma that is nonetheless forced onto multiple lines (because one
      // of its arguments is itself a block-like multiline call) is rendered "exploded", with its
      // closing parenthesis on its own line, just like a call with a trailing comma.
      val contentForcesMultiline =
          !hasTrailingComma &&
              arguments.any { argument ->
                val argumentExpression = argument.getArgumentExpression()
                argumentExpression != null &&
                    (argumentExpression.isBlockLikeCall ||
                        argumentExpression.isChainedBlockLikeCall)
              }
      wrapInBlock = !options.manageTrailingCommas
      breakBeforePostfix =
          (options.manageTrailingCommas || contentForcesMultiline) && !hasEmptyParens
      leadingBreak = !hasEmptyParens
      breakAfterPrefix = !hasEmptyParens
    }

    return visitEachCommaSeparated(
        arguments,
        hasTrailingComma,
        wrapInBlock = wrapInBlock,
        breakBeforePostfix = breakBeforePostfix,
        leadingBreak = leadingBreak,
        prefix = "(",
        postfix = ")",
        breakAfterPrefix = breakAfterPrefix,
    )
  }

  /** Example `{ 1 + 1 }` (as lambda) or `{ (x, y) -> x + y }` */
  override fun visitLambdaExpression(lambdaExpression: KtLambdaExpression) {
    visitLambdaExpressionInternal(lambdaExpression, brokeBeforeBrace = null)
  }

  /**
   * The internal version of [visitLambdaExpression].
   *
   * @param brokeBeforeBrace used for tracking if a break was taken right before the lambda
   *   expression. Useful for scoping functions where we want good looking indentation. For example,
   *   here we have correct indentation before `bar()` and `car()` because we can detect the break
   *   after the equals:
   * ```
   * fun foo() =
   *     coroutineScope { x ->
   *       bar()
   *       car()
   *     }
   * ```
   */
  private fun visitLambdaExpressionInternal(
      lambdaExpression: KtLambdaExpression,
      brokeBeforeBrace: BreakTag?,
  ) {
    builder.sync(lambdaExpression)

    val valueParams = lambdaExpression.valueParameters
    val hasParams = valueParams.isNotEmpty()
    val bodyExpression = lambdaExpression.bodyExpression ?: fail()
    val expressionStatements = bodyExpression.children
    val hasStatements = expressionStatements.isNotEmpty()
    val hasComments = bodyExpression.children().any { it is PsiComment }
    val hasArrow = lambdaExpression.functionLiteral.arrow != null

    fun ifBrokeBeforeBrace(onTrue: Indent, onFalse: Indent): Indent {
      if (brokeBeforeBrace == null) return onFalse
      return Indent.If.make(brokeBeforeBrace, onTrue, onFalse)
    }

    /**
     * Enable correct formatting of the `fun foo() = scope {` syntax.
     *
     * We can't denote the lambda (+ scope function) as a block, since (for multiline lambdas) the
     * rectangle rule would force the entire lambda onto a lower line. Instead, we conditionally
     * indent all the interior levels of the lambda based on whether we had to break before the
     * opening brace (or scope function). This mimics the look of a block when the break is taken.
     *
     * These conditional indents should not be used inside interior blocks, since that would apply
     * the condition twice.
     */
    val bracePlusBlockIndent = ifBrokeBeforeBrace(blockPlusExpressionBreakIndent, blockIndent)
    val bracePlusExpressionIndent =
        ifBrokeBeforeBrace(doubleExpressionBreakIndent, expressionBreakIndent)
    val bracePlusZeroIndent = ifBrokeBeforeBrace(expressionBreakIndent, ZERO)

    builder.token("{")

    if (hasParams || hasArrow) {
      builder.space()
      builder.block(bracePlusExpressionIndent) { visitEachCommaSeparated(valueParams) }
      builder.block(bracePlusBlockIndent) {
        if (lambdaExpression.functionLiteral.valueParameterList?.trailingComma != null) {
          builder.token(",")
          builder.forcedBreak()
        } else if (hasParams) {
          builder.breakToFill(" ")
        }
        builder.token("->")
      }
    }

    if (hasParams || hasArrow || hasStatements || hasComments) {
      builder.breakOp(" ", bracePlusZeroIndent)
    }

    if (hasStatements) {
      builder.breakOpThenBlock(bracePlusBlockIndent) {
        builder.blankLineWanted(BlankLineWanted.NO)

        val shouldForceMultiline =
            options.preserveLambdaBreaks && lambdaExpression.hasSourceNewlineInLambdaBody

        if (
            !shouldForceMultiline &&
                expressionStatements.size == 1 &&
                expressionStatements.first() !is KtReturnExpression &&
                !bodyExpression.startsWithComment()
        ) {
          visitStatement(expressionStatements[0])
        } else {
          visitStatements(expressionStatements)
        }
        builder.breakOp(" ", bracePlusZeroIndent)
      }
    } else if (hasComments) {
      val blockComments =
          bodyExpression.children().filter { it is PsiComment && it.text.startsWith("/*") }.toList()
      builder.breakOpThenBlock(bracePlusBlockIndent) {
        builder.fenceComments()
        builder.blankLineWanted(BlankLineWanted.NO)
        for ((i, comment) in blockComments.withIndex()) {
          if (i > 0) {
            builder.forcedBreak()
          }
          builder.token(comment.text)
        }
        builder.breakOp(" ", bracePlusZeroIndent)
      }
    }

    if (hasParams || hasArrow || hasStatements || hasComments) {
      // If we had to break in the body, ensure there is a break before the closing brace
      builder.breakOp(bracePlusZeroIndent)
    }
    builder.block(bracePlusZeroIndent) {
      builder.fenceComments()
      builder.token("}", blockIndent)
    }
  }

  /** Example `this` or `this@Foo` */
  override fun visitThisExpression(expression: KtThisExpression) {
    builder.sync(expression)
    builder.token("this")
    visit(expression.getTargetLabel())
  }

  /** Example `Foo` or `@Foo` */
  override fun visitSimpleNameExpression(expression: KtSimpleNameExpression) {
    builder.sync(expression)
    when (expression) {
      is KtLabelReferenceExpression -> {
        if (expression.text[0] == '@') {
          builder.token("@")
          builder.token(expression.getIdentifier()?.text ?: fail())
        } else {
          builder.token(expression.getIdentifier()?.text ?: fail())
          builder.token("@")
        }
      }
      else -> {
        if (expression.text.isNotEmpty()) {
          builder.token(expression.text)
        }
      }
    }
  }

  /** e.g., `a: Int, b: Int, c: Int` in `fun foo(a: Int, b: Int, c: Int) { ... }`. */
  override fun visitParameterList(list: KtParameterList) {
    visitEachCommaSeparated(list.parameters, list.trailingComma != null, wrapInBlock = false)
  }

  /**
   * Visit each element in [list], with comma (,) tokens in-between.
   *
   * Example:
   * ```
   * a, b, c, 3, 4, 5
   * ```
   *
   * Either the entire list fits in one line, or each element is put on its own line:
   * ```
   * a,
   * b,
   * c,
   * 3,
   * 4,
   * 5
   * ```
   *
   * Optionally include a prefix and postfix:
   * ```
   *   (
   *     a,
   *     b,
   *     c,
   * )
   * ```
   *
   * @param hasTrailingComma if true, each element is placed on its own line (even if they could've
   *   fit in a single line), and a trailing comma is emitted.
   *
   * Example:
   * ```
   * a,
   * b,
   * ```
   *
   * @param wrapInBlock if true, place all the elements in a block. When there's no [leadingBreak],
   *   this will be negatively indented unless [compensateMissingLeadingBreak] says otherwise. Note
   *   that the [prefix] and [postfix] aren't included in the block.
   * @param leadingBreak if true, break before the first element.
   * @param compensateMissingLeadingBreak if true, and there is no [leadingBreak], pull the block
   *   back by one level. Callers that emit a [prefix] are indented on the assumption that the
   *   leading break fires, so without it the elements have to be pulled back to meet the prefix.
   *   Callers that instead place the first element with a break of their own -- such as the `:` of
   *   a supertype list -- are already at the right level and pass false.
   * @param prefix if provided, emit this before the first element.
   * @param postfix if provided, emit this after the last element (or trailing comma).
   * @param breakAfterPrefix if true, emit a break after [prefix], but before the start of the
   *   block.
   * @param breakBeforePostfix if true, place a break after the last element. Redundant when
   *   [hasTrailingComma] is true.
   * @return a [BreakTag] which can tell you if a break was taken, but only when the list doesn't
   *   terminate in a negative closing indent.
   *
   * Example 1, this returns a BreakTag which tells you a break wasn't taken:
   * ```
   * (arg1, arg2)
   * ```
   *
   * Example 2, this returns a BreakTag which tells you a break WAS taken:
   * ```
   * (
   *     arg1,
   *     arg2)
   * ```
   *
   * Example 3, this returns null:
   * ```
   * (
   *     arg1,
   *     arg2,
   * )
   * ```
   *
   * Example 4, this also returns null (similar to example 2, but Google style):
   * ```
   * (
   *     arg1,
   *     arg2
   * )
   * ```
   */
  private fun visitEachCommaSeparated(
      list: Iterable<PsiElement>,
      hasTrailingComma: Boolean = false,
      wrapInBlock: Boolean = true,
      leadingBreak: Boolean = true,
      compensateMissingLeadingBreak: Boolean = true,
      prefix: String? = null,
      postfix: String? = null,
      breakAfterPrefix: Boolean = true,
      breakBeforePostfix: Boolean = options.manageTrailingCommas,
  ): BreakTag? {
    val breakAfterLastElement = hasTrailingComma || (postfix != null && breakBeforePostfix)
    val nameTag = if (breakAfterLastElement) null else BreakTag()

    if (prefix != null) {
      builder.token(prefix)
      if (breakAfterPrefix) {
        builder.breakOp("", ZERO, Optional.ofNullable(nameTag))
      }
    }

    val breakType = if (hasTrailingComma) Doc.FillMode.FORCED else Doc.FillMode.UNIFIED
    fun emitComma() {
      builder.token(",")
      builder.breakOp(breakType, " ", ZERO)
    }

    val indent =
        if (leadingBreak || !compensateMissingLeadingBreak) ZERO else expressionBreakNegativeIndent
    builder.block(indent, isEnabled = wrapInBlock) {
      if (leadingBreak) {
        builder.breakOp(breakType, "", ZERO)
      }

      for ((index, value) in list.withIndex()) {
        if (index > 0) emitComma()
        visit(value)
      }

      if (hasTrailingComma) {
        emitComma()
      }
    }

    if (breakAfterLastElement) {
      // a negative closing indent places the postfix to the left of the elements
      // see examples 2 and 4 in the docstring
      builder.breakOp(breakType, "", expressionBreakNegativeIndent)
    }

    if (postfix != null) {
      if (breakAfterLastElement) {
        builder.block(expressionBreakNegativeIndent) {
          builder.fenceComments()
          builder.token(postfix, expressionBreakIndent)
        }
      } else {
        builder.token(postfix)
      }
    }

    return nameTag
  }

  /** Example `a` in `foo(a)`, or `*a`, or `limit = 50` */
  override fun visitArgument(argument: KtValueArgument) {
    visitArgumentInternal(
        argument,
        wrapInBlock = true,
        brokeBeforeBrace = null,
    )
  }

  /**
   * The internal version of [visitArgument].
   *
   * @param wrapInBlock if true places the argument expression in a block.
   */
  private fun visitArgumentInternal(
      argument: KtValueArgument,
      wrapInBlock: Boolean,
      brokeBeforeBrace: BreakTag?,
  ) {
    builder.sync(argument)
    val hasArgName = argument.getArgumentName() != null
    val isLambda = argument.getArgumentExpression() is KtLambdaExpression
    if (hasArgName) {
      visit(argument.getArgumentName())
      builder.spaceThenToken("=")
      if (isLambda) {
        builder.space()
      }
    }
    if (hasArgName && !isLambda && !argument.isSpread && !forceLineBreakAfterNamedParameter) {
      if (emitCallAfterOperator(argument.getArgumentExpression())) return
    }

    val indent = if (hasArgName && !isLambda) expressionBreakIndent else ZERO
    builder.block(indent, isEnabled = wrapInBlock) {
      if (hasArgName && !isLambda) {
        builder.breakToFill(" ")
      }
      if (argument.isSpread) {
        builder.token("*")
      }
      if (isLambda) {
        visitLambdaExpressionInternal(
            argument.getArgumentExpression() as KtLambdaExpression,
            brokeBeforeBrace = brokeBeforeBrace,
        )
      } else {
        visit(argument.getArgumentExpression())
      }
    }
  }

  override fun visitReferenceExpression(expression: KtReferenceExpression) {
    builder.sync(expression)
    builder.token(expression.text)
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
    builder.guessSemicolon()
  }

  /**
   * For example `a + b`, `a + b + c` or `a..b`
   *
   * The extra handling here drills to the left most expression and handles it for long chains of
   * binary expressions that are formatted not accordingly to the associative values That is, we
   * want to think of `a + b + c` as `(a + b) + c`, whereas the AST parses it as `a + (b + c)`
   */
  override fun visitBinaryExpression(expression: KtBinaryExpression) {
    builder.sync(expression)
    val op = expression.operationToken

    if (KtTokens.ALL_ASSIGNMENTS.contains(op) && expression.right.isLambdaOrScopingFunction) {
      // Assignments are statements in Kotlin; we don't have to worry about compound assignment.
      visit(expression.left)
      builder.spaceThenToken(expression.operationReference.text)
      visitLambdaOrScopingFunction(expression.right)
      return
    }

    val parts =
        ArrayDeque<KtBinaryExpression>().apply {
          var current: KtExpression? = expression
          while (current is KtBinaryExpression && current.operationToken == op) {
            addFirst(current)
            current = current.left
          }
        }

    val leftMostExpression = parts.first()
    visit(leftMostExpression.left)
    for (leftExpression in parts) {
      val isFirst = leftExpression === leftMostExpression

      when (leftExpression.operationToken) {
        KtTokens.RANGE,
        KtTokens.RANGE_UNTIL -> {
          if (isFirst) {
            builder.open(expressionBreakIndent)
          }
          builder.token(leftExpression.operationReference.text)
        }
        KtTokens.ELVIS -> {
          if (isFirst) {
            builder.open(expressionBreakIndent)
          }
          builder.breakOp(" ")
          builder.tokenThenSpace(leftExpression.operationReference.text)
        }
        else -> {
          builder.space()
          if (isFirst) {
            builder.open(if (indentBooleanConditions) expressionBreakIndent else ZERO)
          }
          builder.token(leftExpression.operationReference.text)
          val fillMode =
              if (leftExpression.operationReference.hasLineBreakingCommentBefore)
                  Doc.FillMode.INDEPENDENT
              else Doc.FillMode.UNIFIED
          builder.breakOp(fillMode, " ", ZERO)
        }
      }
      visit(leftExpression.right)
    }
    builder.close()
  }

  override fun visitPostfixExpression(expression: KtPostfixExpression) {
    builder.sync(expression)
    builder.block(ZERO) {
      val baseExpression = expression.baseExpression
      val operator = expression.operationReference.text

      visit(baseExpression)
      if (
          baseExpression is KtPostfixExpression &&
              baseExpression.operationReference.text.last() == operator.first()
      ) {
        builder.space()
      }
      builder.token(operator)
    }
  }

  override fun visitPrefixExpression(expression: KtPrefixExpression) {
    builder.sync(expression)
    builder.block(ZERO) {
      val baseExpression = expression.baseExpression
      val operator = expression.operationReference.text

      builder.token(operator)
      if (
          baseExpression is KtPrefixExpression &&
              operator.last() == baseExpression.operationReference.text.first()
      ) {
        builder.space()
      }
      visit(baseExpression)
    }
  }

  override fun visitLabeledExpression(expression: KtLabeledExpression) {
    builder.sync(expression)
    visit(expression.labelQualifier)
    if (expression.baseExpression !is KtLambdaExpression) {
      builder.space()
    }
    visit(expression.baseExpression)
  }

  /**
   * Declare one variable or variable-like thing.
   *
   * Examples:
   * - `var a: Int = 5`
   * - `a: Int`
   * - `private val b:
   */
  private fun emitVariableLikeDeclaration(
      isField: Boolean,
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
    val verticalAnnotationBreak = BreakTag()
    if (isField) {
      builder.blankLineWanted(BlankLineWanted.conditional(verticalAnnotationBreak))
    }

    visit(modifiers)
    builder.block(ZERO) {
      builder.block(ZERO) {
        if (valOrVarKeyword != null) {
          builder.tokenThenSpace(valOrVarKeyword)
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
            builder.breakOp(" ")
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
        builder.spaceThenToken("by")
        val delegateExpr = delegate.expression
        val laidOut =
            delegateExpr != null &&
                emitExpressionAfterOperator(
                    delegateExpr,
                    scopingFunctionHugs = true,
                    // The delegate node carries the expression; visiting it keeps the `by` intact.
                    emitHugged = {
                      builder.space()
                      visit(delegate)
                    },
                )
        if (!laidOut) {
          builder.breakOpThenBlock(" ", expressionBreakIndent) {
            builder.fenceComments()
            visit(delegate)
          }
        }
      } else if (initializer != null) {
        emitInitializer(initializer)
      }
    }
    // for example `field = value`, `private set`, or `get = 2 * field`
    val propertyComponents =
        (listOfNotNull(backingField) + accessors.orEmpty()).sortedBy { it.startOffset }
    if (propertyComponents.isNotEmpty()) {
      builder.block(blockIndent) {
        for (component in propertyComponents) {
          if (forceBreakBeforePropertyComponent(component)) builder.forcedBreak()
          else builder.breakOp(" ")
          // The semicolon must come after the newline, or the output code will not parse.
          builder.guessSemicolon()

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

    builder.guessSemicolon()

    if (isField) {
      builder.blankLineWanted(BlankLineWanted.conditional(verticalAnnotationBreak))
    }
  }

  private fun forceBreakBeforePropertyComponent(component: KtExpression): Boolean =
      when (component) {
        is KtBackingField -> true
        is KtPropertyAccessor -> {
          forceLineBreakBeforeAccessors || component.modifierList != null
        }
        else -> true
      }

  /**
   * Lays out the right-hand side of an operator that a declaration is assigned across -- the `=` of
   * an initializer or an expression body, or the `by` of a property delegate -- according to the
   * kind of expression it is.
   *
   * Lambdas, scoping functions and block-like calls keep the operator's line instead of breaking
   * after it, and in styles that hug them, so do `when` expressions, block-like infix calls and
   * calls that carry a trailing lambda alongside their value arguments.
   *
   * Returns false, having emitted nothing, when [expression] is not one of those shapes. Callers
   * then emit their own default layout, which differs between them.
   *
   * @param scopingFunctionHugs whether a lambda or scoping function is emitted by [emitHugged]
   *   instead of by [visitLambdaOrScopingFunction]. The `by` of a delegate is already followed by
   *   its expression on the same line, so its lambda hugs it rather than breaking to it.
   * @param emitHugged emits [expression] on the operator's line, after a plain space. Callers whose
   *   expression is wrapped in another PSI node -- the `by` of a delegate -- visit that node here.
   */
  private fun emitExpressionAfterOperator(
      expression: KtExpression,
      scopingFunctionHugs: Boolean = false,
      emitHugged: () -> Unit = {
        builder.space()
        visit(expression)
      },
  ): Boolean {
    when {
      expression.isLambdaOrScopingFunction ->
          if (scopingFunctionHugs) emitHugged() else visitLambdaOrScopingFunction(expression)
      expression.isChainedScopingFunction ->
          visitChainedScopingFunction(expression, emitLeadingBreak = true)
      expression.isBlockLikeCall -> emitHugged()
      expression.isChainedBlockLikeCall ->
          visitChainedBlockLikeCall(expression, emitLeadingBreak = true)
      !forceLineBreakAfterAssignment && expression is KtObjectLiteralExpression -> emitHugged()
      !forceLineBreakAfterAssignment && expression is KtTryExpression -> emitHugged()
      hugCallsWithTrailingLambda && expression.isCallWithTrailingLambda -> emitHugged()
      hugChainsAfterTrailingLambda &&
          !expression.hasLeadingComment &&
          visitChainAfterTrailingLambda(expression, emitLeadingBreak = true) -> Unit
      hugBlockLikeInfixCalls && expression.isInfixBlockLikeCall ->
          emitInfixBlockLikeCall(expression)
      hugWhenExpressions && expression is KtWhenExpression && !expression.hasLeadingComment ->
          emitWhenExpressionAfterOperator(expression)
      else -> return false
    }
    return true
  }

  /**
   * Lays out [expression] as a chain that keeps the receiver on the line of the operator it follows
   * -- see [emitQualifiedExpressionAfterOperator] -- in the styles that allow it.
   *
   * Returns false, having emitted nothing, when [expression] is not a chain, when the style breaks
   * after the operator unconditionally, or when the chain can't be split that way. Callers then
   * emit their own default layout.
   */
  private fun emitChainAfterOperator(expression: KtExpression): Boolean =
      !forceLineBreakAfterAssignment &&
          expression.isPlainQualifiedChain &&
          !expression.hasLeadingComment &&
          emitQualifiedExpressionAfterOperator(expression)

  /**
   * Emits `= <initializer>`, laying the initializer out according to the kind of expression it is.
   */
  private fun emitInitializer(initializer: KtExpression) {
    builder.spaceThenToken("=")
    if (emitExpressionAfterOperator(initializer)) {
      return
    }
    // A chain gets to keep its receiver on the `=` line when it fits there; everything else
    // breaks after the `=` and is laid out one level in.
    if (!emitChainAfterOperator(initializer)) {
      builder.breakOpThenBlock(" ", expressionBreakIndent) {
        builder.fenceComments()
        visit(initializer)
      }
    }
  }

  private fun emitBackingField(backingField: KtBackingField) {
    builder.sync(backingField)
    builder.block(ZERO) {
      builder.block(ZERO) { builder.token(backingField.namePlaceholder.text) }

      val type = backingField.returnTypeReference
      if (type != null) {
        builder.block(expressionBreakIndent) {
          builder.token(":")
          builder.breakOp(" ")
          visit(type)
        }
      }

      val initializer = backingField.initializer
      if (initializer != null) {
        emitInitializer(initializer)
      }
    }
  }

  /**
   * Emit an `a to Foo(\n ...,\n)` style infix call that follows an operator such as `=`, deciding
   * whether to break after that operator from the width of the head alone.
   */
  private fun emitInfixBlockLikeCall(expression: KtBinaryExpression) {
    builder.sync(expression)
    val right = checkNotNull(expression.right)
    val call = checkNotNull(right.callExpression)

    emitSplitAfterOperator(
        emitHead = {
          builder.fenceComments()
          visit(expression.left)
          builder.spaceThenToken(expression.operationReference.text)
          builder.space()
          // The call may be the selector of a qualifier, as in `a to Organizations.Override(...)`.
          if (right is KtQualifiedExpression) {
            visit(right.receiverExpression)
            builder.token(right.operationSign.value)
          }
          builder.sync(call)
          visit(call.calleeExpression)
          builder.block(ZERO) { visit(call.typeArgumentList) }
        },
        // The extra level carries the head's indent over to the arguments.
        emitTail = { callIndent, _ ->
          builder.block(callIndent) {
            builder.block(expressionBreakIndent) {
              visitValueArgumentListInternal(checkNotNull(call.valueArgumentList))
            }
          }
        },
    )
  }

  /**
   * Emit a `when (...) { ... }` that follows an operator such as `=`, deciding whether to break
   * after that operator from the width of the `when (...) {` head alone:
   * ```
   * val affected = when (event) {          // the head fits: it stays on the `=` line
   *     is Update -> emptyList()
   * }
   *
   * val affected: List<TeamId> =           // the head doesn't: the break is taken, and the body
   *     when (event) {                     // indents relative to the `when`
   *         is Update -> emptyList()
   *     }
   * ```
   *
   * The mechanics are the same as in [emitInfixBlockLikeCall], with the body of the `when` playing
   * the part of the argument list there.
   */
  private fun emitWhenExpressionAfterOperator(expression: KtWhenExpression) {
    builder.sync(expression)
    emitSplitAfterOperator(
        emitHead = {
          builder.fenceComments()
          emitWhenHead(expression)
        },
        // The body's braces align with the `when`, so it is laid out at the head's own indent.
        emitTail = { bodyIndent, _ -> builder.block(bodyIndent) { emitWhenBody(expression) } },
    )
  }

  /**
   * Emit a `foo(\n ...,\n).bar().baz()` style chain whose innermost receiver is a block-like
   * multiline call: render the receiver call normally (so its closing paren sits at the surrounding
   * indent), then emit each `.selector` on its own line, indented by [expressionBreakIndent].
   */
  private fun visitChainedBlockLikeCall(
      expression: KtQualifiedExpression,
      emitLeadingBreak: Boolean,
  ) {
    val parts = breakIntoParts(expression)
    if (emitLeadingBreak) {
      builder.space()
    }
    visit(parts[0])

    emitChainedSelectors(parts, forceBreak = true)
  }

  /**
   * Emit a `launch(dispatcher) { ... }.join()` style chain whose head is a call carrying a trailing
   * lambda: render that call block-like, so its lambda body is indented one block in and its
   * closing brace returns to the chain's own indent, then hang the remaining selectors off that
   * brace:
   * ```
   * GlobalScope.launch(Dispatchers.Main) {
   *     expect(2)
   * }.join()
   * ```
   *
   * Unlike [visitChainedScopingFunction], the selectors are not forced onto their own line -- they
   * only break when they don't fit, in which case they indent by [expressionBreakIndent].
   *
   * Returns false, having emitted nothing, when [expression] isn't built on a trailing lambda that
   * way; the caller then emits the regular chain layout.
   */
  private fun visitChainAfterTrailingLambda(
      expression: KtExpression,
      emitLeadingBreak: Boolean,
  ): Boolean {
    val headIndex = expression.trailingLambdaChainHead ?: return false
    val parts = breakIntoParts(expression)
    if (emitLeadingBreak) {
      builder.space()
    }

    visit(parts[headIndex])
    emitChainedSelectors(parts.subList(headIndex, parts.size), forceBreak = false)
    return true
  }

  /**
   * Emit the `.selector` parts of a chain (everything after the innermost receiver, [parts]`[0]`),
   * each on its own line, indented by [expressionBreakIndent].
   *
   * @param forceBreak whether the break before each selector is forced, or may stay flat
   */
  private fun emitChainedSelectors(parts: List<KtExpression>, forceBreak: Boolean) {
    builder.block(expressionBreakIndent) {
      for (i in 1 until parts.size) {
        val part = parts[i] as KtQualifiedExpression
        if (forceBreak) {
          builder.forcedBreak()
        } else {
          builder.breakOp()
        }
        builder.token(part.operationSign.value)
        val selectorExpression = part.selectorExpression
        if (selectorExpression is KtCallExpression) {
          visit(selectorExpression.calleeExpression)
          visitCallElement(
              null,
              selectorExpression.typeArgumentList,
              selectorExpression.valueArgumentList,
              selectorExpression.lambdaArguments,
          )
        } else {
          visit(selectorExpression)
        }
      }
    }
  }

  /**
   * Returns true when any chained selector after the innermost scoping-function receiver carries
   * value arguments (i.e. `.foo(a)` or `.fold({ ... }, { ... })`).
   *
   * Such chains are excluded from the special scoping-function chain layout in
   * [visitQualifiedExpression], since they are better served by the general qualified-expression
   * layout.
   */
  private fun chainedSelectorsHaveValueArguments(expression: KtExpression): Boolean {
    var current: KtExpression = expression
    while (current is KtQualifiedExpression) {
      val selector = current.selectorExpression
      if (selector is KtCallExpression && !selector.valueArgumentList?.arguments.isNullOrEmpty()) {
        return true
      }
      current = current.receiverExpression
    }
    return false
  }

  /**
   * Emit `runnnnn { ... }.baz().qux()` style: render the innermost scoping-function receiver
   * block-like (so the lambda braces sit at the surrounding indent), then emit each `.selector`
   * after the closing brace as a chained continuation indented by [blockIndent].
   *
   * When the receiver lambda spans multiple lines in the source we force the chained selectors onto
   * their own line; a single-line lambda stays joined to its chained call.
   */
  private fun visitChainedScopingFunction(
      expression: KtQualifiedExpression,
      emitLeadingBreak: Boolean,
  ) {
    val parts = breakIntoParts(expression)
    val root = parts[0]
    val forceBreakBeforeChain = root.isMultilineScopingFunction

    visitLambdaOrScopingFunction(root, emitLeadingBreak = emitLeadingBreak)

    emitChainedSelectors(parts, forceBreak = forceBreakBeforeChain)
  }

  /** See [isLambdaOrScopingFunction] for examples. */
  private fun visitLambdaOrScopingFunction(expr: PsiElement?, emitLeadingBreak: Boolean = true) {
    val breakToExpr = BreakTag()
    val breakSpace = if (emitLeadingBreak) " " else ""
    builder.breakToFill(breakSpace, expressionBreakIndent, Optional.of(breakToExpr))

    var carry = expr
    if (carry is KtQualifiedExpression && carry.receiverExpression is KtSimpleNameExpression) {
      visit(carry.receiverExpression)
      builder.token(carry.operationSign.value)
      carry = carry.selectorExpression
    }
    if (carry is KtCallExpression) {
      visit(carry.calleeExpression)
      builder.space()
      carry = carry.lambdaArguments[0].getArgumentExpression()
    }
    if (carry is KtLabeledExpression) {
      visit(carry.labelQualifier)
      carry = carry.baseExpression ?: fail()
    }
    if (carry is KtLambdaExpression) {
      visitLambdaExpressionInternal(carry, brokeBeforeBrace = breakToExpr)
      return
    }

    throw AssertionError(carry)
  }

  override fun visitClassOrObject(classOrObject: KtClassOrObject) {
    builder.sync(classOrObject)
    val contextReceiverList =
        classOrObject.getStubOrPsiChild(CONTEXT_PARAMETER_LIST) as? KtContextReceiverList
    val modifierList = classOrObject.modifierList
    builder.block(ZERO) {
      if (contextReceiverList != null) {
        visitContextReceiverList(contextReceiverList)
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
        builder.spaceThenToken(name.text)
        visit(classOrObject.typeParameterList)
      }
      visit(classOrObject.primaryConstructor)
      val superTypes = classOrObject.getSuperTypeList()
      if (superTypes != null) {
        builder.space()
        builder.block(ZERO) {
          builder.token(":")
          // A lone supertype constructor call keeps the header line, breaking only its arguments.
          if (!emitSuperTypeCallAfterColon(superTypes)) {
            builder.breakOp(" ", expressionBreakIndent)
            visit(superTypes)
          }
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
        builder.breakOp(" ")
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
      visitCallElement(
          null,
          call.typeArgumentList,
          call.valueArgumentList,
          call.lambdaArguments,
      )
    }
  }

  override fun visitClassInitializer(initializer: KtClassInitializer) {
    builder.sync(initializer)
    builder.tokenThenSpace("init")
    visit(initializer.body)
  }

  override fun visitConstantExpression(expression: KtConstantExpression) {
    builder.sync(expression)
    builder.token(expression.text)
  }

  /** Example `(1 + 1)` */
  override fun visitParenthesizedExpression(expression: KtParenthesizedExpression) {
    builder.sync(expression)
    builder.token("(")
    visit(expression.expression)
    builder.token(")")
  }

  override fun visitPackageDirective(directive: KtPackageDirective) {
    builder.sync(directive)
    if (directive.packageKeyword == null) {
      return
    }
    builder.tokenThenSpace("package")
    for ((index, packageName) in directive.packageNames.withIndex()) {
      if (index > 0) {
        builder.token(".")
      }
      builder.token(packageName.getIdentifier()?.text ?: packageName.getReferencedName())
    }

    builder.guessSemicolon()
    builder.forcedBreak()
  }

  /** Example `import com.foo.A; import com.bar.B` */
  override fun visitImportList(importList: KtImportList) {
    builder.sync(importList)
    importList.imports.forEach { visit(it) }
  }

  /** Example `import com.foo.A` */
  override fun visitImportDirective(directive: KtImportDirective) {
    builder.sync(directive)
    builder.tokenThenSpace("import")

    val importedReference = directive.importedReference
    if (importedReference != null) {
      inImport = true
      visit(importedReference)
      inImport = false
    }
    if (directive.isAllUnder) {
      builder.token(".")
      builder.token("*")
    }

    // Possible alias.
    val alias = directive.alias?.nameIdentifier
    if (alias != null) {
      builder.spacedToken("as")
      builder.token(alias.text ?: fail())
    }

    // Force a newline afterwards.
    builder.guessSemicolon()
    builder.forcedBreak()
  }

  /**
   * Example `context(logger: Logger, raise: Raise<Error>)`
   *
   * Note this also supports the legacy receiver format of `context(Logger, Raise<Error>)` for
   * backward compatibility and for function types
   */
  private fun handleContextReceiverList(contextReceiverList: KtContextReceiverList) {
    builder.sync(contextReceiverList)
    builder.token("context")
    visitEachCommaSeparated(
        contextReceiverList.listToVisit(),
        prefix = "(",
        postfix = ")",
        breakAfterPrefix = false,
        breakBeforePostfix = false,
    )
  }

  override fun visitContextReceiverList(contextReceiverList: KtContextReceiverList) {
    handleContextReceiverList(contextReceiverList)
    builder.forcedBreak()
  }

  /** For example `@Magic private final` */
  override fun visitModifierList(list: KtModifierList) {
    builder.sync(list)
    var onlyAnnotationsSoFar = true

    for (child in list.node.children()) {
      val psi = child.psi
      if (psi is PsiWhiteSpace) {
        continue
      }

      // In Kotlin 2.3+, context receiver lists are children of the modifier list.
      // Handle them directly to avoid issues with the visitor dispatch.
      if (psi is KtContextReceiverList) {
        visitContextReceiverList(psi)
        continue
      }

      if (child.elementType is KtModifierKeywordToken) {
        onlyAnnotationsSoFar = false
        builder.token(child.text)
      } else {
        visit(psi)
      }

      if (onlyAnnotationsSoFar && forceAnnotationBreaks && psi is KtAnnotationEntry) {
        builder.forcedBreak()
      } else if (onlyAnnotationsSoFar) {
        builder.breakOp(" ")
      } else {
        builder.space()
      }
    }
  }

  /**
   * Example:
   * ```
   * @SuppressLint("MagicNumber")
   * print(10)
   * ```
   *
   * in
   *
   * ```
   * fun f() {
   *   @SuppressLint("MagicNumber")
   *   print(10)
   * }
   * ```
   */
  override fun visitAnnotatedExpression(expression: KtAnnotatedExpression) {
    builder.sync(expression)
    builder.block(ZERO) {
      val baseExpression = expression.baseExpression

      builder.block(ZERO) {
        val annotationEntries = expression.annotationEntries
        for (annotationEntry in annotationEntries) {
          if (annotationEntry !== annotationEntries.first()) {
            builder.breakOp(" ")
          }
          visit(annotationEntry)
        }
      }

      // Binary expressions in a block have a different meaning according to their formatting.
      // If there in the line above, they refer to the entire expression, if they're in the same
      // line then only to the first operand of the operator.
      // We force a break to avoid such semantic changes
      when {
        (baseExpression is KtBinaryExpression || baseExpression is KtBinaryExpressionWithTypeRHS) &&
            expression.parent is KtBlockExpression -> builder.forcedBreak()
        baseExpression is KtLambdaExpression -> builder.space()
        baseExpression is KtReturnExpression -> builder.forcedBreak()
        else -> builder.breakOp(" ")
      }

      visit(baseExpression)
    }
  }

  /**
   * For example, @field:[Inject Named("WEB_VIEW")]
   *
   * A KtAnnotation is used only to group multiple annotations with the same use-site-target. It
   * only appears in a modifier list since annotated expressions do not have use-site-targets.
   */
  override fun visitAnnotation(annotation: KtAnnotation) {
    builder.sync(annotation)
    builder.block(ZERO) {
      builder.token("@")
      val useSiteTarget = annotation.useSiteTarget
      if (useSiteTarget != null) {
        visit(useSiteTarget)
        builder.token(":")
      }
      builder.block(expressionBreakIndent) {
        builder.token("[")

        builder.block(ZERO) {
          builder.breakOp()
          for ((index, value) in annotation.entries.withIndex()) {
            if (index > 0) {
              builder.breakOp(" ")
            }
            visit(value)
          }
        }
      }
      builder.token("]")
    }
    builder.forcedBreak()
  }

  /** For example, 'field' in @field:[Inject Named("WEB_VIEW")] */
  override fun visitAnnotationUseSiteTarget(
      annotationTarget: KtAnnotationUseSiteTarget,
      data: Void?,
  ): Void? {
    builder.token(annotationTarget.getAnnotationUseSiteTarget().renderName)
    return null
  }

  /** For example `@Magic` or `@Fred(1, 5)` */
  override fun visitAnnotationEntry(annotationEntry: KtAnnotationEntry) {
    builder.sync(annotationEntry)
    if (annotationEntry.atSymbol != null) {
      builder.token("@")
    }
    val useSiteTarget = annotationEntry.useSiteTarget
    if (useSiteTarget != null && useSiteTarget.parent == annotationEntry) {
      visit(useSiteTarget)
      builder.token(":")
    }
    visitCallElement(
        annotationEntry.calleeExpression,
        null, // Type-arguments are included in the annotation's callee expression.
        annotationEntry.valueArgumentList,
        listOf(),
    )
  }

  override fun visitFileAnnotationList(
      fileAnnotationList: KtFileAnnotationList,
      data: Void?,
  ): Void? {
    for (child in fileAnnotationList.node.children()) {
      // Leaf nodes -- whitespace and the tokens of the annotations themselves -- implement both
      // ASTNode and PsiElement, while composite nodes do not. This skips the leaves, leaving the
      // annotation entries to be visited.
      if (child is PsiElement) {
        continue
      }
      visit(child.psi)
      builder.forcedBreak()
    }

    return null
  }

  override fun visitSuperTypeList(list: KtSuperTypeList) {
    builder.sync(list)
    builder.block(expressionBreakIndent) {
      visitEachCommaSeparated(
          list.entries,
          leadingBreak = forceLineBreakAfterSupertypeColon,
          compensateMissingLeadingBreak = false,
      )
    }
  }

  override fun visitSuperTypeCallEntry(call: KtSuperTypeCallEntry) {
    builder.sync(call)
    visitCallElement(call.calleeExpression, null, call.valueArgumentList, call.lambdaArguments)
  }

  /**
   * Example `Collection<Int> by list` in `class MyList(list: List<Int>) : Collection<Int> by list`
   */
  override fun visitDelegatedSuperTypeEntry(specifier: KtDelegatedSuperTypeEntry) {
    builder.sync(specifier)
    visit(specifier.typeReference)
    builder.spacedToken("by")
    visit(specifier.delegateExpression)
  }

  override fun visitWhenExpression(expression: KtWhenExpression) {
    builder.sync(expression)
    builder.block(ZERO) {
      emitWhenHead(expression)
      emitWhenBody(expression)
    }
  }

  /** Emits `when (subject) {`, the part of a `when` expression that opens its body. */
  private fun emitWhenHead(expression: KtWhenExpression) {
    emitKeywordWithCondition("when", expression.subjectExpression)

    builder.space()
    builder.token("{", Doc.Token.RealOrImaginary.REAL, blockIndent, Optional.of(blockIndent))
  }

  /** Emits the entries of a `when` expression and the `}` closing its body. */
  private fun emitWhenBody(expression: KtWhenExpression) {
    expression.entries.forEachIndexed { index, whenEntry ->
      builder.block(blockIndent) {
        if (index != 0) {
          // preserve new line if there's one
          builder.blankLineWanted(BlankLineWanted.PRESERVE)
        }
        builder.forcedBreak()

        val whenExpression = whenEntry.expression
        val bodyIsBraced =
            whenExpression is KtBlockExpression || whenExpression is KtLambdaExpression
        // When comma-separated conditions are allowed to share a line, whether they actually fit
        // depends on the `-> body` that trails them, so they have to be laid out in the same level
        // as it. A braced body always breaks, so it is kept out of that level -- otherwise the
        // conditions would always be broken apart too.
        val conditionsShareLevelWithBody = !forceLineBreakInWhenConditionList && !bodyIsBraced

        builder.block(ZERO, isEnabled = conditionsShareLevelWithBody) {
          builder.block(ZERO, isEnabled = !conditionsShareLevelWithBody) {
            emitWhenEntryConditions(whenEntry)
          }
          if (whenEntry.trailingComma != null) {
            builder.forcedBreak()
          } else {
            builder.space()
          }
          builder.token("->")
          if (bodyIsBraced) {
            builder.space()
            visit(whenExpression)
          } else {
            builder.block(expressionBreakIndent) {
              builder.breakToFill(" ")
              visit(whenExpression)
            }
          }
        }
        builder.guessSemicolon()
      }
      builder.forcedBreak()
    }
    builder.token("}")
  }

  /** Emits `else`, or the comma-separated conditions of a `when` entry, followed by its guard. */
  private fun emitWhenEntryConditions(whenEntry: KtWhenEntry) {
    if (whenEntry.elseKeyword != null) {
      builder.token("else")
    } else {
      val conditions = whenEntry.conditions
      for ((conditionIndex, condition) in conditions.withIndex()) {
        visit(condition)
        builder.guessToken(",")
        if (conditionIndex != conditions.lastIndex) {
          if (forceLineBreakInWhenConditionList) builder.forcedBreak() else builder.breakOp(" ")
        }
      }
    }
    whenEntry.guard?.let { guard ->
      builder.space()
      emitKeywordWithCondition("if", guard.getExpression(), surroundConditionWithParens = false)
    }
  }

  override fun visitClassBody(body: KtClassBody) {
    builder.sync(body)
    emitBracedBlock(body) { children ->
      val enumEntryList = EnumEntryList.extractChildList(body)
      val members = children.filter { it !is KtEnumEntry }

      if (enumEntryList != null) {
        builder.block(ZERO) {
          builder.breakOp()
          for (value in enumEntryList.enumEntries) {
            visit(value)
            if (builder.peekToken().getOrNull() == ",") {
              builder.token(",")
              builder.forcedBreak()
            }
          }
        }
        builder.guessSemicolon()

        if (members.isNotEmpty()) {
          builder.forcedBreak()
          builder.blankLineWanted(BlankLineWanted.YES)
        }
      } else {
        val parent = body.parent
        if (parent is KtClass && parent.isEnum()) {
          builder.token(";")
          builder.forcedBreak()
        }
      }

      var prev: PsiElement? = null
      for (curr in members) {
        val blankLineBetweenMembers =
            when {
              prev == null -> BlankLineWanted.PRESERVE
              !forceLineBreaksBetweenEmptyMethods &&
                  prev is KtFunction &&
                  prev.bodyBlockExpression == null &&
                  prev.bodyExpression == null -> BlankLineWanted.PRESERVE
              prev !is KtProperty -> BlankLineWanted.YES
              prev.getter != null || prev.setter != null -> BlankLineWanted.YES
              curr is KtProperty -> BlankLineWanted.PRESERVE
              else -> BlankLineWanted.YES
            }
        builder.blankLineWanted(blankLineBetweenMembers)

        markForPartialFormat()
        builder.block(ZERO) { visit(curr) }
        markForPartialFormat()
        builder.guessSemicolon()
        builder.forcedBreak()

        prev = curr
      }
    }
  }

  override fun visitBlockExpression(expression: KtBlockExpression) {
    builder.sync(expression)
    emitBracedBlock(expression) { children -> visitStatements(children) }
  }

  override fun visitWhenConditionWithExpression(condition: KtWhenConditionWithExpression) {
    builder.sync(condition)
    visit(condition.expression)
  }

  override fun visitWhenConditionIsPattern(condition: KtWhenConditionIsPattern) {
    builder.sync(condition)
    builder.tokenThenSpace(if (condition.isNegated) "!is" else "is")
    visit(condition.typeReference)
  }

  /** Example `in 1..2` as part of a when expression */
  override fun visitWhenConditionInRange(condition: KtWhenConditionInRange) {
    builder.sync(condition)
    // TODO: replace with 'condition.isNegated' once https://youtrack.jetbrains.com/issue/KT-34395
    // is fixed.
    val isNegated = condition.firstChild?.node?.findChildByType(KtTokens.NOT_IN) != null
    builder.tokenThenSpace(if (isNegated) "!in" else "in")
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
        builder.breakToFillThenBlock(" ", expressionBreakIndent) {
          builder.fenceComments()
          visit(expression.then)
        }
      }

      if (expression.elseKeyword != null) {
        if (expression.then is KtBlockExpression) {
          builder.space()
        } else {
          builder.breakOp(" ")
        }

        builder.block(ZERO) {
          builder.token("else")
          if (expression.`else` is KtBlockExpression || expression.`else` is KtIfExpression) {
            builder.space()
            builder.block(ZERO) { visit(expression.`else`) }
          } else {
            builder.breakToFillThenBlock(" ", expressionBreakIndent) { visit(expression.`else`) }
          }
        }
      }
    }
  }

  /** Example `a[3]`, `b["a", 5]` or `a.b.c[4]` */
  override fun visitArrayAccessExpression(expression: KtArrayAccessExpression) {
    builder.sync(expression)
    if (expression.arrayExpression is KtQualifiedExpression) {
      emitQualifiedExpression(expression)
    } else {
      visit(expression.arrayExpression)
      visitArrayAccessBrackets(expression)
    }
  }

  /**
   * Emits a comma-separated list wrapped in delimiters, with the closing one outside the level the
   * elements are in so that it returns to the surrounding indent.
   */
  private fun emitDelimitedList(
      elements: Iterable<PsiElement>,
      hasTrailingComma: Boolean,
      openingDelimiter: String,
      closingDelimiter: String,
  ) {
    builder.block(ZERO) {
      builder.token(openingDelimiter)
      builder.breakOpThenBlock(expressionBreakIndent) {
        visitEachCommaSeparated(elements, hasTrailingComma, wrapInBlock = true)
      }
    }
    builder.token(closingDelimiter)
  }

  /**
   * Example `[3]` in `a[3]` or `a[3].b` Separated since it needs to be used from a top level array
   * expression (`a[3]`) and from within a qualified chain (`a[3].b)
   */
  private fun visitArrayAccessBrackets(expression: KtArrayAccessExpression) {
    emitDelimitedList(
        expression.indexExpressions,
        expression.trailingComma != null,
        openingDelimiter = "[",
        closingDelimiter = "]",
    )
  }

  /** Example `val (a, b: Int) = Pair(1, 2)` or `val [a, b] = Pair(1, 2)` */
  override fun visitDestructuringDeclaration(destructuringDeclaration: KtDestructuringDeclaration) {
    builder.sync(destructuringDeclaration)
    val valOrVarKeyword = destructuringDeclaration.valOrVarKeyword
    if (valOrVarKeyword != null) {
      builder.tokenThenSpace(valOrVarKeyword.text)
    }
    val hasTrailingComma = destructuringDeclaration.trailingComma != null
    emitDelimitedList(
        destructuringDeclaration.entries,
        hasTrailingComma,
        openingDelimiter = destructuringDeclaration.lPar?.text ?: "(",
        closingDelimiter = destructuringDeclaration.rPar?.text ?: ")",
    )
    val initializer = destructuringDeclaration.initializer
    if (initializer != null) {
      builder.spaceThenToken("=")
      if (hasTrailingComma) {
        builder.space()
      } else {
        builder.breakToFill(" ", expressionBreakIndent)
      }
      builder.block(expressionBreakIndent, !hasTrailingComma) { visit(initializer) }
    }
  }

  /** Example `val a: String` or `x = a` which is part of `(val a: String, x = a)` */
  override fun visitDestructuringDeclarationEntry(
      multiDeclarationEntry: KtDestructuringDeclarationEntry,
  ) {
    builder.sync(multiDeclarationEntry)
    emitVariableLikeDeclaration(
        initializer = multiDeclarationEntry.initializer,
        isField = false,
        modifiers = multiDeclarationEntry.modifierList,
        name = multiDeclarationEntry.nameIdentifier?.text ?: fail(),
        type = multiDeclarationEntry.typeReference,
        valOrVarKeyword = multiDeclarationEntry.ownValOrVarKeywordText,
    )
  }

  /** Example `"Hello $world!"` or `"""Hello world!"""` */
  override fun visitStringTemplateExpression(expression: KtStringTemplateExpression) {
    builder.sync(expression)
    builder.token(WhitespaceTombstones.replaceTrailingWhitespaceWithTombstone(expression.text))
  }

  /** Example `super` in `super.doIt(5)` or `super<Foo>` in `super<Foo>.doIt(5)` */
  override fun visitSuperExpression(expression: KtSuperExpression) {
    builder.sync(expression)
    builder.token("super")
    val superTypeQualifier = expression.superTypeQualifier
    if (superTypeQualifier != null) {
      builder.token("<")
      visit(superTypeQualifier)
      builder.token(">")
    }
    visit(expression.labelQualifier)
  }

  /** Example `<T, S>` */
  override fun visitTypeParameterList(list: KtTypeParameterList) {
    builder.sync(list)
    builder.block(expressionBreakIndent) {
      visitEachCommaSeparated(
          list = list.parameters,
          hasTrailingComma = list.trailingComma != null,
          prefix = "<",
          postfix = ">",
          wrapInBlock = !options.manageTrailingCommas,
      )
    }
  }

  override fun visitTypeParameter(parameter: KtTypeParameter) {
    builder.sync(parameter)
    visit(parameter.modifierList)
    builder.token(parameter.nameIdentifier?.text ?: "")
    val extendsBound = parameter.extendsBound
    if (extendsBound != null) {
      builder.spacedToken(":")
      visit(extendsBound)
    }
  }

  /** Example `where T : View, T : Listener` */
  override fun visitTypeConstraintList(list: KtTypeConstraintList) {
    builder.block(expressionBreakIndent) {
      builder.breakToFill(" ")
      builder.token("where")
      builder.block(expressionBreakIndent) {
        builder.breakOp(" ")
        builder.sync(list)
        visitEachCommaSeparated(list.constraints, wrapInBlock = false)
      }
    }
  }

  /** Example `T : Foo` */
  override fun visitTypeConstraint(constraint: KtTypeConstraint) {
    builder.sync(constraint)
    // TODO(nreid260): What about annotations on the type reference? `where @A T : Int`
    visit(constraint.subjectTypeParameterName)
    builder.spacedToken(":")
    visit(constraint.boundTypeReference)
  }

  /** Example `for (i in items) { ... }` */
  override fun visitForExpression(expression: KtForExpression) {
    builder.sync(expression)
    builder.block(ZERO) {
      builder.tokenThenSpace("for")
      builder.token("(")
      visit(expression.loopParameter)
      builder.spaceThenToken("in")
      builder.block(ZERO) {
        builder.breakOpThenBlock(" ", expressionBreakIndent) { visit(expression.loopRange) }
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
    builder.tokenThenSpace("do")
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
            builder.tokenThenSpace(":")
            visit(typeReference)
          }
        }
      } else {
        emitVariableLikeDeclaration(
            isField = false,
            modifiers = parameter.modifierList,
            valOrVarKeyword = parameter.valOrVarKeyword?.text,
            name = parameter.nameIdentifier?.text,
            type = typeReference,
            initializer = parameter.defaultValue,
        )
      }
    }
  }

  /** Example `String::isNullOrEmpty` */
  override fun visitCallableReferenceExpression(expression: KtCallableReferenceExpression) {
    builder.sync(expression)
    visit(expression.receiverExpression)

    // For some reason, expression.receiverExpression doesn't contain the question-mark token in
    // case of a nullable type, e.g., in String?::isNullOrEmpty.
    // Instead, KtCallableReferenceExpression exposes a method that looks for the QUEST token in
    // its children.
    if (expression.hasQuestionMarks) {
      builder.token("?")
    }

    builder.block(expressionBreakIndent) {
      builder.token("::")
      builder.breakToFill()
      visit(expression.callableReference)
    }
  }

  override fun visitClassLiteralExpression(expression: KtClassLiteralExpression) {
    builder.sync(expression)
    val receiverExpression = expression.receiverExpression
    if (receiverExpression is KtCallExpression) {
      visitCallElement(
          receiverExpression.calleeExpression,
          receiverExpression.typeArgumentList,
          receiverExpression.valueArgumentList,
          receiverExpression.lambdaArguments,
      )
    } else {
      visit(receiverExpression)
    }
    builder.token("::")
    builder.token("class")
  }

  override fun visitFunctionType(type: KtFunctionType) {
    builder.sync(type)

    type.contextReceiverList?.let { functionTypeContextReceiverList ->
      handleContextReceiverList(functionTypeContextReceiverList)
      builder.space()
    }

    val receiver = type.receiver
    if (receiver != null) {
      visit(receiver)
      builder.token(".")
    }
    builder.block(expressionBreakIndent) {
      val parameterList = type.parameterList
      if (parameterList != null) {
        visitEachCommaSeparated(
            parameterList.parameters,
            prefix = "(",
            postfix = ")",
            hasTrailingComma = parameterList.trailingComma != null,
        )
      }
    }
    builder.spacedToken("->")
    builder.block(expressionBreakIndent) { visit(type.returnTypeReference) }
  }

  /**
   * Emits an operation whose right-hand side is a type -- `a is Int`, `a as Int` -- as a single
   * group, so that a break lands before the operator rather than inside the left-hand side.
   *
   * A qualified left-hand side lays out its own chain, so the group opens after it; anything else
   * is enclosed by the group.
   *
   * @param emitSeparator emits what goes between the left-hand side and the operator
   */
  private fun emitTypeOperation(
      left: KtExpression?,
      operationReference: PsiElement,
      right: PsiElement?,
      emitSeparator: () -> Unit,
  ) {
    val openGroupBeforeLeft = left !is KtQualifiedExpression
    if (openGroupBeforeLeft) builder.open(ZERO)
    visit(left)
    if (!openGroupBeforeLeft) builder.open(ZERO)
    emitSeparator()
    visit(operationReference)
    builder.breakToFillThenBlock(" ", expressionBreakIndent) { visit(right) }
    builder.close()
  }

  /** Example `a is Int` or `b !is Int` */
  override fun visitIsExpression(expression: KtIsExpression) {
    builder.sync(expression)
    emitTypeOperation(
        expression.leftHandSide,
        expression.operationReference,
        expression.typeReference,
    ) {
      val parent = expression.parent
      if (
          parent is KtValueArgument ||
              parent is KtParenthesizedExpression ||
              parent is KtContainerNode
      ) {
        builder.breakOp(" ", expressionBreakIndent)
      } else {
        builder.space()
      }
    }
  }

  /** Example `a as Int` or `a as? Int` */
  override fun visitBinaryWithTypeRHSExpression(expression: KtBinaryExpressionWithTypeRHS) {
    builder.sync(expression)
    emitTypeOperation(expression.left, expression.operationReference, expression.right) {
      builder.breakOp(" ", expressionBreakIndent)
    }
  }

  /**
   * Example:
   * ```
   * fun f() {
   *   val a: Array<Int> = [1, 2, 3]
   * }
   * ```
   */
  override fun visitCollectionLiteralExpression(expression: KtCollectionLiteralExpression) {
    builder.sync(expression)
    builder.block(expressionBreakIndent) {
      visitEachCommaSeparated(
          expression.getInnerExpressions(),
          expression.trailingComma != null,
          prefix = "[",
          postfix = "]",
          wrapInBlock = !options.manageTrailingCommas,
      )
    }
  }

  override fun visitTryExpression(expression: KtTryExpression) {
    builder.sync(expression)
    builder.tokenThenSpace("try")
    visit(expression.tryBlock)
    for (catchClause in expression.catchClauses) {
      visit(catchClause)
    }
    visit(expression.finallyBlock)
  }

  override fun visitCatchSection(catchClause: KtCatchClause) {
    builder.sync(catchClause)
    builder.spacedToken("catch")
    builder.block(ZERO) {
      builder.token("(")
      builder.block(expressionBreakIndent) {
        builder.breakOp()
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
    builder.spacedToken("finally")
    visit(finallySection.finalExpression)
  }

  override fun visitThrowExpression(expression: KtThrowExpression) {
    builder.sync(expression)
    builder.tokenThenSpace("throw")
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
      builder.tokenThenSpace("typealias")
      builder.token(typeAlias.nameIdentifier?.text ?: fail())
      visit(typeAlias.typeParameterList)

      builder.spaceThenToken("=")
      builder.breakToFillThenBlock(" ", expressionBreakIndent) {
        visit(typeAlias.getTypeReference())
        visit(typeAlias.typeConstraintList)
        builder.guessSemicolon()
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

  override fun visitKtFile(file: KtFile) {
    markForPartialFormat()
    val importListEmpty = file.importList?.text.isNullOrBlank()

    var isFirst = true
    for (child in file.children) {
      if (child.text.isBlank()) {
        continue
      }

      builder.blankLineWanted(
          when {
            isFirst -> BlankLineWanted.NO
            child is PsiComment -> continue
            child is KtScript && importListEmpty -> BlankLineWanted.PRESERVE
            else -> BlankLineWanted.YES
          },
      )

      builder.markForPartialFormat()
      visit(child)
      builder.markForPartialFormat()
      isFirst = false
    }
    markForPartialFormat()
  }

  override fun visitScript(script: KtScript) {
    markForPartialFormat()
    var lastChildHadBlankLineBefore = false
    var lastChildIsContextReceiver = false
    var first = true
    for (child in script.blockExpression.children) {
      if (child.text.isBlank()) {
        continue
      }
      builder.forcedBreak()
      val childGetsBlankLineBefore = child !is KtProperty
      if (first) {
        builder.blankLineWanted(BlankLineWanted.PRESERVE)
      } else if (lastChildIsContextReceiver) {
        builder.blankLineWanted(BlankLineWanted.NO)
      } else if (
          child !is PsiComment && (childGetsBlankLineBefore || lastChildHadBlankLineBefore)
      ) {
        builder.blankLineWanted(BlankLineWanted.YES)
      }
      builder.markForPartialFormat()
      visit(child)
      builder.guessSemicolon()
      builder.markForPartialFormat()
      lastChildHadBlankLineBefore = childGetsBlankLineBefore
      lastChildIsContextReceiver =
          child is KtScriptInitializer &&
              child.firstChild?.firstChild?.firstChild?.text == "context"
      first = false
    }
    markForPartialFormat()
  }

  /**
   * markForPartialFormat is used to delineate the smallest areas of code that must be formatted
   * together.
   *
   * When only parts of the code are being formatted, the requested area is expanded until it's
   * covered by an area marked by this method.
   */
  private fun markForPartialFormat() {
    if (!inExpression.last()) {
      builder.markForPartialFormat()
    }
  }

  /**
   * Throws a formatting error
   *
   * This is used as `expr ?: fail()` to avoid using the !! operator and provide better error
   * messages.
   */
  private fun fail(message: String = "Unexpected"): Nothing {
    throw FormattingError(builder.diagnostic(message))
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
      builder.tokenThenSpace(keyword)
      if (surroundConditionWithParens) {
        builder.token("(")
      }
      if (options.manageTrailingCommas) {
        builder.block(expressionBreakIndent) {
          builder.breakOp()
          visit(condition)
          builder.breakOp(expressionBreakNegativeIndent)
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
