package dev.veyra.mixin.accessor;

import net.vulkanmod.render.engine.VkGpuTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = VkGpuTexture.class, remap = false)
public interface VkGpuTextureAccessor {
    @Accessor("needsClear") void veyra$setNeedsClear(boolean needsClear);
}
