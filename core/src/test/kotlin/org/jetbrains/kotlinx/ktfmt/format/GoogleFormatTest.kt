package org.jetbrains.kotlinx.ktfmt.format

import org.jetbrains.kotlinx.ktfmt.testutil.FormatterTestFactory

// core/src/test/resources/cases/google
class GoogleFormatTest : FormatterTestFactory("google", options = Formatter.GOOGLE_FORMAT)
