package dev.veyra.compat;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import net.minecraft.client.Minecraft;

/** Minecraft 1.21.1 render targets take the macOS clear flag when created and resized. */
public final class LegacyTargets {
    private LegacyTargets() { }

    public static TextureTarget textureTarget(int width, int height, boolean useDepth) {
        return new com.mojang.blaze3d.pipeline.TextureTarget(width, height, useDepth, Minecraft.ON_OSX);
    }

    public static void resize(RenderTarget target, int width, int height) {
        target.resize(width, height, Minecraft.ON_OSX);
    }
}
