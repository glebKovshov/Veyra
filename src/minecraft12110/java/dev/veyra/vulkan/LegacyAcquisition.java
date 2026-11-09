package dev.veyra.vulkan;

public final class LegacyAcquisition {
    private LegacyAcquisition() { }
    public static boolean succeeded(int result) {
        return result == org.lwjgl.vulkan.VK10.VK_SUCCESS || result == org.lwjgl.vulkan.KHRSwapchain.VK_SUBOPTIMAL_KHR;
    }
}
