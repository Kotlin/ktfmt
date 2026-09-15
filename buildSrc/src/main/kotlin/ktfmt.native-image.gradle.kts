import org.gradle.crypto.checksum.Checksum
import org.gradle.kotlin.dsl.register
import org.jetbrains.ktfmt.NativeImageSmokeTestTask
import org.jetbrains.ktfmt.Os
import org.jetbrains.ktfmt.currentArch
import org.jetbrains.ktfmt.currentOs
import org.jetbrains.ktfmt.ktfmtVersion
import org.jetbrains.ktfmt.signingKey
import org.jetbrains.ktfmt.signingKeyId
import org.jetbrains.ktfmt.signingPassword

plugins {
  application
  signing
  org.graalvm.buildtools.native
  id("org.gradle.crypto.checksum")
}

application {
  mainClass = "org.jetbrains.ktfmt.cli.Main"
}

val nativeImageJavacClasspath =
    configurations.create("nativeImageJavacClasspath") {
      extendsFrom(configurations.getByName("implementation"))
      isCanBeResolved = true
    }

val nativeImageLibs = extensions.getByType<VersionCatalogsExtension>().named("nativeImageLibs")
val nativeImageDir = layout.projectDirectory.dir("src/main/native-image")

dependencies {
  nativeImageJavacClasspath(nativeImageLibs.findLibrary("graalvm-nativeimage").get())
  nativeImageClasspath(nativeImageLibs.findLibrary("jline-terminal").get())
  nativeImageClasspath(nativeImageLibs.findLibrary("jline-terminal-jansi").get())
  nativeImageClasspath(nativeImageLibs.findLibrary("jline-terminal-jna").get())
  nativeImageClasspath(nativeImageLibs.findLibrary("jline-terminal-jni").get())
}

val nativeImageSourceSet =
    sourceSets.create("nativeImageSourceSet") {
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
      destinationDirectory = layout.buildDirectory.dir("classes/native-image")
      dependsOn(tasks.named("compileJava"))
    }

val nativeImageJar =
    tasks.register<Jar>("nativeImageJar") {
      group = "build"
      description = "Assembles Native Image jar and resources"
      from(compileNativeImageClasses.flatMap { it.destinationDirectory })
      from(nativeImageSourceSet.resources)
      archiveClassifier = "nativeimage"
    }

val nativeCompile =
    tasks.named("nativeCompile") {
      dependsOn(nativeImageJar)
      inputs.files(
          nativeImageDir.file("initialize-at-build-time.txt"),
          nativeImageDir.file("initialize-at-run-time.txt"),
      )
    }

val nativeImageArchiveExtension = if (currentOs == Os.WINDOWS) "zip" else "tar.gz"
val nativeImageArchiveBaseName =
    "ktfmt-${currentOs.osName}-${currentArch.archName}"
        .let { name ->
          ktfmtVersion.map { "$name-$it" }
        }
val nativeImageArchiveName = nativeImageArchiveExtension.let { extension ->
  nativeImageArchiveBaseName.map { "$it.$extension" }
}
val archiveDirectory = layout.buildDirectory.dir("archive")

val archive =
    tasks.register("nativeImageArchive", if (currentOs == Os.WINDOWS) Zip::class else Tar::class) {
      description = "Packs the native image distribution into the publishable release archive"
      archiveFileName.set(nativeImageArchiveName)
      destinationDirectory.set(archiveDirectory)
      isPreserveFileTimestamps = false
      isReproducibleFileOrder = true
      if (this is Tar) compression = Compression.GZIP
      from(nativeCompile) {
        into(nativeImageArchiveBaseName)
        filePermissions { unix("rwxr-xr-x") }
      }
    }

val checksum =
    tasks.register<Checksum>("nativeImageChecksum") {
      description = "Generates the SHA-256 checksum of the native image release archive"
      inputFiles.setFrom(archive.flatMap { it.archiveFile })
      outputDirectory.set(layout.buildDirectory.dir("checksums/nativeImage"))
      checksumAlgorithm.set(Checksum.Algorithm.SHA256)
    }

signing {
  val key = signingKey.orNull
  val password = signingPassword.orNull
  if (!key.isNullOrBlank() && !password.isNullOrBlank()) {
    useInMemoryPgpKeys(signingKeyId.orNull, key, password)
    sign(archive.get())
  }
}

tasks.register<Copy>("nativeImageArtifacts") {
  group = "build"
  description = "Builds and signs the native image release archive and its SHA-256 checksum"
  /**
   * We can't use `signTask` as a task provider because it may not be registered (if the signing is
   * not configured). Therefore, we have to manually wire the dependency between `signTask.map {
   * it.signatureFiles }` and `signTask`.
   */
  val signTask = archive.signTask()
  val signatures = files(signTask.map { it.signatureFiles }).builtBy(signTask)
  from(archive, checksum, signatures)
  into(layout.buildDirectory.dir("artifacts"))
}

tasks.register<NativeImageSmokeTestTask>("nativeImageSmokeTest") {
  group = "verification"
  description = "Runs the Native Image binary against the project sources"
  dependsOn(nativeCompile)

  binary =
      layout.buildDirectory.file(
          "native/nativeCompile/" + if (currentOs == Os.WINDOWS) "ktfmt.exe" else "ktfmt",
      )
  sources = layout.projectDirectory.dir("src")
  report = layout.buildDirectory.file("reports/native-image/smoke-test.txt")
}

graalvmNative {
  binaries.named("main") {
    imageName = "ktfmt"
    mainClass = "org.jetbrains.ktfmt.cli.Main"
    classpath(
        files(
            nativeImageJar.flatMap { it.archiveFile },
            tasks.named<Jar>("jar").flatMap { it.archiveFile },
            configurations.getByName("compileClasspath"),
            configurations.getByName("runtimeClasspath"),
            configurations.getByName("nativeImageClasspath"),
        ),
    )
    buildArgs.addAll(
        "-O3",
        "-march=compatibility",
        "--no-fallback",
        "--future-defaults=all",
        "--link-at-build-time=org.jetbrains.ktfmt",
        "--add-opens=java.base/java.util=ALL-UNNAMED",
        "--color=always",
        "--gc=serial",
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

    buildArgs.addAll(
        linesFromFile("initialize-at-build-time.txt").map { lines ->
          lines.map { "--initialize-at-build-time=$it" }
        },
    )
    buildArgs.addAll(
        linesFromFile("initialize-at-run-time.txt").map { lines ->
          lines.map { "--initialize-at-run-time=$it" }
        },
    )

    if (currentOs != Os.WINDOWS) {
      buildArgs.add("--static-nolibc")
    }
  }
}

fun linesFromFile(fileName: String): Provider<List<String>> {
  val file = nativeImageDir.file(fileName)
  return providers.fileContents(file).asText.map { text ->
    text.lines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }
  }
}

fun TaskProvider<out AbstractArchiveTask>.signTask(): TaskCollection<Sign> {
  val signTaskName = "sign${name.replaceFirstChar { it.uppercase() }}"
  return tasks.withType<Sign>().named { it == signTaskName }
}
