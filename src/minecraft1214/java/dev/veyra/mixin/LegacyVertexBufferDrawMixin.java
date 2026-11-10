package dev.veyra.mixin;

import com.mojang.blaze3d.vertex.VertexBuffer;
import dev.veyra.compat.LegacyDraws;
import net.minecraft.client.renderer.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(VertexBuffer.class)
public abstract class LegacyVertexBufferDrawMixin {
    @Inject(method = "drawWithRenderType", at = @At(value = "INVOKE", shift = At.Shift.AFTER,
            target = "Lnet/minecraft/client/renderer/RenderType;setupRenderState()V"))
    private void veyra$routeDraw(RenderType type, CallbackInfo callback) {
        LegacyDraws.beginRenderType(type);
    }
}
