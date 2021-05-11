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

package moe.luke.shadow;

import android.graphics.Bitmap;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The chunk information for a nine patch.
 * <p>
 * This does not represent the bitmap, only the chunk info responsible for the padding and the
 * stretching areas.
 * <p>
 * Since android.graphics.drawable.NinePatchDrawable and android.graphics.NinePatch both deal with
 * the nine patch chunk as a byte[], this class is converted to and from byte[] through
 * serialization.
 * <p>
 * This is meant to be used with the NinePatch_Delegate in Layoutlib API 5+.
 */
final class NinePatchChunk {


    private static int[] getPixels(Bitmap img, int x, int y, int w, int h, int[] pixels) {
        if (w == 0 || h == 0) {
            return new int[0];
        }
        if (pixels == null) {
            pixels = new int[w * h];
        } else if (pixels.length < w * h) {
            throw new IllegalArgumentException("Pixels array must have a length >= w * h");
        }
        img.getPixels(pixels, 0, 0, x, y, w, h);
        return pixels;
    }

    /**
     * Finds the 9-patch patches and padding from a {@link Bitmap} image that contains
     * both the image content and the control outer lines.
     */
    public static byte[] findPatches(Bitmap image) {
        // the size of the actual image content
        int width = image.getWidth() - 2;
        int height = image.getHeight() - 2;

        int[] row = null;
        int[] column = null;

        // extract the patch line. Make sure to start at 1 and be only as long as the image content,
        // to not include the outer control line.
        row = getPixels(image, 1, 0, width, 1, row);
        column = getPixels(image, 0, 1, 1, height, column);

        boolean[] result = new boolean[1];
        Pair<List<Pair<Integer>>> left = getPatches(column, result);
        result = new boolean[1];
        Pair<List<Pair<Integer>>> top = getPatches(row, result);
        List<Rectangle> fixed = getRectangles(left.first, top.first);
        List<Rectangle> patches = getRectangles(left.second, top.second);

        List<Rectangle> horizontalPatches;
        List<Rectangle> verticalPatches;
        if (!fixed.isEmpty()) {
            horizontalPatches = getRectangles(left.first, top.second);
            verticalPatches = getRectangles(left.second, top.first);
        } else {
            if (!top.first.isEmpty()) {
                horizontalPatches = new ArrayList<>(0);
                verticalPatches = getVerticalRectangles(height, top.first);
            } else if (!left.first.isEmpty()) {
                horizontalPatches = getHorizontalRectangles(width, left.first);
                verticalPatches = new ArrayList<>(0);
            } else {
                horizontalPatches = verticalPatches = new ArrayList<>(0);
            }
        }

        // extract the padding line. Make sure to start at 1 and be only as long as the image
        // content, to not include the outer control line.
        row = getPixels(image, 1, height + 1, width, 1, row);
        column = getPixels(image, width + 1, 1, 1, height, column);

        Pair<List<Pair<Integer>>> bottom = getPatches(row, result);
        Pair<Integer> horizontalPadding = getPadding(bottom.first);

        Pair<List<Pair<Integer>>> right = getPatches(column, result);
        Pair<Integer> verticalPadding = getPadding(right.first);

        ArrayList<Rectangle> allRegions = new ArrayList<>();
        allRegions.addAll(horizontalPatches);
        allRegions.addAll(verticalPatches);
        allRegions.addAll(patches);
        allRegions.addAll(fixed);
        //noinspection ComparatorCombinators
        Collections.sort(allRegions, (o1, o2) -> {
            int y = Integer.compare(o1.y, o2.y);
            if (y != 0) {
                return y;
            }
            return Integer.compare(o1.x, o2.x);
        });
        int xDivsOffset = 32;
        int yDivsOffset = xDivsOffset + 4 * top.second.size() * 2;
        int colorsOffset = yDivsOffset + 4 * left.second.size() * 2;

        // Create the serialized form of the chunk, following format laid out in frameworks/base/libs/androidfw/ResourceTypes.[h|cpp]
        // The chunk is written using little endian format to match the Android framework.
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (LittleEndianDataOutputStream oos = new LittleEndianDataOutputStream(baos)) {
            oos.writeBoolean(true); // wasDeserialized (1 byte)
            oos.writeByte(top.second.size() * 2); // numXDivs (1 byte)
            oos.writeByte(left.second.size() * 2); // numYDivs 1(1 byte)
            oos.writeByte(allRegions.size()); // numColors (1 byte)
            oos.writeInt(xDivsOffset); // xDivsOffset (4 bytes)
            oos.writeInt(yDivsOffset); // yDivsOffset (4 bytes)
            oos.writeInt(horizontalPadding.first); // paddingLeft (4 bytes)
            oos.writeInt(horizontalPadding.second); // paddingRight (4 bytes)
            oos.writeInt(verticalPadding.first); // paddingTop (4 bytes)
            oos.writeInt(verticalPadding.second); // paddingBottom (4 bytes)
            oos.writeInt(colorsOffset); // colorsOffset (4 bytes)
            for (Pair<Integer> patch : top.second) { // xDivs
                oos.writeInt(patch.first); // left position (4 bytes)
                oos.writeInt(patch.second); // right position (4 bytes)
            }
            for (Pair<Integer> patch : left.second) {
                oos.writeInt(patch.first); // top position (4 bytes)
                oos.writeInt(patch.second); // bottom position (4 bytes)
            }
            for (Rectangle region : allRegions) { // colors
                oos.writeInt(getRegionColor(image, region)); // color (4 bytes)
            }
        } catch (IOException ignore) {
        }
        return baos.toByteArray();
    }

    private static List<Rectangle> getVerticalRectangles(
            int imageHeight,
            List<Pair<Integer>> topPairs
    ) {
        List<Rectangle> rectangles = new ArrayList<>();
        for (Pair<Integer> top : topPairs) {
            int x = top.first;
            int width = top.second - top.first;

            rectangles.add(new Rectangle(x, 0, width, imageHeight));
        }
        return rectangles;
    }

    private static List<Rectangle> getHorizontalRectangles(
            int imageWidth,
            List<Pair<Integer>> leftPairs
    ) {
        List<Rectangle> rectangles = new ArrayList<>();
        for (Pair<Integer> left : leftPairs) {
            int y = left.first;
            int height = left.second - left.first;

            rectangles.add(new Rectangle(0, y, imageWidth, height));
        }
        return rectangles;
    }

    private static Pair<Integer> getPadding(List<Pair<Integer>> pairs) {
        if (pairs.isEmpty()) {
            return new Pair<>(0, 0);
        } else if (pairs.size() == 1) {
            if (pairs.get(0).first == 0) {
                return new Pair<>(pairs.get(0).second - pairs.get(0).first, 0);
            } else {
                return new Pair<>(0, pairs.get(0).second - pairs.get(0).first);
            }
        } else {
            int index = pairs.size() - 1;
            return new Pair<>(pairs.get(0).second - pairs.get(0).first,
                    pairs.get(index).second - pairs.get(index).first);
        }
    }

    private static List<Rectangle> getRectangles(
            List<Pair<Integer>> leftPairs,
            List<Pair<Integer>> topPairs
    ) {
        List<Rectangle> rectangles = new ArrayList<>();
        for (Pair<Integer> left : leftPairs) {
            int y = left.first;
            int height = left.second - left.first;
            for (Pair<Integer> top : topPairs) {
                int x = top.first;
                int width = top.second - top.first;

                rectangles.add(new Rectangle(x, y, width, height));
            }
        }
        return rectangles;
    }

    /**
     * Computes a list of Patch based on a pixel line.
     * <p>
     * This returns both the fixed areas, and the patches (stretchable) areas.
     * <p>
     * The return value is a pair of list. The first list ({@link Pair#first}) is the list
     * of fixed area. The second list ({@link Pair#second}) is the list of stretchable areas.
     * <p>
     * Each area is defined as a Pair of (start, end) coordinate in the given line.
     *
     * @param pixels         the pixels of the control line. The line should have the same length as the
     *                       content (i.e. it should be stripped of the first/last control pixel which are not
     *                       used)
     * @param startWithPatch a boolean array of size 1 used to return the boolean value of whether
     *                       a patch (stretchable area) is first or not.
     * @return xxx
     */
    private static Pair<List<Pair<Integer>>> getPatches(int[] pixels, boolean[] startWithPatch) {
        int lastIndex = 0;
        int lastPixel = pixels[0];
        boolean first = true;

        List<Pair<Integer>> fixed = new ArrayList<>();
        List<Pair<Integer>> patches = new ArrayList<>();

        for (int i = 0; i < pixels.length; i++) {
            int pixel = pixels[i];
            if (pixel != lastPixel) {
                if (lastPixel == 0xFF000000) {
                    if (first) startWithPatch[0] = true;
                    patches.add(new Pair<>(lastIndex, i));
                } else {
                    fixed.add(new Pair<>(lastIndex, i));
                }
                first = false;

                lastIndex = i;
                lastPixel = pixel;
            }
        }
        if (lastPixel == 0xFF000000) {
            if (first) startWithPatch[0] = true;
            patches.add(new Pair<>(lastIndex, pixels.length));
        } else {
            fixed.add(new Pair<>(lastIndex, pixels.length));
        }

        if (patches.isEmpty()) {
            patches.add(new Pair<>(1, pixels.length));
            startWithPatch[0] = true;
            fixed.clear();
        }

        return new Pair<>(fixed, patches);
    }

    /**
     * This checks whether the entire region of the given image has the same color. If it does, this
     * returns that color, or 0 (corresponding to the enum value for
     * android::Res_png_9patch::TRANSPARENT_COLOR) if the region is completely transparent. If the
     * region is composed of several colors, this returns 1 (corresponding to
     * android::Res_png_9patch::NO_COLOR). This follows algorithm from AAPT2 (see
     * frameworks/base/tools/aapt2/compile/NinePatch.cpp)
     */
    private static int getRegionColor(Bitmap image, Rectangle region) {
        int expectedColor = image.getPixel(region.x, region.y);
        int expectedAlpha = (expectedColor >> 24) & 0xff;
        for (int y = region.y; y < region.y + region.height; y++) {
            for (int x = region.x + 1; x < region.x + region.width; x++) {
                int color = image.getPixel(x, y);
                int alpha = (color >> 24) & 0xff;
                if (alpha == 0 && expectedAlpha != 0) {
                    return 1; // android::Res_png_9patch::NO_COLOR
                } else if (alpha != 0 && color != expectedColor) {
                    return 1; // android::Res_png_9patch::NO_COLOR
                }
            }
        }
        if (expectedAlpha == 0) {
            return 0; // android::Res_png_9patch::TRANSPARENT_COLOR
        }
        return expectedColor;
    }

    /**
     * A pair of values.
     *
     * @param <E>
     */
    private static class Pair<E> extends android.util.Pair<E, E> {
        /**
         * Constructor for a Pair.
         *
         * @param first  the first object in the Pair
         * @param second the second object in the pair
         */
        public Pair(E first, E second) {
            super(first, second);
        }
    }

}
