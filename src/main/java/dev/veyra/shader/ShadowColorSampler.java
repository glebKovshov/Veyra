package dev.veyra.shader;

import java.io.IOException;
import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Iris shadow color attachments sampled only after the shadow pass. */
public final class ShadowColorSampler {
    public static final String NAME = "VeyraShadowColorSampler";
    public static final int MAX_INDEX = 7;
    private static final Pattern REFERENCE = Pattern.compile("\\bshadowcolor(\\d*)\\b");
    private ShadowColorSampler() { }

    public static boolean used(String source) {
        return !indicesUnchecked(source).isEmpty();
    }

    public static Set<Integer> indices(String source) throws IOException {
        String active = UnusedSamplerDeclarations.removeUnused(ShaderSource.maskBlockAndLineComments(source));
        Matcher matcher = REFERENCE.matcher(active);
        Set<Integer> indices = new TreeSet<>();
        while (matcher.find()) {
            String suffix = matcher.group(1);
            int index;
            try {
                index = suffix.isEmpty() ? 0 : Integer.parseInt(suffix);
            } catch (NumberFormatException exception) {
                throw new IOException("Invalid shadow color sampler '" + matcher.group() + "'.", exception);
            }
            if (index < 0 || index > MAX_INDEX) {
                throw new IOException("Shadow color samplers must be shadowcolor0..shadowcolor" + MAX_INDEX + ".");
            }
            indices.add(index);
        }
        return Collections.unmodifiableSet(indices);
    }

    private static Set<Integer> indicesUnchecked(String source) {
        String active = UnusedSamplerDeclarations.removeUnused(ShaderSource.maskBlockAndLineComments(source));
        Matcher matcher = REFERENCE.matcher(active);
        Set<Integer> indices = new TreeSet<>();
        while (matcher.find()) {
            try {
                indices.add(matcher.group(1).isEmpty() ? 0 : Integer.parseInt(matcher.group(1)));
            } catch (NumberFormatException ignored) {
                // Validation reports malformed names through indices().
            }
        }
        return indices;
    }

    public static String samplerName(int index) {
        if (index < 0 || index > MAX_INDEX) throw new IllegalArgumentException("Unsupported shadow color index " + index + ".");
        return index == 0 ? NAME : "VeyraShadowColor" + index + "Sampler";
    }

    public static boolean isSampler(String name) {
        return name.equals("shadowcolor") || name.matches("shadowcolor[0-" + MAX_INDEX + "]");
    }

    public static String bridge(Set<Integer> indices) {
        StringBuilder bridge = new StringBuilder();
        for (int index : new TreeSet<>(indices)) {
            String sampler = samplerName(index);
            bridge.append("uniform sampler2D ").append(sampler).append(";\n")
                    .append("#define shadowcolor").append(index).append(' ').append(sampler).append('\n');
            if (index == 0) bridge.append("#define shadowcolor ").append(sampler).append('\n');
        }
        return bridge.toString();
    }

    public static void validate(String source, boolean available, int auxiliaryColors) throws IOException {
        Set<Integer> indices = indices(source);
        if (indices.isEmpty()) return;
        if (!available) throw new IOException("shadowcolor" + indices.iterator().next()
                + " requires a completed shadow pass; shadow attachment feedback is unsupported.");
        int reserved = indices.size() + (ShadowDepthSampler.used(source) ? 1 : 0);
        int availableColors = GeometryColorSamplers.MAX_INPUTS - reserved;
        if (auxiliaryColors > availableColors)
            throw new IOException("Shadow samplers leave at most " + availableColors
                    + " auxiliary color sampler slots.");
    }
}
