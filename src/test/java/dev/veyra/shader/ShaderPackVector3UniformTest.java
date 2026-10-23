package dev.veyra.shader;

import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import net.minecraft.client.renderer.UniformValue;
import org.joml.Vector3f;
import org.joml.Vector3i;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.util.shaderc.Shaderc.*;

class ShaderPackVector3UniformTest {
    @Test
    void matchesCompiledStd140OffsetsForVector3FollowedByScalars() {
        List<Integer> offsets = compiledOffsets();
        assertEquals(List.of(0, 16, 28, 32, 44), offsets);

        List<UniformValue> values = List.of(new UniformValue.FloatUniform(0.5F),
                new ShaderPackVec3Uniform(new Vector3f(1, 2, 3)), new UniformValue.IntUniform(7),
                new ShaderPackIVec3Uniform(new Vector3i(12, 34, 56)), new UniformValue.FloatUniform(9.5F));
        Std140SizeCalculator size = new Std140SizeCalculator();
        values.forEach(value -> value.addSize(size));
        assertEquals(48, size.get());
        Std140Builder builder = Std140Builder.intoBuffer(ByteBuffer.allocate(size.get()).order(ByteOrder.nativeOrder()));
        values.forEach(value -> value.writeTo(builder));
        ByteBuffer data = builder.get().order(ByteOrder.nativeOrder());
        assertEquals(0.5F, data.getFloat(offsets.get(0)));
        assertEquals(1.0F, data.getFloat(offsets.get(1)));
        assertEquals(3.0F, data.getFloat(offsets.get(1) + 8));
        assertEquals(7, data.getInt(offsets.get(2)));
        assertEquals(12, data.getInt(offsets.get(3)));
        assertEquals(56, data.getInt(offsets.get(3) + 8));
        assertEquals(9.5F, data.getFloat(offsets.get(4)));
        assertEquals(UniformValue.Type.VEC3, values.get(1).type());
        assertEquals(UniformValue.Type.IVEC3, values.get(3).type());
    }

    private static List<Integer> compiledOffsets() {
        long compiler = shaderc_compiler_initialize();
        assertNotEquals(0L, compiler);
        long result = 0;
        try {
            result = shaderc_compile_into_spv(compiler, """
                    #version 450
                    layout(std140, binding = 0) uniform Parameters {
                        float leading;
                        vec3 sunPosition;
                        int frameCounter;
                        ivec3 cameraPositionInt;
                        float trailing;
                    } parameters;
                    void main() {
                        gl_Position = vec4(parameters.sunPosition + vec3(parameters.cameraPositionInt),
                                parameters.leading + float(parameters.frameCounter) + parameters.trailing);
                    }
                    """, shaderc_vertex_shader, "vector3-layout.vsh", "main", 0);
            assertNotEquals(0L, result);
            assertEquals(shaderc_compilation_status_success, shaderc_result_get_compilation_status(result),
                    shaderc_result_get_error_message(result));
            ByteBuffer spirv = shaderc_result_get_bytes(result).order(ByteOrder.LITTLE_ENDIAN);
            var offsets = new TreeMap<Integer, Integer>();
            for (int word = 5; word < spirv.remaining() / 4;) {
                int header = spirv.getInt(word * 4);
                int count = header >>> 16;
                assertTrue(count > 0);
                // OpMemberDecorate(struct, member, Offset, byteOffset).
                if ((header & 0xffff) == 72 && count == 5 && spirv.getInt((word + 3) * 4) == 35) {
                    offsets.put(spirv.getInt((word + 2) * 4), spirv.getInt((word + 4) * 4));
                }
                word += count;
            }
            return List.copyOf(offsets.values());
        } finally {
            if (result != 0) shaderc_result_release(result);
            shaderc_compiler_release(compiler);
        }
    }
}
