package dev.veyra.mixin;

import dev.veyra.compat.LegacyDraws;
import net.minecraft.client.renderer.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RenderType.class)
public abstract class LegacyRenderTypeDrawMixin {
    @Inject(method = "draw", at = @At(value = "INVOKE", shift = At.Shift.AFTER,
            target = "Lnet/minecraft/client/renderer/RenderType;setupRenderState()V"))
    private void veyra$routeDraw(CallbackInfo callback) {
        LegacyDraws.beginRenderType((RenderType) (Object) this);
    }
}
