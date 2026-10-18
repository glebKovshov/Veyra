package dev.veyra.mixin;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTextureView;
import dev.veyra.shader.VeyraRenderer;
import net.vulkanmod.interfaces.shader.ExtendedRenderPipeline;
import net.vulkanmod.render.engine.VkRenderPass;
import net.vulkanmod.render.engine.VkTextureView;
import net.vulkanmod.vulkan.texture.VTextureSelector;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = VkRenderPass.class, remap = false)
public abstract class VkRenderPassMixin {
    @Inject(method = "bindTexture", at = @At("RETURN"), remap = false)
    private void veyra$bindDepthSnapshots(String name, GpuTextureView view, GpuSampler sampler, CallbackInfo ci) {
        if (!name.equals("VeyraDepthTex1Sampler") && !name.equals("VeyraDepthTex2Sampler")) {
            return;
        }
        VkRenderPass pass = (VkRenderPass) (Object) this;
        if (pass.getPipeline() == null || !(view instanceof VkTextureView vulkanView)) {
            return;
        }
        var pipeline = ExtendedRenderPipeline.of(pass.getPipeline()).getPipeline();
        var descriptor = pipeline.getImageDescriptor(name);
        var texture = vulkanView.texture();
        if (descriptor != null && !texture.isClosed() && texture.getVulkanImage() != null) {
            // External snapshots must populate VulkanMod's indexed image table for this descriptor.
            VTextureSelector.bindTexture(descriptor.imageIdx, texture.getVulkanImage());
        }
    }

    @ModifyVariable(method = "setPipeline", at = @At("HEAD"), argsOnly = true, remap = false)
    private RenderPipeline veyra$routeEntityPipeline(RenderPipeline original) {
        RenderPipeline replacement = VeyraRenderer.getHandPipeline(original);
        if (replacement == null) {
            replacement = VeyraRenderer.getEntityPipeline(original);
        }
        if (replacement == null) {
            replacement = VeyraRenderer.getBeaconBeamPipeline(original);
        }
        if (replacement == null) {
            replacement = VeyraRenderer.getParticlePipeline(original);
        }
        if (replacement == null) {
            replacement = VeyraRenderer.getWeatherPipeline(original);
        }
        if (replacement == null) {
            replacement = VeyraRenderer.getLightningPipeline(original);
        }
        if (replacement == null) {
            replacement = VeyraRenderer.getBasicPipeline(original);
        }
        if (replacement == null) {
            replacement = VeyraRenderer.getLinePipeline(original);
        }
        if (replacement == null) {
            replacement = VeyraRenderer.getGlintPipeline(original);
        }
        if (replacement == null) {
            replacement = VeyraRenderer.getSkyBasicPipeline(original);
        }
        if (replacement == null) {
            replacement = VeyraRenderer.getSkyTexturedPipeline(original);
        }
        if (replacement == null) {
            replacement = VeyraRenderer.getWorldBorderPipeline(original);
        }
        if (replacement == null) {
            replacement = VeyraRenderer.getDamagedBlockPipeline(original);
        }
        return replacement == null ? original : replacement;
    }
}
