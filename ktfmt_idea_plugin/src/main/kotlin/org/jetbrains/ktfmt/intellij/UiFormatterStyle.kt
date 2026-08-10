/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.ktfmt.intellij

import org.jetbrains.ktfmt.format.Formatter.GOOGLE_FORMAT
import org.jetbrains.ktfmt.format.Formatter.KOTLINLANG_FORMAT
import org.jetbrains.ktfmt.format.Formatter.META_FORMAT
import org.jetbrains.ktfmt.format.FormattingOptions

/** Configuration options for the formatting style. */
internal enum class UiFormatterStyle(private val description: String) {
  Meta("Meta (default)"),
  Google("Google (internal)"),
  KotlinLang("Kotlinlang"),
  Custom("Custom");

  override fun toString(): String = description

  companion object {
    internal fun getStandardFormattingOptions(style: UiFormatterStyle): FormattingOptions =
        when (style) {
          Meta -> META_FORMAT
          Google -> GOOGLE_FORMAT
          KotlinLang -> KOTLINLANG_FORMAT
          Custom -> error("Custom style formatting options should be retrieved separately")
        }
  }
}
