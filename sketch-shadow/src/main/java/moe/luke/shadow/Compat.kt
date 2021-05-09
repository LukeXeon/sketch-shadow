@file:JvmName("Compat")

package moe.luke.shadow

import android.os.Build
import android.view.View
import android.view.ViewGroup
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder

private val FLAG_CLIP_TO_PADDING by lazy {
    ViewGroup::class.java.getDeclaredField("FLAG_CLIP_TO_PADDING")
        .apply {
            isAccessible = true
        }.getInt(null)
}

private val mGroupFlagsField by lazy {
    ViewGroup::class.java.getDeclaredField("mGroupFlags")
        .apply {
            isAccessible = true
        }
}

private val ViewGroup.mGroupFlags: Int
    get() {
        return mGroupFlagsField.getInt(this)
    }

private fun ViewGroup.hasBooleanFlag(flag: Int): Boolean {
    return mGroupFlags and flag == flag
}

internal var View.clipToOutlineCompat: Boolean
    get() {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            this.clipToOutline
        } else {
            false
        }
    }
    set(value) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            this.clipToOutline = value
        }
    }

internal var ViewGroup.clipToPaddingCompat: Boolean
    get() {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            this.clipToPadding
        } else {
            return hasBooleanFlag(FLAG_CLIP_TO_PADDING)
        }
    }
    set(value) {
        this.clipToPadding = value
    }

/**
 * PNG Chunk struct
 * [The Metadata in PNG files](http://dev.exiv2.org/projects/exiv2/wiki/The_Metadata_in_PNG_files)
 *
 * +--------+---------+
 * | Length | 4 bytes |
 * +--------+---------+
 * | Chunk  | 4 bytes |
 * |  type  |         |
 * +--------+---------+
 * | Chunk  | Length  |
 * |  data  |  bytes  |
 * +--------+---------+
 * | CRC    | 4 bytes |
 * +--------+---------+
 *
 * @param input
 * @return chunk
 * @throws IOException
 */
internal fun loadNinePatchChunk(input: ByteArray): ByteArray? {
    val reader = ByteBuffer.wrap(input).order(ByteOrder.BIG_ENDIAN)
    // check PNG signature
    // A PNG always starts with an 8-byte signature: 137 80 78 71 13 10 26 10 (decimal values).
    if (reader.int != -0x76afb1b9 || reader.int != 0x0D0A1A0A) {
        return null
    }
    while (true) {
        val length = reader.int
        val type = reader.int
        // check for nine patch chunk type (npTc)
        if (type != 0x6E705463) {
            reader.position(reader.position() + length + 4 /*crc*/)
            continue
        }
        return ByteArray(length).apply {
            reader.get(this)
        }
    }
}