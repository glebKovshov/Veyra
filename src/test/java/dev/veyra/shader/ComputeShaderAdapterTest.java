package dev.veyra.shader;

import org.junit.jupiter.api.Test;

import java.io.IOException;

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
        assertTrue(error.getMessage().contains("fixed literal const ivec3 workGroups"));
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
