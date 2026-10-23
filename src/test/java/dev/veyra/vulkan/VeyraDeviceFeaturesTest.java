package dev.veyra.vulkan;

import org.junit.jupiter.api.Test;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkDeviceCreateInfo;
import org.lwjgl.vulkan.VkPhysicalDeviceFeatures;
import org.lwjgl.vulkan.VkPhysicalDeviceFeatures2;
import org.lwjgl.vulkan.VkPhysicalDeviceVulkan11Features;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VeyraDeviceFeaturesTest {
    @Test void requestsCoreFeaturesThroughFeatures2AndPreservesTheExistingFeatureChain() {
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

            VkDeviceCreateInfo enabledCreateInfo = VeyraDeviceFeatures.withSupportedFeatures(
                    createInfo, supported, stack);
            VkPhysicalDeviceFeatures2 enabledFeatures = VkPhysicalDeviceFeatures2.create(
                    enabledCreateInfo.pNext());
            VkPhysicalDeviceFeatures enabled = enabledFeatures.features();

            assertNotSame(createInfo, enabledCreateInfo);
            assertNull(enabledCreateInfo.pEnabledFeatures());
            assertEquals(enabledFeatures.address(), enabledCreateInfo.pNext());
            assertEquals(vulkan11.address(), enabledFeatures.pNext());
            assertEquals(requested.address(), createInfo.pEnabledFeatures().address());
            assertTrue(enabled.vertexPipelineStoresAndAtomics());
            assertTrue(enabled.shaderStorageImageExtendedFormats());
            assertTrue(enabled.samplerAnisotropy());
            assertTrue(VkPhysicalDeviceVulkan11Features.create(enabledFeatures.pNext()).shaderDrawParameters());
        }
    }

    @Test void requestsSupportedFeaturesWhenVulkanModLeavesCoreFeaturesUnset() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkPhysicalDeviceFeatures supported = VkPhysicalDeviceFeatures.calloc(stack)
                    .independentBlend(true)
                    .vertexPipelineStoresAndAtomics(true)
                    .shaderStorageImageExtendedFormats(true);
            VkDeviceCreateInfo createInfo = VkDeviceCreateInfo.calloc(stack).sType$Default();

            VkDeviceCreateInfo enabledCreateInfo = VeyraDeviceFeatures.withSupportedFeatures(
                    createInfo, supported, stack);
            VkPhysicalDeviceFeatures2 enabledFeatures = VkPhysicalDeviceFeatures2.create(
                    enabledCreateInfo.pNext());

            assertNull(createInfo.pEnabledFeatures());
            assertNull(enabledCreateInfo.pEnabledFeatures());
            assertTrue(enabledFeatures.features().independentBlend());
            assertTrue(enabledFeatures.features().vertexPipelineStoresAndAtomics());
            assertTrue(enabledFeatures.features().shaderStorageImageExtendedFormats());
            assertEquals(createInfo.pNext(), enabledFeatures.pNext());
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
}
