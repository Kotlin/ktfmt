package org.jetbrains.ktfmt.testutil

import java.nio.file.Path
import org.jetbrains.ktfmt.format.FormattingOptions
import org.jetbrains.ktfmt.format.TrailingCommaManagementStrategy

class Directive(val name: String, val configure: CaseConfig.Builder.(String) -> Unit)

class CaseConfig(val options: FormattingOptions, val checkIdempotency: Boolean) {

  class Builder(base: FormattingOptions) {
    val options: FormattingOptions.Builder = base.toBuilder()
    var checkIdempotency: Boolean = true

    fun build(): CaseConfig = CaseConfig(options.build(), checkIdempotency)
  }

  companion object {
    private val DIRECTIVES: Map<String, Directive> = listOf(
        Directive("MAX_WIDTH") { options.maxWidth(it.toInt()) },
        Directive("BLOCK_INDENT") { options.blockIndent(it.toInt()) },
        Directive("CONTINUATION_INDENT") { options.continuationIndent(it.toInt()) },
        Directive("TRAILING_COMMA_STRATEGY") {
          options.trailingCommaManagementStrategy(
              TrailingCommaManagementStrategy.valueOf(it),
          )
        },
        Directive("REMOVE_UNUSED_IMPORTS") {
          options.removeUnusedImports(it.toBooleanStrict())
        },
        Directive("PRESERVE_LAMBDA_BREAKS") {
          options.preserveLambdaBreaks(it.toBooleanStrict())
        },
        Directive("PRINT_OPTS_AFTER_FORMATTING") {
          options.debuggingPrintOpsAfterFormatting(it.toBooleanStrict())
        },
        Directive("CHECK_IDEMPOTENCY") { checkIdempotency = it.toBooleanStrict() },
    )
        .associateBy { it.name }

    private val DIRECTIVE_REGEX = Regex("""^// ([A-Z][A-Z0-9_]+)(?: +(.*))?$""")

    fun parse(code: String, origin: Path? = null): ParsedDirectives {
      val header = code.lines().takeWhile { it.startsWith("//") }
      var directivesEnded = false
      val parsedDirectives =
          header
              .mapIndexedNotNull { lineNumber, line ->
                val matchResult = DIRECTIVE_REGEX.matchEntire(line)
                if (matchResult == null) {
                  directivesEnded = true
                  return@mapIndexedNotNull null
                }
                val (name, value) = matchResult.destructured
                val directive =
                    DIRECTIVES[name]
                        ?: when {
                          directivesEnded ->
                              error(
                                  "$origin:${lineNumber + 1}: directive '$name' should be listed first in the file",
                              )
                          else -> error("$origin:${lineNumber + 1}: unknown directive '$name'")
                        }
                directive to value
              }
              .toMap()
      return ParsedDirectives(parsedDirectives)
    }
  }
}

class ParsedDirectives(private val entries: Map<Directive, String>) {
  fun configure(base: FormattingOptions): CaseConfig {
    val builder = CaseConfig.Builder(base)
    for ((directive, value) in entries) {
      directive.configure(builder, value)
    }
    return builder.build()
  }
}
