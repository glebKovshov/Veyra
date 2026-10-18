package dev.veyra.mixin;

import dev.veyra.vulkan.VeyraGeometryTargets;
import net.vulkanmod.vulkan.Renderer;
import net.vulkanmod.vulkan.shader.GraphicsPipeline;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = Renderer.class, remap = false)
public abstract class RendererGeometryTargetsMixin {
    @Inject(method = "bindGraphicsPipeline", at = @At("HEAD"))
    private void veyra$routeGeometryTargets(GraphicsPipeline pipeline, CallbackInfo ci) {
        VeyraGeometryTargets.beforeBind(pipeline);
    }
}
