package org.jetbrains.ktfmt.format

import org.jetbrains.ktfmt.testutil.FormatterTestFactory

// core/src/test/resources/cases/kotlinlang
class KotlinlangFormatTest :
    FormatterTestFactory("kotlinlang", options = Formatter.KOTLINLANG_FORMAT)
