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

    static boolean isBilinear(PostPass.Input input) { return input.bilinear(); }

    static GpuTextureView texture(PostPass.Input input, Map<Identifier, ResourceHandle<RenderTarget>> targets) {
        return input.texture(targets);
    }
}
