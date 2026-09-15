/**
 * Based on
 * https://github.com/Kotlin/kotlinx-io/blob/master/build-logic/src/main/kotlin/kotlinx/io/conventions/kotlinx-io-publish.gradle.kts
 */
import org.gradle.kotlin.dsl.`maven-publish`
import org.gradle.kotlin.dsl.signing
import org.jetbrains.ktfmt.configurationProperty
import org.jetbrains.ktfmt.signingKey
import org.jetbrains.ktfmt.signingKeyId
import org.jetbrains.ktfmt.signingPassword

plugins {
  `maven-publish`
  signing
}

val repoUrl = configurationProperty("libs.repo.url")
val repoUsername = configurationProperty("libs.repo.user")
val repoPassword = configurationProperty("libs.repo.password")

afterEvaluate {
  publishing {
    repositories {
      if (repoUrl.isPresent) {
        maven {
          url = project.uri(repoUrl)
          credentials {
            username = repoUsername.orNull
            password = repoPassword.orNull
          }
        }
      }

      /** For local builds */
      maven(rootProject.layout.buildDirectory.dir("repo")) {
        name = "buildRepo"
      }
    }

    publications {
      create<MavenPublication>("maven") {
        groupId = "org.jetbrains"
        artifactId = "ktfmt"
        version = rootProject.version.toString()

        from(components["java"])
        artifact(tasks.named("sourcesJar"))
        artifact(tasks.named("javadocJar"))

        pom {
          name = "ktfmt"
          description =
              "A program that reformats Kotlin source code to comply with the common community standard for Kotlin code conventions."
          url = "https://github.com/Kotlin/ktfmt"
          inceptionYear = "2019"
          developers {
            developer {
              name = "Kotlin Team"
              organization = "JetBrains"
              organizationUrl = "https://www.jetbrains.com"
            }
          }
          licenses {
            license {
              name = "The Apache License, Version 2.0"
              url = "https://www.apache.org/licenses/LICENSE-2.0.txt"
            }
          }
          scm {
            url = "https://github.com/Kotlin/ktfmt.git"
          }
        }
      }
    }
  }

  signing {
    val signingKey = signingKey.orNull
    val signingPassword = signingPassword.orNull
    if (!signingKey.isNullOrBlank() && !signingPassword.isNullOrBlank()) {
      useInMemoryPgpKeys(signingKeyId.orNull, signingKey, signingPassword)
      sign(publishing.publications)
    }
  }
}
