package dev.veyra.shader;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Removes fragment inputs with no active references before the vertex interface is linked. */
final class UnusedVaryingDeclarations {
    private static final Pattern IDENTIFIER = Pattern.compile("\\b[A-Za-z_][A-Za-z0-9_]*\\b");
    private static final String[] INTERPOLATION_QUALIFIERS = {
            "flat", "smooth", "noperspective", "centroid", "sample", "invariant"
    };

    private UnusedVaryingDeclarations() {
    }

    static String removeUnused(String source) {
        String uncommented = ShaderSource.maskBlockAndLineComments(source);
        List<Declaration> declarations = findDeclarations(uncommented);
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
        if (matchesToken(source, cursor, "layout")) {
            cursor = skipWhitespace(source, cursor + "layout".length());
            if (cursor >= source.length() || source.charAt(cursor) != '(') return null;
            int close = source.indexOf(')', cursor + 1);
            if (close < 0) return null;
            cursor = skipWhitespace(source, close + 1);
        }
        for (String qualifier : INTERPOLATION_QUALIFIERS) {
            if (matchesToken(source, cursor, qualifier)) {
                cursor = skipWhitespace(source, cursor + qualifier.length());
                // GLSL permits more than one interpolation qualifier before the storage token.
                while (true) {
                    int nextQualifierEnd = interpolationQualifierEnd(source, cursor);
                    if (nextQualifierEnd < 0) break;
                    cursor = skipWhitespace(source, nextQualifierEnd);
                }
                break;
            }
        }
        if (matchesToken(source, cursor, "varying")) cursor = skipWhitespace(source, cursor + "varying".length());
        else if (matchesToken(source, cursor, "in")) cursor = skipWhitespace(source, cursor + "in".length());
        else return null;

        int typeEnd = identifierEnd(source, cursor);
        if (typeEnd == cursor) return null;
        int nameStart = skipWhitespace(source, typeEnd);
        if (nameStart == typeEnd) return null;
        int nameEnd = identifierEnd(source, nameStart);
        if (nameEnd == nameStart) return null;
        cursor = skipWhitespace(source, nameEnd);
        return cursor < source.length() && source.charAt(cursor) == ';'
                ? new ParsedDeclaration(source.substring(nameStart, nameEnd), cursor) : null;
    }

    private static int interpolationQualifierEnd(String source, int cursor) {
        for (String qualifier : INTERPOLATION_QUALIFIERS) {
            if (matchesToken(source, cursor, qualifier)) return cursor + qualifier.length();
        }
        return -1;
    }

    private static int skipLineLeadingWhitespace(String source, int cursor) {
        while (cursor < source.length() && source.charAt(cursor) != '\r' && source.charAt(cursor) != '\n'
                && Character.isWhitespace(source.charAt(cursor))) cursor++;
        return cursor;
    }

    private static int skipWhitespace(String source, int cursor) {
        while (cursor < source.length() && Character.isWhitespace(source.charAt(cursor))) cursor++;
        return cursor;
    }

    private static int identifierEnd(String source, int cursor) {
        if (cursor >= source.length() || !isIdentifierStart(source.charAt(cursor))) return cursor;
        cursor++;
        while (cursor < source.length() && isIdentifierPart(source.charAt(cursor))) cursor++;
        return cursor;
    }

    private static boolean matchesToken(String source, int cursor, String token) {
        if (cursor < 0 || !source.startsWith(token, cursor)) return false;
        int end = cursor + token.length();
        return end == source.length() || !isIdentifierPart(source.charAt(end));
    }

    private static boolean isIdentifierStart(char character) {
        return character == '_' || character >= 'A' && character <= 'Z' || character >= 'a' && character <= 'z';
    }

    private static boolean isIdentifierPart(char character) {
        return isIdentifierStart(character) || character >= '0' && character <= '9';
    }

    private static int nextLineStart(String source, int cursor) {
        while (cursor < source.length() && source.charAt(cursor) != '\r' && source.charAt(cursor) != '\n') cursor++;
        if (cursor < source.length() && source.charAt(cursor) == '\r') cursor++;
        if (cursor < source.length() && source.charAt(cursor) == '\n') cursor++;
        return cursor;
    }

    private static void blank(StringBuilder source, int start, int end) {
        for (int index = start; index < end; index++) {
            if (source.charAt(index) != '\r' && source.charAt(index) != '\n') source.setCharAt(index, ' ');
        }
    }

    private record Declaration(int start, int end, String name) {
    }

    private record ParsedDeclaration(String name, int semicolon) { }
}
