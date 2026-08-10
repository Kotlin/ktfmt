/*
 * Portions Copyright (c) Meta Platforms, Inc. and affiliates.
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

/*
 * Copyright 2016 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

/*
 * This was copied from https://github.com/google/google-java-format and modified extensively to
 * work for Kotlin formatting
 */

package org.jetbrains.ktfmt.kdoc

/** Mutable integer for tracking the level of nesting. */
internal class NestingCounter {
  private var value: Int = 0

  val isPositive: Boolean
    get() = value > 0

  fun value(): Int {
    return value
  }

  fun increment() {
    value++
  }

  fun incrementIfPositive() {
    if (value > 0) {
      value++
    }
  }

  fun decrementIfPositive() {
    if (value > 0) {
      value--
    }
  }

  fun reset() {
    value = 0
  }
}
