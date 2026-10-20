package dev.veyra.shader;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
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

    private static final Map<String, String> UNBOUND_ENTITY_VALUES = Map.of(
            "entityId", "int", "blockEntityId", "int", "currentRenderedItemId", "int", "entityColor", "vec4");
    private static final Pattern ENTITY_ATTRIBUTE_PROGRAM = Pattern.compile(
            "gbuffers_(?:entities\\w*|block\\w*|hand\\w*|spidereyes|armor_glint|text\\w*)|.*sign_text.*");
    private static final Pattern CURRENT_RENDERED_ITEM_PROGRAM = Pattern.compile(
            "gbuffers_(?:entities\\w*|block\\w*|hand\\w*|spidereyes|armor_glint|text\\w*|textured(?:_lit)?)|.*sign_text.*|shadow_(?:entities|block)");

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
            String unbound = unboundEntityDeclaration(programName, type, name);
            if (unbound != null) {
                matcher.appendReplacement(withoutUniforms, "");
                macros.append(unbound);
                continue;
            }
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

    /**
     * Veyra binds entity and block-entity identifiers in entity-family programs and resolves
     * currentRenderedItemId from the ItemStack submitted by item, armor, hand, and shadow renderers. Programs
     * outside those routes receive Iris-compatible fallbacks: blockEntityId and currentRenderedItemId
     * are -1 and entityColor is vec4(0). entityId follows CapturedRenderingState, which entity rendering
     * resets to 0 after every entity, so programs outside that path read 0 in a populated world.
     */
    public static boolean isUnboundEntityIdentifier(String programName, String type, String name) {
        if (!type.equals(UNBOUND_ENTITY_VALUES.get(name))) {
            return false;
        }
        Pattern boundPrograms = name.equals("currentRenderedItemId")
                ? CURRENT_RENDERED_ITEM_PROGRAM : ENTITY_ATTRIBUTE_PROGRAM;
        return !boundPrograms.matcher(programName).matches();
    }

    /** True when Veyra replaces this identifier with a per-render entity, block, or item constant. */
    public static boolean isBoundEntityIdentifier(String programName, String type, String name) {
        if (!type.equals(UNBOUND_ENTITY_VALUES.get(name))) {
            return false;
        }
        Pattern boundPrograms = name.equals("currentRenderedItemId")
                ? CURRENT_RENDERED_ITEM_PROGRAM : ENTITY_ATTRIBUTE_PROGRAM;
        return (name.equals("entityId") || name.equals("blockEntityId")
                || name.equals("currentRenderedItemId"))
                && boundPrograms.matcher(programName).matches();
    }

    /** The Iris fallback constant for an entity uniform outside entity programs, or null when Iris binds it. */
    public static String unboundEntityDeclaration(String programName, String type, String name) {
        if (!isUnboundEntityIdentifier(programName, type, name)) {
            return null;
        }
        String value = switch (name) {
            case "blockEntityId", "currentRenderedItemId" -> "-1";
            case "entityColor" -> "vec4(0.0)";
            default -> "0";
        };
        return "const " + type + " " + name + " = " + value + ";\n";
    }

    public static List<String> matrixUniforms() {
        return MATRIX_UNIFORMS;
    }

    /** Merges Iris built-ins and pack uniforms without creating duplicate uniform-block members. */
    static List<String> mergeUniformNames(List<String> builtInUniforms,
                                          List<CustomUniforms.Uniform> customUniforms) {
        if (customUniforms.isEmpty()) {
            return builtInUniforms;
        }
        LinkedHashSet<String> names = new LinkedHashSet<>(builtInUniforms);
        customUniforms.stream().map(CustomUniforms.Uniform::name).forEach(names::add);
        return List.copyOf(names);
    }
}
