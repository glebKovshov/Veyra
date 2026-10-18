package dev.veyra.shader;

/** Iris shadow directives that affect Veyra's shadow passes. */
public record ShadowSettings(int resolution, float distance, float intervalSize, float sunPathRotation,
                             float distanceRenderMultiplier, float entityShadowDistanceMultiplier,
                             boolean shadowTranslucent) {
    public static final int DEFAULT_RESOLUTION = 1024;
    public static final float DEFAULT_DISTANCE = 128.0F;
    public static final float DEFAULT_INTERVAL_SIZE = 2.0F;
    public static final float DEFAULT_SUN_PATH_ROTATION = 0.0F;
    public static final float DEFAULT_DISTANCE_RENDER_MULTIPLIER = -1.0F;
    public static final float DEFAULT_ENTITY_SHADOW_DISTANCE_MULTIPLIER = 1.0F;
    public static final ShadowSettings DEFAULT = new ShadowSettings(
            DEFAULT_RESOLUTION, DEFAULT_DISTANCE, DEFAULT_INTERVAL_SIZE, DEFAULT_SUN_PATH_ROTATION,
            DEFAULT_DISTANCE_RENDER_MULTIPLIER, DEFAULT_ENTITY_SHADOW_DISTANCE_MULTIPLIER, false);

    public ShadowSettings {
        if (resolution < 1 || resolution > 8192) {
            throw new IllegalArgumentException("shadowMapResolution must be between 1 and 8192.");
        }
        if (!Float.isFinite(distance) || distance <= 0.0F || distance > 8192.0F) {
            throw new IllegalArgumentException("shadowDistance must be greater than 0 and no more than 8192.");
        }
        if (!Float.isFinite(intervalSize) || intervalSize < 0.0F || intervalSize > 65536.0F) {
            throw new IllegalArgumentException("shadowIntervalSize must be between 0 and 65536.");
        }
        if (!Float.isFinite(sunPathRotation)) {
            throw new IllegalArgumentException("sunPathRotation must be finite.");
        }
        if (!Float.isFinite(distanceRenderMultiplier)) {
            throw new IllegalArgumentException("shadowDistanceRenderMul must be finite.");
        }
        if (!Float.isFinite(entityShadowDistanceMultiplier)) {
            throw new IllegalArgumentException("entityShadowDistanceMul must be finite.");
        }
    }

    public boolean hasDistanceRenderLimit() {
        return distanceRenderMultiplier > 0.0F;
    }

    public double distanceRenderLimit() {
        return (double) distance * distanceRenderMultiplier;
    }

    public boolean usesTerrainEntityShadowFrustum() {
        return entityShadowDistanceMultiplier == 1.0F || entityShadowDistanceMultiplier < 0.0F;
    }

    public boolean hasEntityDistanceRenderLimit() {
        return !usesTerrainEntityShadowFrustum()
                && distanceRenderMultiplier > 0.0F
                && entityShadowDistanceMultiplier > 0.0F;
    }

    public double entityDistanceRenderLimit() {
        return (double) distance * distanceRenderMultiplier * entityShadowDistanceMultiplier;
    }
}
