package dev.veyra.vulkan;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.veyra.compat.VkGpuTexture;
import java.util.Objects;

/** Minecraft 1.21.4 texture slots hold texture ids; this keeps the views Veyra bound to them. */
public final class LegacyTextureSlots {
    private static final LegacyTextureView[] VIEWS = new LegacyTextureView[VeyraTextureSlots.CAPACITY];
    private LegacyTextureSlots() { }

    public static void setShaderTexture(int index, LegacyTextureView view) {
        Objects.checkIndex(index, VIEWS.length);
        RenderSystem.setShaderTexture(index, view == null ? 0 : ((VkGpuTexture) view.textureForBinding()).glId());
        VIEWS[index] = view;
    }

    public static LegacyTextureView getShaderTexture(int index) {
        Objects.checkIndex(index, VIEWS.length);
        int id = RenderSystem.getShaderTexture(index);
        LegacyTextureView cached = VIEWS[index];
        if (cached != null && !cached.isClosed()
                && ((VkGpuTexture) cached.textureForBinding()).glId() == id) {
            return cached;
        }
        VkGpuTexture texture = VkGpuTexture.of(id);
        return VIEWS[index] = texture == null ? null : LegacyVkTextureView.create(texture);
    }
}
