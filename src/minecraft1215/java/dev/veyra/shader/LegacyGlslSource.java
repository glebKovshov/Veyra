package dev.veyra.shader;

import java.io.IOException;
import java.util.HashSet;
import java.util.regex.Pattern;

/** Lowers Veyra's generated UBO declarations to the old converter's named-uniform API. */
public final class LegacyGlslSource {
    private static final Pattern BLOCK = Pattern.compile(
            "(?m)^[\\t ]*(?:layout\\s*\\([^)]*\\)\\s*)?uniform\\s+([A-Za-z_]\\w*)\\s*\\{");
    private static final Pattern FIELD = Pattern.compile(
            "\\s*(float|int|vec[234]|ivec[23]|mat4)\\s+([A-Za-z_]\\w*)\\s*;");
    private LegacyGlslSource() { }

    public static String stripUnusedFragmentInputs(String source) {
        var declaration = Pattern.compile("(?m)^[\\t ]*(?:layout\\s*\\([^\\r\\n)]*\\)\\s*)?"
                + "(?:(?:flat|smooth|noperspective|centroid|sample)\\s+)*in\\s+[A-Za-z_]\\w*\\s+([A-Za-z_]\\w*)\\s*;");
        String body = declaration.matcher(ShaderSource.maskBlockAndLineComments(source)).replaceAll("");
        var inputs = declaration.matcher(source);
        StringBuilder result = new StringBuilder(source.length());
        while (inputs.find()) {
            boolean used = Pattern.compile("\\b" + Pattern.quote(inputs.group(1)) + "\\b").matcher(body).find();
            inputs.appendReplacement(result, used ? java.util.regex.Matcher.quoteReplacement(inputs.group()) : "");
        }
        inputs.appendTail(result);
        return result.toString();
    }

    public static String preprocess(String file, String source, net.minecraft.client.renderer.ShaderDefines defines)
            throws IOException {
        var macros = new java.util.LinkedHashMap<>(defines.values());
        for (String flag : defines.flags()) macros.put(flag, "1");
        return GlslPreprocessor.process(file, expandPostInterfaces(source), macros);
    }

    public static String prepare(String source) throws IOException {
        String masked = expandPostInterfaces(ShaderSource.maskBlockAndLineComments(source));
        var blocks = BLOCK.matcher(masked);
        StringBuilder result = new StringBuilder(masked.length());
        var instances = new java.util.LinkedHashMap<String, java.util.Set<String>>();
        int copied = 0;
        while (blocks.find()) {
            int close = masked.indexOf('}', blocks.end());
            if (close < 0) throw new IOException("Unterminated uniform block " + blocks.group(1));
            int end = close + 1;
            while (end < masked.length() && Character.isWhitespace(masked.charAt(end))) end++;
            String instance = null;
            if (end < masked.length() && (Character.isLetter(masked.charAt(end)) || masked.charAt(end) == '_')) {
                int start = end++;
                while (end < masked.length() && (Character.isLetterOrDigit(masked.charAt(end)) || masked.charAt(end) == '_')) end++;
                instance = masked.substring(start, end);
                while (end < masked.length() && Character.isWhitespace(masked.charAt(end))) end++;
            }
            // shortcut: Veyra generates flat UBOs; extend this lowering before adding array/struct UBO fields.
            if (end >= masked.length() || masked.charAt(end) != ';')
                throw new IOException("Unsupported uniform block instance declaration");
            result.append(masked, copied, blocks.start());
            String body = masked.substring(blocks.end(), close);
            var fields = FIELD.matcher(body);
            var names = new HashSet<String>();
            int fieldEnd = 0;
            while (fields.find()) {
                if (!body.substring(fieldEnd, fields.start()).isBlank() || !names.add(fields.group(2)))
                    throw new IOException("Unsupported or duplicate field in uniform block " + blocks.group(1));
                result.append("uniform ").append(fields.group(1)).append(' ').append(fields.group(2)).append(";\n");
                fieldEnd = fields.end();
            }
            if (!body.substring(fieldEnd).isBlank() || names.isEmpty())
                throw new IOException("Unsupported or empty uniform block " + blocks.group(1));
            if (instance != null && instances.putIfAbsent(instance, names) != null)
                throw new IOException("Duplicate uniform block instance " + instance);
            copied = end + 1;
        }
        result.append(masked, copied, masked.length());
        String lowered = result.toString();
        for (var entry : instances.entrySet()) for (String field : entry.getValue())
            lowered = lowered.replaceAll("\\b" + Pattern.quote(entry.getKey()) + "\\s*\\.\\s*" + Pattern.quote(field) + "\\b", field);
        // Native 1.21.5 exposes one effective fog range, used for both modern range terms.
        for (String name : new String[]{"FogEnvironmentalStart", "FogRenderDistanceStart"})
            lowered = lowered.replaceAll("\\b" + name + "\\b", "FogStart");
        for (String name : new String[]{"FogEnvironmentalEnd", "FogRenderDistanceEnd", "FogSkyEnd", "FogCloudsEnd"})
            lowered = lowered.replaceAll("\\b" + name + "\\b", "FogEnd");
        return protectParameterDirections(lowered);
    }

    private static String expandPostInterfaces(String source) {
        var definitions = Pattern.compile("(?m)^[\\t ]*#define[\\t ]+"
                + "(VEYRA_INTERNAL_POST_(?:IN|OUT)_\\d+)[\\t ]+"
                + "(?:layout[\\t ]*\\([^\\r\\n)]*\\)|VEYRA_INTERNAL_POST_LOCATION\\(\\d+\\))"
                + "[\\t ]+([^\\r\\n]+)$").matcher(source);
        var declarations = new java.util.LinkedHashMap<String, String>();
        StringBuilder result = new StringBuilder(source.length());
        while (definitions.find()) {
            declarations.put(definitions.group(1), definitions.group(2));
            definitions.appendReplacement(result, "");
        }
        definitions.appendTail(result);
        String expanded = result.toString();
        for (var entry : declarations.entrySet()) {
            // Native conversion assigns linked stage locations and consumes bare in/out declarations.
            expanded = expanded.replaceAll("(?m)^[\\t ]*" + Pattern.quote(entry.getKey()) + "[\\t ]*;",
                    java.util.regex.Matcher.quoteReplacement(entry.getValue() + ";"));
        }
        return expanded;
    }

    public static java.util.Map<String, String> uniformTypes(String... sources) throws IOException {
        var declarations = Pattern.compile("\\buniform\\s+(float|int|vec[234]|ivec[23]|mat4)\\s+([A-Za-z_]\\w*)\\s*;");
        var types = new java.util.LinkedHashMap<String, String>();
        for (String source : sources) {
            var fields = declarations.matcher(source);
            while (fields.find()) {
                String previous = types.putIfAbsent(fields.group(2), fields.group(1));
                if (previous != null && !previous.equals(fields.group(1)))
                    throw new IOException("Conflicting uniform types for " + fields.group(2));
            }
        }
        return java.util.Map.copyOf(types);
    }

    public static Fragment fragment(String source) throws IOException {
        var explicit = Pattern.compile("layout\\s*\\(\\s*location\\s*=\\s*(\\d+)\\s*\\)\\s*out\\s+vec4\\s+[A-Za-z_]\\w*\\s*;").matcher(source);
        var locations = new HashSet<Integer>();
        while (explicit.find()) {
            try {
                if (!locations.add(Integer.parseInt(explicit.group(1)))) throw new IOException("Duplicate fragment output location");
            } catch (NumberFormatException invalid) { throw new IOException("Invalid fragment output location", invalid); }
        }
        var bare = Pattern.compile("(?m)^([\\t ]*)out\\s+vec4\\s+([A-Za-z_]\\w*)\\s*;").matcher(source);
        StringBuilder out = new StringBuilder(source.length());
        int next = 0;
        while (bare.find()) {
            while (locations.contains(next)) next++;
            locations.add(next);
            bare.appendReplacement(out, java.util.regex.Matcher.quoteReplacement(
                    bare.group(1) + "layout(location = " + next++ + ") out vec4 " + bare.group(2) + ";"));
        }
        bare.appendTail(out);
        return new Fragment(out.toString(), !locations.isEmpty());
    }

    public record Fragment(String source, boolean hasOutputs) {
        public String restore(String converted) {
            if (!hasOutputs) return converted;
            String injected = "layout(location = 0) out vec4 fragColor;";
            int start = converted.indexOf(injected);
            if (start < 0) throw new IllegalStateException("Native fragment output header changed");
            return converted.substring(0, start) + converted.substring(start + injected.length());
        }
    }

    private static String protectParameterDirections(String source) throws IOException {
        String prefix = "veyraLegacyParameter";
        if (source.contains(prefix)) throw new IOException("Reserved legacy GLSL parameter alias is already declared");
        StringBuilder out = new StringBuilder(source.length());
        int parentheses = 0;
        boolean directive = false, lineStart = true, used = false;
        for (int offset = 0; offset < source.length();) {
            char c = source.charAt(offset);
            if (lineStart && c == '#') directive = true;
            if (c == '\n') { directive = false; lineStart = true; }
            else if (!Character.isWhitespace(c)) lineStart = false;
            if (!directive && (Character.isLetter(c) || c == '_')) {
                int end = offset + 1;
                while (end < source.length() && (Character.isLetterOrDigit(source.charAt(end)) || source.charAt(end) == '_')) end++;
                String token = source.substring(offset, end);
                if (parentheses > 0 && (token.equals("in") || token.equals("out") || token.equals("inout"))) {
                    out.append(prefix).append(token);
                    used = true;
                } else out.append(token);
                offset = end;
                continue;
            }
            if (!directive) {
                if (c == '(') parentheses++;
                if (c == ')') parentheses--;
            }
            out.append(c);
            offset++;
        }
        return used ? "#define " + prefix + "in in\n#define " + prefix + "out out\n#define " + prefix + "inout inout\n" + out : out.toString();
    }
}
