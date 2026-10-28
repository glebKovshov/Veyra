package dev.veyra.shader;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GeometryAlphaTest {
    private static final String SOURCE = """
            #version 120
            uniform sampler2D texture;
            varying vec2 texcoord;
            void main() {
                gl_FragColor = texture2D(texture, texcoord);
                if (gl_FragColor.a < 0.01) return;
            }
            """;

    @Test
    void terrainDiscardsTransparentFragmentsAfterPackEarlyReturns() throws Exception {
        String adapted = TerrainShaderAdapter.adaptFragment(SOURCE);
        int wrapper = adapted.lastIndexOf("void main()");
        assertTrue(wrapper > 0);
        String main = adapted.substring(wrapper);
        assertTrue(main.indexOf("veyraShaderPackMain();") < main.indexOf("discard;"), main);
        assertTrue(main.contains(".a < alphaTestRef) discard;"), main);
    }

    @Test
    void entityCutoutsRetainAlphaTestingAfterReplacingMinecraftFragment() throws Exception {
        String adapted = EntityShaderAdapter.adaptFragment(SOURCE);
        String main = adapted.substring(adapted.lastIndexOf("void main()"));
        assertTrue(main.indexOf("veyraEntityPackMain();") < main.indexOf("discard;"), main);
        assertTrue(main.contains(".a < alphaTestRef) discard;"), main);
    }
}
