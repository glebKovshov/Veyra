package dev.veyra.smoke.mixin;

import dev.veyra.smoke.GraphicsSmoke;
import net.vulkanmod.vulkan.Renderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Keep readback in the submission which produced the image, before presentation. */
@Mixin(value = Renderer.class, remap = false)
public abstract class GraphicsFrameMixin {
    @Inject(method = "endFrame", at = @At("HEAD"), remap = false)
    private void veyra$captureRenderedFrame(CallbackInfo ci) { GraphicsSmoke.renderedFrame(); }
}
