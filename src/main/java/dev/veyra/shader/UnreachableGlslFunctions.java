package dev.veyra.shader;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Removes GLSL helper bodies outside an entrypoint's static call tree. */
final class UnreachableGlslFunctions {
    private static final Pattern FUNCTION_DEFINITION = Pattern.compile(
            "(?m)^[\\t ]*(?:[A-Za-z_][A-Za-z0-9_]*[\\t ]+)+([A-Za-z_][A-Za-z0-9_]*)[\\t ]*\\([^;{}]*\\)[\\t ]*\\{");
    private static final Pattern FUNCTION_CALL = Pattern.compile("\\b([A-Za-z_][A-Za-z0-9_]*)\\s*\\(");

    private UnreachableGlslFunctions() {
    }

    static String removeUnreachable(String source, String entrypoint) {
        String uncommented = ShaderSource.maskBlockAndLineComments(source);
        Matcher matcher = FUNCTION_DEFINITION.matcher(uncommented);
        Map<String, List<FunctionBody>> functions = new java.util.LinkedHashMap<>();
        while (matcher.find()) {
            int openingBrace = uncommented.indexOf('{', matcher.start());
            int closingBrace = matchingBrace(uncommented, openingBrace);
            if (closingBrace >= 0) {
                FunctionBody function = new FunctionBody(matcher.group(1), matcher.start(), openingBrace,
                        closingBrace + 1);
                functions.computeIfAbsent(function.name(), ignored -> new ArrayList<>()).add(function);
            }
        }
        List<FunctionBody> topLevelFunctions = functions.values().stream().flatMap(List::stream)
                .sorted(java.util.Comparator.comparingInt(FunctionBody::start)).toList();
        functions.clear();
        int topLevelEnd = -1;
        for (FunctionBody function : topLevelFunctions) {
            // The permissive declaration matcher can also recognize function-like text inside
            // a function body. GLSL does not allow nested function definitions; ignoring these
            // overlapping matches keeps source slicing and reachability limited to real bodies.
            if (function.start() < topLevelEnd) continue;
            functions.computeIfAbsent(function.name(), ignored -> new ArrayList<>()).add(function);
            topLevelEnd = function.end();
        }
        if (functions.isEmpty() || !functions.containsKey(entrypoint)) return source;

        Set<String> reachable = new LinkedHashSet<>();
        ArrayDeque<String> pending = new ArrayDeque<>();
        pending.add(entrypoint);
        while (!pending.isEmpty()) {
            String name = pending.removeFirst();
            if (!reachable.add(name)) continue;
            for (FunctionBody function : functions.getOrDefault(name, List.of())) {
                Matcher calls = FUNCTION_CALL.matcher(uncommented.substring(function.bodyStart() + 1,
                        function.end() - 1));
                while (calls.find()) {
                    String callee = calls.group(1);
                    if (functions.containsKey(callee) && !reachable.contains(callee)) pending.addLast(callee);
                }
            }
        }

        List<FunctionBody> unreachable = functions.values().stream().flatMap(List::stream)
                .filter(function -> !reachable.contains(function.name()))
                .sorted(java.util.Comparator.comparingInt(FunctionBody::start)).toList();
        if (unreachable.isEmpty()) return source;
        StringBuilder result = new StringBuilder(source.length());
        int copiedThrough = 0;
        for (FunctionBody function : unreachable) {
            result.append(source, copiedThrough, function.start());
            for (int index = function.start(); index < function.end(); index++) {
                char character = source.charAt(index);
                result.append(character == '\r' || character == '\n' ? character : ' ');
            }
            copiedThrough = function.end();
        }
        return result.append(source, copiedThrough, source.length()).toString();
    }

    private static int matchingBrace(String source, int openingBrace) {
        if (openingBrace < 0) return -1;
        int depth = 0;
        for (int index = openingBrace; index < source.length(); index++) {
            char character = source.charAt(index);
            if (character == '{') depth++;
            else if (character == '}' && --depth == 0) return index;
        }
        return -1;
    }

    private record FunctionBody(String name, int start, int bodyStart, int end) {
    }
}
