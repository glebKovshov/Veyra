package dev.veyra.mixin;

import dev.veyra.shader.TerrainMidTexCoord;
import net.vulkanmod.render.vertex.TerrainBuilder;
import net.vulkanmod.render.vertex.VertexBuilder;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = TerrainBuilder.class, remap = false)
public abstract class LegacyTerrainBuilderMixin {
    @Shadow protected VertexBuilder vertexBuilder;

    @Redirect(method = "<init>", at = @At(value = "FIELD", opcode = Opcodes.PUTFIELD,
            target = "Lnet/vulkanmod/render/vertex/TerrainBuilder;vertexBuilder:Lnet/vulkanmod/render/vertex/VertexBuilder;"))
    private void veyra$extendCompressedBuilder(TerrainBuilder owner, VertexBuilder original) {
        // Assign before the native constructor passes this builder to each facing buffer.
        vertexBuilder = net.vulkanmod.render.PipelineManager.terrainVertexFormat == TerrainMidTexCoord.VERTEX_FORMAT
                ? new TerrainMidTexCoord.VertexBuilderWithMidpoint() : original;
    }
}
