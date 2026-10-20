package dev.veyra.shader;

import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import net.vulkanmod.render.vertex.CustomVertexFormat;
import net.vulkanmod.render.vertex.VertexBuilder;

/** VulkanMod's compressed terrain format with Iris' per-quad texture midpoint appended. */
public final class TerrainMidTexCoord {
    public static final int ATTRIBUTE_OFFSET = 16;
    public static final int VERTEX_SIZE = 20;

    public static final VertexFormat VERTEX_FORMAT = VertexFormat.builder()
            .add("Position", CustomVertexFormat.ELEMENT_POSITION_INT16)
            .add("UV0", CustomVertexFormat.ELEMENT_UV0_UINT16)
            .add("Color", CustomVertexFormat.ELEMENT_COLOR_UINT)
            .add("MidTexCoord", new VertexFormatElement(3, 1,
                    VertexFormatElement.Type.USHORT, VertexFormatElement.Usage.UV, 2))
            .build();

    private TerrainMidTexCoord() {
    }

    public static float center(float first, float second, float third, float fourth) {
        return (first + second + third + fourth) * 0.25F;
    }

    /** Matches VulkanMod's compressed UV encoding; the shader reads the bits as unsigned shorts. */
    public static short pack(float coordinate) {
        return (short) (coordinate * 32768.0F);
    }

    /** Uses VulkanMod's existing compressed fields and reserves four bytes for the packed midpoint. */
    public static final class VertexBuilderWithMidpoint implements VertexBuilder {
        private final VertexBuilder compressed = new VertexBuilder.CompressedVertexBuilder();

        @Override
        public void vertex(long pointer, float x, float y, float z, int color,
                           float u, float v, int light, int packedNormal) {
            compressed.vertex(pointer, x, y, z, color, u, v, light, packedNormal);
        }

        @Override
        public void position(long pointer, float x, float y, float z) {
            compressed.position(pointer, x, y, z);
        }

        @Override
        public void color(long pointer, int color) {
            compressed.color(pointer, color);
        }

        @Override
        public void uv(long pointer, float u, float v) {
            compressed.uv(pointer, u, v);
        }

        @Override
        public void light(long pointer, int light) {
            compressed.light(pointer, light);
        }

        @Override
        public void normal(long pointer, int normal) {
            compressed.normal(pointer, normal);
        }

        @Override
        public int getStride() {
            return VERTEX_SIZE;
        }
    }
}
