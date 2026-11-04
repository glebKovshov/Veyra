package dev.veyra.smoke.mixin;

import dev.veyra.smoke.PerfProbe;
import net.vulkanmod.render.chunk.WorldRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Diagnostic: render-thread time spent selecting chunk sections (camera and shadow walks). */
@Mixin(value = WorldRenderer.class, remap = false)
public abstract class GraphicsPerfSectionsMixin {
    @Inject(method = "setupRenderer", at = @At("HEAD"), remap = false)
    private void veyra$setupStart(CallbackInfo callback) { PerfProbe.setupStart(); }

    @Inject(method = "setupRenderer", at = @At("RETURN"), remap = false)
    private void veyra$setupEnd(CallbackInfo callback) { PerfProbe.setupEnd(); }
}
