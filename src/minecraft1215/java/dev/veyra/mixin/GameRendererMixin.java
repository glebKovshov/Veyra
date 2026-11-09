package dev.veyra.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.textures.GpuTexture;
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
import static dev.veyra.vulkan.LegacyRenderHooks.CLEAR_DEPTH_TEXTURE;

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

    @Inject(method = "render", at = @At(value = "INVOKE", ordinal = 0, remap = false,
            target = "Lcom/mojang/blaze3d/systems/CommandEncoder;" + CLEAR_DEPTH_TEXTURE))
    private void veyra$finishWorldBeforeGui(CallbackInfo callback) {
        VeyraRenderer.render(Minecraft.getInstance());
        VeyraRenderer.setGuiRendering(true);
    }

    @Redirect(method = "renderLevel", at = @At(value = "INVOKE", remap = false,
            target = "Lcom/mojang/blaze3d/systems/CommandEncoder;" + CLEAR_DEPTH_TEXTURE))
    private void veyra$preserveWorldDepthForHand(CommandEncoder encoder, GpuTexture texture, double depth) {
        if (!VeyraRenderer.isRenderingEnabled()) encoder.clearDepthTexture(texture, depth);
    }

    @WrapMethod(method = "renderItemInHand")
    private void veyra$renderHand(Camera camera, float partialTick, Matrix4f projection, Operation<Void> original) {
        VeyraRenderer.beginHandRendering();
        try { original.call(camera, partialTick, projection); }
        finally { VeyraRenderer.endHandRendering(); }
    }
}
