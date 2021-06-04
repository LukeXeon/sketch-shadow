/*
 * Copyright (C) 2010 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.gcta.shadow

import android.graphics.Bitmap
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.*

/**
 * The chunk information for a nine patch.
 *
 *
 * This does not represent the bitmap, only the chunk info responsible for the padding and the
 * stretching areas.
 *
 *
 * Since android.graphics.drawable.NinePatchDrawable and android.graphics.NinePatch both deal with
 * the nine patch chunk as a byte[], this class is converted to and from byte[] through
 * serialization.
 *
 *
 * This is meant to be used with the NinePatch_Delegate in Layoutlib API 5+.
 */
internal object NinePatchChunk {

    private fun getPixels(
        img: Bitmap,
        x: Int,
        y: Int,
        w: Int,
        h: Int,
        pixels: IntArray?
    ): IntArray {
        var pixels1 = pixels
        if (w == 0 || h == 0) {
            return IntArray(0)
        }
        if (pixels1 == null) {
            pixels1 = IntArray(w * h)
        } else {
            require(pixels1.size >= w * h) { "Pixels array must have a length >= w * h" }
        }
        img.getPixels(pixels1, 0, w, x, y, w, h)
        return pixels1
    }

    /**
     * Finds the 9-patch patches and padding from a [Bitmap] image that contains
     * both the image content and the control outer lines.
     */
    fun findPatches(image: Bitmap): ByteArray {
        // the size of the actual image content
        val width = image.width - 2
        val height = image.height - 2
        var row: IntArray? = null
        var column: IntArray? = null

        // extract the patch line. Make sure to start at 1 and be only as long as the image content,
        // to not include the outer control line.
        row = getPixels(image, 1, 0, width, 1, row)
        column = getPixels(image, 0, 1, 1, height, column)
        var result = BooleanArray(1)
        val left = getPatches(column, result)
        result = BooleanArray(1)
        val top = getPatches(row, result)
        val fixed = getRectangles(left.first, top.first)
        val patches = getRectangles(left.second, top.second)
        val horizontalPatches: List<Rectangle>
        val verticalPatches: List<Rectangle>
        if (fixed.isNotEmpty()) {
            horizontalPatches = getRectangles(left.first, top.second)
            verticalPatches = getRectangles(left.second, top.first)
        } else {
            when {
                top.first.isNotEmpty() -> {
                    horizontalPatches = ArrayList(0)
                    verticalPatches = getVerticalRectangles(height, top.first)
                }
                left.first.isNotEmpty() -> {
                    horizontalPatches = getHorizontalRectangles(width, left.first)
                    verticalPatches = ArrayList(0)
                }
                else -> {
                    verticalPatches = ArrayList(0)
                    horizontalPatches = verticalPatches
                }
            }
        }

        // extract the padding line. Make sure to start at 1 and be only as long as the image
        // content, to not include the outer control line.
        row = getPixels(image, 1, height + 1, width, 1, row)
        column = getPixels(image, width + 1, 1, 1, height, column)
        val bottom = getPatches(row, result)
        val horizontalPadding = getPadding(bottom.first)
        val right = getPatches(column, result)
        val verticalPadding = getPadding(right.first)
        val allRegions = ArrayList<Rectangle>()
        allRegions.addAll(horizontalPatches)
        allRegions.addAll(verticalPatches)
        allRegions.addAll(patches)
        allRegions.addAll(fixed)
        allRegions.sortWith { o1: Rectangle, o2: Rectangle ->
            val y = o1.y.compareTo(o2.y)
            if (y != 0) {
                return@sortWith y
            }
            o1.x.compareTo(o2.x)
        }
        val xDivsOffset = 32
        val yDivsOffset = xDivsOffset + 4 * top.second.size * 2
        val colorsOffset = yDivsOffset + 4 * left.second.size * 2

        // Create the serialized form of the chunk, following format laid out in frameworks/base/libs/androidfw/ResourceTypes.[h|cpp]
        // The chunk is written using little endian format to match the Android framework.
        val baos = ByteArrayOutputStream()
        try {
            LEDataOutputStream(baos).use { oos ->
                oos.writeBoolean(true) // wasDeserialized (1 byte)
                oos.writeByte(top.second.size * 2) // numXDivs (1 byte)
                oos.writeByte(left.second.size * 2) // numYDivs 1(1 byte)
                oos.writeByte(allRegions.size) // numColors (1 byte)
                oos.writeInt(xDivsOffset) // xDivsOffset (4 bytes)
                oos.writeInt(yDivsOffset) // yDivsOffset (4 bytes)
                oos.writeInt(horizontalPadding.first) // paddingLeft (4 bytes)
                oos.writeInt(horizontalPadding.second) // paddingRight (4 bytes)
                oos.writeInt(verticalPadding.first) // paddingTop (4 bytes)
                oos.writeInt(verticalPadding.second) // paddingBottom (4 bytes)
                oos.writeInt(colorsOffset) // colorsOffset (4 bytes)
                for (patch in top.second) { // xDivs
                    oos.writeInt(patch.first) // left position (4 bytes)
                    oos.writeInt(patch.second) // right position (4 bytes)
                }
                for (patch in left.second) {
                    oos.writeInt(patch.first) // top position (4 bytes)
                    oos.writeInt(patch.second) // bottom position (4 bytes)
                }
                for (region in allRegions) { // colors
                    oos.writeInt(getRegionColor(image, region)) // color (4 bytes)
                }
            }
        } catch (ignore: IOException) {
        }
        return baos.toByteArray()
    }

    private fun getVerticalRectangles(
        imageHeight: Int,
        topPairs: List<IntPair>
    ): List<Rectangle> {
        val rectangles = ArrayList<Rectangle>()
        for (top in topPairs) {
            val x = top.first
            val width = top.second - top.first
            rectangles.add(Rectangle(x, 0, width, imageHeight))
        }
        return rectangles
    }

    private fun getHorizontalRectangles(
        imageWidth: Int,
        leftPairs: List<IntPair>
    ): List<Rectangle> {
        val rectangles = ArrayList<Rectangle>()
        for (left in leftPairs) {
            val y = left.first
            val height = left.second - left.first
            rectangles.add(Rectangle(0, y, imageWidth, height))
        }
        return rectangles
    }

    private fun getPadding(pairs: List<IntPair>): IntPair {
        return if (pairs.isEmpty()) {
            IntPair(0, 0)
        } else if (pairs.size == 1) {
            if (pairs[0].first == 0) {
                IntPair(pairs[0].second - pairs[0].first, 0)
            } else {
                IntPair(0, pairs[0].second - pairs[0].first)
            }
        } else {
            val index = pairs.size - 1
            IntPair(
                pairs[0].second - pairs[0].first,
                pairs[index].second - pairs[index].first
            )
        }
    }

    private fun getRectangles(
        leftPairs: List<IntPair>,
        topPairs: List<IntPair>
    ): List<Rectangle> {
        val rectangles = ArrayList<Rectangle>()
        for (left in leftPairs) {
            val y = left.first
            val height = left.second - left.first
            for (top in topPairs) {
                val x = top.first
                val width = top.second - top.first
                rectangles.add(Rectangle(x, y, width, height))
            }
        }
        return rectangles
    }

    /**
     * Computes a list of Patch based on a pixel line.
     *
     *
     * This returns both the fixed areas, and the patches (stretchable) areas.
     *
     *
     * The return value is a pair of list. The first list ([IntPair.first]) is the list
     * of fixed area. The second list ([IntPair.second]) is the list of stretchable areas.
     *
     *
     * Each area is defined as a Pair of (start, end) coordinate in the given line.
     *
     * @param pixels         the pixels of the control line. The line should have the same length as the
     * content (i.e. it should be stripped of the first/last control pixel which are not
     * used)
     * @param startWithPatch a boolean array of size 1 used to return the boolean value of whether
     * a patch (stretchable area) is first or not.
     * @return xxx
     */
    private fun getPatches(
        pixels: IntArray,
        startWithPatch: BooleanArray
    ): Pair<List<IntPair>> {
        var lastIndex = 0
        var lastPixel = pixels[0]
        var first = true
        val fixed = ArrayList<IntPair>()
        val patches = ArrayList<IntPair>()
        for (i in pixels.indices) {
            val pixel = pixels[i]
            if (pixel != lastPixel) {
                if (lastPixel == -0x1000000) {
                    if (first) startWithPatch[0] = true
                    patches.add(IntPair(lastIndex, i))
                } else {
                    fixed.add(IntPair(lastIndex, i))
                }
                first = false
                lastIndex = i
                lastPixel = pixel
            }
        }
        if (lastPixel == -0x1000000) {
            if (first) startWithPatch[0] = true
            patches.add(IntPair(lastIndex, pixels.size))
        } else {
            fixed.add(IntPair(lastIndex, pixels.size))
        }
        if (patches.isEmpty()) {
            patches.add(IntPair(1, pixels.size))
            startWithPatch[0] = true
            fixed.clear()
        }
        return Pair(fixed, patches)
    }

    /**
     * This checks whether the entire region of the given image has the same color. If it does, this
     * returns that color, or 0 (corresponding to the enum value for
     * android::Res_png_9patch::TRANSPARENT_COLOR) if the region is completely transparent. If the
     * region is composed of several colors, this returns 1 (corresponding to
     * android::Res_png_9patch::NO_COLOR). This follows algorithm from AAPT2 (see
     * frameworks/base/tools/aapt2/compile/NinePatch.cpp)
     */
    private fun getRegionColor(image: Bitmap, region: Rectangle): Int {
        val expectedColor = image.getPixel(region.x, region.y)
        val expectedAlpha = expectedColor shr 24 and 0xff
        for (y in region.y until region.y + region.height) {
            for (x in region.x + 1 until region.x + region.width) {
                val color = image.getPixel(x, y)
                val alpha = color shr 24 and 0xff
                if (alpha == 0 && expectedAlpha != 0) {
                    return 1 // android::Res_png_9patch::NO_COLOR
                } else if (alpha != 0 && color != expectedColor) {
                    return 1 // android::Res_png_9patch::NO_COLOR
                }
            }
        }
        return if (expectedAlpha == 0) {
            0 // android::Res_png_9patch::TRANSPARENT_COLOR
        } else expectedColor
    }
}