package dev.veyra.shader;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Moves supported Iris scalar uniforms into VulkanMod's generated global UBO. */
public final class ShaderPackUniformAdapter {
    private static final Pattern UNIFORM_DECLARATION = Pattern.compile(
            "(?m)^[\\t ]*uniform\\s+(?:(?:lowp|mediump|highp)\\s+)?([A-Za-z_][A-Za-z0-9_]*)\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*;[\\t ]*(?://[^\\r\\n]*)?(?:\\r?\\n|$)");
    private static final Map<String, TerrainShaderAdapter.ShaderUniform> SUPPORTED_UNIFORMS =
            TerrainShaderAdapter.shaderPackUniforms().stream()
                    .collect(java.util.stream.Collectors.toUnmodifiableMap(
                            TerrainShaderAdapter.ShaderUniform::name, uniform -> uniform));

    private ShaderPackUniformAdapter() {
    }

    public static AdaptedShader extract(String source, String programName) throws IOException {
        Matcher matcher = UNIFORM_DECLARATION.matcher(source);
        StringBuffer withoutUniforms = new StringBuffer();
        StringBuilder declarations = new StringBuilder();
        List<UniformField> fields = new ArrayList<>();
        StringBuilder macros = new StringBuilder();
        Map<String, String> declaredUniforms = new HashMap<>();

        while (matcher.find()) {
            String type = matcher.group(1);
            String name = matcher.group(2);
            TerrainShaderAdapter.ShaderUniform uniform = SUPPORTED_UNIFORMS.get(name);
            if (uniform == null) {
                throw new IOException(programName + " custom uniform '" + name + "' is not supported yet.");
            }
            if (!uniform.accepts(type)) {
                throw new IOException(programName + " uniform '" + name + "' must use "
                        + uniform.sourceTypeDescription() + ".");
            }
            if (declaredUniforms.putIfAbsent(name, type) != null) {
                throw new IOException(programName + " declares uniform '" + name + "' more than once.");
            }

            String bufferName = uniform.booleanField() && type.equals("bool")
                    ? uniform.blockMemberName()
                    : name;
            declarations.append("uniform ").append(uniform.bufferType()).append(' ')
                    .append(bufferName).append(";\n");
            fields.add(new UniformField(uniform.bufferType(), bufferName));
            if (uniform.booleanField() && type.equals("bool")) {
                macros.append("#define ").append(name).append(" (")
                        .append(bufferName).append(" != 0)\n");
            }
            matcher.appendReplacement(withoutUniforms, "");
        }
        matcher.appendTail(withoutUniforms);
        return new AdaptedShader(withoutUniforms.toString(), declarations.toString(),
                List.copyOf(fields), macros.toString());
    }

    public record AdaptedShader(String source, String declarations, List<UniformField> fields, String macros) {
    }

    public record UniformField(String type, String name) {
    }
}
