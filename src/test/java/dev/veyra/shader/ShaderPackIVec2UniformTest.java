package dev.veyra.shader;

import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import org.joml.Vector2i;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ShaderPackIVec2UniformTest {
    @Test
    void writesIntegerVectorWithStd140Alignment() {
        ShaderPackIVec2Uniform uniform = new ShaderPackIVec2Uniform(new Vector2i(12, 34));
        Std140SizeCalculator size = new Std140SizeCalculator();
        size.putInt();
        uniform.addSize(size);
        assertEquals(16, size.get());

        Std140Builder builder = Std140Builder.intoBuffer(ByteBuffer.allocate(size.get()));
        builder.putInt(7);
        uniform.writeTo(builder);
        ByteBuffer data = builder.get();

        assertEquals(7, data.getInt(0));
        assertEquals(12, data.getInt(8));
        assertEquals(34, data.getInt(12));
    }
}
