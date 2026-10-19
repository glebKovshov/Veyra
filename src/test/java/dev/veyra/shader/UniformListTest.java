package dev.veyra.shader;

import java.io.IOException;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class UniformListTest {
    @Test
    void splitsDeclaratorListsIntoSingleDeclarations() {
        String split = GlslPreprocessor.splitUniformLists(
                "  uniform float near, far;\nuniform highp int heldBlockLightValue ,heldBlockLightValue2;\nfloat a, b;\n");
        assertEquals("  uniform float near;\n  uniform float far;\nuniform highp int heldBlockLightValue;\n"
                + "uniform highp int heldBlockLightValue2;\nfloat a, b;\n", split);
    }

    @Test
    void preprocessedBslStyleListsReachTheTerrainBridge() throws IOException {
        String source = GlslPreprocessor.process("gbuffers_terrain.fsh", """
                #version 120
                uniform float near, far;
                uniform float viewWidth, viewHeight;
                uniform int heldBlockLightValue, heldBlockLightValue2;
                void main() { gl_FragColor = vec4(near + far + viewWidth / viewHeight + float(heldBlockLightValue2)); }
                """);
        String adapted = TerrainShaderAdapter.adaptFragment(source);
        assertTrue(adapted.contains("    float far;"));
        assertTrue(adapted.contains("    int heldBlockLightValue2;"));
        assertFalse(adapted.contains("uniform float near"));
    }
}
