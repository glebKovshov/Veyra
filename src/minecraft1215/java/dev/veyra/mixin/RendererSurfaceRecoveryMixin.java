package dev.veyra.mixin;

import dev.veyra.vulkan.VeyraSurfaceRecovery;
import net.minecraft.client.Minecraft;
import net.vulkanmod.vulkan.Renderer;
import net.vulkanmod.vulkan.framebuffer.SwapChain;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.system.MemoryStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = Renderer.class, remap = false)
public abstract class RendererSurfaceRecoveryMixin {
    @Shadow private SwapChain swapChain;

    @Inject(method = "beginFrame", at = @At("HEAD"))
    private void veyra$retryLostSurface(CallbackInfo callback) {
        // The old backend marks a suspended surface with a zero extent.
        if (Renderer.isRecording() || swapChain == null
                || (swapChain.getWidth() > 0 && swapChain.getHeight() > 0)) return;
        long window = Minecraft.getInstance().getWindow().getWindow();
        try (MemoryStack stack = MemoryStack.stackPush()) {
            var width = stack.mallocInt(1);
            var height = stack.mallocInt(1);
            GLFW.glfwGetFramebufferSize(window, width, height);
            boolean iconified = GLFW.glfwGetWindowAttrib(window, GLFW.GLFW_ICONIFIED) == GLFW.GLFW_TRUE;
            if (VeyraSurfaceRecovery.shouldRetry(width.get(0), height.get(0), iconified))
                Renderer.scheduleSwapChainUpdate();
        }
    }
}
