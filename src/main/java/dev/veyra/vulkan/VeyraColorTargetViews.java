package dev.veyra.vulkan;

import net.vulkanmod.vulkan.Vulkan;
import net.vulkanmod.vulkan.memory.MemoryManager;
import net.vulkanmod.vulkan.texture.VulkanImage;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkImageViewCreateInfo;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;

import static org.lwjgl.vulkan.VK10.*;

/** Preserve RGB sampling semantics when its physical render attachment has an extra alpha channel. */
public final class VeyraColorTargetViews {
    private static final Map<VulkanImage, Map<Integer, Long>> opaqueAlphaViews = new IdentityHashMap<>();

    private VeyraColorTargetViews() { }

    public static synchronized void register(VulkanImage image, boolean opaqueAlpha) {
        if (opaqueAlpha) opaqueAlphaViews.computeIfAbsent(image, ignored -> new HashMap<>());
        else forget(image);
    }

    public static synchronized void inherit(VulkanImage source, VulkanImage destination) {
        register(destination, opaqueAlphaViews.containsKey(source));
    }

    public static synchronized long sampledView(VulkanImage image, int mipLevel, long original) {
        Map<Integer, Long> views = opaqueAlphaViews.get(image);
        if (views == null) return original;
        if (mipLevel < -1 || mipLevel >= image.mipLevels) {
            throw new IllegalArgumentException("RGB sampled view mip is outside its image: " + mipLevel);
        }
        return views.computeIfAbsent(mipLevel, level -> createView(image, level));
    }

    private static long createView(VulkanImage image, int mipLevel) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkImageViewCreateInfo info = VkImageViewCreateInfo.calloc(stack).sType$Default()
                    .image(image.getId()).viewType(image.viewType).format(image.format);
            info.components().a(VK_COMPONENT_SWIZZLE_ONE);
            info.subresourceRange().aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                    .baseMipLevel(mipLevel < 0 ? 0 : mipLevel)
                    .levelCount(mipLevel < 0 ? image.mipLevels : 1).baseArrayLayer(0).layerCount(image.arrayLayers);
            var result = stack.mallocLong(1);
            int status = vkCreateImageView(Vulkan.getVkDevice(), info, null, result);
            if (status != VK_SUCCESS) throw new IllegalStateException("Could not create RGB sampled view: " + status);
            return result.get(0);
        }
    }

    public static synchronized void forget(VulkanImage image) {
        Map<Integer, Long> views = opaqueAlphaViews.remove(image);
        if (views == null || views.isEmpty()) return;
        var device = Vulkan.getVkDevice();
        // Submitted descriptors retain these views until their frame fence completes.
        views.values().forEach(view -> MemoryManager.getInstance()
                .addFrameOp(() -> vkDestroyImageView(device, view, null)));
    }
}
