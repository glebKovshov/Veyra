package dev.veyra.vulkan;

import org.lwjgl.vulkan.VkPhysicalDeviceFeatures;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkDeviceCreateInfo;
import org.lwjgl.vulkan.VkPhysicalDeviceFeatures2;

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

    /** Enables core features through VkPhysicalDeviceFeatures2 while preserving VulkanMod's feature chain. */
    public static VkPhysicalDeviceFeatures2 enableSupportedFeatures(VkDeviceCreateInfo createInfo,
                                                                     VkPhysicalDeviceFeatures supported,
                                                                     MemoryStack stack) {
        VkPhysicalDeviceFeatures enabled = VkPhysicalDeviceFeatures.calloc(stack);
        VkPhysicalDeviceFeatures requested = createInfo.pEnabledFeatures();
        if (requested != null) enabled.set(requested);
        mergeSupportedFeatures(enabled, supported);

        VkPhysicalDeviceFeatures2 featureChain = VkPhysicalDeviceFeatures2.calloc(stack)
                .sType$Default();
        featureChain.features().set(enabled);
        featureChain.pNext(createInfo.pNext());
        createInfo.pEnabledFeatures((VkPhysicalDeviceFeatures) null);
        createInfo.pNext(featureChain.address());
        return featureChain;
    }
}
