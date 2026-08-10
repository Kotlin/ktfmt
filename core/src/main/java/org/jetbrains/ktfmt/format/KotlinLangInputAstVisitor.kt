package org.jetbrains.ktfmt.format

import com.google.googlejavaformat.OpsBuilder

internal class KotlinLangInputAstVisitor(
    options: FormattingOptions,
    builder: OpsBuilder,
) : KotlinInputAstVisitor(options, builder) {
  override val forceAnnotationBreaks: Boolean = true
}
