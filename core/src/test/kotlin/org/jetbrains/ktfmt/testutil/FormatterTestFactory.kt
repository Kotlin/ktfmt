package org.jetbrains.ktfmt.testutil

import java.net.URI
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.nameWithoutExtension
import kotlin.io.path.readText
import kotlin.io.path.walk
import kotlin.io.path.writeText
import org.jetbrains.ktfmt.format.FileType
import org.jetbrains.ktfmt.format.Formatter
import org.jetbrains.ktfmt.format.FormattingOptions
import org.jetbrains.ktfmt.format.KotlinCode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DynamicContainer
import org.junit.jupiter.api.DynamicNode
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory

/**
 * Base class for a group of file-based formatter cases.
 *
 * Usage:
 * 1) Create a folder with the test group in resources, e.g. "cases/format/". Nested folders are
 *    walked too, and the case name is the path relative to the group, e.g. "annotation/basic"
 * 2) Populate it with tests: `Foo.input` is an input for the formatter, `Foo.output` is an expected
 *    output. If an .output is not present, it is assumed that formatting `.input` is an idempotent
 *    op. A `Foo.new.output` file additionally runs the case with [NEW_FORMAT]
 * 3) Create a test class:
 * ```
 * class FormatTest() : FormatterTestFactory()
 * ```
 *
 * For each case, two tests are generated -- one for the formatting and one for the idempotency.
 *
 * The group-wide [options] can be overridden per case by a directive header: a block of comments at
 * the very top of the `.input`, e.g.
 *
 * ```
 * // MAX_WIDTH 50
 * // CHECK_IDEMPOTENCY false
 * ```
 *
 * See [CaseConfig.DIRECTIVES] for supported directives.
 *
 * You can specify [group] explicitly or it will be deduced from the test class name, and test cases
 * will be looked for in `resources/cases/$group`.
 *
 * Using in IDE:
 * 1) Enable IJ-based test execution: Settings -> Build, Execution, Deployment -> Build Tools ->
 *    Gradle -> Run Tests using IntelliJ. It will make test navigation work. See IDEA-361423
 *
 * 2) To run all tests, the gutter button is in FormatterTestFactory, not in Tests.kt
 *
 * 3) Reassign ".input"/".output" association to Kotlin: Settings -> Editor -> File Types
 */
abstract class FormatterTestFactory(
    group: String? = null,
    private val options: FormattingOptions = DEFAULT_CASE_FORMAT,
) {
  private val group: String = group ?: javaClass.simpleName.removeSuffix("Test").lowercase()

  companion object {
    val DEFAULT_CASE_FORMAT: FormattingOptions = Formatter.META_FORMAT

    val NEW_FORMAT =
        Formatter.KOTLINLANG_FORMAT.copy(
            experimentalEngine = true,
        )

    // Add other formats if needed for extensibility
    val FORMAT_VARIANTS = mapOf("new" to NEW_FORMAT)

    // Without this, neither 'overwrite' nor navigation in IJ will work
    val ROOT: Path = run {
      val location = javaClass.protectionDomain?.codeSource?.location!!
      val root = URI(location.toURI().toString().substringBefore("build/classes/kotlin/test"))
      Path.of(root).resolve("src/test/resources/cases")
    }
  }

  @TestFactory
  fun cases(): List<DynamicNode> {
    // Loads e.g. cases/format/, all cases from the folder at once
    val cases = load(group)
    check(cases.isNotEmpty()) {
      "No '.input' files in ${ROOT.resolve(group)}"
    }

    return cases.map { case ->
      DynamicContainer.dynamicContainer(
          case.name,
          case.inputPath.toUri(),
          case.expectations(options).flatMap(::tests).stream(),
      )
    }
  }

  private fun load(group: String): List<TestDescription> {
    val directory = ROOT.resolve(group)
    require(directory.isDirectory()) { "No such directory: $directory" }

    return directory
        .walk()
        .filter { it.extension == "input" }
        .sortedBy { it.toString() }
        .map { input ->
          val name = directory.relativize(input).joinToString("/").removeSuffix(".input")
          val output = input.resolveSibling("${input.nameWithoutExtension}.output")
          TestDescription(
              group = group,
              name = name,
              inputPath = input,
              input = input.readText(Charsets.UTF_8),
              output = output.takeIf { it.isRegularFile() },
          )
        }
        .toList()
  }

  private fun tests(expectation: TestCase): List<DynamicTest> {
    val uri = (expectation.output ?: expectation.description.inputPath).toUri()
    // format(expected) == actual
    val checks = mutableListOf(
        DynamicTest.dynamicTest(
            "${expectation.label} Formats as expected",
            uri,
        ) {
          formatsAsExpected(expectation)
        },
    )
    // format(format(expected)) == format(expected)
    if (expectation.output != null && expectation.config.checkIdempotency) {
      checks +=
          DynamicTest.dynamicTest("${expectation.label} Format is idempotent", uri) {
            outputIsIdempotent(expectation)
          }
    }
    return checks
  }

  private fun formatsAsExpected(expectation: TestCase, overwrite: Boolean = false) {
    val actual =
        Formatter.format(
            expectation.config.options,
            KotlinCode(expectation.description.input, FileType.SCRIPT),
        )

    if (actual == expectation.expected) {
      return
    }

    if (overwrite) {
      overwriteOutput(expectation, actual)
      return
    }

    assertEquals(expectation.expected, actual, failureMessage(expectation))
  }

  private fun outputIsIdempotent(expectation: TestCase) {
    val reformatted =
        Formatter.format(
            expectation.config.options,
            KotlinCode(expectation.expected, FileType.SCRIPT),
        )
    assertEquals(
        expectation.expected,
        reformatted,
        "${expectation.description.displayName}${expectation.label}: non-idempotent formatting",
    )
  }

  private fun overwriteOutput(expectation: TestCase, actual: String) {
    val target = expectation.output ?: expectation.description.expectation(expectation.variant)
    target.writeText(actual, Charsets.UTF_8)
    throw AssertionError(
        "Rewrote ${expectation.description}, review and re-run the test",
    )
  }

  private fun failureMessage(expectation: TestCase): String = buildString {
    append(
        expectation.label,
        expectation.description.displayName,
        " is not formatted as expected.\n",
    )
    append(
        "\nHint: re-run with 'overwrite' property set to 'true' to write the actual output to the expectation file",
    )
  }

  /**
   * A single case from test data -- `.input` file plus the expectations recorded next to it. An
   * example:
   * ```
   * TestDescription(
   *     group=enums,
   *     name=CommaWithSemicolon,
   *     inputFile=/Users/qwwdfsad/workspace/ktfmt/core/src/test/resources/cases/enums/CommaWithSemicolon.input,
   *     input="An actual string from the file"
   *     output=/Users/qwwdfsad/workspace/ktfmt/core/src/test/resources/cases/enums/CommaWithSemicolon.output)
   * ```
   *
   * Can produce multiple actual tests
   */
  private data class TestDescription(
      val group: String,
      val name: String,
      val inputPath: Path,
      val input: String,
      val output: Path?, // null if there is no .output next to the input
  ) {

    val displayName: String
      get() = "$group/$name"

    fun expectations(groupOptions: FormattingOptions): List<TestCase> = buildList {
      val directives = CaseConfig.parse(input, inputPath)
      add(
          testCase(
              variant = null,
              output = output,
              config = directives.configure(groupOptions),
          ),
      )

      FORMAT_VARIANTS.forEach { (variant, variantOptions) ->
        val variantOutput = expectation(variant).takeIf { it.isRegularFile() }
        if (variantOutput != null) {
          add(
              testCase(
                  variant = variant,
                  output = variantOutput,
                  config = directives.configure(variantOptions),
              ),
          )
        }
      }
    }

    private fun testCase(
        variant: String?,
        output: Path?,
        config: CaseConfig,
    ): TestCase = TestCase(
        description = this,
        variant = variant,
        output = output,
        expected = output?.readText(Charsets.UTF_8) ?: input, // No .output, idempotency
        config = config,
    )

    fun expectation(variant: String?): Path {
      val suffix = if (variant == null) "" else ".$variant"
      return inputPath.resolveSibling("${inputPath.nameWithoutExtension}$suffix.output")
    }
  }

  private data class TestCase(
      val description: TestDescription,
      val variant: String?,
      val output: Path?, // null if idempotent
      val expected: String, // expected formatted.kt
      val config: CaseConfig,
  ) {
    val label: String
      get() = if (variant == null) "" else " [$variant]"
  }
}
