package dev.veyra.shader;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Builds the Vulkan GLSL interface for a shader-pack compute stage. */
public final class ComputeShaderAdapter {
    private static final Pattern OPAQUE_UNIFORM = Pattern.compile(
            "(?m)^[\\t ]*(?:(readonly|writeonly|coherent|volatile|restrict)\\s+)?uniform\\s+"
                    + "((?:[iu]?sampler|[iu]?image)[A-Za-z0-9_]*)\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*;"
                    + "[\\t ]*(?://[^\\r\\n]*)?(?:\\r?\\n|$)");
    private static final Pattern VERSION = Pattern.compile("(?m)^[\\t ]*#version[^\\r\\n]*(?:\\r?\\n|$)");
    private static final Pattern EXTENSION = Pattern.compile("(?m)^[\\t ]*#extension[^\\r\\n]*(?:\\r?\\n|$)");
    private static final Pattern PRECISION = Pattern.compile(
            "(?m)^[\\t ]*precision\\s+(?:lowp|mediump|highp)\\s+\\w+\\s*;[\\t ]*(?:\\r?\\n|$)");
    private static final Pattern LOCAL_SIZE = Pattern.compile(
            "layout\\s*\\(\\s*local_size_x\\s*=\\s*(\\d+)\\s*,\\s*local_size_y\\s*=\\s*(\\d+)\\s*,\\s*local_size_z\\s*=\\s*(\\d+)\\s*\\)\\s*in\\s*;");
    private static final Pattern WORK_GROUPS_DECLARATION = Pattern.compile(
            "\\bconst\\s+ivec3\\s+workGroups\\s*=\\s*([^;]+);"
    );
    private static final Pattern FIXED_WORK_GROUPS = Pattern.compile(
            "ivec3\\s*\\(\\s*(\\d+)\\s*,\\s*(\\d+)\\s*,\\s*(\\d+)\\s*\\)"
    );

    private ComputeShaderAdapter() { }

    public static AdaptedStage adapt(String fileName, String source,
                                     ShaderPackImageProperties imageProperties) throws IOException {
        // Iris defines this feature macro when the pack uses image.* resources. Veyra's compute
        // path provides the custom-image interface even though the offline preprocessor has no Iris runtime.
        String active = GlslPreprocessor.process(fileName, source, Map.of("IRIS_FEATURE_CUSTOM_IMAGES", "1"));
        active = IrisRenderStages.bindUniform(active, IrisRenderStages.computeStage(), fileName);
        active = UnreachableGlslFunctions.removeUnreachable(active, "main");
        String masked = ShaderSource.maskBlockAndLineComments(active);
        Matcher matcher = OPAQUE_UNIFORM.matcher(masked);
        List<OpaqueDeclaration> declarations = new ArrayList<>();
        while (matcher.find()) {
            declarations.add(new OpaqueDeclaration(matcher.start(), matcher.end(),
                    matcher.group(1) == null ? "" : matcher.group(1), matcher.group(2), matcher.group(3)));
        }

        StringBuilder sourceWithoutOpaque = new StringBuilder(active.length());
        int cursor = 0;
        for (OpaqueDeclaration declaration : declarations) {
            sourceWithoutOpaque.append(active, cursor, declaration.start());
            appendWhitespacePreservingLines(sourceWithoutOpaque, active, declaration.start(), declaration.end());
            cursor = declaration.end();
        }
        sourceWithoutOpaque.append(active, cursor, active.length());
        String referenceSource = ShaderSource.maskBlockAndLineComments(sourceWithoutOpaque.toString());

        Map<String, OpaqueResource> resourcesByName = new LinkedHashMap<>();
        for (OpaqueDeclaration declaration : declarations) {
            if (!Pattern.compile("\\b" + Pattern.quote(declaration.name()) + "\\b")
                    .matcher(referenceSource).find()) continue;
            OpaqueResource previous = resourcesByName.get(declaration.name());
            if (previous != null) {
                if (!previous.glslType().equals(declaration.glslType())) {
                    throw new IOException(fileName + " declares opaque uniform '" + declaration.name()
                            + "' with conflicting types.");
                }
                continue;
            }
            boolean storage = declaration.glslType().contains("image");
            String imageName = storage ? declaration.name() : imageProperties.images().values().stream()
                    .filter(image -> image.sampler().equals(declaration.name()))
                    .map(ShaderPackImageProperties.Image::name).findFirst().orElse(null);
            if (imageName == null) {
                throw new IOException(fileName + " uses sampler '" + declaration.name()
                        + "' without a matching shaders.properties image.* declaration.");
            }
            ShaderPackImageProperties.Image image = imageProperties.images().get(imageName);
            if (image == null) {
                throw new IOException(fileName + " uses storage image '" + declaration.name()
                        + "' without a matching shaders.properties image.* declaration.");
            }
            boolean shader3d = declaration.glslType().endsWith("3D");
            if (shader3d != image.is3d()) {
                throw new IOException(fileName + " uniform '" + declaration.name() + "' has GLSL type "
                        + declaration.glslType() + " but image." + imageName + " is "
                        + (image.is3d() ? "3D" : "2D") + ".");
            }
            String format = "";
            if (storage) {
                format = image.internalFormat().toLowerCase(java.util.Locale.ROOT);
                image.vulkanFormat(); // Fail at shader load time when the storage format has no Vulkan mapping.
            }
            resourcesByName.put(declaration.name(), new OpaqueResource(declaration.name(),
                    declaration.glslType(), declaration.qualifier(), imageName, storage, format, -1));
        }

        List<OpaqueResource> resources = new ArrayList<>();
        int nextBinding = 1; // Binding zero is the combined compute uniform block.
        for (OpaqueResource resource : resourcesByName.values()) {
            resources.add(resource.withBinding(nextBinding++));
        }

        Map<String, OpaqueResource> resourcesByNameWithBindings = new HashMap<>();
        resources.forEach(resource -> resourcesByNameWithBindings.put(resource.name(), resource));
        StringBuilder withLayouts = new StringBuilder(active.length() + 512);
        cursor = 0;
        for (OpaqueDeclaration declaration : declarations) {
            withLayouts.append(active, cursor, declaration.start());
            OpaqueResource resource = resourcesByNameWithBindings.get(declaration.name());
            if (resource == null) {
                appendWhitespacePreservingLines(withLayouts, active, declaration.start(), declaration.end());
            } else {
                withLayouts.append("layout(set = 0, binding = ").append(resource.binding());
                if (resource.storageImage()) withLayouts.append(", ").append(resource.format());
                withLayouts.append(") ");
                if (!declaration.qualifier().isEmpty()) withLayouts.append(declaration.qualifier()).append(' ');
                withLayouts.append("uniform ").append(declaration.glslType()).append(' ')
                        .append(declaration.name()).append(';');
                if (active.substring(declaration.start(), declaration.end()).endsWith("\r\n")) withLayouts.append("\r\n");
                else if (active.substring(declaration.start(), declaration.end()).endsWith("\n")) withLayouts.append('\n');
            }
            cursor = declaration.end();
        }
        withLayouts.append(active, cursor, active.length());

        IrisFogUniformAdapter.AdaptedShader fogStage = IrisFogUniformAdapter.extract(
                withLayouts.toString(), fileName, true);
        ShaderPackUniformAdapter.AdaptedShader uniformStage = ShaderPackUniformAdapter.extract(
                fogStage.source(), fileName);
        Map<String, ShaderPackUniformAdapter.UniformField> uniformFields = new LinkedHashMap<>();
        fogStage.fields().forEach(field -> uniformFields.put(field.name(), field));
        for (ShaderPackUniformAdapter.UniformField field : uniformStage.fields()) {
            ShaderPackUniformAdapter.UniformField previous = uniformFields.putIfAbsent(field.name(), field);
            if (previous != null && !previous.type().equals(field.type())) {
                throw new IOException(fileName + " declares uniform '" + field.name()
                        + "' with conflicting compute types.");
            }
        }
        String fogSource = ShaderSource.maskBlockAndLineComments(fogStage.source());
        addLegacyFogField(uniformFields, fogSource, "FogColor", "vec4", fileName);
        addLegacyFogField(uniformFields, fogSource, "FogEnvironmentalStart", "float", fileName);
        addLegacyFogField(uniformFields, fogSource, "FogEnvironmentalEnd", "float", fileName);
        Matcher localSizeMatcher = LOCAL_SIZE.matcher(ShaderSource.maskBlockAndLineComments(uniformStage.source()));
        if (!localSizeMatcher.find()) {
            throw new IOException(fileName + " must declare fixed local_size_x, local_size_y, and local_size_z values.");
        }
        int[] localSize = {
                positiveInt(fileName, "local_size_x", localSizeMatcher.group(1)),
                positiveInt(fileName, "local_size_y", localSizeMatcher.group(2)),
                positiveInt(fileName, "local_size_z", localSizeMatcher.group(3))
        };
        Matcher workGroupsMatcher = WORK_GROUPS_DECLARATION.matcher(
                ShaderSource.maskBlockAndLineComments(uniformStage.source()));
        int[] workGroups = {0, 0, 0};
        if (workGroupsMatcher.find()) {
            String declaration = workGroupsMatcher.group(1);
            Matcher fixed = FIXED_WORK_GROUPS.matcher(declaration);
            if (!fixed.matches() || workGroupsMatcher.find()) {
                throw new IOException(fileName + " must declare at most one fixed literal const ivec3 workGroups.");
            }
            workGroups = new int[] {
                    positiveInt(fileName, "workGroups.x", fixed.group(1)),
                    positiveInt(fileName, "workGroups.y", fixed.group(2)),
                    positiveInt(fileName, "workGroups.z", fixed.group(3))
            };
        }

        StringBuilder block = new StringBuilder();
        if (!uniformFields.isEmpty()) {
            block.append("layout(std140, set = 0, binding = 0) uniform VeyraComputeUniforms {\n");
            for (ShaderPackUniformAdapter.UniformField field : uniformFields.values()) {
                block.append("    ").append(field.type()).append(' ').append(field.name()).append(";\n");
            }
            block.append("};\n");
        }
        String body = VERSION.matcher(uniformStage.source()).replaceAll("");
        body = EXTENSION.matcher(body).replaceAll("");
        body = PRECISION.matcher(body).replaceAll("");
        if (uniformFields.isEmpty()) {
            // VulkanMod indexes layout bindings into a buffer sized by descriptor count: no unused UBO slot.
            body = Pattern.compile("layout\\(set = 0, binding = (\\d+)").matcher(body)
                    .replaceAll(match -> "layout(set = 0, binding = " + (Integer.parseInt(match.group(1)) - 1));
            resources.replaceAll(resource -> new OpaqueResource(resource.name(), resource.glslType(), resource.qualifier(),
                    resource.imageName(), resource.storageImage(), resource.format(), resource.binding() - 1));
        }
        String adapted = "#version 450\n" + block + fogStage.bridge() + uniformStage.macros() + body;
        return new AdaptedStage(adapted, List.copyOf(uniformFields.values()), List.copyOf(resources),
                localSize[0], localSize[1], localSize[2], workGroups[0], workGroups[1], workGroups[2]);
    }

    private static void addLegacyFogField(Map<String, ShaderPackUniformAdapter.UniformField> fields,
                                          String source, String name, String type, String fileName)
            throws IOException {
        if (!Pattern.compile("\\b" + Pattern.quote(name) + "\\b").matcher(source).find()) return;
        ShaderPackUniformAdapter.UniformField previous = fields.putIfAbsent(name,
                new ShaderPackUniformAdapter.UniformField(type, name));
        if (previous != null && !previous.type().equals(type)) {
            throw new IOException(fileName + " uses " + name + " with an incompatible uniform type.");
        }
    }

    private static int positiveInt(String file, String field, String value) throws IOException {
        int parsed;
        try {
            parsed = Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            throw new IOException(file + " has invalid " + field + "=" + value + ".", exception);
        }
        if (parsed <= 0) throw new IOException(file + " has non-positive " + field + ".");
        return parsed;
    }

    private static void appendWhitespacePreservingLines(StringBuilder target, String source, int start, int end) {
        for (int i = start; i < end; i++) {
            char character = source.charAt(i);
            target.append(character == '\r' || character == '\n' ? character : ' ');
        }
    }

    private record OpaqueDeclaration(int start, int end, String qualifier, String glslType, String name) { }

    public record OpaqueResource(String name, String glslType, String qualifier, String imageName,
                                 boolean storageImage, String format, int binding) {
        private OpaqueResource withBinding(int value) {
            return new OpaqueResource(name, glslType, qualifier, imageName, storageImage, format, value);
        }
    }

    public record AdaptedStage(String source, List<ShaderPackUniformAdapter.UniformField> uniforms,
                               List<OpaqueResource> resources, int localSizeX, int localSizeY, int localSizeZ,
                               int workGroupsX, int workGroupsY, int workGroupsZ) {
        public AdaptedStage {
            uniforms = List.copyOf(uniforms);
            resources = List.copyOf(resources);
        }

        public boolean hasFixedWorkGroups() {
            return workGroupsX > 0 && workGroupsY > 0 && workGroupsZ > 0;
        }
    }
}
