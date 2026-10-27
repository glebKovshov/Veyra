package dev.veyra.mixin;

import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.textures.GpuTexture;
import dev.veyra.shader.VeyraRenderer;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    @Redirect(method = "renderLevel", at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/systems/CommandEncoder;clearDepthTexture(Lcom/mojang/blaze3d/textures/GpuTexture;D)V"))
    private void veyra$preserveWorldDepthForHand(CommandEncoder encoder, GpuTexture texture, double depth) {
        if (!VeyraRenderer.isRenderingEnabled()) encoder.clearDepthTexture(texture, depth);
    }

    @Inject(method = "render", at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/systems/CommandEncoder;clearDepthTexture(Lcom/mojang/blaze3d/textures/GpuTexture;D)V"))
    private void veyra$finishShaderFrameBeforeGuiDepthClear(CallbackInfo callback) {
        VeyraRenderer.render(net.minecraft.client.Minecraft.getInstance());
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void veyra$finishShaderFrameWithoutGui(CallbackInfo callback) {
        // The phase cursor keeps this fallback harmless if composite already ran.
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
