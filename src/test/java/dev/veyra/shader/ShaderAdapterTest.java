package dev.veyra.shader;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class ShaderAdapterTest {
    @TestFactory
    List<DynamicTest> adaptsTerrainAndEntityExamples() throws IOException {
        try (Stream<Path> files = Files.walk(Path.of("examples"))) {
            return files.filter(Files::isRegularFile).filter(path -> {
                String name = path.getFileName().toString();
                return name.matches("gbuffers_(terrain(?:_solid|_cutout)?|water|entities(?:_translucent)?|"
                        + "block(?:_translucent)?|hand(?:_water)?|spidereyes|clouds|damagedblock)\\.fsh");
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
                String programName = name.substring(0, name.length() - 4);
                Path vertexPath = path.getParent().resolve(programName + ".vsh");
                var packVaryings = Files.exists(vertexPath)
                        ? EntityVertexShaderAdapter.supportsProgram(programName)
                                ? EntityVertexShaderAdapter.describeVaryings(programName,
                                        ShaderIncludes.expand(programName + ".vsh", Files.readString(vertexPath), included -> {
                                            Path file = vertexPath.getParent().resolve(included);
                                            return Files.exists(file) ? Optional.of(Files.readString(file)) : Optional.empty();
                                        }))
                                : TerrainVertexShaderAdapter.validateAndGetVaryings(programName,
                                        ShaderIncludes.expand(programName + ".vsh", Files.readString(vertexPath), included -> {
                                            Path file = vertexPath.getParent().resolve(included);
                                            return Files.exists(file) ? Optional.of(Files.readString(file)) : Optional.empty();
                                        }))
                        : java.util.Map.<String, TerrainVertexShaderAdapter.PackVarying>of();
                if (name.equals("gbuffers_clouds.fsh")) {
                    adapted = CloudShaderAdapter.adaptFragment(source);
                } else if (programName.equals("gbuffers_damagedblock")) {
                    adapted = Files.exists(vertexPath)
                            ? DamagedBlockShaderAdapter.adaptFragment(source, programName, packVaryings,
                                    false, false)
                            : DamagedBlockShaderAdapter.adaptFragment(source, programName);
                } else if (EntityVertexShaderAdapter.supportsProgram(programName)) {
                    adapted = EntityShaderAdapter.adaptGeometryFragment(source, name.substring(0, name.length()-4),
                            programName.startsWith("gbuffers_hand"),
                            Files.exists(path.getParent().resolve("shadow.fsh")), -1, -1,
                            ItemIdMap.UNMAPPED, ShaderPackUniformAdapter.supportsEntityColorProgram(
                                    name.substring(0, name.length()-4)), false, packVaryings);
                } else {
                    adapted = TerrainShaderAdapter.adaptFragment(source, true, name.equals("gbuffers_water.fsh"),
                            packVaryings);
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
                varying vec4 spriteBounds;
                void main() { gl_FragColor = vec4(abs(viewPos) / 64.0, 1.0) + spriteBounds * 0.0; }
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
        assertTrue(fragment.contains("layout(location = 19) in vec4 veyraSpriteBounds;"));
        assertTrue(fragment.contains("#define dist veyraTerrainDistance"));
        assertTrue(fragment.contains("#define spriteBounds veyraSpriteBounds"));
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
        assertTrue(vertex.contains("layout (location = 6) in int VeyraTerrainPackedNormal;"));
        assertTrue(vertex.contains("layout (location = 7) in float VeyraTerrainTangentX;"));
        assertTrue(vertex.contains("layout (location = 8) in float VeyraTerrainTangentY;"));
        assertTrue(vertex.contains("layout (location = 9) in float VeyraTerrainTangentZ;"));
        assertTrue(vertex.contains("layout (location = 10) in float VeyraTerrainTangentHandedness;"));
        assertTrue(vertex.contains("#define VeyraTerrainTangent vec4(VeyraTerrainTangentX, VeyraTerrainTangentY,"));
        assertTrue(vertex.contains("layout (location = 11) out flat int veyraTerrainMaterialId;"));
        assertTrue(vertex.contains("layout (location = 13) out flat vec2 veyraMcEntity;"));
        assertTrue(vertex.contains("layout (location = 14) out flat vec2 veyraAbsMidCoordPos;"));
        assertTrue(vertex.contains("layout (location = 15) out flat vec2 veyraMidCoord;"));
        assertTrue(vertex.contains("layout (location = 19) out vec4 veyraSpriteBounds;"));
        assertTrue(vertex.contains("mat4 textureMatrix;"));
        assertTrue(vertex.contains("texCoord0 = (textureMatrix * vec4(veyraRawTexCoord, 0.0, 1.0)).xy;"));
        assertTrue(vertex.contains("veyraMidCoord = (textureMatrix * vec4(veyraRawMidTexCoord, 0.0, 1.0)).xy;"));
        assertTrue(vertex.contains("const vec2 veyraMidCoordOffset = texCoord0 - veyraMidCoord;"));
        assertTrue(vertex.contains("veyraSignMidCoordPos = sign(veyraMidCoordOffset);"));
        assertTrue(vertex.contains("veyraAbsMidCoordPos = abs(veyraMidCoordOffset);"));
        assertTrue(vertex.contains("veyraBslTexCoord = vec4(sign(veyraMidCoordOffset) * 0.5 + 0.5, 0.0, 0.0);"));
        assertTrue(vertex.contains("veyraBslTexCoordAM = vec4(min(texCoord0, veyraMidCoord - veyraMidCoordOffset), abs(veyraMidCoordOffset) * 2.0);"));
        assertTrue(vertex.contains("const vec2 veyraSpriteRadius = abs(texCoord0 - veyraMidCoord);"));
        assertTrue(vertex.contains("veyraSpriteBounds = vec4(veyraMidCoord - veyraSpriteRadius, veyraMidCoord + veyraSpriteRadius);"));
        assertTrue(vertex.contains("veyraMcEntity = vec2(float(VeyraTerrainMaterialId), VeyraTerrainRenderType);"));
    }

    @Test
    void executesSupportedTerrainVertexMainAfterVulkanModSetup() throws IOException {
        String vulkanModVertex = TerrainShaderAdapter.adaptVertex("""
                #version 460
                layout (location = 0) out vec4 vertexColor;
                layout (location = 1) out vec2 texCoord0;
                layout (location = 4) out flat float fadeFactor;
                layout (location = 2) in uint PackedColor;
                layout (location = 1) in uvec2 UV0;
                layout(binding = 1) uniform UBO {
                    int UseRgss;
                };
                const float UV_INV = 1.0 / 32768.0;
                void main() {
                    const vec3 pos = getVertexPosition();
                    gl_Position = MVP * vec4(pos, 1.0);
                    vertexColor = Color * sample_lightmap2(Sampler2, Position.a);
                    texCoord0 = UV0 * UV_INV;
                }
                """);

        String adapted = TerrainVertexShaderAdapter.adapt(vulkanModVertex, """
                #version 120
                #define VSH
                #ifdef VSH
                void main() {
                    gl_Position = gl_ModelViewProjectionMatrix * gl_Vertex;
                    gl_Position.x = 4.0 * gl_Position.w;
                }
                #endif
                #ifdef FSH
                attribute vec3 unsupportedInactiveInput;
                #endif
                """, "gbuffers_terrain");

        assertTrue(adapted.contains("void veyraShaderPackMain();"));
        assertTrue(adapted.contains("veyraShaderPackMain();"));
        assertTrue(adapted.contains("void veyraShaderPackMain("));
        assertTrue(adapted.contains("#define gl_Vertex vec4(getVertexPosition(), 1.0)"));
        assertTrue(adapted.contains("#define gl_ModelViewProjectionMatrix (gbufferProjection * gbufferModelView)"));
        assertFalse(adapted.contains("unsupportedInactiveInput"));
        assertTrue(adapted.indexOf("veyraShaderPackMain();") < adapted.lastIndexOf("void veyraShaderPackMain("));
    }

    @Test
    void rejectsTerrainVertexInterfacesThatHaveNoMatchingVulkanInputsOrFragmentBridge() throws IOException {
        IOException attribute = assertThrows(IOException.class, () -> TerrainVertexShaderAdapter.validate(
                "gbuffers_terrain", "#version 120\nattribute vec3 at_midBlock;\nvoid main(){}\n"));
        assertTrue(attribute.getMessage().contains("at_midBlock"));

        IOException output = assertThrows(IOException.class, () -> TerrainVertexShaderAdapter.validate(
                "gbuffers_terrain", "#version 120\nout dmat4 unsupportedTransform;\nvoid main(){}\n"));
        assertTrue(output.getMessage().contains("unsupported varying type dmat4"));
    }

    @Test
    void linksCustomTerrainVertexOutputsToFragmentInputs() throws IOException {
        String baseVertex = TerrainShaderAdapter.adaptVertex("""
                #version 460
                layout (location = 0) out vec4 vertexColor;
                layout (location = 1) out vec2 texCoord0;
                layout (location = 4) out flat float fadeFactor;
                layout (location = 2) in uint PackedColor;
                layout (location = 1) in uvec2 UV0;
                layout(binding = 1) uniform UBO {
                    int UseRgss;
                };
                const float UV_INV = 1.0 / 32768.0;
                void main() {
                    const vec3 pos = getVertexPosition();
                    gl_Position = MVP * vec4(pos, 1.0);
                    vertexColor = Color * sample_lightmap2(Sampler2, Position.a);
                    texCoord0 = UV0 * UV_INV;
                }
                """);
        TerrainVertexShaderAdapter.AdaptedStage vertex = TerrainVertexShaderAdapter.adaptStage(baseVertex, """
                #version 130
                out vec2 texCoord;
                out vec3 normal;
                flat out int mat;
                flat out float chunkFade;
                void main() {
                    texCoord = gl_MultiTexCoord0.xy;
                    normal = gl_Normal;
                    mat = 27101;
                    chunkFade = mc_chunkFade;
                }
                """, "gbuffers_terrain");
        String fragment = TerrainShaderAdapter.adaptFragment("""
                #version 130
                in vec2 texCoord;
                in vec3 normal;
                flat in int mat;
                flat in float chunkFade;
                void main() {
                    gl_FragColor = vec4(texCoord, normal.x * 0.0 + float(mat) * 0.0 + chunkFade * 0.0, 1.0);
                }
                """, false, false, vertex.varyings());

        assertTrue(vertex.source().contains("#define texCoord texCoord0"));
        assertTrue(vertex.source().contains("layout(location = 20) out vec3 veyraPackVarying_normal;"));
        assertEquals(20, vertex.varyings().get("normal").location());
        assertTrue(fragment.contains("layout(location = 20) in vec3 veyraPackVarying_normal;"));
        assertTrue(fragment.contains("layout(location = 4) flat in float veyraPackVarying_chunkFade;"));
        assertTrue(fragment.contains("#define mat veyraTerrainMaterialId"));
        assertTrue(fragment.contains("#define chunkFade veyraPackVarying_chunkFade"));
        assertFalse(fragment.contains("veyraInitializeTerrainTangentFrame"));
    }

    @Test
    void linksMatrixTerrainVaryingsAndBridgesVertexNoiseSampler() throws IOException {
        String baseVertex = TerrainShaderAdapter.adaptVertex("""
                #version 460
                layout (location = 0) out vec4 vertexColor;
                layout (location = 1) out vec2 texCoord0;
                layout (location = 4) out flat float fadeFactor;
                layout (location = 2) in uint PackedColor;
                layout (location = 1) in uvec2 UV0;
                layout(binding = 1) uniform UBO {
                    int UseRgss;
                };
                const float UV_INV = 1.0 / 32768.0;
                void main() {
                    const vec3 pos = getVertexPosition();
                    gl_Position = MVP * vec4(pos, 1.0);
                    vertexColor = Color * sample_lightmap2(Sampler2, Position.a);
                    texCoord0 = UV0 * UV_INV;
                    fadeFactor = 1.0;
                }
                """);
        String packVertex = """
                #version 130
                uniform sampler2D noisetex;
                out mat3 tbnMatrix;
                out vec4 matrixFollowing;
                vec3 noiseOffset(vec2 uv) {
                    return texture2D(noisetex, uv).rgb;
                }
                void main() {
                    float noise = noiseOffset(vec2(0.5)).x;
                    tbnMatrix = mat3(noise + 1.0);
                    matrixFollowing = vec4(noise);
                }
                """;

        TerrainVertexShaderAdapter.AdaptedStage vertex = TerrainVertexShaderAdapter.adaptStage(
                baseVertex, packVertex, "gbuffers_water", 5);
        assertTrue(vertex.source().contains("layout(binding = 5) uniform sampler2D VeyraNoiseSampler;"));
        assertTrue(vertex.source().contains("#define noisetex VeyraNoiseSampler"));
        assertTrue(vertex.source().contains("texture(noisetex, uv)"));
        assertFalse(vertex.source().contains("uniform sampler2D noisetex;"));
        assertEquals(20, vertex.varyings().get("tbnMatrix").location());
        assertEquals(23, vertex.varyings().get("matrixFollowing").location());

        String fragment = TerrainShaderAdapter.adaptFragment("""
                #version 130
                in mat3 tbnMatrix;
                in vec4 matrixFollowing;
                void main() {
                    gl_FragColor = vec4(tbnMatrix[0] + matrixFollowing.rgb, 1.0);
                }
                """, false, false, vertex.varyings());
        assertTrue(fragment.contains("layout(location = 20) in mat3 veyraPackVarying_tbnMatrix;"));
        assertTrue(fragment.contains("layout(location = 23) in vec4 veyraPackVarying_matrixFollowing;"));
        assertTrue(fragment.contains("#define tbnMatrix veyraPackVarying_tbnMatrix"));
    }

    @Test
    void allocatesCustomTerrainVaryingsAcrossAllUnoccupiedLocations() throws IOException {
        StringBuilder source = new StringBuilder("#version 130\n");
        for (int index = 0; index < 14; index++) source.append("out float extra").append(index).append(";\n");
        source.append("void main() {}\n");
        Map<String, TerrainVertexShaderAdapter.PackVarying> varyings =
                TerrainVertexShaderAdapter.validateAndGetVaryings("gbuffers_terrain", source.toString());

        assertEquals(20, varyings.get("extra0").location());
        assertEquals(31, varyings.get("extra11").location());
        assertEquals(2, varyings.get("extra12").location());
        assertEquals(3, varyings.get("extra13").location());

        source.append("out float overflow;\n");
        IOException error = assertThrows(IOException.class, () ->
                TerrainVertexShaderAdapter.validate("gbuffers_terrain", source.toString()));
        assertTrue(error.getMessage().contains("too many vertex outputs"));
    }

    @Test
    void freesUnconsumedTerrainVertexOutputsForCustomPackVaryings() throws IOException {
        String fragment = TerrainShaderAdapter.adaptFragment("""
                varying vec4 color;
                void main() { gl_FragColor = color; }
                """);
        Set<Integer> unusedLocations = TerrainShaderAdapter.unusedTerrainVertexOutputs(fragment);
        assertFalse(unusedLocations.contains(0));
        assertTrue(unusedLocations.contains(5));

        String vertex = """
                layout(location = 0) out vec4 vertexColor;
                layout(location = 5) out vec2 veyraLightmapCoord;
                void main() { vertexColor = vec4(1.0); veyraLightmapCoord = vec2(0.0); }
                """;
        String pruned = TerrainShaderAdapter.pruneUnusedTerrainVertexOutputs(vertex, unusedLocations);
        assertTrue(pruned.contains("layout(location = 0) out vec4 vertexColor;"));
        assertTrue(pruned.contains("vec2 veyraLightmapCoord;"));
        assertFalse(pruned.contains("layout(location = 5) out vec2 veyraLightmapCoord;"));

        String normalFragment = TerrainShaderAdapter.adaptFragment("""
                varying vec3 normal;
                void main() { gl_FragColor = vec4(normal, 1.0); }
                """);
        Set<Integer> normalUnusedLocations = TerrainShaderAdapter.unusedTerrainVertexOutputs(normalFragment);
        assertFalse(normalUnusedLocations.contains(7));
        assertTrue(normalUnusedLocations.contains(1));
        assertTrue(normalUnusedLocations.contains(10));
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
        assertEquals(48, TerrainMidTexCoord.VERTEX_FORMAT.getVertexSize());
        assertEquals(20, TerrainMidTexCoord.MATERIAL_ID_OFFSET);
        assertEquals(24, TerrainMidTexCoord.RENDER_TYPE_OFFSET);
        assertEquals(28, TerrainMidTexCoord.PACKED_NORMAL_OFFSET);
        assertEquals(32, TerrainMidTexCoord.TANGENT_OFFSET);
        assertEquals(11, TerrainMidTexCoord.VERTEX_FORMAT.getElements().size());
        for (int location = 7; location <= 10; location++) {
            var element = TerrainMidTexCoord.VERTEX_FORMAT.getElements().get(location);
            assertEquals(com.mojang.blaze3d.vertex.VertexFormatElement.Usage.GENERIC, element.usage());
            assertEquals(com.mojang.blaze3d.vertex.VertexFormatElement.Type.FLOAT, element.type());
            assertEquals(1, element.count(), "VulkanMod only supports scalar generic FLOAT attributes");
        }
    }

    @Test
    void computesOrientedTerrainTangentsFromQuadGeometryAndUvs() {
        float[] tangent = new float[4];
        TerrainMidTexCoord.computeTangent(
                new float[]{0, 1, 1, 0}, new float[]{0, 0, 1, 1}, new float[]{0, 0, 0, 0},
                new float[]{0, 1, 1, 0}, new float[]{0, 0, 1, 1},
                new int[]{127 << 16, 127 << 16, 127 << 16, 127 << 16}, tangent);

        assertEquals(1.0F, tangent[0], 1.0e-6F);
        assertEquals(0.0F, tangent[1], 1.0e-6F);
        assertEquals(0.0F, tangent[2], 1.0e-6F);
        assertEquals(1.0F, tangent[3]);
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
        assertTrue(adapted.source().contains("layout(binding = 2) uniform sampler2D VeyraNoiseSampler;"));
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
    void assignsContiguousBindingsToActiveCloudSamplers() throws IOException {
        CloudShaderAdapter.AdaptedFragment adapted = CloudShaderAdapter.adapt("""
                #version 120
                uniform sampler2D colortex0;
                uniform sampler2D noisetex;
                uniform sampler2D texture;
                void main() {
                    vec2 uv = vec2(0.5);
                    gl_FragColor = texture2D(colortex0, uv)
                            + texture2D(noisetex, uv) + texture2D(texture, uv);
                }
                """);

        assertTrue(adapted.source().contains("layout(binding = 2) uniform sampler2D VeyraCloudSceneSampler;"));
        assertTrue(adapted.source().contains("layout(binding = 3) uniform sampler2D VeyraNoiseSampler;"));
        assertTrue(adapted.source().contains("layout(binding = 4) uniform sampler2D VeyraCloudTextureSampler;"));
        assertEquals(2, adapted.samplerBindings().get("VeyraCloudSceneSampler"));
        assertEquals(3, adapted.samplerBindings().get("VeyraNoiseSampler"));
        assertEquals(4, adapted.samplerBindings().get("VeyraCloudTextureSampler"));
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
        assertFalse(adapted.contains("#define gl_Color (Color * ColorModulator)"));
        assertEquals(1, adapted.split("#define gl_Color vertexColor", -1).length - 1);
        assertTrue(adapted.contains("vertexColor = Color * ColorModulator;"));
        assertTrue(adapted.contains("gl_Position = ftransform();"));
        assertTrue(adapted.contains("color = gl_Color;"));
        assertFalse(adapted.contains("varying vec4 color;"));
        assertFalse(adapted.contains("varying float vertexDistance;"));
    }

    @Test
    void adaptsBslCloudTextureCoordinatesNormalsAndDirectionVaryings() throws IOException {
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
        CloudShaderAdapter.AdaptedVertex vertex = CloudShaderAdapter.adaptVertexWithMetadata(vulkanModVertex, """
                #version 120
                varying vec2 texCoord;
                varying vec3 normal;
                varying vec3 sunVec, upVec, eastVec;
                varying vec4 color;
                uniform float timeAngle;
                uniform mat4 gbufferModelView;
                const float sunPathRotation = 0.0;
                void main() {
                    texCoord = (gl_TextureMatrix[0] * gl_MultiTexCoord0).xy;
                    color = gl_Color;
                    normal = normalize(gl_NormalMatrix * gl_Normal);
                    sunVec = normalize((gbufferModelView * vec4(vec3(timeAngle), 1.0)).xyz);
                    upVec = normalize(gbufferModelView[1].xyz);
                    eastVec = normalize(gbufferModelView[0].xyz);
                    gl_Position = ftransform();
                }
                """);
        CloudShaderAdapter.AdaptedFragment fragment = CloudShaderAdapter.adapt("""
                #version 120
                uniform sampler2D texture;
                varying vec2 texCoord;
                varying vec3 normal;
                varying vec3 sunVec, upVec, eastVec;
                varying vec4 color;
                void main() {
                    vec4 albedo = texture2D(texture, texCoord);
                    albedo.rgb *= dot(normal, upVec) + dot(sunVec, eastVec);
                    gl_FragColor = albedo * color;
                }
                """, vertex.uniformFields());

        assertTrue(vertex.requiresExtendedVertexFormat());
        assertTrue(vertex.source().contains("layout(location = 2) in vec2 VeyraCloudTexCoord0;"));
        assertTrue(vertex.source().contains("layout(location = 3) in vec3 VeyraCloudNormal;"));
        assertTrue(vertex.source().contains("layout(location = 6) out vec3 veyraCloudEastVec;"));
        assertTrue(vertex.source().contains("uniform mat4 textureMatrix;")
                || vertex.uniformFields().stream().anyMatch(field -> field.name().equals("textureMatrix")));
        assertTrue(fragment.usesCloudTexture());
        assertTrue(fragment.source().contains("layout(binding = 2) uniform sampler2D VeyraCloudTextureSampler;"));
        assertTrue(fragment.source().contains("texture(VeyraCloudTextureSampler, texCoord)"));
        assertTrue(fragment.source().contains("layout(location = 6) in vec3 veyraCloudEastVec;"));
        assertTrue(fragment.uniformFields().containsAll(vertex.uniformFields()));
    }

    @Test
    void removesUnusedIrisCloudLightmapHelperButKeepsRejectingRealUse() throws IOException {
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
        String cloudShader = """
                #version 130
                uniform vec3 fogColor;
                uniform sampler2D colortex0;
                uniform sampler2D noisetex;
                vec2 GetLightMapCoordinates() {
                    vec2 lmCoord = (gl_TextureMatrix[1] * gl_MultiTexCoord1).xy;
                    return clamp((lmCoord - 0.03125) * 1.06667, 0.0, 1.0);
                }
                vec2 getCloudTexCoord() {
                    return gl_MultiTexCoord0.xy + fogColor.xy * 0.0;
                }
                vec2 cloudTexCoord() { return getCloudTexCoord(); }
                float unusedNoise() { return texture2D(noisetex, vec2(0.0)).r; }
                varying vec2 texCoord;
                void main() {
                    texCoord = cloudTexCoord();
                    gl_Position = ftransform();
                }
                """;

        CloudShaderAdapter.AdaptedVertex adapted = CloudShaderAdapter.adaptVertexWithMetadata(
                vulkanModVertex, cloudShader);

        assertTrue(adapted.requiresExtendedVertexFormat());
        assertFalse(adapted.source().contains("GetLightMapCoordinates"), adapted.source());
        assertFalse(adapted.source().contains("gl_MultiTexCoord1"), adapted.source());
        assertFalse(adapted.source().contains("uniform sampler2D colortex0"), adapted.source());
        assertFalse(adapted.source().contains("noisetex"), adapted.source());
        assertFalse(adapted.source().contains("unusedNoise"), adapted.source());
        assertTrue(adapted.source().contains("#define gl_MultiTexCoord0 vec4(VeyraCloudTexCoord0, 0.0, 1.0)"));
        assertTrue(adapted.source().contains("#define fogColor (FogColor.rgb)"), adapted.source());
        assertTrue(adapted.uniformFields().contains(
                new ShaderPackUniformAdapter.UniformField("vec4", "FogColor")));
        CloudShaderAdapter.AdaptedFragment fragment = CloudShaderAdapter.adapt("""
                #version 120
                uniform vec3 fogColor;
                varying vec4 color;
                void main() { gl_FragColor = vec4(fogColor, color.a); }
                """, adapted.uniformFields());
        assertEquals(1, fragment.uniformFields().stream().filter(field -> field.name().equals("FogColor")).count());

        IOException error = assertThrows(IOException.class, () -> CloudShaderAdapter.adaptVertex(
                vulkanModVertex, cloudShader.replace("texCoord = cloudTexCoord();",
                        "texCoord = GetLightMapCoordinates();")));
        assertTrue(error.getMessage().contains("lmCoord"), error.getMessage());
    }

    @Test
    void mapsLegacyCloudModelViewMatrixAndLowercaseTexcoord() throws IOException {
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
        String cloudVertex = """
                varying vec2 texcoord;
                varying vec4 color;
                varying float dist;
                void main() {
                    gl_Position = ftransform();
                    dist = length(gl_ModelViewMatrix * gl_Vertex);
                    texcoord = (gl_TextureMatrix[0] * gl_MultiTexCoord0).xy;
                    color = gl_Color;
                }
                """;

        CloudShaderAdapter.AdaptedVertex vertex = CloudShaderAdapter.adaptVertexWithMetadata(
                vulkanModVertex, cloudVertex);

        assertFalse(vertex.source().contains("gl_ModelViewMatrix"), vertex.source());
        assertTrue(vertex.source().contains("ModelViewMat * gl_Vertex"), vertex.source());
        assertTrue(vertex.uniformFields().contains(
                new ShaderPackUniformAdapter.UniformField("mat4", "ModelViewMat")));
        assertTrue(vertex.uniformFields().contains(
                new ShaderPackUniformAdapter.UniformField("mat4", "textureMatrix")));
        assertTrue(vertex.varyings().containsAll(Set.of("texcoord", "color", "dist")));

        CloudShaderAdapter.AdaptedFragment fragment = CloudShaderAdapter.adapt("""
                uniform sampler2D texture;
                varying vec2 texcoord;
                varying vec4 color;
                varying float dist;
                void main() { gl_FragColor = texture2D(texture, texcoord) * color * clamp(dist, 0.0, 1.0); }
                """, vertex.uniformFields());
        assertTrue(fragment.usesCloudTexture());
        assertTrue(fragment.varyings().containsAll(Set.of("texcoord", "color", "dist")));
        assertTrue(fragment.source().contains("layout(location = 7) in float veyraCloudDist;"), fragment.source());
        assertTrue(fragment.source().contains("layout(location = 8) in vec2 veyraCloudTexcoord;"), fragment.source());
        assertTrue(fragment.uniformFields().containsAll(vertex.uniformFields()));
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
        assertTrue(error.getMessage().contains("coord0"), error.getMessage());
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
                "uniform sampler2D colortex5;\nvarying vec2 texCoord;\nvoid main() { gl_FragColor = texture2D(colortex5, texCoord); }\n"));
    }
}
