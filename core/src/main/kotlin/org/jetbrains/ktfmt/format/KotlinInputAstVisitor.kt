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
import com.google.googlejavaformat.Indent
import com.google.googlejavaformat.Indent.Const.ZERO
import com.google.googlejavaformat.OpsBuilder
import com.google.googlejavaformat.Output.BreakTag
import java.util.ArrayDeque
import java.util.Optional
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract
import kotlin.jvm.optionals.getOrNull
import org.jetbrains.kotlin.com.intellij.psi.PsiComment
import org.jetbrains.kotlin.com.intellij.psi.PsiElement
import org.jetbrains.kotlin.com.intellij.psi.PsiWhiteSpace
import org.jetbrains.kotlin.com.intellij.psi.stubs.PsiFileStubImpl
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
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtEnumEntry
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFinallySection
import org.jetbrains.kotlin.psi.KtForExpression
import org.jetbrains.kotlin.psi.KtIfExpression
import org.jetbrains.kotlin.psi.KtImportDirective
import org.jetbrains.kotlin.psi.KtIsExpression
import org.jetbrains.kotlin.psi.KtLabelReferenceExpression
import org.jetbrains.kotlin.psi.KtLabeledExpression
import org.jetbrains.kotlin.psi.KtLambdaArgument
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtModifierList
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtPackageDirective
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtParameterList
import org.jetbrains.kotlin.psi.KtParenthesizedExpression
import org.jetbrains.kotlin.psi.KtPostfixExpression
import org.jetbrains.kotlin.psi.KtPrefixExpression
import org.jetbrains.kotlin.psi.KtPrimaryConstructor
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtPropertyAccessor
import org.jetbrains.kotlin.psi.KtPropertyDelegate
import org.jetbrains.kotlin.psi.KtQualifiedExpression
import org.jetbrains.kotlin.psi.KtReferenceExpression
import org.jetbrains.kotlin.psi.KtReturnExpression
import org.jetbrains.kotlin.psi.KtSecondaryConstructor
import org.jetbrains.kotlin.psi.KtSimpleNameExpression
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.psi.KtSuperExpression
import org.jetbrains.kotlin.psi.KtSuperTypeCallEntry
import org.jetbrains.kotlin.psi.KtThisExpression
import org.jetbrains.kotlin.psi.KtThrowExpression
import org.jetbrains.kotlin.psi.KtTryExpression
import org.jetbrains.kotlin.psi.KtTypeAlias
import org.jetbrains.kotlin.psi.KtTypeArgumentList
import org.jetbrains.kotlin.psi.KtTypeConstraintList
import org.jetbrains.kotlin.psi.KtTypeParameterList
import org.jetbrains.kotlin.psi.KtTypeReference
import org.jetbrains.kotlin.psi.KtValueArgument
import org.jetbrains.kotlin.psi.KtValueArgumentList
import org.jetbrains.kotlin.psi.KtWhenConditionInRange
import org.jetbrains.kotlin.psi.KtWhenConditionIsPattern
import org.jetbrains.kotlin.psi.KtWhenConditionWithExpression
import org.jetbrains.kotlin.psi.KtWhenExpression
import org.jetbrains.kotlin.psi.KtWhileExpression
import org.jetbrains.kotlin.psi.psiUtil.children
import org.jetbrains.kotlin.psi.psiUtil.getPrevSiblingIgnoringWhitespace
import org.jetbrains.kotlin.psi.psiUtil.startOffset
import org.jetbrains.kotlin.psi.psiUtil.startsWithComment
import org.jetbrains.kotlin.psi.stubs.elements.KtStubElementTypes
import org.jetbrains.kotlin.psi.stubs.impl.KotlinPlaceHolderStubImpl
import org.jetbrains.ktfmt.format.visitor.AbstractFormatterVisitor
import org.jetbrains.ktfmt.format.visitor.FileFormatter
import org.jetbrains.ktfmt.format.visitor.ListFormatter
import org.jetbrains.ktfmt.format.visitor.TypeFormatter
import org.jetbrains.ktfmt.format.visitor.asIndent
import org.jetbrains.ktfmt.format.visitor.block
import org.jetbrains.ktfmt.format.visitor.callExpression
import org.jetbrains.ktfmt.format.visitor.chainRoot
import org.jetbrains.ktfmt.format.visitor.fenceComments
import org.jetbrains.ktfmt.format.visitor.hasEmptyParens
import org.jetbrains.ktfmt.format.visitor.isBlockLikeCall
import org.jetbrains.ktfmt.format.visitor.isChainedBlockLikeCall
import org.jetbrains.ktfmt.format.visitor.isLambda
import org.jetbrains.ktfmt.format.visitor.sync
import org.jetbrains.ktfmt.format.visitor.token
import org.jetbrains.ktfmt.util.CONTEXT_PARAMETER_LIST
import org.jetbrains.ktfmt.util.ownValOrVarKeywordText

/** An AST visitor that builds a stream of {@link Op}s to format. */
open class KotlinInputAstVisitor(
    override val options: FormattingOptions,
    override val builder: OpsBuilder,
) : AbstractFormatterVisitor(), FileFormatter, TypeFormatter, ListFormatter {

  /** Standard indentation for a block */
  private val blockIndent: Indent.Const = options.blockIndent.asIndent

  /**
   * Standard indentation for a long expression or function call, it is different than block
   * indentation on purpose
   */
  override val expressionBreakIndent: Indent.Const = options.continuationIndent.asIndent

  private val blockPlusExpressionBreakIndent: Indent.Const =
      (options.blockIndent + options.continuationIndent).asIndent

  private val doubleExpressionBreakIndent: Indent.Const = (options.continuationIndent * 2).asIndent

  override val expressionBreakNegativeIndent: Indent.Const = (-options.continuationIndent).asIndent

  /** A record of whether we have visited into an expression. */
  override val inExpression = ArrayDeque(ImmutableList.of(false))

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

      if (parameterList != null && parameterList.hasEmptyParens()) {
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

      if (typeConstraintList != null) {
        visit(typeConstraintList)
      }
      if (bodyExpression is KtBlockExpression) {
        builder.space()
        visit(bodyExpression)
      } else if (bodyExpression != null) {
        builder.space()
        builder.block(ZERO) {
          builder.token("=")
          if (isLambdaOrScopingFunction(bodyExpression)) {
            visitLambdaOrScopingFunction(bodyExpression)
          } else if (isChainedScopingFunction(bodyExpression)) {
            visitChainedScopingFunction(bodyExpression, emitLeadingBreak = true)
          } else if (bodyExpression.isBlockLikeCall) {
            builder.space()
            visit(bodyExpression)
          } else if (bodyExpression.isChainedBlockLikeCall) {
            visitChainedBlockLikeCall(bodyExpression, emitLeadingBreak = true)
          } else {
            builder.block(expressionBreakIndent) {
              builder.breakOp(Doc.FillMode.INDEPENDENT, " ", ZERO)
              builder.block(ZERO) { visit(bodyExpression) }
            }
          }
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
    builder.token("{", Doc.Token.RealOrImaginary.REAL, blockIndent, Optional.of(blockIndent))
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

  private fun visitStatement(statement: PsiElement) {
    builder.block(ZERO) { visit(statement) }
    builder.guessToken(";")
  }

  private fun visitStatements(statements: Array<PsiElement>) {
    var first = true
    builder.guessToken(";")
    for (statement in statements) {
      builder.forcedBreak()
      if (!first) {
        builder.blankLineWanted(OpsBuilder.BlankLineWanted.PRESERVE)
      }
      first = false
      markForPartialFormat()
      visitStatement(statement)
      markForPartialFormat()
    }
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
          builder.breakOp(Doc.FillMode.UNIFIED, "", ZERO)
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
      isChainedScopingFunction(expression) &&
          isMultilineScopingFunction(expression.chainRoot) &&
          chainedSelectorsHaveNoValueArguments(expression) -> {
        visitChainedScopingFunction(expression, emitLeadingBreak = false)
      }
      expression.isChainedBlockLikeCall -> {
        visitChainedBlockLikeCall(expression, emitLeadingBreak = false)
      }
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
    val parts = breakIntoParts(expression)
    // whether we want to make a lambda look like a block, this make Kotlin DSLs look as expected
    val useBlockLikeLambdaStyle = parts.last().isLambda && parts.count { it.isLambda } == 1
    val groupingInfos = computeGroupingInfo(parts, useBlockLikeLambdaStyle)
    builder.block(expressionBreakIndent) {
      val nameTag = genSym() // allows adjusting arguments indentation if a break will be made
      for ((index, ktExpression) in parts.withIndex()) {
        if (ktExpression is KtQualifiedExpression) {
          builder.breakOp(Doc.FillMode.UNIFIED, "", ZERO, Optional.of(nameTag))
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
  }

  /**
   * Decomposes a qualified expression into parts, so `rainbow.red.orange.yellow` becomes `[rainbow,
   * rainbow.red, rainbow.red.orange, rainbow.orange.yellow]`
   */
  private fun breakIntoParts(expression: KtExpression): List<KtExpression> {
    val parts = ArrayDeque<KtExpression>()

    // use an ArrayDeque and add elements to the beginning so the innermost expression comes first
    // foo.bar.yay -> [yay, bar.yay, foo.bar.yay]

    var node: KtExpression? = expression
    while (node != null) {
      parts.addFirst(node)
      node =
          when (node) {
            is KtQualifiedExpression -> node.receiverExpression
            is KtArrayAccessExpression -> node.arrayExpression
            is KtPostfixExpression -> node.baseExpression
            else -> null
          }
    }

    return parts.toList()
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
            brokeBeforeBrace = formatValueArgumentList(argumentList)
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
      builder.block(bracePlusExpressionIndent) { formatCommaSeparatedList(valueParams) }
      builder.block(bracePlusBlockIndent) {
        if (lambdaExpression.functionLiteral.valueParameterList?.trailingComma != null) {
          builder.token(",")
          builder.forcedBreak()
        } else if (hasParams) {
          builder.breakOp(Doc.FillMode.INDEPENDENT, " ", ZERO)
        }
        builder.token("->")
      }
    }

    if (hasParams || hasArrow || hasStatements || hasComments) {
      builder.breakOp(Doc.FillMode.UNIFIED, " ", bracePlusZeroIndent)
    }

    if (hasStatements) {
      builder.breakOp(Doc.FillMode.UNIFIED, "", bracePlusBlockIndent)
      builder.block(bracePlusBlockIndent) {
        builder.blankLineWanted(OpsBuilder.BlankLineWanted.NO)

        val shouldForceMultiline =
            options.preserveLambdaBreaks && hasSourceNewlineInLambdaBody(lambdaExpression)

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
        builder.breakOp(Doc.FillMode.UNIFIED, " ", bracePlusZeroIndent)
      }
    } else if (hasComments) {
      val blockComments =
          bodyExpression.children().filter { it is PsiComment && it.text.startsWith("/*") }.toList()
      builder.breakOp(Doc.FillMode.UNIFIED, "", bracePlusBlockIndent)
      builder.block(bracePlusBlockIndent) {
        builder.fenceComments()
        builder.blankLineWanted(OpsBuilder.BlankLineWanted.NO)
        if (blockComments.size == 1) {
          builder.token(blockComments[0].text)
        } else {
          for ((i, comment) in blockComments.withIndex()) {
            if (i > 0) {
              builder.forcedBreak()
            }
            builder.token(comment.text)
          }
        }
        builder.breakOp(Doc.FillMode.UNIFIED, " ", bracePlusZeroIndent)
      }
    }

    if (hasParams || hasArrow || hasStatements || hasComments) {
      // If we had to break in the body, ensure there is a break before the closing brace
      builder.breakOp(Doc.FillMode.UNIFIED, "", bracePlusZeroIndent)
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
      builder.space()
      builder.token("=")
      if (isLambda) {
        builder.space()
      }
    }
    val indent = if (hasArgName && !isLambda) expressionBreakIndent else ZERO
    builder.block(indent, isEnabled = wrapInBlock) {
      if (hasArgName && !isLambda) {
        builder.breakOp(Doc.FillMode.INDEPENDENT, " ", ZERO)
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
    builder.guessToken(";")
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

    if (KtTokens.ALL_ASSIGNMENTS.contains(op) && isLambdaOrScopingFunction(expression.right)) {
      // Assignments are statements in Kotlin; we don't have to worry about compound assignment.
      visit(expression.left)
      builder.space()
      builder.token(expression.operationReference.text)
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
          builder.breakOp(Doc.FillMode.UNIFIED, " ", ZERO)
          builder.token(leftExpression.operationReference.text)
          builder.space()
        }
        else -> {
          builder.space()
          if (isFirst) {
            builder.open(expressionBreakIndent)
          }
          builder.token(leftExpression.operationReference.text)
          val fillMode =
              if (hasLineBreakingCommentBefore(leftExpression.operationReference))
                  Doc.FillMode.INDEPENDENT
              else Doc.FillMode.UNIFIED
          builder.breakOp(fillMode, " ", ZERO)
        }
      }
      visit(leftExpression.right)
    }
    builder.close()
  }

  /**
   * Checks if a line-breaking comment precedes [element] in the PSI tree.
   *
   * Line comments (`//`) always force a break. Block comments (`/* */`) only count if they are on
   * their own line (preceded by whitespace with a newline). Inline block comments like `x /*tag*/
   * ||` do not force a break and should not trigger INDEPENDENT fill mode.
   */
  private fun hasLineBreakingCommentBefore(element: PsiElement): Boolean {
    var prev = element.prevSibling
    while (prev is PsiWhiteSpace) {
      prev = prev.prevSibling
    }
    if (prev !is PsiComment) return false

    // Line comments always force a line break
    if (prev.text.startsWith("//")) return true

    // Block comments force a break only if on their own line
    val beforeComment = prev.prevSibling
    return beforeComment is PsiWhiteSpace && beforeComment.text.contains('\n')
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
        if (isLambdaOrScopingFunction(delegateExpr)) {
          builder.space()
          visit(delegate)
        } else if (delegateExpr != null && isChainedScopingFunction(delegateExpr)) {
          visitChainedScopingFunction(delegateExpr, emitLeadingBreak = true)
        } else if (delegateExpr.isBlockLikeCall) {
          builder.space()
          visit(delegate)
        } else if (delegateExpr != null && delegateExpr.isChainedBlockLikeCall) {
          visitChainedBlockLikeCall(delegateExpr, emitLeadingBreak = true)
        } else {
          builder.breakOp(Doc.FillMode.UNIFIED, " ", expressionBreakIndent)
          builder.block(expressionBreakIndent) {
            builder.fenceComments()
            visit(delegate)
          }
        }
      } else if (initializer != null) {
        builder.space()
        builder.token("=")
        if (isLambdaOrScopingFunction(initializer)) {
          visitLambdaOrScopingFunction(initializer)
        } else if (isChainedScopingFunction(initializer)) {
          visitChainedScopingFunction(initializer, emitLeadingBreak = true)
        } else if (initializer.isBlockLikeCall) {
          builder.space()
          visit(initializer)
        } else if (initializer.isChainedBlockLikeCall) {
          visitChainedBlockLikeCall(initializer, emitLeadingBreak = true)
        } else {
          builder.breakOp(Doc.FillMode.UNIFIED, " ", expressionBreakIndent)
          builder.block(expressionBreakIndent) {
            builder.fenceComments()
            visit(initializer)
          }
        }
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
                    parameterList = getParameterListWithBugFixes(component),
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
        builder.token("=")
        if (isLambdaOrScopingFunction(initializer)) {
          visitLambdaOrScopingFunction(initializer)
        } else if (isChainedScopingFunction(initializer)) {
          visitChainedScopingFunction(initializer, emitLeadingBreak = true)
        } else if (initializer.isBlockLikeCall) {
          builder.space()
          visit(initializer)
        } else if (initializer.isChainedBlockLikeCall) {
          visitChainedBlockLikeCall(initializer, emitLeadingBreak = true)
        } else {
          builder.breakOp(Doc.FillMode.UNIFIED, " ", expressionBreakIndent)
          builder.block(expressionBreakIndent) {
            builder.fenceComments()
            visit(initializer)
          }
        }
      }
    }
  }

  // Bug in Kotlin 1.9.10: KtPropertyAccessor is the direct parent of the left and right paren
  // elements. Also parameterList is always null for getters. As a workaround, we create our own
  // fake KtParameterList.
  // TODO: won't need this after https://youtrack.jetbrains.com/issue/KT-70922
  private fun getParameterListWithBugFixes(accessor: KtPropertyAccessor): KtParameterList? {
    if (accessor.bodyExpression == null && accessor.bodyBlockExpression == null) return null

    val stub = accessor.stub ?: PsiFileStubImpl(accessor.containingFile)

    return object :
        KtParameterList(KotlinPlaceHolderStubImpl(stub, KtStubElementTypes.VALUE_PARAMETER_LIST)) {
      override fun getParameters(): List<KtParameter> {
        return accessor.valueParameters
      }

      override fun getTrailingComma(): PsiElement? {
        return accessor.parameterList?.trailingComma
      }

      override fun getLeftParenthesis(): PsiElement? {
        return accessor.parameterList?.leftParenthesis
      }

      override fun getRightParenthesis(): PsiElement? {
        return accessor.parameterList?.rightParenthesis
      }
    }
  }

  /**
   * Returns whether an expression is a lambda or initializer expression in which case we will want
   * to avoid indenting the lambda block
   *
   * Examples:
   * 1. '... = { ... }' is a lambda expression
   * 2. '... = Runnable { ... }' is considered a scoping function
   * 3. '... = scope { ... }' '... = apply { ... }' is a scoping function
   * 4. '... = scope.launch { ... }' is a dot-qualified scoping function
   *
   * but not:
   * 1. '... = foo() { ... }' due to the empty parenthesis
   * 2. '... = Runnable @Annotation { ... }' due to the annotation
   */
  private fun isLambdaOrScopingFunction(expression: KtExpression?): Boolean {
    if (expression == null) return false
    val prev = expression.getPrevSiblingIgnoringWhitespace()
    if (prev is PsiComment && prev.text.startsWith("//")) {
      return false // Leading line comments cause weird indentation; block comments are ok.
    }

    var carry = expression
    if (carry is KtQualifiedExpression && carry.receiverExpression is KtSimpleNameExpression) {
      carry = carry.selectorExpression
    }
    if (carry is KtCallExpression) {
      if (
          carry.valueArgumentList?.leftParenthesis == null &&
              carry.lambdaArguments.isNotEmpty() &&
              carry.typeArgumentList?.arguments.isNullOrEmpty()
      ) {
        carry = carry.lambdaArguments[0].getArgumentExpression()
      } else {
        return false
      }
    }
    if (carry is KtLabeledExpression) {
      carry = carry.baseExpression
    }
    if (carry is KtLambdaExpression) {
      return true
    }

    return false
  }

  /**
   * Returns true when [expression] is a chain whose innermost receiver is a scoping function call.
   *
   * For example, this matches `runnnnn { ... }.baz()` (innermost receiver `runnnnn { ... }` is a
   * scoping function). It does not match a chain whose root is a plain identifier or a non-scoping
   * call, since those don't have a block-like opener to anchor the chain against.
   */
  @OptIn(ExperimentalContracts::class)
  private fun isChainedScopingFunction(expression: KtExpression): Boolean {
    contract { returns(true) implies (expression is KtQualifiedExpression) }

    if (expression !is KtQualifiedExpression) return false
    return isLambdaOrScopingFunction(expression.chainRoot)
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

    builder.block(expressionBreakIndent) {
      for (i in 1 until parts.size) {
        val part = parts[i] as KtQualifiedExpression
        builder.forcedBreak()
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
   * value arguments (i.e. `.foo(a)` or `.fold({ ... }, { ... })`). Used to decide formatting style
   * for property initializers: value-arg chains stay on same line as `=`, while no-arg chains
   * break.
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
   * Returns true when every chained selector after the innermost scoping-function receiver carries
   * no value arguments (i.e. only `.foo()` or `.foo { ... }` with a trailing lambda). Selectors
   * that pass regular value arguments are excluded from special chained handling in qualified
   * expressions, since those chains are better served by the general qualified-expression layout
   * except in property initializer context where we handle them specially.
   */
  private fun chainedSelectorsHaveNoValueArguments(expression: KtExpression): Boolean {
    return !chainedSelectorsHaveValueArguments(expression)
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
    val forceBreakBeforeChain = isMultilineScopingFunction(root)

    visitLambdaOrScopingFunction(root, emitLeadingBreak = emitLeadingBreak)

    // The break before each selector must stay outside the block below, at the same level as
    // the lambda, so that it is taken exactly when the lambda breaks. Inside the block it
    // would fire only when the selector itself is too long, so a lambda broken by max width
    // would keep its selector on the closing brace's line — and the next format pass, seeing
    // a multiline lambda in the source, would force the selector onto its own line (#640).
    val fillMode = if (forceBreakBeforeChain) Doc.FillMode.FORCED else Doc.FillMode.UNIFIED
    for (i in 1 until parts.size) {
      val part = parts[i] as KtQualifiedExpression
      builder.breakOp(fillMode, "", expressionBreakIndent)
      builder.block(expressionBreakIndent) {
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
   * Returns true when [expression] is a scoping-function call whose lambda body has source-level
   * newlines (i.e. spans multiple lines). Used to decide whether chained selectors after the
   * lambda's closing brace must break onto a new line.
   */
  private fun isMultilineScopingFunction(expression: KtExpression): Boolean {
    var carry: KtExpression? = expression
    if (carry is KtQualifiedExpression && carry.receiverExpression is KtSimpleNameExpression) {
      carry = carry.selectorExpression
    }
    if (carry is KtCallExpression) {
      carry = carry.lambdaArguments.firstOrNull()?.getArgumentExpression()
    }
    if (carry is KtLabeledExpression) {
      carry = carry.baseExpression
    }
    if (carry is KtLambdaExpression) {
      return hasSourceNewlineInLambdaBody(carry)
    }
    return false
  }

  /**
   * Returns true if the source code contains a newline anywhere inside the body of
   * [lambdaExpression] — that is, between the opening `{` and the closing `}` of the function
   * literal. Used by [FormattingOptions.preserveLambdaBreaks] to keep user-authored multi-line
   * lambdas multi-line.
   */
  private fun hasSourceNewlineInLambdaBody(lambdaExpression: KtLambdaExpression): Boolean {
    val functionLiteral = lambdaExpression.functionLiteral
    for (child in functionLiteral.node.children()) {
      if (child.psi is PsiWhiteSpace && child.textContains('\n')) return true
    }
    return false
  }

  /** See [isLambdaOrScopingFunction] for examples. */
  private fun visitLambdaOrScopingFunction(expr: PsiElement?, emitLeadingBreak: Boolean = true) {
    val breakToExpr = genSym()
    val breakSpace = if (emitLeadingBreak) " " else ""
    builder.breakOp(
        Doc.FillMode.INDEPENDENT,
        breakSpace,
        expressionBreakIndent,
        Optional.of(breakToExpr),
    )

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
    builder.token("init")
    builder.space()
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
    builder.token("package")
    builder.space()
    var first = true
    for (packageName in directive.packageNames) {
      if (first) {
        first = false
      } else {
        builder.token(".")
      }
      builder.token(packageName.getIdentifier()?.text ?: packageName.getReferencedName())
    }

    builder.guessToken(";")
    builder.forcedBreak()
  }

  /** Example `import com.foo.A` */
  override fun visitImportDirective(directive: KtImportDirective) {
    builder.sync(directive)
    builder.token("import")
    builder.space()

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
      builder.space()
      builder.token("as")
      builder.space()
      builder.token(alias.text ?: fail())
    }

    // Force a newline afterwards.
    builder.guessToken(";")
    builder.forcedBreak()
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
            builder.breakOp(Doc.FillMode.UNIFIED, " ", ZERO)
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
        else -> builder.breakOp(Doc.FillMode.UNIFIED, " ", ZERO)
      }

      visit(expression.baseExpression)
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
          var first = true
          builder.breakOp(Doc.FillMode.UNIFIED, "", ZERO)
          for (value in annotation.entries) {
            if (!first) {
              builder.breakOp(Doc.FillMode.UNIFIED, " ", ZERO)
            }
            first = false

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
      builder.token("{", Doc.Token.RealOrImaginary.REAL, blockIndent, Optional.of(blockIndent))

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
    emitBracedBlock(expression) { children -> visitStatements(children) }
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
   * Example `[3]` in `a[3]` or `a[3].b` Separated since it needs to be used from a top level array
   * expression (`a[3]`) and from within a qualified chain (`a[3].b)
   */
  private fun visitArrayAccessBrackets(expression: KtArrayAccessExpression) {
    builder.block(ZERO) {
      builder.token("[")
      builder.breakOp(Doc.FillMode.UNIFIED, "", expressionBreakIndent)
      builder.block(expressionBreakIndent) {
        formatCommaSeparatedList(
            expression.indexExpressions,
            hasTrailingComma = expression.trailingComma != null,
        )
      }
    }
    builder.token("]")
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
    builder.block(ZERO) {
      builder.token(openingDelimiter)
      builder.breakOp(Doc.FillMode.UNIFIED, "", expressionBreakIndent)
      builder.block(expressionBreakIndent) {
        formatCommaSeparatedList(
            destructuringDeclaration.entries,
            hasTrailingComma,
        )
      }
    }
    builder.token(closingDelimiter)
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
      builder.breakOp(Doc.FillMode.INDEPENDENT, "", ZERO)
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

  /** Example `a is Int` or `b !is Int` */
  override fun visitIsExpression(expression: KtIsExpression) {
    builder.sync(expression)
    val openGroupBeforeLeft = expression.leftHandSide !is KtQualifiedExpression
    if (openGroupBeforeLeft) builder.open(ZERO)
    visit(expression.leftHandSide)
    if (!openGroupBeforeLeft) builder.open(ZERO)
    val parent = expression.parent
    if (
        parent is KtValueArgument ||
            parent is KtParenthesizedExpression ||
            parent is KtContainerNode
    ) {
      builder.breakOp(Doc.FillMode.UNIFIED, " ", expressionBreakIndent)
    } else {
      builder.space()
    }
    visit(expression.operationReference)
    builder.breakOp(Doc.FillMode.INDEPENDENT, " ", expressionBreakIndent)
    builder.block(expressionBreakIndent) { visit(expression.typeReference) }
    builder.close()
  }

  /** Example `a as Int` or `a as? Int` */
  override fun visitBinaryWithTypeRHSExpression(expression: KtBinaryExpressionWithTypeRHS) {
    builder.sync(expression)
    val openGroupBeforeLeft = expression.left !is KtQualifiedExpression
    if (openGroupBeforeLeft) builder.open(ZERO)
    visit(expression.left)
    if (!openGroupBeforeLeft) builder.open(ZERO)
    builder.breakOp(Doc.FillMode.UNIFIED, " ", expressionBreakIndent)
    visit(expression.operationReference)
    builder.breakOp(Doc.FillMode.INDEPENDENT, " ", expressionBreakIndent)
    builder.block(expressionBreakIndent) { visit(expression.right) }
    builder.close()
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
      formatCommaSeparatedList(
          expression.getInnerExpressions(),
          hasTrailingComma = expression.trailingComma != null,
          prefix = "[",
          postfix = "]",
          wrapInBlock = !options.manageTrailingCommas,
      )
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
      builder.token(keyword)
      builder.space()
      if (surroundConditionWithParens) {
        builder.token("(")
      }
      if (options.manageTrailingCommas) {
        builder.block(expressionBreakIndent) {
          builder.breakOp(Doc.FillMode.UNIFIED, "", ZERO)
          visit(condition)
          builder.breakOp(Doc.FillMode.UNIFIED, "", expressionBreakNegativeIndent)
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
