package org.jetbrains.ktfmt.format

import org.jetbrains.ktfmt.testutil.FormatterTestFactory

// core/src/test/resources/cases/google
class GoogleFormatTest : FormatterTestFactory("google", options = Formatter.GOOGLE_FORMAT)
