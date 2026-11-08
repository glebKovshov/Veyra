package dev.veyra.shader;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Rewrites {@code in}/{@code out} qualifiers of function parameters. VulkanMod 0.6.8's GLSL parser treats every
 * {@code in}/{@code out} token as a stage interface declaration, wherever it appears, so a parameter such as
 * Photon's {@code out vec3 bent_normal} silently lost its out semantics and returned nothing (its ambient occlusion
 * bent normals became zero, then noise through temporal accumulation). {@code in} is the default parameter
 * qualifier, and {@code inout} behaves like {@code out} for code that writes the parameter before reading it.
 */
final class FunctionParameterQualifiers {
    /** A return type (optionally with a precision qualifier) and function name directly before '('. */
    private static final Pattern FUNCTION_HEAD = Pattern.compile(
            "\\b[A-Za-z_][A-Za-z0-9_]*(?:\\s*\\[\\s*\\d*\\s*])?\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*\\(");
    private static final Pattern OUT = Pattern.compile("\\bout\\b");
    private static final Pattern IN = Pattern.compile("\\bin\\s+(?=[A-Za-z_])");

    private FunctionParameterQualifiers() {
    }

    static String normalize(String source) {
        String masked = ShaderSource.maskBlockAndLineComments(source);
        if (!OUT.matcher(masked).find() && !masked.contains("in ")) return source;
        int[] depth = braceDepths(masked);
        StringBuilder result = new StringBuilder(source.length());
        int cursor = 0;
        Matcher head = FUNCTION_HEAD.matcher(masked);
        while (head.find()) {
            int open = head.end() - 1;
            if (depth[open] != 0 || isKeyword(head.group(1))) continue;
            int close = matchingParen(masked, open);
            if (close < 0) break;
            String parameters = masked.substring(open + 1, close);
            if (!OUT.matcher(parameters).find() && !IN.matcher(parameters).find()) continue;
            result.append(source, cursor, open + 1);
            String original = source.substring(open + 1, close);
            String rewritten = rewrite(original, parameters);
            result.append(rewritten);
            cursor = close;
            head.region(close, masked.length());
        }
        if (cursor == 0) return source;
        return result.append(source, cursor, source.length()).toString();
    }

    private static String rewrite(String original, String masked) {
        StringBuilder out = new StringBuilder(original.length() + 8);
        int cursor = 0;
        Matcher token = Pattern.compile("\\b(?:out|in)\\b").matcher(masked);
        while (token.find()) {
            out.append(original, cursor, token.start());
            if (token.group().equals("out")) {
                out.append("inout");
                cursor = token.end();
            } else {
                // Drop "in" and the whitespace after it; the default qualifier is already "in".
                int end = token.end();
                while (end < masked.length() && Character.isWhitespace(masked.charAt(end))) end++;
                cursor = end;
            }
        }
        return out.append(original, cursor, original.length()).toString();
    }

    private static boolean isKeyword(String name) {
        return switch (name) {
            case "if", "for", "while", "switch", "return", "layout" -> true;
            default -> false;
        };
    }

    private static int[] braceDepths(String masked) {
        int[] depths = new int[masked.length() + 1];
        int depth = 0;
        for (int i = 0; i < masked.length(); i++) {
            depths[i] = depth;
            char c = masked.charAt(i);
            if (c == '{') depth++;
            else if (c == '}') depth = Math.max(0, depth - 1);
        }
        depths[masked.length()] = depth;
        return depths;
    }

    private static int matchingParen(String masked, int open) {
        int depth = 0;
        for (int i = open; i < masked.length(); i++) {
            char c = masked.charAt(i);
            if (c == '(') depth++;
            else if (c == ')' && --depth == 0) return i;
            else if (c == ';' || c == '{') return -1;
        }
        return -1;
    }
}
