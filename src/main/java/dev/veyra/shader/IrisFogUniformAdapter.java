package dev.veyra.shader;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Maps Iris fog uniforms and supported legacy fog members to Minecraft's fog UBO. */
final class IrisFogUniformAdapter {
    private static final Pattern UNIFORM_DECLARATION = Pattern.compile(
            "(?m)^[\\t ]*uniform\\s+(?:(?:lowp|mediump|highp)\\s+)?([A-Za-z_][A-Za-z0-9_]*)\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*;[\\t ]*(?://[^\\r\\n]*)?(?:\\r?\\n|$)");
    private static final Pattern LEGACY_FOG_MEMBER = Pattern.compile(
            "\\bgl_Fog\\s*\\.\\s*([A-Za-z_][A-Za-z0-9_]*)\\b");
    // GLSL compatibility defines scale as 1 / (end - start); return zero for a degenerate range.
    private static final String LEGACY_FOG_SCALE = "(abs(FogEnvironmentalEnd - FogEnvironmentalStart) > 0.000001"
            + " ? 1.0 / (FogEnvironmentalEnd - FogEnvironmentalStart) : 0.0)";
    private static final Map<String, FogUniform> UNIFORMS = Map.of(
            "fogColor", new FogUniform("vec3", "(FogColor.rgb)", "vec4", "FogColor"),
            "fogStart", new FogUniform("float", "FogEnvironmentalStart", "float", "FogEnvironmentalStart"),
            "fogEnd", new FogUniform("float", "FogEnvironmentalEnd", "float", "FogEnvironmentalEnd"));
    private static final String FOG_BLOCK = """
            layout(std140) uniform Fog {
                vec4 FogColor;
                float FogEnvironmentalStart;
                float FogEnvironmentalEnd;
                float FogRenderDistanceStart;
                float FogRenderDistanceEnd;
                float FogSkyEnd;
                float FogCloudsEnd;
            };
            """;
    private static final Pattern MINECRAFT_FOG_IMPORT = Pattern.compile(
            "(?m)^[\\t ]*#[\\t ]*moj_import[\\t ]*<minecraft:fog\\.glsl>[\\t ]*(?:\\r?\\n|$)");
    private static final Pattern FOG_BLOCK_DECLARATION = Pattern.compile(
            "(?is)\\buniform\\s+Fog\\s*\\{[^}]*\\bvec4\\s+FogColor\\s*;");
    private static final Pattern FOG_FUNCTION_DECLARATION = Pattern.compile(
            "\\b[A-Za-z_][A-Za-z0-9_]*\\s+Fog\\s*\\(");
    private static final Pattern FOG_FUNCTION_REFERENCE = Pattern.compile("\\bFog\\b(?=\\s*\\()");

    private IrisFogUniformAdapter() {
    }

    static boolean nativeFogValuesDeclared(String source) {
        if (source == null || source.isEmpty()) return false;
        String uncommented = ShaderSource.maskBlockAndLineComments(source);
        return MINECRAFT_FOG_IMPORT.matcher(uncommented).find()
                || FOG_BLOCK_DECLARATION.matcher(uncommented).find();
    }

    static AdaptedShader extract(String source, String programName) throws IOException {
        return extract(source, programName, false);
    }

    static AdaptedShader extract(String source, String programName, boolean fogValuesAlreadyDeclared)
            throws IOException {
        LegacyFogRewrite legacyFog = rewriteLegacyFog(source, programName);
        source = legacyFog.source();
        Matcher matcher = UNIFORM_DECLARATION.matcher(source);
        StringBuilder remaining = new StringBuilder(source.length());
        StringBuilder macros = new StringBuilder();
        List<ShaderPackUniformAdapter.UniformField> fields = new ArrayList<>();
        Set<String> declaredUniforms = new HashSet<>();
        int cursor = 0;
        boolean usesFog = legacyFog.used();
        while (matcher.find()) {
            FogUniform fogUniform = UNIFORMS.get(matcher.group(2));
            if (fogUniform == null) {
                continue;
            }
            String type = matcher.group(1);
            String name = matcher.group(2);
            if (!fogUniform.type().equals(type)) {
                throw new IOException(programName + " uniform '" + name + "' must use " + fogUniform.type() + ".");
            }
            if (!declaredUniforms.add(name)) {
                throw new IOException(programName + " declares uniform '" + name + "' more than once.");
            }
            remaining.append(source, cursor, matcher.start());
            cursor = matcher.end();
            macros.append("#define ").append(name).append(' ')
                    .append(fogUniform.expression()).append('\n');
            fields.add(new ShaderPackUniformAdapter.UniformField(
                    fogUniform.bufferType(), fogUniform.bufferField()));
            usesFog = true;
        }
        if (!usesFog) {
            String adaptedSource = fogValuesAlreadyDeclared
                    ? renameFogFunctionThatConflictsWithUniformBlock(source) : source;
            return new AdaptedShader(adaptedSource, "", List.of(), Set.of());
        }
        remaining.append(source, cursor, source.length());
        String adaptedSource = ShaderPackLocalUniformRenamer.renameShadowingLocals(
                remaining.toString(), declaredUniforms.stream().sorted().toList());
        adaptedSource = renameFogFunctionThatConflictsWithUniformBlock(adaptedSource);
        return new AdaptedShader(adaptedSource,
                (fogValuesAlreadyDeclared ? "" : FOG_BLOCK) + macros,
                List.copyOf(fields), Set.copyOf(declaredUniforms));
    }

    private static String renameFogFunctionThatConflictsWithUniformBlock(String source) {
        String analysis = ShaderSource.maskBlockAndLineComments(source);
        if (!FOG_FUNCTION_DECLARATION.matcher(analysis).find()) return source;

        String aliasBase = "veyraShaderPackFog";
        String alias = aliasBase;
        int suffix = 2;
        while (Pattern.compile("\\b" + Pattern.quote(alias) + "\\b").matcher(analysis).find()) {
            alias = aliasBase + "_" + suffix++;
        }

        List<Integer> offsets = new ArrayList<>();
        Matcher references = FOG_FUNCTION_REFERENCE.matcher(analysis);
        while (references.find()) offsets.add(references.start());
        StringBuilder renamed = new StringBuilder(source);
        for (int index = offsets.size() - 1; index >= 0; index--) {
            int start = offsets.get(index);
            renamed.replace(start, start + "Fog".length(), alias);
        }
        return renamed.toString();
    }

    private static LegacyFogRewrite rewriteLegacyFog(String source, String programName) throws IOException {
        Matcher matcher = LEGACY_FOG_MEMBER.matcher(ShaderSource.maskBlockAndLineComments(source));
        if (!matcher.find()) return new LegacyFogRewrite(source, false);

        StringBuilder rewritten = new StringBuilder(source.length());
        boolean used = false;
        int cursor = 0;
        do {
            String member = matcher.group(1);
            String replacement = switch (member) {
                case "color" -> "FogColor";
                case "start" -> "FogEnvironmentalStart";
                case "end" -> "FogEnvironmentalEnd";
                case "scale" -> LEGACY_FOG_SCALE;
                default -> throw new IOException(programName + " uses unsupported legacy fog member 'gl_Fog."
                        + member + "'; Veyra currently maps color, start, end, and scale to Minecraft's fog UBO.");
            };
            rewritten.append(source, cursor, matcher.start()).append(replacement);
            cursor = matcher.end();
            used = true;
        } while (matcher.find());
        rewritten.append(source.substring(cursor));
        return new LegacyFogRewrite(rewritten.toString(), used);
    }

    record AdaptedShader(String source, String bridge, List<ShaderPackUniformAdapter.UniformField> fields,
                         Set<String> aliasedUniforms) {
    }

    private record LegacyFogRewrite(String source, boolean used) {
    }

    private record FogUniform(String type, String expression, String bufferType, String bufferField) {
    }
}
