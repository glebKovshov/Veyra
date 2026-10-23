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
        assertTrue(adapted.contains("#define VEYRA_INTERNAL_POST_OUT_0 layout(location = 0) noperspective out vec2 texCoord"), adapted);
        assertEquals(1, occurrences(adapted, "VEYRA_INTERNAL_POST_OUT_0;"), adapted);
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

    @Test
    void linksBslStylePostPassVaryingsAcrossVertexAndFragmentStages() throws Exception {
        String vertexSource = """
                varying vec2 texCoord;
                varying vec3 sunVec, upVec, eastVec;
                uniform float timeAngle;
                void main() {
                    texCoord = gl_MultiTexCoord0.xy;
                    sunVec = vec3(timeAngle);
                    upVec = vec3(0.0, 1.0, 0.0);
                    eastVec = vec3(1.0, 0.0, 0.0);
                    gl_Position = ftransform();
                }
                """;
        String fragmentSource = """
                varying vec2 texCoord;
                varying vec3 sunVec, upVec, eastVec;
                void main() {
                    gl_FragColor = vec4(texCoord, dot(sunVec, upVec) + eastVec.x, 1.0);
                }
                """;

        PostVertexVaryings.Interface linked = PostVertexVaryings.link("deferred1", vertexSource, fragmentSource);
        String adaptedVertex = adapt(vertexSource, List.of(), Set.of(), false, linked);
        String adaptedFragment = PostFragmentShaderAdapterTest.adapt(fragmentSource, linked);

        assertTrue(adaptedVertex.contains("#define VEYRA_INTERNAL_POST_OUT_1 layout(location = 1) out vec3 sunVec"), adaptedVertex);
        assertTrue(adaptedVertex.contains("#define VEYRA_INTERNAL_POST_OUT_2 layout(location = 2) out vec3 upVec"), adaptedVertex);
        assertTrue(adaptedVertex.contains("#define VEYRA_INTERNAL_POST_OUT_3 layout(location = 3) out vec3 eastVec"), adaptedVertex);
        assertTrue(adaptedFragment.contains("#define VEYRA_INTERNAL_POST_IN_1 layout(location = 1) in vec3 sunVec"), adaptedFragment);
        assertTrue(adaptedFragment.contains("#define VEYRA_INTERNAL_POST_IN_2 layout(location = 2) in vec3 upVec"), adaptedFragment);
        assertTrue(adaptedFragment.contains("#define VEYRA_INTERNAL_POST_IN_3 layout(location = 3) in vec3 eastVec"), adaptedFragment);
        assertFalse(adaptedVertex.contains("varying vec3"), adaptedVertex);
        assertFalse(adaptedFragment.contains("varying vec3"), adaptedFragment);
    }

    private static String adapt(String source, List<String> uniforms, Set<Integer> colorSamplers,
                                boolean usesNoiseSampler) throws Exception {
        PostVertexVaryings.Interface linked = PostVertexVaryings.link(
                "post_fixture", source, "");
        return adapt(source, uniforms, colorSamplers, usesNoiseSampler, linked);
    }

    private static String adapt(String source, List<String> uniforms, Set<Integer> colorSamplers,
                                boolean usesNoiseSampler, PostVertexVaryings.Interface postVaryings) throws Exception {
        Method method = VeyraRenderer.class.getDeclaredMethod("adaptPostVertexShader",
                String.class, String.class, List.class, Set.class, boolean.class, PostVertexVaryings.Interface.class);
        method.setAccessible(true);
        return (String) method.invoke(null, source, "post_fixture", uniforms, colorSamplers, usesNoiseSampler, postVaryings);
    }

    private static int occurrences(String source, String needle) {
        int count = 0;
        for (int index = source.indexOf(needle); index >= 0; index = source.indexOf(needle, index + needle.length())) count++;
        return count;
    }
}
