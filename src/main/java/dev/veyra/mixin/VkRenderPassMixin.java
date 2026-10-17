package dev.veyra.mixin;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import dev.veyra.shader.VeyraRenderer;
import net.vulkanmod.render.engine.VkRenderPass;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(value = VkRenderPass.class, remap = false)
public abstract class VkRenderPassMixin {
    @ModifyVariable(method = "setPipeline", at = @At("HEAD"), argsOnly = true, remap = false)
    private RenderPipeline veyra$routeEntityPipeline(RenderPipeline original) {
        RenderPipeline replacement = VeyraRenderer.getEntityPipeline(original);
        if (replacement == null) {
            replacement = VeyraRenderer.getBeaconBeamPipeline(original);
        }
        return replacement == null ? original : replacement;
    }
}
