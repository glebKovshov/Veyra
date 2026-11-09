package dev.veyra.shader;

import java.nio.ByteBuffer;
import org.joml.Matrix4fc;
import org.joml.Vector2fc;
import org.joml.Vector2ic;
import org.joml.Vector4fc;
import org.lwjgl.system.MemoryStack;

/** Writes the std140 scalar/vector/matrix forms used by Veyra's shader-pack block. */
public final class LegacyStd140Builder {
    private final ByteBuffer buffer;
    private final int start;
    private LegacyStd140Builder(ByteBuffer buffer) { this.buffer = buffer; start = buffer.position(); }
    public static LegacyStd140Builder intoBuffer(ByteBuffer buffer) { return new LegacyStd140Builder(buffer); }
    public static LegacyStd140Builder onStack(MemoryStack stack, int size) { return intoBuffer(stack.calloc(size)); }
    public ByteBuffer get() {
        return buffer.duplicate().position(start).limit(buffer.position()).slice().order(buffer.order());
    }
    public LegacyStd140Builder align(int alignment) {
        int position = Math.addExact(start, LegacyStd140SizeCalculator.aligned(buffer.position() - start, alignment));
        if (position > buffer.limit()) throw new java.nio.BufferOverflowException();
        while (buffer.position() < position) buffer.put((byte) 0);
        return this;
    }
    public LegacyStd140Builder putFloat(float value) { align(4); buffer.putFloat(value); return this; }
    public LegacyStd140Builder putInt(int value) { align(4); buffer.putInt(value); return this; }
    public LegacyStd140Builder putVec2(Vector2fc value) { return align(8).putFloat(value.x()).putFloat(value.y()); }
    public LegacyStd140Builder putIVec2(Vector2ic value) { return align(8).putInt(value.x()).putInt(value.y()); }
    public LegacyStd140Builder putVec4(Vector4fc value) {
        return align(16).putFloat(value.x()).putFloat(value.y()).putFloat(value.z()).putFloat(value.w());
    }
    public LegacyStd140Builder putMat4f(Matrix4fc value) {
        align(16);
        if (buffer.remaining() < 64) throw new java.nio.BufferOverflowException();
        for (int column = 0; column < 4; column++) {
            for (int row = 0; row < 4; row++) buffer.putFloat(value.get(column, row));
        }
        return this;
    }
}
