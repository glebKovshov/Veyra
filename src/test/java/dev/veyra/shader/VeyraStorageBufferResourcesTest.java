package dev.veyra.shader;

import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class VeyraStorageBufferResourcesTest {
    @Test
    void interpretsVulkanUintRangeAndCapsItToVulkanModDescriptorRange() throws IOException {
        assertEquals(128L * 1024 * 1024,
                VeyraStorageBufferResources.descriptorMaxStorageBufferRange(128 * 1024 * 1024));
        assertEquals(Integer.MAX_VALUE,
                VeyraStorageBufferResources.descriptorMaxStorageBufferRange(Integer.MAX_VALUE));
        assertEquals(Integer.MAX_VALUE,
                VeyraStorageBufferResources.descriptorMaxStorageBufferRange(-1));
        assertEquals(Integer.MAX_VALUE,
                VeyraStorageBufferResources.descriptorMaxStorageBufferRange(Integer.MIN_VALUE));
    }

    @Test
    void rejectsADeviceThatReportsNoStorageBufferRange() {
        IOException failure = assertThrows(IOException.class,
                () -> VeyraStorageBufferResources.descriptorMaxStorageBufferRange(0));
        assertEquals("VulkanMod reports an unusable maxStorageBufferRange of 0.", failure.getMessage());
    }
}
