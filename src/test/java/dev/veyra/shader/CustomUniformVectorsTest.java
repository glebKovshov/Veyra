package dev.veyra.shader;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class CustomUniformVectorsTest {
    @AfterEach
    void reset() { CustomUniforms.activate(CustomUniforms.EMPTY); }

    private static void vector(CustomUniforms uniforms, String name, float... expected) {
        assertNull(uniforms.unavailableReason(name), uniforms.unavailableReason(name));
        float[] actual = new float[expected.length];
        uniforms.copyVector(name, actual);
        assertArrayEquals(expected, actual, 1e-6F);
    }

    @Test
    void resolvesVectorAndComponentForwardReferencesWithoutExposingVariables() {
        CustomUniforms uniforms = CustomUniforms.parse("""
                uniform.vec4.tint=vec4(base.r, base.g, scalar, pair.t)
                uniform.float.scalar=base.b
                uniform.vec2.pair=vec2(base .x, base.y)
                variable.vec3.base=vec3(1, 2, 3)
                """);
        uniforms.evaluate(name -> null, .05);
        vector(uniforms, "tint", 1, 2, 3, 2);
        vector(uniforms, "pair", 1, 2);
        assertEquals(3, uniforms.floatValue("scalar"));
        assertNull(uniforms.uniform("base"));
        assertEquals(List.of("tint", "scalar", "pair"), uniforms.uniforms().stream().map(CustomUniforms.Uniform::name).toList());
    }

    @Test
    void readsMatrixElementsLikePhotonsSunDirection() {
        // Photon: sun_dir = gbufferModelViewInverse * view_sun_dir, written element by element (column.row).
        CustomUniforms uniforms = CustomUniforms.parse("""
                variable.float.vx = 0.0
                variable.float.vy = 1.0
                variable.float.vz = 0.0
                uniform.vec3.sun_dir = vec3(gbufferModelViewInverse.0.0 * vx + gbufferModelViewInverse.1.0 * vy + gbufferModelViewInverse.2.0 * vz, \\
                    gbufferModelViewInverse.0.1 * vx + gbufferModelViewInverse.1.1 * vy + gbufferModelViewInverse.2.1 * vz, \\
                    gbufferModelViewInverse.0.2 * vx + gbufferModelViewInverse.1.2 * vy + gbufferModelViewInverse.2.2 * vz)
                """);
        Map<String, Double> matrix = Map.of("gbufferModelViewInverse.1.0", 0.25,
                "gbufferModelViewInverse.1.1", 0.5, "gbufferModelViewInverse.1.2", -0.75);
        uniforms.evaluate(name -> matrix.getOrDefault(name, name.startsWith("gbufferModelViewInverse.") ? 0.0 : null), .05);
        vector(uniforms, "sun_dir", 0.25F, 0.5F, -0.75F);
        assertTrue(CustomUniforms.isMatrixElementInput("shadowProjection.3.2"));
        assertFalse(CustomUniforms.isMatrixElementInput("gbufferModelViewInverse.4.0"));
    }

    @Test
    void evaluatesIrisVectorArithmeticAndFunctions() {
        CustomUniforms uniforms = CustomUniforms.parse("""
                variable.vec3.a=vec3(-1.2, 2.3, 3.4)
                uniform.vec3.arithmetic=-(a + vec3(1, 2, 3)) * vec3(2, 3, 4) / vec3(2, 3, 4) - vec3(1, 1, 1)
                uniform.vec3.absolute=abs(a)
                uniform.vec3.down=floor(a)
                uniform.vec3.up=ceil(a)
                uniform.vec3.minimum=min(a, vec3(1, 2, 3))
                uniform.vec3.maximum=max(a, vec3(1, 2, 3))
                uniform.vec3.limited=clamp(a, vec3(0, 0, 0), vec3(2, 2, 2))
                uniform.vec3.selected=if(true, a, vec3(0, 0, 0))
                uniform.vec3.conditional=false ? a : vec3(1, 2, 3)
                """);
        uniforms.evaluate(name -> null, .05);
        vector(uniforms, "arithmetic", -.8F, -5.3F, -7.4F);
        vector(uniforms, "absolute", 1.2F, 2.3F, 3.4F);
        vector(uniforms, "down", -2, 2, 3);
        vector(uniforms, "up", -1, 3, 4);
        vector(uniforms, "minimum", -1.2F, 2, 3);
        vector(uniforms, "maximum", 1, 2.3F, 3.4F);
        vector(uniforms, "limited", 0, 2, 2);
        vector(uniforms, "selected", -1.2F, 2.3F, 3.4F);
        vector(uniforms, "conditional", 1, 2, 3);
    }

    @Test
    void readsBuiltInVectorsAndAllSingleComponentAliases() {
        CustomUniforms uniforms = CustomUniforms.parse("""
                uniform.vec3.position=cameraPosition
                uniform.vec4.aliases=vec4(cameraPosition.s, position.g, (position + vec3(0, 0, 1)).2, vec4(1, 2, 3, 4).q)
                uniform.vec2.brightness=eyeBrightness
                """);
        uniforms.evaluate(Map.of("cameraPosition.x", 10.0, "cameraPosition.y", 20.0, "cameraPosition.z", 30.0,
                "eyeBrightness.x", 12.0, "eyeBrightness.y", 240.0)::get, .05);
        vector(uniforms, "position", 10, 20, 30);
        vector(uniforms, "aliases", 10, 20, 31, 4);
        vector(uniforms, "brightness", 12, 240);
    }

    @Test
    void rejectsUnsupportedOverloadsAndPropagatesUnavailableComponents() {
        for (String expression : List.of("vec3(1)", "vec3(vec2(1, 2), 3)", "vec2(1, 2)",
                "vec3(1, 2, 3) + 1", "sin(vec3(1, 2, 3))", "!vec3(1, 2, 3)",
                "clamp(vec3(1, 2, 3), 0, 1)", "if(true, vec3(1, 2, 3), vec2(1, 2))")) {
            CustomUniforms uniforms = CustomUniforms.parse("uniform.vec3.bad=" + expression + "\nuniform.float.dependent=bad.x");
            assertNotNull(uniforms.unavailableReason("bad"), expression);
            assertNotNull(uniforms.unavailableReason("dependent"), expression);
            assertNull(uniforms.uniform("bad"));
        }
        for (String expression : List.of("vec2(1, 2).z", "cameraPosition.xy", "cameraPosition.w", "worldTime.x")) {
            assertNotNull(CustomUniforms.parse("uniform.float.bad=" + expression).unavailableReason("bad"), expression);
        }
    }

    @Test
    void detectsCyclesThroughComponentAccessAndMissingVectorInputs() {
        CustomUniforms cyclic = CustomUniforms.parse("uniform.vec2.a=vec2(b.x, 1)\nuniform.vec2.b=a");
        assertEquals(List.of("a", "b"), cyclic.circularDefinitions());
        CustomUniforms missing = CustomUniforms.parse("uniform.vec2.a=vec2(missing, 1)\nuniform.float.b=a.x");
        assertTrue(missing.unavailableReason("b").contains("unavailable"));
    }

    @Test
    void doesNotOverwriteBuiltInVectorsOrMatrices() {
        for (String name : List.of("cameraPosition", "sunPosition", "gbufferProjection")) {
            assertTrue(CustomUniforms.parse("uniform.vec3." + name + "=vec3(1, 2, 3)")
                    .unavailableReason(name).contains("built-in"), name);
        }
    }

    @Test
    void evaluatesVectorArgumentsOnceAndShortCircuitsUnselectedBranches() {
        var calls = new java.util.ArrayList<String>();
        CustomUniforms uniforms = CustomUniforms.parse("""
                uniform.vec2.value=min(vec2(worldTime, frameCounter), vec2(100, 100))
                uniform.vec2.selected=if(true, value, vec2(rainStrength, wetness))
                """);
        uniforms.evaluate(name -> { calls.add(name); return 5.0; }, .05);
        assertEquals(List.of("worldTime", "frameCounter"), calls);
        vector(uniforms, "selected", 5, 5);
    }

    @Test
    void copiesValuesWithoutAliasingAndClearsMissingValues() {
        CustomUniforms uniforms = CustomUniforms.parse("uniform.vec2.value=vec2(1, 2)");
        float[] result = {9, 9, 9, 9};
        uniforms.copyVector("value", result);
        assertArrayEquals(new float[4], result);
        uniforms.evaluate(name -> null, .05);
        uniforms.copyVector("value", result);
        assertArrayEquals(new float[]{1, 2, 0, 0}, result);
        result[0] = 99;
        vector(uniforms, "value", 1, 2);
        assertThrows(IllegalArgumentException.class, () -> uniforms.copyVector("value", new float[1]));
        CustomUniforms.EMPTY.copyVector("value", result);
        assertArrayEquals(new float[4], result);
    }

    @Test
    void geometryAdaptersExposeVectorTypesAndRejectWrongShaderDeclarations() throws Exception {
        CustomUniforms.activate(CustomUniforms.parse("uniform.vec3.tint=vec3(1, 2, 3)"));
        var adapted = ShaderPackUniformAdapter.extract("uniform vec3 tint;\nvoid main(){}", "gbuffers_entities");
        assertTrue(adapted.fields().contains(new ShaderPackUniformAdapter.UniformField("vec3", "tint")));
        String terrain = TerrainShaderAdapter.adaptFragment("uniform vec3 tint;\nvoid main(){gl_FragColor=vec4(tint,1);}", false);
        assertTrue(terrain.contains("    vec3 tint;"));
        assertThrows(java.io.IOException.class, () -> ShaderPackUniformAdapter.extract("uniform vec4 tint;\nvoid main(){}", "gbuffers_entities"));
    }
}
