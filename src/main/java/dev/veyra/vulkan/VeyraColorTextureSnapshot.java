package dev.veyra.vulkan;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTextureView;
import net.vulkanmod.render.engine.VkGpuDevice;
import net.vulkanmod.render.engine.VkGpuTexture;
import net.vulkanmod.vulkan.Renderer;
import net.vulkanmod.vulkan.device.DeviceManager;
import net.vulkanmod.vulkan.texture.SamplerManager;
import net.vulkanmod.vulkan.texture.VulkanImage;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VK11;
import org.lwjgl.vulkan.VkFormatProperties;
import org.lwjgl.vulkan.VkImageCopy;

/** A sampled copy of a completed scene color image, with the source format and dimensions. */
public final class VeyraColorTextureSnapshot implements AutoCloseable {
    private final String name;
    private VkGpuTexture texture;
    private GpuTextureView view;
    private int width;
    private int height;
    private int format;
    private boolean ready;

    public VeyraColorTextureSnapshot(String name) {
        this.name = name;
    }

    public boolean isReady(int width, int height, int format) {
        return ready && isAllocated() && this.width == width && this.height == height && this.format == format;
    }

    public void ensure(int width, int height, int format) {
        if (isAllocated() && this.width == width && this.height == height && this.format == format) {
            return;
        }
        close();
        if (width < 1 || height < 1) {
            throw new IllegalArgumentException("The " + name + " dimensions must be positive.");
        }
        if (!(RenderSystem.getDevice() instanceof VkGpuDevice device)) {
            throw new IllegalStateException("The active GPU device is not VulkanMod's Vulkan device.");
        }

        VulkanImage image = VulkanImage.builder(width, height)
                .setName("Veyra " + name)
                .setFormat(format)
                .setUsage(VK10.VK_IMAGE_USAGE_SAMPLED_BIT | VK10.VK_IMAGE_USAGE_TRANSFER_DST_BIT)
                .createVulkanImage();
        VkGpuTexture createdTexture = device.gpuTextureFromVulkanImage(image);
        GpuTextureView createdView = null;
        try {
            createdView = device.createTextureView(createdTexture);
            texture = createdTexture;
            view = createdView;
            this.width = width;
            this.height = height;
            this.format = format;
        } catch (RuntimeException | Error exception) {
            if (createdView != null && !createdView.isClosed()) createdView.close();
            createdTexture.close();
            throw exception;
        }
    }

    public VulkanImage image() {
        if (!isAllocated()) {
            throw new IllegalStateException("The " + name + " color attachment is unavailable.");
        }
        return texture.getVulkanImage();
    }

    public GpuTextureView sampledView() {
        if (!ready) throw new IllegalStateException("The " + name + " snapshot has not been captured yet.");
        if (view == null || view.isClosed()) throw new IllegalStateException("The " + name + " view is unavailable.");
        return view;
    }

    /** Copy the source on VulkanMod's graphics queue and leave the snapshot ready for sampling. */
    public void capture(VulkanImage source) {
        if ((source.usage & VK10.VK_IMAGE_USAGE_TRANSFER_SRC_BIT) == 0) {
            throw new IllegalStateException("The source scene color image does not support transfer reads.");
        }
        Renderer renderer = Renderer.getInstance();
        if (renderer.getBoundRenderPass() != null) renderer.endRenderPass();
        ensure(source.width, source.height, source.format);
        VulkanImage destination = image();
        if ((destination.usage & VK10.VK_IMAGE_USAGE_TRANSFER_DST_BIT) == 0) {
            throw new IllegalStateException("The " + name + " image does not support transfer writes.");
        }
        if ((destination.usage & VK10.VK_IMAGE_USAGE_SAMPLED_BIT) == 0) {
            throw new IllegalStateException("The " + name + " image does not support shader sampling.");
        }
        if (source.format != destination.format || source.width != destination.width
                || source.height != destination.height || source.getId() == destination.getId()) {
            throw new IllegalStateException("The " + name + " image does not match its source scene color.");
        }

        ready = false;
        int previousLayout = source.getCurrentLayout();
        if (previousLayout == VK10.VK_IMAGE_LAYOUT_UNDEFINED) {
            throw new IllegalStateException("The source scene color image has not been initialized.");
        }
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkFormatProperties properties = VkFormatProperties.calloc(stack);
            VK10.vkGetPhysicalDeviceFormatProperties(DeviceManager.physicalDevice, source.format, properties);
            int transferFeatures = VK11.VK_FORMAT_FEATURE_TRANSFER_SRC_BIT | VK11.VK_FORMAT_FEATURE_TRANSFER_DST_BIT;
            if ((properties.optimalTilingFeatures() & transferFeatures) != transferFeatures) {
                throw new IllegalStateException("The scene color format does not support Vulkan image copies.");
            }
            var commands = Renderer.getCommandBuffer();
            source.transitionImageLayout(stack, commands, VK10.VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL);
            destination.transitionImageLayout(stack, commands, VK10.VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL);
            VkImageCopy.Buffer region = VkImageCopy.calloc(1, stack);
            region.srcSubresource().aspectMask(VK10.VK_IMAGE_ASPECT_COLOR_BIT).layerCount(1);
            region.dstSubresource().aspectMask(VK10.VK_IMAGE_ASPECT_COLOR_BIT).layerCount(1);
            region.extent().set(source.width, source.height, 1);
            VK10.vkCmdCopyImage(commands, source.getId(), VK10.VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,
                    destination.getId(), VK10.VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, region);
            source.transitionImageLayout(stack, commands, previousLayout);
        }
        transitionToSampling();
    }

    public void transitionToSampling() {
        VulkanImage color = image();
        if (color.getCurrentLayout() != VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL) {
            try (MemoryStack stack = MemoryStack.stackPush()) {
                color.transitionImageLayout(stack, Renderer.getCommandBuffer(),
                        VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL);
            }
        }
        color.setSampler(SamplerManager.getSampler(true, true, 0));
        ready = true;
    }

    @Override
    public void close() {
        ready = false;
        if (view != null && !view.isClosed()) view.close();
        view = null;
        if (texture != null && !texture.isClosed()) texture.close();
        texture = null;
        width = 0;
        height = 0;
        format = 0;
    }

    private boolean isAllocated() {
        return texture != null && !texture.isClosed() && view != null && !view.isClosed();
    }
}
