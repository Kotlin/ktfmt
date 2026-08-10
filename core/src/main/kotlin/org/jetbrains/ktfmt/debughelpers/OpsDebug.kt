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

package org.jetbrains.ktfmt.debughelpers

import com.google.common.collect.ImmutableList
import com.google.common.collect.Range
import com.google.googlejavaformat.CloseOp
import com.google.googlejavaformat.CommentsHelper
import com.google.googlejavaformat.Doc
import com.google.googlejavaformat.Input
import com.google.googlejavaformat.Op
import com.google.googlejavaformat.OpenOp
import com.google.googlejavaformat.OpsBuilder
import com.google.googlejavaformat.Output
import java.util.regex.Pattern

/** A regex to extract the indent size from OpenOp.toString(). */
private val OPENOP_STRING_FORM_REGEX = Pattern.compile("""OpenOp\{plusIndent=Const\{n=(\d+)}}""")

/**
 * printOps prints a list of Ops in a hierarchical way.
 *
 * It's useful when debugging incorrect newlines.
 */
fun printOps(ops: ImmutableList<Op>) {
  println("Ops: ")
  var indent = 0
  for (op in ops) {
    val line =
        when (op) {
          is OpenOp -> {
            val matcher = OPENOP_STRING_FORM_REGEX.matcher(op.toString())
            if (matcher.matches()) {
              val opIndent = matcher.group(1)
              "[ " + if (opIndent != "0") opIndent else ""
            } else {
              "[ $op"
            }
          }
          is CloseOp -> "]"
          is Doc.Token -> {
            var result: String? = ""
            val output =
                object : Output() {
                  override fun indent(indent: Int) = Unit

                  override fun blankLine(k: Int, wanted: OpsBuilder.BlankLineWanted?) = Unit

                  override fun markForPartialFormat(start: Input.Token?, end: Input.Token?) = Unit

                  override fun getCommentsHelper(): CommentsHelper {
                    throw Throwable()
                  }

                  override fun append(text: String?, range: Range<Int>?) {
                    result = text
                  }
                }
            op.write(output)
            """"$result""""
          }
          else -> {
            val result = op.toString()
            if (result == "Space{}") "\" \"" else result
          }
        }
    if (op is CloseOp) {
      indent--
    }
    repeat(2 * indent) { print(" ") }
    if (op is OpenOp) {
      indent++
    }
    println(line)
  }
  println()
}
