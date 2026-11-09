package dev.veyra.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
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
import net.vulkanmod.vulkan.shader.DescriptorSets;
import net.vulkanmod.vulkan.shader.Pipeline;
import net.vulkanmod.vulkan.shader.PipelineState;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkPipelineColorBlendAttachmentState;
import org.lwjgl.vulkan.VkPipelineColorBlendStateCreateInfo;
import org.lwjgl.vulkan.VkPipelineRasterizationStateCreateInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Final;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkAllocationCallbacks;
import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkGraphicsPipelineCreateInfo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.nio.LongBuffer;
import java.util.Set;

@Mixin(value = GraphicsPipeline.class, remap = false)
public abstract class GraphicsPipelineMixin extends Pipeline implements VeyraPipelinePassCache {
    @Shadow @Final private Object2LongMap<PipelineState> graphicsPipelines;
    private static long veyra$firstGraphicsPipelineDevice;

    protected GraphicsPipelineMixin(String name) { super(name); }

    @WrapOperation(method = "<init>", at = @At(value = "INVOKE", target =
            "Lnet/vulkanmod/vulkan/shader/GraphicsPipeline;createShaderModules(Lnet/vulkanmod/vulkan/shader/Pipeline$Builder;)V"))
    private void veyra$releaseFailedShaderConstruction(GraphicsPipeline pipeline, Pipeline.Builder builder,
                                                       Operation<Void> original) {
        try {
            original.call(pipeline, builder);
        } catch (RuntimeException | Error failure) {
            // Layouts already exist, but descriptor pools and the registry entry have not been created.
            descriptorSets = new DescriptorSets[0];
            try { pipeline.cleanUp(); }
            catch (RuntimeException | Error cleanupFailure) { failure.addSuppressed(cleanupFailure); }
            throw failure;
        }
    }

    @Redirect(method = "createGraphicsPipeline", at = @At(value = "INVOKE", target =
            "Lorg/lwjgl/vulkan/VkPipelineRasterizationStateCreateInfo;frontFace(I)Lorg/lwjgl/vulkan/VkPipelineRasterizationStateCreateInfo;"))
    private VkPipelineRasterizationStateCreateInfo veyra$shadowFrontFace(
            VkPipelineRasterizationStateCreateInfo info, int original) {
        // Shadow clip Y is inverted to retain OpenGL texture coordinates.
        return info.frontFace(VeyraGeometryTargets.isShadowPipeline((GraphicsPipeline) (Object) this)
                ? (original == VK10.VK_FRONT_FACE_CLOCKWISE ? VK10.VK_FRONT_FACE_COUNTER_CLOCKWISE : VK10.VK_FRONT_FACE_CLOCKWISE)
                : original);
    }

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

}
