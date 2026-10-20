package dev.veyra.shader;

import java.io.IOException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EntityFragmentOutputsTest {
    @Test
    void preservesExplicitEntityOutputsAcrossMultipleColorTargets() throws IOException {
        String source = """
                #version 120
                /* RENDERTARGETS:0,12 */
                uniform float fogStart;
                layout(location = 0) out vec4 sceneColor;
                layout(location = 1) out vec4 materialColor;
                void main() {
                    sceneColor = vec4(0.2, 0.3, 0.4, 1.0);
                    materialColor = vec4(0.8, 0.1, 0.7, 1.0);
                }
                """;

        String adapted = EntityShaderAdapter.adaptFragment(source, "gbuffers_entities");

        assertTrue(adapted.contains("#define VEYRA_ENTITY_OUTPUT_0 layout(location = 0) out vec4 sceneColor;"), adapted);
        assertTrue(adapted.contains("#define VEYRA_ENTITY_OUTPUT_1 layout(location = 1) out vec4 materialColor;"), adapted);
        assertTrue(adapted.contains("\nVEYRA_ENTITY_OUTPUT_0\n"), adapted);
        assertTrue(adapted.contains("\nVEYRA_ENTITY_OUTPUT_1\n"), adapted);
        assertTrue(adapted.contains("sceneColor = vec4(0.2, 0.3, 0.4, 1.0);"), adapted);
        assertTrue(adapted.contains("materialColor = vec4(0.8, 0.1, 0.7, 1.0);"), adapted);
        assertEquals(1, occurrences(adapted, "#define VEYRA_ENTITY_OUTPUT_0 layout(location = 0) out vec4 sceneColor;"));
        assertEquals(1, occurrences(adapted, "#define VEYRA_ENTITY_OUTPUT_1 layout(location = 1) out vec4 materialColor;"));
        assertFalse(hasParserVisibleLayoutDeclaration(adapted), adapted);
    }

    @Test
    void mapsLegacyEntityFragmentDataToTheDeclaredDrawBuffers() throws IOException {
        String source = """
                #version 120
                /* DRAWBUFFERS:04 */
                void main() {
                    gl_FragData[0] = vec4(0.9, 0.0, 0.9, 1.0);
                    gl_FragData[1] = vec4(1.0, 0.0, 1.0, 1.0);
                }
                """;

        String adapted = EntityShaderAdapter.adaptFragment(source, "gbuffers_entities");

        assertTrue(adapted.contains("#define VEYRA_ENTITY_OUTPUT_0 layout(location = 0) out vec4 veyraFragData0;"), adapted);
        assertTrue(adapted.contains("#define VEYRA_ENTITY_OUTPUT_1 layout(location = 1) out vec4 veyraFragData1;"), adapted);
        assertTrue(adapted.contains("veyraFragData0 = vec4(0.9, 0.0, 0.9, 1.0);"), adapted);
        assertTrue(adapted.contains("veyraFragData1 = vec4(1.0, 0.0, 1.0, 1.0);"), adapted);
        assertFalse(adapted.contains("gl_FragData"), adapted);
        assertFalse(hasParserVisibleLayoutDeclaration(adapted), adapted);
    }

    @Test
    void keepsAnEntityColorOutputBehindTheVulkanModParser() throws IOException {
        String adapted = EntityShaderAdapter.adaptFragment("void main() { gl_FragColor = vec4(1.0); }",
                "gbuffers_entities");

        assertTrue(adapted.contains("#define VEYRA_ENTITY_OUTPUT_0 layout(location = 0) out vec4 veyraFragColor;"), adapted);
        assertTrue(adapted.contains("\nVEYRA_ENTITY_OUTPUT_0\n"), adapted);
        assertFalse(hasParserVisibleLayoutDeclaration(adapted), adapted);
    }

    @Test
    void keepsUnboundEntityDrawBufferSlotsPrivate() throws IOException {
        String source = "/* DRAWBUFFERS:0 */\nvoid main() { gl_FragData[1] = vec4(0.5); gl_FragData[0] = gl_FragData[1]; }";
        String adapted = EntityShaderAdapter.adaptFragment(source, "gbuffers_entities");

        assertTrue(adapted.contains("#define VEYRA_ENTITY_OUTPUT_0 layout(location = 0) out vec4 veyraFragData0;"), adapted);
        assertTrue(adapted.contains("vec4 veyraFragData1;"), adapted);
        assertFalse(adapted.contains("VEYRA_ENTITY_OUTPUT_1"), adapted);
        assertTrue(adapted.contains("veyraFragData0 = veyraFragData1;"), adapted);
        assertFalse(hasParserVisibleLayoutDeclaration(adapted), adapted);
    }

    @Test
    void rejectsEntityOutputsOutsideTheVulkanBridgeLimit() {
        String source = "layout(location = 8) out vec4 extra;\nvoid main() { extra = vec4(1); }";
        IOException error = assertThrows(IOException.class,
                () -> EntityShaderAdapter.adaptFragment(source, "gbuffers_entities"));
        assertTrue(error.getMessage().contains("output location 8"), error.getMessage());
    }

    private static int occurrences(String text, String needle) {
        int count = 0;
        for (int at = 0; (at = text.indexOf(needle, at)) >= 0; at += needle.length()) count++;
        return count;
    }

    private static boolean hasParserVisibleLayoutDeclaration(String source) {
        return source.lines().map(String::strip)
                .anyMatch(line -> line.startsWith("layout(location") && !line.startsWith("#define "));
    }
}
