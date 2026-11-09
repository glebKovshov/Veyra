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
    public static void useGlobal(UBO block) { block.setUseGlobalBuffer(true); }
    public static boolean isLoose(UBO block) { return block.name.equals("UBO 0"); }
    public static Uniform.Info field(String type, String name) { return Uniform.createUniformInfo(type, name); }
    public static com.mojang.blaze3d.pipeline.RenderPipeline.Builder declare(
            com.mojang.blaze3d.pipeline.RenderPipeline.Builder builder, String name) {
        return builder.withUniform(name, com.mojang.blaze3d.shaders.UniformType.UNIFORM_BUFFER);
    }
    public static void copyTo(com.mojang.blaze3d.pipeline.RenderPipeline.Builder builder,
                              com.mojang.blaze3d.pipeline.RenderPipeline.UniformDescription uniform) {
        if (uniform.type() == com.mojang.blaze3d.shaders.UniformType.TEXEL_BUFFER)
            builder.withUniform(uniform.name(), uniform.type(), uniform.textureFormat());
        else builder.withUniform(uniform.name(), uniform.type());
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
