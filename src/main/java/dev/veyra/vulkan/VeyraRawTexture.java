package dev.veyra.vulkan;

import dev.veyra.shader.RawTextureSpec;
import net.vulkanmod.vulkan.Vulkan;
import net.vulkanmod.vulkan.device.DeviceManager;
import net.vulkanmod.vulkan.memory.MemoryManager;
import net.vulkanmod.vulkan.texture.VulkanImage;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.util.vma.Vma;
import org.lwjgl.util.vma.VmaAllocationCreateInfo;
import org.lwjgl.util.vma.VmaAllocationInfo;
import org.lwjgl.vulkan.VkBufferCreateInfo;
import org.lwjgl.vulkan.VkBufferImageCopy;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkFormatProperties;
import org.lwjgl.vulkan.VkImageCreateInfo;
import org.lwjgl.vulkan.VkImageMemoryBarrier;
import org.lwjgl.vulkan.VkImageViewCreateInfo;
import org.lwjgl.vulkan.VkSamplerCreateInfo;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.LongBuffer;

import static org.lwjgl.vulkan.VK10.*;

/**
 * An immutable sampled 2D or 3D image holding an Iris raw custom texture. It owns its sampler because a 3D
 * texture needs a third address mode (Photon's tiling cloud noise repeats along depth too).
 */
public final class VeyraRawTexture extends VulkanImage {
    private final long allocation;
    private final long ownView;
    private final long ownSampler;
    private final boolean linearFiltering;
    private boolean freeScheduled;
    private boolean destroyed;

    private VeyraRawTexture(String name, long image, long allocation, long view, long sampler,
                            int format, int width, int height, boolean linearFiltering) {
        super(name, image, format, VK_IMAGE_ASPECT_COLOR_BIT, width, height, 1, 1, view);
        this.allocation = allocation;
        this.ownView = view;
        this.ownSampler = sampler;
        this.linearFiltering = linearFiltering;
        setSampler(sampler);
    }

    /** Creates the image and uploads its texels synchronously; the result is ready for shader reads. */
    public static VeyraRawTexture upload(String name, RawTextureSpec spec, ByteBuffer texels,
                                         boolean blur, boolean clamp) throws IOException {
        int format = spec.vulkanFormat();
        boolean volume = spec.dimensions() == 3;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            int maxDimension = volume ? DeviceManager.deviceProperties.limits().maxImageDimension3D()
                    : DeviceManager.deviceProperties.limits().maxImageDimension2D();
            if (spec.width() > maxDimension || spec.height() > maxDimension || spec.depth() > maxDimension) {
                throw new IOException("Raw texture " + name + " exceeds the Vulkan device limit of " + maxDimension + ".");
            }
            VkFormatProperties properties = VkFormatProperties.malloc(stack);
            vkGetPhysicalDeviceFormatProperties(DeviceManager.physicalDevice, format, properties);
            int features = properties.optimalTilingFeatures();
            if ((features & VK_FORMAT_FEATURE_SAMPLED_IMAGE_BIT) == 0) {
                throw new IOException("The Vulkan device cannot sample format " + format + " for raw texture " + name + ".");
            }
            boolean linear = blur && (features & VK_FORMAT_FEATURE_SAMPLED_IMAGE_FILTER_LINEAR_BIT) != 0;

            VkImageCreateInfo imageInfo = VkImageCreateInfo.calloc(stack).sType$Default()
                    .imageType(volume ? VK_IMAGE_TYPE_3D : VK_IMAGE_TYPE_2D)
                    .format(format)
                    .extent(extent -> extent.set(spec.width(), spec.height(), spec.depth()))
                    .mipLevels(1).arrayLayers(1).samples(VK_SAMPLE_COUNT_1_BIT)
                    .tiling(VK_IMAGE_TILING_OPTIMAL)
                    .usage(VK_IMAGE_USAGE_SAMPLED_BIT | VK_IMAGE_USAGE_TRANSFER_DST_BIT)
                    .sharingMode(VK_SHARING_MODE_EXCLUSIVE)
                    .initialLayout(VK_IMAGE_LAYOUT_UNDEFINED);
            VmaAllocationCreateInfo imageAllocationInfo = VmaAllocationCreateInfo.calloc(stack)
                    .usage(Vma.VMA_MEMORY_USAGE_AUTO_PREFER_DEVICE);
            LongBuffer imageHandle = stack.mallocLong(1);
            PointerBuffer imageAllocation = stack.mallocPointer(1);
            check(Vma.vmaCreateImage(Vulkan.getAllocator(), imageInfo, imageAllocationInfo, imageHandle,
                    imageAllocation, null), "allocate raw texture " + name);
            long image = imageHandle.get(0);
            long allocation = imageAllocation.get(0);
            long view = 0L, sampler = 0L;
            try {
                copyTexels(image, spec, texels, name);
                VkImageViewCreateInfo viewInfo = VkImageViewCreateInfo.calloc(stack).sType$Default()
                        .image(image).viewType(volume ? VK_IMAGE_VIEW_TYPE_3D : VK_IMAGE_VIEW_TYPE_2D).format(format);
                viewInfo.subresourceRange().aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                        .baseMipLevel(0).levelCount(1).baseArrayLayer(0).layerCount(1);
                LongBuffer viewHandle = stack.mallocLong(1);
                check(vkCreateImageView(Vulkan.getVkDevice(), viewInfo, null, viewHandle), "create a view for " + name);
                view = viewHandle.get(0);

                int filter = linear ? VK_FILTER_LINEAR : VK_FILTER_NEAREST;
                int address = clamp ? VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE : VK_SAMPLER_ADDRESS_MODE_REPEAT;
                VkSamplerCreateInfo samplerInfo = VkSamplerCreateInfo.calloc(stack).sType$Default()
                        .magFilter(filter).minFilter(filter).mipmapMode(VK_SAMPLER_MIPMAP_MODE_NEAREST)
                        .addressModeU(address).addressModeV(address).addressModeW(address)
                        .maxLod(0.0F).borderColor(VK_BORDER_COLOR_FLOAT_TRANSPARENT_BLACK);
                LongBuffer samplerHandle = stack.mallocLong(1);
                check(vkCreateSampler(Vulkan.getVkDevice(), samplerInfo, null, samplerHandle),
                        "create a sampler for " + name);
                sampler = samplerHandle.get(0);
                VeyraRawTexture texture = new VeyraRawTexture(name, image, allocation, view, sampler,
                        format, spec.width(), spec.height(), linear);
                texture.setCurrentLayout(VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL);
                return texture;
            } catch (IOException | RuntimeException failure) {
                if (sampler != 0L) vkDestroySampler(Vulkan.getVkDevice(), sampler, null);
                if (view != 0L) vkDestroyImageView(Vulkan.getVkDevice(), view, null);
                Vma.vmaDestroyImage(Vulkan.getAllocator(), image, allocation);
                throw failure;
            }
        }
    }

    private static void copyTexels(long image, RawTextureSpec spec, ByteBuffer texels, String name) throws IOException {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkBufferCreateInfo bufferInfo = VkBufferCreateInfo.calloc(stack).sType$Default()
                    .size(texels.remaining()).usage(VK_BUFFER_USAGE_TRANSFER_SRC_BIT)
                    .sharingMode(VK_SHARING_MODE_EXCLUSIVE);
            VmaAllocationCreateInfo stagingInfo = VmaAllocationCreateInfo.calloc(stack)
                    .usage(Vma.VMA_MEMORY_USAGE_AUTO)
                    .flags(Vma.VMA_ALLOCATION_CREATE_HOST_ACCESS_SEQUENTIAL_WRITE_BIT
                            | Vma.VMA_ALLOCATION_CREATE_MAPPED_BIT);
            LongBuffer bufferHandle = stack.mallocLong(1);
            PointerBuffer bufferAllocation = stack.mallocPointer(1);
            VmaAllocationInfo mapped = VmaAllocationInfo.calloc(stack);
            check(Vma.vmaCreateBuffer(Vulkan.getAllocator(), bufferInfo, stagingInfo, bufferHandle,
                    bufferAllocation, mapped), "allocate the upload buffer for " + name);
            long buffer = bufferHandle.get(0);
            long allocation = bufferAllocation.get(0);
            try {
                MemoryUtil.memCopy(MemoryUtil.memAddress(texels), mapped.pMappedData(), texels.remaining());
                Vma.vmaFlushAllocation(Vulkan.getAllocator(), allocation, 0, VK_WHOLE_SIZE);

                var queue = DeviceManager.getGraphicsQueue();
                var commands = queue.beginCommands();
                VkCommandBuffer handle = commands.getHandle();
                barrier(stack, handle, image, VK_IMAGE_LAYOUT_UNDEFINED, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
                        0, VK_ACCESS_TRANSFER_WRITE_BIT, VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT, VK_PIPELINE_STAGE_TRANSFER_BIT);
                VkBufferImageCopy.Buffer region = VkBufferImageCopy.calloc(1, stack);
                region.imageSubresource().aspectMask(VK_IMAGE_ASPECT_COLOR_BIT).mipLevel(0)
                        .baseArrayLayer(0).layerCount(1);
                region.imageExtent().set(spec.width(), spec.height(), spec.depth());
                vkCmdCopyBufferToImage(handle, buffer, image, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, region);
                barrier(stack, handle, image, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
                        VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL, VK_ACCESS_TRANSFER_WRITE_BIT, VK_ACCESS_SHADER_READ_BIT,
                        VK_PIPELINE_STAGE_TRANSFER_BIT,
                        VK_PIPELINE_STAGE_VERTEX_SHADER_BIT | VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT);
                long fence = queue.submitCommands(commands);
                check(vkWaitForFences(Vulkan.getVkDevice(), fence, true, -1L), "upload raw texture " + name);
                commands.reset();
            } finally {
                Vma.vmaDestroyBuffer(Vulkan.getAllocator(), buffer, allocation);
            }
        }
    }

    private static void barrier(MemoryStack stack, VkCommandBuffer commands, long image, int oldLayout, int newLayout,
                                int sourceAccess, int targetAccess, int sourceStages, int targetStages) {
        VkImageMemoryBarrier.Buffer barrier = VkImageMemoryBarrier.calloc(1, stack).sType$Default()
                .oldLayout(oldLayout).newLayout(newLayout)
                .srcAccessMask(sourceAccess).dstAccessMask(targetAccess)
                .srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED).dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                .image(image);
        barrier.subresourceRange().aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                .baseMipLevel(0).levelCount(1).baseArrayLayer(0).layerCount(1);
        vkCmdPipelineBarrier(commands, sourceStages, targetStages, 0, null, null, barrier);
    }

    private static void check(int result, String action) throws IOException {
        if (result != VK_SUCCESS) throw new IOException("Vulkan could not " + action + " (VkResult " + result + ").");
    }

    public boolean supportsLinearFiltering() {
        return linearFiltering;
    }

    /** VeyraImageDescriptor installs a default sampler; raw textures keep their own address modes. */
    public long ownSampler() {
        return ownSampler;
    }

    @Override
    public long getImageView() {
        return ownView;
    }

    @Override
    public long getImageView(int ignoredFormat) {
        return ownView;
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
        vkDestroySampler(Vulkan.getVkDevice(), ownSampler, null);
        vkDestroyImageView(Vulkan.getVkDevice(), ownView, null);
        Vma.vmaDestroyImage(Vulkan.getAllocator(), getId(), allocation);
    }
}
