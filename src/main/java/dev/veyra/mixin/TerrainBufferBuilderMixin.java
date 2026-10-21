package dev.veyra.mixin;

import dev.veyra.shader.TerrainMaterialContext;
import dev.veyra.shader.TerrainMidTexCoord;
import net.vulkanmod.render.shader.PipelineManager;
import net.vulkanmod.render.vertex.TerrainBufferBuilder;
import org.lwjgl.system.MemoryUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = TerrainBufferBuilder.class, remap = false)
public abstract class TerrainBufferBuilderMixin {
    @Shadow protected long bufferPtr;
    @Shadow protected int nextElementByte;
    @Shadow int vertices;
    @Shadow private int vertexSize;

    @Unique private final float[] veyra$quadU = new float[4];
    @Unique private final float[] veyra$quadV = new float[4];
    @Unique private final float[] veyra$quadX = new float[4];
    @Unique private final float[] veyra$quadY = new float[4];
    @Unique private final float[] veyra$quadZ = new float[4];
    @Unique private final int[] veyra$quadNormal = new int[4];
    @Unique private final float[] veyra$quadTangent = new float[4];

    @Inject(method = "vertex(FFFIFFII)V", at = @At("HEAD"), remap = false)
    private void veyra$rememberQuadUv(float x, float y, float z, int color, float u, float v,
                                      int light, int packedNormal, CallbackInfo ci) {
        int corner = this.vertices & 3;
        this.veyra$quadU[corner] = u;
        this.veyra$quadV[corner] = v;
        this.veyra$quadX[corner] = x;
        this.veyra$quadY[corner] = y;
        this.veyra$quadZ[corner] = z;
        this.veyra$quadNormal[corner] = packedNormal;
    }

    @Inject(method = "vertex(FFFIFFII)V", at = @At("RETURN"), remap = false)
    private void veyra$writeExtendedTerrainAttributes(float x, float y, float z, int color,
                                                       float u, float v, int light,
                                                       int packedNormal, CallbackInfo ci) {
        if (PipelineManager.getTerrainVertexFormat() != TerrainMidTexCoord.VERTEX_FORMAT) {
            return;
        }

        long currentVertex = this.bufferPtr + this.nextElementByte - this.vertexSize;
        MemoryUtil.memPutInt(currentVertex + TerrainMidTexCoord.MATERIAL_ID_OFFSET,
                TerrainMaterialContext.materialId());
        MemoryUtil.memPutFloat(currentVertex + TerrainMidTexCoord.RENDER_TYPE_OFFSET,
                TerrainMaterialContext.renderType());
        MemoryUtil.memPutInt(currentVertex + TerrainMidTexCoord.PACKED_NORMAL_OFFSET, packedNormal);
        MemoryUtil.memPutInt(currentVertex + TerrainMidTexCoord.MID_BLOCK_OFFSET,
                TerrainMaterialContext.packedMidBlock(x, y, z));

        if ((this.vertices & 3) != 0) return;

        short midpointU = TerrainMidTexCoord.pack(TerrainMidTexCoord.center(
                this.veyra$quadU[0], this.veyra$quadU[1], this.veyra$quadU[2], this.veyra$quadU[3]));
        short midpointV = TerrainMidTexCoord.pack(TerrainMidTexCoord.center(
                this.veyra$quadV[0], this.veyra$quadV[1], this.veyra$quadV[2], this.veyra$quadV[3]));
        TerrainMidTexCoord.computeTangent(this.veyra$quadX, this.veyra$quadY, this.veyra$quadZ,
                this.veyra$quadU, this.veyra$quadV, this.veyra$quadNormal, this.veyra$quadTangent);
        long firstVertex = this.bufferPtr + this.nextElementByte - (long) this.vertexSize * 4;
        for (int corner = 0; corner < 4; corner++) {
            long pointer = firstVertex + (long) corner * this.vertexSize + TerrainMidTexCoord.ATTRIBUTE_OFFSET;
            MemoryUtil.memPutShort(pointer, midpointU);
            MemoryUtil.memPutShort(pointer + Short.BYTES, midpointV);
            long tangentPointer = firstVertex + (long) corner * this.vertexSize + TerrainMidTexCoord.TANGENT_OFFSET;
            for (int component = 0; component < this.veyra$quadTangent.length; component++) {
                MemoryUtil.memPutFloat(tangentPointer + (long) component * Float.BYTES,
                        this.veyra$quadTangent[component]);
            }
        }
    }
}
