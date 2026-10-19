package dev.veyra.shader;

import java.io.IOException;
import java.util.regex.Pattern;

/** The existing single shadow color attachment, sampled only after the shadow pass. */
public final class ShadowColorSampler {
    public static final String NAME = "VeyraShadowColorSampler";
    public static final int TERRAIN_SLOT = 11;
    private static final Pattern REFERENCE = Pattern.compile("\\bshadowcolor(?:0)?\\b");
    private ShadowColorSampler() { }
    public static boolean used(String source) {
        return REFERENCE.matcher(UnusedSamplerDeclarations.removeUnused(
                ShaderSource.maskBlockAndLineComments(source))).find();
    }
    public static void validate(String source, boolean available, int auxiliaryColors) throws IOException {
        if (!used(source)) return;
        if (!available) throw new IOException("shadowcolor0 requires a completed shadow pass; shadow attachment feedback is unsupported.");
        if (auxiliaryColors > TERRAIN_SLOT - GeometryColorSamplers.FIRST_IMAGE_SLOT)
            throw new IOException("shadowcolor0 leaves at most five auxiliary water color sampler slots.");
    }
}
