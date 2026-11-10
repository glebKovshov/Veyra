package dev.veyra.mixin;

import dev.veyra.shader.VeyraShadowRenderer;
import dev.veyra.shader.VeyraRenderer;
import dev.veyra.vulkan.VeyraGeometryTargets;
import net.minecraft.client.Minecraft;
import net.vulkanmod.render.chunk.WorldRenderer;
import net.vulkanmod.vulkan.Renderer;
import net.vulkanmod.vulkan.memory.buffer.IndirectBuffer;
import net.vulkanmod.vulkan.pass.MainPass;
import net.vulkanmod.vulkan.shader.GraphicsPipeline;
import net.vulkanmod.render.vertex.TerrainRenderType;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * VulkanMod 0.6.7 for 1.21.1 draws terrain layers like the 0.6.x renderer (its own render types,
 * a main-target rebind) but sets the block atlas sampler itself before binding textures, like 0.5.6.
 */
@Mixin(value = WorldRenderer.class, remap = false)
public abstract class WorldRendererMixin {
    @org.spongepowered.asm.mixin.Unique private dev.veyra.compat.GpuTexture veyra$atlasTexture;
    @org.spongepowered.asm.mixin.Unique private dev.veyra.vulkan.LegacySampler veyra$atlasSampler;

    @Inject(method = "renderSectionLayer", at = @At(value = "INVOKE", target =
            "Lnet/vulkanmod/vulkan/texture/VTextureSelector;bindShaderTextures(Lnet/vulkanmod/vulkan/shader/Pipeline;)V"), remap = false)
    private void veyra$keepShaderPackAtlasSharp(CallbackInfo callback) {
        if (Renderer.getInstance().getBoundPipeline() instanceof GraphicsPipeline pipeline
                && VeyraGeometryTargets.layout(pipeline) != null) {
            veyra$atlasTexture = dev.veyra.compat.VkGpuTexture.of(Minecraft.getInstance().getTextureManager().getTexture(
                    net.minecraft.client.renderer.texture.TextureAtlas.LOCATION_BLOCKS));
            veyra$atlasSampler = dev.veyra.vulkan.LegacySampler.from(veyra$atlasTexture);
            dev.veyra.vulkan.LegacySampler.getSampler(dev.veyra.compat.AddressMode.CLAMP_TO_EDGE,
                    dev.veyra.compat.AddressMode.CLAMP_TO_EDGE,
                    dev.veyra.compat.FilterMode.LINEAR,
                    dev.veyra.compat.FilterMode.NEAREST, veyra$atlasSampler.mipmaps()).apply(veyra$atlasTexture);
        }
    }

    @Inject(method = "renderSectionLayer", at = @At("RETURN"), remap = false)
    private void veyra$restoreAtlasSampler(CallbackInfo callback) {
        if (veyra$atlasSampler == null) return;
        veyra$atlasSampler.apply(veyra$atlasTexture);
        veyra$atlasSampler = null;
        veyra$atlasTexture = null;
    }

    @Inject(method = "renderSectionLayer", at = @At("HEAD"), remap = false)
    private void veyra$captureDepthTex1(TerrainRenderType renderType,
                                       double camX, double camY, double camZ,
                                       Matrix4f modelView, Matrix4f projection,
                                       CallbackInfo ci) {
        if (renderType == TerrainRenderType.TRANSLUCENT && !VeyraShadowRenderer.isRendering()) {
            VeyraRenderer.captureDepthTex1();
            // Iris copies depthtex2 here too: all opaque depth without the hand (see the shared hook).
            VeyraRenderer.captureDepthTex2();
            VeyraRenderer.renderDeferred(net.minecraft.client.Minecraft.getInstance());
        }
    }

    @Redirect(method = "setupRenderer", at = @At(value = "INVOKE", target =
            "Lnet/vulkanmod/vulkan/memory/buffer/IndirectBuffer;reset()V"), remap = false)
    private void veyra$keepShadowIndirectCommands(IndirectBuffer buffer) {
        // The shadow pass records indirect terrain draws into this frame's buffer before the main
        // pass; resetting here would let the main pass overwrite them before submission.
        if (!VeyraShadowRenderer.preservesIndirectCommands()) {
            buffer.reset();
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
