package org.gcta.shadow

import android.graphics.Rect
import android.util.Pair
import com.google.gson.Gson
import java.io.ObjectInput

internal typealias Rectangle = Rect

internal typealias IntPair = IntArray

internal typealias Pair<T> = Pair<T, T>

@Suppress("FunctionName", "NOTHING_TO_INLINE")
internal inline fun IntPair(a: Int, b: Int): IntPair {
    return intArrayOf(a, b)
}

internal fun ObjectInput.readSafely(buffer: ByteArray): Int {
    val length = buffer.size
    var count = 0
    while (count != length) {
        val r = read(buffer, count, length - count)
        if (r == -1) {
            return count
        } else {
            count += r
        }
    }
    return length
}

/**
 * Returns a big-endian representation of `value` in an 8-element byte array; equivalent to
 * `ByteBuffer.allocate(8).putLong(value).array()`. For example, the input value `0x1213141516171819L` would yield the byte array `{0x12, 0x13, 0x14, 0x15, 0x16, 0x17,
 * 0x18, 0x19}`.
 */
internal fun toByteArray(value: Long): ByteArray {
    // Note that this code needs to stay compatible with GWT, which has known
    // bugs when narrowing byte casts of long values occur.
    var value1 = value
    val result = ByteArray(8)
    for (i in 7 downTo 0) {
        result[i] = (value1 and 0xffL).toByte()
        value1 = value1 shr 8
    }
    return result
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