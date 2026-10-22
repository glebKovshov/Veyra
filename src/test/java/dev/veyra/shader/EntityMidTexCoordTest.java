package dev.veyra.shader;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

class EntityMidTexCoordTest {
    @Test
    void appendsMidpointWithoutChangingNativeEntityOffsets() {
        assertNotSame(DefaultVertexFormat.NEW_ENTITY, EntityMidTexCoord.VERTEX_FORMAT);
        for (VertexFormatElement element : DefaultVertexFormat.NEW_ENTITY.getElements()) {
            assertEquals(DefaultVertexFormat.NEW_ENTITY.getOffset(element),
                    EntityMidTexCoord.VERTEX_FORMAT.getOffset(element), element.toString());
        }
        assertEquals(DefaultVertexFormat.NEW_ENTITY.getVertexSize(), EntityMidTexCoord.MIDPOINT_OFFSET);
        assertEquals(DefaultVertexFormat.NEW_ENTITY.getVertexSize() + Float.BYTES * 2,
                EntityMidTexCoord.VERTEX_FORMAT.getVertexSize());
    }

    @Test
    void averagesAllFourCoordinatesForAQuad() {
        assertEquals(0.375F, EntityMidTexCoord.average(0.0F, 1.0F, 0.5F, 0.0F));
        assertEquals(0.625F, EntityMidTexCoord.average(0.25F, 0.75F, 1.0F, 0.5F));
    }
}
