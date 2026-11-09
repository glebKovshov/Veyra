package dev.veyra.mixin.accessor;

import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import java.util.List;
import java.util.Map;

@Mixin(TextureAtlas.class)
public interface TextureAtlasAccessor {
    @Accessor("texturesByName") Map<ResourceLocation, TextureAtlasSprite> veyra$getSprites();
    @Accessor("animatedTextures") List<?> veyra$getAnimatedTexturesStates();
}
