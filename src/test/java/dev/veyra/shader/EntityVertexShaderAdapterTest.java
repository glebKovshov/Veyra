package dev.veyra.shader;

import java.io.IOException;
import java.util.Set;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EntityVertexShaderAdapterTest {
    private static final String NATIVE = """
            #version 460
            layout(location=0) in vec3 Position;
            layout(location=1) in vec4 Color;
            layout(location=2) in vec2 UV0;
            layout(location=3) in ivec2 UV1;
            layout(location=4) in ivec2 UV2;
            layout(location=5) in vec3 Normal;
            uniform mat4 ModelViewMat;
            uniform mat4 ProjMat;
            layout(location=0) out vec4 vertexColor;
            layout(location=1) out vec2 texCoord0;
            void main() {
                gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
                vertexColor = Color;
                texCoord0 = UV0;
            }
            """;

    @Test
    void assignsPackVaryingsOutsideNativeEntityInterfaceAndLinksLegacyInputs() throws Exception {
        String pack = """
                #version 120
                varying vec3 LightC;
                varying vec2 texcoord;
                void main() {
                    gl_Position = ftransform();
                    LightC = vec3(gl_Color.rgb);
                    texcoord = (gl_TextureMatrix[0] * gl_MultiTexCoord0).xy;
                }
                """;

        EntityVertexShaderAdapter.AdaptedStage adapted =
                EntityVertexShaderAdapter.adaptStage(NATIVE, pack, "gbuffers_entities", false);

        assertEquals(16, adapted.varyings().get("LightC").location());
        assertEquals(17, adapted.varyings().get("texcoord").location());
        assertTrue(adapted.source().contains("layout(location = 16) out vec3 veyraEntityVarying_LightC;"));
        assertTrue(adapted.source().contains("#define gl_Color Color"));
        assertTrue(adapted.source().contains("veyraMinecraftEntityMain(); veyraShaderPackMain();"));
        assertFalse(adapted.usesNoiseSampler());
    }

    @Test
    void reusesNativeInverseModelViewUniformInsteadOfDeclaringItAgainForThePack() throws Exception {
        String nativeSource = NATIVE.replace("uniform mat4 ModelViewMat;",
                "uniform mat4 ModelViewMat;\nuniform mat4 gbufferModelViewInverse;");
        String pack = """
                #version 120
                uniform mat4 gbufferModelViewInverse;
                void main() { gl_Position = ftransform() * gbufferModelViewInverse; }
                """;

        String adapted = EntityVertexShaderAdapter.adaptStage(
                nativeSource, pack, "gbuffers_entities", false).source();

        assertEquals(1, occurrences(adapted, "uniform mat4 gbufferModelViewInverse;"), adapted);
    }

    @Test
    void reusesNativeLightmapSamplerInsteadOfDeclaringItAgainForThePack() throws Exception {
        String nativeSource = NATIVE.replace("uniform mat4 ModelViewMat;",
                "uniform mat4 ModelViewMat;\nuniform sampler2D Sampler2;");
        String pack = """
                #version 120
                uniform sampler2D lightmap;
                void main() {
                    gl_Position = ftransform();
                    vec4 lightmapSample = texture2D(lightmap, vec2(0.0));
                }
                """;

        String adapted = EntityVertexShaderAdapter.adaptStage(
                nativeSource, pack, "shadow_entities", false).source();

        assertEquals(1, occurrences(adapted, "uniform sampler2D Sampler2;"), adapted);
        assertTrue(adapted.contains("texture(Sampler2, vec2(0.0))"), adapted);
    }

    @Test
    void entityShadowStagesUseIrisShadowProjectionAndRemapDepthToVulkan() throws Exception {
        String remap = "gl_Position.z = (gl_Position.z + gl_Position.w) * 0.5;";
        String[] shadowPrograms = {"shadow_entities", "shadow_block"};
        String[] projectionExpressions = {
                "ftransform()",
                "gl_ProjectionMatrix * gl_Vertex",
                "gl_ModelViewProjectionMatrix * gl_Vertex"
        };
        for (String program : shadowPrograms) {
            for (String expression : projectionExpressions) {
                String pack = "#version 120\nvoid main() { gl_Position = " + expression + "; }\n";
                String adapted = EntityVertexShaderAdapter.adaptStage(NATIVE, pack, program, false).source();
                String packMain = functionBody(adapted, "veyraShaderPackMain");

                assertTrue(packMain.contains("shadowProjection"), packMain);
                assertFalse(packMain.contains("ProjMat"), packMain);
                assertTrue(adapted.indexOf(remap) > adapted.indexOf("void veyraShaderPackMain()"), adapted);
            }
        }
        String declaringPack = """
                #version 120
                uniform mat4 shadowProjection;
                uniform mat4 shadowModelView;
                void main() { gl_Position = shadowProjection * shadowModelView * gl_Vertex; }
                """;

        String declared = EntityVertexShaderAdapter.adaptStage(NATIVE, declaringPack, "shadow_block", false).source();
        String color = EntityVertexShaderAdapter.adaptStage(NATIVE,
                "#version 120\nvoid main() { gl_Position = ftransform(); }\n", "gbuffers_entities", false).source();
        String hand = EntityVertexShaderAdapter.adaptStage(NATIVE,
                "#version 120\nvoid main() { gl_Position = ftransform(); }\n", "gbuffers_hand", true).source();

        assertEquals(1, occurrences(declared, "uniform mat4 shadowProjection;"), declared);
        assertTrue(functionBody(color, "veyraShaderPackMain").contains("ProjMat"), color);
        assertFalse(color.contains(remap), color);
        assertTrue(functionBody(hand, "veyraShaderPackMain").contains("ProjMat"), hand);
        assertTrue(hand.contains("gl_Position.z *= 0.125;"), hand);
        assertFalse(hand.contains(remap), hand);
    }

    private static String functionBody(String source, String functionName) {
        int signature = source.indexOf("void " + functionName + "()");
        assertTrue(signature >= 0, source);
        int open = source.indexOf('{', signature);
        int close = source.indexOf('}', open);
        assertTrue(open >= 0 && close > open, source);
        return source.substring(open + 1, close);
    }

    private static int occurrences(String source, String needle) {
        int count = 0;
        for (int offset = 0; (offset = source.indexOf(needle, offset)) >= 0; offset += needle.length()) count++;
        return count;
    }

    @Test
    void bindsEntityIdentifiersInCustomVertexStagesPerRenderVariant() throws Exception {
        for (String[] identifier : new String[][] {
                {"gbuffers_entities", "entityId", "37"},
                {"gbuffers_block", "blockEntityId", "18"},
                {"gbuffers_hand", "currentRenderedItemId", "9"}
        }) {
            String program = identifier[0];
            String name = identifier[1];
            int value = Integer.parseInt(identifier[2]);
            String pack = "uniform int " + name + ";\n"
                    + "void main() { gl_Position = vec4(float(" + name + ")); }\n";

            EntityVertexShaderAdapter.AdaptedStage adapted = EntityVertexShaderAdapter.adaptStage(
                    NATIVE, pack, program, program.equals("gbuffers_hand"),
                    name.equals("entityId") ? value : EntityIdMap.UNMAPPED,
                    name.equals("blockEntityId") ? value : BlockIdMap.UNMAPPED,
                    name.equals("currentRenderedItemId") ? value : ItemIdMap.UNMAPPED);

            assertTrue(EntityShaderAdapter.usesEntityIdentifier(pack, program, name));
            assertTrue(adapted.source().contains("const int " + name + " = " + value + ";"), adapted.source());
            assertFalse(adapted.source().contains("uniform int " + name), adapted.source());
        }
    }

    @Test
    void bindsIrisRenderStageInCustomEntityVertexStages() throws Exception {
        String pack = "uniform int renderStage;\n"
                + "void main() { gl_Position = vec4(float(renderStage)); }\n";

        EntityVertexShaderAdapter.AdaptedStage adapted = EntityVertexShaderAdapter.adaptStage(
                NATIVE, pack, "gbuffers_block_translucent", false);

        assertTrue(adapted.source().contains("const int renderStage = 12;"), adapted.source());
        assertFalse(adapted.source().contains("uniform int renderStage"), adapted.source());
    }

    @Test
    void bridgesEntityAndHandMidTexCoordToTheVeyraQuadInput() throws Exception {
        String pack = """
                #version 120
                attribute vec4 mc_midTexCoord;
                void main() {
                    gl_Position = ftransform() + vec4(mc_midTexCoord.xy, 0.0, 0.0);
                }
                """;

        for (String program : new String[] {"gbuffers_entities", "gbuffers_hand", "shadow_entities"}) {
            EntityVertexShaderAdapter.AdaptedStage adapted = EntityVertexShaderAdapter.adaptStage(
                    NATIVE, pack, program, program.equals("gbuffers_hand"));

            assertTrue(adapted.usesMidTexCoord(), program);
            assertFalse(adapted.source().contains("attribute vec4 mc_midTexCoord"), adapted.source());
            assertTrue(adapted.source().contains("in vec2 MidTexCoord;"), adapted.source());
            assertTrue(adapted.source().indexOf("in vec2 MidTexCoord;")
                            < adapted.source().indexOf("out "), adapted.source());
            assertTrue(adapted.source().contains("#define mc_midTexCoord vec4(MidTexCoord, 0.0, 1.0)"),
                    adapted.source());
        }
    }

    @Test
    void suppliesEntityShadowFallbacksForBlockOnlyMidBlockAndVertexIdInputs() throws Exception {
        String pack = """
                #version 120
                attribute vec3 at_midBlock;
                void main() {
                    vec3 modelPos = at_midBlock.xyz / 64.0;
                    int vertex = gl_VertexID;
                    gl_Position = ftransform() + vec4(modelPos, float(vertex % 4) * 0.0);
                }
                """;

        EntityVertexShaderAdapter.AdaptedStage adapted = EntityVertexShaderAdapter.adaptStage(
                NATIVE, pack, "shadow_entities", false);

        assertFalse(adapted.source().contains("at_midBlock"), adapted.source());
        assertTrue(adapted.source().contains("vec3 modelPos = vec4(0.0).xyz / 64.0;"), adapted.source());
        assertTrue(adapted.source().contains("int vertex = gl_VertexIndex;"), adapted.source());
    }

    @Test
    void validatesMidTexCoordWithItsExtendedNativeVertexInput() throws Exception {
        String pack = "#version 120\nattribute vec4 mc_midTexCoord;\n"
                + "void main(){gl_Position=vec4(mc_midTexCoord.xy,0.0,1.0);}";

        EntityVertexShaderAdapter.AdaptedStage adapted =
                EntityVertexShaderAdapter.validate("gbuffers_entities", pack, false);

        assertTrue(adapted.usesMidTexCoord());
        assertTrue(adapted.source().contains("in vec2 MidTexCoord;"), adapted.source());
    }

    @Test
    void removesRedundantInputQualifiersFromPackVertexFunctionParameters() throws Exception {
        String pack = "vec3 GetRawWave(in vec3 pos, float wind) { return pos * wind; }\n"
                + "void main() { gl_Position = vec4(GetRawWave(Position, 1.0), 1.0); }\n";

        String adapted = EntityVertexShaderAdapter.adaptStage(NATIVE, pack, "shadow_entities", false).source();

        assertTrue(adapted.contains("GetRawWave( vec3 pos, float wind)"), adapted);
        assertTrue(adapted.contains("in vec3 Position;"), adapted);
    }

    @Test
    void rejectsMidTexCoordForTheDamagedBlockFormat() {
        String pack = "#version 120\nvoid main(){gl_Position=vec4(mc_midTexCoord.xy,0.0,1.0);}";

        IOException error = assertThrows(IOException.class,
                () -> EntityVertexShaderAdapter.validate("gbuffers_damagedblock", pack, false));

        assertTrue(error.getMessage().contains("mc_midTexCoord"), error.getMessage());
        assertTrue(error.getMessage().contains("BLOCK vertex format"), error.getMessage());
    }

    @Test
    void reconstructsShadowEntityWorldPositionAfterTheNativeClipTransform() throws Exception {
        String adapted = EntityShaderAdapter.adaptVertexShader(
                NATIVE, true, "shadow_entities", false, false, false, false, false, true);

        assertTrue(adapted.contains("out vec4 position;"), adapted);
        assertTrue(adapted.contains("uniform mat4 shadowModelViewInverse;"), adapted);
        assertTrue(adapted.contains("uniform mat4 shadowProjectionInverse;"), adapted);
        int clipAssignment = adapted.indexOf("gl_Position = ProjMat * ModelViewMat");
        int worldPositionAssignment = adapted.indexOf(
                "position = shadowModelViewInverse * shadowProjectionInverse * gl_Position;");
        assertTrue(clipAssignment >= 0, adapted);
        assertTrue(worldPositionAssignment > clipAssignment, adapted);
    }

    @Test
    void rejectsShadowPositionGenerationOutsideShadowPasses() {
        IOException error = assertThrows(IOException.class, () -> EntityShaderAdapter.adaptVertexShader(
                NATIVE, false, "gbuffers_entities", false, false, false, false, false, true));
        assertTrue(error.getMessage().contains("outside a shadow pass"), error.getMessage());
    }

    @Test
    void linksDamagedBlockFragmentToCustomVertexVaryings() throws Exception {
        String vertex = """
                #version 120
                varying vec2 texcoord;
                varying vec4 glcolor;
                void main() {
                    gl_Position = ftransform();
                    texcoord = (gl_TextureMatrix[0] * gl_MultiTexCoord0).xy;
                    glcolor = gl_Color;
                }
                """;
        String fragment = """
                #version 120
                uniform sampler2D texture;
                uniform float alphaTestRef;
                varying vec2 texcoord;
                varying vec4 glcolor;
                void main() {
                    vec4 color = texture2D(texture, texcoord) * glcolor;
                    if (color.a < alphaTestRef) discard;
                    gl_FragColor = color;
                }
                """;
        var outputs = EntityVertexShaderAdapter.describeVaryings("gbuffers_damagedblock", vertex);

        String adapted = DamagedBlockShaderAdapter.adaptFragment(fragment, "gbuffers_damagedblock",
                outputs, false, false);

        assertEquals(16, outputs.get("texcoord").location());
        assertEquals(17, outputs.get("glcolor").location());
        assertTrue(adapted.contains("layout(location = 16) in vec2 texCoord0;"));
        assertTrue(adapted.contains("layout(location = 17) in vec4 vertexColor;"));
        assertFalse(adapted.contains("uniform float alphaTestRef"));
    }

    @Test
    void mapsDamagedBlockLightmapAliasThroughTheBlockVertexFormat() throws Exception {
        String nativeBlock = EntityShaderAdapter.adaptBlockVertexFormat("""
                #version 460
                layout(location=0) in vec3 Position;
                layout(location=1) in vec4 Color;
                layout(location=2) in vec2 UV0;
                layout(location=3) in vec2 UV2;
                layout(location=4) in vec3 Normal;
                layout(location=0) out vec4 vertexColor;
                layout(location=1) out vec2 texCoord0;
                layout(location=2) out vec2 texCoord2;
                uniform mat4 ModelViewMat;
                uniform mat4 ProjMat;
                void main() {
                    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
                    vertexColor = Color;
                    texCoord0 = UV0;
                    texCoord2 = UV2;
                }
                """, "gbuffers_damagedblock");
        String pack = """
                #version 120
                varying vec2 lmCoord;
                void main() {
                    gl_Position = ftransform();
                    lmCoord = (gl_TextureMatrix[1] * gl_MultiTexCoord1).xy;
                }
                """;

        String blockFormat = EntityShaderAdapter.adaptBlockVertexFormat(
                nativeBlock, "gbuffers_damagedblock");
        String adapted = EntityVertexShaderAdapter.adaptStage(
                blockFormat, pack, "gbuffers_damagedblock", false).source();

        assertTrue(adapted.contains("layout(location=3) in ivec2 UV2;"), adapted);
        assertTrue(adapted.contains("texCoord2 = vec2(UV2);"), adapted);
        assertTrue(adapted.contains("#define gl_MultiTexCoord1 vec4(vec2(UV2), 0.0, 1.0)"), adapted);
        assertFalse(adapted.contains("#define gl_MultiTexCoord2"), adapted);
    }

    @Test
    void rejectsEntityOverlayInputForDamagedBlockBecauseBlockFormatDoesNotProvideIt() {
        String pack = """
                #version 120
                attribute vec2 UV1;
                void main(){gl_Position=ftransform();}
                """;

        IOException error = assertThrows(IOException.class,
                () -> EntityVertexShaderAdapter.validate("gbuffers_damagedblock", pack, false));

        assertTrue(error.getMessage().contains("UV1"));
        assertTrue(error.getMessage().contains("BLOCK vertex format"));
    }

    @Test
    void rejectsUnavailableSecondLegacyTextureCoordinateForDamagedBlock() {
        String pack = """
                #version 120
                void main(){gl_Position=ftransform()+gl_MultiTexCoord2;}
                """;

        IOException error = assertThrows(IOException.class,
                () -> EntityVertexShaderAdapter.validate("gbuffers_damagedblock", pack, false));

        assertTrue(error.getMessage().contains("gl_MultiTexCoord2"));
        assertTrue(error.getMessage().contains("BLOCK vertex format"));
    }

    @Test
    void rejectsUnavailableLightmapSamplerInDamagedBlockPipeline() throws Exception {
        String vertex = "#version 120\nvarying vec2 lmcoord;\n"
                + "void main(){gl_Position=ftransform();lmcoord=vec2(0.0);}\n";
        String fragment = "#version 120\nuniform sampler2D lightmap;\nvarying vec2 lmcoord;\n"
                + "void main(){gl_FragColor=texture2D(lightmap,lmcoord);}\n";
        var outputs = EntityVertexShaderAdapter.describeVaryings("gbuffers_damagedblock", vertex);

        IOException error = assertThrows(IOException.class, () -> DamagedBlockShaderAdapter.adaptFragment(
                fragment, "gbuffers_damagedblock", outputs, false, false));

        assertTrue(error.getMessage().contains("lightmap sampler"));
    }

    @Test
    void appliesHandDepthAfterTheShaderPackEntryPoint() throws Exception {
        String pack = "#version 120\nvoid main(){gl_Position=ftransform();}\n";
        String adapted = EntityVertexShaderAdapter.adaptStage(NATIVE, pack, "gbuffers_hand", true).source();

        assertTrue(adapted.contains("veyraMinecraftEntityMain(); veyraShaderPackMain(); gl_Position.z *= 0.125;"));
    }

    @Test
    void linksEntityFragmentInputsToCustomVertexOutputs() throws Exception {
        String vertex = "#version 120\nvarying vec3 LightC;\nvoid main(){LightC=vec3(1.0);gl_Position=ftransform();}\n";
        String fragment = "#version 120\nvarying vec3 LightC;\nvoid main(){gl_FragColor=vec4(LightC,1.0);}";
        var varyings = EntityVertexShaderAdapter.describeVaryings("gbuffers_entities", vertex);

        String adapted = EntityShaderAdapter.adaptGeometryFragment(fragment, "gbuffers_entities", false,
                false, -1, -1, ItemIdMap.UNMAPPED, true, false, varyings);

        assertTrue(adapted.contains("layout(location = 16) in vec3 veyraEntityPackInput_16;"));
        assertTrue(adapted.contains("vec4(veyraEntityPackInput_16,1.0)"));
    }

    @Test
    void preservesConfiguredCustomTextureSamplersInEntityFragments() throws Exception {
        String fragment = """
                #version 120
                uniform sampler2D lightTint;
                void main() { gl_FragColor = texture2D(lightTint, vec2(0.5)); }
                """;

        IOException unsupported = assertThrows(IOException.class,
                () -> EntityShaderAdapter.adaptGeometryFragment(fragment, "gbuffers_entities", false,
                        false, -1, -1, ItemIdMap.UNMAPPED, true, false, java.util.Map.of()));
        assertTrue(unsupported.getMessage().contains("unsupported sampler 'lightTint'"));

        String adapted = EntityShaderAdapter.adaptGeometryFragment(fragment, "gbuffers_entities", false,
                false, -1, -1, ItemIdMap.UNMAPPED, true, false, java.util.Map.of(), Set.of("lightTint"));
        assertTrue(adapted.contains("uniform sampler2D lightTint;"), adapted);
        assertTrue(adapted.contains("veyraEntitySample(lightTint, vec2(0.5))"), adapted);
    }

    @Test
    void linksManyCustomEntityInputsInOneFragmentPass() throws Exception {
        StringBuilder vertex = new StringBuilder("#version 120\n");
        StringBuilder fragment = new StringBuilder("#version 120\n");
        for (int index = 0; index < 8; index++) {
            String name = "packValue" + index;
            vertex.append("varying vec3 ").append(name).append(';').append('\n');
            fragment.append("varying vec3 ").append(name).append(';').append('\n');
        }
        vertex.append("void main(){\n");
        fragment.append("void main(){ vec3 total=vec3(0.0);\n");
        for (int index = 0; index < 8; index++) {
            String name = "packValue" + index;
            vertex.append(name).append("=vec3(1.0);").append('\n');
            fragment.append("total += ").append(name).append(';').append('\n');
        }
        vertex.append("gl_Position=ftransform();}\n");
        fragment.append("gl_FragColor=vec4(total,1.0);}");
        var varyings = EntityVertexShaderAdapter.describeVaryings("gbuffers_entities", vertex.toString());

        String adapted = EntityShaderAdapter.adaptGeometryFragment(fragment.toString(), "gbuffers_entities", false,
                false, -1, -1, ItemIdMap.UNMAPPED, true, false, varyings);

        for (int index = 0; index < 8; index++) {
            int location = 16 + index;
            assertTrue(adapted.contains("layout(location = " + location + ") in vec3 veyraEntityPackInput_"
                    + location + ";"), adapted);
            assertTrue(adapted.contains("total += veyraEntityPackInput_" + location + ";"), adapted);
        }
    }

    @Test
    void linksLeadingFlatShadowInputToTheSelectedShadowVertexStage() throws Exception {
        String vertex = """
                #version 120
                attribute vec4 mc_Entity;
                flat varying int mat;
                void main() {
                    mat = int(mc_Entity.x + 0.5);
                    gl_Position = ftransform();
                }
                """;
        String fragment = """
                #version 460
                flat in int mat;
                void main() { gl_FragColor = vec4(float(mat)); }
                """;

        EntityVertexShaderAdapter.AdaptedStage adaptedVertex =
                EntityVertexShaderAdapter.adaptStage(NATIVE, vertex, "shadow_entities", false);
        String adaptedFragment = EntityShaderAdapter.adaptGeometryFragment(fragment,
                "shadow_entities", false, true, -1, -1, ItemIdMap.UNMAPPED,
                false, false, adaptedVertex.varyings());

        assertTrue(adaptedVertex.source().contains("flat out int veyraEntityVarying_mat;"));
        assertTrue(adaptedVertex.source().contains("mat = int(vec4(0.0).x + 0.5);"));
        assertFalse(adaptedVertex.source().contains("attribute vec4 mc_Entity"));
        assertTrue(adaptedFragment.contains("layout(location = 16) flat in int veyraEntityPackInput_16;"));
        assertTrue(adaptedFragment.contains("float(veyraEntityPackInput_16)"));
    }

    @Test
    void linksNoiseSamplerOnlyOnceIntoCustomVertexStage() throws Exception {
        String pack = """
                #version 120
                uniform sampler2D noisetex;
                void main(){gl_Position=ftransform()+texture2D(noisetex,vec2(0.5));}
                """;

        EntityVertexShaderAdapter.AdaptedStage adapted =
                EntityVertexShaderAdapter.adaptStage(NATIVE, pack, "gbuffers_entities", false);

        assertTrue(adapted.usesNoiseSampler());
        assertEquals(1L, adapted.source().lines()
                .filter(line -> line.equals("uniform sampler2D VeyraNoiseSampler;")).count());
        assertEquals(1L, adapted.source().lines()
                .filter(line -> line.equals("#define noisetex VeyraNoiseSampler")).count());
    }

    @Test
    void mapsEntityTextureAndLightmapAliasesToNativeVertexSamplers() throws Exception {
        String pack = """
                #version 120
                uniform sampler2D tex;
                uniform sampler2D texture;
                uniform sampler2D lightmap;
                void main() {
                    gl_Position = ftransform()
                        + texture2DLod(tex, vec2(0.5), 0.0)
                        + texture2D(texture, vec2(0.5))
                        + texture2D(lightmap, vec2(0.5));
                }
                """;

        EntityVertexShaderAdapter.AdaptedStage adapted =
                EntityVertexShaderAdapter.adaptStage(NATIVE, pack, "shadow_entities", false);

        assertTrue(adapted.source().contains("uniform sampler2D Sampler0;"));
        assertTrue(adapted.source().contains("uniform sampler2D Sampler2;"));
        assertTrue(adapted.source().contains("textureLod(Sampler0, vec2(0.5), 0.0)"));
        assertTrue(adapted.source().contains("texture(Sampler0, vec2(0.5))"));
        assertTrue(adapted.source().contains("texture(Sampler2, vec2(0.5))"));
        assertFalse(adapted.source().contains("uniform sampler2D tex;"));
        assertFalse(adapted.source().contains("uniform sampler2D texture;"));
        assertFalse(adapted.source().contains("uniform sampler2D lightmap;"));
    }

    @Test
    void allowsConfiguredCustomTextureSamplersInEntityVertexShaders() throws Exception {
        String pack = """
                #version 120
                uniform sampler2D lightTint;
                void main() {
                    gl_Position = ftransform() + vec4(texture2D(lightTint, vec2(0.5)).rgb, 0.0);
                }
                """;

        IOException unsupported = assertThrows(IOException.class,
                () -> EntityVertexShaderAdapter.adaptStage(NATIVE, pack, "gbuffers_entities", false));
        assertTrue(unsupported.getMessage().contains("unsupported sampler 'lightTint'"));

        EntityVertexShaderAdapter.AdaptedStage adapted = EntityVertexShaderAdapter.adaptStage(
                NATIVE, pack, "gbuffers_entities", false,
                EntityIdMap.UNMAPPED, BlockIdMap.UNMAPPED, ItemIdMap.UNMAPPED, Set.of("lightTint"));
        assertTrue(adapted.source().contains("uniform sampler2D lightTint;"), adapted.source());
        assertTrue(adapted.source().contains("texture(lightTint, vec2(0.5))"), adapted.source());
    }

    @Test
    void rejectsExplicitFragmentVaryingLocationThatDoesNotMatchVertexOutput() throws Exception {
        String vertex = """
                #version 120
                varying vec3 LightC;
                void main(){LightC=vec3(1.0);gl_Position=ftransform();}
                """;
        String fragment = """
                #version 460
                layout(location=18) in vec3 LightC;
                void main(){gl_FragColor=vec4(LightC,1.0);}
                """;
        var varyings = EntityVertexShaderAdapter.describeVaryings("gbuffers_entities", vertex);

        IOException error = assertThrows(IOException.class,
                () -> EntityShaderAdapter.adaptGeometryFragment(fragment, "gbuffers_entities", false,
                        false, -1, -1, ItemIdMap.UNMAPPED, true, false, varyings));
        assertTrue(error.getMessage().contains("declares location 18"));
        assertTrue(error.getMessage().contains("uses location 16"));
    }

    @Test
    void rejectsAttributesThatNewEntityDoesNotProvide() {
        String pack = "#version 120\nattribute vec4 at_tangent;\nvoid main(){gl_Position=ftransform();}\n";

        IOException error = assertThrows(IOException.class,
                () -> EntityVertexShaderAdapter.validate("gbuffers_entities", pack, false));
        assertTrue(error.getMessage().contains("at_tangent"));
    }

    @Test
    void rejectsMismatchedPackVaryingInterpolation() throws Exception {
        String vertex = "#version 120\nflat varying int LightC;\nvoid main(){LightC=1;gl_Position=ftransform();}\n";
        String fragment = "#version 120\nvarying int LightC;\nvoid main(){gl_FragColor=vec4(float(LightC));}";
        var varyings = EntityVertexShaderAdapter.describeVaryings("gbuffers_entities", vertex);

        IOException error = assertThrows(IOException.class,
                () -> EntityShaderAdapter.adaptGeometryFragment(fragment, "gbuffers_entities", false,
                        false, -1, -1, ItemIdMap.UNMAPPED, true, false, varyings));
        assertTrue(error.getMessage().contains("uses smooth interpolation"));
    }

    @Test
    void rejectsLegacyFrontColorInsteadOfMappingItToTheReadOnlyColorInput() {
        String pack = "#version 120\nvoid main(){gl_FrontColor=gl_Color;gl_Position=ftransform();}\n";

        IOException error = assertThrows(IOException.class,
                () -> EntityVertexShaderAdapter.validate("gbuffers_entities", pack, false));
        assertTrue(error.getMessage().contains("gl_FrontColor"));
    }

    @Test
    void removesUnusedMcEntityDeclarationFromBlockVertexProgram() throws Exception {
        String pack = """
                #version 120
                attribute vec4 mc_Entity;
                void main(){gl_Position=ftransform();}
                """;

        String adapted = EntityVertexShaderAdapter.validate("gbuffers_block", pack, false).source();

        assertFalse(adapted.contains("mc_Entity"), adapted);
    }

    @Test
    void removesUnusedMcEntityDeclarationFromEntityVertexProgram() throws Exception {
        String pack = """
                #version 120
                attribute vec4 mc_Entity;
                void main(){gl_Position=ftransform();}
                """;

        String adapted = EntityVertexShaderAdapter.validate("gbuffers_entities", pack, false).source();

        assertFalse(adapted.contains("mc_Entity"), adapted);
    }

    @Test
    void blockVertexProgramReadsDefaultMcEntityLikeIris() throws Exception {
        String pack = """
                #version 120
                attribute vec4 mc_Entity;
                void main(){gl_Position=ftransform()+vec4(mc_Entity.x);}
                """;

        String adapted = EntityVertexShaderAdapter.validate("gbuffers_block", pack, false).source();

        assertFalse(adapted.contains("mc_Entity"), adapted);
        assertTrue(adapted.contains("vec4(vec4(0.0, 0.0, 0.0, 1.0).x)"), adapted);
    }

    @Test
    void rejectsUnsupportedMultilineMcEntityAttributeLayout() {
        String pack = """
                #version 120
                layout(location = 12)
                    in vec4
                    mc_Entity;
                void main() { gl_Position = ftransform(); }
                """;

        IOException error = assertThrows(IOException.class,
                () -> EntityVertexShaderAdapter.validate("gbuffers_entities", pack, false));
        assertTrue(error.getMessage().contains("unsupported vertex attribute layout"), error.getMessage());
    }

    @Test
    void ignoresMcEntityInMultilineFunctionInputParameter() throws Exception {
        String pack = """
                #version 120
                vec4 helper(
                    in vec4 inputValue
                ) {
                    return mc_Entity + inputValue;
                }
                void main() { gl_Position = ftransform() + helper(vec4(0.0)); }
                """;

        String adapted = EntityVertexShaderAdapter.validate("gbuffers_entities", pack, false).source();

        assertTrue(adapted.contains("return vec4(0.0, 0.0, 0.0, 1.0) + inputValue;"), adapted);
    }
}
