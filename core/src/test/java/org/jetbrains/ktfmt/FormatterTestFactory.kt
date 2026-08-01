package org.jetbrains.ktfmt.testutil

import com.facebook.ktfmt.format.Formatter
import com.facebook.ktfmt.format.FormattingOptions
import com.facebook.ktfmt.format.TrailingCommaManagementStrategy
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.nameWithoutExtension
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.io.resolve
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DynamicContainer
import org.junit.jupiter.api.DynamicNode
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory

/**
 * Base class for a group of file-based formatter cases.
 *
 * Usage:
 * 1) Create a folder with the test group in resources, e.g. "cases/enums/"
 * 2) Populate it with tests: `Foo.input` is an input for the formatter, `Foo.output` is an expected
 *    output. If an .output is not present, it is assumed that formatting `.input` is an idempotent
 *    op
 * 3) Create a test class in Tests.kt:
 * ```
 * class EnumsTest() : FormatterTestFactory()
 * ```
 *
 * For each case, two tests are generated -- one for the formatting and one for the idempotency.
 * Customization currently supported only on the [options] level per class, though later we can
 * support directives as well.
 *
 * You can specify [group] explicitly or it will be deduced from the test class name, and test cases
 * will be looked for in `resources/cases/$group`.
 *
 * Using in IDE:
 * 1) Enable IJ-based test execution: Settings -> Build, Execution, Deployment -> Build Tools ->
 *    Gradle -> Run Tests using Intellij. It will make test navigation work. See IDEA-361423
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

  private companion object {
    val DEFAULT_CASE_FORMAT: FormattingOptions =
        Formatter.META_FORMAT.copy(
            trailingCommaManagementStrategy = TrailingCommaManagementStrategy.NONE,
        )

    // Without this, neither 'overwrite' nor navigation in IJ will work
    val root = run {
      val location = javaClass.protectionDomain?.codeSource?.location!!
      val root = location.toURI().path.substringBefore("build/classes/kotlin/test")
      Path.of(root).resolve("src/test/resources/cases")
    }
  }

  @TestFactory
  fun cases(): List<DynamicNode> {
    // Loads e.g. cases/enums/, all cases from the folder at once
    val cases = load(group)
    check(cases.isNotEmpty()) {
      "No '.input' files in ${root.resolve(group)}"
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
    val directory = root.resolve(group)
    require(directory.isDirectory()) { "No such directory: $directory" }

    return directory
        .listDirectoryEntries()
        .filter { it.extension == "input" }
        .sortedBy { it.name }
        .map { input ->
          val name = input.nameWithoutExtension
          val output = input.resolveSibling("$name.output")
          TestDescription(
              group = group,
              name = name,
              inputPath = input,
              input = input.readText(Charsets.UTF_8),
              output = output.takeIf { it.isRegularFile() },
          )
        }
  }

  private fun tests(expectation: TestCase): List<DynamicTest> {
    val uri = expectation.description.inputPath.toUri()
    // format(expected) == actual
    val checks = mutableListOf(
        DynamicTest.dynamicTest("Formats as expected${expectation.label}", uri) {
          formatsAsExpected(expectation)
        },
    )
    // format(format(expected)) == format(expected)
    if (expectation.output != null) {
      checks +=
          DynamicTest.dynamicTest("Format is idempotent${expectation.label}", uri) {
            outputIsIdempotent(expectation)
          }
    }
    return checks
  }

  private fun formatsAsExpected(expectation: TestCase, overwrite: Boolean = false) {
    val actual = Formatter.format(expectation.options, expectation.description.input)

    if (actual == expectation.expected) {
      return
    }

    if (overwrite) {
      overwriteOutput(expectation, actual)
      return
    }

    assertEquals(expectation.expected, actual, failureMessage(expectation, actual))
  }

  private fun outputIsIdempotent(expectation: TestCase) {
    val reformatted = Formatter.format(expectation.options, expectation.expected)
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

  private fun failureMessage(expectation: TestCase, actual: String): String = buildString {
    append(
        expectation.description.displayName,
        expectation.label,
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

    fun expectations(groupOptions: FormattingOptions): List<TestCase> = listOf(
        TestCase(
            description = this,
            variant = null,
            output = output,
            expected = output?.readText(Charsets.UTF_8) ?: input, // No .output, idempotency
            options = groupOptions,
        ),
    )

    fun expectation(variant: String?): Path {
      val suffix = if (variant == null) "" else ".$variant"
      return inputPath.resolveSibling("$name$suffix.output")
    }
  }

  private data class TestCase(
      val description: TestDescription,
      val variant: String?,
      val output: Path?, // null if idempotent
      val expected: String, // expected formatted .kt
      val options: FormattingOptions,
  ) {
    val label: String
      get() = if (variant == null) "" else " [$variant]"
  }
}
