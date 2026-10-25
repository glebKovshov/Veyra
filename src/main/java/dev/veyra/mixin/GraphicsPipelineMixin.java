package dev.veyra.mixin;

import dev.veyra.mixin.accessor.PipelineStateAccessor;
import dev.veyra.VeyraClient;
import dev.veyra.vulkan.VeyraDeviceFeatures;
import dev.veyra.vulkan.VeyraGeometryTargets;
import dev.veyra.vulkan.VeyraMrtFramebuffer;
import dev.veyra.vulkan.VeyraPipelinePassCache;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import net.vulkanmod.vulkan.Vulkan;
import net.vulkanmod.vulkan.framebuffer.RenderPass;
import net.vulkanmod.vulkan.shader.GraphicsPipeline;
import net.vulkanmod.vulkan.shader.PipelineState;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkPipelineColorBlendAttachmentState;
import org.lwjgl.vulkan.VkPipelineColorBlendStateCreateInfo;
import org.lwjgl.vulkan.VkPipelineRenderingCreateInfoKHR;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Final;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkAllocationCallbacks;
import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkGraphicsPipelineCreateInfo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.util.Set;

@Mixin(value = GraphicsPipeline.class, remap = false)
public abstract class GraphicsPipelineMixin implements VeyraPipelinePassCache {
    @Shadow @Final private Object2LongMap<PipelineState> graphicsPipelines;
    private static long veyra$firstGraphicsPipelineDevice;

    @Redirect(method = "createGraphicsPipeline", at = @At(value = "INVOKE", target =
            "Lorg/lwjgl/vulkan/VK10;vkCreateGraphicsPipelines(Lorg/lwjgl/vulkan/VkDevice;JLorg/lwjgl/vulkan/VkGraphicsPipelineCreateInfo$Buffer;Lorg/lwjgl/vulkan/VkAllocationCallbacks;Ljava/nio/LongBuffer;)I"))
    private static int veyra$traceGraphicsPipelineDevice(VkDevice device, long pipelineCache,
            VkGraphicsPipelineCreateInfo.Buffer createInfos, VkAllocationCallbacks allocator, LongBuffer pipelines) {
        if (Boolean.getBoolean("veyra.graphicsSmoke")) {
            long pipelineDevice = device.address();
            long createdDevice = VeyraDeviceFeatures.createdLogicalDeviceHandle;
            if (veyra$firstGraphicsPipelineDevice == 0L || pipelineDevice != createdDevice
                    || pipelineDevice != veyra$firstGraphicsPipelineDevice) {
                VeyraClient.LOGGER.info("Graphics pipeline device handle=0x{}, vkCreateDevice handle=0x{}, matches={}",
                        Long.toHexString(pipelineDevice), Long.toHexString(createdDevice),
                        pipelineDevice == createdDevice);
                if (veyra$firstGraphicsPipelineDevice == 0L) veyra$firstGraphicsPipelineDevice = pipelineDevice;
            }
        }
        return VK10.vkCreateGraphicsPipelines(device, pipelineCache, createInfos, allocator, pipelines);
    }

    @Override public void veyra$retireRenderPasses(Set<RenderPass> passes) {
        var iterator = graphicsPipelines.object2LongEntrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            if (!passes.contains(((PipelineStateAccessor) entry.getKey()).veyra$getRenderPass())) continue;
            long handle = entry.getLongValue();
            iterator.remove();
            VK10.vkDestroyPipeline(Vulkan.getVkDevice(), handle, null);
        }
    }
    @Redirect(method = "createGraphicsPipeline", at = @At(value = "INVOKE", target =
            "Lorg/lwjgl/vulkan/VkPipelineColorBlendStateCreateInfo;pAttachments(Lorg/lwjgl/vulkan/VkPipelineColorBlendAttachmentState$Buffer;)Lorg/lwjgl/vulkan/VkPipelineColorBlendStateCreateInfo;"))
    private VkPipelineColorBlendStateCreateInfo veyra$colorAttachments(VkPipelineColorBlendStateCreateInfo info,
            VkPipelineColorBlendAttachmentState.Buffer original, PipelineState state) {
        var pass = ((PipelineStateAccessor) state).veyra$getRenderPass();
        var layout = VeyraGeometryTargets.layout((GraphicsPipeline) (Object) this);
        var blending = VeyraGeometryTargets.blend((GraphicsPipeline) (Object) this);
        int count = pass != null && pass.getFramebuffer() instanceof VeyraMrtFramebuffer target ? target.colors().size() : original.remaining();
        if (layout == null) return info.pAttachments(original);
        var attachments = VkPipelineColorBlendAttachmentState.calloc(count, MemoryStack.stackGet());
        for (int i = 0; i < attachments.capacity(); i++) {
            attachments.get(i).set(original.get(0));
            if (!layout.writes(i)) attachments.get(i).colorWriteMask(0);
            var mode = blending.forTarget(layout.targets().get(i));
            if (mode != null) attachments.get(i).blendEnable(mode.enabled())
                    .srcColorBlendFactor(mode.sourceColor()).dstColorBlendFactor(mode.destinationColor())
                    .srcAlphaBlendFactor(mode.sourceAlpha()).dstAlphaBlendFactor(mode.destinationAlpha())
                    .colorBlendOp(VK10.VK_BLEND_OP_ADD).alphaBlendOp(VK10.VK_BLEND_OP_ADD);
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
