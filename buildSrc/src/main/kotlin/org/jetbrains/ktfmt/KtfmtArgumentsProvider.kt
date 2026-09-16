package org.jetbrains.ktfmt

import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.process.CommandLineArgumentProvider

class KtfmtArgumentsProvider(
    @get:InputFiles @get:PathSensitive(PathSensitivity.RELATIVE) val files: FileCollection,
    @get:Input val check: Boolean,
) : CommandLineArgumentProvider {
  override fun asArguments(): Iterable<String> = buildList {
    if (check) {
      add("--dry-run")
      add("--set-exit-if-changed")
    }
    addAll(files.files.sorted().map { it.path })
  }
}
