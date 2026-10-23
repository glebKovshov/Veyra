package dev.veyra.shader;

import net.vulkanmod.vulkan.memory.buffer.Buffer;
import net.vulkanmod.vulkan.shader.descriptor.UBO;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.lwjgl.vulkan.VK10.VK_BUFFER_USAGE_STORAGE_BUFFER_BIT;
import static org.lwjgl.vulkan.VK10.VK_DESCRIPTOR_TYPE_STORAGE_BUFFER_DYNAMIC;
import static org.lwjgl.vulkan.VK10.VK_SHADER_STAGE_VERTEX_BIT;

class VeyraStorageBufferUBOTest {
    @Test
    void exposesThePackBufferAsADynamicStorageDescriptor() {
        UBO parsedBlock = new UBO("VeyraStorageBuffer_0_fixture", 0, VK_SHADER_STAGE_VERTEX_BIT, 16, List.of());
        Buffer buffer = new Buffer("fixture", VK_BUFFER_USAGE_STORAGE_BUFFER_BIT, null);
        var allocation = new VeyraStorageBufferResources.StorageBuffer(0, buffer, 256, 256);

        VeyraStorageBufferUBO descriptor = new VeyraStorageBufferUBO(parsedBlock, allocation);

        assertEquals(VK_DESCRIPTOR_TYPE_STORAGE_BUFFER_DYNAMIC, descriptor.getType());
        assertEquals(ShaderPackStorageBuffers.VULKAN_DESCRIPTOR_BINDING_BASE, descriptor.getBinding());
        assertEquals(VK_SHADER_STAGE_VERTEX_BIT, descriptor.getStages());
        assertEquals(256, descriptor.getSize());
        assertEquals(256, descriptor.getBufferSlice().getSize());
        assertSame(buffer, descriptor.getBufferSlice().getBuffer());

        descriptor.setUseGlobalBuffer(true);

        assertFalse(descriptor.useGlobalBuffer());
        assertSame(buffer, descriptor.getBufferSlice().getBuffer());
    }
}
