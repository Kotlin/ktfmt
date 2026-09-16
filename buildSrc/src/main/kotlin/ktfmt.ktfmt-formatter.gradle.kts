import org.gradle.api.attributes.Bundling
import org.gradle.api.attributes.Category
import org.gradle.api.attributes.LibraryElements
import org.gradle.api.attributes.Usage
import org.gradle.api.attributes.java.TargetJvmEnvironment
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.JavaExec
import org.gradle.kotlin.dsl.assign
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.register
import org.jetbrains.ktfmt.KtfmtArgumentsProvider

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

dependencies { ktfmtCliDependencies(project(":ktfmt")) }

val ktfmtFiles =
    fileTree(rootDir) {
      include("**/*.kt")
      include("**/*.kts")
      exclude("**/build/**")
      exclude("**/.gradle/**")
      exclude("**/.intellijPlatform/**")
    }

fun JavaExec.configureKtfmtRun(files: FileCollection, check: Boolean) {
  mainClass = "org.jetbrains.ktfmt.cli.Main"
  argumentProviders.add(KtfmtArgumentsProvider(files, check))
  classpath(ktfmtCliClasspath)
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

subprojects {
  tasks.named { it == "check" }.configureEach { dependsOn(ktfmtCheck) }
}
