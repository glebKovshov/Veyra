package dev.veyra.vulkan;

import com.mojang.blaze3d.systems.RenderSystem;

public final class LegacyTextureSlots {
    private static final LegacyTextureSlotCache CACHE = new LegacyTextureSlotCache();
    private LegacyTextureSlots() { }

    public static void setShaderTexture(int index, LegacyTextureView view) {
        CACHE.bind(index, view, RenderSystem::setShaderTexture);
    }

    public static LegacyTextureView getShaderTexture(int index) {
        LegacyTextureSlotCache.slot(index);
        return CACHE.resolve(index, RenderSystem.getShaderTexture(index));
    }
}
