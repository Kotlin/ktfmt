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
) {
  var groupOpenCount: Int = 0
  var shouldCloseGroup: Boolean = false
  var isLast: Boolean = false

  operator fun component2() = groupOpenCount

  operator fun component3() = shouldCloseGroup

  operator fun component4() = isLast
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
 * Once in parts, these are in the reverse order. To render the array correct we need to make sure
 * `b` and [2] are in a group so we avoid splitting them. To do so we need to open a group for `b`
 * (that will be done in part 2), and always close a group for an array.
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
 *             {c . d} --> another group to attach the first function name to the fields before it
 *                         this time we don't start the group in the beginning, and use
 *                         lastIndexToOpen to track the spot after the last time we stopped
 *                         grouping.
 * ```
 *
 * The final expression with groupings:
 * ```
 * {{a.b}[2]}.{c.d}()
 * ```
 */
internal fun computeGroupingInfo(
    parts: List<KtExpression>,
    hasTrailingLambda: Boolean,
    continuationIndent: Indentation.Const,
): List<GroupInfo> {
  val groupingInfos = List(parts.size) { GroupInfo(parts[it]) }
  groupingInfos.lastOrNull()?.let { it.isLast = true }

  var inPrefix = true
  var lastIndexToOpen = 0
  for ((index, part) in parts.withIndex()) {
    when (part) {
      is KtQualifiedExpression -> {
        if (
            lastIndexToOpen == 0 &&
                part.shouldGroupWithPrevious(index, parts.lastIndex, continuationIndent)
        ) {
          // this and the previous items should be grouped for better style
          // we add another group to open in index 0
          groupingInfos[0].groupOpenCount++
          // we don't always close a group when emitting this node, so we need this flag to
          // mark if we need to close a group
          groupingInfos[index].shouldCloseGroup = true
        } else {
          // use this index in to open future groups for arrays and postfixes
          // we will also stop grouping field access to the beginning of the expression
          lastIndexToOpen = index
        }
      }
      is KtArrayAccessExpression,
      is KtPostfixExpression -> {
        // we group these with the last item with a name, and we always close them
        groupingInfos[lastIndexToOpen].groupOpenCount++
      }
    }
  }
  if (hasTrailingLambda) {
    // a trailing lambda adds a group that we stop before emitting the lambda
    groupingInfos[0].groupOpenCount++
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
