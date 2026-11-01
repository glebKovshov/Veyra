package dev.veyra.mixin;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import dev.veyra.mixin.accessor.LevelRendererAccessor;
import dev.veyra.shader.VeyraRenderer;
import dev.veyra.shader.VeyraShadowRenderer;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.state.LevelRenderState;
import com.mojang.blaze3d.framegraph.FrameGraphBuilder;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin {
    @Inject(method = "renderLevel", at = @At("HEAD"))
    private void veyra$renderShadowPassBeforeFrameGraph(GraphicsResourceAllocator allocator, DeltaTracker deltaTracker,
                                        boolean renderBlockOutline, Camera camera, Matrix4f modelView,
                                        Matrix4f projection, Matrix4f projectionMatrix,
                                        GpuBufferSlice fogBuffer, Vector4f fogColor, boolean renderSky,
                                        CallbackInfo ci) {
        VeyraRenderer.captureFrameMatrices(modelView, projection, projectionMatrix, camera.position());
        VeyraRenderer.captureFogColor(fogColor);
        VeyraRenderer.renderBeginCompute(net.minecraft.client.Minecraft.getInstance());
    }

    @Inject(method = "renderLevel", at = @At(value = "INVOKE", target =
            "Lcom/mojang/blaze3d/framegraph/FrameGraphBuilder;execute(Lcom/mojang/blaze3d/resource/GraphicsResourceAllocator;Lcom/mojang/blaze3d/framegraph/FrameGraphBuilder$Inspector;)V",
            shift = At.Shift.BEFORE))
    private void veyra$renderShadowPass(GraphicsResourceAllocator allocator, DeltaTracker deltaTracker,
                                        boolean renderBlockOutline, Camera camera, Matrix4f modelView,
                                        Matrix4f projection, Matrix4f projectionMatrix,
                                        GpuBufferSlice fogBuffer, Vector4f fogColor, boolean renderSky,
                                        CallbackInfo ci) {
        LevelRenderState state = ((LevelRendererAccessor) (Object) this).veyra$getLevelRenderState();
        VeyraShadowRenderer.render(camera, modelView, projection, projectionMatrix, deltaTracker, state);
        VeyraRenderer.renderPrepare(net.minecraft.client.Minecraft.getInstance());
    }

    @Inject(method = "renderLevel", at = @At("RETURN"))
    private void veyra$captureDepthTex2(GraphicsResourceAllocator allocator, DeltaTracker deltaTracker,
                                        boolean renderBlockOutline, Camera camera, Matrix4f modelView,
                                        Matrix4f projection, Matrix4f projectionMatrix,
                                        GpuBufferSlice fogBuffer, Vector4f fogColor, boolean renderSky,
                                        CallbackInfo ci) {
        VeyraRenderer.captureDepthTex2();
    }
}
