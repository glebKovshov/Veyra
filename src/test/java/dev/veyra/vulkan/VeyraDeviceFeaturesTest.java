package dev.veyra.vulkan;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VeyraDeviceFeaturesTest {
    @Test
    void shadowVoxelWritesRequireBothVertexStoresAndExtendedImageFormats() {
        assertTrue(VeyraDeviceFeatures.supportsShadowVoxelWrites(true, true));
        assertFalse(VeyraDeviceFeatures.supportsShadowVoxelWrites(false, true));
        assertFalse(VeyraDeviceFeatures.supportsShadowVoxelWrites(true, false));
        assertFalse(VeyraDeviceFeatures.supportsShadowVoxelWrites(false, false));
    }
}
