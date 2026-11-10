package dev.veyra.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.veyra.shader.VeyraRenderer;
import dev.veyra.shader.VeyraShadowRenderer;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Minecraft 1.21.1 draws the level inline instead of through a frame graph: the fog color is set,
 * the main target is cleared, then the sky and terrain are drawn. Veyra's frame work hangs on the
 * same moments the frame graph version uses: after the fog color, and before the world is drawn.
 */
@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin {
    @Unique private static final String RENDER_LEVEL = "renderLevel(Lnet/minecraft/client/DeltaTracker;ZLnet/minecraft/client/Camera;"
            + "Lnet/minecraft/client/renderer/GameRenderer;Lnet/minecraft/client/renderer/LightTexture;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;)V";
    @Unique private final Matrix4f veyra$cullProjection = new Matrix4f();

    @Inject(method = "prepareCullFrustum", at = @At("HEAD"))
    private void veyra$captureCullProjection(net.minecraft.world.phys.Vec3 position, Matrix4f view,
            Matrix4f projection, CallbackInfo callback) {
        // The render projection has Vulkan conventions; native camera-cube expansion needs the culling projection.
        veyra$cullProjection.set(projection);
    }

    @Inject(method = RENDER_LEVEL, at = @At("HEAD"))
    private void veyra$captureMatrices(DeltaTracker deltaTracker, boolean outline, Camera camera, GameRenderer gameRenderer,
            LightTexture lightTexture, Matrix4f modelView, Matrix4f projection, CallbackInfo callback) {
        VeyraRenderer.captureFrameMatrices(modelView, projection, veyra$cullProjection, camera.getPosition());
    }

    @Inject(method = RENDER_LEVEL, at = @At(value = "INVOKE", shift = At.Shift.AFTER,
            target = "Lnet/minecraft/client/renderer/FogRenderer;levelFogColor()V"))
    private void veyra$captureFogBeforeBegin(DeltaTracker deltaTracker, boolean outline, Camera camera, GameRenderer gameRenderer,
            LightTexture lightTexture, Matrix4f modelView, Matrix4f projection, CallbackInfo callback) {
        float[] fog = RenderSystem.getShaderFogColor();
        VeyraRenderer.captureFogColor(new Vector4f(fog[0], fog[1], fog[2], fog[3]));
        VeyraRenderer.renderBeginCompute(Minecraft.getInstance());
    }

    @Inject(method = RENDER_LEVEL, at = @At(value = "INVOKE", ordinal = 0,
            target = "Lcom/mojang/blaze3d/systems/RenderSystem;clear(IZ)V"))
    private void veyra$renderShadowsBeforeWorld(DeltaTracker deltaTracker, boolean outline, Camera camera, GameRenderer gameRenderer,
            LightTexture lightTexture, Matrix4f modelView, Matrix4f projection, CallbackInfo callback) {
        VeyraShadowRenderer.render(camera, modelView, projection, veyra$cullProjection, deltaTracker);
        VeyraRenderer.renderPrepare(Minecraft.getInstance());
    }
}
