package dev.veyra.mixin;

import dev.veyra.vulkan.VeyraColorTargetViews;
import net.vulkanmod.vulkan.framebuffer.SwapChain;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = SwapChain.class, remap = false)
public abstract class SwapChainViewsMixin {
    @Inject(method = {"recreate", "cleanUp"}, at = @At("HEAD"))
    private void veyra$retireSampledViews(CallbackInfo callback) {
        var images = ((SwapChain) (Object) this).getImages();
        if (images != null) images.forEach(VeyraColorTargetViews::forget);
    }
}
