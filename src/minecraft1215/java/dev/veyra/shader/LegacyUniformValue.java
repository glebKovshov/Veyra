package dev.veyra.shader;

import org.joml.Matrix4fc;
import org.joml.Vector2fc;
import org.joml.Vector4fc;

public interface LegacyUniformValue {
    enum Type { FLOAT, INT, VEC2, VEC3, IVEC3, VEC4, MATRIX4X4 }
    void writeTo(LegacyStd140Builder builder);
    void addSize(LegacyStd140SizeCalculator calculator);
    Type type();

    record FloatUniform(float value) implements LegacyUniformValue {
        public void writeTo(LegacyStd140Builder b) { b.putFloat(value); }
        public void addSize(LegacyStd140SizeCalculator c) { c.putFloat(); }
        public Type type() { return Type.FLOAT; }
    }
    record IntUniform(int value) implements LegacyUniformValue {
        public void writeTo(LegacyStd140Builder b) { b.putInt(value); }
        public void addSize(LegacyStd140SizeCalculator c) { c.putInt(); }
        public Type type() { return Type.INT; }
    }
    record Vec2Uniform(Vector2fc value) implements LegacyUniformValue {
        public void writeTo(LegacyStd140Builder b) { b.putVec2(value); }
        public void addSize(LegacyStd140SizeCalculator c) { c.putVec2(); }
        public Type type() { return Type.VEC2; }
    }
    record Vec4Uniform(Vector4fc value) implements LegacyUniformValue {
        public void writeTo(LegacyStd140Builder b) { b.putVec4(value); }
        public void addSize(LegacyStd140SizeCalculator c) { c.putVec4(); }
        public Type type() { return Type.VEC4; }
    }
    record Matrix4x4Uniform(Matrix4fc value) implements LegacyUniformValue {
        public void writeTo(LegacyStd140Builder b) { b.putMat4f(value); }
        public void addSize(LegacyStd140SizeCalculator c) { c.putMat4f(); }
        public Type type() { return Type.MATRIX4X4; }
    }
}
