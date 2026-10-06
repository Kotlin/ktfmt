import org.gradle.kotlin.dsl.assign
import org.gradle.kotlin.dsl.register
import org.jetbrains.kotlinx.ktfmt.GenerateKtfmtFileTask
import org.jetbrains.kotlinx.ktfmt.ktfmtVersion

tasks.register<GenerateKtfmtFileTask>("generateKtfmtFile") {
  description = "Generate Ktfmt.kt"
  properties.put("version", ktfmtVersion)
  outputFile =
      layout.buildDirectory.file("generated/main/kotlin/org/jetbrains/kotlinx/ktfmt/util/Ktfmt.kt")
}
