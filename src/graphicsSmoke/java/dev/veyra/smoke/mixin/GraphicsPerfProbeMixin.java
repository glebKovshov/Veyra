package dev.veyra.smoke.mixin;

import dev.veyra.smoke.PerfProbe;
import net.vulkanmod.vulkan.Renderer;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkDevice;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Diagnostic: time the frame fence wait (GPU-bound) against the render thread's own work. */
@Mixin(value = Renderer.class, remap = false)
public abstract class GraphicsPerfProbeMixin {
    @Redirect(method = "beginFrame", at = @At(value = "INVOKE", ordinal = 0,
            target = "Lorg/lwjgl/vulkan/VK10;vkWaitForFences(Lorg/lwjgl/vulkan/VkDevice;JZJ)I"), remap = false)
    private int veyra$timeFenceWait(VkDevice device, long fence, boolean waitAll, long timeout) {
        long start = System.nanoTime();
        int result = VK10.vkWaitForFences(device, fence, waitAll, timeout);
        PerfProbe.fenceWait(System.nanoTime() - start);
        return result;
    }

    @Inject(method = "endFrame", at = @At("HEAD"), remap = false)
    private void veyra$frameBoundary(CallbackInfo callback) {
        PerfProbe.frame();
    }
}
