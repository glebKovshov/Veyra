package dev.veyra.shader;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Removes only unreferenced helper definitions that depend on unavailable post-pass vertex inputs. */
final class PostVertexLegacyFunctionFilter {
    private static final Pattern FUNCTION_DEFINITION = Pattern.compile(
            "(?m)^[\\t ]*(?:[A-Za-z_][A-Za-z0-9_]*[\\t ]+)+"
                    + "([A-Za-z_][A-Za-z0-9_]*)[\\t ]*\\([^;{}]*\\)[\\t ]*\\{");

    private PostVertexLegacyFunctionFilter() { }

    static String removeUnreferencedDefinitions(String source, Pattern unavailableInputs) {
        String result = source;
        boolean changed;
        do {
            changed = false;
            Matcher definitions = FUNCTION_DEFINITION.matcher(result);
            while (definitions.find()) {
                if (!unavailableInputs.matcher(result.substring(definitions.start(), functionEnd(result, definitions.end() - 1))).find()) {
                    continue;
                }
                String functionName = definitions.group(1);
                Matcher references = Pattern.compile("\\b" + Pattern.quote(functionName) + "\\b").matcher(result);
                int occurrences = 0;
                while (references.find() && occurrences < 2) occurrences++;
                if (occurrences != 1) continue;

                int end = functionEnd(result, definitions.end() - 1);
                StringBuilder filtered = new StringBuilder(result);
                for (int index = definitions.start(); index < end; index++) {
                    char character = filtered.charAt(index);
                    if (character != '\n' && character != '\r') filtered.setCharAt(index, ' ');
                }
                result = filtered.toString();
                changed = true;
                break;
            }
        } while (changed);
        return result;
    }

    private static int functionEnd(String source, int openingBrace) {
        int depth = 0;
        for (int index = openingBrace; index < source.length(); index++) {
            char character = source.charAt(index);
            if (character == '{') depth++;
            else if (character == '}' && --depth == 0) return index + 1;
        }
        return source.length();
    }
}
