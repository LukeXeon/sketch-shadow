package org.gcta.shadow

import android.graphics.Rect
import android.util.Pair
import com.google.gson.Gson

internal typealias Rectangle = Rect

internal typealias IntPair = IntArray

internal typealias Pair<T> = Pair<T, T>

@Suppress("FunctionName", "NOTHING_TO_INLINE")
internal inline fun IntPair(a: Int, b: Int): IntPair {
    return intArrayOf(a, b)
}

internal inline val IntPair.first: Int
    get() = this[0]

internal inline val IntPair.second: Int
    get() = this[1]

internal inline val Rectangle.x: Int
    get() = left

internal inline val Rectangle.y: Int
    get() = top

internal inline val Rectangle.width: Int
    get() = right

internal inline val Rectangle.height: Int
    get() = bottom

internal val gson by lazy { Gson() }