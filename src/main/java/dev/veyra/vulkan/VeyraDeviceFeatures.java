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

    /** Adds the core-feature block through the Vulkan 1.1 features2 device-create path. */
    public static VkDeviceCreateInfo withSupportedFeatures(VkDeviceCreateInfo createInfo,
                                                            VkPhysicalDeviceFeatures supported,
                                                            MemoryStack stack) {
        VkPhysicalDeviceFeatures2 enabled = VkPhysicalDeviceFeatures2.calloc(stack).sType$Default();
        VkPhysicalDeviceFeatures requested = createInfo.pEnabledFeatures();
        if (requested != null) enabled.features().set(requested);
        mergeSupportedFeatures(enabled.features(), supported);
        enabled.pNext(createInfo.pNext());
        createInfo.pEnabledFeatures((VkPhysicalDeviceFeatures) null);
        createInfo.pNext(enabled);
        return createInfo;
    }
}
