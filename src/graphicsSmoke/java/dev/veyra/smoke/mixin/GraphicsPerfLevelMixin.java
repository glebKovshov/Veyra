package dev.veyra.smoke.mixin;

import dev.veyra.smoke.PerfProbe;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Diagnostic: render-thread time of the whole world render. */
@Mixin(LevelRenderer.class)
public abstract class GraphicsPerfLevelMixin {
    @Inject(method = "renderLevel", at = @At("HEAD"))
    private void veyra$levelStart(CallbackInfo callback) { PerfProbe.levelStart(); }

    @Inject(method = "renderLevel", at = @At("RETURN"))
    private void veyra$levelEnd(CallbackInfo callback) { PerfProbe.levelEnd(); }
}
