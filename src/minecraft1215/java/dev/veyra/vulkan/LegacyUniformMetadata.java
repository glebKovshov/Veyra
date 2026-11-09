package dev.veyra.vulkan;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.shaders.UniformType;
import java.util.ArrayList;
import java.util.Map;
import net.vulkanmod.render.engine.EGlProgram;
import net.vulkanmod.vulkan.shader.descriptor.UBO;

/** Installs real per-field CPU storage before VulkanMod connects native UBO suppliers. */
public final class LegacyUniformMetadata {
    private LegacyUniformMetadata() { }
    public static void install(EGlProgram program, UBO block, Map<String, String> types) {
        var missing = new ArrayList<RenderPipeline.UniformDescription>();
        for (var field : block.getUniforms()) {
            String type = types.get(field.getName());
            if (type == null) throw new IllegalStateException("Missing source type for native uniform " + field.getName());
            UniformType storage = storageType(type);
            var existing = program.getUniform(field.getName());
            if (existing == null) missing.add(new RenderPipeline.UniformDescription(field.getName(), storage));
            else if (existing.getType() != storage)
                throw new IllegalStateException("Native uniform storage differs for " + field.getName());
        }
        if (!missing.isEmpty()) program.setupUniforms(missing, java.util.List.of());
    }
    static UniformType storageType(String type) {
        return switch (type) {
            case "float" -> UniformType.FLOAT;
            case "int" -> UniformType.INT;
            // The old API has no IVEC2; native vec2 fields copy only the first two words of this CPU storage.
            case "ivec2", "ivec3" -> UniformType.IVEC3;
            case "vec2" -> UniformType.VEC2;
            case "vec3" -> UniformType.VEC3;
            case "vec4" -> UniformType.VEC4;
            case "mat4" -> UniformType.MATRIX4X4;
            default -> throw new IllegalArgumentException("Unsupported legacy uniform type " + type);
        };
    }
}
