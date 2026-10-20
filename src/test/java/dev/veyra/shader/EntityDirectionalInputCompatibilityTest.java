package dev.veyra.shader;

import java.io.IOException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EntityDirectionalInputCompatibilityTest {
    private static final String BSL_STYLE_HAND = """
            #version 120
            uniform mat4 gbufferModelView;
            varying vec2 texCoord, lmCoord;
            varying vec3 sunVec, upVec, eastVec;
            void main() {
                gl_FragColor = vec4(normalize(sunVec) + normalize(upVec) + normalize(eastVec), 1.0);
            }
            """;

    @Test
    void bridgesCommaSeparatedBslDirectionInputsToLiveIrisUniforms() throws IOException {
        String adapted = EntityShaderAdapter.adaptFragment(BSL_STYLE_HAND, "gbuffers_hand", true,
                false, -1, -1, ItemIdMap.UNMAPPED, true, false);

        assertTrue(adapted.contains("uniform vec3 sunPosition;"), adapted);
        assertTrue(adapted.contains("uniform mat4 gbufferModelView;"), adapted);
        assertTrue(adapted.contains("#define sunVec normalize(sunPosition)"), adapted);
        assertTrue(adapted.contains("#define upVec normalize(gbufferModelView[1].xyz)"), adapted);
        assertTrue(adapted.contains("#define eastVec normalize(gbufferModelView[0].xyz)"), adapted);
        assertFalse(adapted.contains("varying vec3 sunVec"), adapted);
        assertFalse(adapted.contains("in vec3 eastVec"), adapted);
        assertFalse(adapted.contains("varying vec2 texCoord, lmCoord"), adapted);
    }

    @Test
    void reportsUnsupportedMembersOfCommaSeparatedInputDeclarationsPrecisely() {
        String source = "varying vec3 sunVec, unsupportedDirection;\nvoid main() {}";
        IOException error = assertThrows(IOException.class,
                () -> EntityShaderAdapter.adaptFragment(source, "gbuffers_entities"));
        assertTrue(error.getMessage().contains("unsupportedDirection"), error.getMessage());
    }
}
