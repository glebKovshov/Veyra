package dev.veyra.mixin;

import org.junit.jupiter.api.Test;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkSubmitInfo;
import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.vulkan.VK10.*;

class RendererSubmissionTest {
    @Test
    void finalSubmitWaitsBeforeAllSwapchainAccessesButPreservesOtherDependencies() throws Exception {
        var method = RendererSubmissionMixin.class.getDeclaredMethod("withAcquireWaitStages", VkSubmitInfo.class,
                long.class, MemoryStack.class);
        method.setAccessible(true);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkSubmitInfo original = VkSubmitInfo.calloc(stack).sType$Default().pNext(123)
                    .pWaitSemaphores(stack.longs(11, 22)).waitSemaphoreCount(2)
                    .pWaitDstStageMask(stack.ints(VK_PIPELINE_STAGE_TRANSFER_BIT, VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT))
                    .pSignalSemaphores(stack.longs(33)).pCommandBuffers(stack.pointers(44));
            VkSubmitInfo submitted = (VkSubmitInfo) method.invoke(null, original, 22L, stack);
            assertEquals(2, submitted.waitSemaphoreCount());
            assertEquals(VK_PIPELINE_STAGE_TRANSFER_BIT, submitted.pWaitDstStageMask().get(0));
            assertEquals(VK_PIPELINE_STAGE_ALL_COMMANDS_BIT, submitted.pWaitDstStageMask().get(1));
            assertEquals(VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT, original.pWaitDstStageMask().get(1));
            assertEquals(11, submitted.pWaitSemaphores().get(0));
            assertEquals(22, submitted.pWaitSemaphores().get(1));
            assertEquals(33, submitted.pSignalSemaphores().get(0));
            assertEquals(44, submitted.pCommandBuffers().get(0));
            assertEquals(123, submitted.pNext());
        }
    }

    private static VkSubmitInfo withAcquireDependency(VkSubmitInfo original, long semaphore, MemoryStack stack)
            throws ReflectiveOperationException {
        var method = RendererSubmissionMixin.class.getDeclaredMethod("withAcquireDependency", VkSubmitInfo.class, long.class, MemoryStack.class);
        method.setAccessible(true);
        return (VkSubmitInfo) method.invoke(null, original, semaphore, stack);
    }

    @Test
    void waitsForAcquisitionAndRelaysTheBinarySemaphoreWithoutChangingTheCaller() throws Exception {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkSubmitInfo original = VkSubmitInfo.calloc(stack).sType$Default();
            VkSubmitInfo submitted = withAcquireDependency(original, 42, stack);
            assertEquals(1, submitted.waitSemaphoreCount());
            assertEquals(42, submitted.pWaitSemaphores().get(0));
            assertEquals(VK_PIPELINE_STAGE_ALL_COMMANDS_BIT, submitted.pWaitDstStageMask().get(0));
            assertEquals(1, submitted.signalSemaphoreCount());
            assertEquals(42, submitted.pSignalSemaphores().get(0));
            assertEquals(0, original.waitSemaphoreCount());
            assertEquals(0, original.signalSemaphoreCount());
            assertNull(original.pWaitSemaphores());
        }
    }

    @Test
    void preservesUploadDependenciesSignalsCommandBuffersAndExtensionChain() throws Exception {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkSubmitInfo original = VkSubmitInfo.calloc(stack).sType$Default().pNext(123)
                    .pWaitSemaphores(stack.longs(11, 22)).waitSemaphoreCount(2)
                    .pWaitDstStageMask(stack.ints(VK_PIPELINE_STAGE_TRANSFER_BIT, VK_PIPELINE_STAGE_VERTEX_SHADER_BIT))
                    .pSignalSemaphores(stack.longs(33)).pCommandBuffers(stack.pointers(44));
            VkSubmitInfo submitted = withAcquireDependency(original, 55, stack);
            assertEquals(3, submitted.waitSemaphoreCount());
            assertEquals(11, submitted.pWaitSemaphores().get(0));
            assertEquals(22, submitted.pWaitSemaphores().get(1));
            assertEquals(55, submitted.pWaitSemaphores().get(2));
            assertEquals(VK_PIPELINE_STAGE_TRANSFER_BIT, submitted.pWaitDstStageMask().get(0));
            assertEquals(VK_PIPELINE_STAGE_VERTEX_SHADER_BIT, submitted.pWaitDstStageMask().get(1));
            assertEquals(2, submitted.signalSemaphoreCount());
            assertEquals(33, submitted.pSignalSemaphores().get(0));
            assertEquals(55, submitted.pSignalSemaphores().get(1));
            assertEquals(44, submitted.pCommandBuffers().get(0));
            assertEquals(123, submitted.pNext());
            assertEquals(2, original.waitSemaphoreCount());
            assertEquals(1, original.signalSemaphoreCount());
            assertEquals(33, original.pSignalSemaphores().get(0));
        }
    }
}
