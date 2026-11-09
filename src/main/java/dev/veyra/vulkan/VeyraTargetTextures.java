package dev.veyra.vulkan;

import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.TextureFormat;

/** Minecraft metadata stays RGBA8; the caller installs the shader pack's native image format. */
public final class VeyraTargetTextures {
    private VeyraTargetTextures() { }

    public static void markCleared(net.vulkanmod.render.engine.VkGpuTexture texture) {
        ((dev.veyra.mixin.accessor.VkGpuTextureAccessor) texture).veyra$setNeedsClear(false);
    }

    public static GpuTexture createColor(GpuDevice device, String name, GpuTexture previous, int width, int height) {
        int usage = previous == null ? GpuTexture.USAGE_RENDER_ATTACHMENT | GpuTexture.USAGE_TEXTURE_BINDING
                : previous.usage();
        return device.createTexture(name, usage, TextureFormat.RGBA8, width, height, 1, 1);
    }
}
