package dev.veyra.vulkan;

import com.mojang.blaze3d.opengl.Uniform;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.veyra.shader.LegacyGlslSource;
import java.util.List;
import java.util.Map;
import net.vulkanmod.render.engine.EGlProgram;
import net.vulkanmod.vulkan.util.MappedBuffer;
import org.joml.Matrix4f;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import static org.lwjgl.vulkan.VK10.*;

final class LegacyUniformMetadataCheck {
    static void run() throws Exception {
        if (!RenderSystem.isOnRenderThread()) RenderSystem.initRenderThread();
        var id = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("veyra", "uniform-copy-test");
        var builder = RenderPipeline.builder().withLocation(id).withVertexShader(id).withFragmentShader(id)
                .withVertexFormat(com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION,
                        com.mojang.blaze3d.vertex.VertexFormat.Mode.TRIANGLES);
        var original = new RenderPipeline.UniformDescription("original", UniformType.VEC4);
        VeyraUniformBlocks.copyTo(builder, original);
        assert VeyraUniformBlocks.declare(builder, "VeyraParameters") == builder;
        assert builder.build().getUniforms().equals(List.of(original)) : "Deferred block metadata changed native uniforms";
        String source = LegacyGlslSource.prepare("""
                layout(std140) uniform VeyraParameters {
                    mat4 ProjMat;
                    ivec2 atlas;
                    int entity;
                    vec3 sky;
                    float phase;
                } parameters;
                """);
        var types = LegacyGlslSource.uniformTypes(source, "uniform float phase;");
        var fields = List.of(info("mat4", "ProjMat"), info("vec2", "atlas"), info("int", "entity"),
                info("vec3", "sky"), info("float", "phase"));
        var block = VeyraUniformBlocks.create("metadata-check", 0, VK_SHADER_STAGE_ALL_GRAPHICS, fields);
        var program = new EGlProgram(1, "metadata-check");
        var invalid = new EGlProgram(2, "invalid-metadata-check");
        try {
            program.setupUniforms(List.of(new RenderPipeline.UniformDescription("ProjMat", UniformType.MATRIX4X4)), List.of("Sampler0"));
            var projection = program.getUniform("ProjMat");
            LegacyUniformMetadata.install(program, block, types);
            assert program.getUniforms().size() == 5 && program.getSamplers().equals(List.of("Sampler0"));
            assert program.PROJECTION_MATRIX == projection;
            LegacyUniformMetadata.install(program, block, types);
            assert program.getUniforms().size() == 5 && program.getUniform("ProjMat") == projection;
            assert program.getUniform("atlas").getType() == UniformType.IVEC3;
            projection.set(new Matrix4f().translate(4, 5, 6));
            program.getUniform("atlas").set(new int[]{0x7fc01234, Integer.MIN_VALUE, 0});
            program.getUniform("entity").set(-123456789);
            program.getUniform("sky").set(new float[]{1, 2, 3});
            program.getUniform("phase").set(0.25F);
            for (var field : block.getUniforms()) {
                var uniform = program.getUniform(field.getName());
                var storage = Uniform.class.getDeclaredField(uniform.getType().isIntStorage() ? "intValues" : "floatValues");
                storage.setAccessible(true);
                Object data = storage.get(uniform);
                var bytes = data instanceof java.nio.IntBuffer integers ? MemoryUtil.memByteBuffer(integers)
                        : MemoryUtil.memByteBuffer((java.nio.FloatBuffer) data);
                var mapped = MappedBuffer.createFromBuffer(bytes);
                field.setSupplier(() -> mapped);
            }
            try (var stack = MemoryStack.stackPush()) {
                assert block.getSize() == 96;
                var bytes = stack.calloc(96);
                block.update(MemoryUtil.memAddress(bytes));
                assert bytes.getFloat(48) == 4 && bytes.getFloat(52) == 5 && bytes.getFloat(56) == 6;
                assert bytes.getInt(64) == 0x7fc01234 && bytes.getInt(68) == Integer.MIN_VALUE;
                assert bytes.getInt(72) == -123456789;
                assert bytes.getFloat(80) == 1 && bytes.getFloat(88) == 3 && bytes.getFloat(92) == 0.25F;
                program.getUniform("atlas").set(new int[]{42, -7, 0});
                block.update(MemoryUtil.memAddress(bytes));
                assert bytes.getInt(64) == 42 && bytes.getInt(68) == -7;
            }
            invalid.setupUniforms(List.of(new RenderPipeline.UniformDescription("entity", UniformType.FLOAT)), List.of());
            try { LegacyUniformMetadata.install(invalid, block, types); throw new AssertionError("Wrong native storage accepted"); }
            catch (IllegalStateException expected) { }
            assert invalid.getUniforms().size() == 1 : "Failed metadata installation partially changed the program";
            try { LegacyUniformMetadata.install(program, block, Map.of()); throw new AssertionError("Missing source types accepted"); }
            catch (IllegalStateException expected) { }
            try { LegacyGlslSource.uniformTypes("uniform float duplicate;", "uniform int duplicate;"); throw new AssertionError("Conflicting stages accepted"); }
            catch (java.io.IOException expected) { }
            String fog = LegacyGlslSource.prepare("""
                    layout(std140) uniform Fog {
                        float FogEnvironmentalStart;
                        float FogEnvironmentalEnd;
                        float FogRenderDistanceStart;
                        float FogRenderDistanceEnd;
                        float FogCloudsEnd;
                    };
                    """);
            assert LegacyGlslSource.uniformTypes(fog).equals(Map.of("FogStart", "float", "FogEnd", "float"));
        } finally {
            program.getUniforms().forEach(Uniform::close);
            invalid.getUniforms().forEach(Uniform::close);
        }
        System.out.println("Legacy EGlProgram metadata, supplier byte copies, integer vectors and fog fields passed");
    }
    private static net.vulkanmod.vulkan.shader.layout.Uniform.Info info(String type, String name) {
        return net.vulkanmod.vulkan.shader.layout.Uniform.createUniformInfo(type, name);
    }
}
