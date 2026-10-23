package dev.veyra.shader;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ComputeShaderAdapterTest {
    @Test
    void supportsImageOnlyComputeWithoutAnEmptyUniformBlockOrBindingGap() throws Exception {
        var stage = ComputeShaderAdapter.adapt("shadowcomp.csh", """
                layout(local_size_x=1,local_size_y=1,local_size_z=1) in;
                const ivec3 workGroups=ivec3(1,1,1);
                writeonly uniform image3D floodfill_img;
                void main(){imageStore(floodfill_img,ivec3(0),vec4(1));}
                """, ShaderPackImageProperties.parse(IMAGE_PROPERTIES));
        assertTrue(stage.uniforms().isEmpty());
        assertEquals(0, stage.resources().getFirst().binding());
        assertFalse(stage.source().contains("VeyraComputeUniforms"));
        assertTrue(stage.source().contains("binding = 0, rgba16f"));
    }

    @Test
    void supportsTwoDimensionalCustomStorageImages() throws Exception {
        var properties = ShaderPackImageProperties.parse("""
                image.screen_img = screen_sampler red_integer r32ui unsigned_int false false 4 4
                """);
        var stage = ComputeShaderAdapter.adapt("shadowcomp.csh", """
                layout(local_size_x=1,local_size_y=1,local_size_z=1) in;
                const ivec3 workGroups=ivec3(1,1,1);
                uniform usampler2D screen_sampler;
                writeonly uniform uimage2D screen_img;
                void main(){
                    uint previous=texelFetch(screen_sampler,ivec2(0),0).r;
                    imageStore(screen_img,ivec2(0),uvec4(previous+1u));
                }
                """, properties);

        assertEquals(List.of("usampler2D", "uimage2D"),
                stage.resources().stream().map(ComputeShaderAdapter.OpaqueResource::glslType).toList());
        assertTrue(stage.source().contains("layout(set = 0, binding = 0) uniform usampler2D screen_sampler;"));
        assertTrue(stage.source().contains("layout(set = 0, binding = 1, r32ui) writeonly uniform uimage2D screen_img;"));
    }
    private static final String IMAGE_PROPERTIES = """
            image.floodfill_img = floodfill_sampler rgba rgba16f half_float false false 32 32 32
            """;

    @Test
    void createsExplicitVulkanBindingsForUniformsSamplerAndStorageImage() throws Exception {
        String source = """
                #version 430 compatibility
                layout (local_size_x = 8, local_size_y = 8, local_size_z = 8) in;
                const ivec3 workGroups = ivec3(16, 8, 16);
                uniform float framemod2;
                uniform sampler3D floodfill_sampler;
                writeonly uniform image3D floodfill_img;
                void main() {
                    ivec3 pos = ivec3(gl_GlobalInvocationID);
                    float value = texelFetch(floodfill_sampler, pos, 0).r + framemod2;
                    imageStore(floodfill_img, pos, vec4(value));
                }
                """;

        var adapted = ComputeShaderAdapter.adapt("shadowcomp.csh", source,
                ShaderPackImageProperties.parse(IMAGE_PROPERTIES));

        assertTrue(adapted.source().startsWith("#version 450\n"));
        assertTrue(adapted.source().contains("layout(std140, set = 0, binding = 0) uniform VeyraComputeUniforms"));
        assertTrue(adapted.source().contains("layout(set = 0, binding = 1) uniform sampler3D floodfill_sampler;"));
        assertTrue(adapted.source().contains("layout(set = 0, binding = 2, rgba16f) writeonly uniform image3D floodfill_img;"));
        assertEquals(1, adapted.uniforms().size());
        assertEquals("framemod2", adapted.uniforms().getFirst().name());
        assertEquals(2, adapted.resources().size());
        assertEquals(8, adapted.localSizeX());
        assertEquals(8, adapted.localSizeY());
        assertEquals(8, adapted.localSizeZ());
        assertTrue(adapted.hasFixedWorkGroups());
        assertEquals(16, adapted.workGroupsX());
        assertEquals(8, adapted.workGroupsY());
        assertEquals(16, adapted.workGroupsZ());
    }

    @Test
    void dropsInactiveBranchesAndUnreferencedSamplerDeclarations() throws Exception {
        String source = """
                #version 430 compatibility
                layout(local_size_x = 4, local_size_y = 2, local_size_z = 1) in;
                #if 0
                uniform sampler3D unavailable_sampler;
                #endif
                uniform sampler2D unused_sampler;
                uniform float framemod2;
                void main() { float value = framemod2; }
                """;

        var adapted = ComputeShaderAdapter.adapt("shadowcomp.csh", source,
                ShaderPackImageProperties.parse(""));

        assertTrue(adapted.resources().isEmpty());
        assertFalse(adapted.source().contains("unavailable_sampler"));
        assertFalse(adapted.source().contains("unused_sampler"));
        assertEquals(4, adapted.localSizeX());
        assertEquals(2, adapted.localSizeY());
        assertEquals(1, adapted.localSizeZ());
    }

    @Test
    void rejectsUsedSamplersWithoutMatchingImageProperties() {
        String source = """
                #version 430 compatibility
                layout(local_size_x = 1, local_size_y = 1, local_size_z = 1) in;
                uniform sampler3D missing_sampler;
                uniform float framemod2;
                void main() { float value = texelFetch(missing_sampler, ivec3(0), 0).r + framemod2; }
                """;
        assertThrows(IOException.class, () -> ComputeShaderAdapter.adapt("shadowcomp.csh", source,
                ShaderPackImageProperties.parse("")));
    }

    @Test
    void rejectsExpressionBasedWorkGroupCountsInsteadOfGuessingFromImageDimensions() {
        String source = """
                #version 430 compatibility
                layout(local_size_x = 8, local_size_y = 8, local_size_z = 8) in;
                const ivec3 workGroups = ivec3(IMAGE_SIZE / 8, 8, 16);
                uniform float framemod2;
                void main() { float value = framemod2; }
                """;
        IOException error = assertThrows(IOException.class, () -> ComputeShaderAdapter.adapt("shadowcomp.csh", source,
                ShaderPackImageProperties.parse(IMAGE_PROPERTIES)));
        assertTrue(error.getMessage().contains("literal const ivec3 workGroups"));
    }

    @Test
    void resolvesRenderRelativeWorkGroupsFromCurrentFramebufferAndLocalSize() throws Exception {
        String source = """
                #version 430 compatibility
                layout(local_size_x = 8, local_size_y = 4, local_size_z = 2) in;
                const vec2 workGroupsRender = vec2(0.5f, 0.25f);
                void main() { }
                """;

        var adapted = ComputeShaderAdapter.adapt("shadowcomp.csh", source,
                ShaderPackImageProperties.parse(""));

        assertFalse(adapted.hasFixedWorkGroups());
        assertTrue(adapted.usesRenderRelativeWorkGroups());
        assertEquals(0.5, adapted.workGroupDispatch().scaleX());
        assertEquals(0.25, adapted.workGroupDispatch().scaleY());
        assertArrayEquals(new int[]{120, 68, 1}, adapted.resolveWorkGroups(1920, 1080));
    }

    @Test
    void defaultsUndeclaredWorkGroupCountToOneInvocationPerRenderPixel() throws Exception {
        String source = """
                #version 430 compatibility
                layout(local_size_x = 16, local_size_y = 8, local_size_z = 2) in;
                void main() { }
                """;

        var adapted = ComputeShaderAdapter.adapt("shadowcomp.csh", source,
                ShaderPackImageProperties.parse(""));

        assertTrue(adapted.usesRenderRelativeWorkGroups());
        assertArrayEquals(new int[]{120, 135, 1}, adapted.resolveWorkGroups(1920, 1080));
    }

    @Test
    void supportsZeroWorkAndSingleArgumentRenderScale() throws Exception {
        var noWork = ComputeShaderAdapter.adapt("shadowcomp.csh", """
                #version 430 compatibility
                layout(local_size_x = 8, local_size_y = 8, local_size_z = 1) in;
                const vec2 workGroupsRender = vec2(0.0f);
                void main() { }
                """, ShaderPackImageProperties.parse(""));
        var oneScale = ComputeShaderAdapter.adapt("shadowcomp.csh", """
                #version 430 compatibility
                layout(local_size_x = 8, local_size_y = 8, local_size_z = 1) in;
                const vec2 workGroupsRender = vec2(0.5);
                void main() { }
                """, ShaderPackImageProperties.parse(""));

        assertArrayEquals(new int[]{0, 0, 1}, noWork.resolveWorkGroups(1920, 1080));
        assertArrayEquals(new int[]{120, 68, 1}, oneScale.resolveWorkGroups(1920, 1080));
    }

    @Test
    void preservesIrisFloatRoundingForRenderRelativePixelCounts() throws Exception {
        var adapted = ComputeShaderAdapter.adapt("shadowcomp.csh", """
                #version 430 compatibility
                layout(local_size_x = 1, local_size_y = 1, local_size_z = 1) in;
                const vec2 workGroupsRender = vec2(0.1f);
                void main() { }
                """, ShaderPackImageProperties.parse(""));

        // Iris stores this scale as float; 10 * 0.1f rounds to exactly 1.0f before ceil.
        assertArrayEquals(new int[]{1, 1, 1}, adapted.resolveWorkGroups(10, 10));
    }

    @Test
    void rejectsConflictingOrNegativeWorkGroupSizingDeclarations() {
        String conflicting = """
                #version 430 compatibility
                layout(local_size_x = 1, local_size_y = 1, local_size_z = 1) in;
                const ivec3 workGroups = ivec3(1, 1, 1);
                const vec2 workGroupsRender = vec2(1.0);
                void main() { }
                """;
        String negativeScale = """
                #version 430 compatibility
                layout(local_size_x = 1, local_size_y = 1, local_size_z = 1) in;
                const vec2 workGroupsRender = vec2(1.0, -0.5);
                void main() { }
                """;

        IOException both = assertThrows(IOException.class, () -> ComputeShaderAdapter.adapt(
                "shadowcomp.csh", conflicting, ShaderPackImageProperties.parse("")));
        IOException negative = assertThrows(IOException.class, () -> ComputeShaderAdapter.adapt(
                "shadowcomp.csh", negativeScale, ShaderPackImageProperties.parse("")));
        assertTrue(both.getMessage().contains("cannot declare both"));
        assertTrue(negative.getMessage().contains("invalid workGroupsRender.y"));
    }

    @Test
    void mapsIrisFogColorIntoTheComputeUniformBlock() throws Exception {
        String source = """
                #version 430 compatibility
                layout(local_size_x = 1, local_size_y = 1, local_size_z = 1) in;
                uniform float framemod2;
                uniform vec3 fogColor;
                void main() { vec3 value = fogColor * framemod2; }
                """;

        var adapted = ComputeShaderAdapter.adapt("shadowcomp.csh", source,
                ShaderPackImageProperties.parse(""));

        assertTrue(adapted.source().contains("#define fogColor (FogColor.rgb)"), adapted.source());
        assertTrue(adapted.source().contains("vec4 FogColor;"), adapted.source());
        assertTrue(adapted.uniforms().stream().anyMatch(field -> field.name().equals("framemod2")));
        assertTrue(adapted.uniforms().stream().anyMatch(field -> field.name().equals("FogColor")));
    }

    @Test
    void resolvesPackCustomUniformsFromTheActivePackBeforeComputePreflight() throws Exception {
        String source = """
                #version 430 compatibility
                layout(local_size_x = 1, local_size_y = 1, local_size_z = 1) in;
                const ivec3 workGroups = ivec3(1, 1, 1);
                uniform float packBrightness;
                void main() { float value = packBrightness; }
                """;
        CustomUniforms previous = CustomUniforms.active();
        try {
            CustomUniforms.activate(CustomUniforms.EMPTY);
            IOException unavailable = assertThrows(IOException.class, () -> ComputeShaderAdapter.adapt(
                    "shadowcomp.csh", source, ShaderPackImageProperties.parse("")));
            assertTrue(unavailable.getMessage().contains("custom uniform 'packBrightness' is not supported yet"),
                    unavailable.getMessage());

            CustomUniforms.activate(CustomUniforms.parse("uniform.float.packBrightness=0.5\n"));
            var adapted = ComputeShaderAdapter.adapt("shadowcomp.csh", source, ShaderPackImageProperties.parse(""));

            assertTrue(adapted.uniforms().stream().anyMatch(field -> field.name().equals("packBrightness")));
        } finally {
            CustomUniforms.activate(previous);
        }
    }

    @Test
    void expandsIrisCustomImageFeatureBranchesBeforeAssigningBindings() throws Exception {
        String source = """
                #version 430 compatibility
                layout(local_size_x = 1, local_size_y = 1, local_size_z = 1) in;
                #if defined IRIS_FEATURE_CUSTOM_IMAGES
                uniform sampler3D volume_sampler;
                #endif
                uniform float framemod2;
                void main() { float value = texelFetch(volume_sampler, ivec3(0), 0).r + framemod2; }
                """;

        var adapted = ComputeShaderAdapter.adapt("shadowcomp.csh", source,
                ShaderPackImageProperties.parse("image.volume_img = volume_sampler rgba rgba16f half_float false false 4 4 4"));

        assertEquals(1, adapted.resources().size());
        assertTrue(adapted.source().contains("layout(set = 0, binding = 1) uniform sampler3D volume_sampler;"));
    }
}
