package dev.veyra.shader;

import dev.veyra.mixin.accessor.SpriteAnimationStateAccessor;
import dev.veyra.mixin.accessor.LegacySpriteTickerAccessor;
import dev.veyra.mixin.accessor.TextureAtlasAccessor;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.Identifier;

import java.util.HashMap;
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
        Map<Identifier, BaseFrame> result = new HashMap<>();
        for (Object ticker : accessor.veyra$getAnimatedTexturesStates()) {
            LegacySpriteTickerAccessor wrapper = (LegacySpriteTickerAccessor) ticker;
            SpriteAnimationStateAccessor state = (SpriteAnimationStateAccessor) wrapper.veyra$getTicker();
            result.put(wrapper.veyra$getSprite().contents().name(), new BaseFrame(state.veyra$getFrame(),
                    Math.max(0, state.veyra$getSubFrame())));
        }
        return Map.copyOf(result);
    }
}
