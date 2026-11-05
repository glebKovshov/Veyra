package dev.veyra.screen;

import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Runs a shader-pack load after the screen has presented a "loading" status. Large packs compile for many
 * seconds on the render thread; starting inside the click handler froze the menu with no feedback, which
 * reads as a hang. The work runs from {@code Screen.tick()}, outside any frame being recorded.
 */
final class DeferredShaderWork {
    /** Frames to present before blocking; VulkanMod queues up to two frames ahead of the swapchain. */
    private static final int FRAMES_BEFORE_WORK = 3;

    private Supplier<String> work;
    private Consumer<String> onDone;
    private int framesShown;

    boolean pending() {
        return work != null;
    }

    void schedule(Supplier<String> work, Consumer<String> onDone) {
        if (this.work != null) return;
        this.work = work;
        this.onDone = onDone;
        framesShown = 0;
    }

    void frameRendered() {
        if (work != null) framesShown++;
    }

    void tick() {
        if (work == null || framesShown < FRAMES_BEFORE_WORK) return;
        Supplier<String> running = work;
        Consumer<String> done = onDone;
        work = null;
        onDone = null;
        done.accept(running.get());
    }
}
