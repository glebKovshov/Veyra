package dev.veyra.mixin.accessor;

import net.minecraft.client.renderer.texture.SpriteContents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(SpriteContents.AnimationState.class)
public interface SpriteAnimationStateAccessor {
    @Accessor("frame") int veyra$getFrame();
    @Accessor("subFrame") int veyra$getSubFrame();
}
