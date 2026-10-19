package dev.veyra.shader;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Normalizes supported Iris uniforms for VulkanMod geometry pipelines. */
public final class ShaderPackUniformAdapter {
    private static final List<String> MATRIX_UNIFORMS = List.of(
            "gbufferModelView", "gbufferModelViewInverse", "gbufferProjection", "gbufferProjectionInverse",
            "gbufferPreviousModelView", "gbufferPreviousProjection", "shadowModelView", "shadowModelViewInverse",
            "shadowProjection", "shadowProjectionInverse");
    private static final Pattern UNIFORM_DECLARATION = Pattern.compile(
            "(?m)^[\\t ]*uniform\\s+(?:(?:lowp|mediump|highp)\\s+)?([A-Za-z_][A-Za-z0-9_]*)\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*;[\\t ]*(?://[^\\r\\n]*)?(?:\\r?\\n|$)");

    private ShaderPackUniformAdapter() {
    }

    public static AdaptedShader extract(String source, String programName) throws IOException {
        Matcher matcher = UNIFORM_DECLARATION.matcher(source);
        StringBuffer withoutUniforms = new StringBuffer();
        StringBuilder declarations = new StringBuilder();
        List<UniformField> fields = new ArrayList<>();
        StringBuilder macros = new StringBuilder();
        Map<String, String> declaredUniforms = new HashMap<>();
        Map<String, TerrainShaderAdapter.ShaderUniform> supportedUniforms = new HashMap<>();
        TerrainShaderAdapter.shaderPackUniforms().forEach(uniform -> supportedUniforms.put(uniform.name(), uniform));

        while (matcher.find()) {
            String type = matcher.group(1);
            String name = matcher.group(2);
            TerrainShaderAdapter.ShaderUniform uniform = supportedUniforms.get(name);
            boolean matrixUniform = uniform == null && type.equals("mat4") && MATRIX_UNIFORMS.contains(name);
            if (uniform == null && !matrixUniform) {
                throw new IOException(TerrainShaderAdapter.unsupportedUniformMessage(programName, name));
            }
            if (uniform != null && !uniform.accepts(type)) {
                throw new IOException(programName + " uniform '" + name + "' must use "
                        + uniform.sourceTypeDescription() + ".");
            }
            if (declaredUniforms.putIfAbsent(name, type) != null) {
                throw new IOException(programName + " declares uniform '" + name + "' more than once.");
            }

            if (matrixUniform) {
                declarations.append("uniform mat4 ").append(name).append(";\n");
                fields.add(new UniformField("mat4", name));
            } else {
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

    public static List<String> matrixUniforms() {
        return MATRIX_UNIFORMS;
    }
}
