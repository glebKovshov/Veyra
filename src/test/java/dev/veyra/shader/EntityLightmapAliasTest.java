package dev.veyra.shader;

import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EntityLightmapAliasTest {
    private static final String SOURCE = """
            #version 120
            varying vec2 lmCoord;
            void main() { gl_FragColor = vec4(lmCoord, 0.0, 1.0); }
            """;

    @Test
    void recognizesIrisLmCoordAsTheLightmapInput() {
        assertTrue(EntityShaderAdapter.requiresLightmapInput(SOURCE));
    }

    @Test
    void mapsIrisLmCoordToVeyrasEntityLightmapVarying() throws IOException {
        String adapted = EntityShaderAdapter.adaptFragment(SOURCE, "gbuffers_entities");

        assertTrue(adapted.contains("in vec2 lmcoord;"), adapted);
        assertTrue(adapted.contains("lmcoord"), adapted);
        assertFalse(adapted.contains("lmCoord"), adapted);
    }

    @Test
    void localViewPositionVariableIsNotMistakenForAnUndeclaredFragmentInput() throws IOException {
        String source = """
                #version 120
                void main() {
                    vec3 viewPos = vec3(1.0);
                    gl_FragColor = vec4(viewPos, 1.0);
                }
                """;

        String adapted = EntityShaderAdapter.adaptFragment(source, "gbuffers_entities");

        assertFalse(adapted.contains("in vec3 viewPos;"), adapted);
    }

    @Test
    void undeclaredViewPositionInputIsStillRejected() {
        String source = """
                #version 120
                void main() { gl_FragColor = vec4(viewPos, 1.0); }
                """;

        IOException exception = assertThrows(IOException.class,
                () -> EntityShaderAdapter.adaptFragment(source, "gbuffers_entities"));
        assertTrue(exception.getMessage().contains("viewPos"), exception.getMessage());
    }
}
