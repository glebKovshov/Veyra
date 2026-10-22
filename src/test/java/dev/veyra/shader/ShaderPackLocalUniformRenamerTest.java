package dev.veyra.shader;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ShaderPackLocalUniformRenamerTest {
    @Test
    void renamesParametersAndKeepsGlobalUniformUsesOutsideTheirFunction() {
        String source = """
                float linearDepth(float depth, float far, float near) {
                    return depth / far + near;
                }
                float globalDepth() {
                    return far + near;
                }
                """;

        String adapted = ShaderPackLocalUniformRenamer.renameShadowingLocals(source, List.of("far", "near"));

        assertTrue(adapted.contains("float depth, float veyraLocalFar, float veyraLocalNear"), adapted);
        assertTrue(adapted.contains("depth / veyraLocalFar + veyraLocalNear"), adapted);
        assertTrue(adapted.contains("return far + near;"), adapted);
    }

    @Test
    void renamesOnlyWithinTheShadowingBlockAndPreservesMemberNames() {
        String source = """
                struct Values { float pi; };
                float localPi(Values value) {
                    {
                        const float pi = 3.14159;
                        value.pi += pi;
                    }
                    return value.pi + pi;
                }
                """;

        String adapted = ShaderPackLocalUniformRenamer.renameShadowingLocals(source, List.of("pi"));

        assertTrue(adapted.contains("const float veyraLocalPi = 3.14159;"), adapted);
        assertTrue(adapted.contains("value.pi += veyraLocalPi;"), adapted);
        assertTrue(adapted.contains("return value.pi + pi;"), adapted);
    }

    @Test
    void renamesGlobalShaderConstantsThatShadowPackUniforms() {
        String source = """
                const float pi = 3.14159;
                float globalValue() { return pi; }
                float localValue() { const float pi = 2.0; return pi; }
                """;

        String adapted = ShaderPackLocalUniformRenamer.renameShadowingLocals(source, List.of("pi"));

        assertTrue(adapted.contains("const float veyraLocalPi_2 = 3.14159;"), adapted);
        assertTrue(adapted.contains("float globalValue() { return veyraLocalPi_2; }"), adapted);
        assertTrue(adapted.contains("const float veyraLocalPi = 2.0; return veyraLocalPi;"), adapted);
    }
}
