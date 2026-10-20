package dev.veyra.mixin;

import dev.veyra.shader.TerrainMidTexCoord;
import net.vulkanmod.render.chunk.build.thread.ThreadBuilderPack;
import net.vulkanmod.render.vertex.TerrainBuilder;
import net.vulkanmod.render.vertex.VertexBuilder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(value = ThreadBuilderPack.class, remap = false)
public abstract class ThreadBuilderPackMixin {
    @ModifyArg(
            method = "lambda$defaultTerrainBuilderConstructor$0",
            at = @At(value = "INVOKE", target = "Lnet/vulkanmod/render/vertex/TerrainBuilder;<init>(ILnet/vulkanmod/render/vertex/VertexBuilder;)V"),
            index = 1,
            remap = false)
    private static VertexBuilder veyra$useExtendedCompressedBuilder(VertexBuilder original) {
        return net.vulkanmod.render.shader.PipelineManager.getTerrainVertexFormat()
                == TerrainMidTexCoord.VERTEX_FORMAT
                ? new TerrainMidTexCoord.VertexBuilderWithMidpoint()
                : original;
    }
}
