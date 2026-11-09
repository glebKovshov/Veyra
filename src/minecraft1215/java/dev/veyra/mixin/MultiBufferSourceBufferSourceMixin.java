package dev.veyra.mixin;

import dev.veyra.shader.VeyraRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(MultiBufferSource.BufferSource.class)
public abstract class MultiBufferSourceBufferSourceMixin {
    @ModifyVariable(method = "getBuffer", at = @At("HEAD"), argsOnly = true)
    private RenderType veyra$captureIdentifierPipeline(RenderType renderType) {
        // The returned render type retains its identifier-specific pipeline until the batch draws.
        return VeyraRenderer.routeBlockEntityRenderType(renderType);
    }
}
