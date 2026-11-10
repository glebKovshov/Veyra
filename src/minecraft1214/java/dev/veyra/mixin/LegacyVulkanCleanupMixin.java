package dev.veyra.mixin;

import dev.veyra.compat.LegacyDepthViews;
import net.vulkanmod.vulkan.Vulkan;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = Vulkan.class, remap = false)
public abstract class LegacyVulkanCleanupMixin {
    @Inject(method = "cleanUp", at = @At("HEAD"))
    private static void veyra$destroyDepthViews(CallbackInfo callback) {
        LegacyDepthViews.clear();
    }
}
