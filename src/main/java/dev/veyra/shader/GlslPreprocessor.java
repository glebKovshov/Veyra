package dev.veyra.shader;

import org.anarres.cpp.Feature;
import org.anarres.cpp.LexerException;
import org.anarres.cpp.Preprocessor;
import org.anarres.cpp.PreprocessorListener;
import org.anarres.cpp.Source;
import org.anarres.cpp.StringLexerSource;
import org.anarres.cpp.Token;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** GLSL conditional/macro evaluation before the Vulkan interface adapters inspect declarations. */
final class GlslPreprocessor {
    private static final int MAX_OUTPUT = 4 * 1024 * 1024;
    private static final String MARKER = "VEYRA_INTERNAL_DIRECTIVE_";
    private static final Pattern DIRECTIVE = Pattern.compile("(?m)^[\\t ]*#\\s*(version|extension)\\s+([^\\r\\n]+)");

    private GlslPreprocessor() { }

    static Map<String, String> environment() {
        Map<String, String> macros = new LinkedHashMap<>();
        macros.put("MC_VERSION", "12111");
        macros.put("MC_GL_VERSION", "460");
        macros.put("MC_GLSL_VERSION", "460");
        macros.put("MC_RENDER_QUALITY", "1.0");
        macros.put("MC_SHADOW_QUALITY", "1.0");
        macros.put("MC_HAND_DEPTH", "0.125");
        // Veyra implements Iris shader-pack compatibility, including Iris-only uniforms/stages.
        macros.put("IS_IRIS", "1");
        macros.put("VEYRA", "200");
        macros.put("IRIS_TAG_SUPPORT", "2");
        macros.put("MAX_COLOR_BUFFERS", "16");
        macros.put("MC_NORMAL_MAP", "1");
        macros.put("MC_SPECULAR_MAP", "1");
        macros.putAll(IrisRenderStages.macros());
        macros.putAll(BiomeIds.defines());
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        macros.put(os.contains("win") ? "MC_OS_WINDOWS" : os.contains("mac") ? "MC_OS_MAC" : "MC_OS_LINUX", "1");
        return macros;
    }

    static String process(String file, String source) throws IOException {
        return process(file, source, Map.of());
    }

    private static final java.util.regex.Pattern VULKAN_SAMPLER_KEYWORD =
            java.util.regex.Pattern.compile("\\bsampler\\b");
    private static final java.util.regex.Pattern MULTILINE_LAYOUT =
            java.util.regex.Pattern.compile("\\blayout\\s*\\(([^()]*\\R[^()]*)\\)");

    static String collapseMultilineLayouts(String source) {
        java.util.regex.Matcher layout = MULTILINE_LAYOUT.matcher(source);
        if (!layout.find()) return source;
        StringBuilder collapsed = new StringBuilder(source.length());
        int cursor = 0;
        do {
            collapsed.append(source, cursor, layout.start()).append("layout(")
                    .append(layout.group(1).strip().replaceAll("\\s+", " ")).append(')');
            cursor = layout.end();
        } while (layout.find());
        return collapsed.append(source, cursor, source.length()).toString();
    }

    private static final java.util.regex.Pattern FRAGMENT_STAGE = java.util.regex.Pattern.compile("\\.fsh$");

    private record ProcessKey(String file, String source, Map<String, String> additionalMacros,
                              Map<String, String> environment) { }

    /**
     * Adapters and validation preprocess the same program sources repeatedly while a pack is selected.
     * Photon alone has well over a hundred stages, each preprocessed under several macro sets, so the cache is
     * bounded by output size rather than a small entry count that a large pack would cycle through.
     */
    private static final int PROCESS_CACHE_SIZE = 1024;
    private static final long PROCESS_CACHE_CHARS = 48L * 1024 * 1024;
    private static long processedChars;
    private static final Map<ProcessKey, String> PROCESSED = java.util.Collections.synchronizedMap(
            new LinkedHashMap<>(PROCESS_CACHE_SIZE, 0.75F, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<ProcessKey, String> eldest) {
                    if (size() > PROCESS_CACHE_SIZE || processedChars > PROCESS_CACHE_CHARS) {
                        processedChars -= eldest.getValue().length();
                        return true;
                    }
                    return false;
                }
            });

    static String process(String file, String source, Map<String, String> additionalMacros) throws IOException {
        ProcessKey key = new ProcessKey(file, source, Map.copyOf(additionalMacros), environment());
        String cached = PROCESSED.get(key);
        if (cached != null) return cached;
        // The preprocessor keeps one empty line per skipped conditional line: 84% of a Photon particle stage is
        // whitespace (39k lines, 2k of code), and every adapter regex would otherwise rescan it.
        String processed = dropBlankLines(processUncached(file, source, additionalMacros));
        // 'sampler' is an ordinary identifier in OpenGL GLSL (Photon names parameters so) but a type keyword in
        // Vulkan GLSL; desktop GLSL has no 'sampler' type, so renaming every such token keeps the pack's meaning.
        processed = VULKAN_SAMPLER_KEYWORD.matcher(processed).replaceAll("veyra_sampler");
        // Adapters parse one-line declarations; Photon spreads layout(location = N) over three lines.
        processed = collapseMultilineLayouts(processed);
        // Geometry and post-pass adapters route vec4 fragment outputs only.
        if (FRAGMENT_STAGE.matcher(file).find()) processed = NarrowFragmentOutputs.widen(processed);
        synchronized (PROCESSED) {
            String previous = PROCESSED.put(key, processed);
            processedChars += processed.length() - (previous == null ? 0 : previous.length());
        }
        return processed;
    }

    /** Removes whitespace-only lines and trailing whitespace; preprocessed GLSL has no line continuations. */
    static String dropBlankLines(String source) {
        StringBuilder compact = new StringBuilder(source.length() / 2);
        int length = source.length();
        int lineStart = 0;
        while (lineStart < length) {
            int newline = source.indexOf('\n', lineStart);
            int lineEnd = newline < 0 ? length : newline;
            int contentEnd = lineEnd;
            while (contentEnd > lineStart && Character.isWhitespace(source.charAt(contentEnd - 1))) contentEnd--;
            if (contentEnd > lineStart) compact.append(source, lineStart, contentEnd).append('\n');
            lineStart = lineEnd + 1;
        }
        return compact.toString();
    }

    private static String processUncached(String file, String source, Map<String, String> additionalMacros)
            throws IOException {
        if (source.contains(MARKER)) throw new IOException(file + ": reserved preprocessor marker in source.");
        List<String> directives = new ArrayList<>();
        Matcher matcher = DIRECTIVE.matcher(ShaderSource.maskBlockAndLineComments(source));
        StringBuilder encoded = new StringBuilder();
        int end = 0;
        Map<String, String> macros = environment();
        macros.putAll(additionalMacros);
        while (matcher.find()) {
            encoded.append(source, end, matcher.start());
            String directive = source.substring(matcher.start(), matcher.end()).strip();
            if (matcher.group(1).equals("version") && !macros.containsKey("__VERSION__")) {
                String version = matcher.group(2).strip().split("\\s+")[0];
                if (version.matches("\\d+")) macros.put("__VERSION__", version);
            }
            encoded.append(MARKER).append(directives.size());
            directives.add(directive);
            end = matcher.end();
        }
        encoded.append(source.substring(end));
        String processed = evaluate(file, encoded.toString(), macros);
        List<String> active = new ArrayList<>();
        for (int i = 0; i < directives.size(); i++) {
            String marker = MARKER + i;
            Pattern token = Pattern.compile("\\b" + marker + "\\b");
            if (token.matcher(processed).find()) {
                active.add(directives.get(i));
                processed = token.matcher(processed).replaceAll("");
            }
        }
        long versions = active.stream().filter(d -> d.matches("#\\s*version\\b.*")).count();
        if (versions > 1) throw new IOException(file + ": more than one active #version directive.");
        return splitUniformLists(String.join("\n", active) + "\n" + processed);
    }

    private static final Pattern UNIFORM_LIST = Pattern.compile(
            "(?m)^([\\t ]*)uniform[\\t ]+((?:(?:lowp|mediump|highp)[\\t ]+)?[A-Za-z_][A-Za-z0-9_]*)[\\t ]+"
                    + "([A-Za-z_][A-Za-z0-9_]*(?:[\\t ]*,[\\t ]*[A-Za-z_][A-Za-z0-9_]*)+)[\\t ]*;");

    /**
     * Rewrites {@code uniform float near, far;} as one declaration per line. GLSL allows declarator
     * lists and packs such as BSL use them, while Veyra's adapters bridge uniforms by single declarations.
     */
    static String splitUniformLists(String source) {
        Matcher matcher = UNIFORM_LIST.matcher(source);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            StringBuilder declarations = new StringBuilder();
            for (String name : matcher.group(3).split(",")) {
                if (!declarations.isEmpty()) declarations.append('\n');
                declarations.append(matcher.group(1)).append("uniform ").append(matcher.group(2)).append(' ')
                        .append(name.strip()).append(';');
            }
            matcher.appendReplacement(result, Matcher.quoteReplacement(declarations.toString()));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    static boolean condition(String expression, Map<String, String> values) throws IOException {
        if (expression.contains("\n") || expression.contains("\r")) throw new IOException("Invalid program condition.");
        Map<String, String> macros = environment();
        macros.put("true", "1"); macros.put("false", "0");
        values.forEach((name, value) -> { if (name.matches("[A-Za-z_][A-Za-z0-9_]*") && !value.equals("false")) macros.put(name, value.equals("true") ? "1" : value); });
        return evaluate("program condition", "#if " + expression + "\nVEYRA_CONDITION_MATCH\n#endif\n", macros)
                .contains("VEYRA_CONDITION_MATCH");
    }

    static String properties(String source, Map<String, String> values) throws IOException {
        if (source.contains(MARKER)) throw new IOException("Reserved preprocessor marker in properties.");
        Map<String, String> macros = environment();
        values.forEach((key, value) -> {
            if (key.matches("[A-Za-z_][A-Za-z0-9_]*") && !value.equals("false")) macros.put(key, value.equals("true") ? "1" : value);
        });
        String[] lines = source.split("\\R", -1);
        StringBuilder encoded = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].stripLeading();
            if (line.matches("#\\s*(if|ifdef|ifndef|elif|else|endif|define|undef|error)\\b.*")) {
                encoded.append(lines[i]);
            } else {
                // Protect property names and values from macro substitution, including continuations.
                encoded.append(MARKER).append(i);
            }
            encoded.append('\n');
        }
        String result = evaluate("shaders.properties", encoded.toString(), macros);
        Matcher tokens = Pattern.compile(MARKER + "(\\d+)").matcher(result);
        StringBuilder decoded = new StringBuilder();
        while (tokens.find()) decoded.append(lines[Integer.parseInt(tokens.group(1))]).append('\n');
        return decoded.toString();
    }

    private static String evaluate(String file, String source, Map<String, String> macros) throws IOException {
        try (Preprocessor preprocessor = new Preprocessor()) {
            // Includes must already be sandboxed and expanded by ShaderIncludes. Never allow JCPP file I/O.
            preprocessor.setFileSystem(new org.anarres.cpp.VirtualFileSystem() {
                @Override public org.anarres.cpp.VirtualFile getFile(String path) {
                    throw new IllegalArgumentException("Unexpanded include is not allowed: " + path);
                }
                @Override public org.anarres.cpp.VirtualFile getFile(String parent, String path) { return getFile(path); }
            });
            preprocessor.addFeature(Feature.KEEPCOMMENTS);
            preprocessor.setListener(new PreprocessorListener() {
                @Override public void handleWarning(Source input, int line, int column, String message) { }
                @Override public void handleError(Source input, int line, int column, String message) throws LexerException {
                    throw new LexerException(file + ":" + line + ": " + message);
                }
                @Override public void handleSourceChange(Source input, SourceChangeEvent event) { }
            });
            for (var macro : macros.entrySet()) preprocessor.addMacro(macro.getKey(), macro.getValue());
            preprocessor.addInput(new StringLexerSource(source.replace("\u0000", ""), true));
            StringBuilder output = new StringBuilder();
            for (Token token = preprocessor.token(); token != null && token.getType() != Token.EOF; token = preprocessor.token()) {
                String text = token.getText();
                if (text.length() > MAX_OUTPUT - output.length()) throw new IOException(file + ": preprocessor output exceeds 4 MiB.");
                output.append(text);
            }
            return output.toString();
        } catch (LexerException | IllegalArgumentException exception) {
            throw new IOException("GLSL preprocessing failed: " + exception.getMessage(), exception);
        }
    }
}
