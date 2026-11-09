package dev.veyra.vulkan;

import com.google.gson.JsonParser;
import java.util.List;
import net.vulkanmod.vulkan.shader.Pipeline;
import net.vulkanmod.vulkan.shader.SPIRVUtils;
import net.vulkanmod.vulkan.shader.Uniforms;
import net.vulkanmod.vulkan.shader.descriptor.ImageDescriptor;
import net.vulkanmod.vulkan.shader.descriptor.UBO;
import net.vulkanmod.vulkan.util.MappedBuffer;
import org.lwjgl.system.MemoryStack;
import static org.lwjgl.vulkan.VK10.*;

final class LegacyPipelineCheck {
    static void run() throws Exception {
        checkNativeSamplerSlots();
        var terrain = LegacyPipelineConfigs.TERRAIN_UB0;
        assert terrain.binding == 0 && terrain.stage == VK_SHADER_STAGE_VERTEX_BIT;
        assert terrain.fields.equals(List.of(new LegacyPipelineConfig.Field("mat4", "MVP")));
        assert LegacyPipelineConfigs.TERRAIN_PC.size() == 1;
        assert LegacyPipelineConfigs.TERRAIN_PC.get(0).getAsJsonObject().get("name").getAsString().equals("ModelOffset");
        String terrainSource = LegacyShaderLoadUtil.loadShader(LegacyShaderLoadUtil.resolveShaderPath("basic"), "terrain/terrain.vsh");
        assert terrainSource.contains("vec4 getVertexPosition()") && terrainSource.contains("const vec4 pos = getVertexPosition();");
        assert LegacyShaderLoadUtil.getJsonConfig(LegacyShaderLoadUtil.resolveShaderPath("basic/clouds"), "clouds")
                .equals(LegacyShaderLoadUtil.getJsonConfig(LegacyShaderLoadUtil.resolveShaderPath("basic"), "clouds"));
        rejects(() -> LegacyShaderLoadUtil.loadShader(LegacyShaderLoadUtil.resolveShaderPath("basic"), "missing-test.vsh"));
        var clouds = LegacyPipelineConfig.fromJson("clouds",
                LegacyShaderLoadUtil.getJsonConfig(LegacyShaderLoadUtil.resolveShaderPath("basic"), "clouds"));
        assert clouds.ubs.size() == 2 && clouds.imageDescriptors.isEmpty();
        assert clouds.ubs.get(0).fields.get(1).equals(new LegacyPipelineConfig.Field("vec3", "ModelOffset"));
        assert clouds.ubs.get(1).fields.stream().map(LegacyPipelineConfig.Field::name).toList()
                .equals(List.of("ColorModulator", "FogColor", "FogStart", "FogEnd"));
        var cloudConfig = LegacyPipelineConfigs.clouds(LegacyShaderLoadUtil.getJsonConfig(
                LegacyShaderLoadUtil.resolveShaderPath("basic/clouds"), "clouds"));
        assert cloudConfig.ubs.getFirst().fields.equals(clouds.ubs.getFirst().fields);
        assert cloudConfig.ubs.getLast().stage == (VK_SHADER_STAGE_VERTEX_BIT | VK_SHADER_STAGE_FRAGMENT_BIT);
        assert cloudConfig.ubs.getLast().fields.equals(List.of(new LegacyPipelineConfig.Field("vec4", "ColorModulator"),
                new LegacyPipelineConfig.Field("float", "FogCloudsEnd")));
        String nativeCloudVertex = LegacyShaderLoadUtil.loadShader(LegacyShaderLoadUtil.resolveShaderPath("basic/clouds"), "clouds.vsh");
        String cloudVertex = LegacyPipelineConfigs.cloudVertex(nativeCloudVertex);
        assert cloudVertex.contains("mat4 MVP;") && cloudVertex.contains("vec3 ModelOffset;");
        assert cloudVertex.contains("float FogCloudsEnd;") && cloudVertex.contains("vertexDistance = length(viewPos.xyz);");
        assert cloudVertex.replace("""
                layout(binding = 1) uniform VeyraCloudUniformBuffer {
                    vec4 ColorModulator;
                    float FogCloudsEnd;
                };
                """, "").equals(nativeCloudVertex);
        rejects(() -> LegacyPipelineConfigs.cloudVertex("#version 450\nvoid main() {}"));
        rejects(() -> LegacyPipelineConfigs.clouds(new com.google.gson.JsonObject()));
        var nativeTerrain = LegacyPipelineConfig.fromJson("terrain",
                LegacyShaderLoadUtil.getJsonConfig(LegacyShaderLoadUtil.resolveShaderPath("basic"), "terrain"));
        assert nativeTerrain.imageDescriptors.equals(List.of(
                new LegacyPipelineConfig.ImageDescriptorInfo(2, "sampler2D", "Sampler0", 0),
                new LegacyPipelineConfig.ImageDescriptorInfo(3, "sampler2D", "Sampler2", 2)));
        rejects(() -> LegacyPipelineConfig.builder().addUB(terrain).addUB(terrain).build());
        rejects(() -> LegacyPipelineConfig.builder().addUB(terrain).addImageDescriptor(0, "sampler2D", "collision", 0).build());
        rejects(() -> LegacyPipelineConfig.UB.builder(1, 1).addUniform("float", "duplicate").addUniform("float", "duplicate"));
        rejects(() -> LegacyPipelineConfig.fromJson("missing", null));
        rejects(() -> new LegacyPipelineConfig.ImageDescriptorInfo(-1, "sampler2D", "invalid", 0));
        rejects(() -> new LegacyPipelineConfig.ImageDescriptorInfo(1, "unknown", "invalid", 0));
        rejects(() -> LegacyPipelineConfig.fromJson("bad-int", JsonParser.parseString(
                "{\"UBOs\":[{\"binding\":0,\"type\":\"vertex\",\"fields\":[{\"type\":\"int\",\"count\":3,\"name\":\"bad\"}]}]}").getAsJsonObject()));

        try (var stack = MemoryStack.stackPush()) {
            var integers = MappedBuffer.createFromBuffer(stack.calloc(12));
            integers.putInt(0, -123456789); integers.putInt(4, 0x7fc01234); integers.putInt(8, Integer.MIN_VALUE);
            var vector = MappedBuffer.createFromBuffer(stack.calloc(16));
            vector.putFloat(0, 3.5F); vector.putFloat(12, -2.0F);
            var matrix = MappedBuffer.createFromBuffer(stack.calloc(64));
            for (int i = 0; i < 16; i++) matrix.putFloat(i * 4, i + 0.25F);
            Uniforms.vec4f_uniformMap.put("portColor", () -> vector);
            Uniforms.mat4f_uniformMap.put("portMatrix", () -> matrix);
            Uniforms.vec1f_uniformMap.put("portScalar", () -> 9.5F);
            Uniforms.vec3f_uniformMap.put("ModelOffset", () -> integers);
            float[] fogRange = {12.5F, 52.5F};
            Uniforms.vec1f_uniformMap.put("FogStart", () -> fogRange[0]);
            Uniforms.vec1f_uniformMap.put("FogEnd", () -> fogRange[1]);
            try {
                var fog = LegacyPipelineConfig.UB.builder(1, VK_SHADER_STAGE_ALL_GRAPHICS);
                for (String name : List.of("FogEnvironmentalStart", "FogEnvironmentalEnd", "FogRenderDistanceStart",
                        "FogRenderDistanceEnd", "FogSkyEnd", "FogCloudsEnd")) fog.addUniform("float", name);
                var fogBuilder = new LegacyPipelineBuilder(null, "fog-check");
                fogBuilder.applyConfig(LegacyPipelineConfig.builder().addUB(fog.build()).build());
                var fogBlock = fogBuilder.getUBOs().getFirst();
                assert fogBlock.getSize() == 24;
                var fogBytes = stack.calloc(24);
                fogBlock.update(org.lwjgl.system.MemoryUtil.memAddress(fogBytes));
                assert fogBytes.getFloat(0) == 12.5F && fogBytes.getFloat(8) == 12.5F;
                for (int offset : new int[]{4, 12, 16, 20}) assert fogBytes.getFloat(offset) == 52.5F;
                fogRange[0] = 20; fogRange[1] = 80;
                fogBlock.update(org.lwjgl.system.MemoryUtil.memAddress(fogBytes));
                assert fogBytes.getFloat(0) == 20 && fogBytes.getFloat(20) == 80;
                var block = LegacyPipelineConfig.UB.builder(4, VK_SHADER_STAGE_ALL_GRAPHICS)
                        .addUniform("ivec2", "portInt2").addUniform("ivec3", "portInt3")
                        .addUniform("vec4", "portColor").addUniform("mat4", "portMatrix")
                        .addUniform("float", "portScalar").build();
                var config = LegacyPipelineConfig.builder().addUB(block)
                        .setPushConstants(LegacyPipelineConfigs.TERRAIN_PC)
                        .addImageDescriptor(9, "sampler2D", "sampled", 7)
                        .addImageDescriptor(11, "image2D", "storage", 13).build();
                var builder = new LegacyPipelineBuilder(null, "port-check");
                builder.setUniformSupplierGetter(info -> info.name.startsWith("portInt") ? () -> integers : null);
                builder.applyConfig(config);
                assert builder.getUBOs().size() == 1 && builder.getImageDescriptors().size() == 2;
                var ubo = builder.getUBOs().getFirst();
                assert ubo.getBinding() == 4 && ubo.getSize() == 116 && ubo.getUniformBuffer() == null;
                var bytes = stack.calloc(ubo.getSize());
                ubo.update(org.lwjgl.system.MemoryUtil.memAddress(bytes));
                assert bytes.getInt(0) == -123456789 && bytes.getInt(4) == 0x7fc01234;
                assert bytes.getInt(16) == -123456789 && bytes.getInt(20) == 0x7fc01234 && bytes.getInt(24) == Integer.MIN_VALUE;
                assert bytes.getFloat(32) == 3.5F && bytes.getFloat(44) == -2.0F;
                assert bytes.getFloat(48) == 0.25F && bytes.getFloat(108) == 15.25F && bytes.getFloat(112) == 9.5F;
                integers.putInt(0, 42);
                ubo.update(org.lwjgl.system.MemoryUtil.memAddress(bytes));
                assert bytes.getInt(0) == 42 && bytes.getInt(16) == 42;
                var sampled = builder.getImageDescriptors().getFirst();
                var storage = builder.getImageDescriptors().getLast();
                assert sampled.getBinding() == 9 && sampled.imageIdx == 7 && !sampled.isStorageImage;
                assert storage.getBinding() == 11 && storage.imageIdx == 13 && storage.isStorageImage;
                var nativeBuffers = Pipeline.Builder.class.getDeclaredField("UBOs");
                nativeBuffers.setAccessible(true);
                assert ((List<?>) nativeBuffers.get(builder)).getFirst() == ubo;
                var nativeConstants = Pipeline.Builder.class.getDeclaredField("pushConstants");
                nativeConstants.setAccessible(true);
                var constants = (net.vulkanmod.vulkan.shader.layout.PushConstants) nativeConstants.get(builder);
                assert constants.getSize() == 12;
                var constantBytes = stack.calloc(12);
                constants.update(org.lwjgl.system.MemoryUtil.memAddress(constantBytes));
                assert constantBytes.getInt(0) == 42 && constantBytes.getInt(4) == 0x7fc01234
                        && constantBytes.getInt(8) == Integer.MIN_VALUE;
                builder.addImageDescriptor(new ImageDescriptor(15, "sampler2D", "added", 17));
                builder.addUBO(new UBO(18, VK_SHADER_STAGE_VERTEX_BIT, 4, List.of()));
                assert ((List<?>) nativeBuffers.get(builder)).size() == 2;
                rejects(() -> builder.addImageDescriptor(new ImageDescriptor(4, "sampler2D", "duplicate", 0)));
                rejects(() -> builder.addUBO(new UBO(15, VK_SHADER_STAGE_VERTEX_BIT, 4, List.of())));
                try { builder.applyConfig(config); throw new AssertionError("Configuration replaced live bindings"); }
                catch (IllegalStateException expected) { }
                rejects(() -> builder.setShaderSrc(SPIRVUtils.ShaderKind.COMPUTE_SHADER, "ignored"));
                try { builder.createGraphicsPipeline(); throw new AssertionError("Missing source reached Vulkan"); }
                catch (IllegalStateException expected) { }
            } finally {
                Uniforms.vec4f_uniformMap.remove("portColor");
                Uniforms.mat4f_uniformMap.remove("portMatrix");
                Uniforms.vec1f_uniformMap.remove("portScalar");
                Uniforms.vec3f_uniformMap.remove("ModelOffset");
                Uniforms.vec1f_uniformMap.remove("FogStart");
                Uniforms.vec1f_uniformMap.remove("FogEnd");
            }
        }
        System.out.println("Legacy pipeline native UBO, integer bits, push constants and explicit bindings passed");
    }
    private static void rejects(Runnable action) {
        try { action.run(); throw new AssertionError("Invalid pipeline config was accepted"); }
        catch (IllegalArgumentException expected) { }
    }
    private static void checkNativeSamplerSlots() throws Exception {
        // Execute the dependency's pure mapping method without its GPU-allocating class initializer.
        var node = new org.objectweb.asm.tree.ClassNode();
        try (var stream = LegacyPipelineCheck.class.getClassLoader().getResourceAsStream(
                "net/vulkanmod/vulkan/texture/VTextureSelector.class")) {
            new org.objectweb.asm.ClassReader(java.util.Objects.requireNonNull(stream)).accept(node, 0);
        }
        var method = node.methods.stream().filter(value -> value.name.equals("getTextureIdx")).findFirst().orElseThrow();
        var writer = new org.objectweb.asm.ClassWriter(0);
        writer.visit(node.version, org.objectweb.asm.Opcodes.ACC_PUBLIC, "NativeTextureSlotCheck", null, "java/lang/Object", null);
        method.accept(writer);
        writer.visitEnd();
        class ReferenceLoader extends ClassLoader {
            Class<?> load(byte[] bytes) { return defineClass(null, bytes, 0, bytes.length); }
        }
        var reference = new ReferenceLoader().load(writer.toByteArray()).getMethod("getTextureIdx", String.class);
        for (String name : List.of("DiffuseSampler", "Sampler0", "Sampler1", "Sampler2", "Sampler3", "Sampler4", "Sampler5", "Sampler6", "Sampler7"))
            assert (int) reference.invoke(null, name) == LegacyPipelineConfig.nativeTextureSlot(name);
        assert (int) reference.invoke(null, "unsupported") == -1;
        rejects(() -> LegacyPipelineConfig.nativeTextureSlot("unsupported"));
    }
}
