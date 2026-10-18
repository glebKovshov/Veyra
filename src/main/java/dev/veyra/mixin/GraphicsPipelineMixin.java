package dev.veyra.mixin;

import dev.veyra.mixin.accessor.PipelineStateAccessor;
import dev.veyra.vulkan.VeyraGeometryTargets;
import dev.veyra.vulkan.VeyraMrtFramebuffer;
import net.vulkanmod.vulkan.shader.GraphicsPipeline;
import net.vulkanmod.vulkan.shader.PipelineState;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkPipelineColorBlendAttachmentState;
import org.lwjgl.vulkan.VkPipelineColorBlendStateCreateInfo;
import org.lwjgl.vulkan.VkPipelineRenderingCreateInfoKHR;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.nio.IntBuffer;

@Mixin(value = GraphicsPipeline.class, remap = false)
public abstract class GraphicsPipelineMixin {
    @Redirect(method = "createGraphicsPipeline", at = @At(value = "INVOKE", target =
            "Lorg/lwjgl/vulkan/VkPipelineColorBlendStateCreateInfo;pAttachments(Lorg/lwjgl/vulkan/VkPipelineColorBlendAttachmentState$Buffer;)Lorg/lwjgl/vulkan/VkPipelineColorBlendStateCreateInfo;"))
    private VkPipelineColorBlendStateCreateInfo veyra$colorAttachments(VkPipelineColorBlendStateCreateInfo info,
            VkPipelineColorBlendAttachmentState.Buffer original, PipelineState state) {
        var pass = ((PipelineStateAccessor) state).veyra$getRenderPass();
        if (pass == null || !(pass.getFramebuffer() instanceof VeyraMrtFramebuffer target)) return info.pAttachments(original);
        var attachments = VkPipelineColorBlendAttachmentState.calloc(target.colors().size(), MemoryStack.stackGet());
        var layout = VeyraGeometryTargets.layout((GraphicsPipeline) (Object) this);
        for (int i = 0; i < attachments.capacity(); i++) {
            attachments.get(i).set(original.get(0));
            if (layout != null && !layout.writes(i)) attachments.get(i).colorWriteMask(0);
        }
        return info.pAttachments(attachments);
    }

    @Redirect(method = "createGraphicsPipeline", at = @At(value = "INVOKE", target =
            "Lorg/lwjgl/vulkan/VkPipelineRenderingCreateInfoKHR;pColorAttachmentFormats(Ljava/nio/IntBuffer;)Lorg/lwjgl/vulkan/VkPipelineRenderingCreateInfoKHR;"))
    private VkPipelineRenderingCreateInfoKHR veyra$attachmentFormats(VkPipelineRenderingCreateInfoKHR info,
            IntBuffer original, PipelineState state) {
        var pass = ((PipelineStateAccessor) state).veyra$getRenderPass();
        if (pass == null || !(pass.getFramebuffer() instanceof VeyraMrtFramebuffer target)) return info.pColorAttachmentFormats(original);
        var formats = MemoryStack.stackGet().mallocInt(target.colors().size());
        target.colors().forEach(image -> formats.put(image.format));
        return info.pColorAttachmentFormats(formats.flip());
    }
}
