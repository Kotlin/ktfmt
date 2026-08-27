# Changelog

All notable changes to the ktfmt project (starting on v0.51) should be documented in this file.

The format is based on [Keep a Changelog](http://keepachangelog.com/).


## [Unreleased]

### Added

- Support partial formatting with `--lines`/`--line` and matching
  `--offset`/`--length` pairs, mirroring
  [google-java-format's](https://github.com/google/google-java-format#from-the-command-line)
  selected-range formatting flags.
- Support selected-range formatting in the IntelliJ plugin.
- GraalVM native image support
- Support val/var keywords in destructuring entries (https://github.com/facebook/ktfmt/pull/637)
- Use `--stdin-name` as the EditorConfig lookup path when formatting source from stdin.

### Changed

- Trailing comma is now added when a single parameter is formatted onto its own line (https://github.com/facebook/ktfmt/issues/461) 
- Migrated from `com.facebook.ktfmt` package to `org.jetbrains.ktfmt`
- Use the file's extension to differentiate between scripts and regular files in the parser

### Fixed

* Fix non-idempotent formatting when a managed trailing comma pushes a line over MAX_WIDTH (e.g. a long qualified expression as the last argument of a call). The comma is now accounted for by re-running the layout, so the line is broken correctly on the first pass. (https://github.com/facebook/ktfmt/pull/636)
* Fix non-idempotent formatting when max width breaks a scoping-function lambda with a chained call (e.g. `runCatching { ... }.getOrNull()`): the chained call now breaks onto its own line together with the lambda on the first pass. (https://github.com/Kotlin/ktfmt/issues/640)


## [0.64]

### Changed

* Reduced overall number of allocations to improve formatting performance (~6-7%) (https://github.com/facebook/ktfmt/pull/620)
* Reuse results of `Parser.parse` (https://github.com/facebook/ktfmt/pull/622)
* Fix trailing lambda with chained call indentation (https://github.com/facebook/ktfmt/pull/626)

### Fixed

* Support name-based destructuring declarations. (https://github.com/facebook/ktfmt/issues/629)

## [0.63]

### Added
- Support `ij_kotlin_indent_size` in editorconfig. (https://github.com/facebook/ktfmt/pull/604)
- Support for lists within quoted blocks in KDoc comments (https://github.com/facebook/ktfmt/commit/68fa1585b759ad4b12ca4802bccd297f6a33b0f3)
- Fix `ONLY_ADD` trailing commas strategy causing lines over MAX_WIDTH length (https://github.com/facebook/ktfmt/issues/610)
- Remove forced breaking of `context` function types (https://github.com/facebook/ktfmt/pull/613)
- Preserve user-authored line breaks inside lambda bodies by default, rather than have ktfmt impose anything. This can be particularly useful for DSL syntax like Compose UI or Kotlin Gradle script. The behavior follows the `FormattingOptions.preserveLambdaBreaks` setting of the chosen style. (https://github.com/facebook/ktfmt/pull/614)
- Add a `FormattingOptions.Builder` API for tools to avoid breaking ABI changes with new options. (https://github.com/facebook/ktfmt/pull/614)

## [0.62]
### Added

- Support `ij_kotlin_continuation_indent_size` in editorconfig. (https://github.com/facebook/ktfmt/pull/600)
- Add `--quiet` flag to suppress per-file formatting status output. (https://github.com/facebook/ktfmt/issues/558)

### Fixed
- Compatibility with Kotlin compiler v2.3.20 (`CONTEXT_RECEIVER_LIST` renamed to `CONTEXT_PARAMETER_LIST`, K1 API opt-in for `KotlinCoreEnvironment.createForProduction`) (https://github.com/facebook/ktfmt/issues/605)
- Dot-qualified scoping functions (e.g., `scope.launch { }`) now format as block-like expressions, consistent with non-qualified calls ([GH#205](https://github.com/facebook/ktfmt/issues/205))
- Backtick-escaped full-path imports are no longer incorrectly removed as unused (https://github.com/facebook/ktfmt/issues/532)
- Single-line comments in if expressions are now correctly indented (https://github.com/facebook/ktfmt/pull/591)
- Idea Plugin not applying custom trailing commas management strategy (https://github.com/facebook/ktfmt/pull/593)
- Comments between a multiline string and `.trimMargin()`/`.trimIndent()` are no longer deleted during formatting (https://github.com/facebook/ktfmt/issues/597)
- Blank lines before lists and blockquotes in KDoc comments are now preserved (https://github.com/facebook/ktfmt/issues/561)
- `maxCommentWidth` now defaults to `maxLineWidth` instead of being capped at 72, so KDoc comments respect the configured line width (https://github.com/facebook/ktfmt/issues/594)
- Block comment formatting inside of lambda expressions (https://github.com/facebook/ktfmt/issues/602)
- Fenced code blocks inside KDoc list items are no longer erroneously reflowed into a single line (https://github.com/facebook/ktfmt/issues/572)
- Comments before `&&`/`||` operators in chained binary expressions no longer strand the operator on its own line (https://github.com/facebook/ktfmt/issues/527)
- Lambda bodies in `when` branches no longer get extra indentation with Meta style (https://github.com/facebook/ktfmt/issues/222)


## [0.61]

### Added
- Support for Kotlin 2.3.0 explicit backing field (https://github.com/facebook/ktfmt/pull/580)

### Fixed
- Editorconfig not found for relative paths (https://github.com/facebook/ktfmt/pull/582)


## [0.60]

### Added
- Support for `else if` guard conditions (https://github.com/facebook/ktfmt/pull/563)
- Explicit Kotlin import layout for the default and Google specific editorconfig files to match ktfmt's style. The same layout was already applied to the Kotlin Lang editorconfig (https://github.com/facebook/ktfmt/pull/571)
- ktfmt cli can pull formatting configs from editor config files (https://github.com/facebook/ktfmt/pull/570)
- Strip leading UTF-8 BOM before formatting so ktfmt no longer errors on files starting with a BOM (https://github.com/facebook/ktfmt/issues/565)


## [0.59]

### Fixed
- Special format handling of multiline strings handling of first line and do not format string template expressions
- Do not remove semicolon after an unnamed empty companion object, if it isn't the last element (https://github.com/facebook/ktfmt/issues/557)


## [0.58]

### Changed
- Updated ShadowJar to 9.0.2 (https://github.com/facebook/ktfmt/pull/555)

### Fixed
- Do not apply special format handling of multiline strings with template expressions in them (https://github.com/facebook/ktfmt/issues/556)
- Make sure that we handle nested expressions for special format handling of multiline strings


## [0.57]

### Added
- `TrailingCommaManagementStrategy.ONLY_ADD` strategy that does not remove existing trailing commas (https://github.com/facebook/ktfmt/issues/461, https://github.com/facebook/ktfmt/issues/512, https://github.com/facebook/ktfmt/issues/514)
- Formatting of where clauses (https://github.com/facebook/ktfmt/issues/541)
- Special format handling of multiline strings with `trimMargin()` and `trimIndent()` (https://github.com/facebook/ktfmt/issues/389)

### Changed
- `FormattingOptions.manageTrailingCommas` was replaced with `FormattingOptions.trailingCommaManagementStrategy`, which also added new `TrailingCommaManagementStrategy.ONLY_ADD` strategy (https://github.com/facebook/ktfmt/issues/461, https://github.com/facebook/ktfmt/issues/512, https://github.com/facebook/ktfmt/issues/514)
- All styles managing trailing commas by default now (https://github.com/facebook/ktfmt/issues/216, https://github.com/facebook/ktfmt/issues/442)

### Removed
- Removed mvn build scripts

### Fixed
- Corrected reference to jar in formatter website's command line instructions https://facebook.github.io/ktfmt/
- Trailing comma on when cases (https://github.com/facebook/ktfmt/issues/376)
- Update idea plugin name to avoid collision with google-java-format (https://github.com/facebook/ktfmt/issues/553)


## [0.56]

### Changed
- Update to Kotlin 2.2.0 (https://github.com/facebook/ktfmt/commit/451be91d53aafcaae01cf0f7f3e389cfb8eefac3)


## [0.55]

### Added
- Support guard conditions (https://github.com/facebook/ktfmt/issues/530, https://github.com/facebook/ktfmt/pull/537)
- `--version` option in CLI (https://github.com/facebook/ktfmt/issues/534)

### Changed
- Update `kotlin-compiler-embeddable` to `2.2.0-Beta2` for forward compatibility with context parameters. (https://github.com/facebook/ktfmt/pull/538)
- Moved ktfmt project to Gradle (away from Maven) (https://github.com/facebook/ktfmt/commit/d03a29e71ebf19873e8b9ac21d255c8f830ef00a)

### Fixed
- Support context parameters (https://github.com/facebook/ktfmt/issues/518, https://github.com/facebook/ktfmt/pull/536)
- Indentation options in `.editorconfig-default` (https://github.com/facebook/ktfmt/issues/543)


## [0.53]

### Fixed
- Comments respecting max line width (https://github.com/facebook/ktfmt/pull/511)
- Exception while parsing property accessor on Kotlin 2.0.20-Beta2+ (https://github.com/facebook/ktfmt/pull/513)

## Changed
- Updated Google Java Format to 1.23.0 (https://github.com/facebook/ktfmt/commit/ed949e89eea22843ac10d4fb91685453754abd25)


## [0.52]

### Fixed
- IntelliJ plugin crash (https://github.com/facebook/ktfmt/pull/501)
- Ordering of `@property` and `@param` in KDoc (https://github.com/facebook/ktfmt/pull/498)
- Annotation in return expressions (https://github.com/facebook/ktfmt/issues/497)

### Changed
- KotlinLang style also managing trailing commas (https://github.com/facebook/ktfmt/issues/216, https://github.com/facebook/ktfmt/issues/442)
- Converted IntelliJ plugin to Kotlin (https://github.com/facebook/ktfmt/pull/502)

### Added
- More stability tests (https://github.com/facebook/ktfmt/pull/488)
- Custom profile in plugin settings, mirroring Gradle/Maven plugins (https://github.com/facebook/ktfmt/pull/503)


## [0.51]

### Added
- Created CHANGELOG.md
- Added --help option to CLI (https://github.com/facebook/ktfmt/pull/477)

### Changed
- Preserves blank spaces between when clauses (https://github.com/facebook/ktfmt/issues/342)
- Named the default style as `Formatter.META_FORMAT` / `--meta-style` (https://github.com/facebook/ktfmt/commit/96a7b1e2539eef43044f676f60400d22265fd115)
- `FormattingOptions` constructor parameters order was changed (https://github.com/facebook/ktfmt/commit/520706e6d010d48619781d7113e5b1522f07a2ba)

### Fixed
- Compilation issues with online formatter (https://github.com/facebook/ktfmt/commit/8605080cb0aadb7eaba20f3b469d6ddafe32c941)
- Removing valid semicolons (https://github.com/facebook/ktfmt/issues/459)
- Incorrect detection of unused `assign` import (https://github.com/facebook/ktfmt/issues/411)

### Removed
- **Deleted `Formatter.DROPBOX_FORMAT` / `--dropbox-style` (BREAKING CHANGE)** (https://github.com/facebook/ktfmt/commit/4a393bb8c1156a4a0fd1ab736c02ca8dbd39a969)
- Deleted `FormattingOptions.Style` enum (https://github.com/facebook/ktfmt/commit/7edeff14c3738427e53427eb6e39675dc30d1d05)
