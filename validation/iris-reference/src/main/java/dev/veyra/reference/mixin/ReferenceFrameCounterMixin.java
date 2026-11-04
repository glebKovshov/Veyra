package dev.veyra.reference.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "net.irisshaders.iris.uniforms.SystemTimeUniforms$FrameCounter", remap = false)
public abstract class ReferenceFrameCounterMixin {
    @Shadow private int count;
    @Unique private boolean veyra$counterRecorded;

    @Inject(method = "beginFrame", at = @At("RETURN"))
    private void veyra$fixComparisonCounter(CallbackInfo ci) {
        if (Boolean.getBoolean("veyra.reference.fixedClock")) {
            count = 160;
            if (!veyra$counterRecorded) {
                veyra$counterRecorded = true;
                System.out.println("REFERENCE_CLOCK renderer=Iris frameCounter=" + count);
            }
        }
    }
}
