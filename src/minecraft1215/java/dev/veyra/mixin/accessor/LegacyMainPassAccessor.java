package dev.veyra.mixin.accessor;

import com.mojang.blaze3d.textures.GpuTexture;
import net.vulkanmod.vulkan.framebuffer.Framebuffer;
import net.vulkanmod.vulkan.pass.DefaultMainPass;
import dev.veyra.vulkan.LegacyMainPassAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import dev.veyra.vulkan.LegacyBorrowedDepthTexture;

@Mixin(value = DefaultMainPass.class, remap = false)
public abstract class LegacyMainPassAccessor implements LegacyMainPassAccess {
    @Accessor("mainFramebuffer") public abstract Framebuffer getMainFramebuffer();
    @Unique private LegacyBorrowedDepthTexture veyra$depthTexture;

    public GpuTexture getDepthAttachment() {
        var image = getMainFramebuffer().getDepthAttachment();
        if (veyra$depthTexture != null && veyra$depthTexture.getVulkanImage() != image) {
            veyra$depthTexture.close();
            veyra$depthTexture = null;
        }
        if (image != null && veyra$depthTexture == null)
            veyra$depthTexture = new LegacyBorrowedDepthTexture(image);
        return veyra$depthTexture;
    }

    @Inject(method = {"onResize", "cleanUp"}, at = @At("HEAD"))
    private void veyra$releaseDepthRegistration(CallbackInfo callback) {
        if (veyra$depthTexture != null) veyra$depthTexture.close();
        veyra$depthTexture = null;
    }
}
