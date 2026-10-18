package dev.veyra.vulkan;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import dev.veyra.mixin.accessor.RenderTargetAccessor;
import net.vulkanmod.render.engine.VkGpuDevice;
import net.vulkanmod.render.engine.VkGpuTexture;
import net.vulkanmod.vulkan.device.DeviceManager;
import net.vulkanmod.vulkan.Renderer;
import net.vulkanmod.vulkan.texture.VulkanImage;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VK11;
import org.lwjgl.vulkan.VkFormatProperties;
import org.lwjgl.vulkan.VkClearDepthStencilValue;
import org.lwjgl.vulkan.VkImageSubresourceRange;

/** A depth-only render target matching VulkanMod's main depth format. */
public final class VeyraDepthTextureSnapshot implements AutoCloseable {
    private final String name;
    private TextureTarget target;
    private VeyraDepthTextureView sampledView;
    private boolean ready;

    public VeyraDepthTextureSnapshot(String name) {
        this.name = name;
    }

    public boolean isReady(int width, int height, int format) {
        return ready && target != null && target.width == width && target.height == height && depthImage().format == format;
    }

    public void ensure(int width, int height, int format) {
        if (target != null && target.width == width && target.height == height
                && depthImage().format == format) {
            return;
        }

        close();
        if (!VulkanImage.isDepthFormat(format)) {
            throw new IllegalStateException("VulkanMod's main depth attachment does not use a depth format.");
        }
        if (!(RenderSystem.getDevice() instanceof VkGpuDevice device)) {
            throw new IllegalStateException("The active GPU device is not VulkanMod's Vulkan device.");
        }

        target = new TextureTarget("Veyra " + name + " snapshot", width, height, true);
        RenderTargetAccessor accessor = (RenderTargetAccessor) target;
        GpuTexture oldDepth = accessor.veyra$getDepthTexture();
        GpuTextureView oldDepthView = accessor.veyra$getDepthTextureView();
        if (oldDepthView != null && !oldDepthView.isClosed()) {
            oldDepthView.close();
        }
        if (oldDepth != null && !oldDepth.isClosed()) {
            oldDepth.close();
        }

        VulkanImage image = VulkanImage.builder(width, height)
                .setName("Veyra " + name + " snapshot")
                .setFormat(format)
                .addUsage(VK10.VK_IMAGE_USAGE_DEPTH_STENCIL_ATTACHMENT_BIT)
                .addUsage(VK10.VK_IMAGE_USAGE_TRANSFER_DST_BIT)
                .createVulkanImage();
        VkGpuTexture depthTexture = device.gpuTextureFromVulkanImage(image);
        GpuTextureView depthTextureView = device.createTextureView(depthTexture);
        accessor.veyra$setDepthTexture(depthTexture);
        accessor.veyra$setDepthTextureView(depthTextureView);
        sampledView = new VeyraDepthTextureView(depthTexture);
    }

    public RenderTarget renderTarget() {
        if (target == null) {
            throw new IllegalStateException("The " + name + " render target has not been created.");
        }
        return target;
    }

    public GpuTexture depthTexture() {
        return ((RenderTargetAccessor) renderTarget()).veyra$getDepthTexture();
    }

    public VulkanImage depthImage() {
        GpuTexture depthTexture = depthTexture();
        if (!(depthTexture instanceof VkGpuTexture vulkanTexture) || vulkanTexture.isClosed()) {
            throw new IllegalStateException("The " + name + " attachment is unavailable.");
        }
        return vulkanTexture.getVulkanImage();
    }

    public GpuTextureView sampledView() {
        if (sampledView == null || sampledView.isClosed()) {
            throw new IllegalStateException("The " + name + " snapshot has not been captured yet.");
        }
        return sampledView;
    }

    public void transitionToAttachment() {
        transition(VK10.VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL);
    }

    /** Initialize actual image contents before any first-frame history sampling. */
    public void clearToFarDepth() {
        if (Renderer.getInstance().getBoundRenderPass() != null) Renderer.getInstance().endRenderPass();
        VulkanImage image = depthImage();
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkFormatProperties properties = VkFormatProperties.calloc(stack);
            VK10.vkGetPhysicalDeviceFormatProperties(DeviceManager.physicalDevice, image.format, properties);
            if ((properties.optimalTilingFeatures() & VK11.VK_FORMAT_FEATURE_TRANSFER_DST_BIT) == 0) {
                throw new IllegalStateException("The depth history format does not support Vulkan transfer clears.");
            }
            var commandBuffer = Renderer.getCommandBuffer();
            image.transitionImageLayout(stack, commandBuffer, VK10.VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL);
            VkClearDepthStencilValue value = VkClearDepthStencilValue.calloc(stack).depth(1.0F).stencil(0);
            VkImageSubresourceRange range = VkImageSubresourceRange.calloc(stack)
                    .aspectMask(image.aspect).baseMipLevel(0).levelCount(1).baseArrayLayer(0).layerCount(1);
            VK10.vkCmdClearDepthStencilImage(commandBuffer, image.getId(),
                    VK10.VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, value, range);
        }
        transitionToSampling();
    }

    public void transitionToSampling() {
        VulkanImage image = depthImage();
        if (image.getCurrentLayout() != VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL) {
            try (MemoryStack stack = MemoryStack.stackPush()) {
                image.transitionImageLayout(stack, Renderer.getCommandBuffer(),
                        VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL);
            }
        }
        sampledView.sampledImage().setCurrentLayout(image.getCurrentLayout());
        ready = true;
    }

    private void transition(int layout) {
        VulkanImage image = depthImage();
        if (image.getCurrentLayout() != layout) {
            try (MemoryStack stack = MemoryStack.stackPush()) {
                image.transitionImageLayout(stack, Renderer.getCommandBuffer(), layout);
            }
        }
        sampledView.sampledImage().setCurrentLayout(image.getCurrentLayout());
    }

    @Override
    public void close() {
        ready = false;
        if (sampledView != null && !sampledView.isClosed()) {
            sampledView.close();
        }
        sampledView = null;
        if (target != null) {
            target.destroyBuffers();
        }
        target = null;
    }
}
