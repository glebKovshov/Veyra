package dev.veyra.vulkan;

import com.mojang.blaze3d.textures.GpuTexture;
import java.util.Objects;

/** Retains borrowed view identity while native Minecraft slots store only textures. */
final class LegacyTextureSlotCache {
    static final int CAPACITY = VeyraTextureSlots.CAPACITY;
    private final LegacyTextureView[] views = new LegacyTextureView[CAPACITY];

    static int slot(int index) { return Objects.checkIndex(index, CAPACITY); }

    void bind(int index, LegacyTextureView view, java.util.function.BiConsumer<Integer, GpuTexture> nativeSetter) {
        index = slot(index);
        GpuTexture texture = view == null ? null : view.textureForBinding();
        nativeSetter.accept(index, texture);
        views[index] = view;
    }

    LegacyTextureView resolve(int index, GpuTexture nativeTexture) {
        index = slot(index);
        LegacyTextureView cached = views[index];
        if (nativeTexture == null) return views[index] = null;
        if (cached != null && cached.texture() == nativeTexture) return cached;
        return views[index] = LegacyVkTextureView.create(nativeTexture);
    }
}
