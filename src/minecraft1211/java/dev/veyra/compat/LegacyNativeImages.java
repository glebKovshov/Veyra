package dev.veyra.compat;

import com.mojang.blaze3d.platform.NativeImage;

/** Minecraft 1.21.1 images store ABGR; later versions read and write ARGB. */
public final class LegacyNativeImages {
    private LegacyNativeImages() { }

    public static int getPixel(NativeImage image, int x, int y) { return swap(image.getPixelRGBA(x, y)); }

    public static void setPixel(NativeImage image, int x, int y, int argb) { image.setPixelRGBA(x, y, swap(argb)); }

    public static int[] getPixels(NativeImage image) {
        int[] pixels = image.getPixelsRGBA();
        for (int index = 0; index < pixels.length; index++) pixels[index] = swap(pixels[index]);
        return pixels;
    }

    // ARGB <-> ABGR: exchange the red and blue channels.
    private static int swap(int color) {
        return color & 0xFF00FF00 | (color & 0xFF) << 16 | color >> 16 & 0xFF;
    }
}
