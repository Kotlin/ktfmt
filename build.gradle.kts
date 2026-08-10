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

import org.gradle.api.attributes.Bundling
import org.gradle.api.attributes.Category
import org.gradle.api.attributes.LibraryElements
import org.gradle.api.attributes.Usage
import org.gradle.api.attributes.java.TargetJvmEnvironment
import org.gradle.api.file.FileCollection
import org.gradle.process.CommandLineArgumentProvider

plugins {
  alias(libs.plugins.dependencyAnalysis)
  alias(libs.plugins.dokka) apply false
  alias(libs.plugins.dokka.javadoc) apply false
  alias(libs.plugins.intelliJPlatform) apply false
  alias(libs.plugins.kotlin) apply false
  alias(libs.plugins.nexusPublish)
  alias(libs.plugins.shadowJar) apply false
}

version = providers.gradleProperty("ktfmt.version").get()

tasks.wrapper { distributionType = Wrapper.DistributionType.ALL }

val ktfmtCliDependencies = configurations.dependencyScope("ktfmtCliDependencies")
val ktfmtCliClasspath =
    configurations.resolvable("ktfmtCliClasspath") {
      extendsFrom(ktfmtCliDependencies.get())
      attributes {
        attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_RUNTIME))
        attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.LIBRARY))
        attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, objects.named(LibraryElements.JAR))
        attribute(Bundling.BUNDLING_ATTRIBUTE, objects.named(Bundling.EXTERNAL))
        attribute(
            TargetJvmEnvironment.TARGET_JVM_ENVIRONMENT_ATTRIBUTE,
            objects.named(TargetJvmEnvironment.STANDARD_JVM),
        )
      }
    }

dependencies { add(ktfmtCliDependencies.name, project(":ktfmt")) }

val ktfmtFiles =
    fileTree(rootDir) {
      include("**/*.kt")
      include("**/*.kts")
      exclude("**/build/**")
      exclude("**/.gradle/**")
      exclude("**/.intellijPlatform/**")
    }

class KtfmtArgumentsProvider(
    @get:InputFiles @get:PathSensitive(PathSensitivity.RELATIVE) val files: FileCollection,
    @get:Input val check: Boolean,
) : CommandLineArgumentProvider {
  override fun asArguments(): Iterable<String> = buildList {
    add("--quiet")
    if (check) {
      add("--dry-run")
      add("--set-exit-if-changed")
    }
    addAll(files.files.sorted().map { it.path })
  }
}

fun JavaExec.configureKtfmtRun(files: FileCollection, check: Boolean) {
  group = if (check) "verification" else "formatting"
  classpath = ktfmtCliClasspath.get()
  mainClass.set("org.jetbrains.ktfmt.cli.Main")
  argumentProviders.add(KtfmtArgumentsProvider(files, check))
  onlyIf { files.files.isNotEmpty() }
}

val ktfmtCheck =
    tasks.register<JavaExec>("ktfmtCheck") {
      group = "verification"
      description = "Run Ktfmt formatter validation"
      configureKtfmtRun(ktfmtFiles, check = true)
    }

val ktfmtFormat =
    tasks.register<JavaExec>("ktfmtFormat") {
      group = "formatting"
      description = "Run Ktfmt formatter"
      configureKtfmtRun(ktfmtFiles, check = false)
    }

dependencyAnalysis {
  issues {
    all {
      // Compiled by a separate task against a handcrafted classpath
      ignoreSourceSet("nativeImageSourceSet")
      onUnusedDependencies {
        severity("fail")
      }
      onAny {
        severity("warn")
      }
    }
  }
}

subprojects {
  tasks.named { it == "check" }.configureEach { dependsOn(rootProject.tasks.named("ktfmtCheck")) }
}

nexusPublishing {
  repositories {
    sonatype {
      nexusUrl = uri("https://ossrh-staging-api.central.sonatype.com/service/local/")
      snapshotRepositoryUrl = uri("https://central.sonatype.com/repository/maven-snapshots/")

      username = System.getenv("OSSRH_USERNAME")
      password = System.getenv("OSSRH_PASSWORD")
    }
  }
}
