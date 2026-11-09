package dev.veyra.mixin.accessor;

import java.nio.ByteBuffer;
import java.util.List;
import net.vulkanmod.vulkan.shader.Pipeline;
import net.vulkanmod.vulkan.shader.descriptor.UBO;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(value = Pipeline.class, remap = false)
public interface NativePipelineAccessor {
    @Accessor("buffers") List<UBO> veyra$getUniformBuffers();
    @Invoker("destroyDescriptorSets") void veyra$destroyDescriptorSets();
    @Invoker("createShaderModule") static long veyra$createShaderModule(ByteBuffer code) {
        throw new AssertionError("Mixin invoker not transformed");
    }
}
