package dev.veyra.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.VertexBuffer;
import dev.veyra.compat.RenderPipelines;
import dev.veyra.shader.IrisSkyHorizon;
import dev.veyra.shader.VeyraRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.SkyRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SkyRenderer.class)
public abstract class SkyRendererMixin {
    @Unique private VertexBuffer veyra$horizon;
    @Unique private int veyra$horizonDistance;

    // Iris draws a horizon fan below the sky disc; shader packs expect it in the sky basic stage.
    @WrapOperation(method = "renderSkyDisc", at = @At(value = "INVOKE", target =
            "Lcom/mojang/blaze3d/vertex/VertexBuffer;drawWithRenderType(Lnet/minecraft/client/renderer/RenderType;)V"))
    private void veyra$drawShaderHorizon(VertexBuffer buffer, RenderType type, Operation<Void> original) {
        if (VeyraRenderer.isRenderingEnabled() && VeyraRenderer.getSkyBasicPipeline(RenderPipelines.sky()) != null) {
            int distance = Minecraft.getInstance().options.getEffectiveRenderDistance();
            if (veyra$horizon == null || veyra$horizonDistance != distance) {
                veyra$releaseHorizon();
                float[] vertices = IrisSkyHorizon.vertices(distance);
                veyra$horizon = VertexBuffer.uploadStatic(type.mode(), type.format(), consumer -> {
                    for (int index = 0; index < vertices.length; index += 3) {
                        consumer.addVertex(vertices[index], vertices[index + 1], vertices[index + 2]);
                    }
                });
                veyra$horizonDistance = distance;
            }
            original.call(veyra$horizon, type);
        } else {
            veyra$releaseHorizon();
        }
        original.call(buffer, type);
    }

    @Inject(method = "close", at = @At("HEAD"))
    private void veyra$closeHorizon(CallbackInfo ci) {
        veyra$releaseHorizon();
    }

    @Unique
    private void veyra$releaseHorizon() {
        if (veyra$horizon != null) {
            veyra$horizon.close();
            veyra$horizon = null;
        }
    }

    @Inject(method = "renderSun", at = @At("HEAD"))
    private void veyra$beginSunStage(CallbackInfo ci) { VeyraRenderer.beginSkySunStage(); }

    @Inject(method = "renderSun", at = @At("RETURN"))
    private void veyra$endSunStage(CallbackInfo ci) { VeyraRenderer.endSkyRenderStage(); }

    @Inject(method = "renderMoon", at = @At("HEAD"))
    private void veyra$beginMoonStage(CallbackInfo ci) { VeyraRenderer.beginSkyMoonStage(); }

    @Inject(method = "renderMoon", at = @At("RETURN"))
    private void veyra$endMoonStage(CallbackInfo ci) { VeyraRenderer.endSkyRenderStage(); }

    @Inject(method = "renderEndSky", at = @At("HEAD"))
    private void veyra$beginEndSkyStage(CallbackInfo ci) { VeyraRenderer.beginSkyVoidStage(); }

    @Inject(method = "renderEndSky", at = @At("RETURN"))
    private void veyra$endEndSkyStage(CallbackInfo ci) { VeyraRenderer.endSkyRenderStage(); }
}
