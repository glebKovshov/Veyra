package dev.veyra.shader;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Removes standalone uniform declarations with no code or macro references. */
final class UnusedUniformDeclarations {
    private static final Pattern DECLARATION = Pattern.compile(
            "(?m)^[\\t ]*(?:layout\\s*\\([^)]*\\)\\s*)?uniform\\s+"
                    + "(?:(?:lowp|mediump|highp)\\s+)?[A-Za-z_][A-Za-z0-9_]*\\s+"
                    + "([A-Za-z_][A-Za-z0-9_]*)\\s*;[^\\r\\n]*(?:\\r?\\n|$)");
    private static final Pattern IDENTIFIER = Pattern.compile("\\b[A-Za-z_][A-Za-z0-9_]*\\b");

    private UnusedUniformDeclarations() {
    }

    static String removeUnused(String source) {
        String uncommented = ShaderSource.maskBlockAndLineComments(source);
        Matcher declarations = DECLARATION.matcher(uncommented);
        List<Declaration> matches = new ArrayList<>();
        while (declarations.find()) {
            matches.add(new Declaration(declarations.start(), declarations.end(), declarations.group(1)));
        }
        if (matches.isEmpty()) return source;

        StringBuilder code = new StringBuilder(uncommented);
        for (Declaration declaration : matches) blank(code, declaration.start(), declaration.end());
        Set<String> usedIdentifiers = new HashSet<>();
        Matcher identifiers = IDENTIFIER.matcher(code);
        while (identifiers.find()) usedIdentifiers.add(identifiers.group());
        List<Declaration> unused = matches.stream()
                .filter(declaration -> !usedIdentifiers.contains(declaration.name()))
                .toList();
        if (unused.isEmpty()) return source;

        StringBuilder result = new StringBuilder(source);
        for (int index = unused.size() - 1; index >= 0; index--) {
            Declaration declaration = unused.get(index);
            blank(result, declaration.start(), declaration.end());
        }
        return result.toString();
    }

    private static void blank(StringBuilder source, int start, int end) {
        for (int index = start; index < end; index++) {
            if (source.charAt(index) != '\r' && source.charAt(index) != '\n') source.setCharAt(index, ' ');
        }
    }

    private record Declaration(int start, int end, String name) {
    }
}
