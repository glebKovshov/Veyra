package dev.veyra.shader;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import org.joml.*;

public final class LegacyPostUniformsCheck {
    public static void run() throws Exception {
        var id = ResourceLocation.fromNamespaceAndPath("veyra", "named-uniform-check");
        var pipeline = RenderPipeline.builder().withLocation(id).withVertexShader(id).withFragmentShader(id)
                .withVertexFormat(DefaultVertexFormat.POSITION, VertexFormat.Mode.TRIANGLES).build();
        var sky = new Vector3f(1, 2, 3);
        var matrix = new Matrix4f().translate(4, 5, 6).scale(2);
        List<String> names = List.of("phase", "entity", "atlas", "sky", "camera", "jitter", "color", "matrix");
        List<LegacyUniformValue> values = List.of(new LegacyUniformValue.FloatUniform(0.25F),
                new LegacyUniformValue.IntUniform(-123456789), new ShaderPackIVec2Uniform(new Vector2i(0x7fc01234, Integer.MIN_VALUE)),
                new ShaderPackVec3Uniform(sky), new ShaderPackIVec3Uniform(new Vector3i(-1, 0x7fc01234, Integer.MIN_VALUE)),
                new LegacyUniformValue.Vec2Uniform(new Vector2f(2.5F, -4)),
                new LegacyUniformValue.Vec4Uniform(new Vector4f(1, 2, 3, 4)), new LegacyUniformValue.Matrix4x4Uniform(matrix));
        var pass = VeyraPostUniforms.create(pipeline, id, Map.of("VeyraParameters", values), List.of(), names);
        var queued = VeyraPostUniforms.bindings(pass);
        sky.set(9); matrix.identity();
        Map<String, Object> writes = new java.util.HashMap<>();
        RenderPass nativePass = (RenderPass) Proxy.newProxyInstance(RenderPass.class.getClassLoader(),
                new Class<?>[]{RenderPass.class}, (proxy, method, args) -> {
                    assert method.getName().equals("setUniform");
                    Object data = args[1];
                    writes.put((String) args[0], data instanceof int[] integers ? integers.clone()
                            : data instanceof float[] floats ? floats.clone() : new Matrix4f((Matrix4f) data));
                    return null;
                });
        queued.accept(nativePass);
        assert writes.size() == names.size();
        assert Arrays.equals((int[]) writes.get("entity"), new int[]{-123456789});
        assert Arrays.equals((int[]) writes.get("atlas"), new int[]{0x7fc01234, Integer.MIN_VALUE, 0});
        try (var nativeUniform = new com.mojang.blaze3d.opengl.Uniform("atlas", com.mojang.blaze3d.shaders.UniformType.IVEC3)) {
            nativeUniform.set((int[]) writes.get("atlas"));
            var storage = com.mojang.blaze3d.opengl.Uniform.class.getDeclaredField("intValues");
            storage.setAccessible(true);
            var integers = (java.nio.IntBuffer) storage.get(nativeUniform);
            assert integers.get(0) == 0x7fc01234 && integers.get(1) == Integer.MIN_VALUE && integers.get(2) == 0;
        }
        assert Arrays.equals((int[]) writes.get("camera"), new int[]{-1, 0x7fc01234, Integer.MIN_VALUE});
        assert Arrays.equals((float[]) writes.get("sky"), new float[]{1, 2, 3});
        assert Arrays.equals((float[]) writes.get("jitter"), new float[]{2.5F, -4});
        assert Arrays.equals((float[]) writes.get("color"), new float[]{1, 2, 3, 4});
        assert ((Matrix4f) writes.get("matrix")).m00() == 2 && ((Matrix4f) writes.get("matrix")).m30() == 4;
        VeyraPostUniforms.makeDynamic(pass, "VeyraParameters", values);
        VeyraPostUniforms.bindings(pass).accept(nativePass);
        assert Arrays.equals((float[]) writes.get("sky"), new float[]{9, 9, 9});
        try { VeyraPostUniforms.update(pass, "VeyraParameters", List.of()); throw new AssertionError("Wrong count accepted"); }
        catch (IllegalArgumentException expected) { }
        try { VeyraPostUniforms.update(pass, "missing", values); throw new AssertionError("Missing block accepted"); }
        catch (IllegalStateException expected) { }
        VeyraPostUniforms.close(pass); VeyraPostUniforms.close(pass);
        queued.accept(nativePass);
        assert Arrays.equals((float[]) writes.get("sky"), new float[]{1, 2, 3}) : "Queued frame lost its values";
        try { VeyraPostUniforms.bindings(pass); throw new AssertionError("Closed pass accepted"); }
        catch (IllegalStateException expected) { }
        try { VeyraPostUniforms.update(pass, "VeyraParameters", values); throw new AssertionError("Closed pass updated"); }
        catch (IllegalStateException expected) { }
        System.out.println("Legacy named post uniforms preserve integer bits, matrix order and queued-frame snapshots");
    }
}
