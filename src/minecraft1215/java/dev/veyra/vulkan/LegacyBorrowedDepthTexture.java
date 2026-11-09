package dev.veyra.vulkan;

import net.vulkanmod.gl.VkGlTexture;
import net.vulkanmod.render.engine.VkGpuTexture;
import net.vulkanmod.vulkan.texture.VulkanImage;

/** The swapchain owns the image; this wrapper owns only its native texture registration. */
public final class LegacyBorrowedDepthTexture extends VkGpuTexture {
    public LegacyBorrowedDepthTexture(VulkanImage image) {
        this(image, VkGlTexture.genTextureId());
    }

    private LegacyBorrowedDepthTexture(VulkanImage image, int textureId) {
        // Minecraft exposes only DEPTH32; transfers still use the image's actual depth-stencil format.
        super(image.name, com.mojang.blaze3d.textures.TextureFormat.DEPTH32, image.width, image.height,
                image.mipLevels, textureId, VkGlTexture.getTexture(textureId));
        VkGlTexture.setVulkanImage(textureId, image);
    }

    @Override public void close() {
        if (closed) return;
        VkGlTexture.setVulkanImage(id, null);
        super.close();
    }
}
