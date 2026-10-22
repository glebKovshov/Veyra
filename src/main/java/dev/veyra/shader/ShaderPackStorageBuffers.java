package dev.veyra.shader;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Filters unused GLSL storage-buffer blocks and reports active blocks before VulkanMod's parser sees them. */
final class ShaderPackStorageBuffers {
    private static final String ACCESS = "(?:(?:readonly|writeonly|coherent|volatile|restrict)\\s+)*";
    private static final Pattern BLOCK_START = Pattern.compile(
            "(?m)^[\\t ]*(?:layout\\s*\\([^)]*\\)\\s*)?" + ACCESS
                    + "buffer\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*\\{");
    private static final Pattern INSTANCE = Pattern.compile("\\s*([A-Za-z_][A-Za-z0-9_]*)\\s*;");

    private ShaderPackStorageBuffers() { }

    static String removeUnusedOrReject(String source, String programName) throws IOException {
        String analysis = ShaderSource.maskBlockAndLineComments(source);
        Matcher blocks = BLOCK_START.matcher(analysis);
        List<Span> unused = new ArrayList<>();
        while (blocks.find()) {
            int openingBrace = blocks.end() - 1;
            int closingBrace = matchingBrace(analysis, openingBrace);
            if (closingBrace < 0) {
                throw new IOException(programName + " has an unterminated storage buffer block '" + blocks.group(1) + "'.");
            }
            Matcher instance = INSTANCE.matcher(analysis);
            instance.region(closingBrace + 1, analysis.length());
            if (!instance.lookingAt()) {
                throw unsupported(programName, blocks.group(1));
            }
            String instanceName = instance.group(1);
            String references = analysis.substring(0, blocks.start()) + analysis.substring(instance.end());
            if (Pattern.compile("\\b" + Pattern.quote(instanceName) + "\\b").matcher(references).find()) {
                throw unsupported(programName, blocks.group(1));
            }
            unused.add(new Span(blocks.start(), instance.end()));
            blocks.region(instance.end(), analysis.length());
        }
        if (unused.isEmpty()) return source;

        StringBuilder result = new StringBuilder(source);
        for (int index = unused.size() - 1; index >= 0; index--) {
            Span span = unused.get(index);
            for (int offset = span.start(); offset < span.end(); offset++) {
                char character = result.charAt(offset);
                if (character != '\r' && character != '\n') result.setCharAt(offset, ' ');
            }
        }
        return result.toString();
    }

    private static IOException unsupported(String programName, String blockName) {
        return new IOException(programName + " uses active storage buffer '" + blockName
                + "', which VulkanMod 0.6.8 native RenderPipeline does not support.");
    }

    private static int matchingBrace(String source, int openingBrace) {
        int depth = 0;
        for (int index = openingBrace; index < source.length(); index++) {
            char character = source.charAt(index);
            if (character == '{') depth++;
            else if (character == '}' && --depth == 0) return index;
        }
        return -1;
    }

    private record Span(int start, int end) { }
}
