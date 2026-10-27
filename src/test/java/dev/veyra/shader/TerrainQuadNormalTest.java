package dev.veyra.shader;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TerrainQuadNormalTest {
    @Test void preservesFluidFaceWinding() {
        float[] x = {0, 0, 1, 1};
        float[] y = {0, 0, 0, 0};
        float[] z = {0, 1, 1, 0};
        assertEquals(127 << 8, TerrainMidTexCoord.packedQuadNormal(x, y, z));
        assertEquals(129 << 8, TerrainMidTexCoord.packedQuadNormal(z, y, x));
    }

    @Test void normalizesSlopedFluidFacesBeforePacking() {
        int normal = TerrainMidTexCoord.packedQuadNormal(
                new float[]{0, 0, 1, 1}, new float[]{0, 0, 1, 1}, new float[]{0, 1, 1, 0});
        assertEquals(-90, (byte) normal);
        assertEquals(90, (byte) (normal >>> 8));
        assertEquals(0, (byte) (normal >>> 16));
    }

    @Test void givesDegenerateQuadsAFiniteFallback() {
        assertEquals(127 << 16, TerrainMidTexCoord.packedQuadNormal(
                new float[4], new float[4], new float[4]));
    }
}
