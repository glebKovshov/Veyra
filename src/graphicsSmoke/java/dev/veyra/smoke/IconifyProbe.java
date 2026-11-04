package dev.veyra.smoke;

import net.minecraft.client.Minecraft;
import net.vulkanmod.vulkan.Renderer;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Diagnostic for {@code -Dveyra.graphicsSmoke.iconifyProbe=<seconds>}: minimizes the window during the hold,
 * restores it, and logs every frame and the main target's brightness until the picture has settled.
 */
public final class IconifyProbe {
    private static final int SECONDS = Integer.getInteger("veyra.graphicsSmoke.iconifyProbe", 0);
    private static final long OBSERVE_NANOS = 8_000_000_000L;
    private static final Logger LOGGER = LoggerFactory.getLogger("veyra-iconify-probe");
    private static int state;
    private static long iconifiedAt, restoredAt, lastFrame;
    private static boolean screenshotPending;

    private IconifyProbe() {}

    static boolean enabled() { return SECONDS > 0; }

    /** True while the probe still needs the hold loop to keep the world open. */
    static boolean holding() { return enabled() && state < 3; }

    static void tick(Minecraft client) {
        long now = System.nanoTime();
        long window = client.getWindow().handle();
        switch (state) {
            case 0 -> {
                GLFW.glfwIconifyWindow(window);
                iconifiedAt = now;
                state = 1;
                LOGGER.info("ICONIFY requested");
            }
            case 1 -> {
                if (now - iconifiedAt < SECONDS * 1_000_000_000L) return;
                GLFW.glfwRestoreWindow(window);
                restoredAt = now;
                state = 2;
                LOGGER.info("RESTORE requested after {} s; iconified={}", SECONDS,
                        GLFW.glfwGetWindowAttrib(window, GLFW.GLFW_ICONIFIED));
            }
            case 2 -> {
                if (now - restoredAt > OBSERVE_NANOS) {
                    state = 3;
                    LOGGER.info("PROBE done");
                    return;
                }
                if (screenshotPending) return;
                screenshotPending = true;
                long requested = now;
                net.minecraft.client.Screenshot.takeScreenshot(client.getMainRenderTarget(), image -> {
                    try (image) {
                        long sum = 0;
                        int samples = 0;
                        for (int y = 0; y < image.getHeight(); y += 8) {
                            for (int x = 0; x < image.getWidth(); x += 8) {
                                int argb = image.getPixel(x, y);
                                sum += ((argb >> 16) & 255) + ((argb >> 8) & 255) + (argb & 255);
                                samples++;
                            }
                        }
                        LOGGER.info("LUMA t={}ms size={}x{} mean={}", (requested - restoredAt) / 1_000_000,
                                image.getWidth(), image.getHeight(), samples == 0 ? 0 : sum / (3L * samples));
                    } finally {
                        screenshotPending = false;
                    }
                });
            }
            default -> { }
        }
    }

    /** Called at VulkanMod's frame end. */
    public static void frame() {
        if (!enabled() || state == 0 || state == 3) return;
        long now = System.nanoTime();
        var swapChain = Renderer.getInstance().getSwapChain();
        var target = Minecraft.getInstance().getMainRenderTarget();
        long since = state == 1 ? (now - iconifiedAt) / 1_000_000 : (now - restoredAt) / 1_000_000;
        LOGGER.info("FRAME {} t={}ms dt={}ms swapchainImages={} extent={}x{} mainTarget={}x{}",
                state == 1 ? "iconified" : "restored", since, lastFrame == 0 ? 0 : (now - lastFrame) / 1_000_000,
                swapChain != null && swapChain.hasImages(),
                swapChain == null ? 0 : swapChain.getExtent().width(), swapChain == null ? 0 : swapChain.getExtent().height(),
                target.width, target.height);
        lastFrame = now;
    }
}
