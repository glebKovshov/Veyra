package dev.veyra.mixin;

import dev.veyra.mixin.accessor.PipelineStateAccessor;
import dev.veyra.vulkan.VeyraMrtFramebuffer;
import net.vulkanmod.vulkan.shader.GraphicsPipeline;
import net.vulkanmod.vulkan.shader.PipelineState;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkPipelineRenderingCreateInfoKHR;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import java.nio.IntBuffer;

/** Attachment formats for VulkanMod's dynamic-rendering backend. */
@Mixin(value = GraphicsPipeline.class, remap = false)
abstract class DynamicGraphicsPipelineMixin {
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
