package dev.veyra.mixin;

import dev.veyra.shader.VeyraRenderer;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    @Inject(method = "renderItemInHand", at = @At("HEAD"))
    private void veyra$beginHandShaderPass(CallbackInfo callback) {
        VeyraRenderer.beginHandRendering();
    }

    @Inject(method = "renderItemInHand", at = @At("RETURN"))
    private void veyra$endHandShaderPass(CallbackInfo callback) {
        VeyraRenderer.endHandRendering();
    }
}
