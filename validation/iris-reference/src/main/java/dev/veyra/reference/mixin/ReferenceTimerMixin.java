package dev.veyra.reference.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "net.irisshaders.iris.uniforms.SystemTimeUniforms$Timer", remap = false)
public abstract class ReferenceTimerMixin {
    @Shadow private float frameTimeCounter;
    @Shadow private float lastFrameTime;
    @Unique private boolean veyra$clockRecorded;

    @Inject(method = "beginFrame", at = @At("RETURN"))
    private void veyra$fixComparisonClock(long now, CallbackInfo ci) {
        if (!Boolean.getBoolean("veyra.reference.fixedClock")) return;
        frameTimeCounter = 8.0F;
        lastFrameTime = 1.0F / 30.0F;
        if (!veyra$clockRecorded) {
            veyra$clockRecorded = true;
            System.out.println("REFERENCE_CLOCK renderer=Iris frameTimeCounter=" + frameTimeCounter
                    + " frameTime=" + lastFrameTime);
        }
    }
}
