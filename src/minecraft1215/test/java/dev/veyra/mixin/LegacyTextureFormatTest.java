package dev.veyra.mixin;

import com.mojang.blaze3d.textures.TextureFormat;
import org.junit.jupiter.api.Test;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.vulkan.VK10.*;

class LegacyTextureFormatTest {
    @Test void keepsDepthMetadataAndAllowsHdrWithoutAFalseRgba8Label() throws Exception {
        var method = LegacyTextureFormatMixin.class.getDeclaredMethod("veyra$describeNativeImage",
                int.class, CallbackInfoReturnable.class);
        method.setAccessible(true);
        for (int format : new int[]{VK_FORMAT_D32_SFLOAT, VK_FORMAT_D24_UNORM_S8_UINT}) {
            var callback = new CallbackInfoReturnable<TextureFormat>("textureFormat", true);
            method.invoke(null, format, callback);
            assertTrue(callback.isCancelled());
            assertEquals(TextureFormat.DEPTH32, callback.getReturnValue());
        }
        var hdr = new CallbackInfoReturnable<TextureFormat>("textureFormat", true);
        method.invoke(null, VK_FORMAT_R16G16B16A16_SFLOAT, hdr);
        assertTrue(hdr.isCancelled());
        assertNull(hdr.getReturnValue());
        var ordinary = new CallbackInfoReturnable<TextureFormat>("textureFormat", true);
        method.invoke(null, VK_FORMAT_R8G8B8A8_UNORM, ordinary);
        assertFalse(ordinary.isCancelled());
    }
}
