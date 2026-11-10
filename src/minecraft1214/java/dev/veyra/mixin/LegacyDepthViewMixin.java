package dev.veyra.mixin;

import dev.veyra.compat.LegacyDepthViews;
import net.vulkanmod.vulkan.shader.descriptor.ImageDescriptor;
import net.vulkanmod.vulkan.texture.VulkanImage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = ImageDescriptor.class, remap = false)
public abstract class LegacyDepthViewMixin {
    @Inject(method = "getImageView", at = @At("RETURN"), cancellable = true)
    private void veyra$sampleDepthAspect(VulkanImage image, CallbackInfoReturnable<Long> callback) {
        if (!((ImageDescriptor) (Object) this).isStorageImage) {
            callback.setReturnValue(LegacyDepthViews.sampledView(image, callback.getReturnValue()));
        }
    }
}
