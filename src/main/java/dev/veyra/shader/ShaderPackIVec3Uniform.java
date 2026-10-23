package dev.veyra.shader;

import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import net.minecraft.client.renderer.UniformValue;
import org.joml.Vector3ic;

import java.util.Objects;

/** A std140 ivec3 whose trailing scalar may use the fourth word. */
final class ShaderPackIVec3Uniform implements UniformValue {
    private final Vector3ic value;

    ShaderPackIVec3Uniform(Vector3ic value) {
        this.value = Objects.requireNonNull(value, "value");
    }

    @Override public void writeTo(Std140Builder builder) {
        builder.align(16).putInt(value.x()).putInt(value.y()).putInt(value.z());
    }

    @Override public void addSize(Std140SizeCalculator calculator) {
        calculator.align(16).putInt().putInt().putInt();
    }

    @Override public Type type() { return Type.IVEC3; }
}
