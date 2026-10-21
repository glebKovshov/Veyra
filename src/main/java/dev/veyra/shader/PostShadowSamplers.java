package dev.veyra.shader;

import java.io.IOException;
import java.util.*;
import java.util.regex.Pattern;

/** Post-stage bindings for the shadow producer's existing images. */
public final class PostShadowSamplers {
    public enum Input {
        DEPTH("VeyraShadowDepth", false, "shadowtex0"),
        OPAQUE_DEPTH("VeyraShadowOpaqueDepth", false, "shadowtex1"),
        COLOR("VeyraShadowColor", true, "shadowcolor0"),
        COLOR1("VeyraShadowColor1", true, "shadowcolor1");
        private final String name;
        private final boolean bilinear;
        private final String shaderName;
        Input(String name, boolean bilinear, String shaderName) {
            this.name = name;
            this.bilinear = bilinear;
            this.shaderName = shaderName;
        }
        public String inputName() { return name; }
        public String samplerName() { return name + "Sampler"; }
        public boolean bilinear() { return bilinear; }
        public int shadowColorIndex() { return this == COLOR ? 0 : this == COLOR1 ? 1 : -1; }
    }
    public record Adapted(String source, Set<Input> inputs) { }
    private static final Map<String, Input> NAMES = Map.of("shadowtex0", Input.DEPTH,
            "shadowtex1", Input.OPAQUE_DEPTH, "shadowcolor0", Input.COLOR,
            "shadowcolor1", Input.COLOR1, "shadowcolor", Input.COLOR);
    private static final Pattern DECLARATION = Pattern.compile("(?m)^[\\t ]*uniform\\s+(?:(?:lowp|mediump|highp)\\s+)?(\\w+)\\s+(shadow(?:tex\\w*|color\\w*|))\\s*;[\\t ]*");
    private static final Pattern REFERENCE = Pattern.compile("\\bshadow(?:tex\\w*|color\\w*)\\b");
    private PostShadowSamplers() { }

    public static Adapted adapt(String source, boolean available) throws IOException {
        String active = UnusedSamplerDeclarations.removeUnused(ShaderSource.maskBlockAndLineComments(source));
        EnumSet<Input> inputs = EnumSet.noneOf(Input.class);
        var references = REFERENCE.matcher(active);
        while (references.find()) {
            String name = references.group();
            Input input = NAMES.get(name);
            if (input == null) throw new IOException("Unsupported post shadow sampler: " + name);
            inputs.add(input);
        }
        if (!inputs.isEmpty() && !available) throw new IOException("Post shadow inputs require a supported shadow pass.");
        Set<String> comparisons = new HashSet<>();
        var comparisonNames = Pattern.compile("\\bsampler2DShadow\\s+(\\w+)").matcher(active);
        while (comparisonNames.find()) comparisons.add(comparisonNames.group(1));
        var declarations = DECLARATION.matcher(active);
        Set<String> seen = new HashSet<>();
        StringBuffer body = new StringBuffer();
        while (declarations.find()) {
            String name = declarations.group(2), type = declarations.group(1);
            Input input = NAMES.get(name);
            if (input == null) throw new IOException("Unsupported post shadow sampler: " + name);
            if (!type.equals("sampler2D") && !(input.shadowColorIndex() < 0 && type.equals("sampler2DShadow")))
                throw new IOException(name + " has an unsupported sampler type: " + type);
            if (!seen.add(name)) throw new IOException(name + " is declared more than once.");
            declarations.appendReplacement(body, "");
        }
        declarations.appendTail(body);
        String result = body.toString();
        if (inputs.contains(Input.DEPTH) || inputs.contains(Input.OPAQUE_DEPTH)) {
            for (String comparison : comparisons) result = result.replaceAll(
                    "\\b(?:texture2D|texture)\\s*\\(\\s*" + Pattern.quote(comparison) + "\\s*,", "veyraPostShadowCompare(" + comparison + ",");
            result = result.replaceAll("\\bsampler2DShadow\\b", "sampler2D")
                    .replaceAll("\\btexture2DShadow\\s*\\(", "veyraPostShadowCompare(")
                    .replaceAll("\\bshadow2D\\s*\\(", "veyraPostShadowSample(")
                    .replaceAll("\\b(?:texture2D|texture)\\s*\\(", "veyraSample(");
        }
        return new Adapted(result, Collections.unmodifiableSet(inputs));
    }

    public static void validateBudget(Set<Input> inputs, int otherSamplers) throws IOException {
        if (otherSamplers + inputs.size() > 12) throw new IOException("Post program exceeds VulkanMod's twelve texture descriptor slots.");
    }

    public static boolean bilinear(String inputName) {
        for (Input input : Input.values()) if (input.inputName().equals(inputName)) return input.bilinear();
        return false;
    }

    public static String bridge(Set<Input> inputs) {
        StringBuilder bridge = new StringBuilder();
        if (inputs.contains(Input.DEPTH) || inputs.contains(Input.OPAQUE_DEPTH)) bridge.append("""
                vec4 veyraSample(sampler2D s, vec2 uv) { return texture(s, uv); }
                vec4 veyraSample(sampler2D s, vec2 uv, float bias) { return texture(s, uv, bias); }
                float veyraPostShadowCompare(sampler2D s, vec3 uv) { return step(uv.z-0.0005, texture(s,uv.xy).r); }
                float veyraPostShadowCompare(sampler2D s, vec3 uv, float bias) { return step(uv.z-0.0005,texture(s,uv.xy,bias).r); }
                vec4 veyraPostShadowSample(sampler2D s, vec3 uv, float bias) { return vec4(veyraPostShadowCompare(s,uv,bias)); }
                vec4 veyraPostShadowSample(sampler2D s, vec3 uv) { return vec4(veyraPostShadowCompare(s,uv)); }
                vec4 veyraSample(sampler2D s, vec3 uv) { return veyraPostShadowSample(s,uv); }
                vec4 veyraSample(sampler2D s, vec3 uv, float bias) { return vec4(step(uv.z-0.0005,textureLod(s,uv.xy,bias).r)); }
                """);
        for (Input input : Input.values()) if (inputs.contains(input))
            bridge.append("uniform sampler2D ").append(input.samplerName()).append(";\n");
        for (Input input : Input.values()) {
            if (!inputs.contains(input)) continue;
            bridge.append("#define ").append(input.shaderName).append(' ').append(input.samplerName()).append('\n');
            if (input == Input.COLOR) bridge.append("#define shadowcolor ").append(input.samplerName()).append('\n');
        }
        return bridge.toString();
    }
}
