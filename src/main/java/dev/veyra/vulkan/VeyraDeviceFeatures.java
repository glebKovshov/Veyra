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

    /** Temporarily requests supported core features through VulkanMod's original feature block. */
    public static VkDeviceCreateInfo withSupportedFeatures(VkDeviceCreateInfo createInfo,
                                                            VkPhysicalDeviceFeatures supported,
                                                            MemoryStack stack) {
        VkPhysicalDeviceFeatures enabled = createInfo.pEnabledFeatures();
        if (enabled == null) {
            enabled = VkPhysicalDeviceFeatures.calloc(stack);
            createInfo.pEnabledFeatures(enabled);
        }
        mergeSupportedFeatures(enabled, supported);
        return createInfo;
    }
}
