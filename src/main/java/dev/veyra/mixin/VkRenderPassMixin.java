package dev.veyra.mixin;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTextureView;
import dev.veyra.shader.VeyraShadowRenderer;
import dev.veyra.shader.VeyraRenderer;
import dev.veyra.vulkan.VeyraNoiseTexture;
import dev.veyra.vulkan.VeyraSpecularTextures;
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
    @Inject(method = "setPipeline", at = @At("RETURN"), remap = false)
    private void veyra$bindNoiseSampler(RenderPipeline ignored, CallbackInfo ci) {
        VkRenderPass pass = (VkRenderPass) (Object) this;
        RenderPipeline active = pass.getPipeline();
        if (active == null) return;
        var pipeline = ExtendedRenderPipeline.of(active).getPipeline();
        if (pipeline != null) VeyraRenderer.bindCloudSceneColor(pass, pipeline);
        if (pipeline != null && active.getFragmentShader().getNamespace().equals("veyra"))
            { VeyraShadowRenderer.bindEntityShadowColor(pass, pipeline); VeyraShadowRenderer.bindEntityOpaqueDepth(pass, pipeline); }
        if (pipeline != null && pipeline.getImageDescriptor(VeyraNoiseTexture.SAMPLER_NAME) != null) {
            VeyraNoiseTexture.bind(pass);
        }
        if (pipeline != null && active.getFragmentShader().getNamespace().equals("veyra")
                && active.getSamplers().contains("Sampler3")) {
            VeyraShadowRenderer.bindEntityShadowSampler(pass, pipeline);
        }
    }

    @Inject(method = "bindTexture", at = @At("HEAD"), cancellable = true, remap = false)
    private void veyra$forceNoiseRepeatSampler(
            String name, GpuTextureView view, GpuSampler sampler, CallbackInfo ci) {
        if (!name.equals(VeyraNoiseTexture.SAMPLER_NAME) || !VeyraNoiseTexture.hasTexture()) return;
        GpuSampler repeatSampler = VeyraNoiseTexture.sampler();
        if (sampler != repeatSampler) {
            ((VkRenderPass) (Object) this).bindTexture(name, view, repeatSampler);
            ci.cancel();
        }
    }

    @Inject(method = "bindTexture", at = @At("RETURN"), remap = false)
    private void veyra$bindSpecular(String name, GpuTextureView view, GpuSampler sampler, CallbackInfo ci) {
        VkRenderPass pass = (VkRenderPass) (Object) this;
        if (name.equals("Sampler0") && pass.getPipeline() != null
                && pass.getPipeline().getFragmentShader().getNamespace().equals("veyra")) {
            VeyraSpecularTextures.bindEntity(pass, view);
        }
    }

    @Inject(method = "bindTexture", at = @At("RETURN"), remap = false)
    private void veyra$bindDepthSnapshots(String name, GpuTextureView view, GpuSampler sampler, CallbackInfo ci) {
        if (!name.equals("Sampler1Sampler") && !name.equals("VeyraDepthTex1Sampler") && !name.equals("VeyraDepthTex2Sampler")) {
            return;
        }
        VkRenderPass pass = (VkRenderPass) (Object) this;
        if (pass.getPipeline() == null || !pass.getPipeline().getFragmentShader().getNamespace().equals("veyra")
                || !(view instanceof VkTextureView vulkanView)) {
            return;
        }
        var pipeline = ExtendedRenderPipeline.of(pass.getPipeline()).getPipeline();
        var descriptor = pipeline.getImageDescriptor(name);
        var texture = vulkanView.texture();
        if (descriptor != null && !texture.isClosed() && texture.getVulkanImage() != null) {
            descriptor.setLayout(org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL);
            descriptor.isReadOnlyLayout = false;
            // External snapshots must populate VulkanMod's indexed image table for this descriptor.
            VTextureSelector.bindTexture(descriptor.imageIdx, texture.getVulkanImage());
        }
    }

    @ModifyVariable(method = "setPipeline", at = @At("HEAD"), argsOnly = true, remap = false)
    private RenderPipeline veyra$routeEntityPipeline(RenderPipeline original) {
        RenderPipeline replacement = VeyraRenderer.getHandPipeline(original);
        if (replacement == null) {
            replacement = VeyraRenderer.getSignTextPipeline(original);
        }
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
