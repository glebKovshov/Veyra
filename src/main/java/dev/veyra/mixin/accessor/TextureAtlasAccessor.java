package dev.veyra.mixin.accessor;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import java.util.Map;
import java.util.List;
@Mixin(TextureAtlas.class)
public interface TextureAtlasAccessor {
    @Accessor("texturesByName") Map<Identifier, TextureAtlasSprite> veyra$getSprites();
    @Accessor("sprites") List<TextureAtlasSprite> veyra$getSpriteList();
    @Accessor("animatedTexturesStates") List<?> veyra$getAnimatedTexturesStates();
}
