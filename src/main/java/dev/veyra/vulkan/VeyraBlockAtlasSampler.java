package dev.veyra.vulkan;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.vulkanmod.render.engine.VkRenderPass;
import net.vulkanmod.vulkan.shader.GraphicsPipeline;

/** Binds Minecraft's live block atlas to an Iris custom texture sampler. */
public final class VeyraBlockAtlasSampler {
    private VeyraBlockAtlasSampler() {
    }

    public static void bind(VkRenderPass pass, GraphicsPipeline pipeline, String samplerName) {
        if (pipeline.getImageDescriptor(samplerName) == null) return;
        var view = Minecraft.getInstance().getTextureManager()
                .getTexture(TextureAtlas.LOCATION_BLOCKS).getTextureView();
        if (view == null || view.isClosed()) {
            throw new IllegalStateException("Minecraft's block texture atlas is not ready for " + samplerName + ".");
        }
        pass.bindTexture(samplerName, view,
                RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
    }
}
