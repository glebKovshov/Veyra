package dev.veyra.shader;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PostFragmentShaderAdapterTest {
    @Test
    void adaptsInterpolationLegacyLodFogAndShaderPackUniformsTogether() throws Exception {
        String source = """
                #version 120
                noperspective in vec2 texCoord;
                uniform float near;
                uniform float far;
                void main() {
                    vec4 color = texture2DLod(colortex0, texCoord, 0.0);
                    color.rgb += vec3(gl_Fog.start * gl_Fog.scale + near / far);
                    gl_FragColor = color;
                }
                """;

        String adapted = adapt(source);

        assertEquals(1, occurrences(adapted, "noperspective in vec2 texCoord;"), adapted);
        assertTrue(adapted.contains("textureLod(colortex0"), adapted);
        assertFalse(adapted.contains("texture2DLod"), adapted);
        assertTrue(adapted.contains("layout(std140) uniform Fog {"), adapted);
        assertTrue(adapted.contains("FogEnvironmentalStart"), adapted);
        assertTrue(adapted.contains("FogEnvironmentalEnd"), adapted);
        assertFalse(adapted.contains("gl_Fog"), adapted);
        assertTrue(adapted.contains("#define near veyraParameters.near"), adapted);
        assertTrue(adapted.contains("#define far veyraParameters.far"), adapted);
    }

    @Test
    void routesFogColorThroughFogBlockWithoutMacroRewritingUniformBlockField() throws Exception {
        String source = """
                #version 120
                uniform vec3 fogColor;
                uniform float fogEnd;
                void main() { gl_FragColor = vec4(fogColor * fogEnd, 1.0); }
                """;

        String adapted = adapt(source);

        assertEquals(1, occurrences(adapted, "#define fogColor"), adapted);
        assertTrue(adapted.contains("vec3 fogColor;"), adapted);
        assertTrue(adapted.indexOf("} veyraParameters;") < adapted.indexOf("layout(std140) uniform Fog {"), adapted);
        assertTrue(adapted.contains("#define fogColor (FogColor.rgb)"), adapted);
        assertTrue(adapted.contains("#define fogEnd FogEnvironmentalEnd"), adapted);
        assertTrue(adapted.contains("gl_FragColor = vec4(fogColor * fogEnd, 1.0)"), adapted);
    }

    @Test
    void ignoresSamplerReferencesInUnreachableHelperFunctions() throws Exception {
        String source = """
                #version 120
                uniform sampler2D colortex1;
                uniform sampler2D colortex2;
                uniform sampler2D colortex3;
                uniform sampler2D colortex4;
                uniform sampler2D colortex5;
                uniform sampler2D colortex6;
                uniform sampler2D colortex7;
                uniform sampler2D depthtex0;
                uniform sampler2D depthtex1;
                uniform sampler2D depthtex2;
                uniform sampler2D noisetex;
                uniform sampler2D shadowtex0;
                uniform sampler2D shadowtex1;
                uniform sampler2D shadowcolor0;
                uniform sampler2D shadowcolor1;
                float unused() {
                    return texture2D(colortex1, vec2(0.0)).r + texture2D(colortex2, vec2(0.0)).r
                         + texture2D(colortex3, vec2(0.0)).r + texture2D(colortex4, vec2(0.0)).r
                         + texture2D(colortex5, vec2(0.0)).r + texture2D(colortex6, vec2(0.0)).r
                         + texture2D(colortex7, vec2(0.0)).r + texture2D(depthtex0, vec2(0.0)).r
                         + texture2D(depthtex1, vec2(0.0)).r + texture2D(depthtex2, vec2(0.0)).r
                         + texture2D(noisetex, vec2(0.0)).r + texture2D(shadowtex0, vec2(0.0)).r
                         + texture2D(shadowtex1, vec2(0.0)).r + texture2D(shadowcolor0, vec2(0.0)).r
                         + texture2D(shadowcolor1, vec2(0.0)).r;
                }
                void main() { gl_FragColor = texture2D(colortex0, vec2(0.0)); }
                """;

        String adapted = adapt(source);

        assertFalse(adapted.contains("float unused()"), adapted);
        assertFalse(adapted.contains("#define shadowtex0"), adapted);
        assertTrue(adapted.contains("#define colortex0 InSampler"), adapted);
    }

    @SuppressWarnings("unchecked")
    private static String adapt(String source) throws Exception {
        Method method = VeyraRenderer.class.getDeclaredMethod(
                "adaptShader", String.class, List.class, boolean.class);
        method.setAccessible(true);
        List<Object> shaders = (List<Object>) method.invoke(null, source, List.of(), true);
        Object first = shaders.getFirst();
        Method sourceAccessor = first.getClass().getDeclaredMethod("source");
        sourceAccessor.setAccessible(true);
        return (String) sourceAccessor.invoke(first);
    }

    private static int occurrences(String source, String needle) {
        int count = 0;
        for (int index = source.indexOf(needle); index >= 0; index = source.indexOf(needle, index + needle.length())) {
            count++;
        }
        return count;
    }
}
