package dev.veyra.shader;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

/** Filters unused GLSL storage-buffer blocks and reports active blocks before VulkanMod's parser sees them. */
public final class ShaderPackStorageBuffers {
    /** Keep shader-pack SSBOs clear of VulkanMod's UBO and sampler bindings. */
    // Leave the low descriptor range to VulkanMod's uniform blocks and Veyra's image samplers.
    static final int VULKAN_DESCRIPTOR_BINDING_BASE = 1024;
    private static final String ACCESS = "(?:(?:readonly|writeonly|coherent|volatile|restrict)\\s+)*";
    private static final Pattern BLOCK_START = Pattern.compile(
            "(?m)^[\\t ]*(?:layout\\s*\\(([^)]*)\\)\\s*)?" + ACCESS
                    + "buffer\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*\\{");
    private static final Pattern PARSER_BLOCK_START = Pattern.compile(
            "(?m)^[\\t ]*(?:layout\\s*\\((?<layout>[^)]*)\\)\\s*)?(?<access>"
                    + ACCESS + ")buffer\\s+(?<name>[A-Za-z_][A-Za-z0-9_]*)\\s*\\{");
    private static final Pattern INSTANCE = Pattern.compile("\\s*([A-Za-z_][A-Za-z0-9_]*)\\s*;");

    private ShaderPackStorageBuffers() { }

    /** VulkanMod's GLSL lexer stops at the first '*' inside block comments; comments are not parser input. */
    public static String stripCommentsForVulkanParser(String source) {
        return ShaderSource.maskBlockAndLineComments(source);
    }

    static String removeUnusedOrReject(String source, String programName) throws IOException {
        return removeUnusedOrReject(source, programName, null, 0, 0);
    }

    static String removeUnusedOrReject(String source, String programName,
                                       ShaderPackStorageBufferProperties properties,
                                       int viewWidth, int viewHeight) throws IOException {
        String analysis = ShaderSource.maskBlockAndLineComments(source);
        Matcher blocks = BLOCK_START.matcher(analysis);
        List<Span> unused = new ArrayList<>();
        while (blocks.find()) {
            String blockName = blocks.group(2);
            int openingBrace = blocks.end() - 1;
            int closingBrace = matchingBrace(analysis, openingBrace);
            if (closingBrace < 0) {
                throw new IOException(programName + " has an unterminated storage buffer block '" + blockName + "'.");
            }
            Matcher instance = INSTANCE.matcher(analysis);
            instance.region(closingBrace + 1, analysis.length());
            if (!instance.lookingAt()) {
                throw unsupported(programName, blockName, binding(blocks.group(1)), properties, viewWidth, viewHeight);
            }
            String instanceName = instance.group(1);
            String references = analysis.substring(0, blocks.start()) + analysis.substring(instance.end());
            if (Pattern.compile("\\b" + Pattern.quote(instanceName) + "\\b").matcher(references).find()) {
                throw unsupported(programName, blockName, binding(blocks.group(1)), properties, viewWidth, viewHeight);
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

    /** Removes blocks referenced only by helpers that were removed and leaves active blocks for parser bridging. */
    static String removeUnused(String source) throws IOException {
        String analysis = ShaderSource.maskBlockAndLineComments(source);
        Matcher blocks = PARSER_BLOCK_START.matcher(analysis);
        List<Span> unused = new ArrayList<>();
        while (blocks.find()) {
            int closingBrace = matchingBrace(analysis, blocks.end() - 1);
            if (closingBrace < 0) {
                throw new IOException("Storage buffer block '" + blocks.group("name") + "' is unterminated.");
            }
            Matcher instance = INSTANCE.matcher(analysis);
            instance.region(closingBrace + 1, analysis.length());
            if (!instance.lookingAt()) {
                continue;
            }
            String instanceName = instance.group(1);
            String references = analysis.substring(0, blocks.start()) + analysis.substring(instance.end());
            if (!Pattern.compile("\\b" + Pattern.quote(instanceName) + "\\b").matcher(references).find()) {
                unused.add(new Span(blocks.start(), instance.end()));
                blocks.region(instance.end(), analysis.length());
            }
        }
        return maskSpans(source, unused);
    }

    /** True when a reachable, active block can write and needs vertexPipelineStoresAndAtomics. */
    static boolean hasWritableActiveBuffer(String source) throws IOException {
        String analysis = ShaderSource.maskBlockAndLineComments(source);
        Matcher blocks = PARSER_BLOCK_START.matcher(analysis);
        while (blocks.find()) {
            int closingBrace = matchingBrace(analysis, blocks.end() - 1);
            String blockName = blocks.group("name");
            if (closingBrace < 0) {
                throw new IOException("Storage buffer block '" + blockName + "' is unterminated.");
            }
            Matcher instance = INSTANCE.matcher(analysis);
            instance.region(closingBrace + 1, analysis.length());
            if (!instance.lookingAt()) continue;

            String instanceName = instance.group(1);
            String references = analysis.substring(0, blocks.start()) + analysis.substring(instance.end());
            boolean active = Pattern.compile("\\b" + Pattern.quote(instanceName) + "\\b")
                    .matcher(references).find();
            if (active && !Pattern.compile("\\breadonly\\b").matcher(blocks.group("access")).find()) {
                return true;
            }
            blocks.region(instance.end(), analysis.length());
        }
        return false;
    }

    /** Rewrites active SSBO declarations as parser-friendly uniform blocks while retaining their real GLSL. */
    public static PreparedSource prepareForVulkanParser(String source,
                                                 ShaderPackStorageBufferProperties properties,
                                                 int viewWidth, int viewHeight) throws IOException {
        String analysis = ShaderSource.maskBlockAndLineComments(source);
        Matcher blocks = PARSER_BLOCK_START.matcher(analysis);
        List<Replacement> replacements = new ArrayList<>();
        Map<String, StorageBlock> storageBlocks = new HashMap<>();
        while (blocks.find()) {
            int closingBrace = matchingBrace(analysis, blocks.end() - 1);
            String blockName = blocks.group("name");
            if (closingBrace < 0) {
                throw new IOException("Storage buffer block '" + blockName + "' is unterminated.");
            }
            Matcher instance = INSTANCE.matcher(analysis);
            instance.region(closingBrace + 1, analysis.length());
            if (!instance.lookingAt()) {
                throw new IOException("Storage buffer block '" + blockName
                        + "' must have one named instance; block arrays and anonymous blocks are not supported yet.");
            }
            String instanceName = instance.group(1);
            String references = analysis.substring(0, blocks.start()) + analysis.substring(instance.end());
            if (!Pattern.compile("\\b" + Pattern.quote(instanceName) + "\\b").matcher(references).find()) {
                replacements.add(new Replacement(blocks.start(), instance.end(), ""));
                blocks.region(instance.end(), analysis.length());
                continue;
            }

            int binding = binding(blocks.group("layout"));
            if (binding < 0) {
                throw new IOException("Active storage buffer '" + blockName + "' has no numeric layout(binding = N).");
            }
            if (properties == null || !properties.buffers().containsKey(binding)) {
                throw new IOException("Active storage buffer '" + blockName + "' at binding " + binding
                        + " has no bufferObject." + binding + " allocation in the selected shader-pack profile.");
            }
            long size = properties.buffers().get(binding).resolveSize(viewWidth, viewHeight);
            String body = source.substring(blocks.end(), closingBrace);
            String signature = blockName + '\n' + binding + '\n' + blocks.group("access") + '\n' + body;
            String placeholder = "VeyraStorageBuffer_" + binding + "_" + shortHash(signature);
            int descriptorBinding = VULKAN_DESCRIPTOR_BINDING_BASE + binding;
            StorageBlock storageBlock = new StorageBlock(placeholder, binding, size, blockName,
                    blocks.group("layout"), blocks.group("access"), body,
                    source.substring(closingBrace + 1, instance.end()));
            StorageBlock previous = storageBlocks.putIfAbsent(placeholder, storageBlock);
            if (previous != null && !previous.equals(storageBlock)) {
                throw new IOException("Storage-buffer parser bridge name collision for '" + blockName + "'.");
            }
            String parserBlock = "layout(binding = " + descriptorBinding + ") uniform " + placeholder
                    + " { vec4 veyraStorageBufferPlaceholder; }" + storageBlock.instanceSuffix();
            replacements.add(new Replacement(blocks.start(), instance.end(), parserBlock));
            blocks.region(instance.end(), analysis.length());
        }

        StringBuilder adapted = new StringBuilder(source);
        for (int index = replacements.size() - 1; index >= 0; index--) {
            Replacement replacement = replacements.get(index);
            adapted.replace(replacement.start(), replacement.end(), replacement.value());
        }
        return new PreparedSource(adapted.toString(), Map.copyOf(storageBlocks));
    }

    private static String maskSpans(String source, List<Span> spans) {
        if (spans.isEmpty()) return source;
        StringBuilder result = new StringBuilder(source);
        for (int index = spans.size() - 1; index >= 0; index--) {
            Span span = spans.get(index);
            for (int offset = span.start(); offset < span.end(); offset++) {
                char character = result.charAt(offset);
                if (character != '\r' && character != '\n') result.setCharAt(offset, ' ');
            }
        }
        return result.toString();
    }

    private static String shortHash(String source) {
        try {
            byte[] digest = java.security.MessageDigest.getInstance("SHA-256")
                    .digest(source.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest, 0, 8);
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable.", exception);
        }
    }

    private static int binding(String layout) {
        if (layout == null) return -1;
        Matcher matcher = Pattern.compile("(?:^|,)\\s*binding\\s*=\\s*(\\d+)\\s*(?:,|$)").matcher(layout);
        return matcher.find() ? Integer.parseInt(matcher.group(1)) : -1;
    }

    private static IOException unsupported(String programName, String blockName, int binding,
                                          ShaderPackStorageBufferProperties properties,
                                          int viewWidth, int viewHeight) {
        String details = "";
        if (binding >= 0) {
            if (properties == null || !properties.buffers().containsKey(binding)) {
                details = " at binding " + binding + " (no bufferObject." + binding + " allocation is declared)";
            } else {
                try {
                    long size = properties.buffers().get(binding).resolveSize(viewWidth, viewHeight);
                    details = " at binding " + binding + " (" + size + " bytes)";
                } catch (IOException exception) {
                    details = " at binding " + binding + " (invalid allocation: " + exception.getMessage() + ")";
                }
            }
        }
        return new IOException(programName + " uses active storage buffer '" + blockName + "'" + details
                + ", which VulkanMod 0.6.8 native RenderPipeline does not support yet.");
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

    public record PreparedSource(String source, Map<String, StorageBlock> storageBlocks) { }

    public record StorageBlock(String placeholder, int bufferObjectBinding, long allocationBytes,
                               String blockName, String layout, String access, String body, String instanceSuffix) {
        public int descriptorBinding() {
            return VULKAN_DESCRIPTOR_BINDING_BASE + bufferObjectBinding;
        }

        public String declaration(int parserBinding) {
            String resolvedLayout = layout == null ? "" : layout.strip();
            Matcher bindingMatcher = Pattern.compile("(?i)(?:^|,)\\s*binding\\s*=\\s*\\d+\\s*(?=,|$)")
                    .matcher(resolvedLayout);
            if (bindingMatcher.find()) {
                resolvedLayout = bindingMatcher.replaceFirst(bindingMatcher.group().replaceAll(
                        "(?i)binding\\s*=\\s*\\d+", "binding = " + parserBinding));
            } else {
                resolvedLayout = resolvedLayout.isEmpty() ? "binding = " + parserBinding
                        : resolvedLayout + ", binding = " + parserBinding;
            }
            return "layout(" + resolvedLayout + ") " + access + "buffer " + blockName + " {"
                    + body + "}" + instanceSuffix;
        }
    }

    private record Replacement(int start, int end, String value) { }
    private record Span(int start, int end) { }
}
