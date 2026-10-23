package dev.veyra.shader;

import dev.veyra.mixin.accessor.SpriteAnimationStateAccessor;
import dev.veyra.mixin.accessor.TextureAtlasAccessor;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.Identifier;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Shared tick and albedo-animation timing for cached companion PBR maps. */
public final class PbrAnimationClock {
    public record BaseFrame(int frameSlot, int elapsedTicks) { }

    private static long ticks;

    private PbrAnimationClock() { }

    public static long advance() {
        if (ticks == Long.MAX_VALUE) ticks = 0;
        else ticks++;
        return ticks;
    }

    public static long ticks() {
        return ticks;
    }

    /** Returns each animated albedo sprite's current animation entry and sub-frame tick. */
    public static Map<Identifier, BaseFrame> animatedSpriteFrames(TextureAtlas atlas) {
        TextureAtlasAccessor accessor = (TextureAtlasAccessor) atlas;
        List<TextureAtlasSprite> sprites = accessor.veyra$getSpriteList();
        List<?> states = accessor.veyra$getAnimatedTexturesStates();
        Map<Identifier, BaseFrame> result = new HashMap<>();
        int stateIndex = 0;
        for (TextureAtlasSprite sprite : sprites) {
            if (!sprite.contents().isAnimated()) continue;
            if (stateIndex >= states.size()) break;
            SpriteAnimationStateAccessor state = (SpriteAnimationStateAccessor) states.get(stateIndex++);
            result.put(sprite.contents().name(), new BaseFrame(state.veyra$getFrame(),
                    Math.max(0, state.veyra$getSubFrame())));
        }
        return Map.copyOf(result);
    }
}
