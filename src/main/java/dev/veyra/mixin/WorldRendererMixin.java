package dev.veyra.mixin;

import dev.veyra.shader.VeyraShadowRenderer;
import dev.veyra.shader.VeyraRenderer;
import dev.veyra.vulkan.VeyraBlockAtlasSampler;
import dev.veyra.vulkan.VeyraGeometryTargets;
import net.minecraft.client.Minecraft;
import net.minecraft.client.TextureFilteringMethod;
import net.vulkanmod.render.chunk.WorldRenderer;
import net.vulkanmod.vulkan.Renderer;
import net.vulkanmod.vulkan.pass.MainPass;
import net.vulkanmod.vulkan.shader.GraphicsPipeline;
import net.vulkanmod.vulkan.texture.SamplerManager;
import net.vulkanmod.vulkan.texture.VulkanImage;
import net.vulkanmod.render.vertex.TerrainRenderType;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = WorldRenderer.class, remap = false)
public abstract class WorldRendererMixin {
    @Redirect(method = "renderSectionLayer", at = @At(value = "INVOKE", target =
            "Lnet/vulkanmod/vulkan/texture/VulkanImage;setSampler(J)V"), remap = false)
    private void veyra$keepShaderPackAtlasSharp(VulkanImage image, long sampler) {
        if (Renderer.getInstance().getBoundPipeline() instanceof GraphicsPipeline pipeline
                && VeyraGeometryTargets.layout(pipeline) != null) {
            var options = Minecraft.getInstance().options;
            sampler = SamplerManager.getSampler(VeyraBlockAtlasSampler.terrainSamplerInfo(image.mipLevels,
                    options.textureFiltering().get() == TextureFilteringMethod.ANISOTROPIC,
                    options.maxAnisotropyValue()));
        }
        image.setSampler(sampler);
    }

    @Inject(method = "renderSectionLayer", at = @At("HEAD"), remap = false)
    private void veyra$captureDepthTex1(TerrainRenderType renderType,
                                       double camX, double camY, double camZ,
                                       Matrix4f modelView, Matrix4f projection,
                                       CallbackInfo ci) {
        if (renderType == TerrainRenderType.TRANSLUCENT && !VeyraShadowRenderer.isRendering()) {
            VeyraRenderer.captureDepthTex1();
            VeyraRenderer.renderDeferred(net.minecraft.client.Minecraft.getInstance());
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
