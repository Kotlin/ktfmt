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

package com.facebook.ktfmt.format

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class WhitespaceTombstonesTest {

  @Test
  fun testReplaceTrailingWhitespaceWithTombstone() {
    assertThat(WhitespaceTombstones.replaceTrailingWhitespaceWithTombstone("")).isEqualTo("")

    assertThat(WhitespaceTombstones.replaceTrailingWhitespaceWithTombstone("  sdfl"))
        .isEqualTo("  sdfl")
    assertThat(WhitespaceTombstones.replaceTrailingWhitespaceWithTombstone("  sdfl "))
        .isEqualTo("  sdfl${WhitespaceTombstones.SPACE_TOMBSTONE}")
    assertThat(WhitespaceTombstones.replaceTrailingWhitespaceWithTombstone("  sdfl  "))
        .isEqualTo("  sdfl ${WhitespaceTombstones.SPACE_TOMBSTONE}")

    assertThat(WhitespaceTombstones.replaceTrailingWhitespaceWithTombstone("  sdfl  \n skdjfh"))
        .isEqualTo("  sdfl ${WhitespaceTombstones.SPACE_TOMBSTONE}\n skdjfh")
    assertThat(WhitespaceTombstones.replaceTrailingWhitespaceWithTombstone("  sdfl  \n skdjfh "))
        .isEqualTo(
            "  sdfl ${WhitespaceTombstones.SPACE_TOMBSTONE}\n skdjfh${WhitespaceTombstones.SPACE_TOMBSTONE}",
        )
    assertThat(WhitespaceTombstones.replaceTrailingWhitespaceWithTombstone("  sdfl  \n\n skdjfh "))
        .isEqualTo(
            "  sdfl ${WhitespaceTombstones.SPACE_TOMBSTONE}\n\n skdjfh${WhitespaceTombstones.SPACE_TOMBSTONE}",
        )
  }
}
