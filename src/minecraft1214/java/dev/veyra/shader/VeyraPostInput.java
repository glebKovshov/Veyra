package dev.veyra.shader;

import com.mojang.blaze3d.framegraph.FramePass;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.resource.ResourceHandle;
import dev.veyra.accessor.RenderTargetAccess;
import dev.veyra.compat.FilterMode;
import dev.veyra.compat.LegacyDraws;
import dev.veyra.compat.RenderPass;
import dev.veyra.compat.VkGpuTexture;
import dev.veyra.vulkan.LegacyTextureView;
import dev.veyra.vulkan.LegacyVkTextureView;
import java.util.Map;
import net.minecraft.client.renderer.CompiledShaderProgram;
import net.minecraft.client.renderer.PostPass;
import net.minecraft.resources.ResourceLocation;

interface VeyraPostInput extends PostPass.Input {
    String samplerName();
    LegacyTextureView texture(Map<ResourceLocation, ResourceHandle<RenderTarget>> targets);
    default boolean bilinear() { return false; }
    default boolean mipmaps() { return false; }

    // Minecraft 1.21.4 binds inputs to the program by sampler index, which differs from the native
    // descriptor index of Veyra's programs; bind through the descriptor names instead.
    @Override default void bindTo(CompiledShaderProgram program, Map<ResourceLocation, ResourceHandle<RenderTarget>> targets) {
        RenderPass pass = LegacyDraws.pass(program);
        LegacyTextureView view = texture(targets);
        var texture = view.textureForBinding();
        texture.setTextureFilter(bilinear() ? FilterMode.LINEAR : FilterMode.NEAREST, mipmaps());
        pass.bindSampler(samplerName() + "Sampler", texture);
        pass.setUniform(samplerName() + "Size", (float) view.getWidth(0), (float) view.getHeight(0));
    }

    /** Lets a vanilla input bind like Veyra's own inputs. */
    static PostPass.Input adapt(PostPass.Input input) {
        return input instanceof VeyraPostInput ? input : new Adapted(input);
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
        if (input instanceof PostPass.TextureInput texture) {
            return LegacyVkTextureView.create(VkGpuTexture.of(texture.texture()));
        }
        throw new IllegalArgumentException("Unknown post-pass input: " + input.getClass().getName());
    }

    record Adapted(PostPass.Input delegate) implements VeyraPostInput {
        @Override public String samplerName() { return VeyraPostInput.samplerName(delegate); }
        @Override public LegacyTextureView texture(Map<ResourceLocation, ResourceHandle<RenderTarget>> targets) {
            return VeyraPostInput.texture(delegate, targets);
        }
        @Override public boolean bilinear() { return VeyraPostInput.isBilinear(delegate); }
        @Override public void addToPass(FramePass pass, Map<ResourceLocation, ResourceHandle<RenderTarget>> targets) {
            delegate.addToPass(pass, targets);
        }
        @Override public void cleanup(Map<ResourceLocation, ResourceHandle<RenderTarget>> targets) {
            delegate.cleanup(targets);
        }
    }
}
