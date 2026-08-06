package com.facebook.ktfmt.format

import com.google.googlejavaformat.OpsBuilder

internal class KotlinLangInputAstVisitor(
    options: FormattingOptions,
    builder: OpsBuilder,
) : KotlinInputAstVisitor(options, builder) {
  override val forceAnnotationBreaks: Boolean = true
  override val forceLineBreakAfterAssignment: Boolean = false
  override val forceLineBreakAfterNamedParameter: Boolean = false
  override val forceLineBreakAfterSupertypeColon: Boolean = false
  override val forceLineBreakBeforeAccessors: Boolean = false
  override val hugBlockLikeInfixCalls: Boolean = true
  override val hugCallsWithTrailingLambda: Boolean = true
  override val hugChainsAfterTrailingLambda: Boolean = true
  override val hugWhenExpressions: Boolean = true
  override val indentBooleanConditions: Boolean = false
  override val forceLineBreakInWhenConditionList: Boolean = false
  override val forceLineBreaksBetweenEmptyMethods: Boolean = false
}
