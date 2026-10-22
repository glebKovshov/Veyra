package dev.veyra.vulkan;

import org.junit.jupiter.api.Test;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkDeviceCreateInfo;
import org.lwjgl.vulkan.VkPhysicalDeviceFeatures;
import org.lwjgl.vulkan.VkPhysicalDeviceFeatures2;
import org.lwjgl.vulkan.VkPhysicalDeviceVulkan11Features;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNull;

class VeyraDeviceFeaturesTest {
    @Test void enablesSupportedCoreFeaturesThroughFeatures2AndPreservesTheExistingChain() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkPhysicalDeviceVulkan11Features vulkan11 = VkPhysicalDeviceVulkan11Features.calloc(stack)
                    .sType$Default()
                    .shaderDrawParameters(true);
            VkPhysicalDeviceFeatures requested = VkPhysicalDeviceFeatures.calloc(stack)
                    .samplerAnisotropy(true);
            VkPhysicalDeviceFeatures supported = VkPhysicalDeviceFeatures.calloc(stack)
                    .vertexPipelineStoresAndAtomics(true)
                    .shaderStorageImageExtendedFormats(true);
            VkDeviceCreateInfo createInfo = VkDeviceCreateInfo.calloc(stack)
                    .sType$Default()
                    .pEnabledFeatures(requested)
                    .pNext(vulkan11);

            VkPhysicalDeviceFeatures2 enabled = VeyraDeviceFeatures.enableSupportedFeatures(
                    createInfo, supported, stack);

            assertNull(createInfo.pEnabledFeatures());
            assertEqualsPointer(enabled.address(), createInfo.pNext());
            assertEqualsPointer(vulkan11.address(), enabled.pNext());
            assertTrue(enabled.features().vertexPipelineStoresAndAtomics());
            assertTrue(enabled.features().shaderStorageImageExtendedFormats());
            assertTrue(enabled.features().samplerAnisotropy());
            assertTrue(VkPhysicalDeviceVulkan11Features.create(enabled.pNext()).shaderDrawParameters());
        }
    }

    @Test void requestsSupportedCoreFeaturesOnTheDeviceCreateInfoFeaturesStruct() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkPhysicalDeviceFeatures enabled = VkPhysicalDeviceFeatures.calloc(stack)
                    .samplerAnisotropy(true)
                    .wideLines(false);
            VkPhysicalDeviceFeatures supported = VkPhysicalDeviceFeatures.calloc(stack)
                    .independentBlend(true)
                    .vertexPipelineStoresAndAtomics(true)
                    .shaderStorageImageExtendedFormats(true);

            VkPhysicalDeviceFeatures result = VeyraDeviceFeatures.mergeSupportedFeatures(enabled, supported);

            assertSame(enabled, result);
            assertTrue(result.independentBlend());
            assertTrue(result.vertexPipelineStoresAndAtomics());
            assertTrue(result.shaderStorageImageExtendedFormats());
            assertTrue(result.samplerAnisotropy());
            assertFalse(result.wideLines());
        }
    }

    @Test void clearsSupportedFeatureRequestsWhenThePhysicalDeviceDoesNotSupportThem() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkPhysicalDeviceFeatures enabled = VkPhysicalDeviceFeatures.calloc(stack)
                    .independentBlend(true)
                    .vertexPipelineStoresAndAtomics(true)
                    .shaderStorageImageExtendedFormats(true)
                    .samplerAnisotropy(true);
            VkPhysicalDeviceFeatures supported = VkPhysicalDeviceFeatures.calloc(stack);

            VkPhysicalDeviceFeatures result = VeyraDeviceFeatures.mergeSupportedFeatures(enabled, supported);

            assertFalse(result.independentBlend());
            assertFalse(result.vertexPipelineStoresAndAtomics());
            assertFalse(result.shaderStorageImageExtendedFormats());
            assertTrue(result.samplerAnisotropy());
        }
    }

    private static void assertEqualsPointer(long expected, long actual) {
        org.junit.jupiter.api.Assertions.assertEquals(expected, actual);
    }
}
