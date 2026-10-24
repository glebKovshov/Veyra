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
}
