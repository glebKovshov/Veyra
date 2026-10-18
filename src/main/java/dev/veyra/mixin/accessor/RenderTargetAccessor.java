package dev.veyra.mixin.accessor;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(RenderTarget.class)
public interface RenderTargetAccessor {
    @Accessor("colorTexture")
    GpuTexture veyra$getColorTexture();

    @Accessor("colorTexture")
    void veyra$setColorTexture(GpuTexture texture);

    @Accessor("colorTextureView")
    GpuTextureView veyra$getColorTextureView();

    @Accessor("colorTextureView")
    void veyra$setColorTextureView(GpuTextureView view);

    @Accessor("depthTexture")
    GpuTexture veyra$getDepthTexture();

    @Accessor("depthTexture")
    void veyra$setDepthTexture(GpuTexture texture);

    @Accessor("depthTextureView")
    GpuTextureView veyra$getDepthTextureView();

    @Accessor("depthTextureView")
    void veyra$setDepthTextureView(GpuTextureView view);
}
