package dev.veyra.mixin;

import dev.veyra.vulkan.VeyraDeviceFeatures;
import net.vulkanmod.vulkan.device.DeviceManager;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkPhysicalDeviceFeatures;
import org.lwjgl.vulkan.VkPhysicalDeviceProperties;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = DeviceManager.class, remap = false)
public abstract class DeviceManagerMixin {
    @Redirect(method = "createLogicalDevice", at = @At(value = "INVOKE", target =
            "Lorg/lwjgl/vulkan/VkPhysicalDeviceFeatures;logicOp(Z)Lorg/lwjgl/vulkan/VkPhysicalDeviceFeatures;"))
    private static VkPhysicalDeviceFeatures veyra$enableIndependentBlend(VkPhysicalDeviceFeatures requested, boolean logicOp) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkPhysicalDeviceFeatures supported = VkPhysicalDeviceFeatures.calloc(stack);
            VK10.vkGetPhysicalDeviceFeatures(DeviceManager.physicalDevice, supported);
            VkPhysicalDeviceProperties properties = VkPhysicalDeviceProperties.calloc(stack);
            VK10.vkGetPhysicalDeviceProperties(DeviceManager.physicalDevice, properties);
            VeyraDeviceFeatures.maxColorAttachments = properties.limits().maxColorAttachments();
            VeyraDeviceFeatures.independentBlendEnabled = supported.independentBlend();
            return requested.logicOp(logicOp).independentBlend(supported.independentBlend());
        }
    }
}
