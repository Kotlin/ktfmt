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

import org.jetbrains.dokka.gradle.tasks.DokkaGeneratePublicationTask
import org.jetbrains.kotlin.gradle.dsl.JvmDefaultMode
import org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation
import org.jetbrains.ktfmt.GenerateKtfmtFileTask

plugins {
  kotlin("jvm")
  alias(libs.plugins.dependencyAnalysis)
  alias(libs.plugins.dokka)
  alias(libs.plugins.dokka.javadoc)
  alias(libs.plugins.shadowJar)
  id("maven-publish")
  id("signing")
  id("ktfmt.ktfmt-file-generator")
  id("ktfmt.native-image")
}

dependencies {
  api(libs.googleJavaformat)
  api(libs.guava)
  api(libs.kotlin.stdlib)
  api(libs.kotlin.compilerEmbeddable)
  implementation(libs.ec4j)
  testImplementation(platform(libs.junit.bom))
  testImplementation(libs.junit.jupiter.api)
  testRuntimeOnly(libs.junit.jupiter.engine)
  testRuntimeOnly(libs.junit.platform.launcher)
}

val generateSources =
    tasks.register("generateSources") {
      description = "Generate sources"
      outputs.dir(layout.buildDirectory.dir("generated/main/kotlin"))
      dependsOn(tasks.withType<GenerateKtfmtFileTask>())
    }

// Match a correct source set by the current Kotlin version (e.g., 2.3.0-beta1 -> 2.3)
val compatibilitySources = run {
  val kotlinVersion = rootProject.libs.versions.kotlin.get().substringBeforeLast(".")
  val sourceRoot = layout.projectDirectory.dir("src/main/kotlin-$kotlinVersion")
  require(sourceRoot.asFile.isDirectory) {
    "No compatibility sources for Kotlin $kotlinVersion: expected $sourceRoot."
  }
  sourceRoot
}

tasks {
  test {
    useJUnitPlatform()
    jvmArgs("-Dfile.encoding=UTF-16")
  }

  withType(Jar::class) { manifest { attributes["Main-Class"] = "org.jetbrains.ktfmt.cli.Main" } }

  register<Jar>("sourcesJar") {
    description = "Sources jar including generated sources and compatibility utils"
    archiveClassifier = "sources"
    from(sourceSets["main"].allSource)
  }

  register<Jar>("javadocJar") {
    description = "Dokka-generated Javadoc jar"
    val dokkaJavadocTask = named<DokkaGeneratePublicationTask>("dokkaGeneratePublicationJavadoc")
    dependsOn(dokkaJavadocTask)
    from(dokkaJavadocTask.flatMap { it.outputDirectory })
    archiveClassifier = "javadoc"
  }

  shadowJar {
    archiveClassifier = "with-dependencies"
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    failOnDuplicateEntries = true
  }
}

kotlin {
  @OptIn(ExperimentalAbiValidation::class) abiValidation { enabled = true }

  compilerOptions { jvmDefault = JvmDefaultMode.NO_COMPATIBILITY }

  val javaVersion: String = rootProject.libs.versions.java.get()
  jvmToolchain(javaVersion.toInt())

  sourceSets {
    main {
      kotlin {
        srcDir(generateSources)
        srcDir(compatibilitySources)
      }
    }
  }
}

group = "org.jetbrains"

version = rootProject.version

publishing {
  publications {
    create<MavenPublication>("maven") {
      groupId = "org.jetbrains"
      artifactId = "ktfmt"
      version = rootProject.version.toString()

      from(components["java"])
      artifact(tasks.named("sourcesJar"))
      artifact(tasks.named("javadocJar"))

      pom {
        name = "Ktfmt"
        description =
            "A program that reformats Kotlin source code to comply with the common community standard for Kotlin code conventions."
        url = "https://github.com/Kotlin/ktfmt"
        inceptionYear = "2019"
        developers { developer { name = "Kotlin" } }
        licenses {
          license {
            name = "The Apache License, Version 2.0"
            url = "https://www.apache.org/licenses/LICENSE-2.0.txt"
          }
        }
        scm {
          connection = "scm:git:https://github.com/Kotlin/ktfmt.git"
          developerConnection = "scm:git:git@github.com:Kotlin/ktfmt.git"
          url = "https://github.com/Kotlin/ktfmt.git"
        }
      }
    }
  }
}

if (System.getenv("SIGN_BUILD") != null) {
  signing {
    useGpgCmd()
    sign(publishing.publications["maven"])
  }
}
