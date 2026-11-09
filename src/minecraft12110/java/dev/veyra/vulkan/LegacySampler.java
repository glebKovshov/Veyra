package dev.veyra.vulkan;

import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.textures.AddressMode;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import dev.veyra.mixin.accessor.LegacyGpuTextureSamplerAccessor;
import java.util.OptionalDouble;

/** Sampling state belongs to the texture on Minecraft versions before 1.21.11. */
public record LegacySampler(AddressMode u, AddressMode v, FilterMode min, FilterMode mag,
                            boolean mipmaps) implements AutoCloseable {
    public static LegacySampler create(AddressMode u, AddressMode v, FilterMode min, FilterMode mag,
                                       int anisotropy, OptionalDouble maxLod) {
        if (anisotropy != 1 || maxLod.isPresent()) {
            throw new IllegalArgumentException("Legacy sampler creation requires default anisotropy and LOD.");
        }
        return new LegacySampler(u, v, min, mag, false);
    }

    public static LegacySampler getClampToEdge(FilterMode filter) {
        return getSampler(AddressMode.CLAMP_TO_EDGE, AddressMode.CLAMP_TO_EDGE, filter, filter, false);
    }

    public static LegacySampler getSampler(AddressMode u, AddressMode v, FilterMode min, FilterMode mag,
                                           boolean mipmaps) {
        return new LegacySampler(u, v, min, mag, mipmaps);
    }

    public static LegacySampler from(GpuTexture texture) {
        var state = (LegacyGpuTextureSamplerAccessor) texture;
        return new LegacySampler(state.veyra$getAddressModeU(), state.veyra$getAddressModeV(),
                state.veyra$getMinFilter(), state.veyra$getMagFilter(), state.veyra$getUseMipmaps());
    }

    // shortcut: sampling state is shared by texture aliases; use descriptor overrides for conflicting bindings.
    public void apply(GpuTexture texture) {
        texture.setAddressMode(u, v);
        texture.setTextureFilter(min, mag, mipmaps);
    }

    public static void bind(RenderPass pass, String name, GpuTextureView view, LegacySampler sampler) {
        if (view != null) sampler.apply(view.texture());
        pass.bindSampler(name, view);
    }

    // No native sampler is allocated: the texture owns its sampling state.
    @Override public void close() { }
}
