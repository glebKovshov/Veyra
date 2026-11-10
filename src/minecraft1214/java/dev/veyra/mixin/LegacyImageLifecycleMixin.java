package dev.veyra.mixin;

import dev.veyra.compat.LegacyDepthViews;
import dev.veyra.compat.LegacyDraws;
import net.vulkanmod.vulkan.texture.VulkanImage;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = VulkanImage.class, remap = false)
public abstract class LegacyImageLifecycleMixin {
    @Inject(method = "doFree", at = @At("HEAD"))
    private void veyra$freeDepthView(CallbackInfo callback) {
        LegacyDepthViews.forget((VulkanImage) (Object) this);
    }

    // Layout barriers are invalid inside a render pass, and Veyra binds a draw's inputs inside one here.
    @Inject(method = "transitionImageLayout(Lorg/lwjgl/system/MemoryStack;Lorg/lwjgl/vulkan/VkCommandBuffer;I)V", at = @At("HEAD"))
    private void veyra$leaveRenderPass(MemoryStack stack, VkCommandBuffer commands, int layout, CallbackInfo callback) {
        if (((VulkanImage) (Object) this).getCurrentLayout() != layout) LegacyDraws.suspendForBarrier();
    }
}
