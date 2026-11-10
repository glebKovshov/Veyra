package dev.veyra.compat;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.veyra.shader.LegacyGlslSource;
import dev.veyra.shader.VeyraNativeImageBindings;
import dev.veyra.vulkan.VeyraShaderCache;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiFunction;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceProvider;
import net.vulkanmod.gl.VkGlProgram;
import net.vulkanmod.gl.VkGlTexture;
import net.vulkanmod.interfaces.ShaderMixed;
import net.vulkanmod.vulkan.shader.GraphicsPipeline;
import net.vulkanmod.vulkan.shader.Pipeline;
import net.vulkanmod.vulkan.shader.SPIRVUtils;
import net.vulkanmod.vulkan.shader.converter.GLSLParser;
import net.vulkanmod.vulkan.shader.converter.Lexer;
import net.vulkanmod.vulkan.shader.descriptor.ImageDescriptor;
import net.vulkanmod.vulkan.shader.descriptor.UBO;
import net.vulkanmod.vulkan.texture.VulkanImage;

/**
 * Stand-in for VulkanMod's 1.21.5 device on Minecraft 1.21.1. Pipelines compile the way VulkanMod
 * 0.6.x compiles its own (GLSL parser, then native pipeline); a vanilla shader instance holds the
 * CPU uniforms so vanilla draw paths can bind the result.
 */
public final class VkGpuDevice implements GpuDevice, VeyraShaderCache {
    private static final VkGpuDevice INSTANCE = new VkGpuDevice();
    private static final String HOLDER_VERTEX = "#version 150\nvoid main() { gl_Position = vec4(0.0); }\n";
    private static final String HOLDER_FRAGMENT = "#version 150\nout vec4 fragColor;\nvoid main() { fragColor = vec4(0.0); }\n";
    private final VkCommandEncoder encoder = new VkCommandEncoder();
    private static int compiling;
    private static int holders;

    private VkGpuDevice() { }

    public static VkGpuDevice instance() { return INSTANCE; }

    /** Whether the shader parser is running for one of Veyra's pipelines. */
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
            // Uniform blocks become named uniforms: before 1.21.6 Minecraft sets uniforms by name.
            vertex = LegacyGlslSource.prepare(vertex);
            fragment = LegacyGlslSource.stripUnusedFragmentInputs(LegacyGlslSource.prepare(fragment));
        } catch (IOException failure) {
            throw new IllegalStateException("Could not preprocess " + pipeline.getLocation(), failure);
        }

        vertex = stripIoLocations(vertex);
        FragmentOutputs outputs = FragmentOutputs.hide(stripIoLocations(fragment));
        fragment = outputs.source();

        VertexFormat format = pipeline.getVertexFormat();
        GraphicsPipeline nativePipeline;
        UBO loose = null;
        compiling++;
        try {
            GLSLParser parser = new GLSLParser();
            parser.setVertexFormat(format);
            try {
                parser.parse(new Lexer(vertex), GLSLParser.Stage.VERTEX);
                parser.parse(new Lexer(fragment), GLSLParser.Stage.FRAGMENT);
            } catch (RuntimeException failure) {
                throw new IllegalStateException("Could not parse pipeline " + pipeline.getLocation(), failure);
            }
            UBO[] ubos = parser.createUBOs();
            List<ImageDescriptor> samplers = parser.getSamplerList();
            for (UBO ubo : ubos) {
                if (ubo != null && "UBO 0".equals(ubo.name)) loose = ubo;
            }
            Pipeline.Builder builder = new Pipeline.Builder(format, name);
            builder.setUniforms(List.of(ubos), samplers);
            builder.setShaderSrc(SPIRVUtils.ShaderKind.VERTEX_SHADER, parser.getOutput(GLSLParser.Stage.VERTEX));
            builder.setShaderSrc(SPIRVUtils.ShaderKind.FRAGMENT_SHADER, outputs.restore(parser.getOutput(GLSLParser.Stage.FRAGMENT)));
            nativePipeline = builder.createGraphicsPipeline();
        } finally {
            compiling--;
        }

        ShaderInstance program = holder(format, loose);
        ShaderMixed mixed = ShaderMixed.of(program);
        GraphicsPipeline placeholder = mixed.getPipeline();
        mixed.setPipeline(nativePipeline);
        VkGlProgram.getProgram(program.getId()).bindPipeline(nativePipeline);
        if (placeholder != null && placeholder != nativePipeline) placeholder.cleanUp();
        if (loose != null) mixed.setupUniformSuppliers(loose);
        mixed.setDoUniformsUpdate();

        pipeline.setPipeline(nativePipeline);
        pipeline.setProgram(new EGlProgram(program));
        LegacyDraws.register(pipeline);
        return new CompiledRenderPipeline() {
            @Override public boolean containsUniform(String uniform) { return program.getUniform(uniform) != null; }
            @Override public boolean isValid() { return true; }
        };
    }

    private static final java.util.regex.Pattern IO_LOCATION = java.util.regex.Pattern.compile(
            "layout\\s*\\(\\s*location\\s*=\\s*\\d+\\s*\\)\\s*(?=(?:(?:flat|smooth|noperspective|centroid)\\s+)*(?:in|out)\\b)");
    private static final java.util.regex.Pattern FRAGMENT_OUTPUT = java.util.regex.Pattern.compile(
            "(?m)^[\\t ]*(?:layout\\s*\\(\\s*location\\s*=\\s*(\\d+)\\s*\\)\\s*)?out\\s+(\\w+)\\s+(\\w+)\\s*;[\\t ]*\\r?\\n?");

    // The 0.6.7 parser reads every layout qualifier as a uniform block; interface variables link by name.
    private static String stripIoLocations(String source) { return IO_LOCATION.matcher(source).replaceAll(""); }

    /**
     * Fragment outputs kept away from the 0.6.7 parser, which accepts a single output, and declared
     * again with their locations after its header.
     */
    private record FragmentOutputs(String source, List<String> declarations) {
        static FragmentOutputs hide(String fragment) {
            var matcher = FRAGMENT_OUTPUT.matcher(fragment);
            java.util.Set<Integer> explicit = new java.util.HashSet<>();
            while (matcher.find()) if (matcher.group(1) != null) explicit.add(Integer.parseInt(matcher.group(1)));
            matcher.reset();
            List<String> declarations = new java.util.ArrayList<>();
            StringBuilder hidden = new StringBuilder(fragment.length());
            int next = 0;
            while (matcher.find()) {
                int location;
                if (matcher.group(1) != null) location = Integer.parseInt(matcher.group(1));
                else {
                    while (explicit.contains(next)) next++;
                    location = next++;
                }
                declarations.add("layout(location = " + location + ") out " + matcher.group(2) + " " + matcher.group(3) + ";\n");
                matcher.appendReplacement(hidden, "");
            }
            matcher.appendTail(hidden);
            return new FragmentOutputs(hidden.toString(), List.copyOf(declarations));
        }

        String restore(String parsed) {
            if (declarations.isEmpty()) return parsed;
            // After the version and extension lines the parser writes first.
            int insert = 0;
            for (String line : parsed.split("\n", -1)) {
                String trimmed = line.trim();
                if (!(trimmed.startsWith("#version") || trimmed.startsWith("#extension") || trimmed.isEmpty())) break;
                insert += line.length() + 1;
            }
            insert = Math.min(insert, parsed.length());
            return parsed.substring(0, insert) + String.join("", declarations) + parsed.substring(insert);
        }
    }

    /**
     * A shader instance with CPU storage for every field of the loose uniform block. Its own sources
     * are trivial: the instance only carries uniforms and the native pipeline compiled above.
     */
    private static ShaderInstance holder(VertexFormat format, UBO loose) {
        String name = "veyra_pipeline_" + holders++;
        JsonObject config = new JsonObject();
        config.addProperty("vertex", name);
        config.addProperty("fragment", name);
        config.add("samplers", new JsonArray());
        JsonArray uniforms = new JsonArray();
        if (loose != null) {
            for (var field : loose.getUniforms()) uniforms.add(uniform(field.getName(), field.getInfo().type));
        }
        config.add("uniforms", uniforms);
        Map<ResourceLocation, String> files = Map.of(
                ResourceLocation.withDefaultNamespace("shaders/core/" + name + ".json"), config.toString(),
                ResourceLocation.withDefaultNamespace("shaders/core/" + name + ".vsh"), HOLDER_VERTEX,
                ResourceLocation.withDefaultNamespace("shaders/core/" + name + ".fsh"), HOLDER_FRAGMENT);
        ResourceProvider provider = location -> {
            String content = files.get(location);
            return content == null ? Optional.empty() : Optional.of(new Resource(
                    net.minecraft.client.Minecraft.getInstance().getVanillaPackResources(),
                    () -> new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8))));
        };
        // The holder's trivial stages are not the pipeline being compiled; its image bindings do not apply.
        return VeyraNativeImageBindings.without(() -> {
            try {
                return new ShaderInstance(provider, name, format);
            } catch (IOException failure) {
                throw new IllegalStateException("Could not create the uniform holder " + name, failure);
            }
        });
    }

    private static JsonObject uniform(String name, String glslType) {
        return switch (glslType) {
            case "float" -> storage(name, "float", 1);
            case "vec2" -> storage(name, "float", 2);
            case "vec3" -> storage(name, "float", 3);
            case "vec4" -> storage(name, "float", 4);
            case "int" -> storage(name, "int", 1);
            case "ivec2" -> storage(name, "int", 2);
            case "ivec3" -> storage(name, "int", 3);
            case "mat3" -> storage(name, "matrix3x3", 9);
            case "mat4" -> storage(name, "matrix4x4", 16);
            default -> throw new IllegalArgumentException("Unsupported uniform type " + glslType + " of " + name);
        };
    }

    private static JsonObject storage(String name, String type, int count) {
        JsonObject uniform = new JsonObject();
        uniform.addProperty("name", name);
        uniform.addProperty("type", type);
        uniform.addProperty("count", count);
        JsonArray values = new JsonArray();
        for (int index = 0; index < count; index++) values.add(0.0F);
        uniform.add("values", values);
        return uniform;
    }

    // Sources are passed to precompilePipeline directly and never cached here.
    @Override public void veyra$pruneShaderSources(Set<ResourceLocation> retained) { }
}
