package dev.veyra.shader;

import java.io.IOException;

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

        String adapted = EntityVertexShaderAdapter.adaptStage(
                nativeBlock, pack, "gbuffers_damagedblock", false).source();

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
}
