package dev.veyra.mixin;

import dev.veyra.shader.VeyraRenderer;
import dev.veyra.shader.TerrainMidTexCoord;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.vulkanmod.render.shader.PipelineManager;
import net.vulkanmod.render.vertex.TerrainRenderType;
import net.vulkanmod.render.vertex.CustomVertexFormat;
import net.vulkanmod.vulkan.shader.GraphicsPipeline;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = PipelineManager.class, remap = false)
public abstract class PipelineManagerMixin {
    @ModifyArg(
            method = "init",
            at = @At(value = "INVOKE", target = "Lnet/vulkanmod/render/shader/PipelineManager;setTerrainVertexFormat(Lcom/mojang/blaze3d/vertex/VertexFormat;)V"),
            index = 0,
            remap = false)
    private static VertexFormat veyra$extendTerrainFormat(VertexFormat original) {
        return original == CustomVertexFormat.COMPRESSED_TERRAIN
                ? TerrainMidTexCoord.VERTEX_FORMAT
                : original;
    }

    @ModifyArg(
            method = "createBasicPipelines",
            at = @At(value = "INVOKE", target = "Lnet/vulkanmod/render/shader/PipelineManager;createPipeline(Ljava/lang/String;Ljava/lang/String;Lnet/vulkanmod/render/shader/PipelineConfig;Lcom/mojang/blaze3d/vertex/VertexFormat;)Lnet/vulkanmod/vulkan/shader/GraphicsPipeline;"),
            index = 3,
            remap = false)
    private static VertexFormat veyra$extendTerrainPipelineFormat(VertexFormat original) {
        return original == CustomVertexFormat.COMPRESSED_TERRAIN
                ? TerrainMidTexCoord.VERTEX_FORMAT
                : original;
    }

    @ModifyArg(
            method = "createBasicPipelines",
            at = @At(value = "INVOKE", target = "Lnet/vulkanmod/render/shader/PipelineManager;createPipeline(Ljava/lang/String;Ljava/lang/String;Lcom/mojang/blaze3d/vertex/VertexFormat;)Lnet/vulkanmod/vulkan/shader/GraphicsPipeline;"),
            index = 2,
            remap = false)
    private static VertexFormat veyra$extendEarlyZPipelineFormat(VertexFormat original) {
        return original == CustomVertexFormat.COMPRESSED_TERRAIN
                ? TerrainMidTexCoord.VERTEX_FORMAT
                : original;
    }

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
