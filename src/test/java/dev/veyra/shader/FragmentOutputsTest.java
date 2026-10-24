package dev.veyra.shader;

import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class FragmentOutputsTest {
    @Test void mapsLegacyOutputLocationsToDifferentPhysicalTargets() throws IOException {
        String source = "/* DRAWBUFFERS:041 */\nvoid main(){gl_FragData[0]=vec4(1);gl_FragData[2]=vec4(0);}";
        FragmentOutputs outputs = FragmentOutputs.parse(source);
        assertEquals(List.of(0, 4, 1), outputs.targets());
        assertTrue(outputs.writes(0)); assertFalse(outputs.writes(1)); assertTrue(outputs.writes(2));
        assertTrue(outputs.rewriteLegacy(source).contains("veyraFragData2=vec4(0)"));
        assertTrue(outputs.declarations().contains("layout(location = 2) out vec4 veyraFragData2;"));
    }
    @Test void preservesExplicitOutputsAndMultiDigitRenderTargets() throws IOException {
        String source = "/* RENDERTARGETS:0,12 */\nlayout(location=0) out vec4 scene;\nlayout(location=1) out vec4 material;\nvoid main(){scene=vec4(1);material=vec4(0);}";
        var outputs = FragmentOutputs.parse(source);
        assertEquals(List.of(0, 12), outputs.targets());
        String adapted = TerrainShaderAdapter.adaptFragment(source);
        assertTrue(adapted.contains("layout(location = 0) out vec4 scene;"));
        assertTrue(adapted.contains("layout(location = 1) out vec4 material;"));
        assertFalse(adapted.contains("out vec4 veyraFragColor;"));
    }
    @Test void defaultTargetsFollowOutputLocations() throws IOException {
        var outputs = FragmentOutputs.parse("void main(){gl_FragData[0]=vec4(1);gl_FragData[1]=vec4(0);}");
        assertEquals(List.of(0, 1), outputs.targets());
        assertFalse(outputs.sceneOnly());
        assertTrue(FragmentOutputs.parse("void main(){gl_FragColor=vec4(1);}").sceneOnly());
    }
    @Test void doesNotRewriteCommentedOutputsOrReadLineCommentDirectives() throws IOException {
        String source = "// /* RENDERTARGETS:9 */\n/* gl_FragData[7]=vec4(0); */\nvoid main(){gl_FragColor=vec4(1);}";
        var outputs = FragmentOutputs.parse(source);
        assertTrue(outputs.sceneOnly());
        assertEquals(source, outputs.rewriteLegacy(source));
    }
    @Test void rejectsAmbiguousOrUnavailableAttachments() {
        for (String source : List.of(
                "/* RENDERTARGETS:0,0 */\nvoid main(){gl_FragData[0]=vec4(1);}",
                "/* RENDERTARGETS:16 */\nvoid main(){gl_FragColor=vec4(1);}",
                "void main(){int i=1;gl_FragData[i]=vec4(1);}",
                "layout(location=0) out vec4 color;\nvoid main(){gl_FragData[0]=vec4(1);}"))
            assertThrows(IOException.class, () -> FragmentOutputs.parse(source), source);
    }
    @Test void preservesDataflowForOutputsNotBoundByDrawBuffers() throws IOException {
        String source = "/* DRAWBUFFERS:0 */\nvoid main(){gl_FragData[1]=vec4(0.5);gl_FragData[0]=gl_FragData[1];}";
        var outputs = FragmentOutputs.parse(source);
        assertTrue(outputs.sceneOnly());
        assertTrue(outputs.declarations().contains("vec4 veyraFragData1;"));
        assertFalse(outputs.declarations().contains("layout(location = 1)"));
        String adapted = TerrainShaderAdapter.adaptFragment(source);
        assertTrue(adapted.contains("veyraFragData0=veyraFragData1"));
    }

    @Test void usesLastActiveProfileSpecificTargetMapping() throws IOException {
        String source = """
                #version 120
                #define BLOCK_REFLECT_QUALITY 3
                #define RP_MODE 1
                /* DRAWBUFFERS:06 */
                #if BLOCK_REFLECT_QUALITY >= 2 && RP_MODE != 0
                    /* DRAWBUFFERS:064 */
                #endif
                void main() {
                    gl_FragData[0] = vec4(1.0);
                    gl_FragData[1] = vec4(1.0);
                    gl_FragData[2] = vec4(1.0);
                }
                """;

        FragmentOutputs outputs = FragmentOutputs.parse(source);
        assertEquals(List.of(0, 6, 4), outputs.targets());
        assertTrue(outputs.declarations().contains("layout(location = 2) out vec4 veyraFragData2;"));
    }
}
