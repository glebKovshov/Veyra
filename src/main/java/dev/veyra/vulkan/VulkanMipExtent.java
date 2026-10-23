package dev.veyra.vulkan;

/** Computes the clamped dimensions of a Vulkan image mip level. */
public final class VulkanMipExtent {
    private VulkanMipExtent() { }

    public static int atLevel(int baseExtent, int level) {
        if (baseExtent <= 0) throw new IllegalArgumentException("Base extent must be positive.");
        if (level < 0) throw new IllegalArgumentException("Mip level must not be negative.");
        return level >= Integer.SIZE - 1 ? 1 : Math.max(1, baseExtent >> level);
    }
}
