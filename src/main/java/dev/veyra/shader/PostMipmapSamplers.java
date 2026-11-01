package dev.veyra.shader;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

/** Mipmap requests from a preprocessed post fragment program. */
final class PostMipmapSamplers {
    private static final Pattern REQUEST = Pattern.compile(
            "\\bconst\\s+bool\\s+colortex([0-9]|1[0-5])MipmapEnabled\\s*=\\s*true\\s*;");

    private PostMipmapSamplers() { }

    static Set<Integer> read(String source) {
        var matcher = REQUEST.matcher(ShaderSource.maskBlockAndLineComments(source));
        Set<Integer> indices = new HashSet<>();
        while (matcher.find()) indices.add(Integer.parseInt(matcher.group(1)));
        return Set.copyOf(indices);
    }
}
