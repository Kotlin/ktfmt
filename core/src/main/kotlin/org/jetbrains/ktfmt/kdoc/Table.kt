/*
 * Portions Copyright (c) Meta Platforms, Inc. and affiliates.
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

/*
 * Copyright (c) Tor Norbye.
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

package org.jetbrains.ktfmt.kdoc

import kotlin.math.max

class Table(
    private val columns: Int,
    private val widths: List<Int>,
    private val rows: List<Row>,
    private val align: List<Align>,
    private val original: List<String>,
) {
  fun original(): List<String> {
    return original
  }

  /**
   * Format the table. Note that table rows cannot be broken into multiple lines in Markdown tables,
   * so the [maxWidth] here is used to decide whether to add padding around the table only, and it's
   * quite possible for the table to format to wider lengths than [maxWidth].
   */
  fun format(maxWidth: Int = Integer.MAX_VALUE): List<String> {
    val tableMaxWidth =
        2 + widths.sumOf { it + 2 } // +2: "| " in each cell and final " |" on the right

    val pad = tableMaxWidth <= maxWidth
    val lines = mutableListOf<String>()
    for (i in rows.indices) {
      val sb = StringBuilder()
      val row = rows[i]
      for (column in 0 until row.cells.size) {
        sb.append('|')
        if (pad) {
          sb.append(' ')
        }
        val cell = row.cells[column]
        val width = widths[column]
        val s =
            if (align[column] == Align.CENTER && i > 0) {
              String.format(
                  "%-${width}s",
                  String.format("%${cell.length + (width - cell.length) / 2}s", cell),
              )
            } else if (align[column] == Align.RIGHT && i > 0) {
              String.format("%${width}s", cell)
            } else {
              String.format("%-${width}s", cell)
            }
        sb.append(s)
        if (pad) {
          sb.append(' ')
        }
      }
      sb.append('|')
      lines.add(sb.toString())
      sb.clear()

      if (i == 0) {
        for (column in 0 until row.cells.size) {
          sb.append('|')
          var width = widths[column]
          if (align[column] != Align.LEFT) {
            width--
            if (align[column] == Align.CENTER) {
              sb.append(':')
              width--
            }
          }
          if (pad) {
            sb.append('-')
          }
          val s = "-".repeat(width)
          sb.append(s)
          if (pad) {
            sb.append('-')
          }
          if (align[column] != Align.LEFT) {
            sb.append(':')
          }
        }
        sb.append('|')
        lines.add(sb.toString())
        sb.clear()
      }
    }

    return lines
  }

  companion object {
    /**
     * If the line starting at index [start] begins a table, return that table as well as the index
     * of the first line after the table.
     */
    fun getTable(
        lines: List<String>,
        start: Int,
        lineContent: (String) -> String,
    ): Pair<Table, Int>? {
      if (start > lines.size - 2) {
        return null
      }
      val headerLine = lineContent(lines[start])
      val separatorLine = lineContent(lines[start + 1])
      val barCount = countSeparators(headerLine)
      if (!isHeaderDivider(barCount, separatorLine.trim())) {
        return null
      }
      val header = getRow(headerLine) ?: return null
      val rows = mutableListOf<Row>()
      rows.add(header)

      val dividerRow = getRow(separatorLine) ?: return null

      var i = start + 2
      while (i < lines.size) {
        val line = lineContent(lines[i])
        if (!line.contains("|")) {
          break
        }
        val row = getRow(line) ?: break
        rows.add(row)
        i++
      }

      val rowsAndDivider = rows + dividerRow
      if (
          rowsAndDivider.all { row ->
            val first = row.cells.firstOrNull()
            first != null && first.isBlank()
          }
      ) {
        rowsAndDivider.forEach { if (it.cells.isNotEmpty()) it.cells.removeAt(0) }
      }

      // val columns = rows.maxOf { it.cells.size }
      val columns = dividerRow.cells.size
      val maxColumns = rows.maxOf { it.cells.size }
      val widths = mutableListOf<Int>()
      for (column in 0 until maxColumns) {
        widths.add(3)
      }
      for (row in rows) {
        for (column in 0 until row.cells.size) {
          widths[column] = max(widths[column], row.cells[column].length)
        }
        for (column in row.cells.size until columns) {
          row.cells.add("")
        }
      }

      val align = mutableListOf<Align>()
      for (cell in dividerRow.cells) {
        val direction =
            if (cell.endsWith(":")) {
              if (cell.startsWith(":-")) {
                Align.CENTER
              } else {
                Align.RIGHT
              }
            } else {
              Align.LEFT
            }
        align.add(direction)
      }
      for (column in align.size until maxColumns) {
        align.add(Align.LEFT)
      }
      val table =
          Table(columns, widths, rows, align, lines.subList(start, i).map { lineContent(it) })
      return Pair(table, i)
    }

    /** Returns true if the given String looks like a markdown table header divider. */
    private fun isHeaderDivider(barCount: Int, s: String): Boolean {
      var i = 0
      var count = 0
      while (i < s.length) {
        val c = s[i++]
        if (c == '\\') {
          i++
        } else if (c == '|') {
          count++
        } else if (c.isWhitespace() || c == ':') {
          continue
        } else if (
            c == '-' &&
                (s.startsWith("--", i) ||
                    s.startsWith("-:", i) ||
                    (i > 1 && s.startsWith(":-:", i - 2)) ||
                    (i > 1 && s.startsWith(":--", i - 2)))
        ) {
          while (i < s.length && s[i] == '-') {
            i++
          }
        } else {
          return false
        }
      }

      return barCount == count
    }

    private fun getRow(s: String): Row? {
      // Can't just use String.split('|') because that would not handle escaped |'s
      if (s.indexOf('|') == -1) {
        return null
      }
      val row = Row()
      var i = 0
      var end = 0
      while (end < s.length) {
        val c = s[end]
        if (c == '\\') {
          end++
        } else if (c == '|') {
          val cell = s.substring(i, end).trim()
          if (end > 0) {
            row.cells.add(cell.trim())
          }
          i = end + 1
        }
        end++
      }
      if (end > i) {
        val cell = s.substring(i, end).trim()
        if (cell.isNotEmpty()) {
          row.cells.add(cell.trim())
        }
      }

      return row
    }

    private fun countSeparators(s: String): Int {
      var i = 0
      var count = 0
      while (i < s.length) {
        val c = s[i]
        if (c == '|') {
          count++
        } else if (c == '\\') {
          i++
        }
        i++
      }
      return count
    }
  }

  enum class Align {
    LEFT,
    RIGHT,
    CENTER,
  }

  class Row {
    val cells: MutableList<String> = mutableListOf()
  }
}
