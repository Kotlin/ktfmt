import org.gradle.kotlin.dsl.assign
import org.gradle.kotlin.dsl.register
import org.jetbrains.ktfmt.GenerateKtfmtFileTask

tasks.register<GenerateKtfmtFileTask>("generateKtfmtFile") {
  description = "Generate Ktfmt.kt"
  propertiesFile = rootProject.file("gradle.properties")
  outputFile = layout.buildDirectory.file("generated/main/kotlin/org/jetbrains/ktfmt/util/Ktfmt.kt")
}
