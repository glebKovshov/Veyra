package dev.veyra.mixin;

import dev.veyra.shader.VeyraComputePipeline;
import net.vulkanmod.vulkan.Renderer;
import net.vulkanmod.vulkan.framebuffer.SwapChain;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkQueue;
import org.lwjgl.vulkan.VkSubmitInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.ArrayList;
import static org.lwjgl.vulkan.VK10.*;

@Mixin(value = Renderer.class, remap = false)
public abstract class RendererSubmissionMixin {
    @Shadow private SwapChain swapChain;
    @Shadow private ArrayList<Long> imageAvailableSemaphores;
    @Shadow private static int currentFrame;

    @Inject(method = "beginMainRenderPass", at = @At(value = "INVOKE", target =
            "Lnet/vulkanmod/vulkan/pass/MainPass;begin(Lorg/lwjgl/vulkan/VkCommandBuffer;Lorg/lwjgl/system/MemoryStack;)V"))
    private void veyra$makeUploadsVisible(MemoryStack stack, CallbackInfo callback) {
        // Upload command buffers submit before this graphics buffer, including index-buffer growth.
        VeyraComputePipeline.memoryBarrier(Renderer.getCommandBuffer(),
                VK_PIPELINE_STAGE_TRANSFER_BIT, VK_PIPELINE_STAGE_ALL_COMMANDS_BIT,
                VK_ACCESS_TRANSFER_WRITE_BIT, VK_ACCESS_MEMORY_READ_BIT | VK_ACCESS_MEMORY_WRITE_BIT);
    }

    @Redirect(method = "flushCmds", at = @At(value = "INVOKE", target =
            "Lorg/lwjgl/vulkan/VK10;vkQueueSubmit(Lorg/lwjgl/vulkan/VkQueue;Lorg/lwjgl/vulkan/VkSubmitInfo;J)I"))
    private int veyra$waitForAcquiredImage(VkQueue queue, VkSubmitInfo submission, long fence) {
        if (!swapChain.isAcquired()) return vkQueueSubmit(queue, submission, fence);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            return vkQueueSubmit(queue, withAcquireDependency(submission,
                    imageAvailableSemaphores.get(currentFrame), stack), fence);
        }
    }

    @Unique
    private static VkSubmitInfo withAcquireWaitStages(VkSubmitInfo original, long semaphore, MemoryStack stack) {
        var stages = stack.mallocInt(original.waitSemaphoreCount());
        for (int i = 0; i < original.waitSemaphoreCount(); i++) {
            stages.put(i, original.pWaitSemaphores().get(i) == semaphore
                    ? VK_PIPELINE_STAGE_ALL_COMMANDS_BIT : original.pWaitDstStageMask().get(i));
        }
        return VkSubmitInfo.malloc(stack).set(original).pWaitDstStageMask(stages);
    }

    @Redirect(method = "submitFrame", at = @At(value = "INVOKE", target =
            "Lorg/lwjgl/vulkan/VK10;vkQueueSubmit(Lorg/lwjgl/vulkan/VkQueue;Lorg/lwjgl/vulkan/VkSubmitInfo;J)I"))
    private int veyra$waitBeforeSwapchainTransitions(VkQueue queue, VkSubmitInfo submission, long fence) {
        if (!swapChain.isAcquired()) return vkQueueSubmit(queue, submission, fence);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            // Acquisition must also precede swapchain layout transitions outside color attachment output.
            return vkQueueSubmit(queue, withAcquireWaitStages(submission,
                    imageAvailableSemaphores.get(currentFrame), stack), fence);
        }
    }

    @Unique
    private static VkSubmitInfo withAcquireDependency(VkSubmitInfo original, long semaphore, MemoryStack stack) {
        int count = original.waitSemaphoreCount();
        var waits = stack.mallocLong(count + 1);
        var stages = stack.mallocInt(count + 1);
        for (int i = 0; i < count; i++) {
            waits.put(i, original.pWaitSemaphores().get(i));
            stages.put(i, original.pWaitDstStageMask().get(i));
        }
        waits.put(count, semaphore);
        stages.put(count, VK_PIPELINE_STAGE_ALL_COMMANDS_BIT);
        int signalCount = original.signalSemaphoreCount();
        var signals = stack.mallocLong(signalCount + 1);
        for (int i = 0; i < signalCount; i++) signals.put(i, original.pSignalSemaphores().get(i));
        // The flush consumes acquisition and relays it so VulkanMod's final submit can still present.
        signals.put(signalCount, semaphore);
        return VkSubmitInfo.malloc(stack).set(original).pWaitSemaphores(waits)
                .waitSemaphoreCount(count + 1).pWaitDstStageMask(stages).pSignalSemaphores(signals);
    }
}
