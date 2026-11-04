package dev.veyra.smoke;

/**
 * Reproduces the Windows resize race deterministically: one swapchain recreation sees a 0x0
 * surface and no later window event schedules another recreation.
 */
public final class SurfaceLossProbe {
    private static boolean armed, applied;

    private SurfaceLossProbe() {}

    static void arm() { armed = true; applied = false; }

    static boolean applied() { return applied; }

    /** Called by the swapchain mixin; returns true when this extent query must report 0x0. */
    public static boolean consume() {
        if (!armed) return false;
        armed = false;
        applied = true;
        return true;
    }
}
