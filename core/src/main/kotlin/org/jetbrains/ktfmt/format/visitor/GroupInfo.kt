package org.jetbrains.ktfmt.format.visitor

import org.jetbrains.kotlin.psi.KtArrayAccessExpression
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtPostfixExpression
import org.jetbrains.kotlin.psi.KtQualifiedExpression
import org.jetbrains.kotlin.psi.KtSimpleNameExpression
import org.jetbrains.kotlin.psi.KtSuperExpression
import org.jetbrains.kotlin.psi.KtThisExpression

internal data class GroupInfo(
    val expression: KtExpression,
    var openingGroups: Int = 0,
    var closingGroups: Int = 0,
    var isTrailingLambda: Boolean = false,
    var isLast: Boolean = false,
)

internal fun KtExpression.computeGroups(continuationIndent: Indentation.Const): List<GroupInfo> {
  val parts = this.chainParts
  // whether we want to make a lambda look like a block, this make Kotlin DSLs look as expected
  val hasTrailingLambda = parts.last().isLambda && parts.count { it.isLambda } == 1
  return computeGroupingInfo(parts, hasTrailingLambda, continuationIndent)
}

/**
 * Generates the [GroupInfo] array to go with an array of [KtQualifiedExpression] parts
 *
 * For example, the expression `a.b[2].c.d()` is made of four expressions:
 * 1. [KtQualifiedExpression] `a.b[2].c . d()` (this will be `parts[4]`)
 * 1. [KtQualifiedExpression] `a.b[2] . c` (this will be `parts[3]`)
 * 2. [KtArrayAccessExpression] `a.b [2]` (this will be `parts[2]`)
 * 3. [KtQualifiedExpression] `a . b` (this will be `parts[1]`)
 * 4. [KtSimpleNameExpression] `a` (this will be `parts[0]`)
 *
 * Once in parts, these are in the reverse order. To render the array access correctly we need to
 * make sure `b` and `[2]` are in a group so we avoid splitting them. To do so we need to open a
 * group for `b`, and always close a group for an array.
 *
 * Here is the same expression, with justified braces marking the groupings it will get:
 * ```
 *  a . b [2] . c . d ()
 * {a . b} --> Grouping `a.b` because it can be a package name or simple field access so we add 1
 *             to the number of groups to open at groupingInfos[0], and mark to close a group at
 *             groupingInfos[1]
 * {a . b [2]} --> Grouping `a.b` with `[2]`, since otherwise we may break inside the brackets
 *                 instead of preferring breaks before dots. So we open a group at [0], but since
 *                 we always close a group after brackets, we don't store that information.
 * ```
 *
 * The final expression with groupings:
 * ```
 * {{a.b}[2]}.c.d()
 * ```
 */
internal fun computeGroupingInfo(
    parts: List<KtExpression>,
    hasTrailingLambda: Boolean,
    continuationIndent: Indentation.Const,
): List<GroupInfo> {
  val groupingInfos = List(parts.size) { GroupInfo(parts[it]) }
  groupingInfos.lastOrNull()?.let {
    it.isTrailingLambda = hasTrailingLambda && it.expression.isLambda
    it.isLast = true
  }

  fun group(from: Int, to: Int) {
    groupingInfos[from].openingGroups++
    groupingInfos[to].closingGroups++
  }

  var inPrefix = true
  var lastAnchor = 0
  for ((index, part) in parts.withIndex()) {
    when (part) {
      is KtQualifiedExpression -> {
        if (inPrefix && part.shouldGroupWithPrevious(index, parts.lastIndex, continuationIndent)) {
          // all parts of the prefix are grouped together
          group(0, index)
        } else {
          inPrefix = false
          // future arrays and postfixes will be anchored to this part
          lastAnchor = index
        }
      }
      is KtArrayAccessExpression,
      is KtPostfixExpression -> {
        group(lastAnchor, index)
      }
    }
  }
  if (hasTrailingLambda) {
    group(0, groupingInfos.lastIndex)
  }
  return groupingInfos
}

/** Decide whether a [KtQualifiedExpression] part should be grouped with the previous part */
internal fun KtQualifiedExpression.shouldGroupWithPrevious(
    currentIndex: Int,
    lastIndex: Int,
    continuationIndent: Indentation.Const,
): Boolean {
  val previous =
      (receiverExpression as? KtQualifiedExpression)?.selectorExpression ?: receiverExpression
  val current = checkNotNull(selectorExpression)

  return when {
    // this is the second, and the first is short, avoid hanging `.`
    currentIndex == 1 && previous.text.length < continuationIndent.value -> true
    // the previous part is `this` or `super`
    previous is KtSuperExpression || previous is KtThisExpression -> true
    // this is `b` or `C` in `a.b.C`, so everything before it is a package name
    current is KtSimpleNameExpression && this is KtDotQualifiedExpression ->
        previous is KtSimpleNameExpression || current.startsWithUpperCase()
    // this is an invocation that either comes directly after type name OR is last in chain
    current is KtCallExpression && previous !is KtCallExpression ->
        previous.startsWithUpperCase() || currentIndex == lastIndex
    else -> false
  }
}
