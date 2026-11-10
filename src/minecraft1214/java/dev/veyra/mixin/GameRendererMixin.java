package dev.veyra.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.veyra.shader.VeyraRenderer;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    @WrapMethod(method = "render")
    private void veyra$renderFrame(DeltaTracker deltaTracker, boolean renderLevel, Operation<Void> original) {
        VeyraRenderer.setGuiRendering(false);
        try {
            original.call(deltaTracker, renderLevel);
            VeyraRenderer.render(Minecraft.getInstance());
        } finally { VeyraRenderer.setGuiRendering(false); }
    }

    // Minecraft 1.21.4 clears depth through the render system before drawing the GUI.
    @Inject(method = "render", at = @At(value = "INVOKE", ordinal = 0,
            target = "Lcom/mojang/blaze3d/systems/RenderSystem;clear(I)V"))
    private void veyra$finishWorldBeforeGui(CallbackInfo callback) {
        VeyraRenderer.render(Minecraft.getInstance());
        VeyraRenderer.setGuiRendering(true);
    }

    @Redirect(method = "renderLevel", at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/systems/RenderSystem;clear(I)V"))
    private void veyra$preserveWorldDepthForHand(int mask) {
        if (!VeyraRenderer.isRenderingEnabled()) com.mojang.blaze3d.systems.RenderSystem.clear(mask);
    }

    @WrapMethod(method = "renderItemInHand")
    private void veyra$renderHand(Camera camera, float partialTick, Matrix4f projection, Operation<Void> original) {
        VeyraRenderer.beginHandRendering();
        try { original.call(camera, partialTick, projection); }
        finally { VeyraRenderer.endHandRendering(); }
    }
}
