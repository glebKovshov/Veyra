package dev.veyra.shader;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.veyra.mixin.accessor.PostPassAccessor;
import java.util.List;
import java.util.Map;
import net.minecraft.client.renderer.PostPass;
import net.minecraft.client.renderer.UniformValue;
import net.minecraft.resources.Identifier;
import org.lwjgl.system.MemoryStack;

final class VeyraPostUniforms {
    private VeyraPostUniforms() { }
    static PostPass create(RenderPipeline pipeline, Identifier target, Map<String, List<UniformValue>> values,
                           List<PostPass.Input> inputs, List<String> names) {
        return new PostPass(pipeline, target, values, inputs);
    }
    static void makeDynamic(PostPass pass, String block, List<UniformValue> values) {
        var uniforms = ((PostPassAccessor) pass).veyra$getCustomUniforms();
        GpuBuffer previous = uniforms.get(block);
        if (previous == null) throw new IllegalStateException("The shader pass did not create its custom uniform buffer.");
        Std140SizeCalculator size = new Std140SizeCalculator();
        values.forEach(value -> value.addSize(size));
        try (MemoryStack stack = MemoryStack.stackPush()) {
            Std140Builder builder = Std140Builder.onStack(stack, size.get());
            values.forEach(value -> value.writeTo(builder));
            GpuBuffer dynamic = RenderSystem.getDevice().createBuffer(() -> "Veyra shader pack uniforms",
                    GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST, builder.get());
            uniforms.put(block, dynamic);
        }
        previous.close();
    }
    static void update(PostPass pass, String block, List<UniformValue> values) {
        GpuBuffer buffer = ((PostPassAccessor) pass).veyra$getCustomUniforms().get(block);
        if (buffer == null || buffer.isClosed()) throw new IllegalStateException("The shader pass custom uniform buffer is unavailable.");
        Std140SizeCalculator size = new Std140SizeCalculator();
        values.forEach(value -> value.addSize(size));
        try (MemoryStack stack = MemoryStack.stackPush()) {
            Std140Builder builder = Std140Builder.onStack(stack, size.get());
            values.forEach(value -> value.writeTo(builder));
            RenderSystem.getDevice().createCommandEncoder().writeToBuffer(buffer.slice(), builder.get());
        }
    }
    static void close(PostPass pass) { pass.close(); }
}
