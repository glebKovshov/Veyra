package dev.veyra.smoke.mixin;

import dev.veyra.shader.VeyraRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Optional shared animation phase for controlled comparisons with the standalone Iris client. */
@Mixin(value = VeyraRenderer.class, remap = false)
public abstract class GraphicsClockMixin {
    @Shadow private static float frameTimeCounter;
    @Shadow private static float frameTime;
    @Shadow private static int frameCounter;
    @Unique private static boolean veyra$clockRecorded;

    @Inject(method = "updateFrameClock", at = @At("RETURN"))
    private static void veyra$fixComparisonClock(CallbackInfo ci) {
        if (!Boolean.getBoolean("veyra.reference.fixedClock")) return;
        frameTimeCounter = 8.0F;
        frameTime = 1.0F / 30.0F;
        frameCounter = 160;
        if (!veyra$clockRecorded) {
            veyra$clockRecorded = true;
            System.out.println("REFERENCE_CLOCK renderer=Veyra frameTimeCounter=" + frameTimeCounter
                    + " frameTime=" + frameTime + " frameCounter=" + frameCounter);
        }
    }
}
