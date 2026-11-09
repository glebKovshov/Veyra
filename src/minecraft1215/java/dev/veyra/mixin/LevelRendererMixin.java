package dev.veyra.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import dev.veyra.shader.VeyraRenderer;
import dev.veyra.shader.VeyraShadowRenderer;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin {
    @org.spongepowered.asm.mixin.Unique private final Matrix4f veyra$cullProjection = new Matrix4f();

    @Inject(method = "prepareCullFrustum", at = @At("HEAD"))
    private void veyra$captureCullProjection(net.minecraft.world.phys.Vec3 position, Matrix4f view,
            Matrix4f projection, CallbackInfo callback) {
        // The render projection has Vulkan conventions; native camera-cube expansion needs the culling projection.
        veyra$cullProjection.set(projection);
    }

    @Inject(method = "renderLevel", at = @At("HEAD"))
    private void veyra$captureMatrices(GraphicsResourceAllocator allocator, DeltaTracker deltaTracker,
            boolean outline, Camera camera, GameRenderer gameRenderer, Matrix4f modelView,
            Matrix4f projection, CallbackInfo callback) {
        VeyraRenderer.captureFrameMatrices(modelView, projection, veyra$cullProjection, camera.getPosition());
    }

    @ModifyExpressionValue(method = "renderLevel", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/client/renderer/FogRenderer;computeFogColor(Lnet/minecraft/client/Camera;FLnet/minecraft/client/multiplayer/ClientLevel;IF)Lorg/joml/Vector4f;"))
    private Vector4f veyra$captureFogBeforeBegin(Vector4f color) {
        VeyraRenderer.captureFogColor(color);
        VeyraRenderer.renderBeginCompute(Minecraft.getInstance());
        return color;
    }

    @Inject(method = "renderLevel", at = @At(value = "INVOKE", remap = false, target =
            "Lcom/mojang/blaze3d/framegraph/FrameGraphBuilder;execute(Lcom/mojang/blaze3d/resource/GraphicsResourceAllocator;Lcom/mojang/blaze3d/framegraph/FrameGraphBuilder$Inspector;)V"))
    private void veyra$renderShadowsBeforeFrameGraph(GraphicsResourceAllocator allocator, DeltaTracker deltaTracker,
            boolean outline, Camera camera, GameRenderer gameRenderer, Matrix4f modelView,
            Matrix4f projection, CallbackInfo callback) {
        VeyraShadowRenderer.render(camera, modelView, projection, veyra$cullProjection, deltaTracker);
        VeyraRenderer.renderPrepare(Minecraft.getInstance());
    }
}
