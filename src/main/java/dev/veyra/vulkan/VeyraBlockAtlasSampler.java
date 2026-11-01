package dev.veyra.vulkan;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.vulkanmod.render.engine.VkRenderPass;
import net.vulkanmod.vulkan.shader.GraphicsPipeline;
import net.vulkanmod.vulkan.texture.SamplerInfo;

import static org.lwjgl.vulkan.VK10.*;

/** Binds Minecraft's live block atlas to an Iris custom texture sampler. */
public final class VeyraBlockAtlasSampler {
    private VeyraBlockAtlasSampler() {
    }

    public static SamplerInfo terrainSamplerInfo(int mipLevels, boolean anisotropic, int maxAnisotropy) {
        // Pack shaders do not run VulkanMod's sampleNearest UV correction for its linear atlas sampler.
        return new SamplerInfo(VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE, VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE,
                VK_FILTER_LINEAR, VK_FILTER_NEAREST, VK_SAMPLER_MIPMAP_MODE_LINEAR,
                mipLevels - 1, anisotropic, maxAnisotropy, -1);
    }

    public static void bind(VkRenderPass pass, GraphicsPipeline pipeline, String samplerName) {
        if (pipeline.getImageDescriptor(samplerName) == null) return;
        var atlas = Minecraft.getInstance().getTextureManager().getTexture(TextureAtlas.LOCATION_BLOCKS);
        var view = atlas.getTextureView();
        if (view == null || view.isClosed()) {
            throw new IllegalStateException("Minecraft's block texture atlas is not ready for " + samplerName + ".");
        }
        pass.bindTexture(samplerName, view, atlas.getSampler());
    }
}
