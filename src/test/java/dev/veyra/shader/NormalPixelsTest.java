package dev.veyra.shader;

import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

class NormalPixelsTest {
    @Test void resolvesAtlasNormalResourceNames() {
        assertEquals("textures/block/stone_n.png", NormalPixels.path("block/stone", true));
        assertEquals("optifine/cit/armor_n.png", NormalPixels.path("optifine/cit/armor", true));
        assertEquals("textures/entity/cow_n.png", NormalPixels.path("entity/cow", true));
        assertEquals("textures/entity/cow_n.png", NormalPixels.path("textures/entity/cow.png", false));
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

    private static void assertUnit(int argb) {
        double x = (double) (argb >>> 16 & 255) / 127.5 - 1.0;
        double y = (double) (argb >>> 8 & 255) / 127.5 - 1.0;
        double z = (double) (argb & 255) / 127.5 - 1.0;
        assertEquals(1.0, Math.sqrt(x * x + y * y + z * z), 0.02);
    }
}