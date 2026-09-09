package org.jetbrains.ktfmt

import org.gradle.api.Project

internal val Project.signingKeyId
  get() = configurationProperty("libs.sign.key.id")
internal val Project.signingKey
  get() = configurationProperty("libs.sign.key.private")
internal val Project.signingPassword
  get() = configurationProperty("libs.sign.passphrase")
