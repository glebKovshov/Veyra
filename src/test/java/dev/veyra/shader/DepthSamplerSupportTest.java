package dev.veyra.shader;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DepthSamplerSupportTest {
    @Test
    void allowsSupportedInputsAndGenericDepthSamplerParameters() {
        assertTrue(DepthSamplerSupport.firstUnsupported(
                "uniform sampler2D depthtex0;\nuniform sampler2D depthtex1;\n"
                        + "uniform sampler2D depthtex2;\n"
                        + "float readDepth(sampler2D depthtex, vec2 uv){return texture(depthtex, uv).r;}\n")
                .isEmpty());
    }

    @Test
    void rejectsOnlyDepthTextureInputsOutsideTheSupportedRange() {
        assertEquals("depthtex3", DepthSamplerSupport.firstUnsupported(
                "uniform sampler2D depthtex3;\n").orElseThrow());
        assertEquals("depthtex12", DepthSamplerSupport.firstUnsupported(
                "texture(depthtex12, uv);\n").orElseThrow());
        assertTrue(DepthSamplerSupport.firstUnsupported("// depthtex3 is just a comment\n").isEmpty());
    }
}
