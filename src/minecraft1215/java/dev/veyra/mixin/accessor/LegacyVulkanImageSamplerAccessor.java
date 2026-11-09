package dev.veyra.mixin.accessor;

import net.vulkanmod.vulkan.texture.VulkanImage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = VulkanImage.class, remap = false)
public interface LegacyVulkanImageSamplerAccessor {
    @Accessor("sampler")
    void veyra$setSampler(long sampler);
}
