package dev.veyra.compat;

import dev.veyra.vulkan.LegacyBorrowedDepthTexture;
import net.vulkanmod.vulkan.Renderer;
import net.vulkanmod.vulkan.texture.VulkanImage;

/** The main pass's depth image as a texture handle; it changes when the swapchain is recreated. */
public final class LegacyMainDepth {
    private static LegacyBorrowedDepthTexture texture;

    private LegacyMainDepth() { }

    public static synchronized GpuTexture texture() {
        var pass = Renderer.getInstance().getMainPass();
        VulkanImage image = pass.getDepthAttachment();
        if (texture != null && texture.getVulkanImage() != image) {
            texture.close();
            texture = null;
        }
        if (image != null && texture == null) texture = new LegacyBorrowedDepthTexture(image);
        return texture;
    }
}
