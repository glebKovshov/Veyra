package dev.veyra.vulkan;

import java.util.List;
import net.vulkanmod.vulkan.shader.descriptor.UBO;
import net.vulkanmod.vulkan.shader.layout.AlignedStruct;
import net.vulkanmod.vulkan.shader.layout.Uniform;

/**
 * VulkanMod 0.6.7 has the 0.6.x uniform blocks; Minecraft 1.21.1 describes pipelines with
 * dev.veyra.compat, whose flattened fields reach the shader instance after both stages are read.
 */
public final class VeyraUniformBlocks {
    private VeyraUniformBlocks() { }
    public static List<UBO> buffers(net.vulkanmod.vulkan.shader.Pipeline pipeline) {
        return ((dev.veyra.mixin.accessor.NativePipelineAccessor) pipeline).veyra$getUniformBuffers();
    }
    public static void useGlobal(UBO block) { block.setUseGlobalBuffer(true); }
    public static boolean isLoose(UBO block) { return block.name.equals("UBO 0"); }
    public static Uniform.Info field(String type, String name) { return Uniform.createUniformInfo(type, name); }
    public static com.mojang.blaze3d.pipeline.RenderPipeline.Builder declare(
            com.mojang.blaze3d.pipeline.RenderPipeline.Builder builder, String name) {
        return builder;
    }
    public static void copyTo(com.mojang.blaze3d.pipeline.RenderPipeline.Builder builder,
                              com.mojang.blaze3d.pipeline.RenderPipeline.UniformDescription uniform) {
        builder.withUniform(uniform.name(), uniform.type());
    }
    public static String description(UBO block) { return block.name; }
    public static UBO create(String name, int binding, int stages, List<Uniform.Info> fields) {
        var builder = AlignedStruct.builder();
        fields.forEach(builder::addUniform);
        UBO block = builder.buildUBO(name, binding, stages);
        block.setUseGlobalBuffer(true);
        return block;
    }
}
