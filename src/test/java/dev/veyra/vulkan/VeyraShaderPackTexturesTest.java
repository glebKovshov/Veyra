package dev.veyra.vulkan;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VeyraShaderPackTexturesTest {
    @Test
    void resolvesAdaptedPostColorTargetSamplerNames() {
        assertEquals("VeyraColortex3Sampler",
                VeyraShaderPackTextures.adaptedSamplerName("deferred1_colortex0", "colortex3"));
        assertEquals("InSampler", VeyraShaderPackTextures.adaptedSamplerName("final", "colortex0"));
        assertEquals("colortex3", VeyraShaderPackTextures.adaptedSamplerName("gbuffers_terrain", "colortex3"));
        assertEquals("gaux4", VeyraShaderPackTextures.adaptedSamplerName("deferred1", "gaux4"));
        assertEquals("colortex16",
                VeyraShaderPackTextures.adaptedSamplerName("deferred1", "colortex16"));
    }
}
