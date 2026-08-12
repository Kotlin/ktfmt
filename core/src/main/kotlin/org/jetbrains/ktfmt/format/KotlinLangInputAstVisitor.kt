package org.jetbrains.ktfmt.format

import com.google.googlejavaformat.OpsBuilder
import org.jetbrains.ktfmt.format.visitor.KotlinLangListFormatter

// New kotlinlang format under useExperimentalEngine
internal class KotlinLangInputAstVisitor(
    options: FormattingOptions,
    builder: OpsBuilder,
) : KotlinInputAstVisitor(options, builder), KotlinLangListFormatter
