package org.jetbrains.ktfmt

import kotlin.jvm.java
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.dsl.RepositoryHandler
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.maven.plugins.MavenPublishPlugin
import org.gradle.kotlin.dsl.assign
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.get
import org.gradle.kotlin.dsl.maven
import org.gradle.plugins.signing.SigningExtension
import org.gradle.plugins.signing.SigningPlugin

private val Project.repoUrl
  get() = configurationProperty("libs.repo.url")
private val Project.repoUsername
  get() = configurationProperty("libs.repo.user")
private val Project.repoPassword
  get() = configurationProperty("libs.repo.password")

/**
 * Based on
 * https://github.com/Kotlin/kotlinx-io/blob/master/build-logic/src/main/kotlin/kotlinx/io/conventions/kotlinx-io-publish.gradle.kts
 */
@Suppress("unused")
class PublishingPlugin : Plugin<Project> {
  override fun apply(project: Project) {
    project.pluginManager.apply(MavenPublishPlugin::class.java)
    project.pluginManager.apply(SigningPlugin::class.java)

    project.afterEvaluate {
      project.extensions.configure<PublishingExtension> {
        repositories {
          configureRepos(project)
        }

        publications {
          create<MavenPublication>("maven") {
            groupId = "org.jetbrains"
            artifactId = "ktfmt"
            version = project.rootProject.version.toString()

            from(project.components["java"])
            artifact(project.tasks.named("sourcesJar"))
            artifact(project.tasks.named("javadocJar"))

            configurePom()
            configureSigning(project)
          }
        }
      }
    }
  }

  private fun RepositoryHandler.configureRepos(project: Project) {
    val repositoryUrl = project.repoUrl.orNull
    if (!repositoryUrl.isNullOrBlank()) {
      maven {
        url = project.uri(repositoryUrl)
        credentials {
          username = project.repoUsername.orNull
          password = project.repoPassword.orNull
        }
      }
    }

    /** For local builds */
    maven(project.rootProject.layout.buildDirectory.dir("repo")) {
      name = "buildRepo"
    }
  }

  private fun MavenPublication.configurePom() {
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

  private fun MavenPublication.configureSigning(project: Project) {
    val keyId = project.signingKeyId.orNull
    val signingKey = project.signingKey.orNull
    val signingPassword = project.signingPassword.orNull
    if (!signingKey.isNullOrBlank() && !signingPassword.isNullOrBlank()) {
      project.extensions.configure<SigningExtension> {
        useInMemoryPgpKeys(keyId, signingKey, signingPassword)
        sign(this@configureSigning)
      }
    }
  }
}
