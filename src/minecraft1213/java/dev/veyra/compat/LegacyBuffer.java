package dev.veyra.compat;

import dev.veyra.mixin.accessor.LegacyBufferAccessor;
import net.vulkanmod.vulkan.memory.Buffer;
import net.vulkanmod.vulkan.memory.MemoryType;

/** VulkanMod 0.5.3 keeps buffer allocation and metadata protected; Veyra's own buffers expose them. */
public class LegacyBuffer extends Buffer {
    protected LegacyBuffer(int usage, MemoryType type) {
        super(usage, type);
    }

    /** The usage flags of any VulkanMod buffer. */
    public static int usage(Buffer buffer) {
        return buffer instanceof LegacyBuffer legacy ? legacy.usage : ((LegacyBufferAccessor) buffer).veyra$getUsage();
    }

    public void allocate(long size) { createBuffer(size); }
    public void assignBufferSize(long size) { setBufferSize(size); }
    public void assignId(long id) { setId(id); }
}
