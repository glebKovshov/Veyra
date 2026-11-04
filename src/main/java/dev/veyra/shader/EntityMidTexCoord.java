package dev.veyra.shader;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;

/**
 * Extends Minecraft's entity format only for Veyra pipelines that consume Iris mc_midTexCoord or at_tangent.
 * Both are per-quad values that Iris' extended ENTITY format carries; Veyra fills them when a quad completes.
 */
public final class EntityMidTexCoord {
    public static final VertexFormatElement ELEMENT = registerElement(3, VertexFormatElement.Usage.UV, 2);
    // VulkanMod 0.6.8 only maps generic FLOAT attributes with one component, as for the terrain tangent.
    public static final VertexFormatElement TANGENT_X = registerElement(0, VertexFormatElement.Usage.GENERIC, 1);
    public static final VertexFormatElement TANGENT_Y = registerElement(0, VertexFormatElement.Usage.GENERIC, 1);
    public static final VertexFormatElement TANGENT_Z = registerElement(0, VertexFormatElement.Usage.GENERIC, 1);
    public static final VertexFormatElement TANGENT_W = registerElement(0, VertexFormatElement.Usage.GENERIC, 1);
    public static final VertexFormatElement[] TANGENT_ELEMENTS = {TANGENT_X, TANGENT_Y, TANGENT_Z, TANGENT_W};
    private static final String[] TANGENT_INPUTS = EntityVertexShaderAdapter.TANGENT_INPUTS;
    public static final VertexFormat VERTEX_FORMAT = VertexFormat.builder()
            .add("Position", VertexFormatElement.POSITION)
            .add("Color", VertexFormatElement.COLOR)
            .add("UV0", VertexFormatElement.UV0)
            .add("UV1", VertexFormatElement.UV1)
            .add("UV2", VertexFormatElement.UV2)
            .add("Normal", VertexFormatElement.NORMAL)
            .padding(1)
            .add("MidTexCoord", ELEMENT)
            .add(TANGENT_INPUTS[0], TANGENT_X)
            .add(TANGENT_INPUTS[1], TANGENT_Y)
            .add(TANGENT_INPUTS[2], TANGENT_Z)
            .add(TANGENT_INPUTS[3], TANGENT_W)
            .build();
    public static final int MIDPOINT_OFFSET = VERTEX_FORMAT.getOffset(ELEMENT);
    public static final int TANGENT_OFFSET = VERTEX_FORMAT.getOffset(TANGENT_X);
    public static final int UV_OFFSET = VERTEX_FORMAT.getOffset(VertexFormatElement.UV0);

    static {
        int nativeSize = DefaultVertexFormat.NEW_ENTITY.getVertexSize();
        if (nativeSize != 36 || MIDPOINT_OFFSET != nativeSize
                || TANGENT_OFFSET != nativeSize + ELEMENT.byteSize()
                || VERTEX_FORMAT.getVertexSize() != TANGENT_OFFSET + 4 * Float.BYTES) {
            throw new ExceptionInInitializerError("Veyra's extended entity format must append to NEW_ENTITY (native size="
                    + nativeSize + ", extended size=" + VERTEX_FORMAT.getVertexSize()
                    + ", midpoint offset=" + MIDPOINT_OFFSET + ", tangent offset=" + TANGENT_OFFSET + ").");
        }
        for (VertexFormatElement nativeElement : DefaultVertexFormat.NEW_ENTITY.getElements()) {
            if (VERTEX_FORMAT.getOffset(nativeElement) != DefaultVertexFormat.NEW_ENTITY.getOffset(nativeElement)) {
                throw new ExceptionInInitializerError("Veyra's extended entity format changed native attribute offsets.");
            }
        }
    }

    private EntityMidTexCoord() { }

    private static VertexFormatElement registerElement(int index, VertexFormatElement.Usage usage, int count) {
        synchronized (VertexFormatElement.class) {
            for (int id = 7; id < 32; id++) {
                if (VertexFormatElement.byId(id) == null) {
                    return VertexFormatElement.register(id, index, VertexFormatElement.Type.FLOAT, usage, count);
                }
            }
        }
        throw new ExceptionInInitializerError("No free vertex element ID is available for Veyra's entity inputs.");
    }

    /** Mask of the Veyra-only elements, which Minecraft's vertex consumers never write. */
    public static int extensionMask() {
        int mask = ELEMENT.mask();
        for (VertexFormatElement element : TANGENT_ELEMENTS) mask |= element.mask();
        return mask;
    }

    public static float average(float first, float second, float third, float fourth) {
        return (first + second + third + fourth) * 0.25F;
    }
}
