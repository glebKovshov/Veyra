package dev.veyra.shader;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;
import org.joml.*;

public final class LegacyStd140Check {
    public static void run() {
        List<LegacyUniformValue> values = List.of(new LegacyUniformValue.FloatUniform(1.25f),
                new LegacyUniformValue.Vec2Uniform(new Vector2f(2, 3)),
                new ShaderPackIVec2Uniform(new Vector2i(0x12345678, -9)),
                new ShaderPackVec3Uniform(new Vector3f(4, 5, 6)), new LegacyUniformValue.FloatUniform(7),
                new ShaderPackIVec3Uniform(new Vector3i(-1, 0x7fffffff, 12)), new LegacyUniformValue.IntUniform(-17),
                new LegacyUniformValue.Vec4Uniform(new Vector4f(8, 9, 10, 11)),
                new LegacyUniformValue.Matrix4x4Uniform(new Matrix4f().translation(21, 22, 23)));
        var size = new LegacyStd140SizeCalculator();
        values.forEach(v -> v.addSize(size));
        assert size.get() == 144;
        ByteBuffer storage = ByteBuffer.allocate(152).order(ByteOrder.nativeOrder());
        java.util.Arrays.fill(storage.array(), (byte) 0x7f);
        storage.position(4).limit(148);
        var builder = LegacyStd140Builder.intoBuffer(storage);
        values.forEach(v -> v.writeTo(builder));
        ByteBuffer data = builder.get();
        assert data.remaining() == 144 && builder.get().remaining() == 144;
        assert data.getFloat(0) == 1.25f && data.getFloat(8) == 2 && data.getFloat(12) == 3;
        assert data.getInt(16) == 0x12345678 && data.getInt(20) == -9;
        assert data.getFloat(32) == 4 && data.getFloat(40) == 6 && data.getFloat(44) == 7;
        assert data.getInt(48) == -1 && data.getInt(52) == Integer.MAX_VALUE && data.getInt(60) == -17;
        assert data.getFloat(64) == 8 && data.getFloat(76) == 11;
        assert data.getFloat(80) == 1 && data.getFloat(100) == 1 && data.getFloat(120) == 1;
        assert data.getFloat(128) == 21 && data.getFloat(132) == 22 && data.getFloat(136) == 23 && data.getFloat(140) == 1;
        for (int i : new int[]{4, 5, 6, 7, 24, 25, 26, 27, 28, 29, 30, 31}) assert data.get(i) == 0;
        assert storage.get(0) == 0x7f && storage.array()[148] == 0x7f;
        var matrixWriter = LegacyStd140Builder.intoBuffer(ByteBuffer.allocate(64).order(ByteOrder.nativeOrder()));
        matrixWriter.putMat4f(new Matrix4f().set(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16));
        var matrixBytes = matrixWriter.get();
        for (int i = 0; i < 16; i++) assert matrixBytes.getFloat(i * 4) == i + 1 : "Matrix was transposed";
        try { builder.putInt(1); throw new AssertionError("Buffer bounds ignored"); }
        catch (java.nio.BufferOverflowException expected) { }
        for (int alignment : new int[]{0, -1, 3}) {
            try { new LegacyStd140SizeCalculator().align(alignment); throw new AssertionError("Invalid alignment"); }
            catch (IllegalArgumentException expected) { }
        }
        try { LegacyStd140SizeCalculator.aligned(Integer.MAX_VALUE, 16); throw new AssertionError("Overflow accepted"); }
        catch (ArithmeticException expected) { }
        try (var stack = org.lwjgl.system.MemoryStack.stackPush()) {
            var onStack = LegacyStd140Builder.onStack(stack, 4);
            onStack.putInt(0x76543210);
            assert onStack.get().getInt(0) == 0x76543210;
        }
        System.out.println("Legacy std140 scalar/vector/matrix layout checks passed");
    }
}
