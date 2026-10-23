package dev.veyra.shader;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class TextureColorDistanceShaderAdapterTest {
    @Test
    void mergesCustomImageSamplersDeclaredInBothGraphicsStages() {
        var inputs = TextureColorDistanceShaderAdapter.SamplerInputs.empty()
                .withCustomImageSamplers(Set.of("vertexImage"))
                .withCustomImageSamplers(Set.of("fragmentImage"));

        assertEquals(Set.of("vertexImage", "fragmentImage"), inputs.customImageSamplers());
    }

    @Test
    void removesUnreachableSharedSamplerAndUniformDeclarations() throws IOException {
        String adapted = TextureColorDistanceShaderAdapter.adaptFragment("""
                uniform sampler2D colortex0;
                uniform int blockEntityId;
                vec4 ReadSharedSceneColor() { return texture2D(colortex0, vec2(0.5)); }
                int ReadSharedBlockId() { return blockEntityId; }
                uniform sampler2D texture;
                varying vec2 texcoord;
                void main() { gl_FragColor = texture2D(texture, texcoord); }
                """, "gbuffers_armor_glint", false);

        assertFalse(adapted.contains("ReadSharedSceneColor"), adapted);
        assertFalse(adapted.contains("ReadSharedBlockId"), adapted);
        assertFalse(adapted.contains("colortex0"), adapted);
        assertFalse(adapted.contains("blockEntityId"), adapted);
    }

    @Test
    void keepsRejectingSamplersReadFromTheFragmentEntrypoint() {
        IOException error = assertThrows(IOException.class, () ->
                TextureColorDistanceShaderAdapter.adaptFragment("""
                        uniform sampler2D colortex0;
                        varying vec2 texcoord;
                        void main() { gl_FragColor = texture2D(colortex0, texcoord); }
                        """, "gbuffers_armor_glint", false));
        assertTrue(error.getMessage().contains("unsupported sampler 'colortex0'"), error.getMessage());
    }

    @Test
    void ignoresSamplerDeclarationsInsideComments() throws IOException {
        String adapted = TextureColorDistanceShaderAdapter.adaptFragment("""
                /*
                #if defined Shadows && defined PCSS
                uniform sampler2D shadowtex0;
                #endif
                */
                uniform sampler2D texture;
                varying vec2 texcoord;
                void main() { gl_FragColor = texture2D(texture, texcoord); }
                """, "gbuffers_armor_glint", false);

        assertFalse(adapted.contains("shadowtex0"), adapted);
    }

    @Test
    void mapsIrisTextureAtlasAliasToMinecraftGlintSampler() throws IOException {
        String adapted = TextureColorDistanceShaderAdapter.adaptFragment("""
                uniform sampler2D tex;
                varying vec2 texcoord;
                void main() { gl_FragColor = texture2D(tex, texcoord); }
                """, "gbuffers_armor_glint", false);

        assertTrue(adapted.contains("#define tex Sampler0"), adapted);
        assertTrue(adapted.contains("veyraTextureSample(tex, texCoord0)"), adapted);
        assertFalse(adapted.contains("uniform sampler2D tex;"), adapted);
    }

    @Test
    void retainsCoreTextureSizeQueriesForSupportedTwoDimensionalSamplers() throws IOException {
        String adapted = TextureColorDistanceShaderAdapter.adaptFragment("""
                uniform sampler2D tex;
                varying vec2 texcoord;
                void main() {
                    vec2 texel = 1.0 / vec2(textureSize(tex, 0));
                    gl_FragColor = texture2D(tex, texcoord + texel);
                }
                """, "gbuffers_armor_glint", false);

        assertTrue(adapted.contains("textureSize(tex, 0)"), adapted);
        assertTrue(adapted.contains("#define tex Sampler0"), adapted);
    }

    @Test
    void mapsIrisFogColorThroughTheMinecraftFogUniformBlock() throws IOException {
        String adapted = TextureColorDistanceShaderAdapter.adaptFragment("""
                uniform vec3 fogColor;
                void main() { gl_FragColor = vec4(fogColor, 1.0); }
                """, "gbuffers_armor_glint", false);

        assertTrue(adapted.contains("uniform Fog"), adapted);
        assertTrue(adapted.contains("#define fogColor (FogColor.rgb)"), adapted);
        assertFalse(adapted.contains("uniform vec3 fogColor"), adapted);
    }

    @Test
    void bindsRenderStageInSkyBasicFragmentVariants() throws IOException {
        String source = """
                #version 120
                uniform int renderStage;
                void main() { gl_FragColor = vec4(float(renderStage)); }
                """;

        for (int stage : new int[] {1, 6, 2}) {
            String adapted = TextureColorDistanceShaderAdapter.adaptFragment(
                    source, "gbuffers_skybasic", Map.of(), false, false, stage);
            assertTrue(adapted.contains("const int renderStage = " + stage + ";"), adapted);
            assertFalse(adapted.contains("uniform int renderStage"), adapted);
        }
    }

    @Test
    void bindsRenderStageForNativeTextureFormatsWithoutPackVertexOutputs() throws IOException {
        String adapted = TextureColorDistanceShaderAdapter.adaptFragment("""
                #version 120
                uniform int renderStage;
                void main() { gl_FragColor = vec4(float(renderStage)); }
                """, "gbuffers_skytextured", false, false, IrisRenderStages.sunStage());

        assertTrue(adapted.contains("const int renderStage = 4;"), adapted);
        assertFalse(adapted.contains("uniform int renderStage"), adapted);
        assertFalse(adapted.contains("layout(location = 6) in vec2 texCoord0;"), adapted);
    }

    @Test
    void mapsLegacyFragmentOutputsToShaderpackColorAttachments() throws IOException {
        String adapted = TextureColorDistanceShaderAdapter.adaptFragment("""
                /* RENDERTARGETS: 0,4 */
                varying vec2 texcoord;
                void main() {
                    gl_FragData[0] = vec4(texcoord, 0.0, 1.0);
                    gl_FragData[1] = vec4(0.0, 1.0, 0.0, 1.0);
                }
                """, "gbuffers_armor_glint", false);

        assertTrue(adapted.contains("#define VEYRA_ENTITY_OUTPUT_0 layout(location = 0) out vec4 veyraFragData0;"), adapted);
        assertTrue(adapted.contains("#define VEYRA_ENTITY_OUTPUT_1 layout(location = 1) out vec4 veyraFragData1;"), adapted);
        assertTrue(adapted.contains("veyraFragData0 = vec4(texCoord0, 0.0, 1.0);"), adapted);
        assertTrue(adapted.contains("veyraFragData1 = vec4(0.0, 1.0, 0.0, 1.0);"), adapted);
        assertFalse(adapted.contains("gl_FragData"), adapted);
    }

    @Test
    void rejectsShaderpackOutputsAboveSupportedLocationRange() {
        IOException error = assertThrows(IOException.class, () ->
                TextureColorDistanceShaderAdapter.adaptFragment("""
                        /* RENDERTARGETS: 0,16 */
                        void main() { gl_FragData[1] = vec4(1.0); }
                        """, "gbuffers_armor_glint", false));
        assertTrue(error.getMessage().contains("colortex0..15"), error.getMessage());
    }

    @Test
    void linksBasicAndLineFragmentInputsToTheirVertexLocationsAndPreservesMrtOutputs() throws IOException {
        Map<String, TerrainVertexShaderAdapter.PackVarying> outputs = Map.of(
                "glColor", new TerrainVertexShaderAdapter.PackVarying("vec4", "flat", 0, "vertexColor", true),
                "lmCoord", new TerrainVertexShaderAdapter.PackVarying("vec2", "flat", 3,
                        "veyraBasicLightmapCoord", true),
                "normal", new TerrainVertexShaderAdapter.PackVarying("vec3", "smooth", 4,
                        "veyraBasicNormal", true),
                "upVec", new TerrainVertexShaderAdapter.PackVarying("vec3", "flat", 6,
                        "veyraPackVarying_upVec", false),
                "sunVec", new TerrainVertexShaderAdapter.PackVarying("vec3", "flat", 7,
                        "veyraPackVarying_sunVec", false));
        String adapted = TextureColorDistanceShaderAdapter.adaptFragment("""
                #version 130
                /* DRAWBUFFERS:06 */
                flat in vec2 lmCoord;
                in vec3 normal;
                flat in vec3 upVec, sunVec;
                flat in vec4 glColor;
                void main() {
                    gl_FragData[0] = glColor * vec4(normal + upVec + sunVec, 1.0);
                    gl_FragData[1] = vec4(lmCoord, 0.0, 1.0);
                }
                """, "gbuffers_basic", outputs);

        assertTrue(adapted.contains("layout(location = 3) flat in vec2 lmCoord;"));
        assertTrue(adapted.contains("layout(location = 4) in vec3 normal;"));
        assertTrue(adapted.contains("layout(location = 6) flat in vec3 upVec;"));
        assertTrue(adapted.contains("layout(location = 7) flat in vec3 sunVec;"));
        assertTrue(adapted.contains("layout(location = 0) flat in vec4 vertexColor;"));
        assertTrue(adapted.contains("VEYRA_ENTITY_OUTPUT_0"));
        assertTrue(adapted.contains("VEYRA_ENTITY_OUTPUT_1"));
        assertFalse(java.util.regex.Pattern.compile("(?m)^\\s*layout\\s*\\(\\s*location\\s*=")
                .matcher(adapted).find(), adapted);

        IOException interpolationMismatch = assertThrows(IOException.class,
                () -> TextureColorDistanceShaderAdapter.adaptFragment("""
                        in vec3 upVec;
                        void main() { gl_FragColor = vec4(upVec, 1.0); }
                        """, "gbuffers_basic", outputs));
        assertTrue(interpolationMismatch.getMessage().contains("interpolation qualifier"));

        IOException missingOutput = assertThrows(IOException.class,
                () -> TextureColorDistanceShaderAdapter.adaptFragment("""
                        in vec3 tangent;
                        void main() { gl_FragColor = vec4(tangent, 1.0); }
                        """, "gbuffers_basic", outputs));
        assertTrue(missingOutput.getMessage().contains("not written by its gbuffers vertex shader"));
    }

    @Test
    void bridgesBasicAndLineFragmentSamplersToMinecraftAndVeyraDescriptors() throws IOException {
        String source = """
                #version 130
                /* DRAWBUFFERS:06 */
                uniform sampler2D texture;
                uniform sampler2D lightmap;
                uniform sampler2D noisetex;
                uniform sampler2D specular;
                uniform sampler2DShadow shadowtex0;
                uniform sampler2D shadowtex1;
                uniform sampler2D shadowcolor0;
                uniform sampler2D gdepth;
                uniform sampler2D colortex6;
                flat in vec2 lmCoord;
                void main() {
                    float shadow = texture2DShadow(shadowtex0, vec3(lmCoord, 0.5));
                    gl_FragData[0] = texture2D(texture, lmCoord) + texture2D(lightmap, lmCoord)
                            + texture2D(noisetex, lmCoord) + texture2D(specular, lmCoord)
                            + texture2D(shadowtex1, lmCoord) + texture2D(shadowcolor0, lmCoord)
                            + texture2D(gdepth, lmCoord) + texture2D(colortex6, lmCoord) + vec4(shadow);
                    gl_FragData[1] = vec4(lmCoord, 0.0, 1.0);
                }
                """;
        Map<String, TerrainVertexShaderAdapter.PackVarying> outputs = Map.of(
                "lmCoord", new TerrainVertexShaderAdapter.PackVarying(
                        "vec2", "flat", 3, "veyraBasicLightmapCoord", true));

        String adapted = TextureColorDistanceShaderAdapter.adaptFragment(
                source, "gbuffers_basic", outputs, true, true);
        var samplers = TextureColorDistanceShaderAdapter.inspectSamplers(source, "gbuffers_basic", true, true);

        assertTrue(adapted.contains("#define lightmap Sampler2"), adapted);
        assertTrue(adapted.contains("#define noisetex VeyraNoiseSampler"), adapted);
        assertTrue(adapted.contains("#define specular VeyraSpecularSampler"), adapted);
        assertTrue(adapted.contains("#define shadowtex0 Sampler3"), adapted);
        assertTrue(adapted.contains("#define shadowtex1 VeyraOpaqueShadowSampler"), adapted);
        assertTrue(adapted.contains("#define shadowcolor0 VeyraShadowColorSampler"), adapted);
        assertTrue(adapted.contains("#define gdepth VeyraGeometryColor1Sampler"), adapted);
        assertTrue(adapted.contains("#define colortex6 VeyraGeometryColor6Sampler"), adapted);
        assertFalse(adapted.contains("uniform sampler2DShadow"), adapted);
        assertEquals(Set.of(1, 6), samplers.geometryColorInputs());
        assertTrue(samplers.usesLightmapSampler());
        assertTrue(samplers.usesNoiseSampler());
        assertTrue(samplers.usesSpecularSampler());
        assertTrue(samplers.usesShadowSampler());
        assertTrue(samplers.usesOpaqueShadow());
        assertTrue(samplers.usesShadowColor());
    }

    @Test
    void bridgesLightmapSamplerWithTheNativeTexturedVertexInterface() throws IOException {
        String source = """
                #version 120
                uniform sampler2D lightmap;
                varying vec2 texcoord;
                void main() { gl_FragColor = texture2D(lightmap, texcoord); }
                """;
        String adapted = TextureColorDistanceShaderAdapter.adaptFragment(
                source, "gbuffers_textured", false, false, true, false, true);
        var samplers = TextureColorDistanceShaderAdapter.inspectSamplers(
                source, "gbuffers_textured", false, true);

        assertTrue(adapted.contains("uniform sampler2D Sampler2;"), adapted);
        assertTrue(adapted.contains("#define lightmap Sampler2"), adapted);
        assertTrue(adapted.contains("veyraTextureSample(lightmap, texCoord0)"), adapted);
        assertFalse(adapted.contains("uniform sampler2D lightmap;"), adapted);
        assertTrue(samplers.usesLightmapSampler());
    }

    @Test
    void worldBorderFallbackDoesNotRequireAnEntityOverlayInput() throws IOException {
        String source = """
                #version 120
                uniform vec4 entityColor;
                void main() { gl_FragColor = entityColor; }
                """;

        String adapted = TextureColorDistanceShaderAdapter.adaptFragment(
                source, "gbuffers_textured", false, false, true, false, true, false);

        assertTrue(adapted.contains("const vec4 entityColor = vec4(0.0);"), adapted);
        assertFalse(adapted.contains("#define entityColor overlayColor"), adapted);
    }

    @Test
    void rejectsBasicAndLineFragmentSamplersMissingFromTheNativePipeline() {
        Map<String, TerrainVertexShaderAdapter.PackVarying> outputs = Map.of(
                "lmCoord", new TerrainVertexShaderAdapter.PackVarying(
                        "vec2", "flat", 3, "veyraBasicLightmapCoord", true));
        IOException lightmapError = assertThrows(IOException.class, () ->
                TextureColorDistanceShaderAdapter.adaptFragment("""
                        uniform sampler2D lightmap;
                        flat in vec2 lmCoord;
                        void main() { gl_FragColor = texture2D(lightmap, lmCoord); }
                        """, "gbuffers_line", outputs, false, false));
        assertTrue(lightmapError.getMessage().contains("lightmap sampler"), lightmapError.getMessage());

        IOException shadowError = assertThrows(IOException.class, () ->
                TextureColorDistanceShaderAdapter.adaptFragment("""
                        uniform sampler2D shadowtex0;
                        flat in vec2 lmCoord;
                        void main() { gl_FragColor = texture2D(shadowtex0, lmCoord); }
                        """, "gbuffers_line", outputs, false, false));
        assertTrue(shadowError.getMessage().contains("shadowtex0"), shadowError.getMessage());
    }

    @Test
    void bridgesLightmapOnLinePipelinesWhenVeyraSuppliesTheSampler() throws IOException {
        Map<String, TerrainVertexShaderAdapter.PackVarying> outputs = Map.of(
                "lmCoord", new TerrainVertexShaderAdapter.PackVarying(
                        "vec2", "flat", 3, "veyraBasicLightmapCoord", true));
        String source = """
                uniform sampler2D lightmap;
                flat in vec2 lmCoord;
                void main() { gl_FragColor = texture2D(lightmap, lmCoord); }
                """;

        String adapted = TextureColorDistanceShaderAdapter.adaptFragment(
                source, "gbuffers_line", outputs, false, true);
        var samplers = TextureColorDistanceShaderAdapter.inspectSamplers(
                source, "gbuffers_line", false, true);

        assertTrue(adapted.contains("uniform sampler2D Sampler2;"), adapted);
        assertTrue(adapted.contains("#define lightmap Sampler2"), adapted);
        assertTrue(samplers.usesLightmapSampler());
    }

    @Test
    void doesNotTreatALocalLightmapCoordinateAsAMissingSampler() throws IOException {
        var samplers = TextureColorDistanceShaderAdapter.inspectSamplers("""
                flat in vec2 lmCoord;
                vec4 shadeBasic() {
                    vec2 lightmap = clamp(lmCoord, vec2(0.0), vec2(1.0));
                    return vec4(lightmap, 0.0, 1.0);
                }
                void main() { gl_FragColor = shadeBasic(); }
                """, "gbuffers_basic", true, false);

        assertFalse(samplers.usesLightmapSampler());
    }

    @Test
    void renamesPackShadowComparisonHelpersBeforeAddingItsVulkanBridge() throws IOException {
        String adapted = TextureColorDistanceShaderAdapter.adaptFragment("""
                uniform sampler2DShadow shadowtex0;
                float texture2DShadow(sampler2DShadow sourceSampler, vec3 sourceCoord) {
                    return shadow2D(sourceSampler, sourceCoord).x;
                }
                void main() {
                    float shadow = texture2DShadow(shadowtex0, vec3(0.5));
                    gl_FragColor = vec4(shadow);
                }
                """, "gbuffers_basic", true, true, true, true, true);

        assertTrue(adapted.contains("float veyraShaderPackTexture2DShadow(sampler2D sourceSampler, vec3 sourceCoord)"), adapted);
        assertTrue(adapted.contains("return veyraTextureShadowSample(sourceSampler, sourceCoord).x;"), adapted);
        assertTrue(adapted.contains("float shadow = veyraShaderPackTexture2DShadow(shadowtex0, vec3(0.5));"), adapted);
        assertFalse(adapted.contains("float texture2DShadow("), adapted);
    }
}
