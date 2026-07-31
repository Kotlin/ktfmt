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

package com.facebook.ktfmt.nativeImage

import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.junit.jupiter.api.Test

/**
 * Guards the GraalVM `@Substitute` in `core/src/main/native-image` that replaces
 * `KotlinCoreEnvironment.Companion.registerApplicationExtensionPointsAndExtensionsFrom`. The
 * substitution is matched by name and parameter types at native-image build time; a mismatch is
 * silently ignored and only surfaces as a native runtime failure. This JVM test runs on every build
 * so a Kotlin compiler upgrade that changes the signature fails here first, before the native image
 * breaks.
 */
class NativeImageSubstitutionTest {
  @Test
  fun `substituted Kotlin compiler method still exists with expected signature`() {
    // Throws NoSuchMethodException (failing the test) if the upstream signature changes.
    KotlinCoreEnvironment.Companion::class
        .java
        .getDeclaredMethod(
            "registerApplicationExtensionPointsAndExtensionsFrom",
            CompilerConfiguration::class.java,
            String::class.java,
        )
  }
}
