package dev.veyra.shader;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SignTextVertexShaderAdapterTest {
    private static final String NATIVE_TEXT_VERTEX = """
            #version 460
            layout(location=0) in vec3 Position;
            layout(location=1) in vec4 Color;
            layout(location=2) in vec2 UV0;
            layout(location=3) in ivec2 UV2;
            layout(location=0) out vec4 vertexColor;
            layout(location=1) out float sphericalVertexDistance;
            layout(location=2) out float cylindricalVertexDistance;
            layout(location=3) out vec2 texCoord0;
            layout(set=0,binding=0,std140) uniform NativeTransforms {
                mat4 ModelViewMat;
                mat4 ProjMat;
                mat4 TextureMat;
            };
            uniform sampler2D Sampler2;
            void main() {
                gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
                sphericalVertexDistance = length(Position);
                cylindricalVertexDistance = length(Position.xz);
                vertexColor = Color * texelFetch(Sampler2, UV2 / 16, 0);
                texCoord0 = UV0;
            }
            """;
    private static final String NATIVE_TEXT_FULLBRIGHT_VERTEX = """
            #version 460
            layout(location=0) in vec3 Position;
            layout(location=1) in vec4 Color;
            layout(location=2) in vec2 UV0;
            layout(location=0) out vec4 vertexColor;
            layout(location=1) out float sphericalVertexDistance;
            layout(location=2) out float cylindricalVertexDistance;
            layout(location=3) out vec2 texCoord0;
            layout(set=0,binding=0,std140) uniform NativeTransforms {
                mat4 ModelViewMat;
                mat4 ProjMat;
                mat4 TextureMat;
            };
            void main() {
                gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
                sphericalVertexDistance = length(Position);
                cylindricalVertexDistance = length(Position.xz);
                vertexColor = Color;
                texCoord0 = UV0;
            }
            """;
    private static final String PACK_VERTEX = """
            #version 120
            uniform int renderStage;
            attribute vec4 mc_Entity;
            attribute vec4 mc_midTexCoord;
            varying vec4 color;
            varying vec4 texcoord;
            varying vec4 normal;
            void main() {
                gl_Position = ftransform();
                color = gl_Color;
                texcoord.xy = (gl_TextureMatrix[0] * gl_MultiTexCoord0).xy;
                texcoord.zw = (gl_TextureMatrix[1] * gl_MultiTexCoord1).xy;
                normal = vec4(normalize(gl_NormalMatrix * gl_Normal), mc_Entity.x + mc_midTexCoord.x + renderStage);
            }
            """;

    @Test
    void linksTexturedPackVertexInputsAndGlyphNormalToSignTextFragment() throws IOException {
        BasicVertexShaderAdapter.AdaptedStage vertex = BasicVertexShaderAdapter.adaptStage(
                NATIVE_TEXT_VERTEX, PACK_VERTEX, "gbuffers_textured", BasicVertexShaderAdapter.Format.SIGN_TEXT);

        assertTrue(vertex.source().contains("#define gl_Normal vec3(0.0, 0.0, 1.0)"));
        assertTrue(vertex.source().contains("#define gl_MultiTexCoord1 vec4(vec2(UV2), 0.0, 1.0)"));
        assertTrue(vertex.source().contains("const int renderStage = 12;"));
        assertTrue(vertex.source().contains("#define mc_Entity vec4(0.0, 0.0, 0.0, 1.0)"));
        assertTrue(vertex.source().contains("#define mc_midTexCoord vec4(0.0, 0.0, 0.0, 1.0)"));
        var normal = vertex.varyings().get("normal");
        assertNotNull(normal);
        assertEquals("vec4", normal.type());

        String fragment = EntityShaderAdapter.adaptTextFragment("""
                #version 120
                uniform sampler2D texture;
                varying vec4 color;
                varying vec4 texcoord;
                varying vec4 normal;
                void main() {
                    gl_FragColor = texture2D(texture, texcoord.xy) * color * (normal.a + normal.z * 0.01);
                }
                """, "gbuffers_textured", false, -1, -1, ItemIdMap.UNMAPPED,
                Set.of(), vertex.varyings());

        assertTrue(fragment.contains("layout(location = " + normal.location() + ") in vec4 veyraEntityPackInput_"),
                fragment);
        assertTrue(fragment.contains("layout(location = 0) in vec4 veyraEntityPackInput_0;"), fragment);
        assertFalse(fragment.contains("dFdx(viewPos)"), fragment);
        assertFalse(fragment.contains("in vec3 viewPos;"), fragment);
    }

    @Test
    void adaptsSeeThroughTextWithoutUv2AsFullBright() throws IOException {
        assertEquals(BasicVertexShaderAdapter.Format.SIGN_TEXT,
                BasicVertexShaderAdapter.signTextFormat(NATIVE_TEXT_VERTEX));
        assertEquals(BasicVertexShaderAdapter.Format.SIGN_TEXT_FULLBRIGHT,
                BasicVertexShaderAdapter.signTextFormat(NATIVE_TEXT_FULLBRIGHT_VERTEX));
        BasicVertexShaderAdapter.AdaptedStage vertex = BasicVertexShaderAdapter.adaptStage(
                NATIVE_TEXT_FULLBRIGHT_VERTEX, PACK_VERTEX, "gbuffers_textured",
                BasicVertexShaderAdapter.Format.SIGN_TEXT_FULLBRIGHT);

        assertFalse(vertex.source().contains("in ivec2 UV2"), vertex.source());
        assertTrue(vertex.source().contains(
                "#define gl_MultiTexCoord1 vec4(240.0, 240.0, 0.0, 1.0)"), vertex.source());
        assertTrue(vertex.source().contains("#define gl_Normal vec3(0.0, 0.0, 1.0)"), vertex.source());
        assertEquals(12, IrisRenderStages.forSkyFormat(BasicVertexShaderAdapter.Format.SIGN_TEXT_FULLBRIGHT));
    }
}
