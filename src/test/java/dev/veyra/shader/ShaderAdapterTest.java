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
                String adapted;
                if (name.equals("gbuffers_clouds.fsh")) {
                    adapted = CloudShaderAdapter.adaptFragment(source);
                } else if (name.startsWith("gbuffers_entities")) {
                    adapted = EntityShaderAdapter.adaptFragment(source);
                } else {
                    adapted = TerrainShaderAdapter.adaptFragment(source);
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
        assertTrue(adapted.contains("veyraEntityFragColor = color;"));
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
    void rejectsCloudTextureInputs() {
        assertThrows(IOException.class, () -> CloudShaderAdapter.adaptFragment(
                "uniform sampler2D texture;\nvarying vec2 texcoord;\nvoid main() { gl_FragColor = texture2D(texture, texcoord); }\n"));
    }
}
