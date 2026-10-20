package dev.veyra.shader;

import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

class ColorDistanceShaderAdapterTest {
    @Test
    void adaptsLegacyMrtOutputsForNativeBasicAndLineStages() throws IOException {
        String adapted = ColorDistanceShaderAdapter.adaptFragment("""
                #version 120
                /* DRAWBUFFERS:06 */
                varying vec4 color;
                void main() {
                    gl_FragData[0] = color;
                    gl_FragData[1] = vec4(0.0, 1.0, 0.0, 1.0);
                }
                """, "gbuffers_basic", true);

        assertTrue(adapted.contains("VEYRA_ENTITY_OUTPUT_0"));
        assertTrue(adapted.contains("VEYRA_ENTITY_OUTPUT_1"));
        assertTrue(adapted.contains("veyraFragData0 = vertexColor;"));
        assertTrue(adapted.contains("veyraFragData1 = vec4(0.0, 1.0, 0.0, 1.0);"));
        assertFalse(adapted.contains("gl_FragData"), adapted);

        IOException error = assertThrows(IOException.class, () -> ColorDistanceShaderAdapter.adaptFragment("""
                /* RENDERTARGETS: 0,16 */
                void main() { gl_FragColor = vec4(1.0); }
                """, "gbuffers_basic", false));
        assertTrue(error.getMessage().contains("colortex0..15"));
    }
}
