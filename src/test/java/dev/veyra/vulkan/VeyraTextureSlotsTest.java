package dev.veyra.vulkan;

import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class VeyraTextureSlotsTest {
    @Test
    void capacityRespectsTheVulkanDeviceAndVeyraLimits() {
        assertEquals(0, VeyraTextureSlots.effectiveCapacity(0));
        assertEquals(16, VeyraTextureSlots.effectiveCapacity(16));
        assertEquals(VeyraTextureSlots.CAPACITY, VeyraTextureSlots.effectiveCapacity(64));
    }

    @Test
    void rejectsOnlyPipelinesBeyondTheSelectedDeviceLimit() throws IOException {
        VeyraTextureSlots.validateSamplerCount(16, 16, "post-composite");
        IOException failure = assertThrows(IOException.class,
                () -> VeyraTextureSlots.validateSamplerCount(17, 16, "post-composite"));
        assertEquals("post-composite needs 17 texture descriptors; this Vulkan device supports at most 16 "
                + "(Veyra's slot capacity is 32).", failure.getMessage());
    }
}
