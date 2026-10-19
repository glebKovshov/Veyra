package dev.veyra.vulkan;

import org.junit.jupiter.api.Test;
import org.lwjgl.vulkan.VK10;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VeyraColorTextureSnapshotTest {
    @Test
    void mapsBgraSwapchainFormatsToSupportedRgbaFormats() {
        assertEquals(VK10.VK_FORMAT_R8G8B8A8_UNORM,
                VeyraColorTextureSnapshot.sampledFormat(VK10.VK_FORMAT_B8G8R8A8_UNORM));
        assertEquals(VK10.VK_FORMAT_R8G8B8A8_SRGB,
                VeyraColorTextureSnapshot.sampledFormat(VK10.VK_FORMAT_B8G8R8A8_SRGB));
    }

    @Test
    void preservesFormatsAlreadySupportedByTheImageBuilder() {
        assertEquals(VK10.VK_FORMAT_R8G8B8A8_UNORM,
                VeyraColorTextureSnapshot.sampledFormat(VK10.VK_FORMAT_R8G8B8A8_UNORM));
    }
}
