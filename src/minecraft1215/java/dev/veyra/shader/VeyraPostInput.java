package dev.veyra.shader;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.resource.ResourceHandle;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.textures.FilterMode;
import dev.veyra.accessor.RenderTargetAccess;
import dev.veyra.vulkan.LegacyTextureView;
import dev.veyra.vulkan.LegacyVkTextureView;
import net.minecraft.client.renderer.PostPass;
import net.minecraft.resources.ResourceLocation;
import java.util.Map;

interface VeyraPostInput extends PostPass.Input {
    String samplerName();
    LegacyTextureView texture(Map<ResourceLocation, ResourceHandle<RenderTarget>> targets);
    default boolean bilinear() { return false; }
    default boolean mipmaps() { return false; }

    @Override default void bindTo(RenderPass pass, Map<ResourceLocation, ResourceHandle<RenderTarget>> targets) {
        LegacyTextureView view = texture(targets);
        var texture = view.textureForBinding();
        texture.setTextureFilter(bilinear() ? FilterMode.LINEAR : FilterMode.NEAREST, mipmaps());
        pass.bindSampler(samplerName() + "Sampler", texture);
        pass.setUniform(samplerName() + "Size", (float) view.getWidth(0), (float) view.getHeight(0));
    }

    static String samplerName(PostPass.Input input) {
        if (input instanceof VeyraPostInput custom) return custom.samplerName();
        if (input instanceof PostPass.TargetInput target) return target.samplerName();
        if (input instanceof PostPass.TextureInput texture) return texture.samplerName();
        throw new IllegalArgumentException("Unknown post-pass input: " + input.getClass().getName());
    }

    static boolean isBilinear(PostPass.Input input) {
        return input instanceof VeyraPostInput custom ? custom.bilinear()
                : input instanceof PostPass.TargetInput target && target.bilinear();
    }

    static LegacyTextureView texture(PostPass.Input input, Map<ResourceLocation, ResourceHandle<RenderTarget>> targets) {
        if (input instanceof VeyraPostInput custom) return custom.texture(targets);
        if (input instanceof PostPass.TargetInput target) {
            var handle = targets.get(target.targetId());
            if (handle == null) throw new IllegalStateException("Missing post-pass target: " + target.targetId());
            RenderTarget attachment = handle.get();
            LegacyTextureView view = target.depthBuffer() ? RenderTargetAccess.depthView(attachment)
                    : RenderTargetAccess.colorView(attachment);
            if (view == null) throw new IllegalStateException("Missing post-pass attachment: " + target.targetId());
            return view;
        }
        if (input instanceof PostPass.TextureInput texture) return LegacyVkTextureView.create(texture.texture().getTexture());
        throw new IllegalArgumentException("Unknown post-pass input: " + input.getClass().getName());
    }
}
