package dev.veyra.vulkan;

import net.vulkanmod.vulkan.texture.SamplerManager;

/** Maps the common clamp/filter/LOD sampler request to VulkanMod 0.5's native cache. */
public final class LegacyNativeSamplers {
    private LegacyNativeSamplers() { }

    public static void setSampler(net.vulkanmod.vulkan.texture.VulkanImage image, long sampler) {
        ((dev.veyra.mixin.accessor.LegacyVulkanImageSamplerAccessor) image).veyra$setSampler(sampler);
    }

    public static long getSampler(boolean clamp, boolean linear, int maxLod) {
        return SamplerManager.getTextureSampler(lod(maxLod), flags(clamp, linear, maxLod));
    }

    static byte lod(int maxLod) {
        if (maxLod < 0 || maxLod > Byte.MAX_VALUE) {
            throw new IllegalArgumentException("Legacy sampler LOD must fit a nonnegative signed byte.");
        }
        return (byte) maxLod;
    }

    static byte flags(boolean clamp, boolean linear, int maxLod) {
        int flags = clamp ? SamplerManager.CLAMP_BIT : 0;
        if (linear) flags |= SamplerManager.LINEAR_FILTERING_BIT;
        if (maxLod > 0) {
            flags |= SamplerManager.USE_MIPMAPS_BIT;
            if (linear) flags |= SamplerManager.MIPMAP_LINEAR_FILTERING_BIT;
        }
        return (byte) flags;
    }
}
