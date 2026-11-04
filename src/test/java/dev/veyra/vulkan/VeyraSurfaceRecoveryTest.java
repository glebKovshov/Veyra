package dev.veyra.vulkan;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VeyraSurfaceRecoveryTest {
    @Test
    void retriesOnlyForVisibleNonEmptyWindows() {
        assertTrue(VeyraSurfaceRecovery.shouldRetry(800, 450, false));
        assertFalse(VeyraSurfaceRecovery.shouldRetry(800, 450, true), "minimized windows wait for restore");
        assertFalse(VeyraSurfaceRecovery.shouldRetry(0, 450, false));
        assertFalse(VeyraSurfaceRecovery.shouldRetry(800, 0, false));
    }
}
