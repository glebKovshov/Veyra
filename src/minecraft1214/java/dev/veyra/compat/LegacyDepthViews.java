package dev.veyra.compat;

import java.util.IdentityHashMap;
import java.util.Map;
import net.vulkanmod.vulkan.Vulkan;
import net.vulkanmod.vulkan.texture.VulkanImage;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkImageViewCreateInfo;

/**
 * Depth-only views of depth-stencil images. VulkanMod for 1.21.4 creates OpenGL-style depth
 * textures in its default depth-stencil format, and a sampled view may name only one aspect.
 */
public final class LegacyDepthViews {
    private static final Map<VulkanImage, Long> VIEWS = new IdentityHashMap<>();

    private LegacyDepthViews() { }

    public static synchronized long sampledView(VulkanImage image, long view) {
        if ((image.aspect & VK10.VK_IMAGE_ASPECT_STENCIL_BIT) == 0 || view != image.getImageView()) return view;
        return VIEWS.computeIfAbsent(image, LegacyDepthViews::create);
    }

    /** Runs when VulkanMod frees the image, after the frames that used it completed. */
    public static synchronized void forget(VulkanImage image) {
        Long view = VIEWS.remove(image);
        if (view != null) VK10.vkDestroyImageView(Vulkan.getVkDevice(), view, null);
    }

    /** Destroys every view before the device; images still alive at shutdown never reach their free. */
    public static synchronized void clear() {
        VIEWS.values().forEach(view -> VK10.vkDestroyImageView(Vulkan.getVkDevice(), view, null));
        VIEWS.clear();
    }

    private static long create(VulkanImage image) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            var info = VkImageViewCreateInfo.calloc(stack).sType$Default()
                    .image(image.getId()).viewType(VK10.VK_IMAGE_VIEW_TYPE_2D).format(image.format);
            info.subresourceRange().aspectMask(VK10.VK_IMAGE_ASPECT_DEPTH_BIT)
                    .baseMipLevel(0).levelCount(image.mipLevels).baseArrayLayer(0).layerCount(1);
            var view = stack.mallocLong(1);
            int result = VK10.vkCreateImageView(Vulkan.getVkDevice(), info, null, view);
            if (result != VK10.VK_SUCCESS) throw new IllegalStateException("Could not create a depth view: " + result);
            return view.get(0);
        }
    }
}
