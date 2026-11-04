package dev.veyra.shader;

/** Offset-preserving comment masks for GLSL declarations and directives. */
final class ShaderSource {
    private ShaderSource() { }

    static String maskBlockAndLineComments(String source) {
        return mask(source, true, true);
    }

    static String maskBlockComments(String source) {
        return mask(source, true, false);
    }

    static String maskLineComments(String source) {
        return mask(source, false, true);
    }

    private static String mask(String source, boolean blockComments, boolean lineComments) {
        char[] out = source.toCharArray();
        boolean block = false, line = false, quote = false, escape = false;
        for (int i = 0; i < out.length; i++) {
            char c = source.charAt(i), next = i + 1 < out.length ? source.charAt(i + 1) : 0;
            if (c == '\r' || c == '\n') { line = false; quote = false; escape = false; }
            if (block) {
                if (c == '*' && next == '/') {
                    if (blockComments) out[i] = out[i + 1] = ' ';
                    i++;
                    block = false;
                } else if (blockComments && c != '\r' && c != '\n') out[i] = ' ';
            } else if (line) {
                if (lineComments && c != '\r' && c != '\n') out[i] = ' ';
            } else if (quote) {
                if (c == '"' && !escape) quote = false;
                escape = c == '\\' && !escape;
            } else if (c == '"') { quote = true; }
            else if (c == '/' && next == '*') {
                block = true;
                if (blockComments) out[i] = out[i + 1] = ' ';
                i++;
            } else if (c == '/' && next == '/' && lineComments) {
                line = true;
                out[i] = out[i + 1] = ' ';
                i++;
            }
        }
        return new String(out);
    }

    private static final String VALUE_TYPE =
            "(?:float|double|int|uint|bool|[biud]?vec[234]|d?mat[234](?:x[234])?)";

    /**
     * Whether the (comment-free) source declares this identifier itself, as a local, global or parameter.
     * Legacy input names such as {@code tangent} are also common local variable names (Photon builds a
     * tangent frame in a helper); such a declaration is not a use of the stage input.
     */
    // shortcut: this is a declaration-name filter, rely on GLSL compilation for scope validation.
    static boolean declaresVariable(String uncommentedSource, String name) {
        return java.util.regex.Pattern.compile("\\b" + VALUE_TYPE + "\\s+" + java.util.regex.Pattern.quote(name) + "\\b")
                .matcher(uncommentedSource).find();
    }

    /** The first identifier the pattern finds that the source does not declare itself, or null. */
    static String firstUndeclaredUse(java.util.regex.Pattern identifiers, String uncommentedSource) {
        java.util.regex.Matcher matcher = identifiers.matcher(uncommentedSource);
        while (matcher.find()) {
            String name = matcher.group();
            if (!declaresVariable(uncommentedSource, name)) return name;
        }
        return null;
    }
}
