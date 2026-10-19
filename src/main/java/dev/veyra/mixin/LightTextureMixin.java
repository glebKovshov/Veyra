package dev.veyra.mixin;

import dev.veyra.shader.VeyraRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Captures Iris' darknessLightFactor: the darkness lightmap scale times the darkness effect option. */
@Mixin(LightTexture.class)
public abstract class LightTextureMixin {
    @Inject(method = "updateLightTexture", at = @At("HEAD"))
    private void veyra$resetDarknessLightFactor(float partialTick, CallbackInfo callback) {
        VeyraRenderer.captureDarknessLightFactor(0.0F);
    }

    @Inject(method = "calculateDarknessScale", at = @At("RETURN"))
    private void veyra$captureDarknessLightFactor(LivingEntity entity, float gamma, float partialTick,
                                                 CallbackInfoReturnable<Float> callback) {
        VeyraRenderer.captureDarknessLightFactor((float) (callback.getReturnValue()
                * Minecraft.getInstance().options.darknessEffectScale().get()));
    }
}
