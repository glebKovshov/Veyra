package dev.veyra.mixin;

import dev.veyra.vulkan.VeyraDeviceFeatures;
import net.vulkanmod.vulkan.device.DeviceManager;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkPhysicalDeviceFeatures;
import org.lwjgl.vulkan.VkPhysicalDeviceProperties;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(value = DeviceManager.class, remap = false)
public abstract class DeviceManagerMixin {
    @ModifyArg(method = "createLogicalDevice", at = @At(value = "INVOKE", target =
            "Lorg/lwjgl/vulkan/VkDeviceCreateInfo;pEnabledFeatures(Lorg/lwjgl/vulkan/VkPhysicalDeviceFeatures;)Lorg/lwjgl/vulkan/VkDeviceCreateInfo;"), index = 0)
    private static VkPhysicalDeviceFeatures veyra$enableSupportedFeatures(VkPhysicalDeviceFeatures requested) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkPhysicalDeviceFeatures supported = VkPhysicalDeviceFeatures.calloc(stack);
            VK10.vkGetPhysicalDeviceFeatures(DeviceManager.physicalDevice, supported);
            VkPhysicalDeviceProperties properties = VkPhysicalDeviceProperties.calloc(stack);
            VK10.vkGetPhysicalDeviceProperties(DeviceManager.physicalDevice, properties);
            VeyraDeviceFeatures.maxColorAttachments = properties.limits().maxColorAttachments();
            VeyraDeviceFeatures.independentBlendEnabled = supported.independentBlend();
            VeyraDeviceFeatures.vertexPipelineStoresAndAtomicsEnabled = supported.vertexPipelineStoresAndAtomics();
            VeyraDeviceFeatures.shaderStorageImageExtendedFormatsEnabled = supported.shaderStorageImageExtendedFormats();
            return requested.independentBlend(supported.independentBlend())
                    .vertexPipelineStoresAndAtomics(supported.vertexPipelineStoresAndAtomics())
                    .shaderStorageImageExtendedFormats(supported.shaderStorageImageExtendedFormats());
        }
    }
}
