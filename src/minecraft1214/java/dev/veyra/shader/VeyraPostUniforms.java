package dev.veyra.shader;

import com.mojang.blaze3d.framegraph.FramePass;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.resource.ResourceHandle;
import dev.veyra.compat.LegacyDraws;
import dev.veyra.compat.RenderPass;
import dev.veyra.compat.RenderPipeline;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.client.renderer.CompiledShaderProgram;
import net.minecraft.client.renderer.PostPass;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;
import org.lwjgl.system.MemoryStack;

/**
 * Minecraft 1.21.4 post passes take float uniforms from the chain config only. Veyra's values are
 * written by an input that binds first, which runs at draw time like the later pass callback. The
 * same input applies the pipeline's blend, depth and write state, which this version takes from
 * global render state rather than from the pipeline.
 */
final class VeyraPostUniforms {
    private VeyraPostUniforms() { }

    static PostPass create(RenderPipeline pipeline, ResourceLocation target, Map<String, List<LegacyUniformValue>> values,
                           List<PostPass.Input> inputs, List<String> names) {
        if (values.size() > 1) throw new IllegalArgumentException("Legacy post passes use one Veyra parameter block");
        if (pipeline.getProgram() == null) throw new IllegalStateException("Post pipeline is not compiled: " + pipeline);
        var pass = new NamedPostPass(pipeline, target, values.isEmpty() ? null : values.keySet().iterator().next(),
                names, values.isEmpty() ? List.of() : values.values().iterator().next());
        inputs.forEach(input -> pass.addInput(VeyraPostInput.adapt(input)));
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
        private final RenderPipeline pipeline;
        private final String block;
        private final List<String> names;
        private volatile List<PackedValue> values;
        private boolean closed;

        NamedPostPass(RenderPipeline pipeline, ResourceLocation target, String block,
                      List<String> names, List<LegacyUniformValue> values) {
            super(pipeline.getLocation().toString(), pipeline.getProgram().program(), target, List.of());
            this.block = block;
            this.names = List.copyOf(names);
            this.values = pack(names, values);
            this.pipeline = pipeline;
            addInput(new UniformInput(this));
        }
    }

    // Applies the values current when the frame was recorded, as the later per-frame callback does.
    private static final class UniformInput implements PostPass.Input {
        private final NamedPostPass owner;
        private List<PackedValue> recorded = List.of();

        private UniformInput(NamedPostPass owner) { this.owner = owner; }

        @Override public void addToPass(FramePass pass, Map<ResourceLocation, ResourceHandle<RenderTarget>> targets) {
            recorded = owner.values;
        }

        @Override public void bindTo(CompiledShaderProgram program, Map<ResourceLocation, ResourceHandle<RenderTarget>> targets) {
            RenderPass pass = LegacyDraws.pass(program);
            recorded.forEach(value -> value.apply(pass));
            LegacyDraws.applyPipelineState(owner.pipeline);
        }

        @Override public void cleanup(Map<ResourceLocation, ResourceHandle<RenderTarget>> targets) {
            LegacyDraws.restorePipelineState();
        }
    }
}
