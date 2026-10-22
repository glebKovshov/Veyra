package dev.veyra.shader;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UnusedSamplerDeclarationsTest {
    @Test
    void removesOnlyUnusedSamplerDeclarations() {
        String source = """
                uniform sampler2D usedSampler;
                uniform sampler2D unusedSampler;
                void main() { vec4 color = texture(usedSampler, vec2(0.5)); }
                """;

        String adapted = UnusedSamplerDeclarations.removeUnused(source);

        assertTrue(adapted.contains("uniform sampler2D usedSampler;"), adapted);
        assertFalse(adapted.contains("uniform sampler2D unusedSampler;"), adapted);
        assertTrue(adapted.contains("texture(usedSampler"), adapted);
    }

    @Test
    void parsesMultilineLayoutAndPrecisionQualifiers() {
        String source = """
                layout(binding = 4)
                uniform highp sampler2D unusedSampler;
                void main() { }
                """;

        String adapted = UnusedSamplerDeclarations.removeUnused(source);

        assertFalse(adapted.contains("uniform highp sampler2D unusedSampler;"), adapted);
        assertTrue(adapted.contains("void main()"), adapted);
    }

    @Test
    void ignoresSamplerDeclarationsAndCommentsWhenCheckingReferences() {
        String declarationOnly = """
                uniform sampler2D shadowtex1;
                // shadowtex1 is not executable use
                void main() { }
                """;

        assertFalse(UnusedSamplerDeclarations.isReferenced(declarationOnly, "shadowtex1"));
        assertTrue(UnusedSamplerDeclarations.isReferenced(
                declarationOnly + "\nvoid helper() { texture(shadowtex1, vec2(0.5)); }", "shadowtex1"));
    }

    @Test
    void referenceCheckUsesWholeIdentifier() {
        String source = "uniform sampler2D shadowtex1;\nvoid main() { vec4 x = texture(shadowtex10, vec2(0.0)); }";

        assertFalse(UnusedSamplerDeclarations.isReferenced(source, "shadowtex1"));
    }
}
