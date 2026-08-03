package com.facebook.ktfmt.format

import com.google.googlejavaformat.OpsBuilder

internal class KotlinLangInputAstVisitor(
    options: FormattingOptions,
    builder: OpsBuilder,
) : KotlinInputAstVisitor(options, builder) {
  override val forceAnnotationBreaks: Boolean = true
}
