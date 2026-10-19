package dev.veyra.shader;

import java.io.IOException;
import java.util.regex.Pattern;

/** Separate depth captured after opaque terrain and entity features, before translucent terrain. */
public final class ShadowDepthSampler {
    public static final String NAME = "VeyraOpaqueShadowSampler";
    public static final int TERRAIN_SLOT = 10;
    private static final Pattern REFERENCE = Pattern.compile("\\bshadowtex1\\b");
    private ShadowDepthSampler() { }
    public static boolean used(String source) {
        return REFERENCE.matcher(UnusedSamplerDeclarations.removeUnused(ShaderSource.maskBlockAndLineComments(source))).find();
    }
    public static void validate(String source, boolean available, int auxiliaryColors) throws IOException {
        if (!used(source)) return;
        if (!available) throw new IOException("shadowtex1 requires a completed shadow pass; shadow depth feedback is unsupported.");
        int availableColors = GeometryColorSamplers.MAX_INPUTS - 1 - (ShadowColorSampler.used(source) ? 1 : 0);
        if (auxiliaryColors > availableColors) throw new IOException("shadowtex1 exceeds the available auxiliary water sampler slots.");
    }
}
