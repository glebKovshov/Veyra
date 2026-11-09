package dev.veyra.mixin;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import dev.veyra.vulkan.LegacySampler;
import com.mojang.blaze3d.textures.GpuTexture;
import dev.veyra.vulkan.LegacyVkTextureView;
import dev.veyra.shader.VeyraShadowRenderer;
import dev.veyra.shader.VeyraRenderer;
import dev.veyra.vulkan.VeyraNoiseTexture;
import dev.veyra.vulkan.VeyraGeometryTargets;
import dev.veyra.vulkan.VeyraNormalTextures;
import dev.veyra.vulkan.VeyraSpecularTextures;
import net.minecraft.client.Minecraft;
import net.vulkanmod.interfaces.shader.ExtendedRenderPipeline;
import net.vulkanmod.render.engine.VkRenderPass;
import net.vulkanmod.render.engine.VkGpuTexture;
import net.vulkanmod.vulkan.shader.GraphicsPipeline;
import net.vulkanmod.vulkan.texture.VTextureSelector;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import static dev.veyra.vulkan.LegacyRenderHooks.BIND_SAMPLER;

@Mixin(value = VkRenderPass.class, remap = false)
public abstract class VkRenderPassMixin {
    // Post-input binding applies mip filtering before this texture-only native API is called.
    @Inject(method = "setPipeline", at = @At("RETURN"), remap = false)
    private void veyra$bindNoiseSampler(RenderPipeline ignored, CallbackInfo ci) {
        VkRenderPass pass = (VkRenderPass) (Object) this;
        RenderPipeline active = pass.getPipeline();
        if (active == null) return;
        if (!(ExtendedRenderPipeline.of(active).getPipeline() instanceof GraphicsPipeline pipeline)) return;
        if (pipeline != null) VeyraGeometryTargets.beforeBind(pipeline);
        if (pipeline != null && active.getFragmentShader().getNamespace().equals("veyra")
                && active.getSamplers().contains("Sampler2")) {
            GpuTexture lightmap = Minecraft.getInstance().gameRenderer.lightTexture().getTarget();
            var descriptor = pipeline.getImageDescriptor("Sampler2");
            if (descriptor != null && lightmap instanceof VkGpuTexture texture) {
                if (!texture.isClosed() && texture.getVulkanImage() != null) {
                    descriptor.setLayout(org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL);
                    descriptor.isReadOnlyLayout = false;
                    VTextureSelector.bindTexture(descriptor.imageIdx, texture.getVulkanImage());
                }
            }
        }
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

    @Inject(method = BIND_SAMPLER, at = @At("HEAD"), remap = false)
    private void veyra$forceNoiseRepeatSampler(
            String name, GpuTexture texture, CallbackInfo ci) {
        if (texture == null || !name.equals(VeyraNoiseTexture.SAMPLER_NAME) || !VeyraNoiseTexture.hasTexture()) return;
        VeyraNoiseTexture.sampler().apply(texture);
    }

    @Inject(method = BIND_SAMPLER, at = @At("RETURN"), remap = false)
    private void veyra$bindSpecular(String name, GpuTexture texture, CallbackInfo ci) {
        VkRenderPass pass = (VkRenderPass) (Object) this;
        if (texture != null && name.equals("Sampler0") && pass.getPipeline() != null
                && pass.getPipeline().getFragmentShader().getNamespace().equals("veyra")) {
            var view = LegacyVkTextureView.create(texture);
            VeyraSpecularTextures.bindEntity(pass, view);
            VeyraNormalTextures.bindEntity(pass, view, LegacySampler.from(texture));
        }
    }

    @Inject(method = BIND_SAMPLER, at = @At("RETURN"), remap = false)
    private void veyra$bindDepthSnapshots(String name, GpuTexture nativeTexture, CallbackInfo ci) {
        if (!name.equals("Sampler1Sampler") && !name.equals("VeyraDepthTex1Sampler") && !name.equals("VeyraDepthTex2Sampler")) {
            return;
        }
        VkRenderPass pass = (VkRenderPass) (Object) this;
        if (pass.getPipeline() == null || !pass.getPipeline().getFragmentShader().getNamespace().equals("veyra")
                || !(nativeTexture instanceof VkGpuTexture texture)) {
            return;
        }
        var pipeline = ExtendedRenderPipeline.of(pass.getPipeline()).getPipeline();
        if (pipeline == null) return;
        var descriptor = pipeline.getImageDescriptor(name);
        if (descriptor != null && !texture.isClosed() && texture.getVulkanImage() != null) {
            descriptor.setLayout(org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL);
            descriptor.isReadOnlyLayout = false;
            // External snapshots must populate VulkanMod's indexed image table for this descriptor.
            VTextureSelector.bindTexture(descriptor.imageIdx, texture.getVulkanImage());
        }
    }

    @ModifyVariable(method = "setPipeline", at = @At("HEAD"), argsOnly = true, remap = false)
    private RenderPipeline veyra$routeEntityPipeline(RenderPipeline original) {
        if (VeyraRenderer.isGuiRendering()) return original;
        if (!VeyraRenderer.shaderPackTexturesReady()) return original;
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
