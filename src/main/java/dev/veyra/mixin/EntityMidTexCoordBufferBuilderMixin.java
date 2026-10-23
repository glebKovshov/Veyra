package dev.veyra.mixin;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import dev.veyra.shader.EntityMidTexCoord;
import org.lwjgl.system.MemoryUtil;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Fills the Veyra-only entity midpoint attribute when a four-vertex quad is completed. */
@Mixin(BufferBuilder.class)
public abstract class EntityMidTexCoordBufferBuilderMixin {
    @Shadow @Final private VertexFormat format;
    @Shadow @Final private VertexFormat.Mode mode;
    @Shadow @Final private int vertexSize;
    @Shadow @Final private int[] offsetsByElement;
    @Shadow private int vertices;
    @Shadow private int elementsToFill;
    @Shadow private long vertexPointer;

    @Inject(method = "endLastVertex", at = @At("HEAD"))
    private void veyra$writeQuadMidTexCoord(CallbackInfo ci) {
        if (vertices == 0 || format != EntityMidTexCoord.VERTEX_FORMAT || mode != VertexFormat.Mode.QUADS) return;

        long uvPointer = vertexPointer + offsetsByElement[VertexFormatElement.UV0.id()];
        long midpointPointer = vertexPointer + offsetsByElement[EntityMidTexCoord.ELEMENT.id()];
        MemoryUtil.memPutFloat(midpointPointer, 0.0F);
        MemoryUtil.memPutFloat(midpointPointer + Float.BYTES, 0.0F);
        elementsToFill &= ~EntityMidTexCoord.ELEMENT.mask();

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
    }
}
