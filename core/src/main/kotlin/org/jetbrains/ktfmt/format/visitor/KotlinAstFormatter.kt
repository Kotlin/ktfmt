package org.jetbrains.ktfmt.format.visitor

import com.google.googlejavaformat.FormattingError
import com.google.googlejavaformat.OpsBuilder
import com.google.googlejavaformat.Output.BreakTag
import java.util.ArrayDeque
import org.jetbrains.kotlin.com.intellij.psi.PsiElement
import org.jetbrains.kotlin.psi.KtAnnotatedExpression
import org.jetbrains.kotlin.psi.KtAnnotation
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtAnnotationUseSiteTarget
import org.jetbrains.kotlin.psi.KtArrayAccessExpression
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtBinaryExpressionWithTypeRHS
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtBreakExpression
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtCallableReferenceExpression
import org.jetbrains.kotlin.psi.KtCatchClause
import org.jetbrains.kotlin.psi.KtClassBody
import org.jetbrains.kotlin.psi.KtClassInitializer
import org.jetbrains.kotlin.psi.KtClassLiteralExpression
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtCollectionLiteralExpression
import org.jetbrains.kotlin.psi.KtConstantExpression
import org.jetbrains.kotlin.psi.KtConstructorDelegationCall
import org.jetbrains.kotlin.psi.KtContextReceiverList
import org.jetbrains.kotlin.psi.KtContinueExpression
import org.jetbrains.kotlin.psi.KtDelegatedSuperTypeEntry
import org.jetbrains.kotlin.psi.KtDestructuringDeclaration
import org.jetbrains.kotlin.psi.KtDestructuringDeclarationEntry
import org.jetbrains.kotlin.psi.KtDoWhileExpression
import org.jetbrains.kotlin.psi.KtDynamicType
import org.jetbrains.kotlin.psi.KtEnumEntry
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtFileAnnotationList
import org.jetbrains.kotlin.psi.KtFinallySection
import org.jetbrains.kotlin.psi.KtForExpression
import org.jetbrains.kotlin.psi.KtFunctionType
import org.jetbrains.kotlin.psi.KtIfExpression
import org.jetbrains.kotlin.psi.KtImportDirective
import org.jetbrains.kotlin.psi.KtImportList
import org.jetbrains.kotlin.psi.KtIntersectionType
import org.jetbrains.kotlin.psi.KtIsExpression
import org.jetbrains.kotlin.psi.KtLabeledExpression
import org.jetbrains.kotlin.psi.KtLambdaArgument
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtModifierList
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtNullableType
import org.jetbrains.kotlin.psi.KtPackageDirective
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtParameterList
import org.jetbrains.kotlin.psi.KtParenthesizedExpression
import org.jetbrains.kotlin.psi.KtPostfixExpression
import org.jetbrains.kotlin.psi.KtPrefixExpression
import org.jetbrains.kotlin.psi.KtPrimaryConstructor
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtQualifiedExpression
import org.jetbrains.kotlin.psi.KtReferenceExpression
import org.jetbrains.kotlin.psi.KtReturnExpression
import org.jetbrains.kotlin.psi.KtScript
import org.jetbrains.kotlin.psi.KtSecondaryConstructor
import org.jetbrains.kotlin.psi.KtSimpleNameExpression
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.psi.KtSuperExpression
import org.jetbrains.kotlin.psi.KtSuperTypeCallEntry
import org.jetbrains.kotlin.psi.KtSuperTypeList
import org.jetbrains.kotlin.psi.KtThisExpression
import org.jetbrains.kotlin.psi.KtThrowExpression
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
import org.jetbrains.kotlin.psi.KtWhenExpression
import org.jetbrains.kotlin.psi.KtWhileExpression
import org.jetbrains.ktfmt.format.FormattingOptions
import org.jetbrains.ktfmt.format.KotlinInputAstVisitor
import org.jetbrains.ktfmt.format.KotlinLangInputAstVisitor

/**
 * Super-interface for gradual evolution of ktfmt.
 *
 * Impl note: Before that, it was a single 3k LOC KotlinInputAstVisitor which was fine as it's the
 * only implementation.
 *
 * Now we are gradually experimenting with new kotlinlang style, so we need an independent yet
 * not-fully-copypasted reusable implementation, hence the current approach:
 *
 * - KotlinAstFormatter is the base interface for everything. It's fully abstract
 * - We branch it out into specialized interfaces, for example:
 *     - TypeFormatter
 *     - ListFormatter
 *     - AnnotationFormatter
 * - They provide (using default implementations in interfaces) the formatting only for specific
 *   parts of Kotlin. It just makes thing more maintainable/separable
 * - We have two final implementations:
 *     - [Original ktfmt][KotlinInputAstVisitor] implementation: it just implements all the
 *       interfaces. Ovderrides everything, the old implementation is still here
 *     - [KotlinLang one][KotlinLangInputAstVisitor] that, if needed, re-implements some specific
 *       subinterfaces (e.g. KotlinLangListFormatter) and overrides things that don't have a
 *       subinterface
 *
 * - To make it work with clashing declaration, we are introducing a one more intermediate vistior
 *   layer (the current one), so things like ListFormatter can actually redefine the behaviour of
 *   KotlinInputAstVisitor
 *
 * Viola, we can evolve KLIAV incrementally!
 *
 * Another important thing:
 * - We have an intermediary AbstractFormatterVisitor to have less dependencies on PSI and be able
 *   to gradually switch to lighttree or a KMP parser
 */
interface KotlinAstFormatter {
  val options: FormattingOptions
  val builder: OpsBuilder

  val blockIndent: Indentation.Const
  val expressionBreakIndent: Indentation.Const

  /** A record of whether we have visited into an expression. */
  val inExpression: ArrayDeque<Boolean>

  var inImport: Boolean

  fun format(element: PsiElement?)

  fun formatKtFile(file: KtFile)

  fun formatKtScript(script: KtScript)

  fun formatStatement(statement: PsiElement)

  fun formatStatements(statements: Array<PsiElement>)

  fun formatNamedFunction(function: KtNamedFunction) {
    TODO("Unreachable code path")
  }

  fun formatTypeReference(type: KtTypeReference)

  fun formatDynamicType(type: KtDynamicType)

  fun formatNullableType(type: KtNullableType)

  fun formatUserType(type: KtUserType)

  fun formatIntersectionType(type: KtIntersectionType)

  fun formatTypeProjection(type: KtTypeProjection)

  fun formatTypeArgumentList(list: KtTypeArgumentList)

  fun formatTypeParameterList(list: KtTypeParameterList)

  fun formatTypeParameter(parameter: KtTypeParameter)

  fun formatTypeConstraintList(list: KtTypeConstraintList)

  fun formatTypeConstraint(constraint: KtTypeConstraint)

  fun formatFunctionType(type: KtFunctionType)

  fun formatClassOrObject(classOrObject: KtClassOrObject) {
    TODO("Unreachable code path")
  }

  fun formatProperty(property: KtProperty) {
    TODO("Unreachable code path")
  }

  fun formatPrimaryConstructor(constructor: KtPrimaryConstructor) {
    TODO("Unreachable code path")
  }

  fun formatSecondaryConstructor(constructor: KtSecondaryConstructor) {
    TODO("Unreachable code path")
  }

  fun formatConstructorDelegationCall(call: KtConstructorDelegationCall) {
    TODO("Unreachable code path")
  }

  fun formatClassInitializer(initializer: KtClassInitializer) {
    TODO("Unreachable code path")
  }

  /** @param wrapInBlock if true places the argument expression in a block. */
  fun formatArgument(
      argument: KtValueArgument,
      wrapInBlock: Boolean,
      brokeBeforeBrace: BreakTag?,
  )

  fun formatSuperTypeList(list: KtSuperTypeList)

  fun formatSuperTypeCallEntry(call: KtSuperTypeCallEntry) {
    TODO("Unreachable code path")
  }

  fun formatDelegatedSuperTypeEntry(specifier: KtDelegatedSuperTypeEntry) {
    TODO("Unreachable code path")
  }

  fun formatClassBody(body: KtClassBody) {
    TODO("Unreachable code path")
  }

  fun formatValueArgumentList(list: KtValueArgumentList): BreakTag?

  fun formatModifierList(list: KtModifierList)

  fun formatContextReceiverList(contextReceiverList: KtContextReceiverList)

  fun formatParameterList(list: KtParameterList)

  fun formatParameter(parameter: KtParameter) {
    TODO("Unreachable code path")
  }

  fun formatQualifiedExpression(expression: KtQualifiedExpression)

  fun formatCallExpression(callExpression: KtCallExpression) {
    TODO("Unreachable code path")
  }

  /**
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
  fun formatLambdaExpression(
      lambdaExpression: KtLambdaExpression,
      brokeBeforeBrace: BreakTag?,
  )

  fun formatThisExpression(expression: KtThisExpression)

  fun formatSimpleNameExpression(expression: KtSimpleNameExpression)

  fun formatReferenceExpression(expression: KtReferenceExpression)

  fun formatReturnExpression(expression: KtReturnExpression) {
    TODO("Unreachable code path")
  }

  fun formatBinaryExpression(expression: KtBinaryExpression)

  fun formatPostfixExpression(expression: KtPostfixExpression)

  fun formatPrefixExpression(expression: KtPrefixExpression)

  fun formatLabeledExpression(expression: KtLabeledExpression)

  fun formatConstantExpression(expression: KtConstantExpression)

  fun formatParenthesizedExpression(expression: KtParenthesizedExpression)

  fun formatWhenExpression(expression: KtWhenExpression) {
    TODO("Unreachable code path")
  }

  fun formatBlockExpression(expression: KtBlockExpression) {
    TODO("Unreachable code path")
  }

  fun formatWhenConditionWithExpression(condition: KtWhenConditionWithExpression) {
    TODO("Unreachable code path")
  }

  fun formatWhenConditionIsPattern(condition: KtWhenConditionIsPattern) {
    TODO("Unreachable code path")
  }

  fun formatWhenConditionInRange(condition: KtWhenConditionInRange) {
    TODO("Unreachable code path")
  }

  fun formatIfExpression(expression: KtIfExpression) {
    TODO("Unreachable code path")
  }

  fun formatArrayAccessExpression(expression: KtArrayAccessExpression)

  fun formatStringTemplateExpression(expression: KtStringTemplateExpression)

  fun formatSuperExpression(expression: KtSuperExpression)

  fun formatForExpression(expression: KtForExpression) {
    TODO("Unreachable code path")
  }

  fun formatWhileExpression(expression: KtWhileExpression) {
    TODO("Unreachable code path")
  }

  fun formatDoWhileExpression(expression: KtDoWhileExpression) {
    TODO("Unreachable code path")
  }

  fun formatBreakExpression(expression: KtBreakExpression) {
    TODO("Unreachable code path")
  }

  fun formatContinueExpression(expression: KtContinueExpression) {
    TODO("Unreachable code path")
  }

  fun formatCallableReferenceExpression(expression: KtCallableReferenceExpression)

  fun formatClassLiteralExpression(expression: KtClassLiteralExpression)

  fun formatIsExpression(expression: KtIsExpression)

  fun formatBinaryWithTypeRHSExpression(expression: KtBinaryExpressionWithTypeRHS)

  fun formatCollectionLiteralExpression(expression: KtCollectionLiteralExpression)

  fun formatTryExpression(expression: KtTryExpression) {
    TODO("Unreachable code path")
  }

  fun formatCatchSection(catchClause: KtCatchClause) {
    TODO("Unreachable code path")
  }

  fun formatFinallySection(finallySection: KtFinallySection) {
    TODO("Unreachable code path")
  }

  fun formatThrowExpression(expression: KtThrowExpression) {
    TODO("Unreachable code path")
  }

  fun formatEnumEntry(enumEntry: KtEnumEntry) {
    TODO("Unreachable code path")
  }

  fun formatTypeAlias(typeAlias: KtTypeAlias) {
    TODO("Unreachable code path")
  }

  fun formatDestructuringDeclaration(destructuringDeclaration: KtDestructuringDeclaration) {
    TODO("Unreachable code path")
  }

  fun formatDestructuringDeclarationEntry(multiDeclarationEntry: KtDestructuringDeclarationEntry) {
    TODO("Unreachable code path")
  }

  fun formatPackageDirective(directive: KtPackageDirective)

  fun formatImportList(importList: KtImportList)

  fun formatImportDirective(directive: KtImportDirective)

  fun formatAnnotatedExpression(expression: KtAnnotatedExpression)

  fun formatAnnotation(annotation: KtAnnotation)

  fun formatAnnotationUseSiteTarget(annotationTarget: KtAnnotationUseSiteTarget)

  fun formatAnnotationEntry(annotationEntry: KtAnnotationEntry)

  fun formatFileAnnotationList(fileAnnotationList: KtFileAnnotationList)

  /**
   * format each element in [list], with comma (,) {} tokens in-between.
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
   * ) {}
   * ```
   *
   * @param forceMultiline if true, each element is placed on its own line (even if they could've
   *   fit in a single line) {}, and a trailing comma is emitted.
   *
   * Example:
   * ```
   * a,
   * b,
   * ```
   *
   * @param wrapInBlock if true, place all the elements in a block. When there's no
   *   [emitLeadingBreak], this will be negatively indented. Note that the [prefix] and [postfix]
   *   aren't included in the block.
   * @param emitLeadingBreak if true, break before the first element.
   * @param prefix if provided, emit this before the first element.
   * @param postfix if provided, emit this after the last element (or trailing comma) {}.
   * @param breakAfterPrefix if true, emit a break after [prefix], but before the start of the
   *   block.
   * @param breakBeforePostfix if true, place a break after the last element. Redundant when
   *   [forceMultiline] is true.
   * @return a [BreakTag] which can tell you if a break was taken, but only when the list doesn't
   *   terminate in a negative closing indent.
   *
   * Example 1, this returns a BreakTag which tells you a break wasn't taken:
   * ```
   * (arg1, arg2) {}
   * ```
   *
   * Example 2, this returns a BreakTag which tells you a break WAS taken:
   * ```
   * (
   *     arg1,
   *     arg2) {}
   * ```
   *
   * Example 3, this returns null:
   * ```
   * (
   *     arg1,
   *     arg2,
   * ) {}
   * ```
   *
   * Example 4, this also returns null (similar to example 2, but Google style) {}:
   * ```
   * (
   *     arg1,
   *     arg2
   * ) {}
   * ```
   */
  fun formatCommaSeparatedList(
      list: Iterable<PsiElement>,
      forceMultiline: Boolean = false,
      wrapInBlock: Boolean = true,
      emitLeadingBreak: Boolean = true,
      prefix: String? = null,
      postfix: String? = null,
      breakAfterPrefix: Boolean = true,
      breakBeforePostfix: Boolean = options.manageTrailingCommas,
  ): BreakTag?

  /**
   * Format the right-hand side of an initializer expression, i.e. the expression after `=`
   * (inclusively)
   *
   * @param assignmentOp The symbol that separates the initializer from the expression, see
   *   [org.jetbrains.kotlin.lexer.KtTokens.ALL_ASSIGNMENTS]
   */
  fun formatInitializerExpression(
      initializer: KtExpression,
      assignmentOp: String = "=",
  )

  /** See [isLambdaOrScopingFunction] for examples. */
  fun formatLambdaOrScopingFunction(expr: PsiElement?, emitLeadingBreak: Boolean = true)

  fun formatFunctionCall(
      callee: KtExpression?,
      typeArgumentList: KtTypeArgumentList?,
      argumentList: KtValueArgumentList?,
      trailingLambda: KtLambdaArgument?,
      argumentsIndent: Indentation = expressionBreakIndent,
      lambdaIndent: Indentation = Indentation.ZERO,
  )

  /**
   * Emit a `foo(\n ...,\n).bar().baz()` style chain whose innermost receiver is a block-like
   * multiline call: render the receiver call normally (so its closing paren sits at the surrounding
   * indent), then emit each `.selector` on its own line, indented by [expressionBreakIndent].
   */
  fun formatChainedBlockLikeCall(
      expression: KtQualifiedExpression,
      emitLeadingBreak: Boolean,
  )

  /**
   * Emit `runnnnn { ... }.baz().qux()` style: render the innermost scoping-function receiver
   * block-like (so the lambda braces sit at the surrounding indent), then emit each `.selector`
   * after the closing brace as a chained continuation indented by [blockIndent].
   *
   * When the receiver lambda spans multiple lines in the source we force the chained selectors onto
   * their own line; a single-line lambda stays joined to its chained call.
   */
  fun formatChainedScopingFunction(
      expression: KtQualifiedExpression,
      emitLeadingBreak: Boolean,
  )

  /**
   * markForPartialFormat is used to delineate the smallest areas of code that must be formatted
   * together.
   *
   * When only parts of the code are being formatted, the requested area is expanded until it's
   * covered by an area marked by this method.
   */
  fun markForPartialFormat() {
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
  fun fail(message: String = "Unexpected"): Nothing {
    throw FormattingError(builder.diagnostic(message))
  }
}
