package dev.veyra.shader;

import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import net.vulkanmod.vulkan.shader.Uniforms;
import org.junit.jupiter.api.Test;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class CustomUniformBuffersTest {
    @Test
    void restoresOverlappingBooleanAliasesInReverseRegistrationOrder() {
        String name = "veyraAliasSwitchTest";
        String alias = "veyra_" + name;
        java.util.function.Supplier<Integer> original = () -> 42;
        Uniforms.vec1i_uniformMap.put(alias, original);
        try {
            CustomUniforms uniforms = CustomUniforms.parse("uniform.bool." + name + "=true\nuniform.int." + alias + "=7");
            uniforms.evaluate(input -> null, .05);
            CustomUniforms.activate(uniforms);
            VeyraRenderer.registerCustomUniforms();
            assertEquals(7, Uniforms.vec1i_uniformMap.get(alias).get());
            CustomUniforms.activate(CustomUniforms.EMPTY);
            VeyraRenderer.registerCustomUniforms();
            assertSame(original, Uniforms.vec1i_uniformMap.get(alias));
            assertFalse(Uniforms.vec1i_uniformMap.containsKey(name));
        } finally {
            CustomUniforms.activate(CustomUniforms.EMPTY);
            VeyraRenderer.registerCustomUniforms();
            Uniforms.vec1i_uniformMap.remove(alias);
        }
    }

    @Test
    void geometrySuppliersFollowPackSwitchesAndRestorePreviousRegistrations() {
        String name = "veyraVectorSwitchTest";
        java.util.function.Supplier<Float> original = () -> 42F;
        Uniforms.vec1f_uniformMap.put(name, original);
        try {
            CustomUniforms first = CustomUniforms.parse("uniform.float." + name + "=3");
            first.evaluate(input -> null, .05);
            CustomUniforms.activate(first);
            VeyraRenderer.registerCustomUniforms();
            assertEquals(3F, Uniforms.vec1f_uniformMap.get(name).get());
            CustomUniforms second = CustomUniforms.parse("uniform.vec3." + name + "=vec3(1, 2, 3)");
            second.evaluate(input -> null, .05);
            CustomUniforms.activate(second);
            VeyraRenderer.registerCustomUniforms();
            assertSame(original, Uniforms.vec1f_uniformMap.get(name));
            var supplier = Uniforms.vec3f_uniformMap.get(name);
            var buffer = supplier.get();
            assertEquals(1F, buffer.getFloat(0));
            assertEquals(3F, buffer.getFloat(8));
            CustomUniforms.activate(CustomUniforms.EMPTY);
            VeyraRenderer.registerCustomUniforms();
            assertFalse(Uniforms.vec3f_uniformMap.containsKey(name));
            assertSame(original, Uniforms.vec1f_uniformMap.get(name));
            assertEquals(0F, supplier.get().getFloat(8));
        } finally {
            CustomUniforms.activate(CustomUniforms.EMPTY);
            VeyraRenderer.registerCustomUniforms();
            Uniforms.vec1f_uniformMap.remove(name);
        }
    }

    @Test
    void postUniformsPackVec2Vec3Vec4AndAdjacentScalars() {
        CustomUniforms uniforms = CustomUniforms.parse("""
                uniform.vec2.pair=vec2(1, 2)
                uniform.float.first=3
                uniform.vec3.triple=vec3(4, 5, 6)
                uniform.int.second=7
                uniform.vec4.quad=vec4(8, 9, 10, 11)
                """);
        uniforms.evaluate(input -> null, .05);
        CustomUniforms.activate(uniforms);
        try {
            var values = List.of("pair", "first", "triple", "second", "quad").stream()
                    .map(VeyraRenderer::customUniformValue).toList();
            Std140SizeCalculator size = new Std140SizeCalculator();
            values.forEach(value -> value.addSize(size));
            assertEquals(48, size.get());
            var builder = Std140Builder.intoBuffer(ByteBuffer.allocateDirect(size.get()).order(ByteOrder.nativeOrder()));
            values.forEach(value -> value.writeTo(builder));
            ByteBuffer data = builder.get().order(ByteOrder.nativeOrder());
            assertEquals(1F, data.getFloat(0));
            assertEquals(2F, data.getFloat(4));
            assertEquals(3F, data.getFloat(8));
            assertEquals(4F, data.getFloat(16));
            assertEquals(6F, data.getFloat(24));
            assertEquals(7, data.getInt(28));
            assertEquals(8F, data.getFloat(32));
            assertEquals(11F, data.getFloat(44));
        } finally { CustomUniforms.activate(CustomUniforms.EMPTY); }
    }
}

