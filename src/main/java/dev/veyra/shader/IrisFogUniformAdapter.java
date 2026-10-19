package dev.veyra.shader;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Maps Iris' common fog uniforms to the environmental fog values exposed by Minecraft. */
final class IrisFogUniformAdapter {
    private static final Pattern UNIFORM_DECLARATION = Pattern.compile(
            "(?m)^[\\t ]*uniform\\s+(?:(?:lowp|mediump|highp)\\s+)?([A-Za-z_][A-Za-z0-9_]*)\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*;[\\t ]*(?://[^\\r\\n]*)?(?:\\r?\\n|$)");
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

    private IrisFogUniformAdapter() {
    }

    static AdaptedShader extract(String source, String programName) throws IOException {
        return extract(source, programName, false);
    }

    static AdaptedShader extract(String source, String programName, boolean fogValuesAlreadyDeclared)
            throws IOException {
        Matcher matcher = UNIFORM_DECLARATION.matcher(source);
        StringBuilder remaining = new StringBuilder(source.length());
        StringBuilder macros = new StringBuilder();
        List<ShaderPackUniformAdapter.UniformField> fields = new ArrayList<>();
        Set<String> declaredUniforms = new HashSet<>();
        int cursor = 0;
        boolean usesFog = false;
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
            return new AdaptedShader(source, "", List.of());
        }
        remaining.append(source, cursor, source.length());
        return new AdaptedShader(remaining.toString(),
                (fogValuesAlreadyDeclared ? "" : FOG_BLOCK) + macros, List.copyOf(fields));
    }

    record AdaptedShader(String source, String bridge, List<ShaderPackUniformAdapter.UniformField> fields) {
    }

    private record FogUniform(String type, String expression, String bufferType, String bufferField) {
    }
}
