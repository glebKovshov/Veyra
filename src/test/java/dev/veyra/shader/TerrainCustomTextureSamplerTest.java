package dev.veyra.shader;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TerrainCustomTextureSamplerTest {
    private static final String SOURCE = """
            #version 120
            uniform sampler2D gaux4;
            void main() {
                gl_FragColor = texture2D(gaux4, vec2(0.5));
            }
            """;

    @Test
    void preservesConfiguredPackTextureAsABoundTerrainSampler() throws Exception {
        String adapted = TerrainShaderAdapter.adaptFragment(
                SOURCE, false, false, Map.of(), false, "gbuffers_terrain", Set.of("gaux4"));

        assertTrue(adapted.contains("layout(binding = 5) uniform sampler2D gaux4;"));
        assertTrue(TerrainShaderAdapter.usedCustomTextureSamplers(SOURCE, Set.of("gaux4")).contains("gaux4"));
    }

    @Test
    void rejectsAnUnmappedPackTextureInsteadOfSilentlyBindingNothing() {
        assertThrows(java.io.IOException.class, () -> TerrainShaderAdapter.adaptFragment(
                SOURCE, false, false, Map.of(), false, "gbuffers_terrain"));
    }
}
