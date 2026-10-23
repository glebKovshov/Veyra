package dev.veyra.shader;

import net.vulkanmod.vulkan.memory.buffer.Buffer;
import net.vulkanmod.vulkan.shader.descriptor.UBO;

import java.util.List;

import static org.lwjgl.vulkan.VK10.VK_DESCRIPTOR_TYPE_STORAGE_BUFFER_DYNAMIC;

/** Adapts VulkanMod's dynamic buffer descriptor update path to an Iris shader-storage buffer. */
public final class VeyraStorageBufferUBO extends UBO {
    private final int rangeBytes;

    public VeyraStorageBufferUBO(UBO parsedBlock, VeyraStorageBufferResources.StorageBuffer storageBuffer) {
        super(parsedBlock.name, ShaderPackStorageBuffers.VULKAN_DESCRIPTOR_BINDING_BASE + storageBuffer.binding(),
                parsedBlock.getStages(), 16, List.of());
        Buffer buffer = storageBuffer.buffer();
        this.rangeBytes = storageBuffer.rangeBytes();
        getBufferSlice().set(buffer, 0, rangeBytes);
    }

    @Override
    public int getType() {
        return VK_DESCRIPTOR_TYPE_STORAGE_BUFFER_DYNAMIC;
    }

    @Override
    public int getSize() {
        return rangeBytes;
    }

    /** Storage descriptors must keep their owned slice instead of VulkanMod's global UBO buffer. */
    @Override
    public void setUseGlobalBuffer(boolean useGlobalBuffer) {
        super.setUseGlobalBuffer(false);
    }
}
