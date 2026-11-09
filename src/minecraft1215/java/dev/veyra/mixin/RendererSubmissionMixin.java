package dev.veyra.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
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
public abstract class RendererSubmissionMixin implements dev.veyra.vulkan.LegacyAcquisitionState {
    @Override public boolean veyra$hasAcquired() { return veyra$hasAcquired; }
    @Unique private boolean veyra$hasAcquired;
    @Shadow private ArrayList<Long> imageAvailableSemaphores;
    @Shadow private ArrayList<Long> renderFinishedSemaphores;
    @Shadow private SwapChain swapChain;
    @Shadow private int framesNum;
    @Shadow private static int currentFrame;
    @Unique private boolean veyra$acquireConsumed;
    @Unique private boolean veyra$resumingFlushedPass;

    @Inject(method = "createSyncObjects", at = @At("RETURN"))
    private void veyra$allocatePresentationSemaphores(CallbackInfo callback) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            var info = org.lwjgl.vulkan.VkSemaphoreCreateInfo.calloc(stack).sType$Default();
            var handle = stack.mallocLong(1);
            try {
                while (renderFinishedSemaphores.size() < swapChain.getImagesNum()) {
                    net.vulkanmod.vulkan.Vulkan.checkResult(vkCreateSemaphore(
                            net.vulkanmod.vulkan.Vulkan.getVkDevice(), info, null, handle), "Create presentation semaphore");
                    renderFinishedSemaphores.add(handle.get(0));
                }
            } catch (RuntimeException | Error failure) {
                veyra$destroyExtraPresentationSemaphores();
                throw failure;
            }
        }
    }

    @WrapOperation(method = "submitFrame", at = @At(value = "INVOKE", target =
            "Ljava/util/ArrayList;get(I)Ljava/lang/Object;"))
    private Object veyra$presentationSemaphoreByImage(ArrayList<?> values, int frame, Operation<Object> original) {
        // Presentation completion follows reacquisition of that image, not the frame's graphics fence.
        return original.call(values, values == renderFinishedSemaphores ? Renderer.getCurrentImage() : frame);
    }

    @Inject(method = "destroySyncObjects", at = @At("RETURN"))
    private void veyra$freePresentationSemaphores(CallbackInfo callback) {
        veyra$destroyExtraPresentationSemaphores();
    }

    @Unique private void veyra$destroyExtraPresentationSemaphores() {
        for (int index = framesNum; index < renderFinishedSemaphores.size(); index++)
            vkDestroySemaphore(net.vulkanmod.vulkan.Vulkan.getVkDevice(), renderFinishedSemaphores.get(index), null);
        if (renderFinishedSemaphores.size() > framesNum)
            renderFinishedSemaphores.subList(framesNum, renderFinishedSemaphores.size()).clear();
    }

    @WrapMethod(method = "flushCmds")
    private void veyra$preserveFlushedColor(Operation<Void> original) {
        boolean previous = veyra$resumingFlushedPass;
        veyra$resumingFlushedPass = true;
        try { original.call(); }
        finally { veyra$resumingFlushedPass = previous; }
    }

    @Redirect(method = "beginRenderPass", at = @At(value = "INVOKE", target =
            "Lnet/vulkanmod/vulkan/pass/MainPass;begin(Lorg/lwjgl/vulkan/VkCommandBuffer;Lorg/lwjgl/system/MemoryStack;)V"))
    private void veyra$loadColorAfterFlush(net.vulkanmod.vulkan.pass.MainPass pass,
            org.lwjgl.vulkan.VkCommandBuffer commands, MemoryStack stack) {
        if (veyra$resumingFlushedPass) pass.rebindMainTarget();
        else pass.begin(commands, stack);
    }

    @Inject(method = "beginFrame", at = @At(value = "INVOKE", target =
            "Lorg/lwjgl/vulkan/KHRSwapchain;vkAcquireNextImageKHR(Lorg/lwjgl/vulkan/VkDevice;JJJJLjava/nio/IntBuffer;)I"))
    private void veyra$resetAcquisition(CallbackInfo callback) {
        // Reset only when acquiring: old beginFrame returns early during an active recording.
        veyra$hasAcquired = false;
        veyra$acquireConsumed = false;
    }

    @com.llamalad7.mixinextras.injector.ModifyExpressionValue(method = "beginFrame", at = @At(value = "INVOKE", target =
            "Lorg/lwjgl/vulkan/KHRSwapchain;vkAcquireNextImageKHR(Lorg/lwjgl/vulkan/VkDevice;JJJJLjava/nio/IntBuffer;)I"))
    private int veyra$trackAcquisition(int result) {
        veyra$hasAcquired = dev.veyra.vulkan.LegacyAcquisition.succeeded(result);
        return result;
    }

    @Inject(method = "beginRenderPass", at = @At(value = "INVOKE", target =
            "Lnet/vulkanmod/vulkan/pass/MainPass;begin(Lorg/lwjgl/vulkan/VkCommandBuffer;Lorg/lwjgl/system/MemoryStack;)V"))
    private void veyra$makeUploadsVisible(MemoryStack stack, CallbackInfo callback) {
        // Upload command buffers submit before this graphics buffer, including index-buffer growth.
        VeyraComputePipeline.memoryBarrier(Renderer.getCommandBuffer(),
                veyra$acquireConsumed ? VK_PIPELINE_STAGE_ALL_COMMANDS_BIT : VK_PIPELINE_STAGE_TRANSFER_BIT,
                VK_PIPELINE_STAGE_ALL_COMMANDS_BIT,
                veyra$acquireConsumed ? VK_ACCESS_MEMORY_WRITE_BIT : VK_ACCESS_TRANSFER_WRITE_BIT,
                VK_ACCESS_MEMORY_READ_BIT | VK_ACCESS_MEMORY_WRITE_BIT);
    }

    @Redirect(method = "flushCmds", at = @At(value = "INVOKE", target =
            "Lorg/lwjgl/vulkan/VK10;vkQueueSubmit(Lorg/lwjgl/vulkan/VkQueue;Lorg/lwjgl/vulkan/VkSubmitInfo;J)I"))
    private int veyra$waitForAcquiredImage(VkQueue queue, VkSubmitInfo submission, long fence) {
        if (!veyra$hasAcquired || veyra$acquireConsumed) return vkQueueSubmit(queue, submission, fence);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            int result = vkQueueSubmit(queue, withAcquireDependency(submission,
                    imageAvailableSemaphores.get(currentFrame), stack), fence);
            if (result == VK_SUCCESS) veyra$acquireConsumed = true;
            return result;
        }
    }

    @Unique
    private static VkSubmitInfo withAcquireWaitStages(VkSubmitInfo original, long semaphore, MemoryStack stack) {
        boolean present = false;
        var stages = stack.mallocInt(original.waitSemaphoreCount());
        for (int i = 0; i < original.waitSemaphoreCount(); i++) {
            present |= original.pWaitSemaphores().get(i) == semaphore;
            stages.put(i, original.pWaitSemaphores().get(i) == semaphore
                    ? VK_PIPELINE_STAGE_ALL_COMMANDS_BIT : original.pWaitDstStageMask().get(i));
        }
        // The resize/discard branch submits commands but omits the acquired-image wait.
        if (!present) return withAcquireDependency(original, semaphore, stack);
        return VkSubmitInfo.malloc(stack).set(original).pWaitDstStageMask(stages);
    }

    @Redirect(method = "submitFrame", at = @At(value = "INVOKE", target =
            "Lorg/lwjgl/vulkan/VK10;vkQueueSubmit(Lorg/lwjgl/vulkan/VkQueue;Lorg/lwjgl/vulkan/VkSubmitInfo;J)I"))
    private int veyra$waitBeforeSwapchainTransitions(VkQueue queue, VkSubmitInfo submission, long fence) {
        if (!veyra$hasAcquired) return vkQueueSubmit(queue, submission, fence);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            // Acquisition must also precede swapchain layout transitions outside color attachment output.
            long acquired = imageAvailableSemaphores.get(currentFrame);
            return vkQueueSubmit(queue, veyra$acquireConsumed
                    ? withoutAcquireDependency(submission, acquired, stack)
                    : withAcquireWaitStages(submission, acquired, stack), fence);
        }
    }

    @Unique
    private static VkSubmitInfo withoutAcquireDependency(VkSubmitInfo original, long semaphore, MemoryStack stack) {
        int count = original.waitSemaphoreCount();
        var waits = stack.mallocLong(count);
        var stages = stack.mallocInt(count);
        for (int i = 0; i < count; i++) {
            if (original.pWaitSemaphores().get(i) == semaphore) continue;
            waits.put(original.pWaitSemaphores().get(i));
            stages.put(original.pWaitDstStageMask().get(i));
        }
        waits.flip();
        stages.flip();
        return VkSubmitInfo.malloc(stack).set(original)
                .pWaitSemaphores(waits.hasRemaining() ? waits : null).waitSemaphoreCount(waits.remaining())
                .pWaitDstStageMask(stages.hasRemaining() ? stages : null);
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
        // The first flush consumes acquisition; its fence wait completes before recording resumes.
        return VkSubmitInfo.malloc(stack).set(original).pWaitSemaphores(waits)
                .waitSemaphoreCount(count + 1).pWaitDstStageMask(stages);
    }
}
