package dev.veyra.shader;

/** Computes Iris' sun-or-moon horizon fade for shadow uniforms. */
final class IrisShadowFade {
    private IrisShadowFade() {
    }

    static float calculate(float currentSunAngle, float currentMoonAngle) {
        if (!Float.isFinite(currentSunAngle) || !Float.isFinite(currentMoonAngle)) {
            return 0.0F;
        }
        float sunAngle = irisAngle(currentSunAngle);
        float moonAngle = irisAngle(currentMoonAngle);
        float shadowAngle = (sunAngle < 180.0F ? sunAngle : moonAngle) / 360.0F;
        float fade = 1.0F - (Math.abs(Math.abs(shadowAngle - 0.5F) - 0.25F) - 0.23F) * 100.0F;
        return Math.max(0.0F, Math.min(1.0F, fade));
    }

    private static float irisAngle(float currentAngle) {
        float angle = currentAngle + 90.0F;
        if (angle < 0.0F) {
            angle += 360.0F;
        } else if (angle > 360.0F) {
            angle -= 360.0F;
        }
        return angle;
    }
}
