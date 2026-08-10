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

import org.apache.tools.ant.taskdefs.condition.Os
import org.graalvm.buildtools.gradle.dsl.GraalVMExtension
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.plugins.JavaApplication
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.Exec
import org.gradle.jvm.tasks.Jar
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.register

@Suppress("unused")
class NativeImagePlugin : Plugin<Project> {
  override fun apply(project: Project) {
    project.plugins.apply("application")
    project.plugins.apply("org.graalvm.buildtools.native")

    project.extensions.configure<JavaApplication> { mainClass.set(ENTRYPOINT) }

    project.plugins.withId("java") { configureNativeImage(project) }
  }

  private fun configureNativeImage(project: Project) {
    val nativeImageLibs =
        project.extensions.getByType<VersionCatalogsExtension>().named("nativeImageLibs")

    val nativeImageJavacClasspath: Configuration =
        project.configurations.create("nativeImageJavacClasspath") {
          extendsFrom(project.configurations.getByName("implementation"))
          isCanBeResolved = true
        }

    project.dependencies.apply {
      add("nativeImageJavacClasspath", nativeImageLibs.findLibrary("graalvm-nativeimage").get())
      add("nativeImageClasspath", nativeImageLibs.findLibrary("jline-terminal").get())
      add("nativeImageClasspath", nativeImageLibs.findLibrary("jline-terminal-jansi").get())
      add("nativeImageClasspath", nativeImageLibs.findLibrary("jline-terminal-jna").get())
      add("nativeImageClasspath", nativeImageLibs.findLibrary("jline-terminal-jni").get())
    }

    val nativeImageDir = project.layout.projectDirectory.dir(NATIVE_IMAGE_SRC_DIR)
    val javaExtension = project.extensions.getByType<JavaPluginExtension>()
    val nativeImageSourceSet =
        javaExtension.sourceSets.create("nativeImageSourceSet") {
          java.srcDir(nativeImageDir.dir("java"))
          resources.srcDir(nativeImageDir.dir("resources"))
          compileClasspath += nativeImageJavacClasspath
        }

    val compileNativeImageClasses =
        project.tasks.register(
            "compileNativeImageClasses",
            org.gradle.api.tasks.compile.JavaCompile::class,
        ) {
          group = "build"
          description = "Compiles Native Image helper classes"
          source = nativeImageSourceSet.java
          classpath = nativeImageJavacClasspath
          destinationDirectory.set(project.layout.buildDirectory.dir("classes/native-image"))
          dependsOn(project.tasks.named("compileJava"))
        }

    val nativeImageJar =
        project.tasks.register("nativeImageJar", Jar::class) {
          group = "build"
          description = "Assembles Native Image jar and resources"
          dependsOn(compileNativeImageClasses)
          from(project.layout.buildDirectory.dir("classes/native-image"))
          from(nativeImageSourceSet.resources)
          archiveClassifier.set("nativeimage")
        }

    val nativeCompile =
        project.tasks.named("nativeCompile") {
          dependsOn(compileNativeImageClasses, nativeImageJar)
        }

    val nativeImageExecutable =
        project.layout.buildDirectory.file(
            "native/nativeCompile/" + if (Os.isFamily(Os.FAMILY_WINDOWS)) "ktfmt.exe" else "ktfmt",
        )

    project.tasks.register<Exec>("nativeImageSmokeTest") {
      group = "verification"
      description = "Runs the Native Image binary against the project sources"
      dependsOn(nativeCompile)

      executable = nativeImageExecutable.get().asFile.absolutePath
      args(
          project.layout.projectDirectory.dir("src").asFile.absolutePath,
          "--dry-run",
          "--set-exit-if-changed",
      )

      doFirst {
        if (!nativeImageExecutable.get().asFile.exists()) {
          throw GradleException("No executable exists at ${nativeImageExecutable.get().asFile}")
        }
        if (!nativeImageExecutable.get().asFile.canExecute()) {
          throw GradleException("${nativeImageExecutable.get().asFile} is not executable")
        }
      }
    }

    configureGraalvmNative(project, nativeImageJar)
  }

  private fun configureGraalvmNative(
      project: Project,
      nativeImageJar: org.gradle.api.tasks.TaskProvider<Jar>,
  ) {
    val nativeRelease = project.findProperty("ktfmt.native.release") == "true"
    val nativeTarget = project.findProperty("ktfmt.native.target") ?: "compatibility"
    val nativeGc = project.findProperty("ktfmt.native.gc") ?: "serial"
    val nativeDebug = project.findProperty("ktfmt.native.debug") == "true"
    val enableLto = project.findProperty("ktfmt.native.lto") == "true"
    val muslSysroot =
        (project.findProperty("ktfmt.native.muslHome") ?: System.getenv("MUSL_HOME"))?.toString()
    val preferMusl =
        (project.findProperty("ktfmt.native.musl") == "true").also { enabled ->
          require(!enabled || muslSysroot != null) {
            "When `ktfmt.native.musl` is true, -Pktfmt.native.muslHome or MUSL_HOME must be set to the Musl sysroot. " +
                "See https://www.graalvm.org/latest/reference-manual/native-image/guides/build-static-executables/"
          }
        }
    val preferSmol = project.findProperty("ktfmt.native.smol") == "true"
    val nativeOpt =
        when (val opt = project.findProperty("ktfmt.native.opt")) {
          null ->
              when {
                preferSmol -> "s"
                nativeRelease -> "3"
                else -> "b"
              }
          else -> opt
        }

    project.extensions.configure<GraalVMExtension>("graalvmNative") {
      binaries.named("main") {
        imageName.set("ktfmt")
        mainClass.set(ENTRYPOINT)
        classpath(
            project.files(
                nativeImageJar.flatMap { it.archiveFile },
                project.tasks.named("jar", Jar::class).flatMap { it.archiveFile },
                project.configurations.getByName("compileClasspath"),
                project.configurations.getByName("runtimeClasspath"),
                project.configurations.getByName("nativeImageClasspath"),
            ),
        )
        buildArgs(
            buildNativeImageArgs(
                project,
                nativeOpt,
                nativeTarget,
                nativeDebug,
                nativeGc,
                enableLto,
                preferMusl,
                muslSysroot,
            ),
        )
      }
    }
  }

  private fun buildNativeImageArgs(
      project: Project,
      nativeOpt: Any,
      nativeTarget: Any,
      nativeDebug: Boolean,
      nativeGc: Any,
      enableLto: Boolean,
      preferMusl: Boolean,
      muslSysroot: String?,
  ): List<String> = buildList {
    add("-O$nativeOpt")
    add("-march=$nativeTarget")
    if (nativeDebug) {
      add("-g")
      add("-H:+SourceLevelDebug")
    }

    add("--no-fallback")
    add("--gc=$nativeGc")
    add("--future-defaults=all")
    add("--link-at-build-time=org.jetbrains.ktfmt")
    add("--initialize-at-build-time=org.jetbrains.ktfmt")
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
    if (preferMusl) {
      add("-H:NativeLinkerOption=-L${muslSysroot}/lib")
    }

    addLinesFromFile(project, "initialize-at-build-time.txt") { "--initialize-at-build-time=$it" }
    addLinesFromFile(project, "initialize-at-run-time.txt") { "--initialize-at-run-time=$it" }

    when (System.getProperty("os.name")) {
      "Linux" ->
          when (normalizeArch(System.getProperty("os.arch"))) {
            "x64" ->
                if (preferMusl) {
                  addAll(listOf("--static", "--libc=musl", "-H:+StaticLibStdCpp"))
                } else {
                  add("--static-nolibc")
                }
            else -> add("--static-nolibc")
          }
      "Mac OS X" -> add("--static-nolibc")
    }
  }

  /** Canonicalizes [java.lang.System.getProperty]("os.arch") values that vary across JVMs. */
  private fun normalizeArch(arch: String?): String =
      when (arch) {
        "amd64",
        "x86_64" -> "x64"
        "aarch64",
        "arm64" -> "aarch64"
        else -> arch.orEmpty()
      }

  private fun MutableList<String>.addLinesFromFile(
      project: Project,
      fileName: String,
      mapper: (String) -> String,
  ) {
    val file = project.layout.projectDirectory.dir(NATIVE_IMAGE_SRC_DIR).file(fileName).asFile
    if (!file.exists()) {
      throw GradleException("Native Image configuration file not found: $file")
    }
    file
        .useLines { lines ->
          lines
              .map { it.trim() }
              .filter { it.isNotEmpty() && !it.startsWith("#") }
              .map(mapper)
              .toList()
        }
        .also { addAll(it) }
  }

  private companion object {
    const val ENTRYPOINT = "org.jetbrains.ktfmt.cli.Main"
    const val NATIVE_IMAGE_SRC_DIR = "src/main/native-image"
  }
}
