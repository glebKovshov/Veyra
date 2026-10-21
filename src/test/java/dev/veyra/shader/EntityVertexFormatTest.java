package dev.veyra.shader;

import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EntityVertexFormatTest {
    @Test
    void adaptsMinecraftItemEntityOverlayInputToNewEntityFormat() throws IOException {
        String source = """
                #version 330
                in vec2 UV1;
                out vec2 texCoord1;
                void main() {
                    texCoord1 = UV1;
                }
                """;

        String adapted = EntityShaderAdapter.adaptItemEntityVertexFormat(source, "gbuffers_hand");

        assertTrue(adapted.contains("in ivec2 UV1;"), adapted);
        assertTrue(adapted.contains("texCoord1 = vec2(UV1);"), adapted);
        assertEquals(1, adapted.split("in ivec2 UV1;", -1).length - 1);
    }

    @Test
    void acceptsItemEntityShaderAlreadyUsingIntegerOverlayInput() throws IOException {
        String source = "#version 330\nin ivec2 UV1;\nvoid main() { }\n";

        assertEquals(source, EntityShaderAdapter.adaptItemEntityVertexFormat(source, "gbuffers_hand"));
    }

    @Test
    void reportsUnexpectedItemEntityOverlayInput() {
        IOException error = assertThrows(IOException.class, () ->
                EntityShaderAdapter.adaptItemEntityVertexFormat("#version 330\nvoid main() { }\n", "gbuffers_hand"));

        assertTrue(error.getMessage().contains("UV1's shader input changed."));
    }

    @Test
    void adaptsCrumblingOverlayInputToItsIntegerVertexFormat() throws IOException {
        String source = """
                #version 330
                in vec2 UV1;
                out vec2 texCoord1;
                void main() {
                    texCoord1 = UV1;
                }
                """;

        String adapted = EntityShaderAdapter.adaptOverlayVertexFormat(source, "gbuffers_damagedblock");

        assertTrue(adapted.contains("in ivec2 UV1;"), adapted);
        assertTrue(adapted.contains("texCoord1 = vec2(UV1);"), adapted);
    }
}
