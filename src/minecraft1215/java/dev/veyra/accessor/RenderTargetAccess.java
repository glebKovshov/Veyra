package dev.veyra.accessor;

import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.pipeline.RenderTarget;
import dev.veyra.vulkan.LegacyTextureView;

public interface RenderTargetAccess {
    static LegacyTextureView colorView(RenderTarget target) {
        return ((RenderTargetAccess) target).veyra$getColorTextureView();
    }
    static LegacyTextureView depthView(RenderTarget target) {
        return ((RenderTargetAccess) target).veyra$getDepthTextureView();
    }
    GpuTexture veyra$getColorTexture();
    void veyra$setColorTexture(GpuTexture texture);
    LegacyTextureView veyra$getColorTextureView();
    void veyra$setColorTextureView(LegacyTextureView view);
    GpuTexture veyra$getDepthTexture();
    void veyra$setDepthTexture(GpuTexture texture);
    LegacyTextureView veyra$getDepthTextureView();
    void veyra$setDepthTextureView(LegacyTextureView view);
}
