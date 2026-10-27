package dev.veyra.shader;

import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import net.vulkanmod.render.vertex.CustomVertexFormat;
import net.vulkanmod.render.vertex.VertexBuilder;

/** VulkanMod's compressed terrain format with Iris midpoint and material inputs appended. */
public final class TerrainMidTexCoord {
    public static final int ATTRIBUTE_OFFSET = 16;
    public static final int MATERIAL_ID_OFFSET = 20;
    public static final int RENDER_TYPE_OFFSET = 24;
    public static final int PACKED_NORMAL_OFFSET = 28;
    public static final int TANGENT_OFFSET = 32;
    public static final int MID_BLOCK_OFFSET = 48;
    public static final int VERTEX_SIZE = 52;

    public static final VertexFormat VERTEX_FORMAT = VertexFormat.builder()
            .add("Position", CustomVertexFormat.ELEMENT_POSITION_INT16)
            .add("UV0", CustomVertexFormat.ELEMENT_UV0_UINT16)
            .add("Color", CustomVertexFormat.ELEMENT_COLOR_UINT)
            .add("MidTexCoord", new VertexFormatElement(3, 1,
                    VertexFormatElement.Type.USHORT, VertexFormatElement.Usage.UV, 2))
            .add("VeyraTerrainMaterialId", new VertexFormatElement(4, 0,
                    VertexFormatElement.Type.INT, VertexFormatElement.Usage.GENERIC, 1))
            .add("VeyraTerrainRenderType", new VertexFormatElement(5, 0,
                    VertexFormatElement.Type.FLOAT, VertexFormatElement.Usage.GENERIC, 1))
            .add("VeyraTerrainPackedNormal", new VertexFormatElement(6, 0,
                    VertexFormatElement.Type.INT, VertexFormatElement.Usage.GENERIC, 1))
            // VulkanMod 0.6.8 only maps generic FLOAT attributes with one component.
            // Keep the tangent as four scalar attributes so its pipeline builder accepts the format.
            .add("VeyraTerrainTangentX", new VertexFormatElement(7, 0,
                    VertexFormatElement.Type.FLOAT, VertexFormatElement.Usage.GENERIC, 1))
            .add("VeyraTerrainTangentY", new VertexFormatElement(8, 0,
                    VertexFormatElement.Type.FLOAT, VertexFormatElement.Usage.GENERIC, 1))
            .add("VeyraTerrainTangentZ", new VertexFormatElement(9, 0,
                    VertexFormatElement.Type.FLOAT, VertexFormatElement.Usage.GENERIC, 1))
            .add("VeyraTerrainTangentHandedness", new VertexFormatElement(10, 0,
                    VertexFormatElement.Type.FLOAT, VertexFormatElement.Usage.GENERIC, 1))
            .add("VeyraTerrainPackedMidBlock", new VertexFormatElement(11, 0,
                    VertexFormatElement.Type.INT, VertexFormatElement.Usage.GENERIC, 1))
            .build();

    private TerrainMidTexCoord() {
    }

    public static float center(float first, float second, float third, float fourth) {
        return (first + second + third + fourth) * 0.25F;
    }

    /** Iris expresses the offset to the original block center in 1/64 block units. */
    public static float midBlockOffset(float blockOrigin, float vertexPosition) {
        return (blockOrigin + 0.5F - vertexPosition) * 64.0F;
    }

    /** Three signed byte offsets and one unsigned emission byte, consumed through one integer attribute. */
    public static int packMidBlock(float x, float y, float z, int emission) {
        return ((byte) x & 255) | (((byte) y & 255) << 8) | (((byte) z & 255) << 16)
                | ((emission & 255) << 24);
    }

    /** Matches VulkanMod's compressed UV encoding; the shader reads the bits as unsigned shorts. */
    public static short pack(float coordinate) {
        return (short) (coordinate * 32768.0F);
    }

    /** Computes Iris's per-quad tangent and handedness from the terrain position, UV, and normal data. */
    public static void computeTangent(float[] x, float[] y, float[] z, float[] u, float[] v,
                                      int[] packedNormals, float[] result) {
        float edge1X = x[1] - x[0], edge1Y = y[1] - y[0], edge1Z = z[1] - z[0];
        float edge2X = x[2] - x[0], edge2Y = y[2] - y[0], edge2Z = z[2] - z[0];
        float deltaU1 = u[1] - u[0], deltaV1 = v[1] - v[0];
        float deltaU2 = u[2] - u[0], deltaV2 = v[2] - v[0];
        float determinant = deltaU1 * deltaV2 - deltaU2 * deltaV1;

        int packedNormal = packedNormals[0] == 0 ? packedQuadNormal(x, y, z) : packedNormals[0];
        float normalX = normalComponent(packedNormal, 0);
        float normalY = normalComponent(packedNormal, 8);
        float normalZ = normalComponent(packedNormal, 16);
        float normalLength = (float) Math.sqrt(normalX * normalX + normalY * normalY + normalZ * normalZ);
        if (normalLength > 1.0e-6F) {
            normalX /= normalLength;
            normalY /= normalLength;
            normalZ /= normalLength;
        }

        float tangentX;
        float tangentY;
        float tangentZ;
        float bitangentX = 0.0F, bitangentY = 0.0F, bitangentZ = 0.0F;
        if (Math.abs(determinant) > 1.0e-8F) {
            float reciprocal = 1.0F / determinant;
            tangentX = (edge1X * deltaV2 - edge2X * deltaV1) * reciprocal;
            tangentY = (edge1Y * deltaV2 - edge2Y * deltaV1) * reciprocal;
            tangentZ = (edge1Z * deltaV2 - edge2Z * deltaV1) * reciprocal;
            bitangentX = (edge2X * deltaU1 - edge1X * deltaU2) * reciprocal;
            bitangentY = (edge2Y * deltaU1 - edge1Y * deltaU2) * reciprocal;
            bitangentZ = (edge2Z * deltaU1 - edge1Z * deltaU2) * reciprocal;
        } else {
            tangentX = edge1X;
            tangentY = edge1Y;
            tangentZ = edge1Z;
        }

        float projection = tangentX * normalX + tangentY * normalY + tangentZ * normalZ;
        tangentX -= normalX * projection;
        tangentY -= normalY * projection;
        tangentZ -= normalZ * projection;
        float tangentLength = (float) Math.sqrt(tangentX * tangentX + tangentY * tangentY + tangentZ * tangentZ);
        if (tangentLength <= 1.0e-6F) {
            if (Math.abs(normalY) < 0.9F) {
                tangentX = normalZ;
                tangentY = 0.0F;
                tangentZ = -normalX;
            } else {
                tangentX = 0.0F;
                tangentY = -normalZ;
                tangentZ = normalY;
            }
            tangentLength = (float) Math.sqrt(tangentX * tangentX + tangentY * tangentY + tangentZ * tangentZ);
        }
        tangentX /= tangentLength;
        tangentY /= tangentLength;
        tangentZ /= tangentLength;

        float crossX = normalY * tangentZ - normalZ * tangentY;
        float crossY = normalZ * tangentX - normalX * tangentZ;
        float crossZ = normalX * tangentY - normalY * tangentX;
        float handedness = crossX * bitangentX + crossY * bitangentY + crossZ * bitangentZ < 0.0F ? -1.0F : 1.0F;
        result[0] = tangentX;
        result[1] = tangentY;
        result[2] = tangentZ;
        result[3] = handedness;
    }

    public static int packedQuadNormal(float[] x, float[] y, float[] z) {
        float ax = x[1] - x[0], ay = y[1] - y[0], az = z[1] - z[0];
        float bx = x[2] - x[0], by = y[2] - y[0], bz = z[2] - z[0];
        float nx = ay * bz - az * by, ny = az * bx - ax * bz, nz = ax * by - ay * bx;
        float length = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (length <= 1.0e-6F) return 127 << 16;
        return (Math.round(nx / length * 127.0F) & 255)
                | (Math.round(ny / length * 127.0F) & 255) << 8
                | (Math.round(nz / length * 127.0F) & 255) << 16;
    }

    private static float normalComponent(int packed, int shift) {
        return ((byte) (packed >>> shift)) / 127.0F;
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
