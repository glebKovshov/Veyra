package dev.veyra.mixin;

import dev.veyra.compat.LegacyDraws;
import net.vulkanmod.gl.VkGlFramebuffer;
import net.vulkanmod.vulkan.Renderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A clear with no framebuffer bound targets OpenGL's default framebuffer, which VulkanMod maps to
 * the main target. Veyra's passes end the main render pass between Minecraft's own binds, and
 * VulkanMod would otherwise drop the clear, leaving the previous frame's depth to reject the world.
 */
@Mixin(value = Renderer.class, remap = false)
public abstract class LegacyDefaultFramebufferClearMixin {
    @Inject(method = "clearAttachments(I)V", at = @At("HEAD"))
    private static void veyra$clearDefaultFramebuffer(int mask, CallbackInfo callback) {
        Renderer renderer = Renderer.getInstance();
        if (Renderer.isRecording() && renderer.getBoundRenderPass() == null
                && VkGlFramebuffer.getBoundFramebuffer() == null && !LegacyDraws.keepsBoundTarget()) {
            renderer.getMainPass().rebindMainTarget();
        }
    }
}
