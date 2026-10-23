package dev.veyra.shader;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class UnusedVaryingDeclarationsTest {
    @Test
    void removesUnusedInputsAndPreservesReferencedInputs() {
        String source = "layout(location = 3) flat in vec3 used;\n"
                + "centroid varying vec2 unused; // comment-only reference: unused\n"
                + "void main() { gl_FragColor = vec4(used, 1.0); }\n";

        String adapted = UnusedVaryingDeclarations.removeUnused(source);

        assertTrue(adapted.contains("flat in vec3 used;"), adapted);
        assertFalse(adapted.contains("vec2 unused"), adapted);
    }

    @Test
    void parsesMultilineLayoutAndInterpolationQualifiers() {
        String source = "layout(\n  location = 7\n) flat centroid in vec2 unused;\n"
                + "void main() {}\n";

        String adapted = UnusedVaryingDeclarations.removeUnused(source);

        assertFalse(adapted.contains("vec2 unused"), adapted);
        assertTrue(adapted.contains("void main() {}"), adapted);
    }

    @Test
    void skipsBlankLinesWithoutRescanningTheRemainingSource() {
        String source = "\n".repeat(60_000) + "void main() {}\n";

        assertTimeoutPreemptively(java.time.Duration.ofSeconds(2), () ->
                assertEquals(source, UnusedVaryingDeclarations.removeUnused(source)));
    }

    @Test
    void handlesManyDeclarationsWithoutRegexBacktracking() {
        StringBuilder source = new StringBuilder("in vec3 used;\n");
        for (int index = 0; index < 2048; index++) {
            source.append("layout(location = ").append(index).append(") smooth in vec2 unused")
                    .append(index).append(";\n");
        }
        source.append("void main() { gl_FragColor = vec4(used, 1.0); }\n");

        String adapted = UnusedVaryingDeclarations.removeUnused(source.toString());

        assertTrue(adapted.contains("in vec3 used;"), adapted);
        assertFalse(adapted.contains("vec2 unused0;"), adapted);
        assertFalse(adapted.contains("vec2 unused2047;"), adapted);
    }
}
