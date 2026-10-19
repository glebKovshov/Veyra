package dev.veyra.mixin.accessor;

import net.vulkanmod.vulkan.shader.Pipeline;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import java.util.List;

@Mixin(value = Pipeline.class, remap = false)
public interface NativePipelineRegistryAccessor {
    @Accessor("PIPELINES") static List<Pipeline> veyra$getPipelines() { throw new AssertionError("Mixin accessor not transformed"); }
}
