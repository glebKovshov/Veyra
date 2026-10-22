package dev.veyra.shader;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Reads the active Iris output mapping while ignoring directives in inactive preprocessor branches. */
final class RenderTargetDirectives {
    private static final String MARKER_PREFIX = "VEYRA_RENDER_TARGET_DIRECTIVE_";
    private static final Pattern DIRECTIVE = Pattern.compile(
            "(?is)/\\*\\s*(RENDERTARGETS|DRAWBUFFERS)\\s*:\\s*([0-9,\\s]+)\\s*\\*/");

    private RenderTargetDirectives() { }

    static List<Integer> activeTargets(String source) throws IOException {
        return scan("fragment render-target directives", source).targets();
    }

    static String keepActiveComments(String file, String source) throws IOException {
        Scan scan = scan(file, source);
        if (scan.active().isEmpty()) return source;
        Matcher matcher = DIRECTIVE.matcher(source);
        StringBuilder filtered = new StringBuilder(source.length());
        int cursor = 0;
        int index = 0;
        while (matcher.find()) {
            filtered.append(source, cursor, matcher.start());
            if (scan.active().get(index)) {
                filtered.append(source, matcher.start(), matcher.end());
            } else {
                for (int offset = matcher.start(); offset < matcher.end(); offset++) {
                    char character = source.charAt(offset);
                    filtered.append(character == '\n' || character == '\r' ? character : ' ');
                }
            }
            cursor = matcher.end();
            index++;
        }
        return filtered.append(source, cursor, source.length()).toString();
    }

    private static Scan scan(String file, String source) throws IOException {
        if (source.contains(MARKER_PREFIX)) {
            throw new IOException("Shader source contains Veyra's reserved render-target marker.");
        }
        Matcher matcher = DIRECTIVE.matcher(source);
        if (!matcher.find()) return new Scan(null, List.of());

        List<Directive> directives = new ArrayList<>();
        StringBuilder marked = new StringBuilder(source.length());
        int cursor = 0;
        do {
            marked.append(source, cursor, matcher.start()).append(MARKER_PREFIX).append(directives.size());
            directives.add(new Directive(matcher.group(1).equalsIgnoreCase("RENDERTARGETS"), matcher.group(2)));
            cursor = matcher.end();
        } while (matcher.find());
        marked.append(source, cursor, source.length());

        String preprocessed = GlslPreprocessor.process(file, marked.toString());
        Directive active = null;
        List<Boolean> activeMarkers = new ArrayList<>(directives.size());
        for (int index = 0; index < directives.size(); index++) {
            boolean markerIsActive = preprocessed.contains(MARKER_PREFIX + index);
            activeMarkers.add(markerIsActive);
            if (!markerIsActive) continue;
            // Iris's comment-directive parser uses the last active occurrence. Packs may
            // intentionally provide a fallback mapping before a profile-specific override.
            active = directives.get(index);
        }
        return new Scan(active == null ? null : parseTargets(active), List.copyOf(activeMarkers));
    }

    private static List<Integer> parseTargets(Directive directive) throws IOException {
        List<Integer> targets = new ArrayList<>();
        if (directive.retargets()) {
            for (String value : directive.value().split(",")) {
                if (value.isBlank()) throw new IOException("RENDERTARGETS contains an empty target index.");
                targets.add(parseIndex(value.trim()));
            }
        } else {
            String value = directive.value().replaceAll("\\s+", "");
            for (int index = 0; index < value.length(); index++) {
                int target = Character.digit(value.charAt(index), 10);
                if (target < 0) throw new IOException("DRAWBUFFERS contains an invalid target index.");
                targets.add(target);
            }
        }
        if (targets.isEmpty()) throw new IOException("The render-target directive must name at least one target.");
        return List.copyOf(targets);
    }

    private static int parseIndex(String value) throws IOException {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            throw new IOException("RENDERTARGETS contains an invalid target index.", exception);
        }
    }

    private record Directive(boolean retargets, String value) { }
    private record Scan(List<Integer> targets, List<Boolean> active) { }
}
