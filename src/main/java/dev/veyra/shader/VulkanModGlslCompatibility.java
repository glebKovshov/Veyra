package dev.veyra.shader;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Applies semantics-preserving GLSL spelling fixes for VulkanMod 0.6.8's parser. */
final class VulkanModGlslCompatibility {
    private static final Pattern EXPLICIT_INPUT_PARAMETER = Pattern.compile(
            "(?<=[(,])\\s*\\bin\\b(?=\\s+[A-Za-z_][A-Za-z_0-9]*\\s+[A-Za-z_][A-Za-z_0-9]*)");

    private VulkanModGlslCompatibility() {
    }

    /**
     * VulkanMod 0.6.8 reads an explicit {@code in} function-parameter qualifier as a stage input declaration.
     * GLSL's default function-parameter qualifier is {@code in}, so removing it preserves shader behavior.
     * Keep actual shader-stage {@code in} declarations unchanged.
     */
    static String stripExplicitInputParameterQualifiers(String source) {
        String uncommented = ShaderSource.maskBlockAndLineComments(source);
        Matcher matcher = EXPLICIT_INPUT_PARAMETER.matcher(uncommented);
        if (!matcher.find()) {
            return source;
        }

        char[] adapted = source.toCharArray();
        do {
            int qualifier = uncommented.indexOf("in", matcher.start());
            adapted[qualifier] = ' ';
            adapted[qualifier + 1] = ' ';
        } while (matcher.find());
        return new String(adapted);
    }
}
