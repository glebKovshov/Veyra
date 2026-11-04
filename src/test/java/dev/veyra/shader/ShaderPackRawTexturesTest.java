package dev.veyra.shader;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static org.junit.jupiter.api.Assertions.*;

class ShaderPackRawTexturesTest {
    private static final String PROPERTIES = """
            texture.noise = image/noise.png
            texture.deferred.depthtex0 = image/atmosphere/scattering.dat TEXTURE_3D RGB16F 32 64 32 RGB HALF_FLOAT
            texture.deferred.colortex6.1 = image/worley_bubbly.dat TEXTURE_3D R8 64 64 64 RED UNSIGNED_BYTE
            texture.deferred.colortex13 = image/galaxy.png
            """;

    @Test
    void parsesIrisRawTextureDeclarationsAndWidensRgbTexels() throws IOException {
        assertNull(RawTextureSpec.parse("image/galaxy.png"));
        RawTextureSpec lut = RawTextureSpec.parse(
                "image/atmosphere/scattering.dat TEXTURE_3D RGB16F 32 64 32 RGB HALF_FLOAT");
        assertEquals(3, lut.dimensions());
        assertEquals(32 * 64 * 32 * 3 * 2, lut.sourceBytes());
        assertEquals(97, lut.vulkanFormat()); // VK_FORMAT_R16G16B16A16_SFLOAT
        assertEquals("sampler3D", lut.samplerType());

        byte[] texel = {1, 2, 3, 4, 5, 6};
        RawTextureSpec single = RawTextureSpec.parse("a.dat TEXTURE_2D RGB16F 1 1 RGB HALF_FLOAT");
        ByteBuffer stored = single.storedTexels(texel).order(ByteOrder.LITTLE_ENDIAN);
        assertEquals(8, stored.remaining());
        assertEquals((short) 0x3C00, stored.getShort(6));

        assertEquals(9, RawTextureSpec.parse("n.dat TEXTURE_3D R8 2 2 2 RED UNSIGNED_BYTE").vulkanFormat());
        assertThrows(IOException.class, () -> single.storedTexels(new byte[5]));
        assertThrows(IOException.class, () -> RawTextureSpec.parse("a.dat TEXTURE_1D R8 4 RED UNSIGNED_BYTE"));
    }

    @Test
    void replacesOnlySamplersDeclaredWithAMatchingDimension() throws IOException {
        var properties = ShaderPackCustomTextureProperties.parse(PROPERTIES);
        String clouds = """
                #version 400
                uniform sampler3D colortex6; // bubbly noise
                #define SAMPLER_WORLEY colortex6
                uniform sampler3D depthtex0;
                uniform sampler2D colortex6Format;
                void main() { gl_FragColor = texture(colortex6, vec3(0.0)) + texture(depthtex0, vec3(0.5)); }
                """;
        var prepared = ShaderPackRawTextures.prepare("deferred1", clouds, properties);
        assertEquals(2, prepared.bindings().size());
        assertFalse(prepared.source().contains("uniform sampler3D"), prepared.source());
        assertTrue(prepared.source().contains("#define SAMPLER_WORLEY veyraRawTexture_colortex6"));
        assertTrue(prepared.source().contains("texture(veyraRawTexture_depthtex0"));
        assertTrue(prepared.source().contains("colortex6Format"));

        String shading = """
                #version 400
                uniform sampler2D colortex6; // ambient lighting data
                void main() { gl_FragColor = texelFetch(colortex6, ivec2(0), 0); }
                """;
        var unchanged = ShaderPackRawTextures.prepare("deferred4", shading, properties);
        assertTrue(unchanged.bindings().isEmpty());
        assertEquals(shading, unchanged.source());

        String decorated = ShaderPackRawTextures.decorate(prepared.source(), prepared.bindings());
        assertTrue(decorated.contains("#define sampler2D sampler3D\nuniform sampler2D veyraRawTexture_colortex6;\n#undef sampler2D"),
                decorated);
        assertTrue(ShaderPackRawTextures.prepare("composite", clouds, properties).bindings().isEmpty());
    }
}
