package dev.veyra.smoke.mixin;

import dev.veyra.smoke.PerfProbe;
import dev.veyra.shader.VeyraShadowRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Diagnostic: render-thread time of the shadow pass, section selection included. */
@Mixin(value = VeyraShadowRenderer.class, remap = false)
public abstract class GraphicsPerfShadowMixin {
    @Inject(method = "render", at = @At("HEAD"), remap = false)
    private static void veyra$shadowStart(CallbackInfo callback) { PerfProbe.shadowStart(); }

    @Inject(method = "render", at = @At("RETURN"), remap = false)
    private static void veyra$shadowEnd(CallbackInfo callback) { PerfProbe.shadowEnd(); }
}
