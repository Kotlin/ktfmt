package org.jetbrains.ktfmt.testutil

import java.nio.file.Path
import org.jetbrains.ktfmt.format.FileType
import org.jetbrains.ktfmt.format.FormattingOptions
import org.jetbrains.ktfmt.format.TrailingCommaManagementStrategy

class Directive(val name: String, val configure: CaseConfig.Builder.(String) -> Unit)

class CaseConfig(
    val options: FormattingOptions,
    val checkIdempotency: Boolean,
    val fileType: FileType,
) {

  class Builder(base: FormattingOptions) {
    val options: FormattingOptions.Builder = base.toBuilder()
    var checkIdempotency: Boolean = true
    var fileType = FileType.REGULAR

    fun build(): CaseConfig = CaseConfig(options.build(), checkIdempotency, fileType)
  }

  companion object {
    private val DIRECTIVES: Map<String, Directive> = listOf(
        Directive("FILE_TYPE") {
          fileType =
              when (it) {
                "REGULAR" -> FileType.REGULAR
                "SCRIPT" -> FileType.SCRIPT
                else -> throw IllegalArgumentException("Unsupported file type: $it")
              }
        },
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
    private val SHEBANG_REGEX = Regex("""^#!.*$""")

    fun parse(code: String, origin: Path? = null): ParsedDirectives {
      var directivesEnded = false
      val parsedDirectives = buildMap {
        val lines =
            code.lineSequence().withIndex().let {
              // directives come after shebang
              if (it.firstOrNull()?.value?.matches(SHEBANG_REGEX) == true) {
                put(DIRECTIVES["FILE_TYPE"]!!, "SCRIPT")
                it.drop(1)
              } else it
            }
        val header = lines.takeWhile { it.value.startsWith("//") }

        for ((lineNumber, line) in header) {
          val matchResult = DIRECTIVE_REGEX.matchEntire(line)
          if (matchResult == null) {
            directivesEnded = true
            continue
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
          put(directive, value)
        }
      }
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
