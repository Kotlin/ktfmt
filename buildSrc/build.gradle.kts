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
  `kotlin-dsl`
}

dependencies { implementation(nativeImageLibs.graalvm.gradle.plugin) }

gradlePlugin {
  plugins {
    register("ktfmt-file-generator") {
      id = "ktfmt.ktfmt-file-generator"
      implementationClass = "org.jetbrains.ktfmt.GenerateKtfmtFilePlugin"
    }
    register("native-image") {
      id = "ktfmt.native-image"
      implementationClass = "org.jetbrains.ktfmt.NativeImagePlugin"
    }
  }
}
