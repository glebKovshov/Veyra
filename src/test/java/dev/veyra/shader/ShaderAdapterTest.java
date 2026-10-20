package dev.veyra.shader;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class ShaderAdapterTest {
    @TestFactory
    List<DynamicTest> adaptsTerrainAndEntityExamples() throws IOException {
        try (Stream<Path> files = Files.walk(Path.of("examples"))) {
            return files.filter(Files::isRegularFile).filter(path -> {
                String name = path.getFileName().toString();
                return name.matches("gbuffers_(terrain(?:_solid|_cutout)?|water|entities(?:_translucent)?|clouds)\\.fsh");
            }).sorted().map(path -> DynamicTest.dynamicTest(path.toString(), () -> {
                String name = path.getFileName().toString();
                String source = ShaderIncludes.expand(name, Files.readString(path), included -> {
                    Path file = path.getParent().resolve(included);
                    return Files.exists(file) ? Optional.of(Files.readString(file)) : Optional.empty();
                });
                source = GlslPreprocessor.process(name, source);
                Path properties = path.getParent().resolve("shaders.properties");
                CustomUniforms.activate(Files.exists(properties)
                        ? CustomUniforms.parse(Files.readString(properties)) : CustomUniforms.EMPTY);
                String adapted;
                try {
                if (name.equals("gbuffers_clouds.fsh")) {
                    adapted = CloudShaderAdapter.adaptFragment(source);
                } else if (name.startsWith("gbuffers_entities")) {
                    adapted = EntityShaderAdapter.adaptFragment(source, name.substring(0, name.length()-4), false, Files.exists(path.getParent().resolve("shadow.fsh")));
                } else {
                    adapted = TerrainShaderAdapter.adaptFragment(source, true, name.equals("gbuffers_water.fsh"));
                }
                } finally {
                    CustomUniforms.activate(CustomUniforms.EMPTY);
                }
                assertTrue(adapted.startsWith("#version 460\n"));
                assertTrue(adapted.contains("void main("));
                assertFalse(adapted.contains("#version 120"));
            })).toList();
        }
    }

    @Test
    void rejectsUnsupportedCustomUniformWithItsName() {
        IOException error = assertThrows(IOException.class, () -> TerrainShaderAdapter.adaptFragment(
                "uniform float customUniform;\nvoid main() { gl_FragColor = vec4(customUniform); }\n"));
        assertTrue(error.getMessage().contains("customUniform"));
    }

    @Test
    void adaptsCamelCaseLightmapCoordToTerrainLightmapInput() throws IOException {
        String adapted = TerrainShaderAdapter.adaptFragment("""
                #version 120
                varying vec2 lmCoord;
                void main() { gl_FragColor = vec4(lmCoord, 0.0, 1.0); }
                """);

        assertTrue(adapted.contains("layout(location = 5) in vec2 veyraLightmapCoord;"));
        assertTrue(adapted.contains("#define lmCoord veyraLightmapCoord"));
        assertFalse(adapted.contains("varying vec2 lmCoord;"));
    }

    @Test
    void adaptsPackColorToTerrainVertexColorInput() throws IOException {
        String adapted = TerrainShaderAdapter.adaptFragment("""
                #version 120
                varying vec4 color;
                void main() { gl_FragColor = color; }
                """);

        assertTrue(adapted.contains("layout(location = 0) in vec4 veyraVertexColor;"));
        assertTrue(adapted.contains("#define color veyraVertexColor"));
        assertFalse(adapted.contains("varying vec4 color;"));
    }

    @Test
    void adaptsTerrainViewPositionToViewSpaceVarying() throws IOException {
        String fragment = TerrainShaderAdapter.adaptFragment("""
                #version 120
                varying vec3 viewPos;
                varying float dist;
                void main() { gl_FragColor = vec4(abs(viewPos) / 64.0, 1.0); }
                """);
        String vertex = TerrainShaderAdapter.adaptVertex("""
                #version 460
                layout (location = 2) in uint PackedColor;
                layout (location = 1) in uvec2 UV0;
                layout (location = 1) out vec2 texCoord0;
                layout (location = 4) out flat float fadeFactor;
                const float UV_INV = 1.0 / 32768.0;
                layout(binding = 1) uniform UBO {
                    int UseRgss;
                };
                void main() {
                    const vec3 pos = getVertexPosition();
                    vertexColor = Color * sample_lightmap2(Sampler2, Position.a);
                    texCoord0 = UV0 * UV_INV;
                }
                """);

        assertTrue(fragment.contains("layout(location = 10) in vec3 veyraViewPosition;"));
        assertTrue(fragment.contains("#define viewPos veyraViewPosition"));
        assertTrue(fragment.contains("layout(location = 16) in float veyraTerrainDistance;"));
        assertTrue(fragment.contains("#define dist veyraTerrainDistance"));
        assertFalse(fragment.contains("varying vec3 viewPos;"));
        assertFalse(fragment.contains("varying float dist;"));
        assertTrue(vertex.contains("layout (location = 10) out vec3 veyraViewPosition;"));
        assertTrue(vertex.contains("layout (location = 16) out float veyraTerrainDistance;"));
        assertTrue(vertex.contains("float near;\n    float far;"));
        assertTrue(vertex.contains("mat4 gbufferModelView;"));
        assertTrue(vertex.indexOf("float near;") < vertex.indexOf("mat4 gbufferModelView;"));
        assertTrue(vertex.contains("veyraViewPosition = (gbufferModelView * vec4(pos, 1.0)).xyz;"));
        assertTrue(vertex.contains("veyraTerrainDistance = length(veyraViewPosition);"));
        assertTrue(vertex.contains("layout (location = 3) in uvec2 MidTexCoord;"));
        assertTrue(vertex.contains("layout (location = 4) in int VeyraTerrainMaterialId;"));
        assertTrue(vertex.contains("layout (location = 5) in float VeyraTerrainRenderType;"));
        assertTrue(vertex.contains("layout (location = 11) out flat int veyraTerrainMaterialId;"));
        assertTrue(vertex.contains("layout (location = 13) out flat vec2 veyraMcEntity;"));
        assertTrue(vertex.contains("layout (location = 14) out flat vec2 veyraAbsMidCoordPos;"));
        assertTrue(vertex.contains("layout (location = 15) out flat vec2 veyraMidCoord;"));
        assertTrue(vertex.contains("mat4 textureMatrix;"));
        assertTrue(vertex.contains("texCoord0 = (textureMatrix * vec4(veyraRawTexCoord, 0.0, 1.0)).xy;"));
        assertTrue(vertex.contains("veyraMidCoord = (textureMatrix * vec4(veyraRawMidTexCoord, 0.0, 1.0)).xy;"));
        assertTrue(vertex.contains("const vec2 veyraMidCoordOffset = texCoord0 - veyraMidCoord;"));
        assertTrue(vertex.contains("veyraSignMidCoordPos = sign(veyraMidCoordOffset);"));
        assertTrue(vertex.contains("veyraAbsMidCoordPos = abs(veyraMidCoordOffset);"));
        assertTrue(vertex.contains("veyraBslTexCoord = vec4(sign(veyraMidCoordOffset) * 0.5 + 0.5, 0.0, 0.0);"));
        assertTrue(vertex.contains("veyraBslTexCoordAM = vec4(min(texCoord0, veyraMidCoord - veyraMidCoordOffset), abs(veyraMidCoordOffset) * 2.0);"));
        assertTrue(vertex.contains("veyraMcEntity = vec2(float(VeyraTerrainMaterialId), VeyraTerrainRenderType);"));
    }

    @Test
    void bridgesIrisTerrainMaterialAndFluidInputs() throws IOException {
        for (String materialType : List.of("int", "float")) {
            String adapted = TerrainShaderAdapter.adaptFragment("""
                    #version 120
                    flat in %s mat;
                    flat in vec2 mc_Entity;
                    void main() {
                        gl_FragColor = mat == 27101 && mc_Entity.y < 0.0
                                ? vec4(1.0, 0.0, 1.0, 1.0) : vec4(0.0, 0.0, 0.0, 1.0);
                    }
                    """.formatted(materialType));

            assertTrue(adapted.contains("layout(location = 11) flat in int veyraTerrainMaterialId;"));
            assertTrue(adapted.contains("layout(location = 13) flat in vec2 veyraMcEntity;"));
            assertTrue(adapted.contains(materialType.equals("float")
                    ? "#define mat float(veyraTerrainMaterialId)" : "#define mat veyraTerrainMaterialId"));
            assertTrue(adapted.contains("#define mc_Entity veyraMcEntity"));
            assertFalse(adapted.contains("flat in " + materialType + " mat;"));
            assertFalse(adapted.contains("flat in vec2 mc_Entity;"));
        }
    }

    @Test
    void bridgesBslTerrainRecolorFromPackBlockIdCategory() throws IOException {
        String adapted = TerrainShaderAdapter.adaptFragment("""
                #version 120
                varying float recolor;
                void main() { gl_FragColor = recolor > 0.5 ? vec4(1.0) : vec4(0.0); }
                """);

        assertTrue(adapted.contains("layout(location = 11) flat in int veyraTerrainMaterialId;"));
        assertTrue(adapted.contains("#define recolor ((veyraTerrainMaterialId / 100 == 151 || "
                + "veyraTerrainMaterialId / 100 == 155 || veyraTerrainMaterialId / 100 == 156) ? 1.0 : 0.0)"));
        assertFalse(adapted.contains("varying float recolor;"));
    }

    @Test
    void bridgesBslTerrainDirectionalInputsFromIrisUniforms() throws IOException {
        String adapted = TerrainShaderAdapter.adaptFragment("""
                #version 120
                varying vec3 sunVec, upVec, eastVec;
                void main() { gl_FragColor = vec4(normalize(sunVec) + normalize(upVec) + normalize(eastVec), 1.0); }
                """);

        assertTrue(adapted.contains("vec3 sunPosition;"));
        assertTrue(adapted.contains("mat4 gbufferModelView;"));
        assertTrue(adapted.contains("#define sunVec normalize(sunPosition)"));
        assertTrue(adapted.contains("#define upVec normalize(gbufferModelView[1].xyz)"));
        assertTrue(adapted.contains("#define eastVec normalize(gbufferModelView[0].xyz)"));
        assertFalse(adapted.contains("varying vec3 sunVec"));
    }

    @Test
    void bridgesBslAdvancedMaterialUvVaryingsFromTerrainTextureCenters() throws IOException {
        String adapted = TerrainShaderAdapter.adaptFragment("""
                #version 120
                varying vec4 vTexCoord, vTexCoordAM;
                void main() { gl_FragColor = vec4(vTexCoord.st * vTexCoordAM.pq + vTexCoordAM.st, 0.0, 1.0); }
                """);

        assertTrue(adapted.contains("layout(location = 17) in vec4 veyraBslTexCoord;"));
        assertTrue(adapted.contains("layout(location = 18) in vec4 veyraBslTexCoordAM;"));
        assertTrue(adapted.contains("#define vTexCoord veyraBslTexCoord"));
        assertTrue(adapted.contains("#define vTexCoordAM veyraBslTexCoordAM"));
        assertFalse(adapted.contains("varying vec4 vTexCoord"));
    }

    @Test
    void bridgesComplementarySignMidCoordPosFromTerrainUvCenters() throws IOException {
        String adapted = TerrainShaderAdapter.adaptFragment("""
                #version 120
                varying vec2 signMidCoordPos;
                void main() {
                    gl_FragColor = signMidCoordPos.x > 0.0 && signMidCoordPos.y > 0.0
                            ? vec4(1.0, 0.0, 1.0, 1.0) : vec4(0.0, 1.0, 0.0, 1.0);
                }
                """);

        assertTrue(adapted.contains("layout(location = 12) in vec2 veyraSignMidCoordPos;"));
        assertTrue(adapted.contains("#define signMidCoordPos veyraSignMidCoordPos"));
        assertFalse(adapted.contains("varying vec2 signMidCoordPos;"));
    }

    @Test
    void bridgesComplementaryAbsoluteMidCoordPosFromTerrainUvCenters() throws IOException {
        String adapted = TerrainShaderAdapter.adaptFragment("""
                #version 120
                varying vec2 absMidCoordPos;
                void main() { gl_FragColor = vec4(absMidCoordPos, 0.0, 1.0); }
                """);

        assertTrue(adapted.contains("layout(location = 14) flat in vec2 veyraAbsMidCoordPos;"));
        assertTrue(adapted.contains("#define absMidCoordPos veyraAbsMidCoordPos"));
        assertFalse(adapted.contains("varying vec2 absMidCoordPos;"));
    }

    @Test
    void bridgesComplementaryMidCoordFromTextureTransformedTerrainUvCenters() throws IOException {
        String adapted = TerrainShaderAdapter.adaptFragment("""
                #version 120
                flat in vec2 midCoord;
                void main() { gl_FragColor = vec4(midCoord, 0.0, 1.0); }
                """);

        assertTrue(adapted.contains("layout(location = 15) flat in vec2 veyraMidCoord;"));
        assertTrue(adapted.contains("#define midCoord veyraMidCoord"));
        assertFalse(adapted.contains("flat in vec2 midCoord;"));
    }

    @Test
    void bridgesTerrainAndWaterPlayerSpacePositions() throws IOException {
        for (String input : List.of("vertexPos", "playerPos")) {
            String adapted = TerrainShaderAdapter.adaptFragment("""
                    #version 120
                    varying vec3 %s;
                    void main() { gl_FragColor = vec4(abs(%s) / 64.0, 1.0); }
                    """.formatted(input, input));
            assertTrue(adapted.contains("layout(location = 7) in vec3 veyraTerrainPosition;"));
            assertTrue(adapted.contains("#define " + input + " veyraTerrainPosition"));
            assertFalse(adapted.contains("varying vec3 " + input + ";"));
        }
    }

    @Test
    void reconstructsTerrainTangentSpaceInputsFromPositionAndUvGradients() throws IOException {
        String adapted = TerrainShaderAdapter.adaptFragment("""
                #version 120
                flat in vec3 normal, binormal, tangent;
                in vec3 viewVector;
                void main() {
                    gl_FragColor = vec4(abs(normal + binormal + tangent + viewVector) / 64.0, 1.0);
                }
                """);

        assertTrue(adapted.contains("dFdx(veyraViewPosition)"));
        assertTrue(adapted.contains("dFdy(veyraViewPosition)"));
        assertTrue(adapted.contains("dFdx(veyraTexCoord)"));
        assertTrue(adapted.contains("dFdy(veyraTexCoord)"));
        assertTrue(adapted.contains("#define normal veyraTerrainNormal()"));
        assertTrue(adapted.contains("#define binormal veyraTerrainBinormal()"));
        assertTrue(adapted.contains("#define tangent veyraTerrainTangent()"));
        assertTrue(adapted.contains("#define viewVector veyraTerrainViewVector()"));
        assertTrue(adapted.contains("void veyraShaderPackMain()"));
        assertTrue(adapted.contains("veyraInitializeTerrainTangentFrame();\n    veyraShaderPackMain();"));
        assertFalse(adapted.contains("flat in vec3 normal, binormal, tangent;"));
        assertFalse(adapted.contains("in vec3 viewVector;"));
    }

    @Test
    void bridgesRawTerrainVertexColor() throws IOException {
        String adapted = TerrainShaderAdapter.adaptFragment("""
                #version 120
                varying vec4 glColorRaw;
                void main() { gl_FragColor = glColorRaw; }
                """);

        assertTrue(adapted.contains("layout(location = 0) in vec4 veyraVertexColor;"));
        assertTrue(adapted.contains("#define glColorRaw veyraVertexColor"));
        assertFalse(adapted.contains("varying vec4 glColorRaw;"));
    }

    @Test
    void encodesPerQuadMidpointWithTheCompressedTerrainUvScale() {
        assertEquals(0.5F, TerrainMidTexCoord.center(0.0F, 1.0F, 1.0F, 0.0F));
        assertEquals((short) 16384, TerrainMidTexCoord.pack(0.5F));
        assertEquals(Short.MIN_VALUE, TerrainMidTexCoord.pack(1.0F));
        assertEquals(28, TerrainMidTexCoord.VERTEX_FORMAT.getVertexSize());
        assertEquals(20, TerrainMidTexCoord.MATERIAL_ID_OFFSET);
        assertEquals(24, TerrainMidTexCoord.RENDER_TYPE_OFFSET);
    }

    @Test
    void reportsTheSpecificUnsupportedTerrainInput() {
        for (String input : List.of("mc_Entity", "mc_midTexCoord", "midTexCoord", "viewNormal",
                "tangent", "binormal", "viewVector", "at_tangent")) {
            IOException error = assertThrows(IOException.class, () -> TerrainShaderAdapter.adaptFragment(
                    "void main() { gl_FragColor = vec4(" + input + "); }\n"));
            assertTrue(error.getMessage().contains("input '" + input + "'"), error.getMessage());
        }

        IOException wrongMaterialType = assertThrows(IOException.class, () -> TerrainShaderAdapter.adaptFragment(
                "in vec3 mat;\nvoid main() { gl_FragColor = vec4(mat, 1.0); }\n"));
        assertTrue(wrongMaterialType.getMessage().contains("mat' must use int or float"));
    }

    @Test
    void keepsLegacyTerrainFragmentDataOutputsSupported() throws IOException {
        String adapted = TerrainShaderAdapter.adaptFragment("""
                #version 120
                /* DRAWBUFFERS:0 */
                void main() { gl_FragData[0] = vec4(1.0); }
                """);

        assertTrue(adapted.contains("veyraFragData0 = vec4(1.0);"));
        assertFalse(adapted.contains("gl_FragData"));
    }

    @Test
    void adaptsIrisMoonPhaseUniformForGeometryPrograms() throws IOException {
        String adapted = CloudShaderAdapter.adaptFragment("""
                #version 120
                uniform int moonPhase;
                varying vec4 color;
                void main() { gl_FragColor = color * (float(moonPhase) / 7.0); }
                """);
        assertTrue(adapted.contains("int moonPhase;"));
        assertTrue(adapted.contains("float(moonPhase)"));
        assertFalse(adapted.contains("uniform int moonPhase;"));
    }

    @Test
    void removesUnusedGlobalCloudSamplerDeclarations() throws IOException {
        String adapted = CloudShaderAdapter.adaptFragment("""
                #version 120
                uniform sampler2D colortex0;
                varying vec4 color;
                void main() { gl_FragColor = color; }
                """);

        assertFalse(adapted.contains("colortex0"));
        assertTrue(adapted.contains("gl_FragColor = color;"));
    }

    @Test
    void adaptsCloudSceneColorSampling() throws IOException {
        CloudShaderAdapter.AdaptedFragment adapted = CloudShaderAdapter.adapt("""
                #version 120
                uniform sampler2D colortex0;
                void main() {
                    vec2 uv = gl_FragCoord.xy / vec2(textureSize(colortex0, 0));
                    gl_FragColor = vec4(texture2D(colortex0, uv).rgb, 1.0);
                }
                """);

        assertTrue(adapted.usesSceneColorSampler());
        assertTrue(adapted.source().contains("layout(binding = 2) uniform sampler2D VeyraCloudSceneSampler;"));
        assertTrue(adapted.source().contains("#define colortex0 VeyraCloudSceneSampler"));
        assertTrue(adapted.source().contains("texture(colortex0, uv)"));
        assertFalse(adapted.source().contains("texture2D("));
        assertFalse(adapted.source().contains("uniform sampler2D colortex0;"));
    }

    @Test
    void adaptsCloudNoiseTextureSampling() throws IOException {
        CloudShaderAdapter.AdaptedFragment adapted = CloudShaderAdapter.adapt("""
                #version 120
                uniform sampler2D noisetex;
                void main() { gl_FragColor = texture2D(noisetex, vec2(0.5)); }
                """);

        assertFalse(adapted.usesSceneColorSampler());
        assertTrue(adapted.usesNoiseSampler());
        assertTrue(adapted.source().contains("layout(binding = 3) uniform sampler2D VeyraNoiseSampler;"));
        assertTrue(adapted.source().contains("#define noisetex VeyraNoiseSampler"));
        assertTrue(adapted.source().contains("texture(noisetex, vec2(0.5))"));
        assertFalse(adapted.source().contains("uniform sampler2D noisetex;"));
    }

    @Test
    void lowersLegacyCloudNoiseTextureLodSampling() throws IOException {
        CloudShaderAdapter.AdaptedFragment adapted = CloudShaderAdapter.adapt("""
                #version 120
                uniform sampler2D noisetex;
                void main() { gl_FragColor = texture2DLod(noisetex, vec2(0.5), 0.0); }
                """);

        assertTrue(adapted.usesNoiseSampler());
        assertTrue(adapted.source().contains("textureLod(noisetex, vec2(0.5), 0.0)"));
        assertFalse(adapted.source().contains("texture2DLod("));
    }

    @Test
    void rejectsActiveCloudSamplersOtherThanSceneColorAndNoise() {
        IOException error = assertThrows(IOException.class, () -> CloudShaderAdapter.adapt("""
                #version 120
                uniform sampler2D gaux1;
                void main() { gl_FragColor = texture2D(gaux1, vec2(0.5)); }
                """));
        assertTrue(error.getMessage().contains("gaux1"));
    }

    @Test
    void adaptsCloudVertexShaderToVulkanModPositionColorInputs() throws IOException {
        String vulkanModVertex = """
                #version 450
                layout(binding = 0) uniform UBO0 { mat4 MVP; vec3 ModelOffset; };
                layout(binding = 1) uniform UBO { vec4 ColorModulator; float FogCloudsEnd; };
                layout(location = 0) in vec3 Position;
                layout(location = 1) in vec4 Color;
                layout(location = 0) out vec4 vertexColor;
                layout(location = 1) out float vertexDistance;
                void main() {
                    gl_Position = MVP * vec4(Position, 1.0);
                    vertexColor = Color * ColorModulator;
                    vertexDistance = length(Position + ModelOffset);
                }
                """;
        String adapted = CloudShaderAdapter.adaptVertex(vulkanModVertex, """
                #version 120
                varying vec4 color;
                varying float vertexDistance;
                void main() {
                    gl_Position = ftransform();
                    color = gl_Color;
                    vertexDistance = length(gl_Vertex.xyz);
                }
                """);

        assertTrue(adapted.contains("#define ftransform() (MVP * vec4(Position, 1.0))"));
        assertTrue(adapted.contains("#define color vertexColor"));
        assertTrue(adapted.contains("vertexColor = Color * ColorModulator;"));
        assertTrue(adapted.contains("gl_Position = ftransform();"));
        assertTrue(adapted.contains("color = gl_Color;"));
        assertFalse(adapted.contains("varying vec4 color;"));
        assertFalse(adapted.contains("varying float vertexDistance;"));
    }

    @Test
    void rejectsCloudVertexShaderInputsMissingFromVulkanModMesh() {
        String vulkanModVertex = "#version 450\nvoid main() { gl_Position = vec4(0.0); }\n";
        IOException error = assertThrows(IOException.class, () -> CloudShaderAdapter.adaptVertex(vulkanModVertex, """
                #version 120
                varying vec2 coord0;
                void main() {
                    gl_Position = ftransform();
                    coord0 = vec2(0.0);
                }
                """));
        assertTrue(error.getMessage().contains("attributes or transforms"), error.getMessage());
    }

    @Test
    void mapsIrisFogUniformsIntoCloudPipelineFields() throws IOException {
        CloudShaderAdapter.AdaptedFragment adapted = CloudShaderAdapter.adapt("""
                #version 120
                uniform vec3 fogColor;
                uniform float fogStart;
                uniform float fogEnd;
                varying vec4 color;
                void main() {
                    float fog = clamp((fogEnd - 1.0) / max(fogEnd - fogStart, 1.0), 0.0, 1.0);
                    gl_FragColor = vec4(mix(color.rgb, fogColor, fog), color.a);
                }
                """);

        assertTrue(adapted.source().contains("vec4 FogColor;"));
        assertTrue(adapted.source().contains("float FogEnvironmentalStart;"));
        assertTrue(adapted.source().contains("float FogEnvironmentalEnd;"));
        assertTrue(adapted.source().contains("#define fogColor (FogColor.rgb)"));
        assertTrue(adapted.source().contains("#define fogStart FogEnvironmentalStart"));
        assertTrue(adapted.source().contains("#define fogEnd FogEnvironmentalEnd"));
        assertFalse(adapted.source().contains("uniform vec3 fogColor;"));
        assertEquals(List.of(
                new ShaderPackUniformAdapter.UniformField("vec4", "FogColor"),
                new ShaderPackUniformAdapter.UniformField("float", "FogEnvironmentalStart"),
                new ShaderPackUniformAdapter.UniformField("float", "FogEnvironmentalEnd")),
                adapted.uniformFields());
    }

    @Test
    void mapsLegacyFogMembersToMinecraftFogBlock() throws IOException {
        IrisFogUniformAdapter.AdaptedShader adapted = IrisFogUniformAdapter.extract("""
                // An unused legacy field in a comment must not block adaptation: gl_Fog.density
                vec4 oldFogColor = gl_Fog.color;
                float fogStart = gl_Fog.start;
                float fogEnd = gl_Fog.end;
                float fogScale = gl_Fog.scale;
                """, "gbuffers_entities");

        assertFalse(ShaderSource.maskBlockAndLineComments(adapted.source()).contains("gl_Fog"));
        assertTrue(adapted.source().contains("vec4 oldFogColor = FogColor;"));
        assertTrue(adapted.source().contains("float fogStart = FogEnvironmentalStart;"));
        assertTrue(adapted.source().contains("float fogEnd = FogEnvironmentalEnd;"));
        assertTrue(adapted.source().contains("1.0 / (FogEnvironmentalEnd - FogEnvironmentalStart)"));
        assertTrue(adapted.bridge().contains("layout(std140) uniform Fog"));
        assertTrue(adapted.bridge().contains("float FogEnvironmentalStart;"));
        assertTrue(adapted.bridge().contains("float FogEnvironmentalEnd;"));
        assertTrue(adapted.fields().isEmpty());
    }

    @Test
    void rejectsLegacyFogDensityWithoutAValidatedRuntimeValue() {
        IOException error = assertThrows(IOException.class, () -> IrisFogUniformAdapter.extract(
                "float density = gl_Fog.density;", "gbuffers_entities"));
        assertTrue(error.getMessage().contains("gl_Fog.density"), error.getMessage());
    }

    @Test
    void adaptsIrisShadowFadeUniformForTerrainAndPostPrograms() throws IOException {
        String terrain = TerrainShaderAdapter.adaptFragment("""
                #version 120
                uniform float shadowFade;
                void main() { gl_FragColor = vec4(shadowFade); }
                """);
        assertTrue(terrain.contains("float shadowFade;"));
        assertFalse(terrain.contains("uniform float shadowFade;"));

        ShaderPackUniformAdapter.AdaptedShader post = ShaderPackUniformAdapter.extract(
                "uniform float shadowFade;\nvoid main() { }\n", "composite");
        assertTrue(post.declarations().contains("uniform float shadowFade;"));
        assertTrue(post.fields().contains(new ShaderPackUniformAdapter.UniformField("float", "shadowFade")));
    }

    @Test
    void adaptsIrisDimensionUniformsForTerrainAndPostPrograms() throws IOException {
        String terrain = TerrainShaderAdapter.adaptFragment("""
                #version 120
                uniform int bedrockLevel;
                uniform float cloudHeight;
                uniform float endFlashIntensity;
                varying vec2 texcoord;
                void main() { gl_FragColor = vec4(float(bedrockLevel) / 128.0 + cloudHeight + endFlashIntensity + texcoord.x); }
                """);
        assertTrue(terrain.contains("int bedrockLevel;"));
        assertTrue(terrain.contains("float cloudHeight;"));
        assertTrue(terrain.contains("float endFlashIntensity;"));
        assertFalse(terrain.contains("uniform int bedrockLevel;"));
        assertFalse(terrain.contains("uniform float cloudHeight;"));
        assertFalse(terrain.contains("uniform float endFlashIntensity;"));

        ShaderPackUniformAdapter.AdaptedShader post = ShaderPackUniformAdapter.extract(
                "uniform int bedrockLevel;\nuniform float cloudHeight;\nuniform float endFlashIntensity;\nvoid main() {}\n", "composite");
        assertTrue(post.declarations().contains("uniform int bedrockLevel;"));
        assertTrue(post.declarations().contains("uniform float cloudHeight;"));
        assertTrue(post.declarations().contains("uniform float endFlashIntensity;"));
        assertTrue(post.fields().contains(new ShaderPackUniformAdapter.UniformField("int", "bedrockLevel")));
        assertTrue(post.fields().contains(new ShaderPackUniformAdapter.UniformField("float", "cloudHeight")));
        assertTrue(post.fields().contains(new ShaderPackUniformAdapter.UniformField("float", "endFlashIntensity")));
        assertEquals("void main() {}", post.source().trim());
    }

    @Test
    void adaptsIrisSmoothedEyeBrightnessForCloudPrograms() throws IOException {
        CloudShaderAdapter.AdaptedFragment adapted = CloudShaderAdapter.adapt("""
                #version 120
                uniform ivec2 eyeBrightness;
                uniform ivec2 eyeBrightnessSmooth;
                varying vec4 color;
                void main() { gl_FragColor = color * (float(eyeBrightness.y + eyeBrightnessSmooth.y) / 480.0); }
                """);

        assertTrue(adapted.source().contains("ivec2 eyeBrightness;"));
        assertTrue(adapted.source().contains("ivec2 eyeBrightnessSmooth;"));
        assertTrue(adapted.uniformFields().stream().anyMatch(field ->
                field.type().equals("ivec2") && field.name().equals("eyeBrightnessSmooth")));
        assertTrue(adapted.uniformFields().stream().anyMatch(field ->
                field.type().equals("ivec2") && field.name().equals("eyeBrightness")));
        assertFalse(adapted.source().contains("uniform ivec2 eyeBrightness;"));
        assertFalse(adapted.source().contains("uniform ivec2 eyeBrightnessSmooth;"));
    }

    @Test
    void adaptsIrisEntityColorInputToVulkanModVertexColor() throws IOException {
        String adapted = EntityShaderAdapter.adaptFragment("""
                #version 120
                varying vec4 color;
                void main() { gl_FragColor = color; }
                """, "gbuffers_spidereyes");
        assertTrue(adapted.contains("in vec4 vertexColor;"));
        assertTrue(adapted.contains("#define color veyraEntityColor"));
        assertTrue(adapted.contains("veyraFragColor = color;"));
        assertFalse(adapted.contains("varying vec4 color;"));
    }

    @Test
    void adaptsIrisNoiseTextureForTerrainAndEntityPrograms() throws IOException {
        String terrain = TerrainShaderAdapter.adaptFragment("""
                #version 120
                uniform sampler2D noisetex;
                varying vec2 texcoord;
                void main() { gl_FragColor = texture2D(noisetex, texcoord); }
                """);
        assertTrue(terrain.contains("layout(binding = 6) uniform sampler2D VeyraNoiseSampler;"));
        assertTrue(terrain.contains("#define noisetex VeyraNoiseSampler"));
        assertTrue(terrain.contains("veyraSample(noisetex, texcoord)"));

        String entity = EntityShaderAdapter.adaptFragment("""
                #version 120
                uniform sampler2D noisetex;
                varying vec2 texcoord;
                void main() { gl_FragColor = texture2D(noisetex, texcoord); }
                """);
        assertTrue(entity.contains("uniform sampler2D VeyraNoiseSampler;"));
        assertTrue(entity.contains("#define noisetex VeyraNoiseSampler"));
        assertTrue(entity.contains("veyraEntitySample(noisetex, texCoord0)"));
    }

    @Test
    void removesUnusedShaderPackSamplerDeclarationsFromGeometryStages() throws IOException {
        String terrain = TerrainShaderAdapter.adaptFragment("""
                uniform sampler2D colortex0;
                uniform sampler2D shadowtex1;
                varying vec2 texcoord;
                void main() { gl_FragColor = vec4(texcoord, 0.0, 1.0); }
                """);
        assertFalse(terrain.contains("colortex0"));
        assertFalse(terrain.contains("shadowtex1"));

        String entity = EntityShaderAdapter.adaptFragment("""
                uniform sampler2D colortex0;
                varying vec2 texcoord;
                void main() { gl_FragColor = vec4(texcoord, 0.0, 1.0); }
                """);
        assertFalse(entity.contains("colortex0"));
    }

    @Test
    void stillRejectsUnsupportedSamplerWhenShaderStageReadsIt() {
        IOException error = assertThrows(IOException.class, () -> TerrainShaderAdapter.adaptFragment("""
                uniform sampler2D colortex0;
                varying vec2 texcoord;
                void main() { gl_FragColor = texture2D(colortex0, texcoord); }
                """));
        assertTrue(error.getMessage().contains("colortex0"));
    }

    @Test
    void lowersShadowCompareSamplerTypesInsideShaderPackHelpers() throws IOException {
        String adapted = TerrainShaderAdapter.adaptFragment("""
                #version 120
                uniform sampler2DShadow shadowtex0;
                varying vec2 texcoord;
                float sampleShadow(sampler2DShadow depthSampler, vec3 coordinate) {
                    return shadow2D(depthSampler, coordinate).x;
                }
                void main() {
                    gl_FragColor = vec4(sampleShadow(shadowtex0, vec3(texcoord, 0.5)));
                }
                """);

        assertTrue(adapted.contains("float sampleShadow(sampler2D depthSampler, vec3 coordinate)"));
        assertTrue(adapted.contains("#define shadow2D(sampler, coord) veyraSample(sampler, coord)"));
        assertTrue(adapted.contains("sampleShadow(shadowtex0"));
        assertFalse(adapted.contains("sampler2DShadow"));
    }

    @Test
    void bridgesEntityShadowDepthReadsToVulkanModTextureSlotThree() throws IOException {
        String adapted = EntityShaderAdapter.adaptFragment("""
                #version 120
                uniform sampler2DShadow shadowtex0;
                varying vec2 texcoord;
                float sampleShadow(sampler2DShadow depthSampler, vec3 coordinate) {
                    return texture2DShadow(depthSampler, coordinate);
                }
                void main() {
                    gl_FragColor = vec4(sampleShadow(shadowtex0, vec3(texcoord, 0.5)));
                }
                """, "gbuffers_entities", false, true);

        assertTrue(adapted.contains("uniform sampler2D Sampler3;"));
        assertTrue(adapted.contains("#define shadowtex0 Sampler3"));
        assertTrue(adapted.contains("float sampleShadow(sampler2D depthSampler, vec3 coordinate)"));
        assertTrue(adapted.contains("float veyraEntityShadowCompare(sampler2D sourceSampler, vec3 sourceCoord)"));
        assertTrue(adapted.contains("veyraEntityShadowCompare(depthSampler, coordinate)"));
        assertFalse(adapted.contains("sampler2DShadow"));

        IOException unavailable = assertThrows(IOException.class, () -> EntityShaderAdapter.adaptFragment("""
                uniform sampler2D shadowtex0;
                varying vec2 texcoord;
                void main() { gl_FragColor = texture2D(shadowtex0, texcoord); }
                """, "gbuffers_entities"));
        assertTrue(unavailable.getMessage().contains("shadow pass"));
    }

    @Test
    void bridgesLegacyTexSamplerAndTextureSizeToTheBlockAtlas() throws IOException {
        String terrain = TerrainShaderAdapter.adaptFragment("""
                uniform sampler2D tex;
                varying vec2 texcoord;
                void main() {
                    float atlasWidth = float(textureSize(tex, 0).x);
                    gl_FragColor = texture2D(tex, texcoord) / atlasWidth;
                }
                """);
        assertTrue(terrain.contains("#define tex Sampler0"));
        assertTrue(terrain.contains("textureSize(tex, 0)"));
        assertTrue(terrain.contains("veyraSample(tex, texcoord)"));

        String entity = EntityShaderAdapter.adaptFragment("""
                uniform sampler2D tex;
                varying vec2 texcoord;
                void main() {
                    float atlasWidth = float(textureSize(tex, 0).x);
                    gl_FragColor = texture2D(tex, texcoord) / atlasWidth;
                }
                """);
        assertTrue(entity.contains("#define tex Sampler0"));
        assertTrue(entity.contains("textureSize(tex, 0)"));
        assertTrue(entity.contains("veyraEntitySample(tex, texCoord0)"));
    }

    @Test
    void ignoresNoisetexMentionsInsideComments() throws IOException {
        String terrain = TerrainShaderAdapter.adaptFragment("""
                // sampler2D noisetex is only mentioned in this comment
                varying vec2 texcoord;
                void main() { gl_FragColor = vec4(texcoord, 0.0, 1.0); }
                """);
        assertFalse(terrain.contains("VeyraNoiseSampler"));
    }

    @Test
    void removesUnavailableTerrainSamplersFromBridge() throws IOException {
        String adapted = TerrainShaderAdapter.adaptFragment("""
                varying vec2 texcoord;
                void main() { gl_FragColor = vec4(texcoord, 0.0, 1.0); }
                """, false);
        assertFalse(adapted.contains("VeyraNoiseSampler"));
        assertFalse(adapted.contains("VeyraShadowSampler"));
    }

    @Test
    void opaqueDepthIsOnlyExposedToTheWaterBridge() throws IOException {
        String source = "uniform sampler2D depthtex1;\nvoid main(){gl_FragColor=vec4(texture2D(depthtex1,vec2(0.5)).r);}\n";
        assertThrows(IOException.class, () -> TerrainShaderAdapter.adaptFragment(source, false));
        String adapted = TerrainShaderAdapter.adaptFragment(source, false, true);
        assertTrue(adapted.contains("layout(binding = 5) uniform sampler2D VeyraDepthTex1Sampler;"));
        assertTrue(adapted.contains("#define depthtex1 VeyraDepthTex1Sampler"));
        assertTrue(adapted.contains("veyraSample(depthtex1,vec2(0.5))"));
        assertFalse(adapted.contains("VeyraShadowSampler"));
        assertThrows(IOException.class, () -> TerrainShaderAdapter.adaptFragment(
                source.replace("sampler2D depthtex1", "sampler2DShadow depthtex1"), false, true));
        String unused = "uniform sampler2D depthtex1;\nvoid main(){gl_FragColor=vec4(1.0);}\n";
        assertFalse(TerrainShaderAdapter.usesOpaqueDepthSampler(unused));
        assertFalse(TerrainShaderAdapter.adaptFragment(unused, false, true).contains("VeyraDepthTex1Sampler"));
    }

    @Test
    void terrainOptionalSamplersUseContiguousBindings() throws IOException {
        for (boolean shadows : List.of(false, true)) {
            for (boolean noise : List.of(false, true)) {
                String source = "uniform sampler2D depthtex1;\n"
                        + (noise ? "uniform sampler2D noisetex;\n" : "")
                        + "void main(){gl_FragColor=texture2D(depthtex1,vec2(0.5))"
                        + (noise ? "+texture2D(noisetex,vec2(0.5))" : "") + ";}\n";
                String adapted = TerrainShaderAdapter.adaptFragment(source, shadows, true);
                int next = 5;
                if (shadows) assertTrue(adapted.contains("layout(binding = " + next++ + ") uniform sampler2D VeyraShadowSampler;"));
                if (noise) assertTrue(adapted.contains("layout(binding = " + next++ + ") uniform sampler2D VeyraNoiseSampler;"));
                assertTrue(adapted.contains("layout(binding = " + next + ") uniform sampler2D VeyraDepthTex1Sampler;"));
            }
        }
    }

    @Test
    void rejectsCloudTextureInputs() {
        assertThrows(IOException.class, () -> CloudShaderAdapter.adaptFragment(
                "uniform sampler2D texture;\nvarying vec2 texcoord;\nvoid main() { gl_FragColor = texture2D(texture, texcoord); }\n"));
    }
}
