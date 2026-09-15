import org.gradle.crypto.checksum.Checksum
import org.gradle.kotlin.dsl.register
import org.jetbrains.ktfmt.Arch
import org.jetbrains.ktfmt.NativeImageSmokeTestTask
import org.jetbrains.ktfmt.Os
import org.jetbrains.ktfmt.currentArch
import org.jetbrains.ktfmt.currentOs
import org.jetbrains.ktfmt.ktfmtVersion
import org.jetbrains.ktfmt.nativeImageProperty
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

val nativeImageGc = nativeImageProperty("ktfmt.native.gc").orElse("serial")

val enableNativeDebug =
    nativeImageProperty("ktfmt.native.debug").map { it.toBooleanStrict() }.orElse(false)

val enableLto = nativeImageProperty("ktfmt.native.lto").map { it.toBooleanStrict() }.orElse(false)

val enableMusl = nativeImageProperty("ktfmt.native.musl").map { it.toBooleanStrict() }.orElse(false)

val muslHome = nativeImageProperty("ktfmt.native.musl.home")

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
val nativeImageArchiveBaseName = ktfmtVersion.map {
  "ktfmt-${currentOs.osName}-${currentArch.archName}-${it}"
}
val nativeImageArchiveName = nativeImageArchiveBaseName.map { "$it.$nativeImageArchiveExtension" }
val archiveDirectory = layout.buildDirectory.dir("archive")

val archive =
    if (currentOs == Os.WINDOWS) {
      tasks.register<Zip>("nativeImageArchive") {
        configureNativeImageArchive(
            nativeCompile,
            nativeImageArchiveBaseName,
            nativeImageArchiveName,
            archiveDirectory,
        )
      }
    } else {
      tasks.register<Tar>("nativeImageArchive") {
        this.compression = Compression.GZIP
        configureNativeImageArchive(
            nativeCompile,
            nativeImageArchiveBaseName,
            nativeImageArchiveName,
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

var signatureFiles: FileCollection? = null

signing {
  val key = signingKey.orNull
  val password = signingPassword.orNull
  if (!key.isNullOrBlank() && !password.isNullOrBlank()) {
    useInMemoryPgpKeys(signingKeyId.orNull, key, password)
    val archiveSignatures = sign(archive.get())
    signatureFiles = files(archiveSignatures.map { it.signatureFiles }).builtBy(archiveSignatures)
  }
}

val artifacts =
    tasks.register<Copy>("nativeImageArtifacts") {
      group = "build"
      description = "Builds and signs the native image release archive and its SHA-256 checksum"
      from(archive, checksumFile, signatureFiles)
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
    buildArgs.addAll(nativeImageArgs())
  }
}

fun nativeImageArgs(): Provider<List<String>> {
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

fun muslLinkerArgs(): Provider<List<String>> =
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

fun staticLinkingArgs(): Provider<List<String>> {
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

fun AbstractArchiveTask.configureNativeImageArchive(
    nativeCompile: TaskProvider<Task>,
    archiveName: Provider<String>,
    fileName: Provider<String>,
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

fun linesFromFile(fileName: String): Provider<List<String>> {
  val file = nativeImageDir.file(fileName)
  return providers.fileContents(file).asText.map { text ->
    text.lines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }
  }
}

fun Provider<Boolean>.toArgs(vararg args: String): Provider<List<String>> {
  val enabledArgs = args.toList()
  return map { enabled -> if (enabled) enabledArgs else emptyList() }
}
