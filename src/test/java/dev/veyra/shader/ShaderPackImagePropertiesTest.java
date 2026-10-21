package dev.veyra.shader;

import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShaderPackImagePropertiesTest {
    @Test
    void parsesComplementaryThreeDimensionalVoxelAndFloodfillImages() throws Exception {
        ShaderPackImageProperties properties = ShaderPackImageProperties.parse("""
                image.voxel_img = voxel_sampler red_integer r16ui unsigned_int true false 128 64 128
                image.floodfill_img = floodfill_sampler rgba rgba16f half_float false false 128 64 128
                """);

        ShaderPackImageProperties.Image voxel = properties.images().get("voxel_img");
        assertEquals("voxel_sampler", voxel.sampler());
        assertTrue(voxel.is3d());
        assertTrue(voxel.clear());
        assertEquals(2, voxel.bytesPerTexel());
        assertArrayEquals(new int[]{128, 64, 128}, properties.resolveDimensions(voxel));

        ShaderPackImageProperties.Image floodfill = properties.images().get("floodfill_img");
        assertEquals("floodfill_sampler", floodfill.sampler());
        assertFalse(floodfill.clear());
        assertEquals(8, floodfill.bytesPerTexel());
        assertEquals(org.lwjgl.vulkan.VK10.VK_FORMAT_R16G16B16A16_SFLOAT, floodfill.vulkanFormat());
    }

    @Test
    void resolvesActiveIntegerDimensionMacros() throws Exception {
        ShaderPackImageProperties properties = ShaderPackImageProperties.parse("""
                #define COLORED_LIGHTING 512
                image.wsr_img = wsr_sampler red_integer r16ui unsigned_int true false COLORED_LIGHTING 64 COLORED_LIGHTING
                """);

        assertArrayEquals(new int[]{512, 64, 512},
                properties.resolveDimensions(properties.images().get("wsr_img")));
    }

    @Test
    void reportsUnresolvedMacrosAndUnsupportedFormatsInsteadOfGuessing() throws Exception {
        ShaderPackImageProperties unresolved = ShaderPackImageProperties.parse(
                "image.test = test_sampler rgba rgba16f half_float false false WIDTH 4");
        assertThrows(IOException.class,
                () -> unresolved.resolveDimensions(unresolved.images().get("test")));

        ShaderPackImageProperties unsupported = ShaderPackImageProperties.parse(
                "image.test = test_sampler rgba rgba16 rgba_unsigned_short false false 4 4");
        assertThrows(IOException.class, () -> unsupported.images().get("test").vulkanFormat());
    }

    @Test
    void rejectsInvalidImageDeclarations() {
        assertThrows(IOException.class, () -> ShaderPackImageProperties.parse(
                "image.test = test_sampler rgba rgba16f half_float maybe false 4 4"));
        assertThrows(IOException.class, () -> ShaderPackImageProperties.parse(
                "image.test = test_sampler rgba rgba16f half_float false false 4 4 4 4"));
    }
}
