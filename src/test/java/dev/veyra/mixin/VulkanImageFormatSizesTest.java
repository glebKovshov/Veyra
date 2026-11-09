package dev.veyra.mixin;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.lwjgl.vulkan.VK10.*;

class VulkanImageFormatSizesTest {
    @Test void supportsHdrAndPackedColorSizesWithoutNativeFallback() throws Exception {
        var method = VulkanImageBuilderMixin.class.getDeclaredMethod("veyra$supportPackColorFormats", int.class);
        method.setAccessible(true);
        int[] formats = {VK_FORMAT_R8_UNORM, VK_FORMAT_R16_SFLOAT, VK_FORMAT_R8G8B8_UNORM,
                VK_FORMAT_R8G8B8A8_SNORM, VK_FORMAT_R16G16B16_SFLOAT,
                VK_FORMAT_R16G16B16A16_SFLOAT, VK_FORMAT_R16G16B16A16_UINT, VK_FORMAT_R32G32B32A32_SFLOAT};
        int[] bytes = {1, 2, 3, 4, 6, 8, 8, 16};
        for (int index = 0; index < formats.length; index++)
            assertEquals(bytes[index], method.invoke(null, formats[index]), "Vulkan format " + formats[index]);
    }
}
