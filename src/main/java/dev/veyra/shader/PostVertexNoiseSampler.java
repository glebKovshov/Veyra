package dev.veyra.shader;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Iris' shared noise texture binding for post-processing vertex stages. */
final class PostVertexNoiseSampler {
    private static final Pattern DECLARATION = Pattern.compile(
            "(?m)^[\\t ]*uniform\\s+(sampler\\w+)\\s+noisetex\\s*;[^\\r\\n]*(?:\\r?\\n|$)");
    private static final Pattern REFERENCE = Pattern.compile("\\bnoisetex\\b");

    private PostVertexNoiseSampler() { }

    static boolean read(String source, String programName) {
        String active = UnusedSamplerDeclarations.removeUnused(source);
        String uncommented = ShaderSource.maskBlockAndLineComments(active);
        Matcher declaration = DECLARATION.matcher(uncommented);
        while (declaration.find()) {
            if (!declaration.group(1).equals("sampler2D")) {
                throw new IllegalArgumentException(programName + ".vsh noisetex must use sampler2D.");
            }
        }
        return REFERENCE.matcher(uncommented).find();
    }

    static String stripDeclarations(String source) {
        return DECLARATION.matcher(source).replaceAll("");
    }

    static String declaration(boolean used) {
        return used ? "uniform sampler2D VeyraNoiseSampler;\n#define noisetex VeyraNoiseSampler\n" : "";
    }
}
