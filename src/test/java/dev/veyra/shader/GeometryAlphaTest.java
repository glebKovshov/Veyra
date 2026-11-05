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

    /** Photon packs light levels into the alpha of its own gbuffer output and runs the alpha test itself. */
    private static final String PACKED_OUTPUT = """
            #version 400 compatibility
            layout(location = 0) out vec4 gbuffer_data_0;
            /* RENDERTARGETS: 1 */
            uniform sampler2D gtexture;
            uniform float alphaTestRef;
            varying vec2 texcoord;
            void main() {
                vec4 base_color = texture(gtexture, texcoord);
                if (base_color.a < alphaTestRef) discard;
                gbuffer_data_0 = vec4(base_color.rgb, 0.02);
            }
            """;

    @Test
    void explicitOutputsKeepPackedAlphaLikeIris() throws Exception {
        for (String adapted : new String[] {
                TerrainShaderAdapter.adaptFragment(PACKED_OUTPUT), EntityShaderAdapter.adaptFragment(PACKED_OUTPUT)}) {
            assertFalse(adapted.contains("gbuffer_data_0.a < alphaTestRef"), adapted);
            assertTrue(adapted.contains("base_color.a < alphaTestRef"), adapted);
        }
    }

    @Test
    void entityCutoutsRetainAlphaTestingAfterReplacingMinecraftFragment() throws Exception {
        String adapted = EntityShaderAdapter.adaptFragment(SOURCE);
        String main = adapted.substring(adapted.lastIndexOf("void main()"));
        assertTrue(main.indexOf("veyraEntityPackMain();") < main.indexOf("discard;"), main);
        assertTrue(main.contains(".a < alphaTestRef) discard;"), main);
    }
}
