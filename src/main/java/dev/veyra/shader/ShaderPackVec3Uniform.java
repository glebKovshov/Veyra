package dev.veyra.shader;

import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import net.minecraft.client.renderer.UniformValue;
import org.joml.Vector3fc;

import java.util.Objects;

/** A std140 vec3 whose trailing scalar may use the fourth word. */
final class ShaderPackVec3Uniform implements UniformValue {
    private final Vector3fc value;

    ShaderPackVec3Uniform(Vector3fc value) {
        this.value = Objects.requireNonNull(value, "value");
    }

    @Override public void writeTo(Std140Builder builder) {
        builder.align(16).putFloat(value.x()).putFloat(value.y()).putFloat(value.z());
    }

    @Override public void addSize(Std140SizeCalculator calculator) {
        calculator.align(16).putFloat().putFloat().putFloat();
    }

    @Override public Type type() { return Type.VEC3; }
}
