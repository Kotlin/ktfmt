package org.jetbrains.ktfmt.format.visitor

import com.google.common.base.Throwables
import com.google.googlejavaformat.FormattingError
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
import org.jetbrains.kotlin.psi.KtWhenExpression
import org.jetbrains.kotlin.psi.KtWhileExpression

abstract class AbstractFormatterVisitor : KtTreeVisitorVoid(), KotlinAstFormatter {

  override fun format(element: PsiElement?) {
    element?.accept(this)
  }

  override fun visitKtFile(file: KtFile) {
    formatKtFile(file)
  }

  override fun visitScript(script: KtScript) {
    formatKtScript(script)
  }

  override fun visitNamedFunction(function: KtNamedFunction) {
    formatNamedFunction(function)
  }

  override fun visitTypeReference(type: KtTypeReference) {
    formatTypeReference(type)
  }

  override fun visitDynamicType(type: KtDynamicType) {
    formatDynamicType(type)
  }

  override fun visitNullableType(type: KtNullableType) {
    formatNullableType(type)
  }

  override fun visitUserType(type: KtUserType) {
    formatUserType(type)
  }

  override fun visitIntersectionType(type: KtIntersectionType) {
    formatIntersectionType(type)
  }

  override fun visitTypeProjection(type: KtTypeProjection) {
    formatTypeProjection(type)
  }

  override fun visitTypeArgumentList(list: KtTypeArgumentList) {
    formatTypeArgumentList(list)
  }

  override fun visitTypeParameterList(list: KtTypeParameterList) {
    formatTypeParameterList(list)
  }

  override fun visitTypeParameter(parameter: KtTypeParameter) {
    formatTypeParameter(parameter)
  }

  override fun visitTypeConstraintList(list: KtTypeConstraintList) {
    formatTypeConstraintList(list)
  }

  override fun visitTypeConstraint(constraint: KtTypeConstraint) {
    formatTypeConstraint(constraint)
  }

  override fun visitFunctionType(type: KtFunctionType) {
    formatFunctionType(type)
  }

  override fun visitClassOrObject(classOrObject: KtClassOrObject) {
    formatClassOrObject(classOrObject)
  }

  override fun visitProperty(property: KtProperty) {
    formatProperty(property)
  }

  override fun visitPrimaryConstructor(constructor: KtPrimaryConstructor) {
    formatPrimaryConstructor(constructor)
  }

  override fun visitSecondaryConstructor(constructor: KtSecondaryConstructor) {
    formatSecondaryConstructor(constructor)
  }

  override fun visitConstructorDelegationCall(call: KtConstructorDelegationCall) {
    formatConstructorDelegationCall(call)
  }

  override fun visitClassInitializer(initializer: KtClassInitializer) {
    formatClassInitializer(initializer)
  }

  override fun visitArgument(argument: KtValueArgument) {
    formatArgument(argument, wrapInBlock = true, brokeBeforeBrace = null)
  }

  override fun visitSuperTypeList(list: KtSuperTypeList) {
    formatSuperTypeList(list)
  }

  override fun visitSuperTypeCallEntry(call: KtSuperTypeCallEntry) {
    formatSuperTypeCallEntry(call)
  }

  override fun visitDelegatedSuperTypeEntry(specifier: KtDelegatedSuperTypeEntry) {
    formatDelegatedSuperTypeEntry(specifier)
  }

  override fun visitClassBody(body: KtClassBody) {
    formatClassBody(body)
  }

  override fun visitValueArgumentList(list: KtValueArgumentList) {
    formatValueArgumentList(list)
  }

  override fun visitModifierList(list: KtModifierList) {
    formatModifierList(list)
  }

  override fun visitContextReceiverList(contextReceiverList: KtContextReceiverList) {
    formatContextReceiverList(contextReceiverList)
  }

  override fun visitParameterList(list: KtParameterList) {
    formatParameterList(list)
  }

  override fun visitParameter(parameter: KtParameter) {
    formatParameter(parameter)
  }

  override fun visitQualifiedExpression(expression: KtQualifiedExpression) {
    formatQualifiedExpression(expression)
  }

  override fun visitCallExpression(callExpression: KtCallExpression) {
    formatCallExpression(callExpression)
  }

  override fun visitLambdaExpression(lambdaExpression: KtLambdaExpression) {
    formatLambdaExpression(lambdaExpression, brokeBeforeBrace = null)
  }

  override fun visitThisExpression(expression: KtThisExpression) {
    formatThisExpression(expression)
  }

  override fun visitSimpleNameExpression(expression: KtSimpleNameExpression) {
    formatSimpleNameExpression(expression)
  }

  override fun visitReferenceExpression(expression: KtReferenceExpression) {
    formatReferenceExpression(expression)
  }

  override fun visitReturnExpression(expression: KtReturnExpression) {
    formatReturnExpression(expression)
  }

  override fun visitBinaryExpression(expression: KtBinaryExpression) {
    formatBinaryExpression(expression)
  }

  override fun visitPostfixExpression(expression: KtPostfixExpression) {
    formatPostfixExpression(expression)
  }

  override fun visitPrefixExpression(expression: KtPrefixExpression) {
    formatPrefixExpression(expression)
  }

  override fun visitLabeledExpression(expression: KtLabeledExpression) {
    formatLabeledExpression(expression)
  }

  override fun visitConstantExpression(expression: KtConstantExpression) {
    formatConstantExpression(expression)
  }

  override fun visitParenthesizedExpression(expression: KtParenthesizedExpression) {
    formatParenthesizedExpression(expression)
  }

  override fun visitWhenExpression(expression: KtWhenExpression) {
    formatWhenExpression(expression)
  }

  override fun visitBlockExpression(expression: KtBlockExpression) {
    formatBlockExpression(expression)
  }

  override fun visitWhenConditionWithExpression(condition: KtWhenConditionWithExpression) {
    formatWhenConditionWithExpression(condition)
  }

  override fun visitWhenConditionIsPattern(condition: KtWhenConditionIsPattern) {
    formatWhenConditionIsPattern(condition)
  }

  override fun visitWhenConditionInRange(condition: KtWhenConditionInRange) {
    formatWhenConditionInRange(condition)
  }

  override fun visitIfExpression(expression: KtIfExpression) {
    formatIfExpression(expression)
  }

  override fun visitArrayAccessExpression(expression: KtArrayAccessExpression) {
    formatArrayAccessExpression(expression)
  }

  override fun visitStringTemplateExpression(expression: KtStringTemplateExpression) {
    formatStringTemplateExpression(expression)
  }

  override fun visitSuperExpression(expression: KtSuperExpression) {
    formatSuperExpression(expression)
  }

  override fun visitForExpression(expression: KtForExpression) {
    formatForExpression(expression)
  }

  override fun visitWhileExpression(expression: KtWhileExpression) {
    formatWhileExpression(expression)
  }

  override fun visitDoWhileExpression(expression: KtDoWhileExpression) {
    formatDoWhileExpression(expression)
  }

  override fun visitBreakExpression(expression: KtBreakExpression) {
    formatBreakExpression(expression)
  }

  override fun visitContinueExpression(expression: KtContinueExpression) {
    formatContinueExpression(expression)
  }

  override fun visitCallableReferenceExpression(expression: KtCallableReferenceExpression) {
    formatCallableReferenceExpression(expression)
  }

  override fun visitClassLiteralExpression(expression: KtClassLiteralExpression) {
    formatClassLiteralExpression(expression)
  }

  override fun visitIsExpression(expression: KtIsExpression) {
    formatIsExpression(expression)
  }

  override fun visitBinaryWithTypeRHSExpression(expression: KtBinaryExpressionWithTypeRHS) {
    formatBinaryWithTypeRHSExpression(expression)
  }

  override fun visitCollectionLiteralExpression(expression: KtCollectionLiteralExpression) {
    formatCollectionLiteralExpression(expression)
  }

  override fun visitTryExpression(expression: KtTryExpression) {
    formatTryExpression(expression)
  }

  override fun visitCatchSection(catchClause: KtCatchClause) {
    formatCatchSection(catchClause)
  }

  override fun visitFinallySection(finallySection: KtFinallySection) {
    formatFinallySection(finallySection)
  }

  override fun visitThrowExpression(expression: KtThrowExpression) {
    formatThrowExpression(expression)
  }

  override fun visitEnumEntry(enumEntry: KtEnumEntry) {
    formatEnumEntry(enumEntry)
  }

  override fun visitTypeAlias(typeAlias: KtTypeAlias) {
    formatTypeAlias(typeAlias)
  }

  override fun visitDestructuringDeclaration(destructuringDeclaration: KtDestructuringDeclaration) {
    formatDestructuringDeclaration(destructuringDeclaration)
  }

  override fun visitDestructuringDeclarationEntry(
      multiDeclarationEntry: KtDestructuringDeclarationEntry,
  ) {
    formatDestructuringDeclarationEntry(multiDeclarationEntry)
  }

  override fun visitPackageDirective(directive: KtPackageDirective) {
    formatPackageDirective(directive)
  }

  override fun visitImportList(importList: KtImportList) {
    formatImportList(importList)
  }

  override fun visitImportDirective(directive: KtImportDirective) {
    formatImportDirective(directive)
  }

  override fun visitAnnotatedExpression(expression: KtAnnotatedExpression) {
    formatAnnotatedExpression(expression)
  }

  override fun visitAnnotation(annotation: KtAnnotation) {
    formatAnnotation(annotation)
  }

  override fun visitAnnotationUseSiteTarget(
      annotationTarget: KtAnnotationUseSiteTarget,
      data: Void?,
  ): Void? {
    formatAnnotationUseSiteTarget(annotationTarget)
    return null
  }

  override fun visitAnnotationEntry(annotationEntry: KtAnnotationEntry) {
    formatAnnotationEntry(annotationEntry)
  }

  override fun visitFileAnnotationList(
      fileAnnotationList: KtFileAnnotationList,
      data: Void?,
  ): Void? {
    formatFileAnnotationList(fileAnnotationList)
    return null
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
}
