package dev.veyra.smoke;

import org.slf4j.LoggerFactory;

/**
 * Diagnostic frame-time breakdown for {@code -Dveyra.graphicsSmoke.perfProbe=true}. A frame whose fence wait
 * is near zero was limited by the render thread (CPU), not by the GPU.
 */
public final class PerfProbe {
    private static final boolean ENABLED = Boolean.getBoolean("veyra.graphicsSmoke.perfProbe");
    private static final int WINDOW = 300;
    private static long lastFrame, frameSum, fenceSum, levelSum, shadowSum, setupSum;
    private static long levelStart, shadowStart, setupStart;
    private static int frames, setupCalls;

    private PerfProbe() {}

    public static void fenceWait(long nanos) { if (ENABLED) fenceSum += nanos; }

    public static void levelStart() { if (ENABLED) levelStart = System.nanoTime(); }

    public static void levelEnd() { if (ENABLED && levelStart != 0) levelSum += System.nanoTime() - levelStart; }

    public static void shadowStart() { if (ENABLED) shadowStart = System.nanoTime(); }

    public static void shadowEnd() { if (ENABLED && shadowStart != 0) shadowSum += System.nanoTime() - shadowStart; }

    public static void setupStart() { if (ENABLED) setupStart = System.nanoTime(); }

    public static void setupEnd() {
        if (!ENABLED || setupStart == 0) return;
        setupSum += System.nanoTime() - setupStart;
        setupCalls++;
    }

    public static void frame() {
        if (!ENABLED) return;
        long now = System.nanoTime();
        if (lastFrame != 0) {
            frameSum += now - lastFrame;
            frames++;
        }
        lastFrame = now;
        if (frames < WINDOW) return;
        double n = frames;
        LoggerFactory.getLogger("veyra-graphics-smoke").info(
                "PERF frames={} frameMs={} fps={} fenceWaitMs={} renderLevelMs={} shadowPassMs={} sectionSetupMs={} setupCallsPerFrame={}",
                frames, ms(frameSum / n), String.format(java.util.Locale.ROOT, "%.1f", 1e9 / (frameSum / n)),
                ms(fenceSum / n), ms(levelSum / n), ms(shadowSum / n), ms(setupSum / n),
                String.format(java.util.Locale.ROOT, "%.2f", setupCalls / n));
        frameSum = fenceSum = levelSum = shadowSum = setupSum = 0;
        frames = setupCalls = 0;
    }

    private static String ms(double nanos) {
        return String.format(java.util.Locale.ROOT, "%.3f", nanos / 1e6);
    }
}
