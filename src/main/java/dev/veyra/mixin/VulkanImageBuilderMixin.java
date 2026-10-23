package dev.veyra.mixin;

import net.vulkanmod.vulkan.texture.VulkanImage;
import org.lwjgl.vulkan.VK10;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = VulkanImage.Builder.class, remap = false)
abstract class VulkanImageBuilderMixin {
    @Shadow(remap = false)
    private static int formatSize(int format) {
        throw new AssertionError();
    }

    @Redirect(
            method = "createVulkanImage",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/vulkanmod/vulkan/texture/VulkanImage$Builder;formatSize(I)I"),
            remap = false)
    private static int veyra$supportRgba16Unorm(int format) {
        if (format == VK10.VK_FORMAT_R16G16B16A16_UNORM) {
            return 8;
        }
        return formatSize(format);
    }
}
