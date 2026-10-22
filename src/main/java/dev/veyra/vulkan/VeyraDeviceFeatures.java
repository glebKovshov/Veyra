package dev.veyra.vulkan;

import org.lwjgl.vulkan.VkPhysicalDeviceFeatures;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkDeviceCreateInfo;

public final class VeyraDeviceFeatures {
    private VeyraDeviceFeatures() { }
    public static boolean independentBlendEnabled;
    public static boolean vertexPipelineStoresAndAtomicsEnabled;
    public static boolean shaderStorageImageExtendedFormatsEnabled;
    public static int maxColorAttachments = 1;
    public static int swapchainUsage;

    public static boolean supportsShadowVoxelWrites() {
        return supportsShadowVoxelWrites(vertexPipelineStoresAndAtomicsEnabled,
                shaderStorageImageExtendedFormatsEnabled);
    }

    static boolean supportsShadowVoxelWrites(boolean vertexStores, boolean extendedFormats) {
        return vertexStores && extendedFormats;
    }

    public static VkPhysicalDeviceFeatures mergeSupportedFeatures(VkPhysicalDeviceFeatures enabled,
                                                                   VkPhysicalDeviceFeatures supported) {
        return enabled.independentBlend(supported.independentBlend())
                .vertexPipelineStoresAndAtomics(supported.vertexPipelineStoresAndAtomics())
                .shaderStorageImageExtendedFormats(supported.shaderStorageImageExtendedFormats());
    }

    /** Copies the device create info and requests supported core features without changing VulkanMod's pNext chain. */
    public static VkDeviceCreateInfo withSupportedFeatures(VkDeviceCreateInfo createInfo,
                                                            VkPhysicalDeviceFeatures supported,
                                                            MemoryStack stack) {
        VkDeviceCreateInfo enabledCreateInfo = VkDeviceCreateInfo.calloc(stack).set(createInfo);
        VkPhysicalDeviceFeatures enabled = VkPhysicalDeviceFeatures.calloc(stack);
        VkPhysicalDeviceFeatures requested = createInfo.pEnabledFeatures();
        if (requested != null) enabled.set(requested);
        mergeSupportedFeatures(enabled, supported);
        enabledCreateInfo.pEnabledFeatures(enabled);
        return enabledCreateInfo;
    }
}
