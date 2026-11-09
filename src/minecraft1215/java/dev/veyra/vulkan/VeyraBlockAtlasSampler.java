package dev.veyra.vulkan;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.vulkanmod.render.engine.VkRenderPass;
import net.vulkanmod.vulkan.shader.GraphicsPipeline;

/** Binds the live atlas using its texture-owned sampling state on the old API. */
public final class VeyraBlockAtlasSampler {
    private VeyraBlockAtlasSampler() { }

    public static void bind(VkRenderPass pass, GraphicsPipeline pipeline, String samplerName) {
        if (LegacyImageDescriptors.find(pipeline.getImageDescriptors(), samplerName) == null) return;
        var atlas = Minecraft.getInstance().getTextureManager().getTexture(TextureAtlas.LOCATION_BLOCKS);
        var texture = atlas.getTexture();
        if (texture == null || texture.isClosed()) {
            throw new IllegalStateException("Minecraft's block texture atlas is not ready for " + samplerName + ".");
        }
        LegacySampler.bind(pass, samplerName, LegacyVkTextureView.create(texture), LegacySampler.from(texture));
    }
}
