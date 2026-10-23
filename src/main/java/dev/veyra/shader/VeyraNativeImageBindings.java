package dev.veyra.shader;

import dev.veyra.vulkan.VeyraImageDescriptor;
import net.vulkanmod.vulkan.shader.descriptor.ImageDescriptor;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Bridges active shader-pack storage images through VulkanMod's sampler-only GLSL parser. */
public final class VeyraNativeImageBindings {
    private static final ThreadLocal<VeyraNativeImageBindings> ACTIVE = new ThreadLocal<>();

    private final VeyraComputePipelineSet customImages;
    private final Map<String, ComputeShaderAdapter.OpaqueResource> storageImages;
    private final Map<String, Integer> descriptorBindings = new HashMap<>();
    private final Set<String> restoredDeclarations = new LinkedHashSet<>();

    private VeyraNativeImageBindings(VeyraComputePipelineSet customImages,
                                     Map<String, ComputeShaderAdapter.OpaqueResource> storageImages) {
        this.customImages = customImages;
        this.storageImages = Collections.unmodifiableMap(new LinkedHashMap<>(storageImages));
    }

    /** Installs parser substitutions only for the duration of synchronous RenderPipeline compilation. */
    static Scope install(VeyraComputePipelineSet customImages,
                         ShaderPackImageBindings.PreparedStage... stages) throws IOException {
        var plan = ShaderPackImageBindings.plan(0, stages);
        Map<String, ComputeShaderAdapter.OpaqueResource> storageImages = new LinkedHashMap<>();
        for (var resource : plan.resources().values()) {
            if (resource.storageImage()) storageImages.put(resource.name(), resource);
        }
        if (storageImages.isEmpty()) return Scope.noop();
        if (customImages == null) {
            throw new IOException("Native graphics storage images require the selected shader-pack image resources.");
        }

        VeyraNativeImageBindings previous = ACTIVE.get();
        VeyraNativeImageBindings installed = new VeyraNativeImageBindings(customImages, storageImages);
        ACTIVE.set(installed);
        return new Scope(previous, installed);
    }

    public static VeyraNativeImageBindings active() {
        return ACTIVE.get();
    }

    /** Converts the parser's sampler placeholders into Veyra-owned storage-image descriptors. */
    public List<ImageDescriptor> replaceSamplerDescriptors(List<ImageDescriptor> parserDescriptors) throws IOException {
        List<ImageDescriptor> replacements = new ArrayList<>(parserDescriptors);
        for (int index = 0; index < replacements.size(); index++) {
            ImageDescriptor parserDescriptor = replacements.get(index);
            ComputeShaderAdapter.OpaqueResource resource = storageImages.get(parserDescriptor.name);
            if (resource == null) continue;
            if (parserDescriptor.isStorageImage()) {
                throw new IOException("VulkanMod unexpectedly parsed storage image '" + resource.name()
                        + "' without Veyra's sampler placeholder.");
            }
            Integer previousBinding = descriptorBindings.putIfAbsent(resource.name(), parserDescriptor.getBinding());
            if (previousBinding != null && previousBinding != parserDescriptor.getBinding()) {
                throw new IOException("VulkanMod assigned multiple descriptor bindings to storage image '"
                        + resource.name() + "'.");
            }
            var boundResource = new ComputeShaderAdapter.OpaqueResource(resource.name(), resource.glslType(),
                    resource.qualifier(), resource.imageName(), true, resource.format(), parserDescriptor.getBinding());
            VeyraImageDescriptor descriptor = customImages.graphicsImageDescriptor(boundResource);
            replacements.set(index, descriptor);
        }

        for (String name : storageImages.keySet()) {
            if (!descriptorBindings.containsKey(name)) {
                throw new IOException("VulkanMod did not create a native sampler placeholder for storage image '"
                        + name + "'.");
            }
        }
        return replacements;
    }

    /** Restores image declarations after VulkanMod has assigned its native descriptor bindings. */
    public String restoreOutput(String parserOutput) throws IOException {
        RestoredOutput restored = restoreStorageImageDeclarations(parserOutput, storageImages, descriptorBindings);
        restoredDeclarations.addAll(restored.names());
        return restored.source();
    }

    void verifyComplete() throws IOException {
        Set<String> missing = new LinkedHashSet<>(storageImages.keySet());
        missing.removeAll(restoredDeclarations);
        if (!missing.isEmpty()) {
            throw new IOException("VulkanMod did not emit restored GLSL declarations for graphics storage images "
                    + missing + ".");
        }
    }

    static RestoredOutput restoreStorageImageDeclarations(
            String parserOutput,
            Map<String, ComputeShaderAdapter.OpaqueResource> storageImages,
            Map<String, Integer> descriptorBindings) throws IOException {
        String source = parserOutput;
        Set<String> restoredNames = new LinkedHashSet<>();
        for (var resource : storageImages.values()) {
            if (!resource.storageImage()) continue;
            Integer expectedBinding = descriptorBindings.get(resource.name());
            if (expectedBinding == null) {
                throw new IOException("No VulkanMod descriptor binding was recorded for storage image '"
                        + resource.name() + "'.");
            }
            Pattern placeholder = Pattern.compile("layout\\s*\\(\\s*binding\\s*=\\s*(\\d+)\\s*\\)\\s*"
                    + "uniform\\s+sampler2D\\s+" + Pattern.quote(resource.name()) + "\\s*;");
            Matcher matcher = placeholder.matcher(source);
            StringBuffer rewritten = new StringBuffer(source.length());
            boolean found = false;
            while (matcher.find()) {
                int actualBinding;
                try {
                    actualBinding = Integer.parseInt(matcher.group(1));
                } catch (NumberFormatException exception) {
                    throw new IOException("VulkanMod emitted an invalid binding for storage image '"
                            + resource.name() + "'.", exception);
                }
                if (actualBinding != expectedBinding) {
                    throw new IOException("VulkanMod's GLSL output binding for storage image '" + resource.name()
                            + "' differs from its descriptor binding.");
                }
                String qualifiers = resource.qualifier().isEmpty() ? "" : resource.qualifier() + " ";
                String declaration = "layout(set = 0, binding = " + expectedBinding + ", " + resource.format()
                        + ") " + qualifiers + "uniform " + resource.glslType() + " " + resource.name() + ";";
                matcher.appendReplacement(rewritten, Matcher.quoteReplacement(declaration));
                found = true;
            }
            matcher.appendTail(rewritten);
            if (found) {
                source = rewritten.toString();
                restoredNames.add(resource.name());
            }
        }
        return new RestoredOutput(source, restoredNames);
    }

    record RestoredOutput(String source, Set<String> names) {
        RestoredOutput {
            names = Collections.unmodifiableSet(new LinkedHashSet<>(names));
        }
    }

    static final class Scope implements AutoCloseable {
        private final VeyraNativeImageBindings previous;
        private final VeyraNativeImageBindings installed;
        private boolean closed;

        private Scope(VeyraNativeImageBindings previous, VeyraNativeImageBindings installed) {
            this.previous = previous;
            this.installed = installed;
        }

        private static Scope noop() {
            return new Scope(null, null);
        }

        void verifyComplete() throws IOException {
            if (installed != null) installed.verifyComplete();
        }

        @Override
        public void close() {
            if (closed) return;
            closed = true;
            if (installed == null) return;
            if (ACTIVE.get() != installed) {
                throw new IllegalStateException("Native storage-image parser scopes were closed out of order.");
            }
            if (previous == null) ACTIVE.remove();
            else ACTIVE.set(previous);
        }
    }
}
