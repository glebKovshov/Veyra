package dev.veyra.shader;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CustomUniformsTest {
    // Definitions in the style of BSL 10.1.8 shaders.properties.
    private static final String BSL_STYLE = """
            # comment
            variable.float.tAmin=frac(sunAngle - 0.033333333)
            variable.float.tAlin=if(tAmin < 0.433333333, tAmin * 1.15384615385, tAmin * 0.882352941176 + 0.117647058824)
            variable.float.hA=if(tAlin > 0.5, 1.0, 0.0)
            variable.float.tAfrc=frac(tAlin * 2.0)
            variable.float.tAfrs=tAfrc * tAfrc * (3.0 - 2.0 * tAfrc)
            variable.float.tAmix=if(hA < 0.5, 0.3, -0.1)
            uniform.float.timeAngle=(tAfrc * (1.0 - tAmix) + tAfrs * tAmix + hA) * 0.5
            uniform.float.shadowFade=clamp(1.0 - (abs(abs(sunAngle - 0.5) - 0.25) - 0.23) * 100.0, 0.0, 1.0)
            uniform.float.timeBrightness=max(sin(timeAngle * 6.28318530718), 0.0)
            variable.float.blindFactorSqrt=clamp(blindness * 2.0 - 1.0, 0.0, 1.0)
            uniform.float.blindFactor=blindFactorSqrt * blindFactorSqrt
            uniform.float.isDesert=smooth(2, if(in(biome, 2, 17, 130), 1, 0), 10, 10)
            uniform.float.isCold=smooth(1, if(in(biome, 10, 11), 1, 0), 10, 10) + \\
                0.5
            uniform.int.framemod8 = frameCounter % 8
            uniform.bool.underwater=isEyeInWater == 1 && !is_sneaking
            uniform.vec3.skyTint=vec3(1.0, 0.5, 0.25)
            """;

    @AfterEach
    void reset() {
        CustomUniforms.activate(CustomUniforms.EMPTY);
    }

    private static Map<String, Double> inputs(double sunAngle, double blindness, int frameCounter) {
        return Map.of("sunAngle", sunAngle, "blindness", blindness, "frameCounter", (double) frameCounter,
                "isEyeInWater", 1.0, "is_sneaking", 0.0);
    }

    @Test
    void parsesUniformsInOrderAndKeepsHelperVariablesPrivate() {
        CustomUniforms uniforms = CustomUniforms.parse(BSL_STYLE);
        assertEquals(List.of("timeAngle", "shadowFade", "timeBrightness", "blindFactor", "isDesert", "isCold", "framemod8", "underwater"),
                uniforms.uniforms().stream().map(CustomUniforms.Uniform::name).toList());
        assertNull(uniforms.uniform("tAmin"));
        assertEquals(CustomUniforms.Type.INT, uniforms.uniform("framemod8").type());
        assertEquals(CustomUniforms.Type.BOOL, uniforms.uniform("underwater").type());
    }

    @Test
    void evaluatesBslTimeAndShadowFadeExpressions() {
        CustomUniforms uniforms = CustomUniforms.parse(BSL_STYLE);
        uniforms.evaluate(inputs(0.25, 0.75, 13)::get, 0.05);
        // Noon: sun high, shadows fully visible.
        assertEquals(1.0F, uniforms.floatValue("shadowFade"), 1.0e-6F);
        assertEquals(0.25F, uniforms.floatValue("blindFactor"), 1.0e-6F);
        assertEquals(5, uniforms.intValue("framemod8"));
        assertEquals(1, uniforms.intValue("underwater"));
        float timeAngle = uniforms.floatValue("timeAngle");
        assertTrue(timeAngle > 0.0F && timeAngle < 0.5F, "timeAngle " + timeAngle);
        assertEquals(Math.max(Math.sin(timeAngle * 6.28318530718), 0.0), uniforms.floatValue("timeBrightness"), 1.0e-5);

        // At sunset (sunAngle 0.5) shadowFade drops to zero.
        uniforms.evaluate(inputs(0.5, 0.0, 16)::get, 0.05);
        assertEquals(0.0F, uniforms.floatValue("shadowFade"), 1.0e-6F);
        assertEquals(0, uniforms.intValue("framemod8"));
    }

    @Test
    void reportsUnavailableInputsVectorsAndDependents() {
        CustomUniforms uniforms = CustomUniforms.parse(BSL_STYLE);
        // biome is an Iris input; numeric ids work, unknown BIOME_* names stay unavailable.
        assertNotNull(uniforms.uniform("isDesert"));
        assertNotNull(uniforms.uniform("isCold"));
        // Iris cannot resolve an unregistered biome constant either: it warns and the uniform reads 0.
        CustomUniforms unregistered = CustomUniforms.parse("""
                variable.float.inSulfur=smooth(56, if(in(biome, BIOME_NOT_REGISTERED_HERE), 1, 0), 15, 15)
                uniform.float.x=inSulfur * 2.0 + 1.0
                uniform.int.y=7
                """);
        assertNull(unregistered.unavailableReason("x"));
        assertEquals(CustomUniforms.Type.FLOAT, unregistered.uniform("x").type());
        assertTrue(unregistered.irisUnresolved().get("inSulfur").contains("BIOME_NOT_REGISTERED_HERE"));
        assertTrue(unregistered.irisUnresolved().get("x").contains("inSulfur"));
        assertFalse(unregistered.irisUnresolved().containsKey("y"));
        unregistered.evaluate(Map.of("biome", 3.0)::get, 0.05);
        assertEquals(0.0F, unregistered.floatValue("x"));
        assertEquals(7, unregistered.intValue("y"));
        assertTrue(uniforms.unavailableReason("skyTint").contains("vec3"));
        CustomUniforms dependent = CustomUniforms.parse("variable.float.a=lightning\nuniform.float.b=a * 2.0\n");
        assertTrue(dependent.unavailableReason("b").contains("unavailable 'a'"));
        assertNull(uniforms.unavailableReason("shadowFade"));
    }

    @Test
    void rejectsMalformedExpressionsAndBuiltInShadowing() {
        CustomUniforms uniforms = CustomUniforms.parse("""
                uniform.float.bad=clamp(1.0, 2.0
                uniform.float.unknownFunction=noise(1.0)
                uniform.float.sunAngle=0.5
                uniform.int.shadowFade=1
                """);
        assertTrue(uniforms.uniforms().isEmpty());
        assertTrue(uniforms.unavailableReason("bad").startsWith("invalid expression"));
        assertTrue(uniforms.unavailableReason("unknownFunction").contains("noise"));
        assertTrue(uniforms.unavailableReason("sunAngle").contains("built-in"));
        assertTrue(uniforms.unavailableReason("shadowFade").contains("must remain a float"));
    }

    @Test
    void customShadowFadeReusesTheBuiltInPostUniformSlot() {
        CustomUniforms uniforms = CustomUniforms.parse("uniform.float.shadowFade=1.0\n");

        assertEquals(1, uniforms.uniforms().size());
        assertEquals(List.of("sunAngle", "shadowFade"), ShaderPackUniformAdapter.mergeUniformNames(
                List.of("sunAngle", "shadowFade"), uniforms.uniforms()));
    }

    @Test
    void operatorPrecedenceTernaryAndDivisionByZero() {
        CustomUniforms uniforms = CustomUniforms.parse("""
                uniform.float.a=1 + 2 * 3 - 4 / 2
                uniform.float.b=frameTime > 0.5 ? 2.0 : -1.0e1
                uniform.float.c=1.0 / 0.0
                uniform.float.d=if(frameTime < 0.0, 1, frameTime < 1.0, 2, 3)
                uniform.int.e=-7.9
                uniform.float.f=pow(2, 10) + atan2(0, 1) + between(5, 1, 10) + equals(1.0, 1.05, 0.1)
                """);
        uniforms.evaluate(Map.of("frameTime", 0.25)::get, 0.25);
        assertEquals(5.0F, uniforms.floatValue("a"));
        assertEquals(-10.0F, uniforms.floatValue("b"));
        assertEquals(0.0F, uniforms.floatValue("c"));
        assertEquals(2.0F, uniforms.floatValue("d"));
        assertEquals(-7, uniforms.intValue("e"));
        assertEquals(1026.0F, uniforms.floatValue("f"));
    }

    @Test
    void smoothUsesIrisHalfLivesInTenthsOfASecond() {
        // Iris SmoothFloat: half-life = fade * 0.1 s, alpha = 1 - exp(-ln2 / halfLife * frameTime).
        CustomUniforms uniforms = CustomUniforms.parse("uniform.float.s=smooth(1, frameCounter, 1, 10)\n");
        uniforms.evaluate(Map.of("frameCounter", 0.0)::get, 0.05);
        assertEquals(0.0F, uniforms.floatValue("s"));
        // Rising uses fadeUp = 1 (0.1 s half-life): 0.1 s halves the remaining distance.
        uniforms.evaluate(Map.of("frameCounter", 1.0)::get, 0.1);
        assertEquals(0.5F, uniforms.floatValue("s"), 1.0e-6F);
        uniforms.evaluate(Map.of("frameCounter", 1.0)::get, 0.2);
        assertEquals(0.875F, uniforms.floatValue("s"), 1.0e-6F);
        // Falling uses fadeDown = 10 (1 s half-life).
        uniforms.evaluate(Map.of("frameCounter", 0.0)::get, 1.0);
        assertEquals(0.4375F, uniforms.floatValue("s"), 1.0e-6F);
    }

    @Test
    void smoothArgumentFormsMatchIris() {
        CustomUniforms uniforms = CustomUniforms.parse("""
                uniform.float.defaultFade=smooth(frameCounter)
                uniform.float.withId=smooth(7, frameCounter)
                uniform.float.oneFade=smooth(frameCounter, 2)
                uniform.float.immediate=smooth(frameCounter, 0, 0)
                """);
        uniforms.evaluate(Map.of("frameCounter", 0.0)::get, 0.05);
        uniforms.evaluate(Map.of("frameCounter", 1.0)::get, 0.1);
        assertEquals(0.5F, uniforms.floatValue("defaultFade"), 1.0e-6F);
        // A constant first argument is OptiFine's id, so the default fade still applies.
        assertEquals(0.5F, uniforms.floatValue("withId"), 1.0e-6F);
        // A single fade argument is used for both directions: 0.2 s half-life.
        assertEquals((float) (1.0 - Math.pow(0.5, 0.5)), uniforms.floatValue("oneFade"), 1.0e-6F);
        assertEquals(1.0F, uniforms.floatValue("immediate"));
    }

    @Test
    void irisArithmeticFunctions() {
        CustomUniforms uniforms = CustomUniforms.parse("""
                uniform.float.floorMod=fmod(-1, 4)
                uniform.float.edgeLow=edge(0.5, 0.2)
                uniform.float.edgeHigh=edge(0.5, 0.7)
                uniform.float.logBase=log(2, 8)
                uniform.float.common=log10(1000) + exp10(2)
                uniform.float.rand=random()
                uniform.float.randRange=random(2, 4)
                uniform.int.randInt=randomInt(5)
                uniform.int.randIntRange=randomInt(3, 6)
                """);
        assertTrue(uniforms.uniforms().size() == 9, uniforms.uniforms().toString());
        uniforms.evaluate(Map.<String, Double>of()::get, 0.05);
        assertEquals(3.0F, uniforms.floatValue("floorMod"));
        assertEquals(0.0F, uniforms.floatValue("edgeLow"));
        assertEquals(1.0F, uniforms.floatValue("edgeHigh"));
        assertEquals(3.0F, uniforms.floatValue("logBase"), 1.0e-6F);
        assertEquals(103.0F, uniforms.floatValue("common"), 1.0e-4F);
        assertTrue(uniforms.floatValue("rand") >= 0.0F && uniforms.floatValue("rand") < 1.0F);
        assertTrue(uniforms.floatValue("randRange") >= 2.0F && uniforms.floatValue("randRange") < 4.0F);
        assertTrue(uniforms.intValue("randInt") >= 0 && uniforms.intValue("randInt") < 5);
        assertTrue(uniforms.intValue("randIntRange") >= 3 && uniforms.intValue("randIntRange") < 6);
    }

    @Test
    void velocityIsAPackOverridableIrisUniform() {
        CustomUniforms overridden = CustomUniforms.parse("uniform.float.velocity=frameTime * 2.0\n");
        assertNotNull(overridden.uniform("velocity"));
        overridden.evaluate(Map.of("frameTime", 0.25)::get, 0.25);
        assertEquals(0.5F, overridden.floatValue("velocity"));
        assertTrue(CustomUniforms.parse("uniform.int.velocity=1\n").unavailableReason("velocity")
                .contains("must remain a float"));
        // Expressions may read the built-in value, as with Complementary's own velocity helpers.
        assertNotNull(CustomUniforms.parse("uniform.float.moving=if(velocity > 0.01, 1, 0)\n").uniform("moving"));

        CustomUniforms.activate(overridden);
        long velocitySlots = TerrainShaderAdapter.shaderPackUniforms().stream()
                .filter(uniform -> uniform.name().equals("velocity")).count();
        assertEquals(1, velocitySlots, "a pack override must not add a second uniform block member");
    }

    @Test
    void biomeConstantsFollowRegistrationOrder() {
        BiomeIds.register("veyra_test:first_fixture_biome");
        BiomeIds.register("veyra_test:second_fixture_biome");
        int second = BiomeIds.id("veyra_test:second_fixture_biome");
        assertEquals(BiomeIds.id("veyra_test:first_fixture_biome") + 1, second);
        assertEquals(second, BiomeIds.constant("BIOME_SECOND_FIXTURE_BIOME"));
        assertEquals(0, BiomeIds.id("veyra_test:unregistered"));
        CustomUniforms uniforms = CustomUniforms.parse(
                "uniform.float.match=if(in(biome, BIOME_SECOND_FIXTURE_BIOME), 1, 0) + PPT_SNOW\n");
        uniforms.evaluate(Map.of("biome", (double) second)::get, 0.05);
        assertEquals(3.0F, uniforms.floatValue("match"));
        assertEquals(String.valueOf(second), GlslPreprocessor.environment().get("BIOME_SECOND_FIXTURE_BIOME"));
    }

    @Test
    void deepNestingIsBounded() {
        String deep = "(".repeat(500) + "1" + ")".repeat(500);
        CustomUniforms uniforms = CustomUniforms.parse("uniform.float.deep=" + deep + "\n");
        assertTrue(uniforms.unavailableReason("deep").contains("nested"));
    }

    @Test
    void geometryAdaptersExposeActiveCustomUniforms() throws IOException {
        CustomUniforms.activate(CustomUniforms.parse(BSL_STYLE));
        ShaderPackUniformAdapter.AdaptedShader adapted = ShaderPackUniformAdapter.extract(
                "uniform float shadowFade;\nuniform bool underwater;\nvoid main(){}\n", "gbuffers_entities");
        assertTrue(adapted.fields().contains(new ShaderPackUniformAdapter.UniformField("float", "shadowFade")));
        assertTrue(adapted.fields().contains(new ShaderPackUniformAdapter.UniformField("int", "veyra_underwater")));
        assertTrue(adapted.macros().contains("#define underwater (veyra_underwater != 0)"));

        String terrain = TerrainShaderAdapter.adaptFragment(
                "uniform float shadowFade;\nvoid main(){gl_FragColor=vec4(shadowFade);}\n", false);
        assertTrue(terrain.contains("    float shadowFade;"));

        IOException missing = assertThrows(IOException.class, () -> ShaderPackUniformAdapter.extract(
                "uniform float skyTint;\nvoid main(){}\n", "gbuffers_entities"));
        assertTrue(missing.getMessage().contains("vec3"), missing.getMessage());
        assertThrows(IOException.class, () -> ShaderPackUniformAdapter.extract(
                "uniform int shadowFade;\nvoid main(){}\n", "gbuffers_entities"));

        CustomUniforms.activate(CustomUniforms.EMPTY);
        ShaderPackUniformAdapter.AdaptedShader irisDefault = ShaderPackUniformAdapter.extract(
                "uniform float shadowFade;\nvoid main(){}\n", "gbuffers_entities");
        assertTrue(irisDefault.fields().contains(new ShaderPackUniformAdapter.UniformField("float", "shadowFade")));
    }

    @Test
    void shaderPackUniformAdapterIgnoresUniformDeclarationsInsideComments() throws IOException {
        String source = "/*\nuniform sampler2D shadowtex0;\n*/\nvoid main() {}\n";

        ShaderPackUniformAdapter.AdaptedShader adapted =
                ShaderPackUniformAdapter.extract(source, "gbuffers_textured");

        assertEquals(source, adapted.source());
        assertTrue(adapted.declarations().isEmpty());
        assertTrue(adapted.fields().isEmpty());
    }
}
