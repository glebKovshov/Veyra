package dev.veyra.shader;

import org.joml.Vector2i;

/** Applies Iris' eye-brightness half-life to block and sky light values. */
public final class EyeBrightnessSmoother {
    public static final float DEFAULT_HALF_LIFE_TICKS = 10.0F;
    private static final float TICKS_PER_SECOND = 20.0F;
    private static final double LN_OF_TWO = Math.log(2.0);

    private float blockBrightness;
    private float skyBrightness;
    private boolean initialized;

    public Vector2i update(int blockTarget, int skyTarget, float deltaSeconds, float halfLifeTicks) {
        if (!Float.isFinite(deltaSeconds) || deltaSeconds < 0.0F) {
            throw new IllegalArgumentException("The eye-brightness frame time must be finite and non-negative.");
        }
        if (!Float.isFinite(halfLifeTicks) || halfLifeTicks < 0.0F) {
            throw new IllegalArgumentException("eyeBrightnessHalflife must be finite and non-negative.");
        }
        if (!initialized) {
            blockBrightness = blockTarget;
            skyBrightness = skyTarget;
            initialized = true;
        } else if (halfLifeTicks == 0.0F) {
            blockBrightness = blockTarget;
            skyBrightness = skyTarget;
        } else {
            double halfLifeSeconds = halfLifeTicks / TICKS_PER_SECOND;
            float smoothing = (float) (1.0 - Math.exp(-LN_OF_TWO * deltaSeconds / halfLifeSeconds));
            blockBrightness += (blockTarget - blockBrightness) * smoothing;
            skyBrightness += (skyTarget - skyBrightness) * smoothing;
        }
        return new Vector2i(Math.round(blockBrightness), Math.round(skyBrightness));
    }

    public void reset() {
        blockBrightness = 0.0F;
        skyBrightness = 0.0F;
        initialized = false;
    }
}
