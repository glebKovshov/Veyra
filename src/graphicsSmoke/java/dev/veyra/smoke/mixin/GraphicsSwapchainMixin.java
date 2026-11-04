package dev.veyra.smoke.mixin;

import dev.veyra.smoke.SurfaceLossProbe;
import net.vulkanmod.vulkan.framebuffer.SwapChain;
import org.lwjgl.vulkan.VkExtent2D;
import org.lwjgl.vulkan.VkSurfaceCapabilitiesKHR;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = SwapChain.class, remap = false)
public abstract class GraphicsSwapchainMixin {
    @Unique private static final VkExtent2D VEYRA$LOST_SURFACE = VkExtent2D.calloc();

    @Inject(method = "getExtent", at = @At("RETURN"), cancellable = true)
    private static void veyra$loseSurfaceOnce(VkSurfaceCapabilitiesKHR capabilities,
                                              CallbackInfoReturnable<VkExtent2D> callback) {
        if (!SurfaceLossProbe.consume()) return;
        LoggerFactory.getLogger("veyra-graphics-smoke").info(
                "Surface-loss probe: reporting 0x0 instead of {}x{} for one swapchain recreation",
                callback.getReturnValue().width(), callback.getReturnValue().height());
        callback.setReturnValue(VEYRA$LOST_SURFACE);
    }

    @Inject(method = "createSwapChain", at = @At("RETURN"))
    private void veyra$afterCreate(CallbackInfo callback) { veyra$record("after-create"); }

    @Inject(method = "cleanUp", at = @At("HEAD"))
    private void veyra$beforeCleanup(CallbackInfo callback) { veyra$record("before-cleanup"); }

    @Unique
    private void veyra$record(String phase) {
        var swap = (SwapChain) (Object) this;
        var images = swap.getImages();
        LoggerFactory.getLogger("veyra-graphics-smoke").info(
                "Surface evidence: phase={} swap={} extent={}x{} hasImages={} image/views={}",
                phase, Long.toHexString(swap.getId()), swap.getWidth(), swap.getHeight(), swap.hasImages(),
                images == null ? "none" : images.stream().map(image -> Long.toHexString(image.getId()) + "/"
                        + Long.toHexString(image.getImageView())).toList());
    }
}
