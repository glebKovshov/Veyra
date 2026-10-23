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

    /** Builds a separate enabled feature block so VulkanMod's source block remains untouched. */
    public static VkDeviceCreateInfo withSupportedFeatures(VkDeviceCreateInfo createInfo,
                                                            VkPhysicalDeviceFeatures supported,
                                                            MemoryStack stack) {
        VkPhysicalDeviceFeatures requested = createInfo.pEnabledFeatures();
        VkPhysicalDeviceFeatures enabled = VkPhysicalDeviceFeatures.calloc(stack);
        if (requested != null) enabled.set(requested);
        createInfo.pEnabledFeatures(enabled);
        mergeSupportedFeatures(enabled, supported);
        return createInfo;
    }
}
