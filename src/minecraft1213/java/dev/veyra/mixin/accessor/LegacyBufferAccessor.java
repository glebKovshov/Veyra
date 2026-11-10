package dev.veyra.mixin.accessor;

import net.vulkanmod.vulkan.memory.Buffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/** VulkanMod 0.5.3 keeps these buffer members protected. */
@Mixin(value = Buffer.class, remap = false)
public interface LegacyBufferAccessor {
    @Accessor("usage") int veyra$getUsage();
    @Invoker("createBuffer") void veyra$createBuffer(long size);
    @Invoker("setId") void veyra$setId(long id);
    @Invoker("setAllocation") void veyra$setAllocation(long allocation);
}
