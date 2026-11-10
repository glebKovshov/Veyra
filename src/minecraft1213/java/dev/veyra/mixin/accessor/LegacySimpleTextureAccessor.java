package dev.veyra.mixin.accessor;

import net.minecraft.client.renderer.texture.SimpleTexture;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(SimpleTexture.class)
public interface LegacySimpleTextureAccessor {
    @Accessor("location") ResourceLocation veyra$getLocation();
}
