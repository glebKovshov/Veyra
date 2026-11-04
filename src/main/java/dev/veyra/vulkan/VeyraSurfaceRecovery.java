package dev.veyra.vulkan;

/**
 * VulkanMod destroys its swapchain when the surface reports a 0x0 extent and builds a 10x10
 * placeholder main pass. It recreates only when a later resize callback arrives. On Windows a
 * resize or restore can deliver its final callback while the surface is still 0x0; the window
 * then has a real size, but no swapchain update remains pending. Retry from the frame loop until
 * the surface can be recreated.
 */
public final class VeyraSurfaceRecovery {
    private VeyraSurfaceRecovery() {}

    /** Whether a swapchain without images should be recreated for this window state. */
    public static boolean shouldRetry(int framebufferWidth, int framebufferHeight, boolean iconified) {
        return !iconified && framebufferWidth > 0 && framebufferHeight > 0;
    }
}
