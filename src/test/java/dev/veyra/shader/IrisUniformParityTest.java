package dev.veyra.shader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class IrisUniformParityTest {
    @AfterEach
    void reset() {
        CustomUniforms.activate(CustomUniforms.EMPTY);
        IrisExtraUniforms.publish(Map.of(), Map.of());
    }

    @Test
    void smoothedFloatMatchesIrisHalfLives() {
        IrisSmoothedFloat smoothed = new IrisSmoothedFloat(1, 10);
        assertEquals(0.0, smoothed.update(0.0, 0.05));
        assertEquals(0.5, smoothed.update(1.0, 0.1), 1.0e-9);
        assertEquals(0.75, smoothed.update(1.0, 0.1), 1.0e-9);
        // Falling uses the one-second half-life.
        assertEquals(0.375, smoothed.update(0.0, 1.0), 1.0e-9);
        IrisSmoothedFloat immediate = new IrisSmoothedFloat(0, 0);
        immediate.update(0.0, 0.05);
        assertEquals(3.0, immediate.update(3.0, 0.05));
        smoothed.reset();
        assertEquals(7.0, smoothed.update(7.0, 0.05), "the first value after a reset is unsmoothed");
    }

    @Test
    void fixedTimeWorldsMatchIrisTimeRules() {
        assertEquals(0, VeyraRenderer.irisWorldTime(6000L, true, false),
                "fixed-time Overworld-like dimensions pin Iris world time to zero");
        assertEquals(6000, VeyraRenderer.irisWorldTime(6000L, true, true),
                "Nether and End preserve their day time even when their dimension type has fixed time");
        assertEquals(6000, VeyraRenderer.irisWorldTime(30000L, false, false));
    }

    @Test
    void registryNamesAreUniqueAndCoverPackRequirements() {
        Set<String> names = new HashSet<>();
        for (IrisExtraUniforms.Definition definition : IrisExtraUniforms.definitions()) {
            assertTrue(names.add(definition.name()), "duplicate " + definition.name());
            assertTrue(IrisUnsetUniforms.IRIS_SET_UNIFORMS.contains(definition.name()),
                    definition.name() + " must be a uniform Iris 1.21.11 registers");
        }
        // Uniforms that Complementary, BSL and Sildur declare and Veyra did not provide before.
        for (String required : new String[]{"heavyFog", "heightLimit", "isElytraFlying", "isRightHanded",
                "sunPosition", "upPosition", "shadowLightPosition", "endFlashPosition", "timeAngle",
                "timeBrightness", "BiomeTemp", "blindFactor", "eyeBrightnessM", "frameTimeSmooth", "rainFactor",
                "starter", "isEyeInCave", "dhFarPlane", "dhNearPlane", "dhRenderDistance"}) {
            assertTrue(IrisExtraUniforms.contains(required), required);
        }
        assertTrue(IrisExtraUniforms.HARDCODED_CUSTOM.contains("timeAngle"));
        assertFalse(IrisExtraUniforms.HARDCODED_CUSTOM.contains("heavyFog"));
    }

    @Test
    void publishedValuesFeedExpressionInputs() {
        IrisExtraUniforms.publish(Map.of("heightLimit", 384.0, "heavyFog", 1.0),
                Map.of("sunPosition", new float[]{1.0F, 2.0F, 3.0F}));
        assertEquals(384.0, IrisExtraUniforms.scalar("heightLimit"));
        assertEquals(2.0, IrisExtraUniforms.expressionInput("sunPosition.y"));
        assertNull(IrisExtraUniforms.expressionInput("sunPosition.w"));
        assertEquals(0.0, IrisExtraUniforms.expressionInput("seaLevel"), "unpublished values read zero");
        assertNull(IrisExtraUniforms.expressionInput("notAnIrisUniform"));

        CustomUniforms uniforms = CustomUniforms.parse("""
                uniform.float.tall=if(heightLimit > 256, 1.0, 0.0) + sunPosition.z
                uniform.float.heavyFog=1.0
                """);
        assertTrue(uniforms.unavailableReason("heavyFog").contains("built-in"),
                "non-hardcoded Iris uniforms cannot be redefined, as in Iris");
        uniforms.evaluate(IrisExtraUniforms::expressionInput, 0.05);
        assertEquals(4.0F, uniforms.floatValue("tall"));
        // Packs may redefine Iris' hardcoded custom uniforms, as BSL does with timeAngle.
        assertNotNull(CustomUniforms.parse("uniform.float.timeAngle=0.5\n").uniform("timeAngle"));
    }

    @Test
    void geometryAndTerrainBridgesBindIrisExtras() throws IOException {
        String terrain = TerrainShaderAdapter.adaptFragment("""
                uniform bool heavyFog;
                uniform int heightLimit;
                uniform vec3 sunPosition;
                uniform float timeAngle;
                void main() { gl_FragColor = vec4(sunPosition * timeAngle, float(heightLimit) + float(heavyFog)); }
                """);
        assertTrue(terrain.contains("    int veyra_heavyFog;"));
        assertTrue(terrain.contains("    int heightLimit;"));
        assertTrue(terrain.contains("    vec3 sunPosition;"));
        assertTrue(terrain.contains("    float timeAngle;"));

        ShaderPackUniformAdapter.AdaptedShader entity = ShaderPackUniformAdapter.extract(
                "uniform vec3 shadowLightPosition;\nuniform bool isRightHanded;\nvoid main(){}\n", "gbuffers_entities");
        assertTrue(entity.fields().contains(new ShaderPackUniformAdapter.UniformField("vec3", "shadowLightPosition")));
        assertTrue(entity.fields().contains(new ShaderPackUniformAdapter.UniformField("int", "veyra_isRightHanded")));

        // A pack override of a hardcoded custom uniform keeps a single block member.
        CustomUniforms.activate(CustomUniforms.parse("uniform.float.timeAngle=worldTime / 24000.0\n"));
        assertEquals(1, TerrainShaderAdapter.shaderPackUniforms().stream()
                .filter(uniform -> uniform.name().equals("timeAngle")).count());
    }

    @Test
    void uniformsIrisNeverSetsBecomeConstants() {
        String source = """
                uniform bool heavyFog = false;
                uniform float vxRenderDistance;
                uniform highp vec3 voxyOffset = vec3(1.0, 2.0, 3.0);
                uniform ivec2 modSize;
                uniform mat4 vxModelView;
                uniform float packDefined = 0.5;
                uniform sampler2D colortex9;
                uniform float lights[4];
                  uniform float rainStrength;
                uniform float centerDepthSmooth;
                uniform mat4 modelViewMatrix;
                """;
        String applied = IrisUnsetUniforms.apply(source, Set.of("packDefined"));
        assertTrue(applied.contains("uniform bool heavyFog;"), "Iris sets heavyFog; only its initializer goes");
        assertTrue(applied.contains("const float vxRenderDistance = 0.0;"));
        assertTrue(applied.contains("const vec3 voxyOffset = vec3(1.0, 2.0, 3.0);"));
        assertTrue(applied.contains("const ivec2 modSize = ivec2(0);"));
        assertTrue(applied.contains("const mat4 vxModelView = mat4(0.0);"));
        assertTrue(applied.contains("uniform float packDefined;"), "pack custom uniforms are set by Iris");
        assertTrue(applied.contains("uniform sampler2D colortex9;"), "opaque types are left to the adapters");
        assertTrue(applied.contains("uniform float lights[4];"), "arrays are left to the adapters");
        assertTrue(applied.contains("  uniform float rainStrength;"));
        // Iris' transformers supply these, so they must stay real uniforms.
        assertTrue(applied.contains("uniform float centerDepthSmooth;"));
        assertTrue(applied.contains("uniform mat4 modelViewMatrix;"));
    }

    @Test
    void stageReadsApplyTheIrisUnsetRule(@TempDir Path pack) throws IOException {
        Path shaders = Files.createDirectories(pack.resolve("shaders"));
        Files.writeString(shaders.resolve("shaders.properties"), "uniform.float.packDefined=frameTime\n");
        Files.writeString(shaders.resolve("gbuffers_terrain.fsh"), """
                #version 120
                uniform bool heavyFog = false;
                uniform float vxRenderDistance, packDefined;
                void main() { gl_FragColor = vec4(vxRenderDistance + packDefined + float(heavyFog)); }
                """);
        String stage = ShaderPack.inspect(pack).readStage("gbuffers_terrain.fsh").orElseThrow();
        assertTrue(stage.contains("uniform bool heavyFog;"), stage);
        assertTrue(stage.contains("const float vxRenderDistance = 0.0;"), stage);
        assertTrue(stage.contains("uniform float packDefined;"), stage);
    }
}
