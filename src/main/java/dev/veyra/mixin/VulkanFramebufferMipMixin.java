package dev.veyra.mixin;

import dev.veyra.vulkan.VulkanMipExtent;
import net.vulkanmod.vulkan.framebuffer.Framebuffer;
import net.vulkanmod.vulkan.texture.VulkanImage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = Framebuffer.class, remap = false)
abstract class VulkanFramebufferMipMixin {
    @Shadow protected int width;
    @Shadow protected int height;
    @Shadow public abstract VulkanImage getColorAttachment();

    @Inject(method = "setLevel", at = @At("TAIL"), remap = false)
    private void veyra$updateMipExtent(int level, CallbackInfo ci) {
        VulkanImage image = getColorAttachment();
        width = VulkanMipExtent.atLevel(image.width, level);
        height = VulkanMipExtent.atLevel(image.height, level);
    }
}
