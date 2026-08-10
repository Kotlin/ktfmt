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

@file:Suppress("IncorrectPackageName", "PackageDirectoryMismatch")

package org.jetbrains.ktfmt.util

import org.jetbrains.kotlin.psi.KtContextReceiverList
import org.jetbrains.kotlin.psi.KtDestructuringDeclarationEntry
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.stubs.elements.KtStubElementTypes

fun KtContextReceiverList.listToVisit(): List<KtElement> {
  return contextParameters().ifEmpty { contextReceivers() }
}

val CONTEXT_PARAMETER_LIST = KtStubElementTypes.CONTEXT_RECEIVER_LIST

/** No val/var keyword in destructuring statements until Kotlin 2.3. */
val KtDestructuringDeclarationEntry.ownValOrVarKeywordText: String?
  get() = null
