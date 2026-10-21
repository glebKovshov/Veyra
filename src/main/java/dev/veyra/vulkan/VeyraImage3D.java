package dev.veyra.vulkan;

import net.vulkanmod.vulkan.Vulkan;
import net.vulkanmod.vulkan.device.DeviceManager;
import net.vulkanmod.vulkan.memory.MemoryManager;
import net.vulkanmod.vulkan.texture.VulkanImage;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.vma.Vma;
import org.lwjgl.util.vma.VmaAllocationCreateInfo;
import org.lwjgl.vulkan.VkClearColorValue;
import org.lwjgl.vulkan.VkFormatProperties;
import org.lwjgl.vulkan.VkImageCreateInfo;
import org.lwjgl.vulkan.VkImageSubresourceRange;
import org.lwjgl.vulkan.VkImageViewCreateInfo;
import org.lwjgl.vulkan.VkImageMemoryBarrier;
import org.lwjgl.vulkan.VkCommandBuffer;

import java.io.IOException;
import java.nio.LongBuffer;

import static org.lwjgl.vulkan.VK10.*;

/** A VMA-owned 3D image exposed through VulkanMod's image descriptor interface. */
public final class VeyraImage3D extends VulkanImage {
    private final long allocation;
    private final long volumeView;
    private final boolean linearFiltering;
    private boolean freeScheduled;
    private boolean destroyed;

    private VeyraImage3D(String name, long image, long allocation, long view,
                         int format, int width, int height, boolean linearFiltering) {
        super(name, image, format, VK_IMAGE_ASPECT_COLOR_BIT, width, height, 1, 1, view);
        this.allocation = allocation;
        this.volumeView = view;
        this.linearFiltering = linearFiltering;
    }

    public static VeyraImage3D create(String name, int format, int width, int height, int depth)
            throws IOException {
        if (width <= 0 || height <= 0 || depth <= 0) {
            throw new IOException("Invalid 3D image dimensions for " + name + ": "
                    + width + "x" + height + "x" + depth + ".");
        }
        int maxDimension = DeviceManager.deviceProperties.limits().maxImageDimension3D();
        if (width > maxDimension || height > maxDimension || depth > maxDimension) {
            throw new IOException("3D image " + name + " exceeds the Vulkan device limit of "
                    + maxDimension + " texels per dimension.");
        }
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkFormatProperties formatProperties = VkFormatProperties.malloc(stack);
            vkGetPhysicalDeviceFormatProperties(DeviceManager.physicalDevice, format, formatProperties);
            int requiredFeatures = VK_FORMAT_FEATURE_SAMPLED_IMAGE_BIT | VK_FORMAT_FEATURE_STORAGE_IMAGE_BIT;
            if ((formatProperties.optimalTilingFeatures() & requiredFeatures) != requiredFeatures) {
                throw new IOException("Vulkan format " + format + " cannot be both sampled and used as a storage image for " + name + ".");
            }

            VkImageCreateInfo imageInfo = VkImageCreateInfo.calloc(stack)
                    .sType$Default()
                    .imageType(VK_IMAGE_TYPE_3D)
                    .format(format)
                    .extent(extent -> extent.set(width, height, depth))
                    .mipLevels(1)
                    .arrayLayers(1)
                    .samples(VK_SAMPLE_COUNT_1_BIT)
                    .tiling(VK_IMAGE_TILING_OPTIMAL)
                    .usage(VK_IMAGE_USAGE_SAMPLED_BIT | VK_IMAGE_USAGE_STORAGE_BIT
                            | VK_IMAGE_USAGE_TRANSFER_DST_BIT | VK_IMAGE_USAGE_TRANSFER_SRC_BIT)
                    .sharingMode(VK_SHARING_MODE_EXCLUSIVE)
                    .initialLayout(VK_IMAGE_LAYOUT_UNDEFINED);
            VmaAllocationCreateInfo allocationInfo = VmaAllocationCreateInfo.calloc(stack)
                    .usage(Vma.VMA_MEMORY_USAGE_AUTO_PREFER_DEVICE);
            LongBuffer imageHandle = stack.mallocLong(1);
            PointerBuffer allocationHandle = stack.mallocPointer(1);
            int result = Vma.vmaCreateImage(Vulkan.getAllocator(), imageInfo, allocationInfo,
                    imageHandle, allocationHandle, null);
            if (result != VK_SUCCESS) {
                throw new IOException("VMA could not allocate 3D image " + name + " (VkResult " + result + ").");
            }

            long image = imageHandle.get(0);
            long allocation = allocationHandle.get(0);
            long view = 0L;
            try {
                VkImageViewCreateInfo viewInfo = VkImageViewCreateInfo.calloc(stack)
                        .sType$Default()
                        .image(image)
                        .viewType(VK_IMAGE_VIEW_TYPE_3D)
                        .format(format)
                        .subresourceRange(VkImageSubresourceRange.calloc(stack)
                                .aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                                .baseMipLevel(0)
                                .levelCount(1)
                                .baseArrayLayer(0)
                                .layerCount(1));
                LongBuffer viewHandle = stack.mallocLong(1);
                result = vkCreateImageView(Vulkan.getVkDevice(), viewInfo, null, viewHandle);
                if (result != VK_SUCCESS) {
                    throw new IOException("Vulkan could not create a 3D view for " + name + " (VkResult " + result + ").");
                }
                view = viewHandle.get(0);
                return new VeyraImage3D(name, image, allocation, view, format, width, height,
                        (formatProperties.optimalTilingFeatures() & VK_FORMAT_FEATURE_SAMPLED_IMAGE_FILTER_LINEAR_BIT) != 0);
            } catch (IOException | RuntimeException exception) {
                if (view != 0L) vkDestroyImageView(Vulkan.getVkDevice(), view, null);
                Vma.vmaDestroyImage(Vulkan.getAllocator(), image, allocation);
                throw exception;
            }
        }
    }

    /** Clears every texel to zero and leaves the image ready for storage and sampled access. */
    public void clearZero() {
        if (!net.vulkanmod.vulkan.Renderer.isRecording()
                || net.vulkanmod.vulkan.Renderer.getCommandBuffer() == null
                || net.vulkanmod.vulkan.Renderer.getInstance().getBoundRenderPass() != null) {
            throw new IllegalStateException("Custom images must be cleared outside a render pass on a recording frame.");
        }
        try (MemoryStack stack = MemoryStack.stackPush()) {
            transitionImageLayout(stack, net.vulkanmod.vulkan.Renderer.getCommandBuffer(), VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL);
            VkClearColorValue clear = VkClearColorValue.calloc(stack).uint32(stack.ints(0, 0, 0, 0));
            VkImageSubresourceRange range = VkImageSubresourceRange.calloc(stack)
                    .aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                    .baseMipLevel(0)
                    .levelCount(1)
                    .baseArrayLayer(0)
                    .layerCount(1);
            vkCmdClearColorImage(net.vulkanmod.vulkan.Renderer.getCommandBuffer(), getId(),
                    VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, clear, range);
            transitionImageLayout(stack, net.vulkanmod.vulkan.Renderer.getCommandBuffer(), VK_IMAGE_LAYOUT_GENERAL);
        }
    }

    public boolean supportsLinearFiltering() {
        return linearFiltering;
    }

    /** VulkanMod 0.6.8's layout helper rejects GENERAL; storage images own this transition path. */
    @Override
    public void transitionImageLayout(MemoryStack stack, VkCommandBuffer commands, int nextLayout) {
        int previousLayout = getCurrentLayout();
        if (previousLayout == nextLayout) return;
        if (commands == null) throw new IllegalStateException("Custom image transitions require a command buffer.");
        LayoutAccess previous = layoutAccess(previousLayout);
        LayoutAccess next = layoutAccess(nextLayout);
        VkImageMemoryBarrier.Buffer barrier = VkImageMemoryBarrier.calloc(1, stack)
                .sType$Default()
                .oldLayout(previousLayout).newLayout(nextLayout)
                .srcAccessMask(previous.access()).dstAccessMask(next.access())
                .srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED).dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                .image(getId());
        barrier.subresourceRange().aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                .baseMipLevel(0).levelCount(1).baseArrayLayer(0).layerCount(1);
        vkCmdPipelineBarrier(commands, previous.stages(), next.stages(), 0, null, null, barrier);
        setCurrentLayout(nextLayout);
    }

    private static LayoutAccess layoutAccess(int layout) {
        return switch (layout) {
            case VK_IMAGE_LAYOUT_UNDEFINED -> new LayoutAccess(VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT, 0);
            case VK_IMAGE_LAYOUT_GENERAL -> new LayoutAccess(
                    VK_PIPELINE_STAGE_ALL_GRAPHICS_BIT | VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT,
                    VK_ACCESS_SHADER_READ_BIT | VK_ACCESS_SHADER_WRITE_BIT);
            case VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL -> new LayoutAccess(VK_PIPELINE_STAGE_TRANSFER_BIT, VK_ACCESS_TRANSFER_WRITE_BIT);
            case VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL -> new LayoutAccess(VK_PIPELINE_STAGE_TRANSFER_BIT, VK_ACCESS_TRANSFER_READ_BIT);
            case VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL -> new LayoutAccess(
                    VK_PIPELINE_STAGE_ALL_GRAPHICS_BIT | VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT, VK_ACCESS_SHADER_READ_BIT);
            default -> throw new IllegalArgumentException("Unsupported custom-image layout " + layout + ".");
        };
    }

    private record LayoutAccess(int stages, int access) { }

    @Override
    public long getImageView() {
        return volumeView;
    }

    @Override
    public long getImageView(int ignoredFormat) {
        return volumeView;
    }

    @Override
    public long getAllocation() {
        return allocation;
    }

    @Override
    public void free() {
        if (!freeScheduled && !destroyed) {
            freeScheduled = true;
            MemoryManager.getInstance().addToFreeable(this);
        }
    }

    @Override
    public void doFree() {
        if (destroyed) return;
        destroyed = true;
        vkDestroyImageView(Vulkan.getVkDevice(), volumeView, null);
        Vma.vmaDestroyImage(Vulkan.getAllocator(), getId(), allocation);
    }
}
