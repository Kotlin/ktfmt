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
import org.gradle.api.Task
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.file.Directory
import org.gradle.api.file.RegularFile
import org.gradle.api.plugins.JavaApplication
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Copy
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
import org.gradle.kotlin.dsl.listProperty
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.register
import org.gradle.plugins.signing.SigningExtension

private val Project.nativeImageGc: Provider<String>
  get() = nativeImageProperty("ktfmt.native.gc").orElse("serial")

private val Project.enableNativeDebug: Provider<Boolean>
  get() = nativeImageProperty("ktfmt.native.debug").map { it.toBooleanStrict() }.orElse(false)

private val Project.enableLto: Provider<Boolean>
  get() = nativeImageProperty("ktfmt.native.lto").map { it.toBooleanStrict() }.orElse(false)

private val Project.enableMusl: Provider<Boolean>
  get() = nativeImageProperty("ktfmt.native.musl").map { it.toBooleanStrict() }.orElse(false)

private val Project.muslHome: Provider<String>
  get() = nativeImageProperty("ktfmt.native.musl.home")

private val Project.nativeImageExecutable: Provider<RegularFile>
  get() =
      layout.buildDirectory.file(
          "native/nativeCompile/" + if (currentOs == Os.WINDOWS) "ktfmt.exe" else "ktfmt",
      )

private val Project.nativeImageArchiveBaseName: String
  get() = "ktfmt-${currentOs.osName}-${currentArch.archName}-${ktfmtVersion.get()}"

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
          inputs.files(
              nativeImageDir.file("initialize-at-build-time.txt"),
              nativeImageDir.file("initialize-at-run-time.txt"),
          )
        }

    tasks.register<NativeImageSmokeTestTask>("nativeImageSmokeTest") {
      group = "verification"
      description = "Runs the Native Image binary against the project sources"
      dependsOn(nativeCompile)

      binary.set(nativeImageExecutable)
      sources.set(layout.projectDirectory.dir("src"))
      report.set(layout.buildDirectory.file("reports/native-image/smoke-test.txt"))
    }

    configureGraalvmNativeImage(nativeImageJar)

    configureNativeImageArtifactsTask(nativeCompile)
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
        buildArgs.addAll(buildNativeImageArgs())
      }
    }
  }

  private fun Project.buildNativeImageArgs(): Provider<List<String>> {
    val args = objects.listProperty<String>()

    args.addAll("-O3", "-march=compatibility")
    args.addAll(enableNativeDebug.toArgs("-g", "-H:+SourceLevelDebug"))

    args.add("--no-fallback")
    args.add(nativeImageGc.map { "--gc=$it" })
    args.addAll(
        "--future-defaults=all",
        "--link-at-build-time=org.jetbrains.ktfmt",
        "--add-opens=java.base/java.util=ALL-UNNAMED",
        "--color=always",
        "-H:+ReportExceptionStackTraces",
        "-H:-UseContainerSupport",
        "-R:+InstallSegfaultHandler",
        "-H:+UnlockExperimentalVMOptions",
        "-H:-ReduceImplicitExceptionStackTraceInformation",
        "-H:-UnlockExperimentalVMOptions",
        "-J--enable-native-access=ALL-UNNAMED",
        "-J--illegal-native-access=allow",
        "-J--sun-misc-unsafe-memory-access=allow",
    )

    args.addAll(enableLto.toArgs("--native-compiler-options=-flto", "-H:NativeLinkerOption=-flto"))
    args.addAll(muslLinkerArgs())

    args.addAll(
        linesFromFile("initialize-at-build-time.txt").map { lines ->
          lines.map { "--initialize-at-build-time=$it" }
        },
    )
    args.addAll(
        linesFromFile("initialize-at-run-time.txt").map { lines ->
          lines.map { "--initialize-at-run-time=$it" }
        },
    )

    args.addAll(staticLinkingArgs())
    return args
  }

  private fun Project.muslLinkerArgs(): Provider<List<String>> =
      enableMusl.zip(muslHome.orElse("")) { muslEnabled, home ->
        when {
          !muslEnabled -> emptyList()
          home.isEmpty() ->
              throw GradleException(
                  "`ktfmt.native.musl.home` required when `ktfmt.native.musl` is true",
              )
          else -> listOf("-H:NativeLinkerOption=-L$home/lib")
        }
      }

  private fun Project.staticLinkingArgs(): Provider<List<String>> {
    val os = currentOs
    val arch = currentArch

    return enableMusl.map { muslEnabled ->
      when (os) {
        Os.LINUX ->
            if (muslEnabled && arch == Arch.AARCH64) {
              listOf("--static", "--libc=musl", "-H:+StaticLibStdCpp")
            } else {
              listOf("--static-nolibc")
            }
        Os.MACOS -> listOf("--static-nolibc")
        Os.WINDOWS -> emptyList()
      }
    }
  }

  private fun Project.configureNativeImageArtifactsTask(nativeCompile: TaskProvider<Task>) {
    val archiveName = nativeImageArchiveBaseName
    val archiveFileName = "$archiveName.$nativeImageArchiveExtension"
    val archiveDirectory = layout.buildDirectory.dir("archive")

    val archive =
        if (currentOs == Os.WINDOWS) {
          tasks.register<Zip>("nativeImageArchive") {
            configureNativeImageArchive(
                nativeCompile,
                archiveName,
                archiveFileName,
                archiveDirectory,
            )
          }
        } else {
          tasks.register<Tar>("nativeImageArchive") {
            this.compression = Compression.GZIP
            configureNativeImageArchive(
                nativeCompile,
                archiveName,
                archiveFileName,
                archiveDirectory,
            )
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

  private fun AbstractArchiveTask.configureNativeImageArchive(
      nativeCompile: TaskProvider<Task>,
      archiveName: String,
      fileName: String,
      destination: Provider<Directory>,
  ) {
    description = "Packs the native image distribution into the publishable release archive"
    from(nativeCompile) {
      into(archiveName)
      filePermissions { unix("rwxr-xr-x") }
    }
    archiveFileName.set(fileName)
    destinationDirectory.set(destination)
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
  }

  private fun Project.linesFromFile(fileName: String): Provider<List<String>> {
    val file = layout.projectDirectory.dir(NATIVE_IMAGE_SRC_DIR).file(fileName)
    return providers.fileContents(file).asText.map { text ->
      text.lines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }
    }
  }

  private fun Provider<Boolean>.toArgs(vararg args: String): Provider<List<String>> {
    val enabledArgs = args.toList()
    return map { enabled -> if (enabled) enabledArgs else emptyList() }
  }

  private companion object {
    const val ENTRYPOINT = "org.jetbrains.ktfmt.cli.Main"
    const val NATIVE_IMAGE_SRC_DIR = "src/main/native-image"
  }
}
