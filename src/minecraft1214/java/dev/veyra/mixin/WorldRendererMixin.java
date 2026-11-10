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
    private void veyra$captureDepthTex1(net.minecraft.client.renderer.RenderType renderType,
                                       double camX, double camY, double camZ,
                                       Matrix4f modelView, Matrix4f projection,
                                       CallbackInfo ci) {
        if (TerrainRenderType.get(renderType) == TerrainRenderType.TRANSLUCENT && !VeyraShadowRenderer.isRendering()) {
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

    // VulkanMod 0.5.6 for 1.21.4 applies the terrain render type's state, whose main-target output
    // ends the shadow pass. The shadow map stays bound while that state is applied and cleared.
    @com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation(method = "renderSectionLayer",
            at = {@At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/RenderType;setupRenderState()V", remap = true),
                  @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/RenderType;clearRenderState()V", remap = true)},
            remap = false)
    private void veyra$keepShadowTargetBound(net.minecraft.client.renderer.RenderType type,
            com.llamalad7.mixinextras.injector.wrapoperation.Operation<Void> original) {
        if (!VeyraShadowRenderer.isRendering()) {
            original.call(type);
            return;
        }
        dev.veyra.compat.LegacyDraws.keepBoundTarget(() -> original.call(type));
    }
}
