package dev.veyra.mixin;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import dev.veyra.shader.EntityMidTexCoord;
import dev.veyra.shader.TerrainMidTexCoord;
import org.lwjgl.system.MemoryUtil;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Fills the Veyra-only entity midpoint and tangent attributes when a four-vertex quad is completed. */
@Mixin(BufferBuilder.class)
public abstract class EntityMidTexCoordBufferBuilderMixin {
    @Shadow @Final private VertexFormat format;
    @Shadow @Final private VertexFormat.Mode mode;
    @Shadow @Final private int vertexSize;
    @Shadow @Final private int[] offsetsByElement;
    @Shadow private int vertices;
    @Shadow private int elementsToFill;
    @Shadow private long vertexPointer;
    @Unique private final float[] veyra$x = new float[3], veyra$y = new float[3], veyra$z = new float[3];
    @Unique private final float[] veyra$u = new float[3], veyra$v = new float[3], veyra$tangent = new float[4];
    @Unique private final int[] veyra$packedNormal = new int[1];

    @Inject(method = "endLastVertex", at = @At("HEAD"))
    private void veyra$writeQuadMidTexCoord(CallbackInfo ci) {
        if (vertices == 0 || format != EntityMidTexCoord.VERTEX_FORMAT || mode != VertexFormat.Mode.QUADS) return;

        long uvPointer = vertexPointer + offsetsByElement[VertexFormatElement.UV0.id()];
        long midpointPointer = vertexPointer + offsetsByElement[EntityMidTexCoord.ELEMENT.id()];
        MemoryUtil.memPutFloat(midpointPointer, 0.0F);
        MemoryUtil.memPutFloat(midpointPointer + Float.BYTES, 0.0F);
        long tangentPointer = vertexPointer + offsetsByElement[EntityMidTexCoord.TANGENT_X.id()];
        for (int component = 0; component < 4; component++) {
            MemoryUtil.memPutFloat(tangentPointer + (long) component * Float.BYTES, 0.0F);
        }
        elementsToFill &= ~EntityMidTexCoord.extensionMask();

        if ((vertices & 3) != 0) return;
        long firstPointer = vertexPointer - 3L * vertexSize;
        int uvOffset = offsetsByElement[VertexFormatElement.UV0.id()];
        int midpointOffset = offsetsByElement[EntityMidTexCoord.ELEMENT.id()];
        float midU = EntityMidTexCoord.average(
                MemoryUtil.memGetFloat(firstPointer + uvOffset),
                MemoryUtil.memGetFloat(firstPointer + vertexSize + uvOffset),
                MemoryUtil.memGetFloat(firstPointer + 2L * vertexSize + uvOffset),
                MemoryUtil.memGetFloat(firstPointer + 3L * vertexSize + uvOffset));
        float midV = EntityMidTexCoord.average(
                MemoryUtil.memGetFloat(firstPointer + uvOffset + Float.BYTES),
                MemoryUtil.memGetFloat(firstPointer + vertexSize + uvOffset + Float.BYTES),
                MemoryUtil.memGetFloat(firstPointer + 2L * vertexSize + uvOffset + Float.BYTES),
                MemoryUtil.memGetFloat(firstPointer + 3L * vertexSize + uvOffset + Float.BYTES));
        for (int corner = 0; corner < 4; corner++) {
            long pointer = firstPointer + (long) corner * vertexSize + midpointOffset;
            MemoryUtil.memPutFloat(pointer, midU);
            MemoryUtil.memPutFloat(pointer + Float.BYTES, midV);
        }

        // Iris derives one tangent per quad from its first three corners (NormalHelper.computeTangent).
        int positionOffset = offsetsByElement[VertexFormatElement.POSITION.id()];
        int normalOffset = offsetsByElement[VertexFormatElement.NORMAL.id()];
        int tangentOffset = offsetsByElement[EntityMidTexCoord.TANGENT_X.id()];
        float[] x = veyra$x, y = veyra$y, z = veyra$z, u = veyra$u, v = veyra$v, tangent = veyra$tangent;
        for (int corner = 0; corner < 3; corner++) {
            long pointer = firstPointer + (long) corner * vertexSize;
            x[corner] = MemoryUtil.memGetFloat(pointer + positionOffset);
            y[corner] = MemoryUtil.memGetFloat(pointer + positionOffset + Float.BYTES);
            z[corner] = MemoryUtil.memGetFloat(pointer + positionOffset + 2L * Float.BYTES);
            u[corner] = MemoryUtil.memGetFloat(pointer + uvOffset);
            v[corner] = MemoryUtil.memGetFloat(pointer + uvOffset + Float.BYTES);
        }
        // Signed normal bytes x, y, z; the padding byte above them is ignored by computeTangent.
        int[] packedNormal = veyra$packedNormal;
        packedNormal[0] = MemoryUtil.memGetInt(firstPointer + normalOffset) & 0x00FFFFFF;
        TerrainMidTexCoord.computeTangent(x, y, z, u, v, packedNormal, tangent);
        for (int corner = 0; corner < 4; corner++) {
            long pointer = firstPointer + (long) corner * vertexSize + tangentOffset;
            for (int component = 0; component < 4; component++) {
                MemoryUtil.memPutFloat(pointer + (long) component * Float.BYTES, tangent[component]);
            }
        }
    }
}
