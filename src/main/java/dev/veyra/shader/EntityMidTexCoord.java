package dev.veyra.shader;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;

/** Extends Minecraft's entity format only for Veyra pipelines that consume Iris mc_midTexCoord. */
public final class EntityMidTexCoord {
    public static final VertexFormatElement ELEMENT = registerElement();
    public static final VertexFormat VERTEX_FORMAT = VertexFormat.builder()
            .add("Position", VertexFormatElement.POSITION)
            .add("Color", VertexFormatElement.COLOR)
            .add("UV0", VertexFormatElement.UV0)
            .add("UV1", VertexFormatElement.UV1)
            .add("UV2", VertexFormatElement.UV2)
            .add("Normal", VertexFormatElement.NORMAL)
            .padding(1)
            .add("MidTexCoord", ELEMENT)
            .build();
    public static final int MIDPOINT_OFFSET = VERTEX_FORMAT.getOffset(ELEMENT);
    public static final int UV_OFFSET = VERTEX_FORMAT.getOffset(VertexFormatElement.UV0);

    static {
        if (DefaultVertexFormat.NEW_ENTITY.getVertexSize() != 36
                || VERTEX_FORMAT.getVertexSize() != DefaultVertexFormat.NEW_ENTITY.getVertexSize() + ELEMENT.byteSize()
                || MIDPOINT_OFFSET != DefaultVertexFormat.NEW_ENTITY.getVertexSize()) {
            throw new ExceptionInInitializerError("Veyra's entity midpoint format must append to NEW_ENTITY (native size="
                    + DefaultVertexFormat.NEW_ENTITY.getVertexSize() + ", extended size="
                    + VERTEX_FORMAT.getVertexSize() + ", midpoint offset=" + MIDPOINT_OFFSET + ").");
        }
        for (VertexFormatElement nativeElement : DefaultVertexFormat.NEW_ENTITY.getElements()) {
            if (VERTEX_FORMAT.getOffset(nativeElement) != DefaultVertexFormat.NEW_ENTITY.getOffset(nativeElement)) {
                throw new ExceptionInInitializerError("Veyra's entity midpoint format changed native attribute offsets.");
            }
        }
    }

    private EntityMidTexCoord() { }

    private static VertexFormatElement registerElement() {
        synchronized (VertexFormatElement.class) {
            for (int id = 7; id < 32; id++) {
                if (VertexFormatElement.byId(id) == null) {
                    return VertexFormatElement.register(id, 3,
                            VertexFormatElement.Type.FLOAT, VertexFormatElement.Usage.UV, 2);
                }
            }
        }
        throw new ExceptionInInitializerError("No free vertex element ID is available for mc_midTexCoord.");
    }

    public static float average(float first, float second, float third, float fourth) {
        return (first + second + third + fourth) * 0.25F;
    }
}
