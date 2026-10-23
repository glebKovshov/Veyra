package dev.veyra.shader;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class PostVertexColorSamplersTest {
    @Test
    void collectsUsedColorBuffersAndBuildsVertexStageBindings() {
        String source = "uniform sampler2D colortex5;\n"
                + "uniform sampler2D colortex2;\n"
                + "uniform sampler2D colortex9;\n"
                + "void main(){gl_Position=vec4(texture(colortex5,vec2(0.5)).r);}\n";

        Set<Integer> samplers = PostVertexColorSamplers.read(source, "deferred1");

        assertEquals(Set.of(5), samplers);
        assertEquals("uniform sampler2D VeyraColortex5Sampler;\n"
                + "#define colortex5 VeyraColortex5Sampler\n",
                PostVertexColorSamplers.declarations(samplers));
    }

    @Test
    void bindsSceneColorToThePostInputSampler() {
        String source = "uniform sampler2D colortex0;\n"
                + "void main(){gl_Position=vec4(texture(colortex0,vec2(0.5)).r);}\n";

        assertEquals(Set.of(0), PostVertexColorSamplers.read(source, "deferred1"));
        assertEquals("uniform sampler2D InSampler;\n"
                + "#define colortex0 InSampler\n"
                + "#define Sampler0 InSampler\n",
                PostVertexColorSamplers.declarations(Set.of(0)));
    }

    @Test
    void ignoresUnusedAndCommentedSamplers() {
        String source = "uniform sampler2D colortex5;\n"
                + "uniform sampler2D colortex6;\n"
                + "// texture(colortex6, vec2(0.5))\n"
                + "void main(){gl_Position=vec4(1.0);}\n";

        assertTrue(PostVertexColorSamplers.read(source, "deferred1").isEmpty());
    }

    @Test
    void rejectsUnsupportedSamplerTypesAndColorBufferIndices() {
        assertTrue(assertThrows(IllegalArgumentException.class, () -> PostVertexColorSamplers.read(
                "uniform samplerCube colortex5;\nvoid main(){gl_Position=texture(colortex5,vec3(1.0));}\n",
                "deferred1")).getMessage().contains("must use sampler2D"));
        assertTrue(assertThrows(IllegalArgumentException.class, () -> PostVertexColorSamplers.read(
                "uniform sampler2D colortex16;\nvoid main(){gl_Position=vec4(texture(colortex16,vec2(0.5)).r);}\n",
                "deferred1")).getMessage().contains("colortex0 through colortex15"));
    }
}
