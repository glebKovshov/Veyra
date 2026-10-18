package dev.veyra.shader;

import org.lwjgl.vulkan.VK10;

/** Clear and storage settings for one Iris-compatible colortex buffer. */
record ColorTargetSettings(boolean clearEachFrame, int clearColor, int width, int height, int format) {
    static ColorTargetSettings defaults(int index, int width, int height) {
        return new ColorTargetSettings(true, index == 1 ? 0xFFFFFFFF : 0x00000000,
                width, height, VK10.VK_FORMAT_R8G8B8A8_UNORM);
    }

    static String formatName(int format) {
        return switch (format) {
            case VK10.VK_FORMAT_R8_UNORM -> "R8";
            case VK10.VK_FORMAT_R8G8B8A8_UNORM -> "RGBA8";
            case VK10.VK_FORMAT_R16_SFLOAT -> "R16F";
            case VK10.VK_FORMAT_R16G16B16A16_SFLOAT -> "RGBA16F";
            case VK10.VK_FORMAT_R32_SFLOAT -> "R32F";
            default -> "Vulkan format " + format;
        };
    }

    static int formatSize(int format) {
        return switch (format) {
            case VK10.VK_FORMAT_R8_UNORM -> 1;
            case VK10.VK_FORMAT_R8G8B8A8_UNORM,
                    VK10.VK_FORMAT_R32_SFLOAT -> 4;
            case VK10.VK_FORMAT_R16_SFLOAT -> 2;
            case VK10.VK_FORMAT_R16G16B16A16_SFLOAT -> 8;
            default -> throw new IllegalArgumentException("Unsupported Vulkan color format " + format + ".");
        };
    }
}
