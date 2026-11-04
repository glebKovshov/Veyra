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
    private static int veyra$supportPackColorFormats(int format) {
        // Bytes per texel of Iris colortex formats that VulkanMod 0.6.8's table omits (Photon uses RG16F).
        return switch (format) {
            case VK10.VK_FORMAT_R8_UNORM, VK10.VK_FORMAT_R8_SNORM, VK10.VK_FORMAT_R8_UINT, VK10.VK_FORMAT_R8_SINT -> 1;
            case VK10.VK_FORMAT_R8G8_UNORM, VK10.VK_FORMAT_R8G8_SNORM, VK10.VK_FORMAT_R16_UNORM,
                    VK10.VK_FORMAT_R16_SNORM, VK10.VK_FORMAT_R16_SFLOAT, VK10.VK_FORMAT_R16_UINT,
                    VK10.VK_FORMAT_R16_SINT -> 2;
            case VK10.VK_FORMAT_R16G16_UNORM, VK10.VK_FORMAT_R16G16_SNORM, VK10.VK_FORMAT_R16G16_SFLOAT,
                    VK10.VK_FORMAT_R16G16_UINT, VK10.VK_FORMAT_R16G16_SINT, VK10.VK_FORMAT_R32_SFLOAT,
                    VK10.VK_FORMAT_R32_UINT, VK10.VK_FORMAT_R32_SINT, VK10.VK_FORMAT_A2B10G10R10_UNORM_PACK32,
                    VK10.VK_FORMAT_B10G11R11_UFLOAT_PACK32 -> 4;
            case VK10.VK_FORMAT_R16G16B16A16_UNORM, VK10.VK_FORMAT_R16G16B16A16_SNORM,
                    VK10.VK_FORMAT_R32G32_SFLOAT, VK10.VK_FORMAT_R32G32_UINT, VK10.VK_FORMAT_R32G32_SINT -> 8;
            case VK10.VK_FORMAT_R32G32B32A32_SFLOAT, VK10.VK_FORMAT_R32G32B32A32_UINT,
                    VK10.VK_FORMAT_R32G32B32A32_SINT -> 16;
            default -> formatSize(format);
        };
    }
}
