package dev.veyra.shader;

import dev.veyra.vulkan.VeyraNormalTextures;
import dev.veyra.vulkan.VeyraEntityNormalDescriptor;
import net.vulkanmod.vulkan.shader.descriptor.ImageDescriptor;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

class NormalPixelsTest {
    @Test void resolvesAtlasNormalResourceNames() {
        assertEquals("textures/block/stone_n.png", NormalPixels.path("block/stone", true));
        assertEquals("optifine/cit/armor_n.png", NormalPixels.path("optifine/cit/armor", true));
        assertEquals("textures/entity/cow_n.png", NormalPixels.path("entity/cow", true));
        assertEquals("textures/entity/cow_n.png", NormalPixels.path("textures/entity/cow.png", false));
        assertEquals("textures/entity/cow/temperate_cow_n.png",
                NormalPixels.path("textures/entity/cow/temperate_cow.png", false));
    }

    @Test void createsIrisFlatNormalFallback() {
        NormalPixels.Image flat = NormalPixels.flat(2, 2);
        assertArrayEquals(new int[]{NormalPixels.FLAT_NORMAL, NormalPixels.FLAT_NORMAL,
                NormalPixels.FLAT_NORMAL, NormalPixels.FLAT_NORMAL}, flat.pixels());
        assertEquals(0xff7f7fff, NormalPixels.FLAT_NORMAL);
    }

    @Test void resamplesAndPadsUnitNormalsInsteadOfEncodedRgb() {
        NormalPixels.Image source = new NormalPixels.Image(2, 1,
                new int[]{0xff8080ff, 0xffff80ff});
        NormalPixels.Image atlas = NormalPixels.flat(6, 4);
        NormalPixels.tile(atlas, source, 1, 1, 3, 1, 1);

        int left = atlas.pixels()[1 * 6 + 1];
        int first = atlas.pixels()[1 * 6 + 2];
        int middle = atlas.pixels()[1 * 6 + 3];
        int last = atlas.pixels()[1 * 6 + 4];
        int right = atlas.pixels()[1 * 6 + 5];
        assertEquals(first, left);
        assertEquals(last, right);
        assertEquals(0xff8080ff, first);
        assertTrue((middle >>> 16 & 255) > 170, Integer.toHexString(middle));
        assertTrue((middle & 255) > 220, Integer.toHexString(middle));
        assertTrue((last >>> 16 & 255) > 200, Integer.toHexString(last));
        assertUnit(last);
        assertUnit(middle);
    }

    @Test void buildsMipsByAveragingAndRenormalizingNormalVectors() {
        NormalPixels.Image source = new NormalPixels.Image(2, 2,
                new int[]{0xff8080ff, 0xffff80ff, 0xff8080ff, 0xffff80ff});
        NormalPixels.Image mip = NormalPixels.mip(source);
        assertEquals(1, mip.width());
        assertEquals(1, mip.height());
        assertTrue((mip.pixels()[0] >>> 16 & 255) > 170);
        assertTrue((mip.pixels()[0] & 255) > 220);
        assertUnit(mip.pixels()[0]);

        NormalPixels.Image flat = NormalPixels.flat(2, 2);
        assertEquals(NormalPixels.FLAT_NORMAL, NormalPixels.mip(flat).pixels()[0]);
    }

    @Test void terrainBridgeBindsNormalsAfterOtherMaterialInputs() throws IOException {
        String source = "uniform sampler2D specular;\nuniform sampler2D normals;\n"
                + "uniform sampler2D colortex5;\n"
                + "void main(){gl_FragColor=texture2D(specular,vec2(0.5))+"
                + "texture2D(normals,vec2(0.5))+texture2D(colortex5,vec2(0.5));}\n";
        String terrain = TerrainShaderAdapter.adaptFragment(source, false, true);
        assertTrue(TerrainShaderAdapter.usesNormalSampler(source));
        assertTrue(terrain.contains("layout(binding = 5) uniform sampler2D VeyraSpecularSampler;"));
        assertTrue(terrain.contains("layout(binding = 6) uniform sampler2D VeyraNormalSampler;"));
        assertTrue(terrain.contains("#define normals VeyraNormalSampler"));
        assertTrue(terrain.contains("layout(binding = 7) uniform sampler2D VeyraGeometryColor5Sampler;"));
        assertEquals(7, GeometryColorSamplers.FIRST_IMAGE_SLOT);

        String unused = "uniform sampler2D normals;\nvoid main(){gl_FragColor=vec4(1.0);}\n";
        assertFalse(TerrainShaderAdapter.usesNormalSampler(unused));
        assertFalse(TerrainShaderAdapter.adaptFragment(unused, false).contains("VeyraNormalSampler"));
        assertThrows(IOException.class, () -> TerrainShaderAdapter.adaptFragment(
                source.replace("sampler2D normals", "samplerCube normals"), false));
    }

    @Test void entityBridgeBindsNormalsOnlyWhenShaderUsesThem() throws IOException {
        String source = "uniform sampler2D normals;\nvarying vec2 texcoord;\n"
                + "void main(){gl_FragColor=texture2D(normals,texcoord);}\n";
        String entity = EntityShaderAdapter.adaptFragment(source);
        assertTrue(entity.contains("uniform sampler2D VeyraNormalSampler;"));
        assertTrue(entity.contains("#define normals VeyraNormalSampler"));

        String unused = "uniform sampler2D normals;\nvoid main(){gl_FragColor=vec4(1.0);}\n";
        assertFalse(EntityShaderAdapter.adaptFragment(unused).contains("VeyraNormalSampler"));
    }

    @Test void entityNormalDescriptorUsesFreeVulkanTextureSlot() {
        int samplerType = org.lwjgl.vulkan.VK10.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER;
        var albedo = new ImageDescriptor(0, "sampler2D", "Sampler0", 0, samplerType);
        var normal = new ImageDescriptor(6, "sampler2D", VeyraNormalTextures.SAMPLER, 2, samplerType);
        var lightmap = new ImageDescriptor(7, "sampler2D", "Sampler2", 3, samplerType);

        var descriptors = EntitySamplerBindings.reserveNormalSamplerSlot(java.util.List.of(albedo, normal, lightmap));

        assertEquals(0, descriptors.get(0).imageIdx);
        assertEquals(VeyraNormalTextures.IMAGE_SLOT, descriptors.get(1).imageIdx);
        assertEquals(3, descriptors.get(2).imageIdx);
        assertEquals(6, descriptors.get(1).getBinding());
        assertEquals(VeyraNormalTextures.SAMPLER, descriptors.get(1).name);
        assertInstanceOf(VeyraEntityNormalDescriptor.class, descriptors.get(1));
    }

    @Test void entityNormalDescriptorAvoidsOccupiedReservedSlot() {
        int samplerType = org.lwjgl.vulkan.VK10.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER;
        var albedo = new ImageDescriptor(0, "sampler2D", "Sampler0", 0, samplerType);
        var normal = new ImageDescriptor(6, "sampler2D", VeyraNormalTextures.SAMPLER, 2, samplerType);
        var reserved = new ImageDescriptor(8, "sampler2D", "Sampler6", VeyraNormalTextures.IMAGE_SLOT, samplerType);

        var descriptors = EntitySamplerBindings.reserveNormalSamplerSlot(java.util.List.of(albedo, normal, reserved));

        assertEquals(1, descriptors.get(1).imageIdx);
        assertEquals(VeyraNormalTextures.IMAGE_SLOT, descriptors.get(2).imageIdx);
    }

    @Test void entityNormalDescriptorUsesDirectImageEvenAtReservedSlot() {
        int samplerType = org.lwjgl.vulkan.VK10.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER;
        var normal = new ImageDescriptor(6, "sampler2D", VeyraNormalTextures.SAMPLER,
                VeyraNormalTextures.IMAGE_SLOT, samplerType);

        var descriptors = EntitySamplerBindings.reserveNormalSamplerSlot(java.util.List.of(normal));

        assertInstanceOf(VeyraEntityNormalDescriptor.class, descriptors.get(0));
        assertEquals(VeyraNormalTextures.IMAGE_SLOT, descriptors.get(0).imageIdx);
    }

    private static void assertUnit(int argb) {
        double x = (double) (argb >>> 16 & 255) / 127.5 - 1.0;
        double y = (double) (argb >>> 8 & 255) / 127.5 - 1.0;
        double z = (double) (argb & 255) / 127.5 - 1.0;
        assertEquals(1.0, Math.sqrt(x * x + y * y + z * z), 0.02);
    }
}
