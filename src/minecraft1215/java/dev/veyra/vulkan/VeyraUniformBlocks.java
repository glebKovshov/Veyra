package dev.veyra.vulkan;

import java.util.List;
import net.vulkanmod.vulkan.shader.descriptor.UBO;
import net.vulkanmod.vulkan.shader.layout.AlignedStruct;
import net.vulkanmod.vulkan.shader.layout.Uniform;

public final class VeyraUniformBlocks {
    private VeyraUniformBlocks() { }
    public static List<UBO> buffers(net.vulkanmod.vulkan.shader.Pipeline pipeline) {
        return ((dev.veyra.mixin.accessor.NativePipelineAccessor) pipeline).veyra$getUniformBuffers();
    }
    public static void useGlobal(UBO block) {
        if (!(block instanceof VeyraStorageDescriptor)) block.setUniformBuffer(null);
    }
    public static boolean isLoose(UBO block) { return block.getBinding() == 0; }
    public static Uniform.Info field(String type, String name) {
        return Uniform.createUniformInfo(switch (type) {
            case "ivec2" -> "vec2";
            case "ivec3" -> "vec3";
            default -> type;
        }, name);
    }
    public static com.mojang.blaze3d.pipeline.RenderPipeline.Builder declare(
            com.mojang.blaze3d.pipeline.RenderPipeline.Builder builder, String name) {
        // The native converter supplies the flattened fields to EGlProgram after reading both shader stages.
        return builder;
    }
    public static void copyTo(com.mojang.blaze3d.pipeline.RenderPipeline.Builder builder,
                              com.mojang.blaze3d.pipeline.RenderPipeline.UniformDescription uniform) {
        builder.withUniform(uniform.name(), uniform.type());
    }
    public static String description(UBO block) { return "UBO binding " + block.getBinding(); }
    public static UBO create(String name, int binding, int stages, List<Uniform.Info> fields) {
        var builder = new AlignedStruct.Builder();
        fields.forEach(builder::addUniformInfo);
        // A null per-block buffer selects VulkanMod's global, frame-managed uniform buffer.
        return builder.buildUBO(binding, stages);
    }
}
