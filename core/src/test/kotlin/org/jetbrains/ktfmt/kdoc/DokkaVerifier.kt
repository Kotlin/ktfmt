/*
 * Copyright (c) Tor Norbye.
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

@file:Suppress("PropertyName", "PrivatePropertyName")

package org.jetbrains.ktfmt.kdoc

import java.io.BufferedReader
import java.io.File
import org.junit.jupiter.api.Assertions.assertEquals

/**
 * Verifies that two KDoc comment strings render to the same HTML documentation using Dokka. This is
 * used by the test infrastructure to make sure that the transformations we're allowing are not
 * changing the appearance of the documentation.
 *
 * Unfortunately, just diffing HTML strings isn't always enough, because dokka will preserve some
 * text formatting which is immaterial to the HTML appearance. Therefore, if you've also installed
 * Pandoc, it will use that to generate a text rendering of the HTML which is then used for diffing
 * instead. (Even this isn't fullproof because pandoc also preserves some details that should not
 * matter). Text rendering does drop a lot of markup (such as bold and italics) so it would be
 * better to compare in some other format, such as PDF, but unfortunately, the PDF rendering doesn't
 * appear to be stable; rendering the same document twice yields a binary diff.
 *
 * Dokka no longer provides a fat/shadow jar; instead you have to download a bunch of different
 * dependencies. Therefore, for convenience this is set up to point to an AndroidX checkout, which
 * has all the prebuilts. Point the below to AndroidX and the rest should work.
 */
class DokkaVerifier(private val tempFolder: File) {
  // Configuration parameters
  // Checkout of https://github.com/androidx/androidx
  private val ANDROIDX_HOME: String? = null

  // Optional install of pandoc, e.g. "/opt/homebrew/bin/pandoc"
  private val PANDOC: String? = null

  // JDK install
  private val JAVA_HOME: String? = System.getenv("JAVA_HOME") ?: System.getProperty("java.home")

  fun verify(before: String, after: String) {
    JAVA_HOME ?: return
    ANDROIDX_HOME ?: return

    val androidx = File(ANDROIDX_HOME)
    if (!androidx.isDirectory) {
      return
    }

    val prebuilts = File(androidx, "prebuilts")
    if (!prebuilts.isDirectory) {
      println("AndroidX prebuilts not found; not verifying with Dokka")
    }
    val cli = find(prebuilts, "org.jetbrains.dokka", "dokka-cli")
    val analysis = find(prebuilts, "org.jetbrains.dokka", "dokka-analysis")
    val base = find(prebuilts, "org.jetbrains.dokka", "dokka-base")
    val compiler = find(prebuilts, "org.jetbrains.dokka", "kotlin-analysis-compiler")
    val intellij = find(prebuilts, "org.jetbrains.dokka", "kotlin-analysis-intellij")
    val coroutines = find(prebuilts, "org.jetbrains.kotlinx", "kotlinx-coroutines-core")
    val html = find(prebuilts, "org.jetbrains.kotlinx", "kotlinx-html-jvm")
    val freemarker = find(prebuilts, "org.freemarker", "freemarker")

    val src = File(tempFolder, "src")
    val out = File(tempFolder, "dokka")
    src.mkdirs()
    out.mkdirs()

    val beforeFile = File(src, "before.kt")
    beforeFile.writeText("${before.split("\n").joinToString("\n") { it.trim() }}\nclass Before\n")

    val afterFile = File(src, "after.kt")
    afterFile.writeText("${after.split("\n").joinToString("\n") { it.trim() }}\nclass After\n")

    val args = mutableListOf<String>()
    args.add(File(JAVA_HOME, "bin/java").path)
    args.add("-jar")
    args.add(cli.path)
    args.add("-pluginsClasspath")
    val pathSeparator =
        ";" // instead of File.pathSeparator as would have been reasonable (e.g. : on Unix)
    val path =
        listOf(analysis, base, compiler, intellij, coroutines, html, freemarker).joinToString(
            pathSeparator,
        ) {
          it.path
        }
    args.add(path)
    args.add("-sourceSet")
    args.add("-src $src") // (nested parameter within -sourceSet)
    args.add("-outputDir")
    args.add(out.path)
    executeProcess(args)

    fun getHtml(file: File): String {
      val rendered = file.readText()
      val begin = rendered.indexOf("<div class=\"copy-popup-wrapper popup-to-left\">")
      val end = rendered.indexOf("<div class=\"tabbedcontent\">", begin)
      return rendered.substring(begin, end).replace(Regex(" +"), " ").replace(">", ">\n")
    }

    fun getText(file: File): String? {
      return if (PANDOC != null) {
        val pandocFile = File(PANDOC)
        if (!pandocFile.isFile) {
          error("Cannot execute $pandocFile")
        }
        val outFile = File(out, "text.text")
        executeProcess(listOf(PANDOC, file.path, "-o", outFile.path))
        val rendered = outFile.readText()

        val begin = rendered.indexOf("[]{.copy-popup-icon}Content copied to clipboard")
        val end = rendered.indexOf("::: tabbedcontent", begin)
        rendered.substring(begin, end).replace(Regex(" +"), " ").replace(">", ">\n")
      } else {
        null
      }
    }

    val indexBefore = File("$out/root/[root]/-before/index.html")
    val beforeContents = getHtml(indexBefore)
    val indexAfter = File("$out/root/[root]/-after/index.html")
    val afterContents = getHtml(indexAfter)
    if (beforeContents != afterContents) {
      val beforeText = getText(indexBefore)
      val afterText = getText(indexAfter)
      if (beforeText != null && afterText != null) {
        assertEquals(beforeText, afterText)
        return
      }

      assertEquals(beforeContents, afterContents)
    }
  }

  private fun find(prebuilts: File, group: String, artifact: String): File {
    val versionDir = File(prebuilts, "androidx/external/${group.replace('.','/')}/$artifact")
    val versions =
        versionDir.listFiles().filter { it.name.first().isDigit() }.sortedByDescending { it.name }
    for (version in versions.map { it.name }) {
      val jar = File(versionDir, "$version/$artifact-$version.jar")
      if (jar.isFile) {
        return jar
      }
    }
    error("Could not find a valid jar file for $group:$artifact")
  }

  private fun executeProcess(args: List<String>) {
    var input: BufferedReader? = null
    var error: BufferedReader? = null
    try {
      val process = Runtime.getRuntime().exec(args.toTypedArray())
      input = process.inputStream.bufferedReader()
      error = process.errorStream.bufferedReader()
      val exitVal = process.waitFor()
      if (exitVal != 0) {
        val sb = StringBuilder()
        sb.append("Failed to execute process\n")
        sb.append("Command args:\n")
        for (arg in args) {
          sb.append("  ").append(arg).append("\n")
        }
        sb.append("Standard output:\n")
        var line: String?
        while (input.readLine().also { line = it } != null) {
          sb.append(line).append("\n")
        }
        sb.append("Error output:\n")
        while (error.readLine().also { line = it } != null) {
          sb.append(line).append("\n")
        }
        error(sb.toString())
      }
    } catch (t: Throwable) {
      val sb = StringBuilder()
      for (arg in args) {
        sb.append("  ").append(arg).append("\n")
      }
      t.printStackTrace()
      error("Could not run process:\n$sb")
    } finally {
      input?.close()
      error?.close()
    }
  }
}
