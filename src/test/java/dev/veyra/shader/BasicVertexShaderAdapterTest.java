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

    private static final String NATIVE_LIGHTNING_VERTEX = """
            #version 460
            layout(location = 0) in vec3 Position;
            layout(location = 1) in vec4 Color;
            layout(location = 0) out vec4 vertexColor;
            layout(location = 1) out float sphericalVertexDistance;
            layout(location = 2) out float cylindricalVertexDistance;
            layout(set = 0, binding = 0, std140) uniform NativeTransforms {
                mat4 ModelViewMat;
                mat4 ProjMat;
                mat4 TextureMat;
                vec4 ColorModulator;
                vec2 ScreenSize;
                vec3 ModelOffset;
            };
            void main() {
                gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
                vertexColor = Color;
                sphericalVertexDistance = 0.0;
                cylindricalVertexDistance = 0.0;
            }
            """;

    private static final String NATIVE_TEXTURED_VERTEX = """
            #version 460
            layout(location = 0) in vec3 Position;
            layout(location = 1) in vec2 UV0;
            layout(location = 0) out vec2 texCoord0;
            layout(set = 0, binding = 0, std140) uniform NativeTransforms {
                mat4 ModelViewMat;
                mat4 ProjMat;
                mat4 TextureMat;
                vec4 ColorModulator;
                vec2 ScreenSize;
                vec3 ModelOffset;
            };
            void main() {
                gl_Position = ProjMat * ModelViewMat * vec4(Position + ModelOffset, 1.0);
                texCoord0 = UV0;
            }
            """;

    private static final String NATIVE_WEATHER_VERTEX = """
            #version 460
            layout(location = 0) in vec3 Position;
            layout(location = 1) in vec2 UV0;
            layout(location = 2) in vec4 Color;
            layout(location = 3) in ivec2 UV2;
            layout(location = 0) out float sphericalVertexDistance;
            layout(location = 1) out float cylindricalVertexDistance;
            layout(location = 2) out vec2 texCoord0;
            layout(location = 3) out vec4 vertexColor;
            layout(set = 0, binding = 0, std140) uniform NativeTransforms {
                mat4 ModelViewMat;
                mat4 ProjMat;
                mat4 TextureMat;
                vec4 ColorModulator;
            };
            uniform sampler2D Sampler2;
            void main() {
                gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
                sphericalVertexDistance = length(Position);
                cylindricalVertexDistance = length(Position.xz);
                texCoord0 = UV0;
                vertexColor = Color * texelFetch(Sampler2, UV2 / 16, 0);
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
    void bindsRenderStageSeparatelyForEachSkyVertexPipeline() throws IOException {
        String vertex = """
                #version 120
                uniform int renderStage;
                void main() {
                    gl_Position = ftransform() + vec4(float(renderStage));
                }
                """;

        for (var format : new BasicVertexShaderAdapter.Format[] {
                BasicVertexShaderAdapter.Format.SKY,
                BasicVertexShaderAdapter.Format.STARS,
                BasicVertexShaderAdapter.Format.SUNRISE_SUNSET }) {
            int expected = IrisRenderStages.forSkyFormat(format);
            String adapted = BasicVertexShaderAdapter.validate("gbuffers_skybasic", vertex, format).source();
            assertTrue(adapted.contains("const int renderStage = " + expected + ";"), adapted);
            assertFalse(adapted.contains("uniform int renderStage"), adapted);
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

    @Test
    void adaptsLightningLegacyInputsToThePositionColorFormatAndLinksPackVaryings() throws IOException {
        BasicVertexShaderAdapter.AdaptedStage adapted = BasicVertexShaderAdapter.adaptStage(
                NATIVE_LIGHTNING_VERTEX, """
                        #version 130
                        out vec2 texCoord;
                        out vec2 lmCoord;
                        out vec3 normal;
                        flat out vec4 glColor;
                        void main() {
                            gl_Position = ftransform();
                            texCoord = (gl_TextureMatrix[0] * gl_MultiTexCoord0).xy;
                            lmCoord = (gl_TextureMatrix[1] * gl_MultiTexCoord1).xy;
                            normal = normalize(gl_NormalMatrix * gl_Normal);
                            glColor = gl_Color;
                        }
                        """, "gbuffers_lightning", BasicVertexShaderAdapter.Format.LIGHTNING);

        assertTrue(adapted.source().contains("#define gl_Color Color"));
        assertTrue(adapted.source().contains("#define gl_Normal vec3(0.0, 0.0, 1.0)"));
        assertTrue(adapted.source().contains("#define gl_MultiTexCoord0 vec4(0.0, 0.0, 0.0, 1.0)"));
        assertTrue(adapted.source().contains("#define gl_MultiTexCoord1 vec4(0.0, 0.0, 0.0, 1.0)"));
        assertTrue(adapted.varyings().containsKey("texCoord"));
        assertTrue(adapted.varyings().containsKey("lmCoord"));
        assertEquals(0, adapted.varyings().get("glColor").location());

        String fragment = TextureColorDistanceShaderAdapter.adaptFragment("""
                #version 130
                in vec2 texCoord;
                flat in vec4 glColor;
                uniform sampler2D tex;
                void main() { gl_FragColor = texture2D(tex, texCoord) * glColor; }
                """, "gbuffers_lightning", adapted.varyings(), false, false);
        assertTrue(fragment.contains("layout(location = 0) flat in vec4 vertexColor;"));
        assertTrue(fragment.contains("layout(location = 6) in vec2 texCoord0;"));
    }

    @Test
    void adaptsTexturedSkyFormatsAndRetainsWorldBorderVertexInputs() throws IOException {
        String skyVertex = """
                #version 120
                varying vec2 texCoord;
                flat varying vec4 color;
                void main() {
                    gl_Position = ftransform();
                    texCoord = (gl_TextureMatrix[0] * gl_MultiTexCoord0).xy;
                    color = gl_Color;
                }
                """;
        for (BasicVertexShaderAdapter.Format format : new BasicVertexShaderAdapter.Format[] {
                BasicVertexShaderAdapter.Format.SKY_TEXTURED,
                BasicVertexShaderAdapter.Format.SKY_TEXTURED_COLOR }) {
            BasicVertexShaderAdapter.AdaptedStage adapted = BasicVertexShaderAdapter.adaptStage(
                    format == BasicVertexShaderAdapter.Format.SKY_TEXTURED
                            ? NATIVE_TEXTURED_VERTEX : NATIVE_TEXTURED_VERTEX.replace(
                                    "layout(location = 1) in vec2 UV0;",
                                    "layout(location = 1) in vec2 UV0;\nlayout(location = 2) in vec4 Color;")
                                    .replace("layout(location = 0) out vec2 texCoord0;",
                                            "layout(location = 0) out vec2 texCoord0;\nlayout(location = 1) out vec4 vertexColor;")
                                    .replace("texCoord0 = UV0;", "texCoord0 = UV0; vertexColor = Color;"),
                    skyVertex, "gbuffers_skytextured", format);
            assertTrue(adapted.source().contains("#define gl_MultiTexCoord0 vec4(UV0, 0.0, 1.0)"));
            assertTrue(adapted.varyings().containsKey("texCoord"));
            assertEquals(0, adapted.varyings().get("color").location());
        }

        String texturedVertex = """
                #version 120
                attribute vec4 mc_Entity;
                attribute vec4 mc_midTexCoord;
                varying vec2 texcoord;
                varying vec4 color;
                void main() {
                    gl_Position = ftransform();
                    texcoord = (gl_TextureMatrix[0] * gl_MultiTexCoord0).xy;
                    color = gl_Color + vec4(normalize(gl_NormalMatrix * gl_Normal), 0.0);
                }
                """;
        BasicVertexShaderAdapter.AdaptedStage adapted = BasicVertexShaderAdapter.adaptStage(
                NATIVE_TEXTURED_VERTEX, texturedVertex, "gbuffers_textured",
                BasicVertexShaderAdapter.Format.TEXTURED);
        assertTrue(adapted.source().contains("#define gl_MultiTexCoord0 vec4(UV0, 0.0, 1.0)"));
        assertTrue(adapted.source().contains("#define gl_Color vec4(1.0)"));
        assertTrue(adapted.source().contains("#define gl_Vertex vec4(Position + ModelOffset, 1.0)"));
        assertFalse(adapted.source().contains("attribute vec4 mc_Entity"));
        assertFalse(adapted.source().contains("attribute vec4 mc_midTexCoord"));
    }

    @Test
    void adaptsWeatherParticleInputsAndPreservesNativeLightmapAndFogOutputs() throws IOException {
        BasicVertexShaderAdapter.AdaptedStage adapted = BasicVertexShaderAdapter.validate(
                "gbuffers_weather", """
                        #version 130
                        flat varying vec2 lmCoord;
                        varying vec2 texCoord;
                        flat varying vec3 upVec, sunVec;
                        flat varying vec4 glColor;
                        varying float lPos;
                        void main() {
                            gl_Position = ftransform();
                            texCoord = (gl_TextureMatrix[0] * gl_MultiTexCoord0).xy;
                            lmCoord = (gl_TextureMatrix[1] * gl_MultiTexCoord1).xy;
                            upVec = vec3(0.0, 1.0, 0.0);
                            sunVec = vec3(0.0, 0.0, 1.0);
                            glColor = gl_Color;
                            lPos = length(gl_Vertex.xyz);
                        }
                        """, BasicVertexShaderAdapter.Format.WEATHER);

        assertTrue(adapted.source().contains("#define gl_MultiTexCoord0 vec4(UV0, 0.0, 1.0)"));
        assertTrue(adapted.source().contains("#define gl_MultiTexCoord1 vec4(vec2(UV2), 0.0, 1.0)"));
        assertTrue(adapted.source().contains("#define vaUV2 UV2"));
        assertTrue(adapted.source().contains("uniform sampler2D Sampler2;"));
        assertTrue(adapted.source().contains("vertexColor=Color*texelFetch(Sampler2,UV2/16,0);"));
        assertTrue(adapted.source().contains("#define glColor vertexColor"));
        assertFalse(adapted.source().contains("sphericalVertexDistance = 0.0;"));
        assertEquals(0, adapted.varyings().get("glColor").location());
        assertEquals(3, adapted.varyings().get("lmCoord").location());
        assertEquals(7, adapted.varyings().get("texCoord").location());

        String fragment = TextureColorDistanceShaderAdapter.adaptFragment("""
                #version 130
                flat varying vec2 lmCoord;
                varying vec2 texCoord;
                flat varying vec3 upVec, sunVec;
                flat varying vec4 glColor;
                varying float lPos;
                void main() {
                    gl_FragColor = glColor + vec4(texCoord + lmCoord, upVec.x + sunVec.x + lPos, 0.0);
                }
                """, "gbuffers_weather", adapted.varyings(), false, true);
        assertTrue(fragment.contains("layout(location = 0) flat in vec4 vertexColor;"));
        assertTrue(fragment.contains("layout(location = 3) flat in vec2 lmCoord;"), fragment);
        assertTrue(fragment.contains("layout(location = 7) in vec2 texCoord0;"), fragment);

        IOException missingNormal = assertThrows(IOException.class, () -> BasicVertexShaderAdapter.validate(
                "gbuffers_weather", """
                        #version 130
                        void main() {
                            gl_Position = ftransform() + vec4(gl_Normal, 0.0);
                        }
                        """, BasicVertexShaderAdapter.Format.WEATHER));
        assertTrue(missingNormal.getMessage().contains("gl_Normal"));
    }

    @Test
    void rejectsActiveMissingTexturedAttributesButAllowsUnusedDeclarations() throws IOException {
        String source = """
                #version 120
                attribute vec4 mc_Entity;
                void main() { gl_Position = ftransform(); }
                """;
        BasicVertexShaderAdapter.adaptStage(NATIVE_TEXTURED_VERTEX, source,
                "gbuffers_textured", BasicVertexShaderAdapter.Format.TEXTURED);
        IOException error = assertThrows(IOException.class, () -> BasicVertexShaderAdapter.adaptStage(
                NATIVE_TEXTURED_VERTEX, source.replace("gl_Position = ftransform();",
                        "gl_Position = ftransform() + vec4(mc_Entity.xyz, 0.0);"),
                "gbuffers_textured", BasicVertexShaderAdapter.Format.TEXTURED));
        assertTrue(error.getMessage().contains("mc_Entity"));
    }

    @Test
    void linksVec4NormalAsAUserVaryingForTexturedPrograms() throws IOException {
        String packVertex = """
                #version 120
                varying vec4 normal;
                void main() {
                    gl_Position = ftransform();
                    normal = vec4(0.0, 0.0, 1.0, 0.0);
                }
                """;
        BasicVertexShaderAdapter.AdaptedStage adaptedVertex = BasicVertexShaderAdapter.adaptStage(
                NATIVE_TEXTURED_VERTEX, packVertex, "gbuffers_textured",
                BasicVertexShaderAdapter.Format.TEXTURED);
        var normal = adaptedVertex.varyings().get("normal");

        assertNotNull(normal);
        assertEquals("vec4", normal.type());
        assertFalse(normal.fixedBridge());

        String adaptedFragment = TextureColorDistanceShaderAdapter.adaptFragment("""
                #version 120
                varying vec4 normal;
                void main() { gl_FragColor = normal; }
                """, "gbuffers_textured", adaptedVertex.varyings(), false, false);
        assertTrue(adaptedFragment.contains("layout(location = " + normal.location() + ") in vec4 normal;"),
                adaptedFragment);
    }

    @Test
    void linksTextureFragmentToNativeTexCoordWhenPackVertexDoesNotReplaceIt() throws IOException {
        String packVertex = "#version 120\nvoid main() { gl_Position = ftransform(); }\n";
        BasicVertexShaderAdapter.AdaptedStage adaptedVertex = BasicVertexShaderAdapter.adaptStage(
                NATIVE_TEXTURED_VERTEX, packVertex, "gbuffers_textured",
                BasicVertexShaderAdapter.Format.TEXTURED);
        assertEquals(6, adaptedVertex.varyings().get("texCoord0").location());

        String adaptedFragment = TextureColorDistanceShaderAdapter.adaptFragment("""
                #version 120
                varying vec2 texCoord0;
                uniform sampler2D gtexture;
                void main() { gl_FragColor = texture2D(gtexture, texCoord0); }
                """, "gbuffers_textured", adaptedVertex.varyings(), false, false);
        assertTrue(adaptedFragment.contains("layout(location = 6) in vec2 texCoord0;"));
        assertTrue(adaptedVertex.source().contains("VEYRA_BASIC_NATIVE_TEXCOORD"));
    }
}
