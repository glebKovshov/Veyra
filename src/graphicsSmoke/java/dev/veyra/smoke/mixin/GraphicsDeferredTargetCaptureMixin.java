package dev.veyra.smoke.mixin;

import dev.veyra.shader.ShaderPassPhase;
import dev.veyra.shader.VeyraRenderer;
import dev.veyra.smoke.GraphicsSmoke;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = VeyraRenderer.class, remap = false)
public abstract class GraphicsDeferredTargetCaptureMixin {
    @Inject(method = "renderPhase", at = @org.spongepowered.asm.mixin.injection.At("HEAD"), remap = false)
    private static void veyra$captureBeforeDeferred(Minecraft minecraft, ShaderPassPhase phase, CallbackInfo ci) {
        if (phase == ShaderPassPhase.DEFERRED) GraphicsSmoke.captureShaderTargetsAtStage("before-deferred");
    }

    @Inject(method = "renderDeferred", at = @org.spongepowered.asm.mixin.injection.At("RETURN"), remap = false)
    private static void veyra$captureAfterDeferred(Minecraft minecraft, CallbackInfo ci) {
        GraphicsSmoke.captureShaderTargetsAtStage("after-deferred");
    }

    @Inject(method = "render", at = @org.spongepowered.asm.mixin.injection.At("RETURN"), remap = false)
    private static void veyra$captureAfterComposite(Minecraft minecraft, CallbackInfo ci) {
        GraphicsSmoke.captureShaderTargetsAtStage("after-composite");
    }
}
