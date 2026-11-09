package dev.veyra.mixin;

import dev.veyra.shader.VeyraComputePipeline;
import net.vulkanmod.vulkan.texture.ImageUtil;
import org.lwjgl.vulkan.VkBufferImageCopy;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import static org.lwjgl.vulkan.VK10.*;

@Mixin(value = ImageUtil.class, remap = false)
abstract class LegacyImageUploadMixin {
    @Redirect(method = "copyBufferToImageCmd", at = @At(value = "INVOKE", target =
            "Lorg/lwjgl/vulkan/VK10;vkCmdCopyBufferToImage(Lorg/lwjgl/vulkan/VkCommandBuffer;JJILorg/lwjgl/vulkan/VkBufferImageCopy$Buffer;)V"))
    private static void veyra$orderOverlappingUploads(VkCommandBuffer commands, long buffer, long image,
                                                      int layout, VkBufferImageCopy.Buffer regions) {
        // Consecutive atlas uploads can overlap while the image remains in TRANSFER_DST layout.
        VeyraComputePipeline.memoryBarrier(commands, VK_PIPELINE_STAGE_TRANSFER_BIT, VK_PIPELINE_STAGE_TRANSFER_BIT,
                VK_ACCESS_TRANSFER_WRITE_BIT, VK_ACCESS_TRANSFER_WRITE_BIT);
        vkCmdCopyBufferToImage(commands, buffer, image, layout, regions);
    }
}
