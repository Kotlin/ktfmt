package org.jetbrains.kotlinx.ktfmt.format

import org.jetbrains.kotlinx.ktfmt.testutil.FormatterTestFactory

// core/src/test/resources/cases/kotlinlang
class KotlinlangFormatTest :
    FormatterTestFactory("kotlinlang", options = Formatter.KOTLINLANG_FORMAT)
