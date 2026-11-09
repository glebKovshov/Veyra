package dev.veyra.mixin.accessor;

import com.mojang.blaze3d.textures.AddressMode;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = GpuTexture.class, remap = false)
public interface LegacyGpuTextureSamplerAccessor {
    @Accessor("addressModeU") AddressMode veyra$getAddressModeU();
    @Accessor("addressModeV") AddressMode veyra$getAddressModeV();
    @Accessor("minFilter") FilterMode veyra$getMinFilter();
    @Accessor("magFilter") FilterMode veyra$getMagFilter();
    @Accessor("useMipmaps") boolean veyra$getUseMipmaps();
}
