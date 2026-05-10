package com.easyclaims.map;

import com.hypixel.hytale.protocol.packets.worldmap.MapImage;

import javax.annotation.Nonnull;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Converts between raw RGBA pixel buffers and MapImage's indexed palette format.
 */
public final class MapImagePixels {
    private MapImagePixels() {
    }

    @Nonnull
    public static MapImage fromRgbaPixels(int width, int height, @Nonnull int[] pixels) {
        if (pixels.length != width * height) {
            throw new IllegalArgumentException("Pixel buffer length does not match dimensions.");
        }

        Map<Integer, Integer> paletteLookup = new LinkedHashMap<>();
        int[] indices = new int[pixels.length];

        for (int i = 0; i < pixels.length; i++) {
            Integer existing = paletteLookup.get(pixels[i]);
            if (existing == null) {
                existing = paletteLookup.size();
                paletteLookup.put(pixels[i], existing);
            }
            indices[i] = existing;
        }

        int[] palette = new int[paletteLookup.size()];
        for (Map.Entry<Integer, Integer> entry : paletteLookup.entrySet()) {
            palette[entry.getValue()] = entry.getKey();
        }

        int bitsPerIndex = Math.max(1, 32 - Integer.numberOfLeadingZeros(Math.max(1, palette.length) - 1));
        byte[] packedIndices = packIndices(indices, bitsPerIndex);

        return new MapImage(width, height, palette, (byte) bitsPerIndex, packedIndices);
    }

    @Nonnull
    public static int[] toRgbaPixels(@Nonnull MapImage image) {
        int pixelCount = image.width * image.height;
        int[] result = new int[pixelCount];

        if (image.palette == null || image.palette.length == 0 || image.packedIndices == null) {
            return result;
        }

        int bitsPerIndex = Byte.toUnsignedInt(image.bitsPerIndex);
        if (bitsPerIndex <= 0) {
            return result;
        }

        int[] indices = unpackIndices(image.packedIndices, bitsPerIndex, pixelCount);
        for (int i = 0; i < pixelCount; i++) {
            int paletteIndex = indices[i];
            if (paletteIndex >= 0 && paletteIndex < image.palette.length) {
                result[i] = image.palette[paletteIndex];
            }
        }

        return result;
    }

    @Nonnull
    private static byte[] packIndices(@Nonnull int[] indices, int bitsPerIndex) {
        int totalBits = indices.length * bitsPerIndex;
        byte[] output = new byte[(totalBits + 7) / 8];

        int bitOffset = 0;
        int mask = bitsPerIndex >= 31 ? -1 : (1 << bitsPerIndex) - 1;

        for (int index : indices) {
            int value = index & mask;
            for (int bit = 0; bit < bitsPerIndex; bit++) {
                if (((value >> bit) & 1) != 0) {
                    int writeBit = bitOffset + bit;
                    output[writeBit >>> 3] = (byte) (output[writeBit >>> 3] | (1 << (writeBit & 7)));
                }
            }
            bitOffset += bitsPerIndex;
        }

        return output;
    }

    @Nonnull
    private static int[] unpackIndices(@Nonnull byte[] packed, int bitsPerIndex, int count) {
        int[] result = new int[count];

        int bitOffset = 0;
        for (int i = 0; i < count; i++) {
            int value = 0;
            for (int bit = 0; bit < bitsPerIndex; bit++) {
                int readBit = bitOffset + bit;
                if (readBit >= packed.length * 8) {
                    return result;
                }
                int current = (packed[readBit >>> 3] >>> (readBit & 7)) & 1;
                value |= (current << bit);
            }
            result[i] = value;
            bitOffset += bitsPerIndex;
        }

        return result;
    }
}

