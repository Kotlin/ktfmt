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

package org.jetbrains.ktfmt.cli

import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.absolute
import org.ec4j.core.EditorConfigLoader
import org.ec4j.core.PropertyTypeRegistry
import org.ec4j.core.Resource
import org.ec4j.core.ResourceProperties
import org.ec4j.core.ResourcePropertiesService
import org.ec4j.core.model.EditorConfig
import org.ec4j.core.model.PropertyType
import org.ec4j.core.model.Version.CURRENT
import org.jetbrains.ktfmt.format.FormattingOptions
import org.jetbrains.ktfmt.format.TrailingCommaManagementStrategy

object EditorConfigResolver {

  private val ijKotlinIndentSize: PropertyType<Int> =
      PropertyType.LowerCasingPropertyType(
          "ij_kotlin_indent_size",
          "Denotes the indent size for Kotlin files. Takes priority over indent_size.",
          PropertyType.PropertyValueParser.POSITIVE_INT_VALUE_PARSER,
      )

  private val ijContinuationIndentSize: PropertyType<Int> =
      PropertyType.LowerCasingPropertyType(
          "ij_continuation_indent_size",
          "Denotes the continuation indent size. Useful to distinguish code blocks versus continuation lines",
          PropertyType.PropertyValueParser.POSITIVE_INT_VALUE_PARSER,
      )

  private val ijKotlinContinuationIndentSize: PropertyType<Int> =
      PropertyType.LowerCasingPropertyType(
          "ij_kotlin_continuation_indent_size",
          "Denotes the Kotlin-specific continuation indent size. Takes precedence over ij_continuation_indent_size",
          PropertyType.PropertyValueParser.POSITIVE_INT_VALUE_PARSER,
      )

  private val commaManagementStrategy: PropertyType<TrailingCommaManagementStrategy> =
      PropertyType.LowerCasingPropertyType(
          "ktfmt_trailing_comma_management_strategy",
          "Ktfmt Trailing Comma Management Strategy",
          { _: String, value: String ->
            TrailingCommaManagementStrategy.entries
                .find { it.name.lowercase() == value }
                ?.let { PropertyType.PropertyValue.valid(value, it) }
                ?: PropertyType.PropertyValue.invalid(
                    value,
                    "Unknown ktfmt_trailing_comma_management_strategy value '$value'",
                )
          },
          TrailingCommaManagementStrategy.entries.map { it.name.lowercase() }.toSet(),
      )

  private val propertyTypeRegistry by
      lazy(LazyThreadSafetyMode.NONE) {
        PropertyTypeRegistry.builder()
            .defaults()
            .type(PropertyType.max_line_length) // missing from defaults?
            .type(ijKotlinIndentSize)
            .type(ijContinuationIndentSize)
            .type(ijKotlinContinuationIndentSize)
            .type(commaManagementStrategy)
            .build()
      }

  private object Cache : org.ec4j.core.Cache {
    val cached = ConcurrentHashMap<Resource, EditorConfig>()

    override fun get(
        editorConfigFile: Resource,
        loader: EditorConfigLoader,
    ): EditorConfig = cached.computeIfAbsent(editorConfigFile, loader::load)
  }

  private val resourcePropertiesService: ResourcePropertiesService by
      lazy(LazyThreadSafetyMode.NONE) {
        ResourcePropertiesService.builder()
            .cache(Cache)
            .loader(EditorConfigLoader.of(CURRENT, propertyTypeRegistry))
            .build()
      }

  fun resolveFormattingOptions(file: File, baseOptions: FormattingOptions): FormattingOptions =
      resourcePropertiesService
          .queryProperties(Resource.Resources.ofPath(file.toPath().absolute(), Charsets.UTF_8))
          .resolveFormattingOptions(baseOptions)

  private fun ResourceProperties.resolveFormattingOptions(
      baseOptions: FormattingOptions,
  ): FormattingOptions {
    // `max_line_length` may return null to indicate 'off', in which case we keep the base maxWidth
    val maxWidth =
        getValue(PropertyType.max_line_length, baseOptions.maxWidth, false) ?: baseOptions.maxWidth

    // `indent_size` may return null to indicate 'tab', in which case we defer to `tab_width`
    // `ij_kotlin_indent_size` takes priority over `indent_size` when present
    val blockIndent =
        getValue(ijKotlinIndentSize, null, false)
            ?: getValue(PropertyType.indent_size, baseOptions.blockIndent, false)
            ?: getValue(PropertyType.tab_width, baseOptions.blockIndent, false)

    val continuationIndent =
        properties[ijKotlinContinuationIndentSize.name]?.takeIf { it.isValid }?.getValueAs<Int>()
            ?: getValue(ijContinuationIndentSize, baseOptions.continuationIndent, false)

    val trailingCommaStrategy =
        getValue(commaManagementStrategy, baseOptions.trailingCommaManagementStrategy, false)

    val resolved =
        baseOptions.copy(
            maxWidth = maxWidth,
            blockIndent = blockIndent,
            continuationIndent = continuationIndent,
            trailingCommaManagementStrategy = trailingCommaStrategy,
        )
    return resolved
  }
}
