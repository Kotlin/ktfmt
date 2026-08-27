package org.jetbrains.ktfmt.util

import com.google.common.collect.RangeSet
import com.google.common.collect.TreeRangeSet

operator fun <T : Comparable<T>> RangeSet<T>.plus(other: RangeSet<T>): RangeSet<T> {
    val result = TreeRangeSet.create<T>()
    result.addAll(this)
    result.addAll(other)
    return result
}
