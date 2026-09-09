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

package org.jetbrains.ktfmt

import org.graalvm.buildtools.gradle.dsl.GraalVMExtension
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.file.RegularFile
import org.gradle.api.plugins.JavaApplication
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.Exec
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.bundling.AbstractArchiveTask
import org.gradle.api.tasks.bundling.Compression
import org.gradle.api.tasks.bundling.Tar
import org.gradle.api.tasks.bundling.Zip
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.crypto.checksum.Checksum
import org.gradle.jvm.tasks.Jar
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.register
import org.gradle.plugins.signing.SigningExtension

private val Project.nativeImageGc: String
  get() = configurationProperty("ktfmt.native.gc").getOrElse("serial")

private val Project.enableNativeDebug: Boolean
  get() = configurationProperty("ktfmt.native.debug").map { it.toBooleanStrict() }.getOrElse(false)

private val Project.enableLto: Boolean
  get() = configurationProperty("ktfmt.native.lto").map { it.toBooleanStrict() }.getOrElse(false)

private val Project.enableMusl: Boolean
  get() = configurationProperty("ktfmt.native.musl").map { it.toBooleanStrict() }.getOrElse(false)

private val Project.muslHome: String?
  get() = configurationProperty("ktfmt.native.musl.home").orNull

private val Project.nativeImageExecutable: Provider<RegularFile>
  get() =
      layout.buildDirectory.file(
          "native/nativeCompile/" + if (currentOs == Os.WINDOWS) "ktfmt.exe" else "ktfmt",
      )

private val Project.nativeImageArchiveBaseName: String
  get() = "ktfmt-${currentOs.osName}-${currentArch.archName}-${rootProject.version}"

private val Project.nativeImageArchiveExtension: String
  get() = if (currentOs == Os.WINDOWS) "zip" else "tar.gz"

@Suppress("unused")
class NativeImagePlugin : Plugin<Project> {
  override fun apply(project: Project) {
    project.plugins.apply("application")
    project.plugins.apply("org.graalvm.buildtools.native")
    project.plugins.apply("signing")
    project.plugins.apply("org.gradle.crypto.checksum")

    project.extensions.configure<JavaApplication> { mainClass.set(ENTRYPOINT) }

    project.configureNativeImage()
  }

  private fun Project.configureNativeImage() {
    val nativeImageLibs = extensions.getByType<VersionCatalogsExtension>().named("nativeImageLibs")

    val nativeImageJavacClasspath =
        configurations.create("nativeImageJavacClasspath") {
          extendsFrom(configurations.getByName("implementation"))
          isCanBeResolved = true
        }

    dependencies.apply {
      add("nativeImageJavacClasspath", nativeImageLibs.findLibrary("graalvm-nativeimage").get())
      add("nativeImageClasspath", nativeImageLibs.findLibrary("jline-terminal").get())
      add("nativeImageClasspath", nativeImageLibs.findLibrary("jline-terminal-jansi").get())
      add("nativeImageClasspath", nativeImageLibs.findLibrary("jline-terminal-jna").get())
      add("nativeImageClasspath", nativeImageLibs.findLibrary("jline-terminal-jni").get())
    }

    val nativeImageDir = layout.projectDirectory.dir(NATIVE_IMAGE_SRC_DIR)
    val javaExtension = extensions.getByType<JavaPluginExtension>()
    val nativeImageSourceSet =
        javaExtension.sourceSets.create("nativeImageSourceSet") {
          java.srcDir(nativeImageDir.dir("java"))
          resources.srcDir(nativeImageDir.dir("resources"))
          compileClasspath += nativeImageJavacClasspath
        }

    val compileNativeImageClasses =
        tasks.register<JavaCompile>("compileNativeImageClasses") {
          group = "build"
          description = "Compiles Native Image helper classes"
          source = nativeImageSourceSet.java
          classpath = nativeImageJavacClasspath
          destinationDirectory.set(layout.buildDirectory.dir("classes/native-image"))
          dependsOn(tasks.named("compileJava"))
        }

    val nativeImageJar =
        tasks.register<Jar>("nativeImageJar") {
          group = "build"
          description = "Assembles Native Image jar and resources"
          from(compileNativeImageClasses.flatMap { it.destinationDirectory })
          from(nativeImageSourceSet.resources)
          archiveClassifier.set("nativeimage")
        }

    val nativeCompile =
        tasks.named("nativeCompile") {
          dependsOn(nativeImageJar)
        }

    tasks.register<Exec>("nativeImageSmokeTest") {
      group = "verification"
      description = "Runs the Native Image binary against the project sources"
      dependsOn(nativeCompile)

      val executableFile = nativeImageExecutable.get().asFile
      executable = executableFile.absolutePath
      args(
          layout.projectDirectory.dir("src").asFile.absolutePath,
          "--dry-run",
          "--set-exit-if-changed",
      )

      doFirst {
        if (!executableFile.exists()) {
          throw GradleException("No executable exists at $executableFile")
        }
        if (!executableFile.canExecute()) {
          throw GradleException("$executableFile is not executable")
        }
      }
    }

    configureGraalvmNativeImage(nativeImageJar)

    configureNativeImageArtifactsTask()
  }

  private fun Project.configureGraalvmNativeImage(nativeImageJar: TaskProvider<Jar>) {
    extensions.configure<GraalVMExtension>("graalvmNative") {
      binaries.named("main") {
        imageName.set("ktfmt")
        mainClass.set(ENTRYPOINT)
        classpath(
            files(
                nativeImageJar.flatMap { it.archiveFile },
                tasks.named("jar", Jar::class).flatMap { it.archiveFile },
                configurations.getByName("compileClasspath"),
                configurations.getByName("runtimeClasspath"),
                configurations.getByName("nativeImageClasspath"),
            ),
        )
        buildArgs(buildNativeImageArgs())
      }
    }
  }

  private fun Project.buildNativeImageArgs(): List<String> = buildList {
    val muslEnabled = enableMusl

    add("-O3")
    add("-march=compatibility")
    if (enableNativeDebug) {
      add("-g")
      add("-H:+SourceLevelDebug")
    }

    add("--no-fallback")
    add("--gc=$nativeImageGc")
    add("--future-defaults=all")
    add("--link-at-build-time=org.jetbrains.ktfmt")
    add("--add-opens=java.base/java.util=ALL-UNNAMED")
    add("--color=always")
    add("-H:+ReportExceptionStackTraces")
    add("-H:-UseContainerSupport")
    add("-R:+InstallSegfaultHandler")
    add("-H:+UnlockExperimentalVMOptions")
    add("-H:-ReduceImplicitExceptionStackTraceInformation")
    add("-H:-UnlockExperimentalVMOptions")
    add("-J--enable-native-access=ALL-UNNAMED")
    add("-J--illegal-native-access=allow")
    add("-J--sun-misc-unsafe-memory-access=allow")

    if (enableLto) {
      add("--native-compiler-options=-flto")
      add("-H:NativeLinkerOption=-flto")
    }
    if (muslEnabled) {
      val muslHome =
          muslHome
              ?: throw GradleException(
                  "`ktfmt.native.musl.home` required when `ktfmt.native.musl` is true",
              )
      add("-H:NativeLinkerOption=-L$muslHome/lib")
    }

    addAll(linesFromFile("initialize-at-build-time.txt").map { "--initialize-at-build-time=$it" })
    addAll(linesFromFile("initialize-at-run-time.txt").map { "--initialize-at-run-time=$it" })

    when (currentOs) {
      Os.LINUX ->
          if (muslEnabled && currentArch == Arch.AARCH64) {
            addAll(listOf("--static", "--libc=musl", "-H:+StaticLibStdCpp"))
          } else {
            add("--static-nolibc")
          }
      Os.MACOS -> add("--static-nolibc")
      Os.WINDOWS -> Unit
    }
  }

  private fun Project.configureNativeImageArtifactsTask() {
    val archive =
        if (currentOs == Os.WINDOWS) {
          this.tasks.register<Zip>("nativeImageArchive") { configureNativeImageArchive() }
        } else {
          this.tasks.register<Tar>("nativeImageArchive") {
            this.compression = Compression.GZIP
            configureNativeImageArchive()
          }
        }

    val checksum =
        tasks.register<Checksum>("nativeImageChecksum") {
          description = "Generates the SHA-256 checksum of the native image release archive"
          inputFiles.setFrom(archive.flatMap { it.archiveFile })
          outputDirectory.set(layout.buildDirectory.dir("checksums/nativeImage"))
          checksumAlgorithm.set(Checksum.Algorithm.SHA256)
        }
    val checksumFile = checksum.flatMap { task ->
      task.outputDirectory.file(archive.get().archiveFileName.get() + ".sha256")
    }
    val artifacts =
        tasks.register<Copy>("nativeImageArtifacts") {
          group = "build"
          description = "Builds and signs the native image release archive and its SHA-256 checksum"
          from(archive, checksumFile)
          into(layout.buildDirectory.dir("artifacts"))
        }

    val key = signingKey.orNull
    val password = signingPassword.orNull
    if (key.isNullOrBlank() || password.isNullOrBlank()) return

    val signing = extensions.getByType<SigningExtension>()
    signing.useInMemoryPgpKeys(signingKeyId.orNull, key, password)
    val archiveSignatures = signing.sign(archive.get())
    artifacts.configure {
      dependsOn(archiveSignatures)
      from(archiveSignatures.map { it.signatureFiles })
    }
  }

  private fun AbstractArchiveTask.configureNativeImageArchive() {
    val archiveName = project.nativeImageArchiveBaseName
    val archiveExtension = project.nativeImageArchiveExtension

    description = "Packs the native image distribution into the publishable release archive"
    dependsOn(project.tasks.named("nativeCompile"))
    from(project.nativeImageExecutable) {
      into(archiveName)
      filePermissions { unix("rwxr-xr-x") }
    }
    archiveFileName.set("$archiveName.$archiveExtension")
    destinationDirectory.set(project.layout.buildDirectory.dir("archive"))
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
  }

  private fun Project.linesFromFile(fileName: String): List<String> {
    val file = layout.projectDirectory.dir(NATIVE_IMAGE_SRC_DIR).file(fileName).asFile
    if (!file.exists()) throw GradleException("Native Image configuration file not found: $file")

    return file.readLines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }
  }

  private companion object {
    const val ENTRYPOINT = "org.jetbrains.ktfmt.cli.Main"
    const val NATIVE_IMAGE_SRC_DIR = "src/main/native-image"
  }
}
