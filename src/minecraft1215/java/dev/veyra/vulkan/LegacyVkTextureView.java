package dev.veyra.vulkan;

import com.mojang.blaze3d.textures.GpuTexture;
import net.vulkanmod.render.engine.VkGpuTexture;

public class LegacyVkTextureView extends LegacyTextureView {
    // shortcut: subranges are metadata only, add native descriptor views before enabling 1.21.5 rendering.
    protected LegacyVkTextureView(VkGpuTexture texture, int baseMipLevel, int mipLevels) {
        super(texture, baseMipLevel, mipLevels);
    }

    public static LegacyVkTextureView create(GpuTexture texture) {
        return create(texture, 0, texture.getMipLevels());
    }

    public static LegacyVkTextureView create(GpuTexture texture, int baseMipLevel, int mipLevels) {
        if (!(texture instanceof VkGpuTexture vulkan)) {
            throw new IllegalArgumentException("Veyra texture views require VulkanMod textures.");
        }
        return new LegacyVkTextureView(vulkan, baseMipLevel, mipLevels);
    }

    @Override public VkGpuTexture texture() { return (VkGpuTexture) super.texture(); }
}
