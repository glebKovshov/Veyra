package dev.veyra.mixin.accessor;

import net.vulkanmod.vulkan.memory.buffer.UniformBuffer;
import net.vulkanmod.vulkan.shader.DescriptorSets;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(value = DescriptorSets.class, remap = false)
public interface NativeDescriptorSetsAccessor {
    @Invoker("bindSets") void veyra$bindSets(VkCommandBuffer command, UniformBuffer uniforms, int bindPoint);
}
