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

package org.jetbrains.ktfmt.nativeImage;

import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.annotate.TargetElement;
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment;
import org.jetbrains.kotlin.config.CompilerConfiguration;

@SuppressWarnings({"unused"})
@TargetClass(KotlinCoreEnvironment.Companion.class)
final class KotlinCoreEnvironmentCompanion {
  @Substitute
  @TargetElement(name = "registerApplicationExtensionPointsAndExtensionsFrom")
  private void stubbedRegisterApplicationExtensionPointsAndExtensionsFrom(
      CompilerConfiguration configuration, String configFilePath) {
    // Nothing at this time.
  }
}
