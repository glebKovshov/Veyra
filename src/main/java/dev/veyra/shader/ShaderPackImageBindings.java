package dev.veyra.shader;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/** Links shaderpack custom images across graphics stages without consuming Minecraft texture slots. */
final class ShaderPackImageBindings {
    private static final String MEMORY = "(?:readonly|writeonly|coherent|volatile|restrict)";
    private static final Pattern UNIFORM = Pattern.compile(
            "(?m)^[\\t ]*(?:layout\\s*\\([^)]*\\)\\s*)?((?:" + MEMORY + "\\s+)*)uniform\\s+"
                    + "((?:" + MEMORY + "\\s+)*)((?:[iu]?sampler|[iu]?image)[A-Za-z0-9_]*)\\s+"
                    + "([A-Za-z_][A-Za-z0-9_]*)\\s*;[\\t ]*(?://[^\\r\\n]*)?(?:\\r?\\n|$)");
    private static final Pattern VERSION = Pattern.compile("(?m)^[\\t ]*#version[^\\r\\n]*(?:\\r?\\n|$)");

    private ShaderPackImageBindings() { }

    static PreparedStage prepare(String file, String source, ShaderPackImageProperties properties) throws IOException {
        String directivesSelected = RenderTargetDirectives.keepActiveComments(file, source);
        String active = GlslPreprocessor.process(file, directivesSelected, Map.of(
                "IRIS_FEATURE_CUSTOM_IMAGES", "1", "IRIS_FEATURE_COMPUTE_SHADERS", "1"));
        active = UnreachableGlslFunctions.removeUnreachable(active, "main");
        var declarations = UNIFORM.matcher(ShaderSource.maskBlockAndLineComments(active));
        List<Declaration> custom = new ArrayList<>();
        while (declarations.find()) {
            String type = declarations.group(3), name = declarations.group(4);
            boolean storage = type.contains("image");
            String imageName = storage ? name : properties.images().values().stream()
                    .filter(image -> image.sampler().equals(name)).map(ShaderPackImageProperties.Image::name)
                    .findFirst().orElse(null);
            if (imageName == null) continue; // Native texture/sampler contracts belong to the existing adapter.
            String qualifiers = (declarations.group(1) + declarations.group(2)).strip().replaceAll("\\s+", " ");
            custom.add(new Declaration(declarations.start(), declarations.end(),
                    new ComputeShaderAdapter.OpaqueResource(name, type, qualifiers, imageName, storage, "", -1)));
        }
        StringBuilder stripped = new StringBuilder(active.length());
        int cursor = 0;
        for (Declaration declaration : custom) {
            stripped.append(active, cursor, declaration.start());
            for (int index = declaration.start(); index < declaration.end(); index++) {
                char character = active.charAt(index);
                stripped.append(character == '\n' || character == '\r' ? character : ' ');
            }
            cursor = declaration.end();
        }
        stripped.append(active, cursor, active.length());
        String references = ShaderSource.maskBlockAndLineComments(stripped.toString());
        Map<String, ComputeShaderAdapter.OpaqueResource> resources = new LinkedHashMap<>();
        for (Declaration declaration : custom) {
            var resource = declaration.resource();
            if (!Pattern.compile("\\b" + Pattern.quote(resource.name()) + "\\b").matcher(references).find()) continue;
            var image = properties.images().get(resource.imageName());
            if (image == null) throw new IOException(file + " uses storage image '" + resource.name()
                    + "' without image." + resource.imageName() + ".");
            image.vulkanFormat();
            boolean integer = image.internalFormat().toLowerCase(Locale.ROOT).endsWith("ui");
            String expected = (integer ? "u" : "") + (resource.storageImage() ? "image" : "sampler") + (image.is3d() ? "3D" : "2D");
            if (!resource.glslType().equals(expected)) throw new IOException(file + " uniform '" + resource.name()
                    + "' must use " + expected + " for image." + resource.imageName() + " (" + image.internalFormat() + ").");
            resource = new ComputeShaderAdapter.OpaqueResource(resource.name(), resource.glslType(), resource.qualifier(),
                    resource.imageName(), resource.storageImage(), resource.storageImage() ? image.internalFormat().toLowerCase(Locale.ROOT) : "", -1);
            var previous = resources.putIfAbsent(resource.name(), resource);
            if (previous != null && (!compatible(previous, resource) || !previous.qualifier().equals(resource.qualifier()))) {
                throw new IOException(file + " has conflicting custom image declarations for '" + resource.name() + "'.");
            }
        }
        return new PreparedStage(stripped.toString(), List.copyOf(resources.values()));
    }

    static Plan plan(int firstBinding, PreparedStage... stages) throws IOException {
        if (firstBinding < 0) throw new IllegalArgumentException("Custom-image bindings must be non-negative.");
        Map<String, ComputeShaderAdapter.OpaqueResource> resources = new LinkedHashMap<>();
        for (PreparedStage stage : stages) {
            for (var resource : stage.resources()) {
                var previous = resources.get(resource.name());
                if (previous != null) {
                    if (!compatible(previous, resource)) throw new IOException("Graphics stages disagree on custom image '" + resource.name() + "'.");
                    continue;
                }
                resources.put(resource.name(), new ComputeShaderAdapter.OpaqueResource(resource.name(), resource.glslType(),
                        resource.qualifier(), resource.imageName(), resource.storageImage(), resource.format(),
                        firstBinding + resources.size()));
            }
        }
        return new Plan(resources);
    }

    private static boolean compatible(ComputeShaderAdapter.OpaqueResource first, ComputeShaderAdapter.OpaqueResource second) {
        return first.glslType().equals(second.glslType()) && first.imageName().equals(second.imageName())
                && first.storageImage() == second.storageImage() && first.format().equals(second.format());
    }

    record PreparedStage(String source, List<ComputeShaderAdapter.OpaqueResource> resources) { }

    record Plan(Map<String, ComputeShaderAdapter.OpaqueResource> resources) {
        Plan { resources = Collections.unmodifiableMap(new LinkedHashMap<>(resources)); }

        String decorate(String adapted, PreparedStage stage) throws IOException {
            if (stage.resources().isEmpty()) return adapted;
            int insertion = declarationInsertion(adapted);
            StringBuilder uniforms = new StringBuilder();
            for (var stageResource : stage.resources()) {
                var bound = resources.get(stageResource.name());
                if (bound == null || !compatible(bound, stageResource)) throw new IOException("Missing custom-image binding for " + stageResource.name() + ".");
                uniforms.append("layout(set = 0, binding = ").append(bound.binding());
                if (stageResource.storageImage()) uniforms.append(", ").append(stageResource.format());
                uniforms.append(") ");
                if (!stageResource.qualifier().isEmpty()) uniforms.append(stageResource.qualifier()).append(' ');
                uniforms.append("uniform ").append(stageResource.glslType()).append(' ').append(stageResource.name()).append(";\n");
            }
            return adapted.substring(0, insertion) + uniforms + adapted.substring(insertion);
        }
    }

    /** Adds sampler declarations for Minecraft RenderPipeline's native sampler linker. */
    static String decorateNativeSamplers(String adapted, PreparedStage stage) throws IOException {
        if (stage.resources().isEmpty()) return adapted;
        int insertion = declarationInsertion(adapted);
        StringBuilder uniforms = new StringBuilder();
        for (var resource : stage.resources()) {
            if (resource.storageImage()) {
                throw new IOException("Native RenderPipeline does not support graphics storage image '"
                        + resource.name() + "'.");
            }
            if (!resource.qualifier().isEmpty()) {
                throw new IOException("Native RenderPipeline does not support sampler qualifiers on '"
                        + resource.name() + "'.");
            }
            // VulkanMod 0.6.8 only recognizes sampler2D while assigning native descriptor bindings.
            // The parser leaves these directives intact, so shaderc sees the real sampler type.
            uniforms.append("#define sampler2D ").append(resource.glslType()).append('\n')
                    .append("uniform sampler2D ").append(resource.name()).append(";\n")
                    .append("#undef sampler2D\n");
        }
        return adapted.substring(0, insertion) + uniforms + adapted.substring(insertion);
    }

    private static int declarationInsertion(String adapted) throws IOException {
        var version = VERSION.matcher(adapted);
        if (!version.find()) throw new IOException("The adapted graphics stage has no #version directive.");
        int insertion = version.end();
        String masked = ShaderSource.maskBlockAndLineComments(adapted);
        while (insertion < adapted.length()) {
            int end = adapted.indexOf('\n', insertion);
            end = end < 0 ? adapted.length() : end + 1;
            String line = masked.substring(insertion, end).strip();
            if (!line.isEmpty() && !line.startsWith("#extension")) break;
            insertion = end;
        }
        return insertion;
    }

    private record Declaration(int start, int end, ComputeShaderAdapter.OpaqueResource resource) { }
}
