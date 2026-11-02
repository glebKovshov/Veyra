package dev.veyra.reference.mixin;

import net.minecraft.client.renderer.GameRenderer;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Optional diagnostic matching Veyra's near-infinite depth, in OpenGL clip coordinates. */
@Mixin(GameRenderer.class)
public abstract class ReferenceProjectionMixin {
    @Unique private boolean veyra$depthRecorded;

    @Inject(method = "getProjectionMatrix", at = @At("RETURN"), cancellable = true)
    private void veyra$matchNativeDepth(CallbackInfoReturnable<Matrix4f> cir) {
        if (!Boolean.getBoolean("veyra.reference.nativeDepth")) return;
        Matrix4f projection = new Matrix4f(cir.getReturnValue());
        if (projection.m23() != -1.0F || projection.m33() != 0.0F)
            throw new IllegalStateException("Native-depth reference requires a perspective projection");
        projection.m22(2.0F * Math.nextDown(-1.0F) + 1.0F).m32(-0.0999999F);
        cir.setReturnValue(projection);
        if (!veyra$depthRecorded) {
            veyra$depthRecorded = true;
            System.out.println("REFERENCE_DEPTH nativeDepth=true m22=" + projection.m22() + " m32=" + projection.m32());
        }
    }
}
