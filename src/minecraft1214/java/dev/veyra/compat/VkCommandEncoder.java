package dev.veyra.compat;

import com.mojang.blaze3d.platform.NativeImage;
import dev.veyra.mixin.accessor.NativeImagePixelsAccessor;
import net.vulkanmod.vulkan.Renderer;
import net.vulkanmod.vulkan.texture.VTextureSelector;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkClearDepthStencilValue;
import org.lwjgl.vulkan.VkImageSubresourceRange;

public final class VkCommandEncoder implements CommandEncoder {
    VkCommandEncoder() { }

    @Override public void writeToTexture(GpuTexture texture, NativeImage image) {
        int width = texture.getWidth(0);
        int height = texture.getHeight(0);
        if (image.getWidth() != width || image.getHeight() != height) {
            throw new IllegalArgumentException("Cannot replace texture of size " + width + "x" + height
                    + " with image of size " + image.getWidth() + "x" + image.getHeight());
        }
        if (texture.isClosed()) throw new IllegalStateException("Destination texture is closed");
        long pixels = ((NativeImagePixelsAccessor) (Object) image).veyra$getPixels();
        VTextureSelector.setActiveTexture(0);
        VTextureSelector.bindTexture(((VkGpuTexture) texture).getVulkanImage());
        VTextureSelector.uploadSubTexture(0, width, height, 0, 0, 0, 0, image.getWidth(),
                MemoryUtil.memByteBuffer(pixels, width * height * image.format().components()));
    }

    /** Clears {@code depth} itself, outside any render pass, like the later encoder's texture clear. */
    @Override public void clearDepthTexture(GpuTexture depth, double value) {
        if (depth.isClosed()) throw new IllegalStateException("Depth texture is closed");
        var image = ((VkGpuTexture) depth).getVulkanImage();
        Renderer renderer = Renderer.getInstance();
        renderer.endRenderPass();
        try (MemoryStack stack = MemoryStack.stackPush()) {
            var commands = Renderer.getCommandBuffer();
            image.transitionImageLayout(stack, commands, VK10.VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL);
            var clear = VkClearDepthStencilValue.calloc(stack).depth((float) value);
            var range = VkImageSubresourceRange.calloc(1, stack);
            range.get(0).aspectMask(image.aspect).baseMipLevel(0).levelCount(image.mipLevels).baseArrayLayer(0).layerCount(1);
            VK10.vkCmdClearDepthStencilImage(commands, image.getId(), VK10.VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, clear, range);
        }
    }
}
