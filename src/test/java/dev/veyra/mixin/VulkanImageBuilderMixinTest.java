package dev.veyra.mixin;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.lwjgl.vulkan.VK10.*;

class VulkanImageBuilderMixinTest {
    @Test
    void sizesExtendedNativeColorFormats() throws Exception {
        var method = VulkanImageBuilderMixin.class.getDeclaredMethod("veyra$supportPackColorFormats", int.class);
        method.setAccessible(true);
        assertEquals(4, method.invoke(null, VK_FORMAT_B10G11R11_UFLOAT_PACK32));
        assertEquals(4, method.invoke(null, VK_FORMAT_A2B10G10R10_UNORM_PACK32));
        assertEquals(8, method.invoke(null, VK_FORMAT_R16G16B16A16_UNORM));
    }
}
