package dev.veyra.shader;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Removes fragment inputs with no active references before the vertex interface is linked. */
final class UnusedVaryingDeclarations {
    private static final Pattern DECLARATION = Pattern.compile(
            "(?m)^[\\t ]*(?:layout\\s*\\([^)]*\\)\\s*)?"
                    + "(?:(?:flat|smooth|noperspective|centroid|sample|invariant)\\s+)*"
                    + "(?:varying|in)\\s+[A-Za-z_][A-Za-z0-9_]*\\s+"
                    + "([A-Za-z_][A-Za-z0-9_]*)\\s*;[\\t ]*(?://[^\\r\\n]*)?(?:\\r?\\n|$)");
    private static final Pattern IDENTIFIER = Pattern.compile("\\b[A-Za-z_][A-Za-z0-9_]*\\b");

    private UnusedVaryingDeclarations() {
    }

    static String removeUnused(String source) {
        String uncommented = ShaderSource.maskBlockAndLineComments(source);
        Matcher matcher = DECLARATION.matcher(uncommented);
        List<Declaration> declarations = new ArrayList<>();
        while (matcher.find()) {
            declarations.add(new Declaration(matcher.start(), matcher.end(), matcher.group(1)));
        }
        if (declarations.isEmpty()) return source;

        StringBuilder code = new StringBuilder(uncommented);
        for (Declaration declaration : declarations) blank(code, declaration.start(), declaration.end());
        Set<String> usedIdentifiers = new HashSet<>();
        Matcher identifiers = IDENTIFIER.matcher(code);
        while (identifiers.find()) usedIdentifiers.add(identifiers.group());
        List<Declaration> unused = declarations.stream()
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
