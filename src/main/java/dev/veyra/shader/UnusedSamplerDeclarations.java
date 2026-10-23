package dev.veyra.shader;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Removes sampler declarations that have no executable references in an adapted shader stage. */
final class UnusedSamplerDeclarations {
    private static final Pattern IDENTIFIER = Pattern.compile("\\b[A-Za-z_][A-Za-z0-9_]*\\b");

    private UnusedSamplerDeclarations() {
    }

    static String removeUnused(String source) {
        if (source.isEmpty() || source.indexOf("uniform") < 0 || source.indexOf("sampler") < 0) return source;
        String uncommented = ShaderSource.maskBlockAndLineComments(source);
        List<Declaration> matches = findDeclarations(uncommented);
        if (matches.isEmpty()) return source;

        // Remove every declaration before looking for references so duplicate declarations do not
        // make one another appear live. Keep all code references and preprocessor macro references.
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
        for (int i = unused.size() - 1; i >= 0; i--) {
            Declaration declaration = unused.get(i);
            blank(result, declaration.start(), declaration.end());
        }
        return result.toString();
    }

    static boolean isReferenced(String source, String identifier) {
        if (source.isEmpty() || source.indexOf(identifier) < 0) return false;
        String uncommented = ShaderSource.maskBlockAndLineComments(source);
        StringBuilder code = new StringBuilder(uncommented);
        for (Declaration declaration : findDeclarations(uncommented)) {
            blank(code, declaration.start(), declaration.end());
        }
        return Pattern.compile("\\b" + Pattern.quote(identifier) + "\\b").matcher(code).find();
    }

    /** Parses sampler declarations linearly; a multiline layout or precision clause is allowed. */
    private static List<Declaration> findDeclarations(String source) {
        List<Declaration> declarations = new ArrayList<>();
        int lineStart = 0;
        while (lineStart < source.length()) {
            ParsedDeclaration parsed = parseDeclaration(source, lineStart);
            if (parsed == null) {
                lineStart = nextLineStart(source, lineStart);
                continue;
            }
            int end = nextLineStart(source, parsed.semicolon() + 1);
            declarations.add(new Declaration(lineStart, end, parsed.name()));
            lineStart = end;
        }
        return declarations;
    }

    private static ParsedDeclaration parseDeclaration(String source, int start) {
        int cursor = skipLineLeadingWhitespace(source, start);
        if (cursor >= source.length() || source.charAt(cursor) == '\r' || source.charAt(cursor) == '\n') return null;
        if (matchesToken(source, cursor, "layout")) {
            cursor = skipWhitespace(source, cursor + "layout".length());
            if (cursor >= source.length() || source.charAt(cursor) != '(') return null;
            int close = source.indexOf(')', cursor + 1);
            if (close < 0) return null;
            cursor = skipWhitespace(source, close + 1);
        }
        if (!matchesToken(source, cursor, "uniform")) return null;
        cursor = skipWhitespace(source, cursor + "uniform".length());
        for (String precision : new String[] {"lowp", "mediump", "highp"}) {
            if (matchesToken(source, cursor, precision)) {
                cursor = skipWhitespace(source, cursor + precision.length());
                break;
            }
        }
        if (!source.startsWith("sampler", cursor)) return null;
        int typeEnd = cursor + "sampler".length();
        while (typeEnd < source.length() && isIdentifierPart(source.charAt(typeEnd))) typeEnd++;
        if (typeEnd == cursor + "sampler".length()) return null;
        cursor = skipWhitespace(source, typeEnd);
        int nameStart = cursor;
        if (cursor >= source.length() || !isIdentifierStart(source.charAt(cursor))) return null;
        cursor++;
        while (cursor < source.length() && isIdentifierPart(source.charAt(cursor))) cursor++;
        String name = source.substring(nameStart, cursor);
        cursor = skipWhitespace(source, cursor);
        return cursor < source.length() && source.charAt(cursor) == ';'
                ? new ParsedDeclaration(name, cursor) : null;
    }

    private static int skipWhitespace(String source, int cursor) {
        while (cursor < source.length() && Character.isWhitespace(source.charAt(cursor))) cursor++;
        return cursor;
    }

    private static int skipLineLeadingWhitespace(String source, int cursor) {
        while (cursor < source.length() && source.charAt(cursor) != '\r' && source.charAt(cursor) != '\n'
                && Character.isWhitespace(source.charAt(cursor))) cursor++;
        return cursor;
    }

    private static boolean matchesToken(String source, int cursor, String token) {
        if (!source.startsWith(token, cursor)) return false;
        int end = cursor + token.length();
        return end == source.length() || !isIdentifierPart(source.charAt(end));
    }

    private static boolean isIdentifierStart(char character) {
        return character == '_' || Character.isLetter(character);
    }

    private static boolean isIdentifierPart(char character) {
        return character == '_' || Character.isLetterOrDigit(character);
    }

    private static int nextLineStart(String source, int cursor) {
        while (cursor < source.length() && source.charAt(cursor) != '\r' && source.charAt(cursor) != '\n') cursor++;
        if (cursor < source.length() && source.charAt(cursor) == '\r') cursor++;
        if (cursor < source.length() && source.charAt(cursor) == '\n') cursor++;
        return cursor;
    }

    private static void blank(StringBuilder source, int start, int end) {
        for (int i = start; i < end; i++) {
            if (source.charAt(i) != '\r' && source.charAt(i) != '\n') source.setCharAt(i, ' ');
        }
    }

    private record Declaration(int start, int end, String name) {
    }

    private record ParsedDeclaration(String name, int semicolon) {
    }
}
