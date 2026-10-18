package dev.veyra.mixin.accessor;

import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = "net.vulkanmod.render.engine.VkGpuDevice$ShaderCompilationKey", remap = false)
public interface ShaderCompilationKeyAccessor {
    @Accessor("id") Identifier veyra$getId();
}
