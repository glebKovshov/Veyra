package dev.veyra.mixin;

import net.vulkanmod.vulkan.Renderer;
import net.vulkanmod.vulkan.memory.buffer.Buffer;
import net.vulkanmod.vulkan.texture.ImageUtil;
import net.vulkanmod.vulkan.texture.VulkanImage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Submit pending image writes and layout changes before the independent readback command buffer. */
@Mixin(value = ImageUtil.class, remap = false)
public abstract class ImageUtilReadbackMixin {
    @Inject(method = "copyImageToBuffer", at = @At("HEAD"))
    private static void veyra$submitBeforeReadback(VulkanImage source, Buffer destination, int mip,
            int width, int height, int x, int y, int offset, int rowLength, int imageHeight, CallbackInfo ci) {
        if (!Renderer.isRecording()) return;
        Renderer renderer = Renderer.getInstance();
        if (source.getId() == renderer.getSwapChain().getColorAttachment().getId()) {
            // A flush resumes the main pass; submit its attachment transition before reading that image.
            renderer.getMainPass().rebindMainTarget();
        }
        renderer.flushCmds();
    }
}
