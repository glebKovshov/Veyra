package dev.veyra.mixin.accessor;

import net.minecraft.client.renderer.texture.SpriteTicker;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = "net.minecraft.client.renderer.texture.TextureAtlasSprite$1")
public interface LegacySpriteTickerAccessor {
    @Accessor("val$ticker") SpriteTicker veyra$getTicker();
    @Accessor("field_40555") TextureAtlasSprite veyra$getSprite();
}
