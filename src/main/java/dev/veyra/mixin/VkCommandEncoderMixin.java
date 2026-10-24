package dev.veyra.mixin;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.textures.GpuTextureView;
import dev.veyra.shader.VeyraRenderer;
import dev.veyra.shader.VeyraShadowRenderer;
import net.vulkanmod.interfaces.shader.ExtendedRenderPipeline;
import net.vulkanmod.render.engine.VkCommandEncoder;
import net.vulkanmod.render.engine.VkRenderPass;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = VkCommandEncoder.class, remap = false)
public abstract class VkCommandEncoderMixin {
    @Inject(method = "trySetup", at = @At("HEAD"), remap = false)
    private void veyra$bindShaderPackTextures(VkRenderPass pass, CallbackInfoReturnable<Boolean> cir) {
        RenderPipeline pipeline = pass.getPipeline();
        if (pipeline == null || !pipeline.getFragmentShader().getNamespace().equals("veyra")) return;
        var nativePipeline = ExtendedRenderPipeline.of(pipeline).getPipeline();
        if (nativePipeline != null) {
            VeyraRenderer.bindShaderPackTextures(pass, nativePipeline, pipeline.getFragmentShader().getPath());
        }
    }

    private static final String CREATE_RENDER_PASS = "createRenderPass(Ljava/util/function/Supplier;"
            + "Lcom/mojang/blaze3d/textures/GpuTextureView;Ljava/util/OptionalInt;"
            + "Lcom/mojang/blaze3d/textures/GpuTextureView;Ljava/util/OptionalDouble;)"
            + "Lcom/mojang/blaze3d/systems/RenderPass;";

    @ModifyVariable(method = CREATE_RENDER_PASS, at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private GpuTextureView veyra$bindShadowColorTarget(GpuTextureView original) {
        return VeyraShadowRenderer.isRenderingFeatures()
                ? VeyraShadowRenderer.shadowColorTextureView() : original;
    }

    @ModifyVariable(method = CREATE_RENDER_PASS, at = @At("HEAD"), argsOnly = true, ordinal = 1)
    private GpuTextureView veyra$bindShadowDepthTarget(GpuTextureView original) {
        return VeyraShadowRenderer.isRenderingFeatures()
                ? VeyraShadowRenderer.shadowDepthTextureView() : original;
    }
}
