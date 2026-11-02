package dev.veyra.mixin.accessor;

import it.unimi.dsi.fastutil.longs.Long2ReferenceOpenHashMap;
import net.vulkanmod.vulkan.memory.MemoryManager;
import net.vulkanmod.vulkan.memory.buffer.Buffer;
import net.vulkanmod.vulkan.texture.VulkanImage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = MemoryManager.class, remap = false)
public interface MemoryManagerAccessor {
    @Accessor("buffers")
    static Long2ReferenceOpenHashMap<Buffer> veyra$getBuffers() { throw new AssertionError("Mixin accessor not transformed"); }

    @Accessor("images")
    static Long2ReferenceOpenHashMap<VulkanImage> veyra$getImages() { throw new AssertionError("Mixin accessor not transformed"); }
}
