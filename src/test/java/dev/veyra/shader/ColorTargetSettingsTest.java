package dev.veyra.shader;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.vulkan.VK10.*;

class ColorTargetSettingsTest {
    @Test
    void rgbStorageFallbackPreservesOpaqueAlphaAndTargetSettings() {
        for (int rgb : new int[]{VK_FORMAT_R8G8B8_UNORM, VK_FORMAT_R8G8B8_SNORM,
                VK_FORMAT_R16G16B16_SFLOAT, VK_FORMAT_B10G11R11_UFLOAT_PACK32}) {
            var fallback = new ColorTargetSettings(false, 0x12345678, 640, 360, rgb)
                    .withStorageFormat(VK_FORMAT_R16G16B16A16_SFLOAT);
            assertTrue(fallback.opaqueAlpha());
            assertEquals(VK_FORMAT_R16G16B16A16_SFLOAT, fallback.format());
            assertFalse(fallback.clearEachFrame());
            assertEquals(0x12345678, fallback.clearColor());
            assertEquals(640, fallback.width());
            assertEquals(360, fallback.height());
            assertTrue(fallback.withStorageFormat(VK_FORMAT_R8G8B8A8_UNORM).opaqueAlpha());
        }
    }

    @Test
    void rgbaStorageFallbackRetainsItsAlphaChannel() {
        for (int rgba : new int[]{VK_FORMAT_R8G8B8A8_SNORM, VK_FORMAT_A2B10G10R10_UNORM_PACK32}) {
            assertFalse(new ColorTargetSettings(true, 0, 640, 360, rgba)
                    .withStorageFormat(VK_FORMAT_R16G16B16A16_SFLOAT).opaqueAlpha());
        }
    }
}
