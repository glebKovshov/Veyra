package dev.veyra.vulkan;

import org.lwjgl.vulkan.VkPhysicalDeviceFeatures;

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
}
