package dev.veyra.shader;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Routes Iris raw custom textures into post-processing stages. A raw texture replaces a sampler name only in
 * programs that declare that name with a sampler type able to read it; others keep the built-in binding, so
 * Photon's deferred1 reads 3D cloud noise as colortex6 while deferred4 still reads its colortex6 target.
 * Matching declarations are renamed so the built-in colortex/depthtex handling never sees them.
 */
public final class ShaderPackRawTextures {
    private static final String PREFIX = "veyraRawTexture_";
    private static final Pattern VERSION = Pattern.compile("(?m)^[\\t ]*#version[^\\r\\n]*(?:\\r?\\n|$)");

    /** A renamed sampler uniform and the raw texture behind it. */
    public record Binding(String sampler, String uniform, String glslType, RawTextureSpec spec) { }

    public record Prepared(String source, List<Binding> bindings) {
        public static Prepared none(String source) { return new Prepared(source, List.of()); }
    }

    private ShaderPackRawTextures() { }

    public static Prepared prepare(String programName, String source,
                                   ShaderPackCustomTextureProperties properties) throws IOException {
        Map<String, RawTextureSpec> raw = properties.rawTexturesForProgram(programName);
        if (raw.isEmpty()) return Prepared.none(source);
        String result = source;
        List<Binding> bindings = new ArrayList<>();
        for (Map.Entry<String, RawTextureSpec> texture : raw.entrySet()) {
            String sampler = texture.getKey();
            RawTextureSpec spec = texture.getValue();
            Pattern declaration = Pattern.compile("(?m)^[\\t ]*uniform\\s+(?:(?:lowp|mediump|highp)\\s+)?("
                    + "sampler[23]D)\\s+" + Pattern.quote(sampler) + "\\s*;");
            Matcher matcher = declaration.matcher(ShaderSource.maskBlockAndLineComments(result));
            StringBuilder stripped = new StringBuilder(result.length());
            int cursor = 0;
            boolean matched = false;
            while (matcher.find()) {
                if (!matcher.group(1).equals(spec.samplerType())) continue;
                matched = true;
                stripped.append(result, cursor, matcher.start());
                cursor = matcher.end();
            }
            if (!matched) continue;
            stripped.append(result, cursor, result.length());
            String uniform = PREFIX + sampler;
            result = Pattern.compile("\\b" + Pattern.quote(sampler) + "\\b").matcher(stripped.toString())
                    .replaceAll(Matcher.quoteReplacement(uniform));
            bindings.add(new Binding(sampler, uniform, spec.samplerType(), spec));
        }
        return new Prepared(result, List.copyOf(bindings));
    }

    /**
     * Declares the renamed samplers. VulkanMod's GLSL parser only assigns descriptors to sampler2D and does not
     * expand macros, so a 3D sampler is declared through a sampler2D macro that shaderc expands to sampler3D.
     */
    public static String decorate(String adapted, List<Binding> bindings) throws IOException {
        if (bindings.isEmpty()) return adapted;
        Matcher version = VERSION.matcher(adapted);
        if (!version.find()) throw new IOException("The adapted stage has no #version directive.");
        int insertion = version.end();
        String masked = ShaderSource.maskBlockAndLineComments(adapted);
        while (insertion < adapted.length()) {
            int end = adapted.indexOf('\n', insertion);
            end = end < 0 ? adapted.length() : end + 1;
            String line = masked.substring(insertion, end).strip();
            if (!line.isEmpty() && !line.startsWith("#extension")) break;
            insertion = end;
        }
        StringBuilder declarations = new StringBuilder();
        for (Binding binding : bindings) {
            if (binding.glslType().equals("sampler2D")) {
                declarations.append("uniform sampler2D ").append(binding.uniform()).append(";\n");
            } else {
                declarations.append("#define sampler2D ").append(binding.glslType()).append('\n')
                        .append("uniform sampler2D ").append(binding.uniform()).append(";\n")
                        .append("#undef sampler2D\n");
            }
        }
        return adapted.substring(0, insertion) + declarations + adapted.substring(insertion);
    }

    /** Union of the bindings of a program's stages, by renamed uniform. */
    public static List<Binding> merge(List<Binding> first, List<Binding> second) {
        Map<String, Binding> merged = new LinkedHashMap<>();
        first.forEach(binding -> merged.put(binding.uniform(), binding));
        second.forEach(binding -> merged.putIfAbsent(binding.uniform(), binding));
        return List.copyOf(merged.values());
    }
}
