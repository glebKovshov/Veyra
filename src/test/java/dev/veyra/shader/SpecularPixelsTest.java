package dev.veyra.shader;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import static org.junit.jupiter.api.Assertions.*;

class SpecularPixelsTest {
    @Test void findsAtlasAndStandaloneResourcePaths() {
        assertEquals("textures/block/stone_s.png", SpecularPixels.path("block/stone", true));
        assertEquals("optifine/cit/armor_s.png", SpecularPixels.path("optifine/cit/armor", true));
        assertEquals("textures/entity/cow/temperate_cow_s.png", SpecularPixels.path("textures/entity/cow/temperate_cow.png", false));
    }
    @Test void padsTheExistingAtlasRectangleWithoutPremultiplyingChannels() {
        var atlas = new SpecularPixels.Image(6, 6, new int[36]);
        var source = new SpecularPixels.Image(2, 2, new int[]{0x00ff0000, 0x0000ff00, 0x000000ff, 0x80010203});
        SpecularPixels.tile(atlas, source, 1, 1, 2, 2, 1);
        assertEquals(0x00ff0000, atlas.pixels()[1 * 6 + 1]);
        assertEquals(0x00ff0000, atlas.pixels()[2 * 6 + 2]);
        assertEquals(0x80010203, atlas.pixels()[4 * 6 + 4]);
        assertEquals(0, atlas.pixels()[0]);
        assertThrows(IllegalArgumentException.class, () -> SpecularPixels.tile(atlas, source, 5, 5, 2, 2, 0));
    }
    @Test void resamplesDataWithNearestIntegerUpscalingAndBilinearOtherwise() {
        var source = new SpecularPixels.Image(2, 1, new int[]{0, 0x00ff0000});
        var integer = new SpecularPixels.Image(4, 2, new int[8]);
        SpecularPixels.tile(integer, source, 0, 0, 4, 2, 0);
        assertArrayEquals(new int[]{0, 0, 0x00ff0000, 0x00ff0000, 0, 0, 0x00ff0000, 0x00ff0000}, integer.pixels());
        var fractional = new SpecularPixels.Image(3, 1, new int[3]);
        SpecularPixels.tile(fractional, source, 0, 0, 3, 1, 0);
        assertEquals(0x00800000, fractional.pixels()[1]);
    }
    @Test void preservesLabPbrMaterialCategoriesDuringMipmapGeneration() {
        var source = new SpecularPixels.Image(2, 2, new int[]{0xff00e60a, 0xc864e614, 0xbec86464, 0xb4ff646e});
        assertEquals(0xbe8ae60f, SpecularPixels.mip(source, true).pixels()[0]);
        assertEquals(0xce8aa53c, SpecularPixels.mip(source, false).pixels()[0]);
    }
    @Test void specularIsBoundOnlyWhenItIsUsedAndKeepsContiguousBindings() throws IOException {
        String source = "uniform sampler2D specular;\nvarying vec2 texcoord;\nvoid main(){gl_FragColor=texture2D(specular,texcoord);}\n";
        String terrain = TerrainShaderAdapter.adaptFragment(source, false);
        assertTrue(terrain.contains("layout(binding = 5) uniform sampler2D VeyraSpecularSampler;"));
        assertTrue(EntityShaderAdapter.adaptFragment(source).contains("#define specular VeyraSpecularSampler"));
        String unused = "uniform sampler2D specular;\nvoid main(){gl_FragColor=vec4(1.0);}\n";
        assertFalse(TerrainShaderAdapter.usesSpecularSampler(unused));
        assertFalse(TerrainShaderAdapter.adaptFragment(unused).contains("VeyraSpecularSampler"));
        assertThrows(IOException.class, () -> TerrainShaderAdapter.adaptFragment(source.replace("sampler2D specular", "samplerCube specular")));
    }
    @Test void appendsAuxiliaryColorsAfterMaterialInput() throws IOException {
        String source = "uniform sampler2D specular;\nuniform sampler2D colortex5;\nvoid main(){gl_FragColor=texture2D(specular,vec2(0.5))+texture2D(colortex5,vec2(0.5));}\n";
        String terrain = TerrainShaderAdapter.adaptFragment(source, false, true);
        assertTrue(terrain.contains("layout(binding = 5) uniform sampler2D VeyraSpecularSampler;"));
        assertTrue(terrain.contains("layout(binding = 6) uniform sampler2D VeyraGeometryColor5Sampler;"));
    }
}
