package dev.veyra.mixin;

import dev.veyra.shader.VeyraShadowRenderer;
import dev.veyra.shader.VeyraRenderer;
import dev.veyra.vulkan.VeyraBlockAtlasSampler;
import dev.veyra.vulkan.VeyraGeometryTargets;
import net.minecraft.client.Minecraft;
import net.vulkanmod.render.chunk.WorldRenderer;
import net.vulkanmod.vulkan.Renderer;
import net.vulkanmod.vulkan.memory.buffer.IndirectBuffer;
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
    @org.spongepowered.asm.mixin.Unique private com.mojang.blaze3d.textures.GpuTexture veyra$atlasTexture;
    @org.spongepowered.asm.mixin.Unique private dev.veyra.vulkan.LegacySampler veyra$atlasSampler;

    @Redirect(method = "renderSectionLayer", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/client/renderer/texture/AbstractTexture;setUseMipmaps(Z)V"), remap = false)
    private void veyra$keepShaderPackAtlasSharp(net.minecraft.client.renderer.texture.AbstractTexture atlas, boolean mipmaps) {
        atlas.setUseMipmaps(mipmaps);
        if (Renderer.getInstance().getBoundPipeline() instanceof GraphicsPipeline pipeline
                && VeyraGeometryTargets.layout(pipeline) != null) {
            veyra$atlasTexture = atlas.getTexture();
            veyra$atlasSampler = dev.veyra.vulkan.LegacySampler.from(veyra$atlasTexture);
            dev.veyra.vulkan.LegacySampler.getSampler(com.mojang.blaze3d.textures.AddressMode.CLAMP_TO_EDGE,
                    com.mojang.blaze3d.textures.AddressMode.CLAMP_TO_EDGE,
                    com.mojang.blaze3d.textures.FilterMode.LINEAR,
                    com.mojang.blaze3d.textures.FilterMode.NEAREST, mipmaps).apply(veyra$atlasTexture);
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
            // Iris copies depthtex2 here too (beginHand, before translucents and deferred): all opaque
            // depth without the hand. Captured at the end of the world render instead, deferred programs
            // read the previous frame's depth with translucents, which breaks depth comparisons against
            // depthtex0 whenever TAA jitter or the camera moves (Sildur's shadows vanish and flicker).
            VeyraRenderer.captureDepthTex2();
            VeyraRenderer.renderDeferred(net.minecraft.client.Minecraft.getInstance());
        }
    }

    @Redirect(method = "setupRenderer", at = @At(value = "INVOKE", target =
            "Lnet/vulkanmod/vulkan/memory/buffer/IndirectBuffer;reset()V"), remap = false)
    private void veyra$keepShadowIndirectCommands(IndirectBuffer buffer) {
        // The shadow pass records indirect terrain draws into this frame's host-visible buffer before
        // the main graph is restored. Resetting here would let the main pass overwrite those commands
        // before submission, so the shadow map would draw the camera's sections instead of its own.
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
