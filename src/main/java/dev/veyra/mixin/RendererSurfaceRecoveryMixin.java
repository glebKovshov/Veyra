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
    @Shadow private int recursion;

    @Inject(method = "beginFrame", at = @At("HEAD"))
    private void veyra$retryLostSurface(CallbackInfo callback) {
        // beginFrame increments recursion before checking the update flag; only the outer frame recreates.
        if (recursion != 0 || swapChain == null || swapChain.hasImages()) return;
        long window = Minecraft.getInstance().getWindow().handle();
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
