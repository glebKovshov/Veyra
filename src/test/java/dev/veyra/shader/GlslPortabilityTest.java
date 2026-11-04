package dev.veyra.shader;

import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

/** Photon-style OpenGL GLSL that Vulkan GLSL or Veyra's one-line parsers would otherwise reject. */
class GlslPortabilityTest {
    @Test
    void renamesTheVulkanSamplerKeywordAndCollapsesMultilineLayouts() throws IOException {
        String processed = GlslPreprocessor.process("gbuffers_water.fsh", """
                #version 400
                layout(
                    location = 0
                ) out vec4 gbuffer_data_0;
                vec4 bicubic(sampler2D sampler, vec2 coord) { return texture(sampler, coord); }
                void main() { gbuffer_data_0 = vec4(1.0); }
                """);

        assertTrue(processed.contains("layout(location = 0) out vec4 gbuffer_data_0;"), processed);
        assertTrue(processed.contains("bicubic(sampler2D veyra_sampler, vec2 coord)"), processed);
        assertFalse(processed.matches("(?s).*\\bsampler\\b.*"), processed);
    }

    @Test
    void widensNarrowFragmentOutputsAfterThePackMain() throws IOException {
        String processed = GlslPreprocessor.process("composite5.fsh", """
                #version 400
                layout(location = 0) out vec3 scene_color;
                layout(location = 1) out float bloom;
                void main() {
                    scene_color = vec3(0.5);
                    bloom = 0.25;
                }
                """);

        assertTrue(processed.contains("layout(location = 0) out vec4 veyraWideOutput_scene_color;"), processed);
        assertTrue(processed.contains("vec3 scene_color;"), processed);
        assertTrue(processed.contains("void veyraNarrowOutputsMain()"), processed);
        assertTrue(processed.contains("veyraWideOutput_scene_color = vec4(scene_color, 1.0);"), processed);
        assertTrue(processed.contains("veyraWideOutput_bloom = vec4(bloom, 0.0, 0.0, 1.0);"), processed);
        assertEquals(processed, NarrowFragmentOutputs.widen(processed), "widening is idempotent");
    }

    @Test
    void keepsStructPostVaryingsAtTheirDeclarationWithLinkedLocations() throws IOException {
        String struct = "struct CloudsParameters { vec3 a; vec3 b; float c; };\n";
        String vertex = "#version 400\n" + struct + "out vec2 uv;\nflat out CloudsParameters clouds_params;\n"
                + "out mat2x4 basis;\nvoid main() { gl_Position = vec4(0.0); }\n";
        String fragment = "#version 400\n" + struct + "in vec2 uv;\nflat in CloudsParameters clouds_params;\n"
                + "in mat2x4 basis;\nvoid main() { }\n";

        var linked = PostVertexVaryings.link("prepare", vertex, fragment);

        assertEquals(java.util.Set.of("clouds_params"), linked.structVaryings());
        int structLocation = linked.varying("clouds_params", true).location();
        int basisLocation = linked.varying("basis", true).location();
        // The struct spans three locations and the matrix two; neither may overlap another varying.
        assertTrue(basisLocation >= structLocation + 3 || structLocation >= basisLocation + 2,
                "struct@" + structLocation + " basis@" + basisLocation);
        assertFalse(linked.vertexDeclarations().contains("CloudsParameters"));
        String strippedFragment = PostVertexVaryings.stripFragmentDeclarations(fragment, linked);
        assertTrue(strippedFragment.contains("VEYRA_INTERNAL_POST_LOCATION(" + structLocation
                + ") flat in CloudsParameters clouds_params"), strippedFragment);
        assertTrue(strippedFragment.indexOf("struct CloudsParameters")
                < strippedFragment.indexOf("in CloudsParameters"), strippedFragment);
    }
}
