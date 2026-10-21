package dev.veyra.mixin;

import dev.veyra.shader.VeyraRenderer;
import net.minecraft.client.renderer.SkyRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SkyRenderer.class)
public abstract class SkyRendererMixin {
    @Inject(method = "renderSun", at = @At("HEAD"))
    private void veyra$beginSunStage(CallbackInfo ci) {
        VeyraRenderer.beginSkySunStage();
    }

    @Inject(method = "renderSun", at = @At("RETURN"))
    private void veyra$endSunStage(CallbackInfo ci) {
        VeyraRenderer.endSkyRenderStage();
    }

    @Inject(method = "renderMoon", at = @At("HEAD"))
    private void veyra$beginMoonStage(CallbackInfo ci) {
        VeyraRenderer.beginSkyMoonStage();
    }

    @Inject(method = "renderMoon", at = @At("RETURN"))
    private void veyra$endMoonStage(CallbackInfo ci) {
        VeyraRenderer.endSkyRenderStage();
    }

    @Inject(method = "renderEndSky", at = @At("HEAD"))
    private void veyra$beginEndSkyStage(CallbackInfo ci) {
        VeyraRenderer.beginSkyVoidStage();
    }

    @Inject(method = "renderEndSky", at = @At("RETURN"))
    private void veyra$endEndSkyStage(CallbackInfo ci) {
        VeyraRenderer.endSkyRenderStage();
    }

    @Inject(method = "renderEndFlash", at = @At("HEAD"))
    private void veyra$beginEndFlashStage(CallbackInfo ci) {
        VeyraRenderer.beginSkyVoidStage();
    }

    @Inject(method = "renderEndFlash", at = @At("RETURN"))
    private void veyra$endEndFlashStage(CallbackInfo ci) {
        VeyraRenderer.endSkyRenderStage();
    }
}
