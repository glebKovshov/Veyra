package dev.veyra.shader;

import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import net.minecraft.client.renderer.UniformValue;
import org.joml.Vector2ic;

import java.util.Objects;

/** Writes an integer vec2 into a post-pass custom uniform buffer. */
final class ShaderPackIVec2Uniform implements UniformValue {
    private final Vector2ic value;

    ShaderPackIVec2Uniform(Vector2ic value) {
        this.value = Objects.requireNonNull(value, "value");
    }

    @Override
    public void writeTo(Std140Builder builder) {
        builder.putIVec2(value);
    }

    @Override
    public void addSize(Std140SizeCalculator calculator) {
        calculator.putIVec2();
    }

    @Override
    public UniformValue.Type type() {
        // Minecraft 1.21.11 has no IVEC2 enum value. PostPass consumes this
        // runtime value only through writeTo/addSize, both of which preserve
        // the integer vector's exact std140 representation.
        return UniformValue.Type.VEC2;
    }
}
