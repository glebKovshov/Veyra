package dev.veyra.shader;

import org.joml.Vector2i;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EyeBrightnessSmootherTest {
    @Test
    void usesIrisDefaultTenTickHalfLifeForBlockAndSkyLight() {
        EyeBrightnessSmoother smoother = new EyeBrightnessSmoother();
        Vector2i start = smoother.update(100, 40, 0.0F, EyeBrightnessSmoother.DEFAULT_HALF_LIFE_TICKS);
        Vector2i halfway = smoother.update(0, 120, 0.5F, EyeBrightnessSmoother.DEFAULT_HALF_LIFE_TICKS);

        assertEquals(100, start.x);
        assertEquals(40, start.y);
        assertEquals(50, halfway.x);
        assertEquals(80, halfway.y);
    }

    @Test
    void supportsZeroHalfLifeAndResetsAtWorldChanges() {
        EyeBrightnessSmoother smoother = new EyeBrightnessSmoother();
        smoother.update(100, 40, 0.0F, 10.0F);
        Vector2i immediate = smoother.update(0, 120, 0.0F, 0.0F);
        assertEquals(0, immediate.x);
        assertEquals(120, immediate.y);

        smoother.reset();
        Vector2i restarted = smoother.update(20, 60, 0.0F, 10.0F);
        assertEquals(20, restarted.x);
        assertEquals(60, restarted.y);
    }

    @Test
    void rejectsInvalidHalfLife() {
        assertThrows(IllegalArgumentException.class,
                () -> new EyeBrightnessSmoother().update(0, 0, 0.0F, Float.NaN));
    }
}
