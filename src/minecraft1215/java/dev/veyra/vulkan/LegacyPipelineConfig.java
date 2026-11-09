package dev.veyra.vulkan;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import net.vulkanmod.vulkan.shader.Pipeline;
import net.vulkanmod.vulkan.shader.layout.Uniform;

/** Keeps explicit Veyra bindings while reading VulkanMod 0.5.6's native JSON schema. */
public final class LegacyPipelineConfig {
    public final Map<String, String> shaderPaths;
    public final List<UB> ubs;
    public final List<ImageDescriptorInfo> imageDescriptors;
    public final JsonArray pushConstantsInfo;

    public LegacyPipelineConfig(Map<String, String> shaderPaths, List<UB> ubs,
                                List<ImageDescriptorInfo> images, JsonArray pushConstantsInfo) {
        this.shaderPaths = Map.copyOf(shaderPaths);
        this.ubs = List.copyOf(ubs);
        this.imageDescriptors = List.copyOf(images);
        this.pushConstantsInfo = pushConstantsInfo.deepCopy();
        var bindings = new HashSet<Integer>();
        for (UB block : ubs) requireBinding(bindings, block.binding);
        for (ImageDescriptorInfo image : images) requireBinding(bindings, image.binding());
    }

    private static void requireBinding(java.util.Set<Integer> bindings, int binding) {
        if (binding < 0 || !bindings.add(binding))
            throw new IllegalArgumentException("Invalid or duplicate pipeline binding " + binding);
    }

    public static Builder builder() { return new Builder(); }

    public static LegacyPipelineConfig fromJson(String name, JsonObject json) {
        if (json == null) throw new IllegalArgumentException("Missing pipeline config " + name);
        if (json.has("ManualUBOs")) throw new IllegalArgumentException("Manual UBO config is not supported: " + name);
        var builder = builder();
        if (json.has("UBOs")) for (var node : json.getAsJsonArray("UBOs")) {
            JsonObject block = node.getAsJsonObject();
            var ub = UB.builder(block.get("binding").getAsInt(),
                    Pipeline.Builder.getStageFromString(block.get("type").getAsString()));
            for (var field : block.getAsJsonArray("fields")) {
                JsonObject value = field.getAsJsonObject();
                String type = value.get("type").getAsString();
                int count = value.get("count").getAsInt();
                if ((type.equals("int") && count != 1) || (type.equals("matrix4x4") && count != 16))
                    throw new IllegalArgumentException("Unsupported uniform count in " + name + ": " + value);
                var info = Uniform.createUniformInfo(type, value.get("name").getAsString(), count);
                ub.addUniform(info.type, info.name);
            }
            builder.addUB(ub.build());
        }
        int nextBinding = builder.ubs.stream().mapToInt(block -> block.binding + 1).max().orElse(0);
        if (json.has("samplers")) for (var node : json.getAsJsonArray("samplers")) {
            String sampler = node.getAsJsonObject().get("name").getAsString();
            builder.addImageDescriptor(nextBinding++, "sampler2D", sampler,
                    nativeTextureSlot(sampler));
        }
        if (json.has("PushConstants")) builder.setPushConstants(json.getAsJsonArray("PushConstants"));
        Map<String, String> paths = json.has("vertex") && json.has("fragment")
                ? Map.of("vertex", json.get("vertex").getAsString(), "fragment", json.get("fragment").getAsString())
                : Map.of();
        return new LegacyPipelineConfig(paths, builder.ubs, builder.images, builder.pushConstants);
    }

    static int nativeTextureSlot(String name) {
        // VTextureSelector's class initializer allocates a Vulkan image; config parsing must stay CPU-only.
        if (name.equals("DiffuseSampler")) return 0;
        if (name.matches("Sampler[0-7]")) return name.charAt(7) - '0';
        throw new IllegalArgumentException("Unknown native sampler " + name);
    }

    public record Field(String type, String name) {
        public Field {
            if (name == null || !name.matches("[A-Za-z_][A-Za-z0-9_]*"))
                throw new IllegalArgumentException("Invalid uniform name " + name);
            Uniform.createUniformInfo(nativeType(type), name);
        }
        static String nativeType(String type) {
            return switch (type) { case "ivec2" -> "vec2"; case "ivec3" -> "vec3"; default -> type; };
        }
    }

    public static final class UB {
        public final int binding;
        public final int stage;
        public final List<Field> fields;
        private UB(int binding, int stage, List<Field> fields) {
            if (binding < 0 || stage == 0) throw new IllegalArgumentException("Invalid uniform block binding/stages");
            this.binding = binding;
            this.stage = stage;
            this.fields = List.copyOf(fields);
        }
        public static Builder builder(int binding, int stage) { return new Builder(binding, stage); }
        public static final class Builder {
            private final int binding, stage;
            private final List<Field> fields = new ArrayList<>();
            private Builder(int binding, int stage) { this.binding = binding; this.stage = stage; }
            public Builder addUniform(String type, String name) {
                if (fields.stream().anyMatch(field -> field.name().equals(name)))
                    throw new IllegalArgumentException("Duplicate uniform " + name);
                fields.add(new Field(type, name));
                return this;
            }
            public UB build() { return new UB(binding, stage, fields); }
        }
    }

    public record ImageDescriptorInfo(int binding, String type, String name, int imageIdx) {
        public ImageDescriptorInfo {
            if (binding < 0 || imageIdx < 0 || name == null || name.isBlank()
                    || !(type.equals("sampler2D") || type.equals("image2D")))
                throw new IllegalArgumentException("Invalid image descriptor " + name);
        }
    }

    public static final class Builder {
        private final List<UB> ubs = new ArrayList<>();
        private final List<ImageDescriptorInfo> images = new ArrayList<>();
        private JsonArray pushConstants = new JsonArray();
        public Builder addUB(UB block) { ubs.add(block); return this; }
        public Builder setPushConstants(JsonArray fields) { pushConstants = fields.deepCopy(); return this; }
        public Builder addImageDescriptor(int binding, String type, String name, int slot) {
            images.add(new ImageDescriptorInfo(binding, type, name, slot));
            return this;
        }
        public LegacyPipelineConfig build() { return new LegacyPipelineConfig(Map.of(), ubs, images, pushConstants); }
    }
}
