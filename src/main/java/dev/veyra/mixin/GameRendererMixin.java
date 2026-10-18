package dev.veyra.mixin;

import dev.veyra.shader.VeyraRenderer;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    @Inject(method = "render", at = @At("RETURN"))
    private void veyra$finishShaderFrameWithoutGui(CallbackInfo callback) {
        // GuiRenderer's pre-GUI hook normally runs composite. With F1/hideGui it may
        // never be called. The renderer's phase cursor makes this fallback a no-op
        // when composite already ran, so visible HUD pixels are not processed twice.
        VeyraRenderer.render(net.minecraft.client.Minecraft.getInstance());
    }

    @Inject(method = "renderItemInHand", at = @At("HEAD"))
    private void veyra$beginHandShaderPass(CallbackInfo callback) {
        VeyraRenderer.beginHandRendering();
    }

    @Inject(method = "renderItemInHand", at = @At("RETURN"))
    private void veyra$endHandShaderPass(CallbackInfo callback) {
        VeyraRenderer.endHandRendering();
    }
}
