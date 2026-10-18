package dev.veyra.shader;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Expands file includes while leaving GLSL conditional compilation to the shader compiler. */
final class ShaderIncludes {
    private static final int MAX_DEPTH = 32;
    private static final int MAX_CHARS = 4 * 1024 * 1024;
    private static final Pattern INCLUDE = Pattern.compile(
            "(?m)^[\\t ]*(#[\\t ]*include[\\t ]+(?:\"([^\"]+)\"|<([^>]+)>))[\\t ]*(?=\\r?$)");

    @FunctionalInterface
    interface Reader {
        Optional<String> read(String path) throws IOException;
    }

    private ShaderIncludes() {
    }

    static String expand(String file, String source, Reader reader) throws IOException {
        return expand(file, source, reader, 0);
    }

    private static String expand(String file, String source, Reader reader, int depth)
            throws IOException {
        // Retain the nested file's own guards. Replacing it at the first repeated path would
        // turn a legal #ifndef-guarded recursion into an unconditional #error.
        if (depth > MAX_DEPTH) {
            return error("Shader includes exceed the maximum nesting depth of " + MAX_DEPTH);
        }
        source = source.replace('\uFEFF', ' ');
        Matcher matcher = INCLUDE.matcher(maskComments(source));
        StringBuilder expanded = new StringBuilder();
        int copiedThrough = 0;
        while (matcher.find()) {
            append(expanded, source.substring(copiedThrough, matcher.start(1)));
            String requested = matcher.group(2) != null ? matcher.group(2) : matcher.group(3);
            String path = resolve(file, requested);
            Optional<String> included = reader.read(path);
            String replacement = included.isPresent()
                    ? expand(path, included.get(), reader, depth + 1)
                    : error("Shader include was not found: " + path);
            append(expanded, replacement);
            if (!replacement.endsWith("\n")) {
                append(expanded, "\n");
            }
            // Preserve comments before/after the directive, including block-comment delimiters.
            copiedThrough = matcher.end(1);
        }
        append(expanded, source.substring(copiedThrough));
        return expanded.toString();
    }

    private static String error(String message) {
        // #error is ignored in inactive branches; an unconditional error still fails compilation.
        return "#error " + message.replace('\r', ' ').replace('\n', ' ') + "\n";
    }

    private static void append(StringBuilder target, String text) throws IOException {
        if (text.length() > MAX_CHARS - target.length()) {
            throw new IOException("Expanded shader exceeds the 4 MiB size limit.");
        }
        target.append(text);
    }

    static String resolve(String includingFile, String requested) throws IOException {
        String portable = requested.replace('\\', '/');
        var segments = new ArrayList<String>();
        if (!portable.startsWith("/") && includingFile.contains("/")) {
            segments.addAll(java.util.List.of(includingFile.substring(0, includingFile.lastIndexOf('/')).split("/")));
        }
        for (String segment : portable.split("/")) {
            if (segment.isEmpty() || segment.equals(".")) {
                continue;
            }
            if (segment.equals("..")) {
                if (segments.isEmpty()) {
                    throw new IOException("Shader include escapes the shaders directory: " + requested);
                }
                segments.removeLast();
            } else {
                segments.add(segment);
            }
        }
        if (segments.isEmpty()) {
            throw new IOException("Shader include path is empty: " + requested);
        }
        return String.join("/", segments);
    }

    /** Replaces comments with spaces, retaining source offsets, quoted paths, and newlines. */
    private static String maskComments(String source) {
        char[] masked = source.toCharArray();
        boolean block = false;
        boolean line = false;
        boolean quoted = false;
        for (int i = 0; i < masked.length; i++) {
            char c = source.charAt(i);
            char next = i + 1 < masked.length ? source.charAt(i + 1) : '\0';
            if (line && (c == '\n' || c == '\r')) {
                line = false;
            }
            if (block) {
                if (c == '*' && next == '/') {
                    masked[i] = masked[++i] = ' ';
                    block = false;
                } else if (c != '\n' && c != '\r') {
                    masked[i] = ' ';
                }
            } else if (line) {
                masked[i] = ' ';
            } else if (c == '"') {
                quoted = !quoted;
            } else if (!quoted && c == '/' && (next == '/' || next == '*')) {
                block = next == '*';
                line = next == '/';
                masked[i] = masked[++i] = ' ';
            }
        }
        return new String(masked);
    }
}
