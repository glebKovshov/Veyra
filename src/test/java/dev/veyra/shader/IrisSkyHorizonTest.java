package dev.veyra.shader;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class IrisSkyHorizonTest {
    @Test void closesTheInwardHorizonFanAndCapsItsRadiusLikeIris() {
        for (int distance : new int[]{3, 16, 32}) {
            float[] vertices = IrisSkyHorizon.vertices(distance);
            assertEquals(30, vertices.length);
            assertArrayEquals(new float[]{0, -16, 0}, java.util.Arrays.copyOf(vertices, 3));
            double radius = Math.min(distance * 16, 256);
            for (int vertex = 1; vertex <= 9; vertex++) {
                int offset = vertex * 3;
                assertEquals(16.0F, vertices[offset + 1]);
                assertEquals(radius, Math.hypot(vertices[offset], vertices[offset + 2]), 0.0001);
            }
            assertEquals(vertices[3], vertices[27], 0.0001);
            assertEquals(vertices[5], vertices[29], 0.0001);
            assertTrue(vertices[8] < 0, "The fan must wind toward the camera inside the cone");
        }
    }
}
