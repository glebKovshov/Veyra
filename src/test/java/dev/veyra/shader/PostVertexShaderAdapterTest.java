package dev.veyra.shader;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class PostVertexShaderAdapterTest {
    @Test
    void usesNamedUniformBlockAndPreservesTexCoordInterpolation() throws Exception {
        String source = "#version 120\n"
                + "noperspective out vec2 texCoord;\n"
                + "uniform float sunAngle;\n"
                + "void main() { gl_Position = ftransform(); texCoord = (gl_TextureMatrix[0] * gl_MultiTexCoord0).xy; }\n";

        String adapted = adapt(source, List.of("sunAngle"), Set.of(), false);

        assertTrue(adapted.contains("} veyraParameters;"), adapted);
        assertTrue(adapted.contains("#define sunAngle veyraParameters.sunAngle"), adapted);
        assertTrue(adapted.contains("noperspective out vec2 texCoord;"), adapted);
        assertEquals(1, occurrences(adapted, "out vec2 texCoord;"), adapted);
        assertTrue(adapted.contains("mat4(1.0) * vec4(veyraFullscreenUv()"), adapted);
        assertFalse(adapted.contains("gl_TextureMatrix"), adapted);
    }

    @Test
    void rewritesLegacyExplicitLodSamplingForModernGlsl() throws Exception {
        String source = "#version 120\n"
                + "uniform sampler2D colortex1;\n"
                + "void main() { gl_Position = ftransform(); vec4 c = texture2DLod(colortex1, vec2(0.0), 0.0); }\n";

        String adapted = adapt(source, List.of(), Set.of(1), false);

        assertTrue(adapted.contains("textureLod(colortex1"), adapted);
        assertFalse(adapted.contains("texture2DLod"), adapted);
    }

    @Test
    void keepsGlobalUniformAliasesWhileRenamingShadowingLocalVariables() throws Exception {
        String source = "#version 120\n"
                + "uniform float pi;\n"
                + "float helper() { const float pi = 3.14159; return pi; }\n"
                + "void main() { gl_Position = ftransform(); }\n";

        String adapted = adapt(source, List.of("pi"), Set.of(), false);

        assertTrue(adapted.contains("float pi;"), adapted);
        assertTrue(adapted.contains("#define pi veyraParameters.pi"), adapted);
        assertTrue(adapted.contains("const float veyraLocalPi = 3.14159;"), adapted);
        assertTrue(adapted.contains("return veyraLocalPi;"), adapted);
    }

    @Test
    void keepsNearAndFarUniformAliasesOutsideShadowingFunctionParameters() throws Exception {
        String source = "#version 120\n"
                + "uniform float near;\n"
                + "uniform float far;\n"
                + "float GetLinearDepth(float depth, float far, float near) { return depth / far + near; }\n"
                + "void main() { gl_Position = ftransform(); gl_Position.x += near + far; }\n";

        String adapted = adapt(source, List.of("near", "far"), Set.of(), false);

        assertTrue(adapted.contains("#define near veyraParameters.near"), adapted);
        assertTrue(adapted.contains("#define far veyraParameters.far"), adapted);
        assertTrue(adapted.contains("float depth, float veyraLocalFar, float veyraLocalNear"), adapted);
        assertTrue(adapted.contains("depth / veyraLocalFar + veyraLocalNear"), adapted);
        assertTrue(adapted.contains("gl_Position.x += near + far"), adapted);
    }

    private static String adapt(String source, List<String> uniforms, Set<Integer> colorSamplers,
                                boolean usesNoiseSampler) throws Exception {
        Method method = VeyraRenderer.class.getDeclaredMethod("adaptPostVertexShader",
                String.class, String.class, List.class, Set.class, boolean.class);
        method.setAccessible(true);
        return (String) method.invoke(null, source, "post_fixture", uniforms, colorSamplers, usesNoiseSampler);
    }

    private static int occurrences(String source, String needle) {
        int count = 0;
        for (int index = source.indexOf(needle); index >= 0; index = source.indexOf(needle, index + needle.length())) count++;
        return count;
    }
}
