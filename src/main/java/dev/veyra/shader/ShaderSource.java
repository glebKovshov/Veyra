package dev.veyra.shader;

/** Offset-preserving comment masks for GLSL declarations and directives. */
final class ShaderSource {
    private ShaderSource() { }

    static String maskBlockAndLineComments(String source) {
        return mask(source, true);
    }

    static String maskBlockComments(String source) {
        return mask(source, false);
    }

    private static String mask(String source, boolean lineComments) {
        char[] out = source.toCharArray();
        boolean block = false, line = false, quote = false, escape = false;
        for (int i = 0; i < out.length; i++) {
            char c = source.charAt(i), next = i + 1 < out.length ? source.charAt(i + 1) : 0;
            if (c == '\r' || c == '\n') { line = false; quote = false; escape = false; }
            if (block) {
                if (c == '*' && next == '/') { out[i] = out[++i] = ' '; block = false; }
                else if (c != '\r' && c != '\n') out[i] = ' ';
            } else if (line) {
                if (c != '\r' && c != '\n') out[i] = ' ';
            } else if (quote) {
                if (c == '"' && !escape) quote = false;
                escape = c == '\\' && !escape;
            } else if (c == '"') { quote = true; }
            else if (c == '/' && (next == '*' || (lineComments && next == '/'))) {
                block = next == '*'; line = next == '/'; out[i] = out[++i] = ' ';
            }
        }
        return new String(out);
    }
}
