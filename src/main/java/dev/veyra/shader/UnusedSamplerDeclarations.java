package dev.veyra.shader;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Removes sampler declarations that have no executable references in an adapted shader stage. */
final class UnusedSamplerDeclarations {
    private static final Pattern DECLARATION = Pattern.compile(
            "(?m)^[\\t ]*(?:layout\\s*\\([^)]*\\)\\s*)?uniform\\s+(?:(?:lowp|mediump|highp)\\s+)?sampler\\w+\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*;[^\\r\\n]*(?:\\r?\\n|$)");

    private UnusedSamplerDeclarations() {
    }

    static String removeUnused(String source) {
        String uncommented = ShaderSource.maskBlockAndLineComments(source);
        Matcher declarations = DECLARATION.matcher(uncommented);
        List<Declaration> matches = new ArrayList<>();
        while (declarations.find()) {
            matches.add(new Declaration(declarations.start(), declarations.end(), declarations.group(1)));
        }
        if (matches.isEmpty()) return source;

        // Remove every declaration before looking for references so duplicate declarations do not
        // make one another appear live. Keep all code references and preprocessor macro references.
        StringBuilder code = new StringBuilder(uncommented);
        for (Declaration declaration : matches) blank(code, declaration.start(), declaration.end());
        List<Declaration> unused = matches.stream().filter(declaration ->
                !Pattern.compile("\\b" + Pattern.quote(declaration.name()) + "\\b")
                        .matcher(code).find()).toList();
        if (unused.isEmpty()) return source;

        StringBuilder result = new StringBuilder(source);
        for (int i = unused.size() - 1; i >= 0; i--) {
            Declaration declaration = unused.get(i);
            blank(result, declaration.start(), declaration.end());
        }
        return result.toString();
    }

    private static void blank(StringBuilder source, int start, int end) {
        for (int i = start; i < end; i++) {
            if (source.charAt(i) != '\r' && source.charAt(i) != '\n') source.setCharAt(i, ' ');
        }
    }

    private record Declaration(int start, int end, String name) {
    }
}
