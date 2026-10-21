package dev.veyra.shader;

import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

class BasicVertexShaderAdapterTest {
    private static final String NATIVE_LEASH_VERTEX = """
            #version 460
            layout(location = 0) in vec3 Position;
            layout(location = 1) in vec4 Color;
            layout(location = 2) in ivec2 UV2;
            layout(location = 0) out vec4 vertexColor;
            layout(location = 1) out float sphericalVertexDistance;
            layout(location = 2) out float cylindricalVertexDistance;
            layout(set = 0, binding = 0, std140) uniform NativeTransforms {
                mat4 ModelViewMat;
                mat4 ProjMat;
                mat4 TextureMat;
                vec4 ColorModulator;
            };
            void main() {
                gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
                vertexColor = Color * ColorModulator;
                sphericalVertexDistance = 0.0;
                cylindricalVertexDistance = 0.0;
            }
            """;

    private static final String NATIVE_LINE_VERTEX = """
            #version 460
            layout(location = 0) in vec3 Position;
            layout(location = 1) in vec4 Color;
            layout(location = 2) in vec3 Normal;
            layout(location = 3) in float LineWidth;
            layout(location = 0) out vec4 vertexColor;
            layout(location = 1) out float sphericalVertexDistance;
            layout(location = 2) out float cylindricalVertexDistance;
            layout(set = 0, binding = 0, std140) uniform NativeTransforms {
                mat4 ModelViewMat;
                mat4 ProjMat;
                mat4 TextureMat;
                vec4 ColorModulator;
                vec2 ScreenSize;
            };
            void main() {
                gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
                vertexColor = Color * ColorModulator;
                sphericalVertexDistance = 0.0;
                cylindricalVertexDistance = 0.0;
            }
            """;

    @Test
    void adaptsLeashInputsAndLinksFlatColorAndLightmapOutputs() throws IOException {
        BasicVertexShaderAdapter.AdaptedStage adapted = BasicVertexShaderAdapter.adaptStage(
                NATIVE_LEASH_VERTEX, """
                        #version 130
                        varying vec2 lmCoord;
                        flat varying vec4 color;
                        void main() {
                            gl_Position = ftransform();
                            lmCoord = (gl_TextureMatrix[1] * gl_MultiTexCoord1).xy;
                            color = gl_Color;
                        }
                        """, "gbuffers_basic", BasicVertexShaderAdapter.Format.LEASH);

        assertTrue(adapted.source().contains("#define gl_MultiTexCoord0 vec4(0.5, 0.5, 0.0, 1.0)"));
        assertTrue(adapted.source().contains("#define gl_MultiTexCoord1 vec4(vec2(UV2), 0.0, 1.0)"));
        assertTrue(adapted.source().contains("#define lmCoord veyraBasicLightmapCoord"));
        assertTrue(adapted.source().contains("#define color vertexColor"));
        assertTrue(adapted.source().contains("layout(location = 3) out vec2 veyraBasicLightmapCoord;"));
        assertEquals(3, adapted.varyings().get("lmCoord").location());
        assertEquals("flat", adapted.varyings().get("color").interpolation());
    }

    @Test
    void adaptsLineEndpointInputsAndWrapsThePackStageTwiceBeforeWidening() throws IOException {
        BasicVertexShaderAdapter.AdaptedStage adapted = BasicVertexShaderAdapter.adaptStage(
                NATIVE_LINE_VERTEX, """
                        #version 130
                        flat out vec4 color;
                        out vec3 normal;
                        void main() {
                            gl_Position = ftransform();
                            color = gl_Color;
                            normal = gl_Normal;
                        }
                        """, "gbuffers_line", BasicVertexShaderAdapter.Format.LINE, true);

        assertTrue(adapted.source().contains("#define gl_MultiTexCoord1 vec4(240.0, 240.0, 0.0, 1.0)"));
        assertTrue(adapted.source().contains("#define vaNormal Normal"));
        assertTrue(adapted.source().contains("#define gl_VertexID gl_VertexIndex"));
        assertTrue(adapted.source().contains("veyraVertexOffset = Normal;"));
        assertTrue(adapted.source().contains("veyraShaderPackMain();\n    vec4 linePosEnd"));
        assertTrue(adapted.source().contains("veyraShaderPackMain();\n    vec4 linePosStart"));
        assertTrue(adapted.source().contains("* LineWidth / ScreenSize"));
        assertEquals(4, adapted.varyings().get("normal").location());
        assertEquals(0, adapted.varyings().get("color").location());
    }

    @Test
    void acceptsUnusedLegacyAttributesButRejectsAnActiveAttributeOutsideTheNativeFormat() throws IOException {
        String source = """
                #version 130
                attribute vec4 mc_Entity;
                void main() { gl_Position = ftransform(); }
                """;
        BasicVertexShaderAdapter.AdaptedStage adapted = BasicVertexShaderAdapter.adaptStage(
                NATIVE_LEASH_VERTEX, source, "gbuffers_basic", BasicVertexShaderAdapter.Format.LEASH);
        assertFalse(adapted.source().contains("mc_Entity"));

        IOException error = assertThrows(IOException.class, () -> BasicVertexShaderAdapter.adaptStage(
                NATIVE_LEASH_VERTEX, source.replace("gl_Position = ftransform();",
                        "gl_Position = ftransform() + vec4(mc_Entity.xyz, 0.0);"),
                "gbuffers_basic", BasicVertexShaderAdapter.Format.LEASH));
        assertTrue(error.getMessage().contains("mc_Entity"));
    }

    @Test
    void executesAnExplicitCoreProfileLineVertexStageOnce() throws IOException {
        BasicVertexShaderAdapter.AdaptedStage adapted = BasicVertexShaderAdapter.adaptStage(
                NATIVE_LINE_VERTEX, """
                        #version 130
                        void main() {
                            gl_Position = ProjMat * ModelViewMat * vec4(vaPosition + vaNormal, 1.0);
                        }
                        """, "gbuffers_line", BasicVertexShaderAdapter.Format.LINE);

        assertTrue(adapted.source().contains("void main() { veyraMinecraftLineMain();"));
        assertTrue(adapted.source().contains("#define vaPosition Position"));
        assertTrue(adapted.source().contains("#define vaNormal Normal"));
        assertFalse(adapted.source().contains("vec4 linePosEnd"));
    }

    @Test
    void executesSkyBasicVertexStagesAcrossTheThreeNativeSkyFormats() throws IOException {
        String vertex = """
                #version 120
                varying vec4 glColor;
                void main() {
                    gl_Position = ftransform();
                    glColor = gl_Color;
                }
                """;
        String fragment = """
                #version 120
                varying vec4 glColor;
                void main() { gl_FragColor = glColor; }
                """;

        for (BasicVertexShaderAdapter.Format format : new BasicVertexShaderAdapter.Format[] {
                BasicVertexShaderAdapter.Format.SKY,
                BasicVertexShaderAdapter.Format.STARS,
                BasicVertexShaderAdapter.Format.SUNRISE_SUNSET }) {
            BasicVertexShaderAdapter.AdaptedStage adapted = BasicVertexShaderAdapter.validate(
                    "gbuffers_skybasic", vertex, format);
            String linkedFragment = TextureColorDistanceShaderAdapter.adaptFragment(
                    fragment, "gbuffers_skybasic", adapted.varyings(), false, false);

            assertTrue(adapted.source().contains("veyraShaderPackMain();"));
            assertTrue(adapted.source().contains("#define gl_Color "
                    + (format == BasicVertexShaderAdapter.Format.SUNRISE_SUNSET ? "Color" : "ColorModulator")));
            assertEquals(0, adapted.varyings().get("glColor").location());
            assertTrue(linkedFragment.contains("layout(location = 0) in vec4 vertexColor;"));
        }
    }

    @Test
    void rejectsSkyVertexInputsMissingFromTheNativeFormats() {
        IOException error = assertThrows(IOException.class, () -> BasicVertexShaderAdapter.validate(
                "gbuffers_skybasic", """
                        #version 120
                        void main() {
                            gl_Position = ftransform();
                            vec3 value = vaNormal;
                        }
                        """, BasicVertexShaderAdapter.Format.STARS));
        assertTrue(error.getMessage().contains("vaNormal"));
    }
}
