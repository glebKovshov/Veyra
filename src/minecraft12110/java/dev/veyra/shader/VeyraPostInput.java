package dev.veyra.shader;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.resource.ResourceHandle;
import com.mojang.blaze3d.textures.GpuTextureView;
import net.minecraft.client.renderer.PostPass;
import net.minecraft.resources.Identifier;
import java.util.Map;

interface VeyraPostInput extends PostPass.Input {
    default boolean bilinear() { return false; }
    default boolean mipmaps() { return false; }

    static String samplerName(PostPass.Input input) { return input.samplerName(); }

    static boolean isBilinear(PostPass.Input input) {
        return input instanceof VeyraPostInput custom ? custom.bilinear()
                : input instanceof PostPass.TargetInput target && target.bilinear();
    }

    static GpuTextureView texture(PostPass.Input input, Map<Identifier, ResourceHandle<RenderTarget>> targets) {
        GpuTextureView view = input.texture(targets);
        if (input instanceof VeyraPostInput) {
            view.texture().setTextureFilter(isBilinear(input)
                    ? com.mojang.blaze3d.textures.FilterMode.LINEAR
                    : com.mojang.blaze3d.textures.FilterMode.NEAREST, VeyraRenderer.isPostMipmapView(view));
        }
        return view;
    }
}
