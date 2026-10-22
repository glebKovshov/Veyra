package dev.veyra.mixin;

import dev.veyra.VeyraClient;
import dev.veyra.vulkan.VeyraDeviceFeatures;
import net.vulkanmod.vulkan.device.DeviceManager;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkAllocationCallbacks;
import org.lwjgl.vulkan.VkDeviceCreateInfo;
import org.lwjgl.vulkan.VkPhysicalDevice;
import org.lwjgl.vulkan.VkPhysicalDeviceFeatures;
import org.lwjgl.vulkan.VkPhysicalDeviceProperties;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = DeviceManager.class, remap = false)
public abstract class DeviceManagerMixin {
    @Redirect(method = "createLogicalDevice", at = @At(value = "INVOKE", target =
            "Lorg/lwjgl/vulkan/VK10;vkCreateDevice(Lorg/lwjgl/vulkan/VkPhysicalDevice;Lorg/lwjgl/vulkan/VkDeviceCreateInfo;Lorg/lwjgl/vulkan/VkAllocationCallbacks;Lorg/lwjgl/PointerBuffer;)I"))
    private static int veyra$enableSupportedFeatures(VkPhysicalDevice physicalDevice, VkDeviceCreateInfo createInfo,
                                                     VkAllocationCallbacks allocator, PointerBuffer device) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkPhysicalDeviceFeatures supported = VkPhysicalDeviceFeatures.calloc(stack);
            VK10.vkGetPhysicalDeviceFeatures(physicalDevice, supported);
            VkPhysicalDeviceProperties properties = VkPhysicalDeviceProperties.calloc(stack);
            VK10.vkGetPhysicalDeviceProperties(physicalDevice, properties);
            VkPhysicalDeviceFeatures originalFeatures = createInfo.pEnabledFeatures();
            VkDeviceCreateInfo enabledCreateInfo = VeyraDeviceFeatures.withSupportedFeatures(
                    createInfo, supported, stack);
            VkPhysicalDeviceFeatures enabled = enabledCreateInfo.pEnabledFeatures();
            VeyraDeviceFeatures.maxColorAttachments = properties.limits().maxColorAttachments();
            VeyraDeviceFeatures.independentBlendEnabled = enabled.independentBlend();
            VeyraDeviceFeatures.vertexPipelineStoresAndAtomicsEnabled = enabled.vertexPipelineStoresAndAtomics();
            VeyraDeviceFeatures.shaderStorageImageExtendedFormatsEnabled = enabled.shaderStorageImageExtendedFormats();
            VeyraClient.LOGGER.info("Vulkan core feature request for {} (API {}): independentBlend={}, vertexPipelineStoresAndAtomics={}, shaderStorageImageExtendedFormats={}, pEnabledFeaturesNull={}.",
                    properties.deviceNameString(), properties.apiVersion(), enabled.independentBlend(),
                    enabled.vertexPipelineStoresAndAtomics(), enabled.shaderStorageImageExtendedFormats(),
                    enabledCreateInfo.pEnabledFeatures() == null);
            try {
                int result = VK10.vkCreateDevice(physicalDevice, createInfo, allocator, device);
                long createdDevice = result == VK10.VK_SUCCESS ? device.get(0) : 0L;
                VeyraClient.LOGGER.info("vkCreateDevice returned result={} device=0x{}, createInfo=0x{}, pEnabledFeatures=0x{}, pNext=0x{}.",
                        result, Long.toHexString(createdDevice), Long.toHexString(createInfo.address()),
                        Long.toHexString(enabled.address()), Long.toHexString(createInfo.pNext()));
                return result;
            } finally {
                createInfo.pEnabledFeatures(originalFeatures);
            }
        }
    }
}
