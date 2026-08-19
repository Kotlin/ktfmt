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

package org.jetbrains.ktfmt.intellij

import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.Service.Level.PROJECT
import com.intellij.openapi.components.SimplePersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import org.jetbrains.ktfmt.format.Formatter
import org.jetbrains.ktfmt.format.FormattingOptions
import org.jetbrains.ktfmt.format.TrailingCommaManagementStrategy
import org.jetbrains.ktfmt.intellij.KtfmtSettings.EnabledState.Disabled
import org.jetbrains.ktfmt.intellij.KtfmtSettings.EnabledState.Enabled
import org.jetbrains.ktfmt.intellij.KtfmtSettings.EnabledState.Unknown
import org.jetbrains.ktfmt.intellij.UiFormatterStyle.Meta

@Service(PROJECT)
@State(name = "KtfmtSettings", storages = [Storage("ktfmt.xml")])
internal class KtfmtSettings(private val project: Project) :
    SimplePersistentStateComponent<KtfmtSettings.State>(State()) {
  val isUninitialized: Boolean
    get() = state.enableKtfmt == Unknown

  var uiFormatterStyle: UiFormatterStyle
    get() = state.uiFormatterStyle
    set(uiFormatterStyle) {
      state.uiFormatterStyle = uiFormatterStyle
    }

  var customFormattingOptions: FormattingOptions
    get() =
        FormattingOptions(
            maxWidth = state.customMaxLineLength,
            blockIndent = state.customBlockIndent,
            continuationIndent = state.customContinuationIndent,
            trailingCommaManagementStrategy =
                state.customTrailingCommaManagementStrategy.toTrailingCommaManagementStrategy(),
            removeUnusedImports = state.customRemoveUnusedImports,
            preserveLambdaBreaks = state.customPreserveLambdaBreaks,
        )
    set(customFormattingOptions) {
      state.applyCustomFormattingOptions(customFormattingOptions)
    }

  var customMaxLineLength: Int
    get() = state.customMaxLineLength
    set(maxLineLength) {
      state.customMaxLineLength = maxLineLength.coerceAtLeast(1)
    }

  var customBlockIndent: Int
    get() = state.customBlockIndent
    set(blockIndent) {
      state.customBlockIndent = blockIndent.coerceAtLeast(1)
    }

  var customContinuationIndent: Int
    get() = state.customContinuationIndent
    set(continuationIndent) {
      state.customContinuationIndent = continuationIndent.coerceAtLeast(1)
    }

  var customTrailingCommaManagementStrategy: TrailingCommaManagementStrategy
    get() = state.customTrailingCommaManagementStrategy.toTrailingCommaManagementStrategy()
    set(value) {
      state.customTrailingCommaManagementStrategy = value.name
    }

  var customRemoveUnusedImports: Boolean
    get() = state.customRemoveUnusedImports
    set(removeUnusedImports) {
      state.customRemoveUnusedImports = removeUnusedImports
    }

  var customPreserveLambdaBreaks: Boolean
    get() = state.customPreserveLambdaBreaks
    set(preserveLambdaBreaks) {
      state.customPreserveLambdaBreaks = preserveLambdaBreaks
    }

  var isEnabled: Boolean
    get() = state.enableKtfmt == Enabled
    set(enabled) {
      setEnabled(if (enabled) Enabled else Disabled)
    }

  fun setEnabled(enabled: EnabledState) {
    state.enableKtfmt = enabled
  }

  override fun loadState(state: State) {
    val migrated = loadOrMigrateIfNeeded(state)
    super.loadState(migrated)
  }

  private fun loadOrMigrateIfNeeded(state: State): State {
    val migrationSettings = project.service<KtfmtSettingsMigration>()

    return when (val stateVersion = migrationSettings.stateVersion) {
      KtfmtSettingsMigration.CURRENT_VERSION -> state
      1,
      2 -> migrationSettings.migrateToCurrent(state)
      else -> {
        thisLogger().error("Cannot migrate settings from $stateVersion. Using defaults.")
        State()
      }
    }
  }

  internal enum class EnabledState {
    Unknown,
    Enabled,
    Disabled,
  }

  internal class State : BaseState() {
    @Deprecated(
        "Deprecated in V2. Use enableKtfmt instead.",
        replaceWith = ReplaceWith("enableKtfmt"),
    )
    var enabled: String? by string()
    @Deprecated(
        "Deprecated in V3. Use customTrailingCommaManagementStrategy instead.",
        replaceWith = ReplaceWith("customTrailingCommaManagementStrategy"),
    )
    var customManageTrailingCommas: Boolean by property(
        Formatter.META_FORMAT.trailingCommaManagementStrategy !=
            TrailingCommaManagementStrategy.NONE,
    )

    var enableKtfmt: EnabledState by enum(Unknown)
    var uiFormatterStyle: UiFormatterStyle by enum(Meta)

    var customMaxLineLength: Int by property(Formatter.META_FORMAT.maxWidth)
    var customBlockIndent: Int by property(Formatter.META_FORMAT.blockIndent)
    var customContinuationIndent: Int by property(Formatter.META_FORMAT.continuationIndent)
    var customTrailingCommaManagementStrategy: String by property(
        Formatter.META_FORMAT.trailingCommaManagementStrategy.name,
        isDefault = { it == Formatter.META_FORMAT.trailingCommaManagementStrategy.name },
    )
    var customRemoveUnusedImports: Boolean by property(Formatter.META_FORMAT.removeUnusedImports)
    var customPreserveLambdaBreaks: Boolean by property(Formatter.META_FORMAT.preserveLambdaBreaks)

    fun applyCustomFormattingOptions(formattingOptions: FormattingOptions) {
      customMaxLineLength = formattingOptions.maxWidth
      customBlockIndent = formattingOptions.blockIndent
      customContinuationIndent = formattingOptions.continuationIndent
      customTrailingCommaManagementStrategy = formattingOptions.trailingCommaManagementStrategy.name
      customRemoveUnusedImports = formattingOptions.removeUnusedImports
      customPreserveLambdaBreaks = formattingOptions.preserveLambdaBreaks

      incrementModificationCount()
    }
  }

  private fun String.toTrailingCommaManagementStrategy(
      defaultValue: TrailingCommaManagementStrategy = TrailingCommaManagementStrategy.NONE,
  ): TrailingCommaManagementStrategy =
      TrailingCommaManagementStrategy.entries.find { it.name == this || it.toString() == this }
          ?: defaultValue

  companion object {
    @JvmStatic
    fun getInstance(project: Project): KtfmtSettings = project.getService(KtfmtSettings::class.java)
  }
}
