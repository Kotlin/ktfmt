# ktfmt [![GitHub release](https://img.shields.io/github/release/kotlin/ktfmt?sort=semver)](https://github.com/Kotlin/ktfmt/releases/)   [![Maven Central Version](https://img.shields.io/maven-central/v/com.facebook/ktfmt)](https://central.sonatype.com/artifact/com.facebook/ktfmt) [![slack](https://img.shields.io/badge/Slack-ktfmt-purple.svg?logo=slack)](https://slack-chats.kotlinlang.org/c/ktfmt)   [![invite](https://img.shields.io/badge/Request%20a%20Slack%20invite-8A2BE2)](https://surveys.jetbrains.com/s3/kotlin-slack-sign-up)   [![issues - ktfmt](https://img.shields.io/github/issues/facebook/ktfmt)](https://github.com/Kotlin/ktfmt/issues)

`ktfmt` is a program that pretty-prints (formats) Kotlin code, based on
[google-java-format](https://github.com/google/google-java-format).

The minimum supported runtime version is JDK 11, released September 2018.

## Demo

|Before Formatting| Formatted by `ktfmt`|
| ---- | ---- |
| ![Original](docs/images/before.png) | ![ktfmt](docs/images/ktfmt.png) |

For comparison, the same code formatted by [`ktlint`](https://github.com/pinterest/ktlint) and
IntelliJ:

| Formatted by `ktlint`|Formatted by IntelliJ|
| ------ | --------|
| ![ktlint](docs/images/ktlint.png) | ![IntelliJ](docs/images/intellij.png) |


## Using the formatter

### IntelliJ, Android Studio, and other JetBrains IDEs

A [ktfmt IntelliJ plugin](https://plugins.jetbrains.com/plugin/14912-ktfmt) is available from the
plugin repository.
To install it:
1. Go to your IDE's settings
2. Select the `Plugins` category
3. Go to the `Marketplace` tab
4. Search for the `ktfmt` plugin
5. Install it

The plugin will be disabled by default. To enable it in the current project, go to
`File → Settings... → ktfmt Settings` (or `IntelliJ IDEA → Preferences... → Editor → ktfmt Settings`
on macOS) and check the `Enable ktfmt` checkbox.
A notification will be presented when you first open a project offering to do this for you.

To enable it by default in new projects, use
`File → New Project Settings → Preferences for new Projects → Editor → ktfmt Settings`.

When enabled, it will replace the normal `Reformat Code` action, which can be triggered from the
`Code` menu or with the Ctrl-Alt-L (by default) keyboard shortcut.

To configure IntelliJ to approximate ktfmt's formatting rules during code editing, you can edit your
project's
[`.editorconfig` file](https://www.jetbrains.com/help/idea/configuring-code-style.html#editorconfig)
to include the Kotlin section from one of the files inside [`docs/editorconfig`](docs/editorconfig).

#### Share IntelliJ ktfmt settings
In order to share the settings, make sure to commit the file `.idea/ktfmt.xml` into your codebase.

### Installation

#### Homebrew

If you're a [Homebrew](https://brew.sh) user, you can install
[ktfmt](https://formulae.brew.sh/formula/ktfmt) via:

```
$ brew install ktfmt
```

### from the command-line

[Download the formatter](https://github.com/Kotlin/ktfmt/releases) and run it with:

```
$ java -jar /path/to/ktfmt-<VERSION>-with-dependencies.jar [--kotlinlang-style | --google-style] [files...]
```

The formatter can act on whole files, on limited lines (`--lines` or `--line`), or on specific
offsets (`--offset` and `--length`). Line ranges look like `5` or `1:12,14`, may be used multiple
times, and are 1-based. Offset ranges must provide matching `--offset` and `--length` pairs;
`--length=0` formats the whole line containing the given `--offset`.

Partial formatting limits the core pretty-print replacements to the selected ranges. Whole-file
cleanup passes, such as import cleanup and multiline string formatting, still run afterward to match
[google-java-format's](https://github.com/google/google-java-format#from-the-command-line)
cleanup-after-selection behavior.

`--kotlinlang-style` makes `ktfmt` use a block indent of 4 spaces instead of 2.
See below for details.

`--enable-editorconfig` makes `ktfmt` enable limited support to override the style's configuration based on the
following subset of editorconfig properties:

| EditorConfig Property                                     | Description                                                                                      |
|:----------------------------------------------------------|--------------------------------------------------------------------------------------------------|
| `max_line_length`                                         | will override the max line width                                                                 |
| `indent_size`<br/>*or `tab_width` if `indent_size = tab`* | will override the block indent                                                                   |
| `ij_continuation_indent_size`                             | will override the continuation indent                                                            |
| `ktfmt_trailing_comma_management_strategy`                | one of `none`, `only_add` or `complete`<br/>will override the trailing comma management strategy |

#### Preserving Lambda Line Breaks

By default, `ktfmt` respects user-authored line breaks inside lambda bodies. This is useful for DSLs
where nesting carries semantic meaning (Jetpack Compose UI hierarchies, Kotlin Gradle scripts, etc.)
without ktfmt prescribing a specific outcome.

A lambda body whose source spans multiple lines stays multi-line; one that fits on a single line
stays single-line if written that way. For example, the following is left as-is:

```kotlin
// Multi-line in source is preserved
App {
  SelectableCard {
    Button { Text("Click me") }
  }
}

// Single-line in source is also preserved
dependencies { implementation(libs.androidx.activity) }
val state = remember { mutableStateOf(false) }
```

***Note:***
*There is no configurability as to the formatter's algorithm for formatting (apart from the different styles
or limited `.editorconfig` support). This is a deliberate design decision to unify our code formatting on a
single format.*

### using Gradle

A [Gradle plugin (ktfmt-gradle)](https://github.com/cortinico/ktfmt-gradle) is available on the
Gradle Plugin Portal. To set it up, just follow the instructions in the
[How-to-use section](https://github.com/cortinico/ktfmt-gradle#how-to-use-).

Alternatively, you can use [Spotless](https://github.com/diffplug/spotless) with the
[ktfmt Gradle plugin](https://github.com/diffplug/spotless/tree/main/plugin-gradle#ktfmt).

### using Maven

Consider using [Spotless](https://github.com/diffplug/spotless) with the
[ktfmt Maven plugin](https://github.com/diffplug/spotless/tree/main/plugin-maven#ktfmt).

### using pre-commit hooks

A [pre-commit hook](https://pre-commit.com/hooks.html) is implemented in
[language-formatters-pre-commit-hooks](https://github.com/macisamuele/language-formatters-pre-commit-hooks)

## FAQ

### `ktfmt` vs `ktlint` vs IntelliJ

`ktfmt` uses google-java-format's underlying engine, and as such, many items on
[google-java-format's FAQ](https://github.com/google/google-java-format/wiki/FAQ) apply to `ktfmt`
as well.

In particular, here are the principles that we try to adhere to:
1. `ktfmt` ignores most existing formatting. It respects existing newlines in some places, but in
  general, its output is deterministic and is independent of the input code.
2. `ktfmt` exposes no configuration options that govern formatting behavior. See
  https://github.com/google/google-java-format/wiki/FAQ#i-just-need-to-configure-it-a-bit-differently-how
  for the rationale.
   1. For exposed configurations, like `style`, we aim to make sure that those are easily shared
      across your organization/codebase to avoid
      [bikeshedding discussions](https://thedecisionlab.com/biases/bikeshedding) about code format.

These two properties make `ktfmt` a good fit in large Kotlin code bases, where consistency is very
important.

We created `ktfmt` because at the time `ktlint` and IntelliJ sometimes failed to produce
nice-looking code that fits in 100 columns, as can be seen in the [Demo](README.md#Demo) section.

### `ktfmt` uses a 2-space indent; why not 4? any way to change that?

Two reasons -
1. Many of our projects use a mixture of Kotlin and Java, and we found the back-and-forth in styles
   to be distracting.
2. From a pragmatic standpoint, the formatting engine behind google-java-format uses more whitespace
   and newlines than other formatters. Using an indentation of 4 spaces quickly reaches the maximal
   column width.

However, we do offer an alternative style for projects that absolutely cannot make the move to
`ktfmt` because of 2-space: the style `--kotlinlang-style` changes block indents to 4-space.

Alternatively, the `ktfmt` command-line supports a limited subset of `.editorconfig` properties; see above.

## Developer's Guide

### Setup

Open the `build.gradle.kts` at the root of the repo in IntelliJ.
Choose "Open as a Project".

### Development

* Currently, we mainly develop by adding tests to `FormatterTest.kt`.

### Building on the Command Line

* Run `./gradlew :ktfmt:shadowJar`
* Run `java -jar core/build/libs/ktfmt-<VERSION>-with-dependencies.jar`

### Releasing

See [RELEASING.md](RELEASING.md).

## License

Apache License 2.0
