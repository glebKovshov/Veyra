package dev.veyra.shader;

import java.util.regex.Pattern;

/** Maps Iris's unbound post-stage texture aliases to the current full-screen input. */
final class PostSamplerAdapter {
    private static final Pattern PRIMARY_SAMPLER_DECLARATION = Pattern.compile(
            "(?m)^[\\t ]*uniform\\s+sampler2D\\s+(?:colortex0|Sampler0|InSampler)\\s*;\\s*$");

    private PostSamplerAdapter() { }

    static String bindPrimaryTexture(String source) {
        String mapped = source.replaceAll("\\btex\\b", "InSampler");
        return PRIMARY_SAMPLER_DECLARATION.matcher(mapped).replaceAll("");
    }
}
