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

import com.intellij.openapi.options.BoundSearchableConfigurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.BottomGap
import com.intellij.ui.dsl.builder.Cell
import com.intellij.ui.dsl.builder.bindIntText
import com.intellij.ui.dsl.builder.bindItem
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.layout.selected
import com.intellij.ui.layout.selectedValueMatches
import javax.swing.JCheckBox
import javax.swing.JComboBox
import javax.swing.JTextField
import org.jetbrains.ktfmt.format.FormattingOptions
import org.jetbrains.ktfmt.format.TrailingCommaManagementStrategy
import org.jetbrains.ktfmt.intellij.KtfmtSettings.EnabledState.Disabled
import org.jetbrains.ktfmt.intellij.KtfmtSettings.EnabledState.Enabled
import org.jetbrains.ktfmt.intellij.UiFormatterStyle.Custom
import org.jetbrains.ktfmt.intellij.UiFormatterStyle.Google
import org.jetbrains.ktfmt.intellij.UiFormatterStyle.KotlinLang
import org.jetbrains.ktfmt.intellij.UiFormatterStyle.Meta

@Suppress("DialogTitleCapitalization")
class KtfmtConfigurable(project: Project) :
    BoundSearchableConfigurable(
        displayName = "ktfmt Settings",
        _id = "org.jetbrains.ktfmt_idea_plugin.settings",
        helpTopic = "ktfmt",
    ) {
  private val settings = KtfmtSettings.getInstance(project)

  override fun createPanel(): DialogPanel = panel {
    lateinit var enabledCheckbox: JCheckBox
    row {
      enabledCheckbox =
          checkBox("Enable ktfmt")
              .bindSelected(
                  getter = { settings.isEnabled },
                  setter = { settings.setEnabled(if (it) Enabled else Disabled) },
              )
              .component
    }

    lateinit var styleComboBox: ComboBox<UiFormatterStyle>
    row {
      styleComboBox =
          comboBox(listOf(Meta, Google, KotlinLang, Custom))
              .label("Code style:")
              .bindItem(
                  getter = { settings.uiFormatterStyle },
                  setter = { settings.uiFormatterStyle = it ?: Meta },
              )
              .enabledIf(enabledCheckbox.selected)
              .component
    }

    group("Custom style") {
          lateinit var maxLineLength: JTextField
          row("Max line length:") {
            maxLineLength =
                textField()
                    .bindIntText(settings::customMaxLineLength)
                    .validatePositiveIntegerOrEmpty()
                    .component
          }

          lateinit var blockIndent: JTextField
          row("Block indent size:") {
            blockIndent =
                textField()
                    .bindIntText(settings::customBlockIndent)
                    .validatePositiveIntegerOrEmpty()
                    .component
          }

          lateinit var continuationIndent: JTextField
          row("Continuation indent size:") {
            continuationIndent =
                textField()
                    .bindIntText(settings::customContinuationIndent)
                    .validatePositiveIntegerOrEmpty()
                    .component
          }

          lateinit var trailingCommaManagementStrategy: JComboBox<TrailingCommaManagementStrategy>
          row("Trailing commas management") {
            trailingCommaManagementStrategy =
                comboBox(TrailingCommaManagementStrategy.entries)
                    .bindItem(
                        getter = { settings.customTrailingCommaManagementStrategy },
                        setter = {
                          settings.customTrailingCommaManagementStrategy =
                              it ?: TrailingCommaManagementStrategy.NONE
                        },
                    )
                    .component
          }

          lateinit var removeUnusedImports: JCheckBox
          row {
            removeUnusedImports =
                checkBox("Remove unused imports")
                    .bindSelected(settings::customRemoveUnusedImports)
                    .component
          }

          lateinit var preserveLambdaBreaks: JCheckBox
          row {
                preserveLambdaBreaks =
                    checkBox("Preserve user-authored line breaks inside lambdas")
                        .bindSelected(settings::customPreserveLambdaBreaks)
                        .component
              }
              .bottomGap(BottomGap.SMALL)

          row("Copy from:") {
            // Note: updating must be done via the components, and not the settings,
            // or the Kotlin DSL bindings will overwrite the values when applying
            link(Meta.toString()) {
                  UiFormatterStyle.getStandardFormattingOptions(Meta)
                      .updateFields(
                          maxLineLength,
                          blockIndent,
                          continuationIndent,
                          trailingCommaManagementStrategy,
                          removeUnusedImports,
                          preserveLambdaBreaks,
                      )
                }
                .component
                .autoHideOnDisable = false

            link(Google.toString()) {
                  UiFormatterStyle.getStandardFormattingOptions(Google)
                      .updateFields(
                          maxLineLength,
                          blockIndent,
                          continuationIndent,
                          trailingCommaManagementStrategy,
                          removeUnusedImports,
                          preserveLambdaBreaks,
                      )
                }
                .component
                .autoHideOnDisable = false

            link(KotlinLang.toString()) {
                  UiFormatterStyle.getStandardFormattingOptions(KotlinLang)
                      .updateFields(
                          maxLineLength,
                          blockIndent,
                          continuationIndent,
                          trailingCommaManagementStrategy,
                          removeUnusedImports,
                          preserveLambdaBreaks,
                      )
                }
                .component
                .autoHideOnDisable = false
          }
        }
        .visibleIf(styleComboBox.selectedValueMatches { it == Custom })
        .enabledIf(enabledCheckbox.selected)
  }
}

private fun FormattingOptions.updateFields(
    maxLineLength: JTextField,
    blockIndent: JTextField,
    continuationIndent: JTextField,
    trailingCommaManagementStrategy: JComboBox<TrailingCommaManagementStrategy>,
    removeUnusedImports: JCheckBox,
    preserveLambdaBreaks: JCheckBox,
) {
  maxLineLength.text = maxWidth.toString()
  blockIndent.text = this.blockIndent.toString()
  continuationIndent.text = this.continuationIndent.toString()
  trailingCommaManagementStrategy.selectedItem = this.trailingCommaManagementStrategy
  removeUnusedImports.isSelected = this.removeUnusedImports
  preserveLambdaBreaks.isSelected = this.preserveLambdaBreaks
}

private fun Cell<JTextField>.validatePositiveIntegerOrEmpty() = validationOnInput { jTextField ->
  if (jTextField.text.isNotEmpty()) {
    val parsedValue = jTextField.text.toIntOrNull()
    when {
      parsedValue == null -> error("Value must be an integer. Will default to 1")
      parsedValue <= 0 -> error("Value must be greater than zero. Will default to 1")
      else -> null
    }
  } else null
}
