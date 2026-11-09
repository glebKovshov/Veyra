package dev.veyra.mixin;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.textures.GpuTexture;
import dev.veyra.shader.VeyraRenderer;
import dev.veyra.shader.VeyraShadowRenderer;
import net.vulkanmod.interfaces.shader.ExtendedRenderPipeline;
import net.vulkanmod.render.engine.VkCommandEncoder;
import net.vulkanmod.render.engine.VkGpuTexture;
import net.vulkanmod.render.engine.VkRenderPass;
import net.vulkanmod.vulkan.Renderer;
import net.vulkanmod.vulkan.shader.GraphicsPipeline;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import static dev.veyra.vulkan.LegacyRenderHooks.CREATE_RENDER_PASS;

@Mixin(value = VkCommandEncoder.class, remap = false)
public abstract class VkCommandEncoderMixin {
    @Inject(method = "clearColorTexture", at = @At("HEAD"))
    private void veyra$acquireBeforeSwapchainClear(GpuTexture original, int color, CallbackInfo callback) {
        if (Renderer.isRecording() || !(original instanceof VkGpuTexture texture)) return;
        var renderer = Renderer.getInstance();
        var swapChain = renderer == null ? null : renderer.getSwapChain();
        if (swapChain == null || !swapChain.getImages().contains(texture.getVulkanImage())) return;
        // Loading overlays can clear after a nested frame was presented; reacquire before touching its image.
        renderer.beginFrame();
    }

    @Inject(method = "trySetup", at = @At("HEAD"), remap = false)
    private void veyra$bindShaderPackTextures(VkRenderPass pass, CallbackInfoReturnable<Boolean> cir) {
        RenderPipeline pipeline = pass.getPipeline();
        if (pipeline == null || !pipeline.getFragmentShader().getNamespace().equals("veyra")) return;
        if (ExtendedRenderPipeline.of(pipeline).getPipeline() instanceof GraphicsPipeline nativePipeline) {
            VeyraRenderer.bindShaderPackTextures(pass, nativePipeline, pipeline.getFragmentShader().getPath());
        }
    }

    @ModifyVariable(method = CREATE_RENDER_PASS, at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private GpuTexture veyra$bindShadowColorTarget(GpuTexture original) {
        return VeyraShadowRenderer.isRenderingFeatures()
                ? VeyraShadowRenderer.shadowColorTextureView().textureForBinding() : original;
    }

    @ModifyVariable(method = CREATE_RENDER_PASS, at = @At("HEAD"), argsOnly = true, ordinal = 1)
    private GpuTexture veyra$bindShadowDepthTarget(GpuTexture original) {
        return VeyraShadowRenderer.isRenderingFeatures()
                ? VeyraShadowRenderer.shadowDepthTextureView().textureForBinding() : original;
    }
}
