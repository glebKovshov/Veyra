package dev.veyra.shader;

import net.vulkanmod.vulkan.shader.descriptor.UBO;
import dev.veyra.vulkan.VeyraStorageDescriptor;
import dev.veyra.vulkan.VeyraUniformBlocks;

/** Adapts VulkanMod's dynamic buffer descriptor update path to an Iris shader-storage buffer. */
public final class VeyraStorageBufferUBO extends VeyraStorageDescriptor {
    public VeyraStorageBufferUBO(UBO parsedBlock, VeyraStorageBufferResources.StorageBuffer storageBuffer) {
        super(VeyraUniformBlocks.description(parsedBlock),
                ShaderPackStorageBuffers.VULKAN_DESCRIPTOR_BINDING_BASE + storageBuffer.binding(),
                parsedBlock.getStages(), storageBuffer.buffer(), storageBuffer.rangeBytes());
    }
    public VeyraStorageBufferUBO(int stages, VeyraStorageBufferResources.StorageBuffer storageBuffer) {
        super("VeyraDirectStorageBuffer_" + storageBuffer.binding(),
                ShaderPackStorageBuffers.VULKAN_DESCRIPTOR_BINDING_BASE + storageBuffer.binding(),
                stages, storageBuffer.buffer(), storageBuffer.rangeBytes());
    }
}
