package dev.veyra.mixin;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import dev.veyra.shader.VeyraShadowRenderer;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.LevelRenderer;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin {
    @Inject(method = "renderLevel", at = @At("HEAD"))
    private void veyra$renderShadowPass(GraphicsResourceAllocator allocator, DeltaTracker deltaTracker,
                                        boolean renderBlockOutline, Camera camera, Matrix4f modelView,
                                        Matrix4f projection, Matrix4f projectionMatrix,
                                        GpuBufferSlice fogBuffer, Vector4f fogColor, boolean renderSky,
                                        CallbackInfo ci) {
        VeyraShadowRenderer.render(camera, modelView, projection);
    }
}
