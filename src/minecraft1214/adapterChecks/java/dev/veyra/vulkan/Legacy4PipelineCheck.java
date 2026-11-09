package dev.veyra.vulkan;

import net.vulkanmod.vulkan.shader.Uniforms;
import net.vulkanmod.vulkan.util.MappedBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import static org.lwjgl.vulkan.VK10.*;

/** Executes the shared adapter against the pinned 1.21.4 native layout classes without creating a device. */
public final class Legacy4PipelineCheck {
    public static void main(String[] args) throws Exception {
        checkShaders();
        var terrain = LegacyPipelineConfigs.TERRAIN_UB0;
        assert terrain.binding == 0 && !terrain.fields.isEmpty();
        assert LegacyShaderLoadUtil.loadShader(LegacyShaderLoadUtil.resolveShaderPath("basic/terrain"), "terrain.vsh")
                .contains("getVertexPosition");
        try (var stack = MemoryStack.stackPush()) {
            var matrix = MappedBuffer.createFromBuffer(stack.calloc(64));
            for (int index = 0; index < 16; index++) matrix.putFloat(index * 4, index + 0.25F);
            Uniforms.mat4f_uniformMap.put("portMatrix", () -> matrix);
            Uniforms.vec1f_uniformMap.put("portScalar", () -> 9.5F);
            try {
                var block = LegacyPipelineConfig.UB.builder(4, VK_SHADER_STAGE_ALL_GRAPHICS)
                        .addUniform("mat4", "portMatrix").addUniform("float", "portScalar").build();
                var builder = new LegacyPipelineBuilder(null, "legacy4-check");
                builder.applyConfig(LegacyPipelineConfig.builder().addUB(block)
                        .addImageDescriptor(7, "sampler2D", "sampled", 2)
                        .addImageDescriptor(9, "image2D", "storage", 3).build());
                var ubo = builder.getUBOs().getFirst();
                assert ubo.getBinding() == 4 && ubo.getSize() == 68 && ubo.getUniformBuffer() == null;
                var bytes = stack.calloc(ubo.getSize());
                ubo.update(MemoryUtil.memAddress(bytes));
                for (int index = 0; index < 16; index++) assert bytes.getFloat(index * 4) == index + 0.25F;
                assert bytes.getFloat(64) == 9.5F;
                matrix.putFloat(16, 37.5F);
                ubo.update(MemoryUtil.memAddress(bytes));
                assert bytes.getFloat(16) == 37.5F;
                var images = builder.getImageDescriptors();
                assert images.size() == 2 && images.getFirst().getBinding() == 7;
                assert images.getFirst().imageIdx == 2 && !images.getFirst().isStorageImage;
                assert images.getLast().getBinding() == 9 && images.getLast().isStorageImage;
                try {
                    builder.addImageDescriptor(images.getFirst());
                    throw new AssertionError("Duplicate descriptor binding accepted");
                } catch (IllegalArgumentException expected) { }
                assert LegacyPipelineConfig.nativeTextureSlot("Sampler2") == 2;
            } finally {
                Uniforms.mat4f_uniformMap.remove("portMatrix");
                Uniforms.vec1f_uniformMap.remove("portScalar");
            }
        }
        System.out.println("1.21.4 native pipeline config, matrix/scalar UBO bytes and sampled/storage bindings passed");
    }

    private static void checkShaders() throws Exception {
        String nativeVertex = LegacyShaderLoadUtil.loadShader(
                LegacyShaderLoadUtil.resolveShaderPath("basic/terrain"), "terrain.vsh");
        for (String include : new String[]{"light.glsl", "fog.glsl"}) {
            nativeVertex = nativeVertex.replace("#include \"" + include + "\"",
                    LegacyShaderLoadUtil.loadShader(LegacyShaderLoadUtil.resolveShaderPath("include"), include));
        }
        String vertex = dev.veyra.shader.TerrainShaderAdapter.adaptVertex(nativeVertex);
        String fragment = dev.veyra.shader.TerrainShaderAdapter.adaptFragment(
                "#version 120\nvarying vec3 upVec;\nvoid main(){gl_FragColor=vec4(upVec,1.0);}");
        assert !fragment.contains("TextureSize;") && !fragment.contains("UseRgss;");
        long compiler = org.lwjgl.util.shaderc.Shaderc.shaderc_compiler_initialize();
        if (compiler == 0) throw new IllegalStateException("Could not initialize shaderc");
        try {
            compile(compiler, vertex, org.lwjgl.util.shaderc.Shaderc.shaderc_glsl_vertex_shader);
            compile(compiler, fragment, org.lwjgl.util.shaderc.Shaderc.shaderc_glsl_fragment_shader);
            var cloudConfig = LegacyPipelineConfigs.clouds(LegacyShaderLoadUtil.getJsonConfig(
                    LegacyShaderLoadUtil.resolveShaderPath("basic/clouds"), "clouds"));
            assert cloudConfig.ubs.getLast().binding == 1;
            String cloudNative = LegacyPipelineConfigs.cloudVertex(LegacyShaderLoadUtil.loadShader(
                    LegacyShaderLoadUtil.resolveShaderPath("basic/clouds"), "clouds.vsh"));
            var cloudVertex = dev.veyra.shader.CloudShaderAdapter.adaptVertexWithMetadata(cloudNative,
                    java.nio.file.Files.readString(java.nio.file.Path.of("examples/cloud-model-view/shaders/gbuffers_clouds.vsh")));
            var cloudFragment = dev.veyra.shader.CloudShaderAdapter.adapt(
                    java.nio.file.Files.readString(java.nio.file.Path.of("examples/cloud-model-view/shaders/gbuffers_clouds.fsh")),
                    cloudVertex.uniformFields());
            String fields = cloudFragment.uniformFields().stream()
                    .map(field -> field.type() + " " + field.name() + ";")
                    .collect(java.util.stream.Collectors.joining("\n"));
            compile(compiler, cloudVertex.source().replace("float FogCloudsEnd;", "float FogCloudsEnd;\n" + fields),
                    org.lwjgl.util.shaderc.Shaderc.shaderc_glsl_vertex_shader);
            compile(compiler, cloudFragment.source(), org.lwjgl.util.shaderc.Shaderc.shaderc_glsl_fragment_shader);
        } finally { org.lwjgl.util.shaderc.Shaderc.shaderc_compiler_release(compiler); }
    }

    private static void compile(long compiler, String source, int stage) {
        long result = org.lwjgl.util.shaderc.Shaderc.shaderc_compile_into_spv(
                compiler, source, stage, "legacy4-terrain", "main", 0);
        if (result == 0) throw new IllegalStateException("shaderc returned no result");
        try {
            assert org.lwjgl.util.shaderc.Shaderc.shaderc_result_get_compilation_status(result)
                    == org.lwjgl.util.shaderc.Shaderc.shaderc_compilation_status_success
                    : org.lwjgl.util.shaderc.Shaderc.shaderc_result_get_error_message(result);
            assert org.lwjgl.util.shaderc.Shaderc.shaderc_result_get_length(result) > 0;
        } finally { org.lwjgl.util.shaderc.Shaderc.shaderc_result_release(result); }
    }
}
