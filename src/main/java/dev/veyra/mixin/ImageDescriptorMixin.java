package dev.veyra.mixin;

import dev.veyra.vulkan.VeyraColorTargetViews;
import net.vulkanmod.vulkan.shader.descriptor.ImageDescriptor;
import net.vulkanmod.vulkan.texture.VulkanImage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = ImageDescriptor.class, remap = false)
public abstract class ImageDescriptorMixin {
    @Inject(method = "getImageView", at = @At("RETURN"), cancellable = true)
    private void veyra$preserveColorChannels(VulkanImage image, CallbackInfoReturnable<Long> callback) {
        var descriptor = (ImageDescriptor) (Object) this;
        if (!descriptor.isStorageImage()) callback.setReturnValue(
                VeyraColorTargetViews.sampledView(image, descriptor.getMipLevel(), callback.getReturnValue()));
    }
}
