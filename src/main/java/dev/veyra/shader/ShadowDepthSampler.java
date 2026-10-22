package dev.veyra.shader;

import java.io.IOException;

/** Separate depth captured after opaque terrain and entity features, before translucent terrain. */
public final class ShadowDepthSampler {
    public static final String NAME = "VeyraOpaqueShadowSampler";
    private ShadowDepthSampler() { }
    public static boolean used(String source) {
        return UnusedSamplerDeclarations.isReferenced(source, "shadowtex1");
    }
    public static void validate(String source, boolean available, int auxiliaryColors) throws IOException {
        if (!used(source)) return;
        if (!available) throw new IOException("shadowtex1 requires a completed shadow pass; shadow depth feedback is unsupported.");
        int availableColors = GeometryColorSamplers.MAX_INPUTS - 1 - ShadowColorSampler.indices(source).size();
        if (auxiliaryColors > availableColors) throw new IOException("shadowtex1 exceeds the available auxiliary water sampler slots.");
    }
}
