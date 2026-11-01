package dev.veyra.vulkan;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VulkanMipExtentTest {
    @Test
    void fullChainsReachOneTexelForRectangularAndOddImages() {
        for (int[] size : new int[][]{{1, 1}, {1, 8}, {9, 5}, {2560, 1440}}) {
            int levels = VulkanMipExtent.levels(size[0], size[1]);
            assertEquals(1, VulkanMipExtent.atLevel(size[0], levels - 1));
            assertEquals(1, VulkanMipExtent.atLevel(size[1], levels - 1));
            if (levels > 1) assertTrue(
                    Math.max(VulkanMipExtent.atLevel(size[0], levels - 2),
                            VulkanMipExtent.atLevel(size[1], levels - 2)) > 1);
        }
        assertThrows(IllegalArgumentException.class, () -> VulkanMipExtent.levels(0, 1));
    }

    @Test
    void computesDimensionsForAtlasMipLevels() {
        assertEquals(2048, VulkanMipExtent.atLevel(2048, 0));
        assertEquals(1024, VulkanMipExtent.atLevel(2048, 1));
        assertEquals(512, VulkanMipExtent.atLevel(2048, 2));
        assertEquals(256, VulkanMipExtent.atLevel(2048, 3));
        assertEquals(128, VulkanMipExtent.atLevel(2048, 4));
    }

    @Test
    void clampsSmallExtentsAndLargeLevelsToOneTexel() {
        assertEquals(1, VulkanMipExtent.atLevel(3, 2));
        assertEquals(1, VulkanMipExtent.atLevel(2048, Integer.MAX_VALUE));
    }

    @Test
    void rejectsInvalidInputs() {
        assertThrows(IllegalArgumentException.class, () -> VulkanMipExtent.atLevel(0, 0));
        assertThrows(IllegalArgumentException.class, () -> VulkanMipExtent.atLevel(32, -1));
    }
}
