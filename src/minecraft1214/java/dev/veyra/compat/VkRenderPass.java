package dev.veyra.compat;

import com.mojang.blaze3d.shaders.Uniform;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.veyra.vulkan.VeyraNoiseTexture;
import net.vulkanmod.vulkan.shader.GraphicsPipeline;
import net.vulkanmod.vulkan.shader.descriptor.ImageDescriptor;
import net.vulkanmod.vulkan.texture.VTextureSelector;
import org.joml.Matrix4f;

/**
 * The draw being set up. Before 1.21.5 there is no render pass object: inputs are render system
 * state, so binding here writes that state for the pipeline about to be bound.
 */
public final class VkRenderPass implements RenderPass {
    private static final java.util.Set<String> SAMPLED_ATTACHMENTS =
            java.util.Set.of("Sampler1Sampler", "VeyraDepthTex1Sampler", "VeyraDepthTex2Sampler");
    private RenderPipeline pipeline;

    VkRenderPass() { }

    void setPipeline(RenderPipeline pipeline) { this.pipeline = pipeline; }

    @Override public RenderPipeline getPipeline() { return pipeline; }

    @Override public void bindSampler(String name, GpuTexture texture) {
        GraphicsPipeline nativePipeline = pipeline == null ? null : pipeline.getPipeline();
        if (nativePipeline == null) return;
        ImageDescriptor descriptor = null;
        for (ImageDescriptor candidate : nativePipeline.getImageDescriptors()) {
            if (candidate.name.equals(name)) { descriptor = candidate; break; }
        }
        if (descriptor == null) return;
        if (texture == null) {
            if (descriptor.imageIdx >= 0 && descriptor.imageIdx < dev.veyra.vulkan.VeyraTextureSlots.CAPACITY) RenderSystem.setShaderTexture(descriptor.imageIdx, 0);
            return;
        }
        VkGpuTexture vulkan = (VkGpuTexture) texture;
        if (name.equals(VeyraNoiseTexture.SAMPLER_NAME) && VeyraNoiseTexture.hasTexture()) {
            VeyraNoiseTexture.sampler().apply(texture);
        }
        if (SAMPLED_ATTACHMENTS.contains(name)) {
            // Attachments sampled here are also written during the frame; keep VulkanMod from
            // treating the descriptor as a permanently read-only image.
            descriptor.setLayout(org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL);
            descriptor.isReadOnlyLayout = false;
        }
        vulkan.flushModeChanges();
        // VulkanMod rebinds descriptor slots from render system state when the pipeline is bound.
        if (descriptor.imageIdx >= 0 && descriptor.imageIdx < dev.veyra.vulkan.VeyraTextureSlots.CAPACITY) {
            RenderSystem.setShaderTexture(descriptor.imageIdx, vulkan.glId());
        }
        VTextureSelector.bindTexture(descriptor.imageIdx, vulkan.getVulkanImage());
    }

    @Override public void setUniform(String name, float... values) {
        Uniform uniform = uniform(name);
        if (uniform != null) uniform.set(values);
    }

    @Override public void setUniform(String name, int... values) {
        Uniform uniform = uniform(name);
        if (uniform == null) return;
        switch (values.length) {
            case 1 -> uniform.set(values[0]);
            case 2 -> uniform.set(values[0], values[1]);
            case 3 -> uniform.set(values[0], values[1], values[2]);
            default -> uniform.set(values[0], values[1], values[2], values[3]);
        }
    }

    @Override public void setUniform(String name, Matrix4f matrix) {
        Uniform uniform = uniform(name);
        if (uniform != null) uniform.set(matrix);
    }

    private Uniform uniform(String name) {
        EGlProgram program = pipeline == null ? null : pipeline.getProgram();
        return program == null ? null : program.getUniform(name);
    }

    @Override public void close() { }
}
