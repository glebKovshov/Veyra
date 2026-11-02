package dev.veyra.mixin;

import net.vulkanmod.vulkan.texture.VulkanImage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;
import static org.lwjgl.vulkan.VK10.*;

@Mixin(value = VulkanImage.class, remap = false)
public abstract class VulkanImageLayoutMixin {
    @ModifyArgs(method = "transitionLayout(Lorg/lwjgl/system/MemoryStack;Lorg/lwjgl/vulkan/VkCommandBuffer;"
            + "Lnet/vulkanmod/vulkan/texture/VulkanImage;IIIIIII)V", remap = false,
            at = @At(value = "INVOKE", target =
            "Lorg/lwjgl/vulkan/VK10;vkCmdPipelineBarrier(Lorg/lwjgl/vulkan/VkCommandBuffer;IIILorg/lwjgl/vulkan/VkMemoryBarrier$Buffer;Lorg/lwjgl/vulkan/VkBufferMemoryBarrier$Buffer;Lorg/lwjgl/vulkan/VkImageMemoryBarrier$Buffer;)V"))
    private static void veyra$includeLateDepthTests(Args arguments) {
        int source = arguments.get(1), destination = arguments.get(2);
        // Depth clears and writes also run in late fragment tests on dynamic-rendering backends.
        if ((source & VK_PIPELINE_STAGE_EARLY_FRAGMENT_TESTS_BIT) != 0)
            arguments.set(1, source | VK_PIPELINE_STAGE_LATE_FRAGMENT_TESTS_BIT);
        if ((destination & VK_PIPELINE_STAGE_EARLY_FRAGMENT_TESTS_BIT) != 0)
            arguments.set(2, destination | VK_PIPELINE_STAGE_LATE_FRAGMENT_TESTS_BIT);
    }
}
