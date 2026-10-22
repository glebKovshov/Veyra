package dev.veyra.shader;

import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

class BeaconBeamVertexShaderAdapterTest {
    private static final String NATIVE_VERTEX = """
            #version 330
            in vec3 Position;
            in vec4 Color;
            in vec2 UV0;
            out vec4 vertexColor;
            out vec2 texCoord0;
            uniform mat4 ProjMat;
            uniform mat4 ModelViewMat;
            uniform mat4 TextureMat;
            void main() {
                gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
                vertexColor = Color;
                texCoord0 = (TextureMat * vec4(UV0, 0.0, 1.0)).xy;
            }
            """;

    @Test
    void adaptsComplementaryStyleFlatVaryingsAndLegacyBeaconInputs() throws IOException {
        BeaconBeamVertexShaderAdapter.AdaptedStage adapted = BeaconBeamVertexShaderAdapter.adaptStage(
                NATIVE_VERTEX, """
                        #version 130
                        out vec2 texCoord;
                        flat out vec3 upVec, sunVec;
                        out vec4 glColor;
                        uniform mat4 gbufferModelView;
                        void main() {
                            gl_Position = ftransform();
                            texCoord = (gl_TextureMatrix[0] * gl_MultiTexCoord0).xy;
                            upVec = normalize(gbufferModelView[1].xyz);
                            sunVec = normalize(vec3(0.3, 0.8, 0.2));
                            glColor = gl_Color;
                        }
                        """, "gbuffers_beaconbeam");

        assertTrue(adapted.source().contains("veyraBeaconShaderMain();"));
        assertTrue(adapted.source().contains("void veyraBeaconShaderMain()"));
        assertTrue(adapted.source().contains("#define gl_Color Color"));
        assertTrue(adapted.source().contains("#define gl_MultiTexCoord0 vec4(UV0, 0.0, 1.0)"));
        assertTrue(adapted.source().contains("#define glColor vertexColor"));
        assertTrue(adapted.source().contains("uniform mat4 gbufferModelView;"));
        assertTrue(adapted.source().contains("layout(location = 2) flat out vec3 veyraBeaconVarying_upVec;"));
        assertTrue(adapted.source().contains("layout(location = 3) flat out vec3 veyraBeaconVarying_sunVec;"));
        assertEquals(2, adapted.varyings().get("upVec").location());
        assertEquals("flat", adapted.varyings().get("sunVec").interpolation());
        assertEquals(0, adapted.varyings().get("glColor").location());
        assertEquals(1, adapted.varyings().get("texCoord").location());
    }

    @Test
    void linksBeaconFragmentVaryingsAndMrtTargets() throws IOException {
        BeaconBeamVertexShaderAdapter.AdaptedStage vertex = BeaconBeamVertexShaderAdapter.adaptStage(
                NATIVE_VERTEX, """
                        #version 130
                        out vec2 texCoord;
                        flat out vec3 upVec, sunVec;
                        out vec4 glColor;
                        void main() {
                            gl_Position = ftransform();
                            texCoord = gl_MultiTexCoord0.xy;
                            upVec = vec3(0.0, 1.0, 0.0);
                            sunVec = vec3(1.0, 0.0, 0.0);
                            glColor = gl_Color;
                        }
                        """, "gbuffers_beaconbeam");
        String fragment = TextureColorDistanceShaderAdapter.adaptFragment("""
                #version 130
                /* DRAWBUFFERS:06 */
                in vec2 texCoord;
                flat in vec3 upVec, sunVec;
                in vec4 glColor;
                uniform sampler2D tex;
                void main() {
                    float signal = clamp(0.75 + 0.1 * abs(upVec.y) + 0.05 * abs(sunVec.x)
                            + 0.05 * texCoord.x + 0.05 * glColor.r, 0.0, 1.0);
                    gl_FragData[0] = vec4(0.0, signal, 0.0, 1.0);
                    gl_FragData[1] = vec4(1.0, 0.0, 1.0, 1.0);
                }
                """, "gbuffers_beaconbeam", vertex.varyings(), false, true);

        assertTrue(fragment.contains("layout(location = 0) out vec4 veyraFragData0;"));
        assertTrue(fragment.contains("layout(location = 1) out vec4 veyraFragData1;"));
        assertTrue(fragment.contains("layout(location = 2) flat in vec3 upVec;"));
        assertTrue(fragment.contains("layout(location = 3) flat in vec3 sunVec;"));
        assertTrue(fragment.contains("layout(location = 0) in vec4 vertexColor;"));
        assertTrue(fragment.contains("layout(location = 1) in vec2 texCoord0;"));
        assertTrue(fragment.contains("#define tex Sampler0"));
        assertEquals(java.util.List.of(0, 6), FragmentOutputs.parse("/* DRAWBUFFERS:06 */\nvoid main(){}").targets());
    }

    @Test
    void suppliesFogBlockWhenNativeBeaconShaderHasNoFogImport() throws IOException {
        String adapted = BeaconBeamVertexShaderAdapter.adaptStage(NATIVE_VERTEX, """
                #version 130
                uniform vec3 fogColor;
                void main() {
                    gl_Position = ftransform();
                    gl_Position.x += fogColor.x;
                }
                """, "gbuffers_beaconbeam").source();

        assertTrue(adapted.contains("layout(std140) uniform Fog"), adapted);
        assertTrue(adapted.contains("#define fogColor (FogColor.rgb)"), adapted);
    }

    @Test
    void reusesNativeFogBlockWhenBeaconShaderImportsIt() throws IOException {
        String minecraftWithFog = NATIVE_VERTEX.replace("#version 330",
                "#version 330\n#moj_import <minecraft:fog.glsl>");
        String adapted = BeaconBeamVertexShaderAdapter.adaptStage(minecraftWithFog, """
                #version 130
                uniform vec3 fogColor;
                void main() {
                    gl_Position = ftransform();
                    gl_Position.x += fogColor.x;
                }
                """, "gbuffers_beaconbeam").source();

        assertFalse(adapted.contains("layout(std140) uniform Fog"), adapted);
        assertTrue(adapted.contains("#moj_import <minecraft:fog.glsl>"), adapted);
        assertTrue(adapted.contains("#define fogColor (FogColor.rgb)"), adapted);
    }

    @Test
    void exposesNativeFallbackOutputsAndRejectsInputsTheBeaconFormatDoesNotHave() throws IOException {
        var nativeOutputs = BeaconBeamVertexShaderAdapter.nativeVaryings();
        assertEquals(0, nativeOutputs.get("vertexColor").location());
        assertEquals(1, nativeOutputs.get("texCoord0").location());

        IOException normal = assertThrows(IOException.class, () -> BeaconBeamVertexShaderAdapter.validate(
                "gbuffers_beaconbeam", "void main(){ gl_Position=ftransform()+vec4(gl_Normal,0.0); }"));
        assertTrue(normal.getMessage().contains("gl_Normal"));

        IOException attribute = assertThrows(IOException.class, () -> BeaconBeamVertexShaderAdapter.validate(
                "gbuffers_beaconbeam", "in vec4 mc_Entity;\nvoid main(){ gl_Position=ftransform()+mc_Entity; }"));
        assertTrue(attribute.getMessage().contains("mc_Entity"));
    }
}
