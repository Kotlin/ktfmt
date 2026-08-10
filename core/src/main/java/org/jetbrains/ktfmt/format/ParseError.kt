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

package org.jetbrains.ktfmt.format

import org.jetbrains.kotlin.com.intellij.openapi.util.text.LineColumn
import org.jetbrains.kotlin.com.intellij.psi.PsiElement

class ParseError(val errorDescription: String, val lineColumn: LineColumn) :
    IllegalArgumentException(
        "${lineColumn.line + 1}:${lineColumn.column + 1}: error: $errorDescription",
    ) {

  constructor(
      errorDescription: String,
      element: PsiElement,
  ) : this(errorDescription, positionOf(element))

  companion object {
    private fun positionOf(element: PsiElement): LineColumn {
      val doc = checkNotNull(element.containingFile.viewProvider.document)
      val offset = element.textOffset
      val lineZero = doc.getLineNumber(offset)
      val colZero = offset - doc.getLineStartOffset(lineZero)
      return LineColumn.of(lineZero, colZero)
    }
  }
}
