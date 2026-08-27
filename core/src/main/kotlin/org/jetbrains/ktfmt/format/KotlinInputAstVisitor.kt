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

@file:Suppress("DEPRECATION")

package org.jetbrains.ktfmt.format

import com.google.googlejavaformat.OpsBuilder
import org.jetbrains.ktfmt.format.visitor.AbstractFormatterVisitor
import org.jetbrains.ktfmt.format.visitor.AnnotationFormatter
import org.jetbrains.ktfmt.format.visitor.CallFormatter
import org.jetbrains.ktfmt.format.visitor.ControlFlowExpressionFormatter
import org.jetbrains.ktfmt.format.visitor.DeclarationFormatter
import org.jetbrains.ktfmt.format.visitor.ExpressionFormatter
import org.jetbrains.ktfmt.format.visitor.FileFormatter
import org.jetbrains.ktfmt.format.visitor.Indentation
import org.jetbrains.ktfmt.format.visitor.ListFormatter
import org.jetbrains.ktfmt.format.visitor.TypeFormatter

/** An AST visitor that builds a stream of {@link Op}s to format. */
open class KotlinInputAstVisitor(
    override val options: FormattingOptions,
    override val builder: OpsBuilder,
) :
    AbstractFormatterVisitor(),
    AnnotationFormatter,
    CallFormatter,
    ControlFlowExpressionFormatter,
    DeclarationFormatter,
    ExpressionFormatter,
    FileFormatter,
    ListFormatter,
    TypeFormatter {

  /** Standard indentation for a block */
  override val blockIndent: Indentation.Const = Indentation.Const(options.blockIndent)

  /**
   * Standard indentation for a long expression or function call, it is different than block
   * indentation on purpose
   */
  override val expressionBreakIndent: Indentation.Const =
      Indentation.Const(options.continuationIndent)
}
