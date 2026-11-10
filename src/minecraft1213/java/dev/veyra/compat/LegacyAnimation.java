package dev.veyra.compat;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.resources.metadata.animation.AnimationFrame;
import net.minecraft.client.resources.metadata.animation.AnimationMetadataSection;

/** The explicitly declared frames of animation metadata, with resolved durations; empty when implicit. */
public final class LegacyAnimation {
    private LegacyAnimation() { }

    public static List<AnimationFrame> frames(AnimationMetadataSection metadata) {
        List<AnimationFrame> frames = new ArrayList<>();
        metadata.forEachFrame((index, time) -> frames.add(new AnimationFrame(index, time)));
        return frames;
    }
}
