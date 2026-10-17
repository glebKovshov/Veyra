package dev.veyra.vulkan;

import net.vulkanmod.gl.VkGlTexture;
import net.vulkanmod.render.engine.VkGpuTexture;
import net.vulkanmod.render.engine.VkTextureView;
import net.vulkanmod.vulkan.Vulkan;
import net.vulkanmod.vulkan.texture.VulkanImage;
import org.lwjgl.vulkan.VK10;

/** A sampled view of the depth aspect of VulkanMod's depth-stencil image. */
public final class VeyraDepthTextureView extends VkTextureView {
    private final DepthSampleTexture texture;

    public VeyraDepthTextureView(VkGpuTexture source) {
        this(new DepthSampleTexture(source));
    }

    private VeyraDepthTextureView(DepthSampleTexture texture) {
        super(texture, 0, texture.getMipLevels());
        this.texture = texture;
    }

    public VulkanImage sampledImage() {
        return texture.getVulkanImage();
    }

    @Override
    public void close() {
        if (isClosed()) {
            return;
        }
        texture.closeDepthImageView();
        super.close();
        texture.close();
    }

    private static final class DepthSampleTexture extends VkGpuTexture {
        private final VulkanImage depthSampleImage;
        private final long depthImageView;
        private final int textureId;

        private DepthSampleTexture(VkGpuTexture source) {
            this(source, createDepthImage(source));
        }

        private DepthSampleTexture(VkGpuTexture source, SampledDepth sampledDepth) {
            super(sampledDepth.textureId(), source.getLabel(), source.getFormat(), source.getWidth(0),
                    source.getHeight(0), source.getDepthOrLayers(), source.getMipLevels(), source.usage(),
                    VkGlTexture.getTexture(sampledDepth.textureId()));
            textureId = sampledDepth.textureId();
            depthImageView = sampledDepth.imageView();
            depthSampleImage = sampledDepth.image();
        }

        private static SampledDepth createDepthImage(VkGpuTexture source) {
            VulkanImage sourceImage = source.getVulkanImage();
            long imageId = sourceImage.getId();
            long depthImageView = VulkanImage.createImageView(imageId, sourceImage.viewType, sourceImage.format,
                    VK10.VK_IMAGE_ASPECT_DEPTH_BIT, sourceImage.arrayLayers, 0, sourceImage.mipLevels);
            VulkanImage depthSampleImage = new VulkanImage(sourceImage.name, imageId, sourceImage.format, sourceImage.mipLevels,
                    sourceImage.width, sourceImage.height, sourceImage.formatSize, sourceImage.usage, depthImageView);
            int textureId = VkGlTexture.genTextureId();
            VkGlTexture.setVulkanImage(textureId, depthSampleImage);
            return new SampledDepth(textureId, depthImageView, depthSampleImage);
        }

        @Override
        public VulkanImage getVulkanImage() {
            return depthSampleImage;
        }

        private void closeDepthImageView() {
            if (depthImageView != 0L) {
                VK10.vkDestroyImageView(Vulkan.getVkDevice(), depthImageView, null);
            }
            VkGlTexture.setVulkanImage(textureId, null);
            VkGlTexture.glDeleteTextures(textureId);
        }
    }

    private record SampledDepth(int textureId, long imageView, VulkanImage image) {
    }
}
