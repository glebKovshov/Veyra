package dev.veyra.mixin;

import dev.veyra.shader.VeyraRenderer;
import net.vulkanmod.render.shader.PipelineManager;
import net.vulkanmod.render.vertex.TerrainRenderType;
import net.vulkanmod.vulkan.shader.GraphicsPipeline;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = PipelineManager.class, remap = false)
public abstract class PipelineManagerMixin {
    @Inject(method = "getCloudsPipeline", at = @At("HEAD"), cancellable = true, remap = false)
    private static void veyra$useCloudsShader(CallbackInfoReturnable<GraphicsPipeline> callback) {
        GraphicsPipeline pipeline = VeyraRenderer.getCloudsPipeline();
        if (pipeline != null) {
            VeyraRenderer.bindCloudSceneColor(pipeline);
            callback.setReturnValue(pipeline);
        }
    }

    @Inject(method = "getTerrainShader", at = @At("HEAD"), cancellable = true, remap = false)
    private static void veyra$useTerrainShader(
            TerrainRenderType terrainRenderType, CallbackInfoReturnable<GraphicsPipeline> callback) {
        GraphicsPipeline pipeline = VeyraRenderer.getTerrainPipeline(terrainRenderType);
        if (pipeline != null) {
            callback.setReturnValue(pipeline);
        }
    }
}
