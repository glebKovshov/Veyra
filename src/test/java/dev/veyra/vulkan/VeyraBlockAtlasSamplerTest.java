package dev.veyra.vulkan;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.vulkan.VK10.*;

class VeyraBlockAtlasSamplerTest {
    @Test
    void keepsMagnifiedTexelsSharpWithoutDroppingMipmapsOrAnisotropy() {
        for (int levels : new int[]{1, 5}) {
            for (boolean anisotropic : new boolean[]{false, true}) {
                var sampler = VeyraBlockAtlasSampler.terrainSamplerInfo(levels, anisotropic, 8);
                assertEquals(VK_FILTER_NEAREST, sampler.getMagFilter());
                assertEquals(VK_FILTER_LINEAR, sampler.getMinFilter());
                assertEquals(VK_SAMPLER_MIPMAP_MODE_LINEAR, sampler.getMipmapMode());
                assertEquals(levels - 1, sampler.getMaxLod());
                assertEquals(anisotropic, sampler.getAnisotropy());
                assertEquals(8, sampler.getMaxAnisotropy());
                assertEquals(VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE, sampler.getAddressModeU());
                assertEquals(VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE, sampler.getAddressModeV());
                assertFalse(sampler.compareEnabled());
            }
        }
    }
}
