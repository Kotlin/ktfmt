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
import com.intellij.openapi.components.SimplePersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros
import org.jetbrains.ktfmt.format.TrailingCommaManagementStrategy
import org.jetbrains.ktfmt.intellij.KtfmtSettings.EnabledState.Disabled
import org.jetbrains.ktfmt.intellij.KtfmtSettings.EnabledState.Enabled
import org.jetbrains.ktfmt.intellij.KtfmtSettings.EnabledState.Unknown
import org.jetbrains.ktfmt.intellij.KtfmtSettingsMigration.MigrationState

@Service(Service.Level.PROJECT)
@State(name = "KtfmtSettingsMigration", storages = [(Storage(StoragePathMacros.WORKSPACE_FILE))])
internal class KtfmtSettingsMigration :
    SimplePersistentStateComponent<MigrationState>(MigrationState()) {

  // ---------
  // Changelog
  // ---------
  //
  // v3 manageTrailingCommas -> trailingCommaManagementStrategy (0.57+)
  // v2 enabled [bool] -> enableKtfmt [enum], custom styles (0.52+)
  // v1 initial version - enabled is a boolean, only preset styles
  var stateVersion: Int
    get() = state.stateVersion.takeIf { it > 0 } ?: 1
    set(value) {
      state.stateVersion = value
    }

  fun migrateToCurrent(oldState: KtfmtSettings.State): KtfmtSettings.State {
    var migratedState = oldState
    if (state.stateVersion == 1) {
      migratedState = migrateFromV1(migratedState)
    }
    if (state.stateVersion == 2) {
      migratedState = migrateFromV2(migratedState)
    }
    return migratedState
  }

  fun migrateFromV1(v1State: KtfmtSettings.State): KtfmtSettings.State {
    check(state.stateVersion == 1) { "Should only be called when stateVersion is 1" }
    val migrated =
        KtfmtSettings.State().apply {
          copyFrom(v1State)

          enableKtfmt =
              @Suppress("DEPRECATION")
              when (v1State.enabled) {
                "true" -> Enabled
                "false" -> Disabled
                else -> Unknown
              }
          @Suppress("DEPRECATION")
          enabled = null
        }
    state.stateVersion = 2
    return migrated
  }

  fun migrateFromV2(v2State: KtfmtSettings.State): KtfmtSettings.State {
    check(state.stateVersion == 2) { "Should only be called when stateVersion is 2" }
    val migrated =
        KtfmtSettings.State().apply {
          copyFrom(v2State)

          customTrailingCommaManagementStrategy =
              @Suppress("DEPRECATION")
              when (v2State.customManageTrailingCommas) {
                true -> TrailingCommaManagementStrategy.COMPLETE
                else -> TrailingCommaManagementStrategy.NONE
              }.name
        }
    state.stateVersion = 3
    return migrated
  }

  class MigrationState : BaseState() {
    var stateVersion: Int by property(-1)
  }

  companion object {
    const val CURRENT_VERSION = 3
  }
}
