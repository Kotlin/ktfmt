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

package org.jetbrains.ktfmt.format

import org.jetbrains.kotlin.cli.common.messages.MessageRenderer.PLAIN_RELATIVE_PATHS
import org.jetbrains.kotlin.cli.common.messages.PrintingMessageCollector
import org.jetbrains.kotlin.cli.jvm.compiler.EnvironmentConfigFiles
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.jetbrains.kotlin.com.intellij.openapi.util.Disposer
import org.jetbrains.kotlin.com.intellij.openapi.util.text.StringUtil
import org.jetbrains.kotlin.com.intellij.psi.PsiErrorElement
import org.jetbrains.kotlin.com.intellij.psi.PsiManager
import org.jetbrains.kotlin.com.intellij.testFramework.LightVirtualFile
import org.jetbrains.kotlin.config.CommonConfigurationKeys
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType
import org.jetbrains.kotlin.psi.psiUtil.startOffset

/** Parser parses a Kotlin file given as a string and returns its parse tree. */
object Parser {

  /**
   * The environment used to open a KtFile
   *
   * We allocate it once, and ignore the Diposable. This causes a memory leak, but it was also
   * causing a worse memory leak before when we created new ones and disposed them. This leak comes
   * from [KotlinCoreEnvironment.createForProduction]:
   * https://github.com/JetBrains/kotlin/blob/master/compiler/cli/src/org/jetbrains/kotlin/cli/jvm/compiler/KotlinCoreEnvironment.kt#L544
   */
  val env: KotlinCoreEnvironment by lazy {
    // To hide annoying warning on Windows
    System.setProperty("idea.use.native.fs.for.win", "false")
    val disposable = Disposer.newDisposable()
    val configuration = CompilerConfiguration()
    configuration.put(
        CommonConfigurationKeys.MESSAGE_COLLECTOR_KEY,
        PrintingMessageCollector(System.err, PLAIN_RELATIVE_PATHS, false),
    )
    @Suppress("OPT_IN_USAGE_ERROR") // KotlinCoreEnvironment.createForProduction
    KotlinCoreEnvironment.createForProduction(
        disposable,
        configuration,
        EnvironmentConfigFiles.JVM_CONFIG_FILES,
    )
  }

  fun parse(code: String): KtFile {
    val virtualFile = LightVirtualFile("temp.kts", KotlinFileType.INSTANCE, code)
    val ktFile = PsiManager.getInstance(env.project).findFile(virtualFile) as KtFile
    val descendants = ktFile.collectDescendantsOfType<PsiErrorElement>()
    if (descendants.isNotEmpty()) throwParseError(code, descendants[0])
    return ktFile
  }

  private fun throwParseError(fileContents: String, error: PsiErrorElement): Nothing {
    throw ParseError(
        error.errorDescription,
        StringUtil.offsetToLineColumn(fileContents, error.startOffset),
    )
  }
}
