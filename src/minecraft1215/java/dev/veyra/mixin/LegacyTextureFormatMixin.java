package dev.veyra.mixin;

import com.mojang.blaze3d.textures.TextureFormat;
import net.vulkanmod.render.engine.VkGpuTexture;
import net.vulkanmod.vulkan.texture.VulkanImage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import static org.lwjgl.vulkan.VK10.*;

@Mixin(value = VkGpuTexture.class, remap = false)
public abstract class LegacyTextureFormatMixin {
    @Inject(method = "textureFormat", at = @At("HEAD"), cancellable = true)
    private static void veyra$describeNativeImage(int format, CallbackInfoReturnable<TextureFormat> callback) {
        if (VulkanImage.isDepthFormat(format)) callback.setReturnValue(TextureFormat.DEPTH32);
        else if (format == VK_FORMAT_R8G8B8A8_SRGB) callback.setReturnValue(TextureFormat.RGBA8);
        // Like newer VulkanMod, HDR images keep their physical format without a Minecraft enum.
        else if (format != VK_FORMAT_R8_UNORM && format != VK_FORMAT_R8G8B8A8_UNORM && format != VK_FORMAT_B8G8R8A8_UNORM)
            callback.setReturnValue(null);
    }
}
