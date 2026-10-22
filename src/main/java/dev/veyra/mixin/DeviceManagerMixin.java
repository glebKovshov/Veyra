package dev.veyra.mixin;

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
            VkPhysicalDeviceFeatures enabled = createInfo.pEnabledFeatures();
            if (enabled == null) {
                enabled = VkPhysicalDeviceFeatures.calloc(stack);
                createInfo.pEnabledFeatures(enabled);
            }
            VeyraDeviceFeatures.mergeSupportedFeatures(enabled, supported);
            VeyraDeviceFeatures.maxColorAttachments = properties.limits().maxColorAttachments();
            VeyraDeviceFeatures.independentBlendEnabled = enabled.independentBlend();
            VeyraDeviceFeatures.vertexPipelineStoresAndAtomicsEnabled = enabled.vertexPipelineStoresAndAtomics();
            VeyraDeviceFeatures.shaderStorageImageExtendedFormatsEnabled = enabled.shaderStorageImageExtendedFormats();
            return VK10.vkCreateDevice(physicalDevice, createInfo, allocator, device);
        }
    }
}
