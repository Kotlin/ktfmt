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

import com.intellij.notification.Notification
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType.INFORMATION
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity.Background

class InitialConfigurationStartupActivity : Background {
  override fun runActivity(project: Project) {
    val settings = KtfmtSettings.getInstance(project)

    if (settings.isUninitialized) {
      settings.isEnabled = false
      displayNewUserNotification(project, settings)
    }
  }

  private fun displayNewUserNotification(project: Project, settings: KtfmtSettings) {
    val notification =
        Notification(
            NotificationGroupManager.getInstance()
                .getNotificationGroup(NOTIFICATION_TITLE)
                .displayId,
            NOTIFICATION_TITLE,
            "The ktfmt plugin is disabled by default.",
            INFORMATION,
        )

    notification
        .addAction(
            object : AnAction("Enable for This Project") {
              override fun actionPerformed(e: AnActionEvent) {
                settings.isEnabled = true
                notification.expire()
              }
            },
        )
        .notify(project)
  }

  companion object {
    private const val NOTIFICATION_TITLE = "Enable ktfmt"
  }
}
