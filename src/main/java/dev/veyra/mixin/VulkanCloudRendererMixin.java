package dev.veyra.mixin;

import dev.veyra.shader.VeyraRenderer;
import net.vulkanmod.render.sky.CloudRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = CloudRenderer.class, remap = false)
public abstract class VulkanCloudRendererMixin {
    @Inject(method = "renderClouds", at = @At("HEAD"), remap = false)
    private void veyra$captureCloudSceneColor(float tickDelta, int renderDistance,
                                               double cameraX, double cameraY, double cameraZ,
                                               long ticks, float partialTick, CallbackInfo ci) {
        VeyraRenderer.captureCloudSceneColor();
    }
}
