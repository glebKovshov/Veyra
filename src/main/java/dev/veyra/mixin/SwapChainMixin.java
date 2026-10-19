package dev.veyra.mixin;

import dev.veyra.vulkan.VeyraDeviceFeatures;
import net.vulkanmod.vulkan.Vulkan;
import net.vulkanmod.vulkan.device.DeviceManager;
import net.vulkanmod.vulkan.framebuffer.SwapChain;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.KHRSurface;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkSurfaceCapabilitiesKHR;
import org.lwjgl.vulkan.VkSwapchainCreateInfoKHR;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = SwapChain.class, remap = false)
public abstract class SwapChainMixin {
    @Redirect(method = "createSwapChain", at = @At(value = "INVOKE", target =
            "Lorg/lwjgl/vulkan/VkSwapchainCreateInfoKHR;imageUsage(I)Lorg/lwjgl/vulkan/VkSwapchainCreateInfoKHR;"))
    private VkSwapchainCreateInfoKHR veyra$readableSwapchain(VkSwapchainCreateInfoKHR info, int original) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkSurfaceCapabilitiesKHR capabilities = VkSurfaceCapabilitiesKHR.calloc(stack);
            Vulkan.checkResult(KHRSurface.vkGetPhysicalDeviceSurfaceCapabilitiesKHR(DeviceManager.physicalDevice,
                    Vulkan.getSurface(), capabilities), "Query Veyra swapchain usage capabilities");
            int usage = original | (capabilities.supportedUsageFlags() & VK10.VK_IMAGE_USAGE_TRANSFER_SRC_BIT);
            VeyraDeviceFeatures.swapchainUsage = usage;
            return info.imageUsage(usage);
        }
    }

    @ModifyArg(method = "createSwapChain", at = @At(value = "INVOKE", target =
            "Lnet/vulkanmod/vulkan/texture/VulkanImage;<init>(Ljava/lang/String;JIIIIIIJ)V"), index = 7)
    private int veyra$preserveActualImageUsage(int reported) {
        // VulkanMod constructs these borrowed-image wrappers with usage=0, despite creating
        // the native swapchain with color/sampled usage. Keep the metadata truthful.
        return VeyraDeviceFeatures.swapchainUsage;
    }
}
