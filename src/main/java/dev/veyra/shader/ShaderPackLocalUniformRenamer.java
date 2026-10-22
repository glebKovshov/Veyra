package dev.veyra.shader;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Renames function-local variables that would be captured by shader-pack uniform alias macros. */
final class ShaderPackLocalUniformRenamer {
    private static final Pattern FUNCTION_DEFINITION = Pattern.compile(
            "\\b(?:(?:const|highp|mediump|lowp|precise|invariant)\\s+)*"
                    + "([A-Za-z_][A-Za-z0-9_]*)\\s+([A-Za-z_][A-Za-z0-9_]*)"
                    + "\\s*\\(([^;{}]*)\\)\\s*\\{");
    private static final Pattern LOCAL_DECLARATION_PREFIX = Pattern.compile(
            "\\b(?:(?:const|highp|mediump|lowp|precise|invariant|readonly|writeonly|coherent|volatile|restrict)\\s++)*+"
                    + "(?!(?:return|if|else|for|while|switch|case|discard|break|continue|do)\\b)"
                    + "[A-Za-z_][A-Za-z0-9_]*+\\s++");
    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");
    private static final ConcurrentMap<String, Pattern> LOCAL_DECLARATIONS = new ConcurrentHashMap<>();

    private ShaderPackLocalUniformRenamer() {
    }

    static String renameShadowingLocals(String source, List<String> uniforms) {
        String result = source;
        for (String uniform : uniforms) {
            if (!containsIdentifier(result, uniform)) continue;
            Pattern declaration = localDeclaration(uniform);
            // Most shader-pack uniforms do not shadow a local in a given pass. Check the raw
            // source first so those names avoid allocating and scanning a full comment mask.
            if (!declaration.matcher(result).find()) continue;
            result = renameShadowingLocal(result, uniform, declaration);
            result = renameShadowingGlobal(result, uniform, declaration);
        }
        return result;
    }

    private static String renameShadowingGlobal(String source, String uniform, Pattern declaration) {
        if (source.isEmpty()) return source;
        String analysis = ShaderSource.maskBlockAndLineComments(source);
        Matcher declarations = declaration.matcher(analysis);
        int braceDepth = 0;
        int parenthesisDepth = 0;
        int cursor = 0;
        boolean declaredGlobally = false;
        while (declarations.find()) {
            int nameStart = declarations.start("name");
            while (cursor < nameStart) {
                char character = analysis.charAt(cursor++);
                if (character == '{') braceDepth++;
                else if (character == '}') braceDepth--;
                else if (character == '(') parenthesisDepth++;
                else if (character == ')') parenthesisDepth--;
            }
            if (braceDepth == 0 && parenthesisDepth == 0) {
                declaredGlobally = true;
                break;
            }
        }
        if (!declaredGlobally) return source;

        String alias = uniqueAlias(analysis, uniform);
        Matcher identifiers = Pattern.compile("\\b" + Pattern.quote(uniform) + "\\b").matcher(analysis);
        List<Replacement> replacements = new ArrayList<>();
        while (identifiers.find()) {
            if (!isMemberName(analysis, identifiers.start())) {
                replacements.add(new Replacement(identifiers.start(), identifiers.end(), alias));
            }
        }
        if (replacements.isEmpty()) return source;
        replacements.sort((left, right) -> Integer.compare(right.start(), left.start()));
        StringBuilder rewritten = new StringBuilder(source);
        for (Replacement replacement : replacements) {
            rewritten.replace(replacement.start(), replacement.end(), replacement.alias());
        }
        return rewritten.toString();
    }

    private static String renameShadowingLocal(String source, String uniform, Pattern declaration) {
        if (source.isEmpty() || !IDENTIFIER.matcher(source).find()) return source;
        String analysis = ShaderSource.maskBlockAndLineComments(source);
        List<Replacement> replacements = new ArrayList<>();
        Matcher functions = FUNCTION_DEFINITION.matcher(analysis);
        while (functions.find()) {
            int openingBrace = functions.end() - 1;
            int functionEnd = matchingBrace(analysis, openingBrace);
            if (functionEnd < 0) continue;

            List<Scope> scopes = new ArrayList<>();
            Matcher parameter = declaration.matcher(analysis);
            parameter.region(functions.start(3), functions.end(3)).useTransparentBounds(true);
            if (parameter.find()) {
                scopes.add(new Scope(functions.start(3), functionEnd));
            } else {
                Matcher locals = declaration.matcher(analysis);
                locals.region(openingBrace + 1, functionEnd - 1);
                while (locals.find()) {
                    int nameStart = locals.start("name");
                    int scopeStart = innermostOpeningBrace(analysis, openingBrace, nameStart);
                    int scopeEnd = scopeStart < 0 ? functionEnd : matchingBrace(analysis, scopeStart);
                    if (scopeStart >= 0 && scopeEnd > nameStart) {
                        scopes.add(new Scope(nameStart, scopeEnd));
                    }
                }
            }
            if (scopes.isEmpty()) continue;

            String alias = uniqueAlias(analysis, uniform);
            Matcher identifiers = Pattern.compile("\\b" + Pattern.quote(uniform) + "\\b")
                    .matcher(analysis);
            identifiers.region(functions.start(), functionEnd);
            while (identifiers.find()) {
                int start = identifiers.start();
                if (isMemberName(analysis, start)) continue;
                if (scopes.stream().anyMatch(scope -> start >= scope.start() && start < scope.end())) {
                    replacements.add(new Replacement(start, identifiers.end(), alias));
                }
            }
        }
        if (replacements.isEmpty()) return source;

        replacements.sort((left, right) -> Integer.compare(right.start(), left.start()));
        StringBuilder rewritten = new StringBuilder(source);
        int lastStart = Integer.MAX_VALUE;
        for (Replacement replacement : replacements) {
            if (replacement.start() >= lastStart) continue;
            rewritten.replace(replacement.start(), replacement.end(), replacement.alias());
            lastStart = replacement.start();
        }
        return rewritten.toString();
    }

    private static Pattern localDeclaration(String name) {
        return LOCAL_DECLARATIONS.computeIfAbsent(name, uniform -> Pattern.compile(
                LOCAL_DECLARATION_PREFIX.pattern()
                        + "(?<name>" + Pattern.quote(uniform) + ")\\b(?=\\s*+(?:=|;|,|\\)|\\[))"));
    }

    private static boolean containsIdentifier(String source, String name) {
        if (name.isEmpty()) return false;
        int cursor = 0;
        while ((cursor = source.indexOf(name, cursor)) >= 0) {
            int end = cursor + name.length();
            boolean leftBoundary = cursor == 0 || !isIdentifierPart(source.charAt(cursor - 1));
            boolean rightBoundary = end == source.length() || !isIdentifierPart(source.charAt(end));
            if (leftBoundary && rightBoundary) return true;
            cursor++;
        }
        return false;
    }

    private static boolean isIdentifierPart(char character) {
        return character == '_' || Character.isLetterOrDigit(character);
    }

    private static String uniqueAlias(String source, String uniform) {
        StringBuilder base = new StringBuilder("veyraLocal")
                .append(Character.toUpperCase(uniform.charAt(0)))
                .append(uniform.substring(1));
        String candidate = base.toString();
        int suffix = 2;
        Matcher existing = Pattern.compile("\\b" + Pattern.quote(candidate) + "\\b").matcher(source);
        while (existing.find()) {
            candidate = base + "_" + suffix++;
            existing = Pattern.compile("\\b" + Pattern.quote(candidate) + "\\b").matcher(source);
        }
        return candidate;
    }

    private static boolean isMemberName(String source, int identifierStart) {
        int previous = identifierStart - 1;
        while (previous >= 0 && Character.isWhitespace(source.charAt(previous))) previous--;
        return previous >= 0 && source.charAt(previous) == '.';
    }

    private static int innermostOpeningBrace(String source, int functionBrace, int position) {
        Deque<Integer> openings = new ArrayDeque<>();
        openings.push(functionBrace);
        for (int index = functionBrace + 1; index < position; index++) {
            char current = source.charAt(index);
            if (current == '{') openings.push(index);
            else if (current == '}' && openings.size() > 1) openings.pop();
        }
        return openings.peek();
    }

    private static int matchingBrace(String source, int openingBrace) {
        int depth = 0;
        for (int index = openingBrace; index < source.length(); index++) {
            char current = source.charAt(index);
            if (current == '{') depth++;
            else if (current == '}' && --depth == 0) return index + 1;
        }
        return -1;
    }

    private record Scope(int start, int end) {
    }

    private record Replacement(int start, int end, String alias) {
    }
}
