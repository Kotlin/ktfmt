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

package org.jetbrains.ktfmt.testutil

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue

fun assertContains(expected: String?, actual: String, message: String? = null) {
  assertNotNull(expected, "Subject of `assertContains` should not be null")
  assertTrue(expected!!.contains(actual), message)
}

fun assertContainsMatch(expected: String?, actual: String, message: String? = null) {
  assertNotNull(expected, "Subject of `assertContainsMatch` should not be null")
  val regex = actual.toRegex()
  assertTrue(regex.containsMatchIn(expected!!), message)
}

fun assertDoesNotContain(expected: String?, actual: String, message: String? = null) {
  assertNotNull(expected, "Subject of `assertDoesNotContain` should not be null")
  assertFalse(expected!!.contains(actual), message)
}

fun assertStartsWith(string: String?, prefix: String, message: String? = null) {
  assertNotNull(string, "Subject of `assertStartsWith` should not be null")
  assertTrue(string!!.startsWith(prefix), message)
}
