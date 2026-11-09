package dev.veyra.vulkan;

import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.TextureFormat;

/** The old device creates attachment/sampleable images without Minecraft usage flags. */
public final class VeyraTargetTextures {
    private VeyraTargetTextures() { }

    public static void markCleared(net.vulkanmod.render.engine.VkGpuTexture texture) {
        // VulkanMod 0.5.6 has no deferred-clear flag; the caller already recorded the clear.
    }

    public static GpuTexture createColor(GpuDevice device, String name, GpuTexture previous, int width, int height) {
        return device.createTexture(name, TextureFormat.RGBA8, width, height, 1);
    }
}
