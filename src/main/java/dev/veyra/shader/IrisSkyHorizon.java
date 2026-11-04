package dev.veyra.shader;

/** Camera-relative horizon cone used by Iris in addition to vanilla's upper sky disc. */
public final class IrisSkyHorizon {
    private IrisSkyHorizon() { }

    public static float[] vertices(int renderDistanceChunks) {
        float radius = Math.min(renderDistanceChunks * 16.0F, 256.0F);
        float[] vertices = new float[30];
        vertices[1] = -16.0F;
        for (int i = 0; i <= 8; i++) {
            double angle = -i * Math.PI / 4.0;
            int offset = (i + 1) * 3;
            vertices[offset] = (float) (radius * Math.cos(angle));
            vertices[offset + 1] = 16.0F;
            vertices[offset + 2] = (float) (radius * Math.sin(angle));
        }
        return vertices;
    }
}
