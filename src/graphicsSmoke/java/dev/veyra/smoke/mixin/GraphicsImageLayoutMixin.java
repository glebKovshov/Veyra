package dev.veyra.smoke.mixin;

import net.vulkanmod.vulkan.texture.VulkanImage;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Arrays;
import java.util.stream.Collectors;

import static org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL;

/** Trace unexpected layout changes on the sampled MRT image; test-only and excluded from the mod JAR. */
@Mixin(value = VulkanImage.class, remap = false)
public abstract class GraphicsImageLayoutMixin {
    @Unique private static int veyra$loggedTransitions;

    @Inject(method = "transitionImageLayout(Lorg/lwjgl/system/MemoryStack;Lorg/lwjgl/vulkan/VkCommandBuffer;Lnet/vulkanmod/vulkan/texture/VulkanImage;I)V",
            at = @At("HEAD"), remap = false)
    private static void veyra$traceMrtTransitions(org.lwjgl.system.MemoryStack stack, VkCommandBuffer commands,
            VulkanImage image, int newLayout, CallbackInfo ci) {
        if (!Boolean.getBoolean("veyra.graphicsSmoke") || veyra$loggedTransitions >= 40
                || image == null || !"Veyra shader pack colortex1 A / Color".equals(image.name)
                || image.getCurrentLayout() == newLayout
                || newLayout != VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL
                && newLayout != VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL) return;
        veyra$loggedTransitions++;
        String caller = Arrays.stream(Thread.currentThread().getStackTrace()).skip(2).limit(7)
                .map(StackTraceElement::toString).collect(Collectors.joining(" <- "));
        LoggerFactory.getLogger("veyra-graphics-smoke").info(
                "MRT layout transition: image={} before={} after={} command={} caller={}",
                Long.toHexString(image.getId()), image.getCurrentLayout(), newLayout,
                Long.toHexString(commands.address()), caller);
    }
}
