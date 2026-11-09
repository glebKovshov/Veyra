package dev.veyra.shader;

import com.mojang.blaze3d.framegraph.FrameGraphBuilder;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.resource.ResourceHandle;
import com.mojang.blaze3d.systems.RenderPass;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.client.renderer.PostPass;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;
import org.lwjgl.system.MemoryStack;

final class VeyraPostUniforms {
    private VeyraPostUniforms() { }
    static PostPass create(RenderPipeline pipeline, ResourceLocation target, Map<String, List<LegacyUniformValue>> values,
                           List<PostPass.Input> inputs, List<String> names) {
        if (values.size() > 1) throw new IllegalArgumentException("Legacy post passes use one Veyra parameter block");
        var pass = new NamedPostPass(pipeline, target, values.isEmpty() ? null : values.keySet().iterator().next(),
                names, values.isEmpty() ? List.of() : values.values().iterator().next());
        inputs.forEach(pass::addInput);
        return pass;
    }
    static void makeDynamic(PostPass pass, String block, List<LegacyUniformValue> values) { update(pass, block, values); }
    static void update(PostPass pass, String block, List<LegacyUniformValue> values) {
        var named = (NamedPostPass) pass;
        if (named.closed || !block.equals(named.block)) throw new IllegalStateException("The shader pass parameters are unavailable");
        named.values = pack(named.names, values);
    }
    static void close(PostPass pass) {
        var named = (NamedPostPass) pass;
        named.closed = true;
        named.values = List.of();
    }

    static Consumer<RenderPass> bindings(PostPass pass) {
        var named = (NamedPostPass) pass;
        if (named.closed) throw new IllegalStateException("Post pass is closed");
        List<PackedValue> snapshot = named.values;
        return renderPass -> snapshot.forEach(value -> value.apply(renderPass));
    }

    private static List<PackedValue> pack(List<String> names, List<LegacyUniformValue> values) {
        if (names.size() != values.size()) throw new IllegalArgumentException("Shader parameter name/value count differs");
        if (new java.util.HashSet<>(names).size() != names.size()) throw new IllegalArgumentException("Duplicate shader parameter name");
        var packed = new ArrayList<PackedValue>(values.size());
        try (var stack = MemoryStack.stackPush()) {
            for (int index = 0; index < values.size(); index++) {
                var value = values.get(index);
                var size = new LegacyStd140SizeCalculator();
                value.addSize(size);
                var writer = LegacyStd140Builder.onStack(stack, size.get());
                value.writeTo(writer);
                var bytes = writer.get();
                String name = names.get(index);
                if (value.type() == LegacyUniformValue.Type.INT || value.type() == LegacyUniformValue.Type.IVEC3
                        || value instanceof ShaderPackIVec2Uniform) {
                    // The old CPU uniform API has IVEC3 but no IVEC2; its spare word stays outside the shader field.
                    int[] data = new int[value instanceof ShaderPackIVec2Uniform ? 3 : bytes.remaining() / 4];
                    for (int word = 0; word < bytes.remaining() / 4; word++) data[word] = bytes.getInt(word * 4);
                    packed.add(new PackedValue(name, data, null, null));
                } else {
                    float[] data = new float[bytes.remaining() / 4];
                    for (int word = 0; word < data.length; word++) data[word] = bytes.getFloat(word * 4);
                    packed.add(value.type() == LegacyUniformValue.Type.MATRIX4X4
                            ? new PackedValue(name, null, null, new Matrix4f().set(data))
                            : new PackedValue(name, null, data, null));
                }
            }
        }
        return List.copyOf(packed);
    }

    private record PackedValue(String name, int[] integers, float[] floats, Matrix4f matrix) {
        void apply(RenderPass pass) {
            if (integers != null) pass.setUniform(name, integers);
            else if (matrix != null) pass.setUniform(name, matrix);
            else pass.setUniform(name, floats);
        }
    }
    private static final class NamedPostPass extends PostPass {
        private final String block;
        private final List<String> names;
        private List<PackedValue> values;
        private boolean closed;
        NamedPostPass(RenderPipeline pipeline, ResourceLocation target, String block,
                      List<String> names, List<LegacyUniformValue> values) {
            super(pipeline, target, List.of());
            this.block = block;
            this.names = List.copyOf(names);
            this.values = pack(names, values);
        }
        @Override public void addToFrame(FrameGraphBuilder graph,
                Map<ResourceLocation, ResourceHandle<RenderTarget>> targets, Matrix4f projection,
                Consumer<RenderPass> callback) {
            var snapshot = bindings(this);
            super.addToFrame(graph, targets, projection, pass -> {
                callback.accept(pass);
                snapshot.accept(pass);
            });
        }
    }
}
