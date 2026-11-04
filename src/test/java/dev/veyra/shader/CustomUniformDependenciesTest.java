package dev.veyra.shader;

import java.util.List;
import java.util.Map;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class CustomUniformDependenciesTest {
    @Test void resolvesForwardReferencesAndCountsRepeatedDependenciesOnce() {
        CustomUniforms uniforms = CustomUniforms.parse("""
                uniform.bool.enabled=total == 6
                uniform.float.total=base + base
                variable.int.base=frameCounter + 1
                """);
        assertEquals(List.of("enabled", "total"), uniforms.uniforms().stream().map(CustomUniforms.Uniform::name).toList());
        uniforms.evaluate(Map.of("frameCounter", 2.8)::get, 0.05);
        assertEquals(6.0F, uniforms.floatValue("total"));
        assertEquals(1, uniforms.intValue("enabled"));
        uniforms.evaluate(Map.of("frameCounter", 3.0)::get, 0.05);
        assertEquals(8.0F, uniforms.floatValue("total"));
        assertEquals(0, uniforms.intValue("enabled"));
    }

    @Test void rejectsCyclesAndTheirDependentsWhileKeepingIndependentDefinitions() {
        CustomUniforms uniforms = CustomUniforms.parse("""
                uniform.float.a=b + 1
                variable.float.b=a + 1
                uniform.float.dependent=a * 2
                uniform.float.self=self + 1
                uniform.float.independent=4
                """);
        for (String name : List.of("a", "b", "dependent", "self")) {
            assertTrue(uniforms.unavailableReason(name).contains("circular"), name);
            assertNull(uniforms.uniform(name));
        }
        uniforms.evaluate(Map.<String, Double>of()::get, 0.05);
        assertEquals(4.0F, uniforms.floatValue("independent"));
        assertEquals(List.of("a", "b", "dependent", "self"), uniforms.circularDefinitions());
    }

    @Test void packPreflightRejectsUnusedCyclesBeforeInstallingAnyPipeline(@TempDir Path root) throws IOException {
        Path shaders = Files.createDirectories(root.resolve("shaders"));
        Files.writeString(shaders.resolve("shaders.properties"), "variable.float.a=b\nvariable.float.b=a\n");
        Files.writeString(shaders.resolve("final.fsh"), "void main() { gl_FragColor=vec4(1.0); }\n");
        IOException error = assertThrows(IOException.class,
                () -> ShaderPack.inspect(root).validatePipelineCapabilities());
        assertTrue(error.getMessage().contains("Circular custom uniform dependencies: a, b"));
    }

    @Test void propagatesBrokenAndIrisUnresolvedForwardReferences() {
        CustomUniforms uniforms = CustomUniforms.parse("""
                uniform.float.broken=unknown + 1
                variable.float.unknown=notAnInput
                uniform.float.zeroed=biomeValue + 7
                variable.float.biomeValue=BIOME_NOT_REGISTERED_HERE
                """);
        assertTrue(uniforms.unavailableReason("broken").contains("unavailable 'unknown'"));
        assertTrue(uniforms.irisUnresolved().get("zeroed").contains("biomeValue"));
        uniforms.evaluate(Map.<String, Double>of()::get, 0.05);
        assertEquals(0.0F, uniforms.floatValue("zeroed"));
    }

    @Test void duplicateDefinitionsKeepTheFirstDeclarationAsInIris() {
        CustomUniforms uniforms = CustomUniforms.parse("""
                uniform.float.value=base
                variable.int.base=3
                uniform.bool.value=false
                variable.int.base=9
                """);
        assertEquals(CustomUniforms.Type.FLOAT, uniforms.uniform("value").type());
        uniforms.evaluate(Map.<String, Double>of()::get, 0.05);
        assertEquals(3.0F, uniforms.floatValue("value"));
    }

    @Test void aMalformedExpressionDoesNotReserveTheNameOfALaterValidDefinition() {
        CustomUniforms uniforms = CustomUniforms.parse("uniform.float.value=sin(\nuniform.float.value=5\n");
        assertNull(uniforms.unavailableReason("value"));
        uniforms.evaluate(Map.<String, Double>of()::get, 0.05);
        assertEquals(5.0F, uniforms.floatValue("value"));
    }

    @Test void builtInOverridesCanReadTheirFallbackButOtherDefinitionsWaitForTheOverride() {
        CustomUniforms uniforms = CustomUniforms.parse("""
                uniform.float.moving=velocity * 3
                uniform.float.velocity=velocity * 2
                """);
        uniforms.evaluate(Map.of("velocity", 0.5)::get, 0.05);
        assertEquals(1.0F, uniforms.floatValue("velocity"));
        assertEquals(3.0F, uniforms.floatValue("moving"));
    }

    @Test void longDependencyChainsDoNotRecurseDuringOrderingOrEvaluation() {
        StringBuilder properties = new StringBuilder();
        for (int i = 0; i < 1000; i++) properties.append("variable.float.v").append(i)
                .append("=v").append(i + 1).append(" + 1\n");
        properties.append("uniform.float.answer=v0\nvariable.float.v1000=1\n");
        CustomUniforms uniforms = CustomUniforms.parse(properties.toString());
        assertNotNull(uniforms.uniform("answer"));
        uniforms.evaluate(Map.<String, Double>of()::get, 0.05);
        assertEquals(1001.0F, uniforms.floatValue("answer"));
    }

    @Test void unaryAndFlatBinaryExpressionDepthIsBoundedBeforeWalkingTheAst() {
        CustomUniforms uniforms = CustomUniforms.parse("uniform.float.unary=" + "-".repeat(4000) + "1\n"
                + "uniform.float.binary=1" + "+1".repeat(4000) + "\n");
        assertTrue(uniforms.unavailableReason("unary").contains("nested"));
        assertTrue(uniforms.unavailableReason("binary").contains("nested"));
    }
}
