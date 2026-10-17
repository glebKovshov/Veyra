package dev.veyra.mixin;

import dev.veyra.shader.VeyraShadowRenderer;
import dev.veyra.shader.VeyraRenderer;
import net.vulkanmod.render.chunk.WorldRenderer;
import net.vulkanmod.vulkan.pass.MainPass;
import net.vulkanmod.render.vertex.TerrainRenderType;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = WorldRenderer.class, remap = false)
public abstract class WorldRendererMixin {
    @Inject(method = "renderSectionLayer", at = @At("HEAD"), remap = false)
    private void veyra$captureDepthTex1(TerrainRenderType renderType,
                                       double camX, double camY, double camZ,
                                       Matrix4f modelView, Matrix4f projection,
                                       CallbackInfo ci) {
        if (renderType == TerrainRenderType.TRANSLUCENT) {
            VeyraRenderer.captureDepthTex1();
        }
    }

    @Redirect(method = "renderSectionLayer",
            at = @At(value = "INVOKE", target = "Lnet/vulkanmod/vulkan/pass/MainPass;rebindMainTarget()V"),
            remap = false)
    private void veyra$keepShadowTargetBound(MainPass mainPass) {
        if (!VeyraShadowRenderer.isRendering()) {
            mainPass.rebindMainTarget();
        }
    }
}
