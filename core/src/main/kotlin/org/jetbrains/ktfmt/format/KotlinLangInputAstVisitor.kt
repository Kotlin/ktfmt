package org.jetbrains.ktfmt.format

import com.google.googlejavaformat.OpsBuilder
import org.jetbrains.ktfmt.format.visitor.AbstractKotlinFormatter
import org.jetbrains.ktfmt.format.visitor.kotlinlang.KotlinLangAnnotationFormatterImpl
import org.jetbrains.ktfmt.format.visitor.kotlinlang.KotlinLangListFormatterImpl

internal class KotlinLangInputAstVisitor(
    options: FormattingOptions,
    builder: OpsBuilder,
) :
    AbstractKotlinFormatter(
        options,
        builder,
        annotationFormatter = KotlinLangAnnotationFormatterImpl(),
        listFormatter = KotlinLangListFormatterImpl(),
    )
