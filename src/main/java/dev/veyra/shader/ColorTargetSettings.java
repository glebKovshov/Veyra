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
            case VK10.VK_FORMAT_R8G8B8_UNORM -> "RGB8";
            case VK10.VK_FORMAT_R8G8B8_SNORM -> "RGB8_SNORM";
            case VK10.VK_FORMAT_R8G8B8A8_UNORM -> "RGBA8";
            case VK10.VK_FORMAT_R8G8B8A8_SNORM -> "RGBA8_SNORM";
            case VK10.VK_FORMAT_R16G16B16A16_UNORM -> "RGBA16";
            case VK10.VK_FORMAT_R16_SFLOAT -> "R16F";
            case VK10.VK_FORMAT_R16G16B16_SFLOAT -> "RGB16F";
            case VK10.VK_FORMAT_R16G16B16A16_SFLOAT -> "RGBA16F";
            case VK10.VK_FORMAT_B10G11R11_UFLOAT_PACK32 -> "R11F_G11F_B10F";
            case VK10.VK_FORMAT_R32_SFLOAT -> "R32F";
            default -> "Vulkan format " + format;
        };
    }

    static int formatSize(int format) {
        return switch (format) {
            case VK10.VK_FORMAT_R8_UNORM -> 1;
            case VK10.VK_FORMAT_R8G8B8_UNORM, VK10.VK_FORMAT_R8G8B8_SNORM -> 3;
            case VK10.VK_FORMAT_R8G8B8A8_UNORM,
                    VK10.VK_FORMAT_R8G8B8A8_SNORM,
                    VK10.VK_FORMAT_B10G11R11_UFLOAT_PACK32,
                    VK10.VK_FORMAT_R32_SFLOAT -> 4;
            case VK10.VK_FORMAT_R16G16B16A16_UNORM -> 8;
            case VK10.VK_FORMAT_R16_SFLOAT -> 2;
            case VK10.VK_FORMAT_R16G16B16_SFLOAT -> 6;
            case VK10.VK_FORMAT_R16G16B16A16_SFLOAT -> 8;
            default -> throw new IllegalArgumentException("Unsupported Vulkan color format " + format + ".");
        };
    }
}
