package dev.veyra.shader;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class IrisShadowFadeTest {
    private static final float EPSILON = 0.0001F;

    @Test
    void selectsTheVisibleSunDuringDaylight() {
        assertEquals(1.0F, IrisShadowFade.calculate(0.0F, 180.0F), EPSILON);
        assertEquals(0.0F, IrisShadowFade.calculate(270.0F, 90.0F), EPSILON);
    }

    @Test
    void selectsTheVisibleMoonAtNight() {
        assertEquals(1.0F, IrisShadowFade.calculate(180.0F, 0.0F), EPSILON);
        assertEquals(0.0F, IrisShadowFade.calculate(90.0F, 270.0F), EPSILON);
    }

    @Test
    void invalidAnglesProduceAFiniteZeroFade() {
        assertEquals(0.0F, IrisShadowFade.calculate(Float.NaN, 0.0F), EPSILON);
        assertEquals(0.0F, IrisShadowFade.calculate(0.0F, Float.POSITIVE_INFINITY), EPSILON);
    }
}
