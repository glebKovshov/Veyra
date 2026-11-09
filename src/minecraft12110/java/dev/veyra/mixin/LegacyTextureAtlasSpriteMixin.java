package dev.veyra.mixin;

import dev.veyra.mixin.accessor.TextureAtlasSpriteAccessor;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(TextureAtlasSprite.class)
abstract class LegacyTextureAtlasSpriteMixin implements TextureAtlasSpriteAccessor {
    // Sprite padding was introduced in 1.21.11; older atlases store unpadded frames.
    @Override public int veyra$getPadding() { return 0; }
}
