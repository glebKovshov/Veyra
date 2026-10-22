package dev.veyra.shader;

import java.io.IOException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EntityMainHandCompatibilityTest {
    private static final String HAND_SOURCE = """
            #version 120
            varying float isMainHand;
            void main() {
                gl_FragColor = vec4(isMainHand, 0.0, 0.0, 1.0);
            }
            """;

    @Test
    void detectsTheDeclaredHandInputButIgnoresComments() {
        assertTrue(EntityShaderAdapter.requiresMainHandInput(HAND_SOURCE));
        assertFalse(EntityShaderAdapter.requiresMainHandInput("// varying float isMainHand;\nvoid main() {}"));
    }

    @Test
    void detectsNamesAcrossMultilineCommaSeparatedVaryingDeclarations() {
        String source = "varying vec3 first,\n        second, isMainHand; // keep this comment\n"
                + "void main() {}\n";

        assertTrue(EntityShaderAdapter.requiresMainHandInput(source));
    }

    @Test
    void handlesLongCommaSeparatedVaryingLists() {
        StringBuilder source = new StringBuilder("varying vec3 ");
        for (int index = 0; index < 2048; index++) {
            if (index > 0) source.append(", ");
            source.append("varying").append(index);
        }
        source.append(", isMainHand;\nvoid main() {}\n");

        assertTrue(EntityShaderAdapter.requiresMainHandInput(source.toString()));
    }

    @Test
    void specializesTheHandInputForEachLogicalHand() throws IOException {
        String mainHand = EntityShaderAdapter.adaptFragment(HAND_SOURCE, "gbuffers_hand", true,
                false, -1, -1, ItemIdMap.UNMAPPED, true, true);
        String offHand = EntityShaderAdapter.adaptFragment(HAND_SOURCE, "gbuffers_hand", true,
                false, -1, -1, ItemIdMap.UNMAPPED, true, false);

        assertTrue(mainHand.contains("const float isMainHand = 1.0;"), mainHand);
        assertTrue(offHand.contains("const float isMainHand = 0.0;"), offHand);
        assertFalse(mainHand.contains("varying float isMainHand"), mainHand);
        assertFalse(mainHand.contains("in float isMainHand"), mainHand);
    }

    @Test
    void rejectsMainHandInputOutsideFirstPersonHandPrograms() {
        IOException error = assertThrows(IOException.class,
                () -> EntityShaderAdapter.adaptFragment(HAND_SOURCE, "gbuffers_entities"));
        assertTrue(error.getMessage().contains("available only in first-person hand programs"));
    }
}
