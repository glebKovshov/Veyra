package dev.veyra.mixin;

import dev.veyra.shader.VeyraRenderer;
import dev.veyra.shader.IrisSkyHorizon;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.SkyRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SkyRenderer.class)
public abstract class SkyRendererMixin {
    @Unique private GpuBuffer veyra$horizon;
    @Unique private int veyra$horizonDistance;

    @WrapOperation(method = "renderSkyDisc", at = @At(value = "INVOKE", remap = false, target =
            "Lcom/mojang/blaze3d/systems/RenderPass;setVertexBuffer(ILcom/mojang/blaze3d/buffers/GpuBuffer;)V"))
    private void veyra$drawShaderHorizon(RenderPass pass, int slot, GpuBuffer originalBuffer, Operation<Void> original) {
        if (VeyraRenderer.isRenderingEnabled() && VeyraRenderer.getSkyBasicPipeline(RenderPipelines.SKY) != null) {
            int distance = Minecraft.getInstance().options.getEffectiveRenderDistance();
            if (veyra$horizon == null || veyra$horizonDistance != distance) {
                veyra$releaseHorizon();
                float[] vertices = IrisSkyHorizon.vertices(distance);
                try (var stack = org.lwjgl.system.MemoryStack.stackPush()) {
                    var bytes = stack.malloc(vertices.length * Float.BYTES);
                    bytes.asFloatBuffer().put(vertices);
                    veyra$horizon = dev.veyra.vulkan.VeyraVertexBuffers.create(
                            RenderSystem.getDevice(), () -> "Veyra sky horizon", bytes);
                }
                veyra$horizonDistance = distance;
            }
            original.call(pass, slot, veyra$horizon);
            pass.draw(0, 10);
        } else {
            veyra$releaseHorizon();
        }
        original.call(pass, slot, originalBuffer);
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

}
