package dev.veyra.mixin;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.textures.GpuTexture;
import dev.veyra.accessor.RenderTargetAccess;
import dev.veyra.vulkan.LegacyTextureView;
import dev.veyra.vulkan.LegacyVkTextureView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RenderTarget.class)
public abstract class LegacyRenderTargetMixin implements RenderTargetAccess {
    @Shadow protected GpuTexture colorTexture;
    @Shadow protected GpuTexture depthTexture;
    @Shadow public abstract GpuTexture getColorTexture();
    @Unique private LegacyTextureView veyra$colorView;
    @Unique private LegacyTextureView veyra$depthView;

    public GpuTexture veyra$getColorTexture() { return colorTexture; }
    public void veyra$setColorTexture(GpuTexture texture) { colorTexture = texture; }
    public GpuTexture veyra$getDepthTexture() { return depthTexture; }
    public void veyra$setDepthTexture(GpuTexture texture) { depthTexture = texture; }

    public LegacyTextureView veyra$getColorTextureView() {
        // MainTarget overrides the getter with the current swapchain attachment.
        return veyra$colorView = veyra$view(getColorTexture(), veyra$colorView);
    }
    public LegacyTextureView veyra$getDepthTextureView() {
        return veyra$depthView = veyra$view(depthTexture, veyra$depthView);
    }
    public void veyra$setColorTextureView(LegacyTextureView view) {
        veyra$validate(getColorTexture(), view);
        if (veyra$colorView != null && veyra$colorView != view) veyra$colorView.close();
        veyra$colorView = view;
    }
    public void veyra$setDepthTextureView(LegacyTextureView view) {
        veyra$validate(depthTexture, view);
        if (veyra$depthView != null && veyra$depthView != view) veyra$depthView.close();
        veyra$depthView = view;
    }

    @Unique private static LegacyTextureView veyra$view(GpuTexture texture, LegacyTextureView previous) {
        if (previous != null && previous.texture() == texture && !previous.isClosed()) return previous;
        if (previous != null) previous.close();
        return texture == null || texture.isClosed() ? null : LegacyVkTextureView.create(texture);
    }
    @Unique private static void veyra$validate(GpuTexture texture, LegacyTextureView view) {
        if (view != null && view.textureForBinding() != texture) {
            throw new IllegalArgumentException("Texture view must refer to the target attachment.");
        }
    }

    @Inject(method = "destroyBuffers", at = @At("HEAD"))
    private void veyra$closeBorrowedViews(CallbackInfo callback) {
        if (veyra$colorView != null) veyra$colorView.close();
        if (veyra$depthView != null) veyra$depthView.close();
        veyra$colorView = null;
        veyra$depthView = null;
    }
}
