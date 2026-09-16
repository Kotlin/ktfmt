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

plugins {
  alias(libs.plugins.kotlin)
  alias(libs.plugins.dependencyAnalysis)
  id("ktfmt.ktfmt-formatter")

  alias(libs.plugins.dokka) apply false
  alias(libs.plugins.dokka.javadoc) apply false
  alias(libs.plugins.intelliJPlatform) apply false
  alias(libs.plugins.shadowJar) apply false
}

version = providers.gradleProperty("ktfmt.version").get()

tasks.wrapper { distributionType = Wrapper.DistributionType.ALL }

dependencyAnalysis {
  issues {
    all {
      // Compiled by a separate task against a handcrafted classpath
      ignoreSourceSet("nativeImageSourceSet")
      onUnusedDependencies {
        severity("fail")
      }
      onAny {
        severity("warn")
      }
    }
  }
}
