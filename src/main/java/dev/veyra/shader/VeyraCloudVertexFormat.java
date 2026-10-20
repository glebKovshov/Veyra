package dev.veyra.shader;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import dev.veyra.mixin.accessor.BufferBuilderAccessor;
import org.lwjgl.system.MemoryUtil;

/** Adds cloud-texture coordinates and face normals when a pack's cloud program needs them. */
public final class VeyraCloudVertexFormat {
    private static final int CLOUD_CELL_SIZE = 12;
    private static final int UV_OFFSET;
    private static final int NORMAL_OFFSET;

    public static final VertexFormat VERTEX_FORMAT = VertexFormat.builder()
            .add("Position", VertexFormatElement.POSITION)
            .add("Color", VertexFormatElement.COLOR)
            .add("UV0", VertexFormatElement.UV0)
            .add("Normal", VertexFormatElement.NORMAL)
            .padding(1)
            .build();

    private static final ThreadLocal<BuildState> CURRENT_BUILD = new ThreadLocal<>();

    static {
        if (!VERTEX_FORMAT.getElements().containsAll(DefaultVertexFormat.POSITION_COLOR.getElements())
                || VERTEX_FORMAT.getVertexSize() <= DefaultVertexFormat.POSITION_COLOR.getVertexSize()) {
            throw new ExceptionInInitializerError("Veyra cloud vertex format must extend POSITION_COLOR.");
        }
        UV_OFFSET = VERTEX_FORMAT.getOffset(VertexFormatElement.UV0);
        NORMAL_OFFSET = VERTEX_FORMAT.getOffset(VertexFormatElement.NORMAL);
    }

    private VeyraCloudVertexFormat() {
    }

    public static void beginBuild(boolean enabled, int centerX, int centerZ, int textureWidth) {
        if (!enabled) {
            CURRENT_BUILD.remove();
            return;
        }
        if (textureWidth < 1) {
            throw new IllegalArgumentException("Cloud texture width must be positive.");
        }
        CURRENT_BUILD.set(new BuildState(centerX, centerZ, textureWidth));
    }

    public static void endBuild() {
        CURRENT_BUILD.remove();
    }

    /** Called after VulkanMod writes each position/color pair into the active cloud buffer. */
    public static void append(BufferBuilder builder, float x, float y, float z) {
        BuildState state = CURRENT_BUILD.get();
        if (state == null) return;

        int corner = state.vertexCount & 3;
        state.vertexCount++;
        long pointer = ((BufferBuilderAccessor) builder).veyra$getVertexPointer();
        state.vertexPointers[corner] = pointer;
        // Mark both appended attributes as written before BufferBuilder starts the next vertex.
        builder.setUv(0.0F, 0.0F);
        builder.setNormal(0.0F, 1.0F, 0.0F);
        state.x[corner] = x;
        state.y[corner] = y;
        state.z[corner] = z;
        if (corner != 3) return;

        float firstX = state.x[1] - state.x[0];
        float firstY = state.y[1] - state.y[0];
        float firstZ = state.z[1] - state.z[0];
        float secondX = state.x[2] - state.x[0];
        float secondY = state.y[2] - state.y[0];
        float secondZ = state.z[2] - state.z[0];
        float normalX = firstY * secondZ - firstZ * secondY;
        float normalY = firstZ * secondX - firstX * secondZ;
        float normalZ = firstX * secondY - firstY * secondX;
        float length = (float) Math.sqrt(normalX * normalX + normalY * normalY + normalZ * normalZ);
        if (length == 0.0F) {
            throw new IllegalStateException("VulkanMod emitted a degenerate cloud quad.");
        }
        normalX /= length;
        normalY /= length;
        normalZ /= length;

        float tileX = min(state.x);
        float tileZ = min(state.z);
        if (normalX > 0.5F) tileX = state.x[0] - CLOUD_CELL_SIZE;
        else if (normalX < -0.5F) tileX = state.x[0];
        if (normalZ > 0.5F) tileZ = state.z[0] - CLOUD_CELL_SIZE;
        else if (normalZ < -0.5F) tileZ = state.z[0];

        int pixelX = state.centerX + Math.round(tileX / CLOUD_CELL_SIZE);
        int pixelZ = state.centerZ + Math.round(tileZ / CLOUD_CELL_SIZE);
        float u = (Math.floorMod(pixelX, state.textureWidth) + 0.5F) / state.textureWidth;
        float v = (Math.floorMod(pixelZ, state.textureWidth) + 0.5F) / state.textureWidth;
        byte nx = normal(normalX);
        byte ny = normal(normalY);
        byte nz = normal(normalZ);

        for (long vertexPointer : state.vertexPointers) {
            MemoryUtil.memPutFloat(vertexPointer + UV_OFFSET, u);
            MemoryUtil.memPutFloat(vertexPointer + UV_OFFSET + Float.BYTES, v);
            MemoryUtil.memPutByte(vertexPointer + NORMAL_OFFSET, nx);
            MemoryUtil.memPutByte(vertexPointer + NORMAL_OFFSET + 1, ny);
            MemoryUtil.memPutByte(vertexPointer + NORMAL_OFFSET + 2, nz);
        }
    }

    private static float min(float[] values) {
        return Math.min(Math.min(values[0], values[1]), Math.min(values[2], values[3]));
    }

    private static byte normal(float component) {
        return (byte) Math.round(Math.max(-1.0F, Math.min(1.0F, component)) * 127.0F);
    }

    private static final class BuildState {
        private final int centerX;
        private final int centerZ;
        private final int textureWidth;
        private final long[] vertexPointers = new long[4];
        private final float[] x = new float[4];
        private final float[] y = new float[4];
        private final float[] z = new float[4];
        private int vertexCount;

        private BuildState(int centerX, int centerZ, int textureWidth) {
            this.centerX = centerX;
            this.centerZ = centerZ;
            this.textureWidth = textureWidth;
        }
    }
}
