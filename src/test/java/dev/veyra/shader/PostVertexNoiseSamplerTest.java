package dev.veyra.shader;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PostVertexNoiseSamplerTest {
    @Test
    void bindsTheNoiseSamplerOnlyWhenTheStageUsesIt() {
        assertTrue(PostVertexNoiseSampler.read(
                "uniform sampler2D noisetex;\nvoid main(){gl_Position=vec4(texture(noisetex,vec2(0.5)).r);}\n",
                "deferred1"));
        assertEquals("uniform sampler2D VeyraNoiseSampler;\n#define noisetex VeyraNoiseSampler\n",
                PostVertexNoiseSampler.declaration(true));
        assertFalse(PostVertexNoiseSampler.read(
                "uniform sampler2D noisetex;\nvoid main(){gl_Position=vec4(1.0);}\n", "deferred1"));
        assertEquals("", PostVertexNoiseSampler.declaration(false));
    }

    @Test
    void rejectsUnsupportedNoiseSamplerTypes() {
        assertTrue(assertThrows(IllegalArgumentException.class, () -> PostVertexNoiseSampler.read(
                "uniform sampler3D noisetex;\nvoid main(){gl_Position=texture(noisetex,vec3(0.5));}\n",
                "deferred1")).getMessage().contains("must use sampler2D"));
    }
}
