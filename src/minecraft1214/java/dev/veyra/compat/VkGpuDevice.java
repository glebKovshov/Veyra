package dev.veyra.compat;

import com.mojang.blaze3d.buffers.BufferType;
import com.mojang.blaze3d.buffers.BufferUsage;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.veyra.accessor.LegacyConvertedShader;
import dev.veyra.mixin.accessor.CompiledShaderProgramInvoker;
import dev.veyra.shader.LegacyGlslSource;
import dev.veyra.vulkan.VeyraShaderCache;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.client.renderer.CompiledShaderProgram;
import net.minecraft.client.renderer.ShaderProgramConfig;
import net.minecraft.resources.ResourceLocation;
import net.vulkanmod.gl.VkGlTexture;
import net.vulkanmod.interfaces.shader.ShaderMixed;
import net.vulkanmod.vulkan.shader.GraphicsPipeline;
import net.vulkanmod.vulkan.shader.converter.GlslConverter;
import net.vulkanmod.vulkan.shader.descriptor.UBO;
import net.vulkanmod.vulkan.texture.VulkanImage;

/**
 * Stand-in for VulkanMod's 1.21.5 device. It compiles Veyra's pipeline descriptions into the
 * program objects VulkanMod 0.5.6 draws with on Minecraft 1.21.4.
 */
public final class VkGpuDevice implements GpuDevice, VeyraShaderCache {
    private static final VkGpuDevice INSTANCE = new VkGpuDevice();
    private static final Pattern VERTEX_INPUT = Pattern.compile(
            "layout\\(location = \\d+\\) in (\\w+) (\\w+);");
    private final VkCommandEncoder encoder = new VkCommandEncoder();
    private static int compiling;

    private VkGpuDevice() { }

    public static VkGpuDevice instance() { return INSTANCE; }

    /** Whether the shader converter is running for one of Veyra's pipelines. */
    public static boolean isCompiling() { return compiling > 0; }

    @Override public CommandEncoder createCommandEncoder() { return encoder; }

    @Override public int getMaxTextureSize() { return 8192; }

    @Override public GpuTexture createTexture(String label, TextureFormat format, int width, int height, int mipLevels) {
        if (mipLevels < 1) throw new IllegalArgumentException("mipLevels must be at least 1");
        int vkFormat = VkGpuTexture.vkFormat(format);
        int attachment = VulkanImage.isDepthFormat(vkFormat)
                ? org.lwjgl.vulkan.VK10.VK_IMAGE_USAGE_DEPTH_STENCIL_ATTACHMENT_BIT
                : org.lwjgl.vulkan.VK10.VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT;
        return gpuTextureFromVulkanImage(VulkanImage.builder(width, height)
                .setName(label)
                .setFormat(vkFormat)
                .setMipLevels(mipLevels)
                .addUsage(attachment)
                .createVulkanImage());
    }

    /** Registers an image under a new texture id; closing the returned texture frees the image. */
    public VkGpuTexture gpuTextureFromVulkanImage(VulkanImage image) {
        int id = VkGlTexture.genTextureId();
        VkGlTexture texture = VkGlTexture.getTexture(id);
        texture.setVulkanImage(image);
        return new VkGpuTexture(image.name, VkGpuTexture.textureFormat(image.format), image.width, image.height,
                image.mipLevels, id, texture);
    }

    @Override public GpuBuffer createBuffer(Supplier<String> label, BufferType type, BufferUsage usage, ByteBuffer data) {
        if (!data.hasRemaining()) throw new IllegalArgumentException("Buffer source must not be empty");
        return new GpuBuffer(type, usage, data);
    }

    @Override public CompiledRenderPipeline precompilePipeline(
            RenderPipeline pipeline, BiFunction<ResourceLocation, ShaderType, String> source) {
        String name = pipeline.getLocation().getPath();
        String vertex = source.apply(pipeline.getVertexShader(), ShaderType.VERTEX);
        String fragment = source.apply(pipeline.getFragmentShader(), ShaderType.FRAGMENT);
        if (vertex == null || fragment == null) {
            throw new IllegalStateException("Missing shader source for pipeline " + pipeline.getLocation());
        }
        try {
            vertex = LegacyGlslSource.preprocess(pipeline.getVertexShader() + ".vsh", vertex, pipeline.getShaderDefines());
            fragment = LegacyGlslSource.preprocess(pipeline.getFragmentShader() + ".fsh", fragment, pipeline.getShaderDefines());
        } catch (IOException failure) {
            throw new IllegalStateException("Could not preprocess " + pipeline.getLocation(), failure);
        }

        GlslConverter converter = new GlslConverter();
        GraphicsPipeline nativePipeline;
        UBO loose;
        LegacyConvertedShader converted;
        compiling++;
        try {
            converter.process(vertex, fragment);
            converted = (LegacyConvertedShader) converter;
            loose = converter.createUBO();
            List<UBO> blocks = new ArrayList<>();
            if (loose != null) blocks.add(loose);
            blocks.addAll(converted.veyra$storageDescriptors());

            // The plain native builder: the converter already declared every binding.
            var nativeBuilder = new net.vulkanmod.vulkan.shader.Pipeline.Builder(pipeline.getVertexFormat(), name);
            nativeBuilder.setUniforms(blocks, converter.getSamplerList());
            nativeBuilder.compileShaders(name, bindVertexInputs(converter.getVshConverted(), pipeline.getVertexFormat()),
                    converter.getFshConverted());
            nativePipeline = nativeBuilder.createGraphicsPipeline();
        } finally {
            compiling--;
        }

        CompiledShaderProgram program = CompiledShaderProgramInvoker.veyra$create(0);
        // No samplers are declared to the program: its apply() would copy them into texture slots by
        // declaration order, while Veyra binds each descriptor's own slot.
        program.setupUniforms(uniformStorage(pipeline, loose, converted.veyra$uniformTypes()), List.of());
        ShaderMixed mixed = ShaderMixed.of(program);
        mixed.setPipeline(nativePipeline);
        if (loose != null) mixed.setupUniformSuppliers(loose);
        mixed.setUniformsUpdate();

        pipeline.setPipeline(nativePipeline);
        pipeline.setProgram(new EGlProgram(program));
        LegacyDraws.register(pipeline);
        return new CompiledRenderPipeline() {
            @Override public boolean containsUniform(String uniform) { return program.getUniform(uniform) != null; }
            @Override public boolean isValid() { return true; }
        };
    }

    // The 0.5.6 converter numbers vertex inputs in declaration order; the native pipeline numbers
    // them by vertex format element.
    private static String bindVertexInputs(String vertex, VertexFormat format) {
        List<String> names = format.getElementAttributeNames();
        Matcher inputs = VERTEX_INPUT.matcher(vertex);
        StringBuilder bound = new StringBuilder(vertex.length());
        while (inputs.find()) {
            int location = names.indexOf(inputs.group(2));
            if (location < 0) {
                throw new IllegalStateException("Vertex input " + inputs.group(2) + " is not in the vertex format " + names);
            }
            inputs.appendReplacement(bound, Matcher.quoteReplacement(
                    "layout(location = " + location + ") in " + inputs.group(1) + " " + inputs.group(2) + ";"));
        }
        inputs.appendTail(bound);
        return bound.toString();
    }

    // Every field of the loose uniform block needs CPU storage in the program before VulkanMod
    // connects the block's suppliers to it.
    private static List<ShaderProgramConfig.Uniform> uniformStorage(
            RenderPipeline pipeline, UBO loose, Map<String, String> sourceTypes) {
        Map<String, ShaderProgramConfig.Uniform> storage = new LinkedHashMap<>();
        for (var field : loose == null ? List.<net.vulkanmod.vulkan.shader.layout.Uniform>of() : loose.getUniforms()) {
            String type = sourceTypes.get(field.getName());
            if (type == null) throw new IllegalStateException("Missing source type for native uniform " + field.getName());
            storage.put(field.getName(), uniform(field.getName(), type));
        }
        for (RenderPipeline.UniformDescription declared : pipeline.getUniforms()) {
            storage.putIfAbsent(declared.name(), uniform(declared.name(), switch (declared.type()) {
                case INT -> "int";
                case IVEC3 -> "ivec3";
                case FLOAT -> "float";
                case VEC2 -> "vec2";
                case VEC3 -> "vec3";
                case VEC4 -> "vec4";
                case MATRIX4X4 -> "mat4";
            }));
        }
        return List.copyOf(storage.values());
    }

    private static ShaderProgramConfig.Uniform uniform(String name, String glslType) {
        return switch (glslType) {
            case "float" -> storage(name, "float", 1);
            case "vec2" -> storage(name, "float", 2);
            case "vec3" -> storage(name, "float", 3);
            case "vec4" -> storage(name, "float", 4);
            case "int" -> storage(name, "int", 1);
            // The CPU uniform API has no two-component integer type; the spare word stays outside the shader field.
            case "ivec2", "ivec3" -> storage(name, "int", 3);
            case "mat4" -> storage(name, "matrix4x4", 16);
            default -> throw new IllegalArgumentException("Unsupported legacy uniform type " + glslType);
        };
    }

    private static ShaderProgramConfig.Uniform storage(String name, String type, int count) {
        List<Float> zeros = new ArrayList<>(count);
        for (int index = 0; index < count; index++) zeros.add(0.0F);
        return new ShaderProgramConfig.Uniform(name, type, count, zeros);
    }

    // Sources are passed to precompilePipeline directly and never cached here.
    @Override public void veyra$pruneShaderSources(Set<ResourceLocation> retained) { }
}
