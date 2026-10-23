package dev.veyra.vulkan;

import net.vulkanmod.vulkan.device.DeviceManager;

import java.io.IOException;

/** Expanded texture-view slots shared by VulkanMod and Veyra shaderpack pipelines. */
public final class VeyraTextureSlots {
    public static final int CAPACITY = 32;

    private VeyraTextureSlots() { }

    public static int maxSamplerDescriptors() {
        if (DeviceManager.deviceProperties == null) return CAPACITY;
        return effectiveCapacity(DeviceManager.deviceProperties.limits().maxPerStageDescriptorSamplers());
    }

    static int effectiveCapacity(int deviceLimit) {
        return Math.max(0, Math.min(CAPACITY, deviceLimit));
    }

    public static void validateSamplerCount(int count) throws IOException {
        validateSamplerCount(count, maxSamplerDescriptors(), "Pipeline");
    }

    public static void validateSamplerCount(int count, int deviceLimit, String programName) throws IOException {
        if (count < 0) throw new IllegalArgumentException("Sampler descriptor count cannot be negative.");
        int supported = effectiveCapacity(deviceLimit);
        if (count > supported) {
            throw new IOException(programName + " needs " + count + " texture descriptors; this Vulkan device supports at most "
                    + supported + " (Veyra's slot capacity is " + CAPACITY + ").");
        }
    }
}
