package dev.veyra.shader;

import com.mojang.blaze3d.framegraph.FrameGraphBuilder;
import com.mojang.blaze3d.pipeline.CompiledRenderPipeline;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.blaze3d.framegraph.FramePass;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import com.mojang.blaze3d.resource.ResourceHandle;
import com.mojang.blaze3d.shaders.ShaderSource;
import com.mojang.blaze3d.shaders.ShaderType;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import dev.veyra.VeyraClient;
import dev.veyra.mixin.accessor.MinecraftAccessor;
import dev.veyra.mixin.accessor.PostPassAccessor;
import dev.veyra.mixin.accessor.ShaderManagerAccessor;
import dev.veyra.vulkan.VeyraDepthTextureView;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.CachedOrthoProjectionMatrixBuffer;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.PostPass;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.ShaderManager;
import net.minecraft.client.renderer.UniformValue;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.FluidTags;
import net.vulkanmod.gl.VkGlTexture;
import net.vulkanmod.render.engine.VkGpuTexture;
import net.vulkanmod.vulkan.Renderer;
import net.vulkanmod.vulkan.texture.VulkanImage;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VK10;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class VeyraRenderer {
    private static final Identifier SWAP_TARGET_A_ID = Identifier.fromNamespaceAndPath("veyra", "swap_a");
    private static final Identifier SWAP_TARGET_B_ID = Identifier.fromNamespaceAndPath("veyra", "swap_b");
    private static final String SHADERPACK_UNIFORMS = "VeyraParameters";
    private static final float WETNESS_RISE_HALF_LIFE_SECONDS = 600.0F / 20.0F;
    private static final float WETNESS_FALL_HALF_LIFE_SECONDS = 200.0F / 20.0F;
    private static final float LN_OF_TWO = (float) Math.log(2.0);
    private static final Identifier BLIT_VERTEX_ID = Identifier.withDefaultNamespace("core/screenquad");
    private static final String COPY_FRAGMENT_SHADER = """
            #version 450
            in vec2 texCoord;
            out vec4 outColor;
            uniform sampler2D InSampler;
            void main() {
                outColor = texture(InSampler, texCoord);
            }
            """;
    private static final Pattern VERSION_DIRECTIVE = Pattern.compile("(?m)^\\s*#version[^\\r\\n]*(?:\\r?\\n|$)");
    private static final Pattern EXTENSION_DIRECTIVE = Pattern.compile("(?m)^\\s*#extension[^\\r\\n]*(?:\\r?\\n|$)");
    private static final Pattern OUTPUT_DECLARATION = Pattern.compile("(?m)(?:layout\\s*\\([^)]*\\)\\s*)?out\\s+vec4\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*;");
    private static final Pattern LOCATION_LAYOUT = Pattern.compile("layout\\s*\\(\\s*location\\s*=\\s*\\d+\\s*\\)\\s*");
    private static final Pattern VARYING_DECLARATION = Pattern.compile("(?m)^\\s*(?:varying|in)\\s+vec2\\s+(?:texcoord|texCoord)\\s*;\\s*$");
    private static final Pattern MAIN_SAMPLER_DECLARATION = Pattern.compile("(?m)^\\s*uniform\\s+sampler2D\\s+(?:colortex0|Sampler0|InSampler)\\s*;\\s*$");
    private static final Pattern SUPPORTED_UNIFORM_DECLARATION = Pattern.compile("(?m)^\\s*uniform\\s+(?:float|int|bool)\\s+(?:viewWidth|viewHeight|aspectRatio|frameTimeCounter|frameTime|frameCounter|sunAngle|rainStrength|wetness|thunderStrength|worldTime|worldDay|eyeAltitude|screenBrightness|blindness|nightVision|darknessFactor|isEyeInWater|isSpectator|is_sneaking|is_sprinting|is_on_ground|is_hurt|is_invisible|is_burning)\\s*;[^\\r\\n]*(?:\\r?\\n|$)");
    private static final Pattern OTHER_COLOR_SAMPLER = Pattern.compile("\\bcolortex([1-9][0-9]*)\\b");
    private static final Pattern UNSUPPORTED_DEPTH_SAMPLER = Pattern.compile("\\bdepthtex(?!0\\b)[A-Za-z0-9_]*\\b");
    private static final Pattern DEPTH_SAMPLER = Pattern.compile("\\b(?:depthtex0|DepthSampler)\\b");
    private static final Pattern DEPTH_SAMPLER_DECLARATION = Pattern.compile(
            "(?m)^\\s*uniform\\s+(?:(?:lowp|mediump|highp)\\s+)?sampler2D\\s+(?:depthtex0|DepthSampler)\\s*;[^\\r\\n]*(?:\\r?\\n|$)");
    // PostPass appends "Sampler" to every input name when binding it to the render pass.
    private static final String DEPTH_INPUT_NAME = "Sampler1";
    private static final String DEPTH_INPUT_SAMPLER = DEPTH_INPUT_NAME + "Sampler";
    private static final List<String> SUPPORTED_UNIFORMS = List.of(
            "viewWidth", "viewHeight", "aspectRatio", "frameTimeCounter", "frameTime", "frameCounter",
            "sunAngle", "rainStrength", "wetness", "thunderStrength", "worldTime", "worldDay",
            "eyeAltitude", "screenBrightness", "blindness", "nightVision", "darknessFactor", "isEyeInWater",
            "isSpectator", "is_sneaking", "is_sprinting", "is_on_ground", "is_hurt", "is_invisible", "is_burning");
    private static final List<String> BOOLEAN_UNIFORMS = List.of(
            "isSpectator", "is_sneaking", "is_sprinting", "is_on_ground", "is_hurt", "is_invisible", "is_burning");
    private static final List<String> INTEGER_UNIFORMS = List.of(
            "frameCounter", "worldTime", "worldDay", "isEyeInWater", "isSpectator", "is_sneaking",
            "is_sprinting", "is_on_ground", "is_hurt", "is_invisible", "is_burning");
    private static final List<String> DYNAMIC_UNIFORMS = List.of(
            "frameTimeCounter", "frameTime", "frameCounter", "sunAngle", "rainStrength", "wetness", "thunderStrength",
            "worldTime", "worldDay", "eyeAltitude", "screenBrightness", "blindness", "nightVision", "darknessFactor",
            "isEyeInWater", "isSpectator",
            "is_sneaking", "is_sprinting", "is_on_ground", "is_hurt", "is_invisible", "is_burning");
    private static final Map<String, PipelineProgram> PIPELINE_CACHE = new HashMap<>();
    private static final Set<PipelineProgram> COMPILED_PIPELINES = Collections.newSetFromMap(new IdentityHashMap<>());

    private static ShaderPackRepository repository;
    private static RenderTarget swapTargetA;
    private static RenderTarget swapTargetB;
    private static CachedOrthoProjectionMatrixBuffer projectionBuffer;
    private static PipelineSet pipelineSet;
    private static GpuTexture sampledDepthTexture;
    private static GpuTextureView sampledDepthTextureView;
    private static boolean pipelinesDirty = true;
    private static boolean renderingEnabled;
    private static String lastError = "";
    private static long previousFrameNanos;
    private static int frameCounter;
    private static float frameTimeCounter;
    private static float frameTime;
    private static ClientLevel wetnessLevel;
    private static boolean wetnessInitialized;
    private static float smoothedWetness;

    private VeyraRenderer() {
    }

    public static void initialize(ShaderPackRepository packRepository) {
        repository = packRepository;
        renderingEnabled = packRepository.getSelected().isPresent();
    }

    public static Optional<ShaderPack> selectedPack() {
        return repository == null ? Optional.empty() : repository.getSelected();
    }

    public static boolean isRenderingEnabled() {
        return renderingEnabled && selectedPack().isPresent();
    }

    public static String getLastError() {
        return lastError;
    }

    public static String select(ShaderPack pack) {
        PipelineSet prepared = null;
        try {
            RenderTarget mainTarget = Minecraft.getInstance().getMainRenderTarget();
            prepared = compilePipelines(pack, mainTarget.width, mainTarget.height);
            repository.select(pack);
            replacePipelineSet(prepared);
            prepared = null;
            pipelinesDirty = false;
            renderingEnabled = true;
            lastError = "";
            VeyraClient.LOGGER.info("Enabled {} post-processing pass(es) from shader pack '{}'.",
                    pipelineSet.shaderPasses().size(), pack.name());
            return "Enabled " + pack.name() + " (" + pipelineSet.shaderPasses().size() + " passes).";
        } catch (Exception exception) {
            if (prepared != null) {
                prepared.close();
            }
            lastError = shortError(exception);
            VeyraClient.LOGGER.error("Could not enable shader pack '{}'.", pack.name(), exception);
            return "Shader compile failed: " + lastError;
        }
    }

    public static String disable() {
        try {
            repository.select(null);
            renderingEnabled = false;
            pipelinesDirty = false;
            replacePipelineSet(null);
            lastError = "";
            return "Shader pack disabled.";
        } catch (IOException exception) {
            lastError = shortError(exception);
            return "Could not save the setting: " + lastError;
        }
    }

    public static void invalidatePipelines() {
        pipelinesDirty = true;
        projectionBuffer = null;
        PIPELINE_CACHE.clear();
        COMPILED_PIPELINES.clear();
        replacePipelineSet(null);
    }

    public static void render(Minecraft minecraft) {
        if (!isRenderingEnabled() || minecraft.level == null || minecraft.player == null) {
            return;
        }

        boolean depthSampling = false;
        try {
            RenderTarget mainTarget = minecraft.getMainRenderTarget();
            if (mainTarget.width <= 0 || mainTarget.height <= 0) {
                return;
            }
            ensurePipelines(mainTarget.width, mainTarget.height);

            FrameGraphBuilder graph = new FrameGraphBuilder();
            ResourceHandle<RenderTarget> main = graph.importExternal("main", mainTarget);
            ensureSwapTargets(mainTarget.width, mainTarget.height);
            ResourceHandle<RenderTarget> swapA = graph.importExternal("veyra:shaderpack-swap-a", swapTargetA);
            ResourceHandle<RenderTarget> swapB = graph.importExternal("veyra:shaderpack-swap-b", swapTargetB);
            Map<Identifier, ResourceHandle<RenderTarget>> targets = new HashMap<>();
            targets.put(PostChain.MAIN_TARGET_ID, main);
            targets.put(SWAP_TARGET_A_ID, swapA);
            targets.put(SWAP_TARGET_B_ID, swapB);

            var samplerInfo = projectionBuffer().getBuffer(mainTarget.width, mainTarget.height);
            FrameUniforms frameUniforms = updateFrameUniforms(minecraft);
            for (ShaderPass shaderPass : pipelineSet.shaderPasses()) {
                updateDynamicUniforms(shaderPass, mainTarget.width, mainTarget.height, frameUniforms);
                shaderPass.pass().addToFrame(graph, targets, samplerInfo);
            }
            pipelineSet.copyPass().addToFrame(graph, targets, samplerInfo);
            DepthSamplerState depthSamplerState = null;
            if (pipelineSet.usesDepthSampler()) {
                depthSampling = true;
                depthSamplerState = prepareDepthSampler();
            }
            graph.execute(GraphicsResourceAllocator.UNPOOLED);
            if (depthSamplerState != null) {
                restoreDepthSampler(depthSamplerState);
            }
        } catch (Exception exception) {
            if (depthSampling) {
                try {
                    restoreDepthSampler();
                } catch (Exception recoveryException) {
                    exception.addSuppressed(recoveryException);
                }
            }
            renderingEnabled = false;
            lastError = shortError(exception);
            VeyraClient.LOGGER.error("Disabled the shader pass after a rendering failure.", exception);
        }
    }

    private static void ensurePipelines(int width, int height) throws IOException {
        if (!pipelinesDirty && pipelineSet != null && pipelineSet.width() == width && pipelineSet.height() == height) {
            return;
        }
        ShaderPack pack = repository.getSelected().orElse(null);
        if (pack == null) {
            renderingEnabled = false;
            replacePipelineSet(null);
            return;
        }
        PipelineSet prepared = compilePipelines(pack, width, height);
        replacePipelineSet(prepared);
        pipelinesDirty = false;
        lastError = "";
        VeyraClient.LOGGER.info("Loaded {} post-processing pass(es) from shader pack '{}'.",
                pipelineSet.shaderPasses().size(), pack.name());
    }

    private static PipelineSet compilePipelines(ShaderPack pack, int width, int height) throws IOException {
        List<ShaderPack.Program> sources = pack.readPrograms();
        List<PipelineProgram> programs = new ArrayList<>(sources.size());
        for (ShaderPack.Program source : sources) {
            AdaptedShader adapted = adaptShader(source.source());
            PipelineProgram program = pipelineProgram(source.name(), adapted);
            compilePipeline(program);
            programs.add(program);
        }

        PipelineProgram copyProgram = pipelineProgram("copy", new AdaptedShader(COPY_FRAGMENT_SHADER, List.of(), false));
        compilePipeline(copyProgram);

        List<ShaderPass> passes = new ArrayList<>(programs.size());
        try {
            Identifier inputTarget = PostChain.MAIN_TARGET_ID;
            for (int index = 0; index < programs.size(); index++) {
                PipelineProgram program = programs.get(index);
                Identifier outputTarget = index % 2 == 0 ? SWAP_TARGET_A_ID : SWAP_TARGET_B_ID;
                List<PostPass.Input> inputs = new ArrayList<>();
                inputs.add(new PostPass.TargetInput("In", inputTarget, false, true));
                if (program.usesDepthSampler()) {
                    inputs.add(new ExternalTextureInput(DEPTH_INPUT_NAME));
                }
                PostPass pass = new PostPass(program.pipeline(), outputTarget,
                        passUniforms(program, width, height, FrameUniforms.ZERO), inputs);
                ShaderPass shaderPass = new ShaderPass(pass, program);
                passes.add(shaderPass);
                if (usesDynamicUniforms(program)) {
                    makeDynamicUniformBuffer(pass, program, width, height);
                }
                inputTarget = outputTarget;
            }
        } catch (RuntimeException exception) {
            passes.forEach(shaderPass -> shaderPass.pass().close());
            throw exception;
        }
        PostPass copyPass = new PostPass(copyProgram.pipeline(), PostChain.MAIN_TARGET_ID, Map.of(),
                List.of(new PostPass.TargetInput("In", programs.size() % 2 == 1 ? SWAP_TARGET_A_ID : SWAP_TARGET_B_ID, false, true)));
        return new PipelineSet(List.copyOf(passes), copyPass, width, height,
                programs.stream().anyMatch(PipelineProgram::usesDepthSampler));
    }

    private static PipelineProgram pipelineProgram(String name, AdaptedShader shader) {
        String hash = sha256(name + '\n' + shader.source());
        String cacheKey = name + ':' + hash;
        return PIPELINE_CACHE.computeIfAbsent(cacheKey, ignored -> {
            String suffix = name + '_' + hash;
            Identifier shaderId = Identifier.fromNamespaceAndPath("veyra", "shaderpack/" + suffix);
            Identifier pipelineId = Identifier.fromNamespaceAndPath("veyra", "pipeline/shaderpack/" + suffix);
            return new PipelineProgram(shaderId, shader.source(), shader.uniforms(), shader.usesDepthSampler(),
                    createPipeline(pipelineId, shaderId, shader.uniforms(), shader.usesDepthSampler()));
        });
    }

    private static void compilePipeline(PipelineProgram program) {
        if (COMPILED_PIPELINES.contains(program)) {
            return;
        }
        ShaderManager shaderManager = ((MinecraftAccessor) Minecraft.getInstance()).veyra$getShaderManager();
        ShaderSource source = (identifier, type) -> {
            if (identifier.equals(program.shaderId()) && type == ShaderType.FRAGMENT) {
                return program.fragmentSource();
            }
            return shaderManager.getShader(identifier, type);
        };

        CompiledRenderPipeline compiled = RenderSystem.getDevice().precompilePipeline(program.pipeline(), source);
        if (!compiled.isValid()) {
            throw new IllegalStateException("VulkanMod could not compile pipeline " + program.pipeline().getLocation() + ".");
        }
        COMPILED_PIPELINES.add(program);
    }

    private static RenderPipeline createPipeline(
            Identifier pipeline, Identifier fragmentShader, List<String> uniforms, boolean usesDepthSampler) {
        RenderPipeline.Builder builder = RenderPipeline.builder(RenderPipelines.POST_PROCESSING_SNIPPET)
                .withFragmentShader(fragmentShader)
                .withVertexShader(BLIT_VERTEX_ID)
                .withLocation(pipeline)
                .withSampler("InSampler")
                .withUniform("SamplerInfo", UniformType.UNIFORM_BUFFER);
        if (usesDepthSampler) {
            builder.withSampler(DEPTH_INPUT_SAMPLER);
        }
        if (!uniforms.isEmpty()) {
            builder.withUniform(SHADERPACK_UNIFORMS, UniformType.UNIFORM_BUFFER);
        }
        return builder.build();
    }

    private static AdaptedShader adaptShader(String source) throws IOException {
        if (source == null || source.isBlank()) {
            throw new IOException("Fragment shader is empty.");
        }
        if (source.length() > 1024 * 1024) {
            throw new IOException("Fragment shader exceeds the 1 MiB size limit.");
        }
        if (source.contains("gl_FragData")) {
            throw new IOException("Multiple fragment outputs (gl_FragData) are not supported by the single-target passes.");
        }
        Matcher otherSampler = OTHER_COLOR_SAMPLER.matcher(source);
        if (otherSampler.find()) {
            throw new IOException("Only colortex0 is currently available to post-processing passes.");
        }
        Matcher unsupportedDepthSampler = UNSUPPORTED_DEPTH_SAMPLER.matcher(source);
        if (unsupportedDepthSampler.find()) {
            throw new IOException("Only depthtex0 is currently available for depth texture sampling on VulkanMod.");
        }
        Matcher depthSampler = DEPTH_SAMPLER.matcher(source);
        boolean usesDepthSampler = depthSampler.find();
        List<String> uniforms = SUPPORTED_UNIFORMS.stream().filter(source::contains).toList();
        String body = source.replace("\uFEFF", "");
        body = VERSION_DIRECTIVE.matcher(body).replaceAll("");
        Matcher extensionMatcher = EXTENSION_DIRECTIVE.matcher(body);
        StringBuilder extensions = new StringBuilder();
        while (extensionMatcher.find()) {
            extensions.append(extensionMatcher.group());
        }
        body = extensionMatcher.replaceAll("");
        body = VARYING_DECLARATION.matcher(body).replaceAll("");
        body = MAIN_SAMPLER_DECLARATION.matcher(body).replaceAll("");
        body = DEPTH_SAMPLER_DECLARATION.matcher(body).replaceAll("");
        body = SUPPORTED_UNIFORM_DECLARATION.matcher(body).replaceAll("");
        body = LOCATION_LAYOUT.matcher(body).replaceAll("");
        body = body.replace("texture2D(", "texture(");

        String outputName = "outColor";
        Matcher outputMatcher = OUTPUT_DECLARATION.matcher(body);
        boolean hasOutput = outputMatcher.find();
        if (hasOutput) {
            outputName = outputMatcher.group(1);
        }

        StringBuilder adapted = new StringBuilder("#version 450\n").append(extensions)
                .append("in vec2 texCoord;\n");
        if (!hasOutput) {
            adapted.append("out vec4 outColor;\n");
        }
        if (!uniforms.isEmpty()) {
            adapted.append("layout(std140) uniform ").append(SHADERPACK_UNIFORMS).append(" {\n");
            for (String uniform : uniforms) {
                String type = INTEGER_UNIFORMS.contains(uniform) ? "int" : "float";
                // VulkanMod's shader parser rejects bool members in uniform blocks, so expose them through int fields.
                String memberName = BOOLEAN_UNIFORMS.contains(uniform) ? "veyra_" + uniform : uniform;
                adapted.append("    ").append(type)
                        .append(' ').append(memberName).append(";\n");
            }
            adapted.append("};\n");
            for (String uniform : uniforms) {
                if (BOOLEAN_UNIFORMS.contains(uniform)) {
                    adapted.append("#define ").append(uniform).append(" (veyra_")
                            .append(uniform).append(" != 0)\n");
                }
            }
        }
        adapted.append("uniform sampler2D InSampler;\n")
                .append("#define colortex0 InSampler\n")
                .append("#define Sampler0 InSampler\n")
                .append("#define texcoord texCoord\n")
                .append("#define gl_FragColor ").append(outputName).append('\n')
                .append("#define texture2D texture\n");
        if (usesDepthSampler) {
            adapted.append("uniform sampler2D ").append(DEPTH_INPUT_SAMPLER).append(";\n")
                    .append("#define depthtex0 ").append(DEPTH_INPUT_SAMPLER).append('\n')
                    .append("#define DepthSampler ").append(DEPTH_INPUT_SAMPLER).append('\n');
        }
        adapted.append(body);
        return new AdaptedShader(adapted.toString(), uniforms, usesDepthSampler);
    }

    private static DepthSamplerState prepareDepthSampler() {
        Renderer renderer = Renderer.getInstance();
        GpuTexture depthTexture = renderer.getMainPass().getDepthAttachment();
        if (!(depthTexture instanceof VkGpuTexture vulkanTexture) || depthTexture.isClosed()) {
            throw new IllegalStateException("VulkanMod's main depth attachment is unavailable for shader sampling.");
        }

        if (renderer.getBoundRenderPass() != null) {
            renderer.endRenderPass();
        }

        if (sampledDepthTexture != depthTexture || sampledDepthTextureView == null
                || sampledDepthTextureView.isClosed()) {
            if (sampledDepthTextureView != null && !sampledDepthTextureView.isClosed()) {
                sampledDepthTextureView.close();
            }
            sampledDepthTexture = depthTexture;
            sampledDepthTextureView = new VeyraDepthTextureView(vulkanTexture);
        }

        VulkanImage depthImage = vulkanTexture.getVulkanImage();
        int currentLayout = depthImage.getCurrentLayout();
        if (currentLayout != VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL) {
            if (currentLayout != VK10.VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL) {
                throw new IllegalStateException("VulkanMod's main depth attachment is not in its writable layout.");
            }
            try (MemoryStack stack = MemoryStack.stackPush()) {
                VulkanImage.transitionLayout(stack, Renderer.getCommandBuffer(), depthImage,
                        currentLayout, VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL,
                        VK10.VK_PIPELINE_STAGE_EARLY_FRAGMENT_TESTS_BIT | VK10.VK_PIPELINE_STAGE_LATE_FRAGMENT_TESTS_BIT,
                        VK10.VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_READ_BIT | VK10.VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT,
                        VK10.VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT, VK10.VK_ACCESS_SHADER_READ_BIT);
            }
        }

        VulkanImage sampledImage = ((VeyraDepthTextureView) sampledDepthTextureView).sampledImage();
        sampledImage.setCurrentLayout(depthImage.getCurrentLayout());
        return new DepthSamplerState(depthImage, sampledImage);
    }

    private static void restoreDepthSampler() {
        if (!(sampledDepthTexture instanceof VkGpuTexture texture)
                || !(sampledDepthTextureView instanceof VeyraDepthTextureView view)) {
            return;
        }
        restoreDepthSampler(new DepthSamplerState(texture.getVulkanImage(), view.sampledImage()));
    }

    private static void restoreDepthSampler(DepthSamplerState state) {
        VulkanImage depthImage = state.sourceImage();
        int currentLayout = depthImage.getCurrentLayout();
        if (currentLayout == VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL) {
            try (MemoryStack stack = MemoryStack.stackPush()) {
                VulkanImage.transitionLayout(stack, Renderer.getCommandBuffer(), depthImage,
                        currentLayout, VK10.VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL,
                        VK10.VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT, VK10.VK_ACCESS_SHADER_READ_BIT,
                        VK10.VK_PIPELINE_STAGE_EARLY_FRAGMENT_TESTS_BIT | VK10.VK_PIPELINE_STAGE_LATE_FRAGMENT_TESTS_BIT,
                        VK10.VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_READ_BIT | VK10.VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT);
            }
        } else if (currentLayout != VK10.VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL) {
            throw new IllegalStateException("VulkanMod's main depth attachment cannot be restored to its writable layout.");
        }
        state.sampledImage().setCurrentLayout(depthImage.getCurrentLayout());
    }

    private static GpuTextureView sampledDepthTextureView() {
        if (sampledDepthTextureView == null || sampledDepthTextureView.isClosed()) {
            throw new IllegalStateException("VulkanMod's main depth texture view has not been prepared.");
        }
        return sampledDepthTextureView;
    }

    private static String sha256(String source) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(source.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable.", exception);
        }
    }

    private static Map<String, List<UniformValue>> passUniforms(
            PipelineProgram program, int width, int height, FrameUniforms frameUniforms) {
        if (program.uniforms().isEmpty()) {
            return Map.of();
        }
        return Map.of(SHADERPACK_UNIFORMS, uniformValues(program, width, height, frameUniforms));
    }

    private static List<UniformValue> uniformValues(
            PipelineProgram program, int width, int height, FrameUniforms frameUniforms) {
        List<UniformValue> values = new ArrayList<>(program.uniforms().size());
        for (String uniform : program.uniforms()) {
            values.add(switch (uniform) {
                case "viewWidth" -> new UniformValue.FloatUniform(width);
                case "viewHeight" -> new UniformValue.FloatUniform(height);
                case "aspectRatio" -> new UniformValue.FloatUniform((float) width / height);
                case "frameTimeCounter" -> new UniformValue.FloatUniform(frameUniforms.frameTimeCounter());
                case "frameTime" -> new UniformValue.FloatUniform(frameUniforms.frameTime());
                case "frameCounter" -> new UniformValue.IntUniform(frameUniforms.frameCounter());
                case "sunAngle" -> new UniformValue.FloatUniform(frameUniforms.sunAngle());
                case "rainStrength" -> new UniformValue.FloatUniform(frameUniforms.rainStrength());
                case "wetness" -> new UniformValue.FloatUniform(frameUniforms.wetness());
                case "thunderStrength" -> new UniformValue.FloatUniform(frameUniforms.thunderStrength());
                case "worldTime" -> new UniformValue.IntUniform(frameUniforms.worldTime());
                case "worldDay" -> new UniformValue.IntUniform(frameUniforms.worldDay());
                case "eyeAltitude" -> new UniformValue.FloatUniform(frameUniforms.eyeAltitude());
                case "screenBrightness" -> new UniformValue.FloatUniform(frameUniforms.screenBrightness());
                case "blindness" -> new UniformValue.FloatUniform(frameUniforms.blindness());
                case "nightVision" -> new UniformValue.FloatUniform(frameUniforms.nightVision());
                case "darknessFactor" -> new UniformValue.FloatUniform(frameUniforms.darknessFactor());
                case "isEyeInWater" -> new UniformValue.IntUniform(frameUniforms.isEyeInWater());
                case "isSpectator" -> new UniformValue.IntUniform(frameUniforms.isSpectator() ? 1 : 0);
                case "is_sneaking" -> new UniformValue.IntUniform(frameUniforms.isSneaking() ? 1 : 0);
                case "is_sprinting" -> new UniformValue.IntUniform(frameUniforms.isSprinting() ? 1 : 0);
                case "is_on_ground" -> new UniformValue.IntUniform(frameUniforms.isOnGround() ? 1 : 0);
                case "is_hurt" -> new UniformValue.IntUniform(frameUniforms.isHurt() ? 1 : 0);
                case "is_invisible" -> new UniformValue.IntUniform(frameUniforms.isInvisible() ? 1 : 0);
                case "is_burning" -> new UniformValue.IntUniform(frameUniforms.isBurning() ? 1 : 0);
                default -> throw new IllegalArgumentException("Unknown shader pack uniform " + uniform + ".");
            });
        }
        return List.copyOf(values);
    }

    private static boolean usesDynamicUniforms(PipelineProgram program) {
        return program.uniforms().stream().anyMatch(DYNAMIC_UNIFORMS::contains);
    }

    private static void makeDynamicUniformBuffer(PostPass pass, PipelineProgram program, int width, int height) {
        Map<String, GpuBuffer> uniforms = ((PostPassAccessor) pass).veyra$getCustomUniforms();
        GpuBuffer previous = uniforms.get(SHADERPACK_UNIFORMS);
        if (previous == null) {
            throw new IllegalStateException("The shader pass did not create its custom uniform buffer.");
        }

        GpuBuffer dynamic = createDynamicUniformBuffer(program, width, height, FrameUniforms.ZERO);
        uniforms.put(SHADERPACK_UNIFORMS, dynamic);
        previous.close();
    }

    private static GpuBuffer createDynamicUniformBuffer(
            PipelineProgram program, int width, int height, FrameUniforms frameUniforms) {
        List<UniformValue> values = uniformValues(program, width, height, frameUniforms);
        Std140SizeCalculator size = new Std140SizeCalculator();
        values.forEach(value -> value.addSize(size));
        try (MemoryStack stack = MemoryStack.stackPush()) {
            Std140Builder builder = Std140Builder.onStack(stack, size.get());
            values.forEach(value -> value.writeTo(builder));
            return RenderSystem.getDevice().createBuffer(
                    () -> "Veyra shader pack uniforms",
                    GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST,
                    builder.get());
        }
    }

    private static void updateDynamicUniforms(
            ShaderPass shaderPass, int width, int height, FrameUniforms frameUniforms) {
        PipelineProgram program = shaderPass.program();
        if (!usesDynamicUniforms(program)) {
            return;
        }

        GpuBuffer buffer = ((PostPassAccessor) shaderPass.pass()).veyra$getCustomUniforms().get(SHADERPACK_UNIFORMS);
        if (buffer == null || buffer.isClosed()) {
            throw new IllegalStateException("The shader pass custom uniform buffer is unavailable.");
        }
        List<UniformValue> values = uniformValues(program, width, height, frameUniforms);
        Std140SizeCalculator size = new Std140SizeCalculator();
        values.forEach(value -> value.addSize(size));
        try (MemoryStack stack = MemoryStack.stackPush()) {
            Std140Builder builder = Std140Builder.onStack(stack, size.get());
            values.forEach(value -> value.writeTo(builder));
            RenderSystem.getDevice().createCommandEncoder().writeToBuffer(buffer.slice(), builder.get());
        }
    }

    private static FrameUniforms updateFrameUniforms(Minecraft minecraft) {
        long now = System.nanoTime();
        frameTime = previousFrameNanos == 0L ? 0.0F : (now - previousFrameNanos) / 1_000_000_000.0F;
        previousFrameNanos = now;
        frameTimeCounter = (frameTimeCounter + frameTime) % 3600.0F;
        frameCounter = frameCounter >= 720719 ? 0 : frameCounter + 1;

        float partialTick = minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        long dayTime = minecraft.level.getDayTime();
        int worldTime = (int) Math.floorMod(dayTime, 24000L);
        float sunAngle = (worldTime + partialTick) / 24000.0F;
        int worldDay = (int) Math.floorDiv(dayTime, 24000L);
        float rainStrength = minecraft.level.getRainLevel(partialTick);
        float thunderStrength = minecraft.level.getThunderLevel(partialTick);
        if (wetnessLevel != minecraft.level) {
            wetnessLevel = minecraft.level;
            wetnessInitialized = false;
        }
        float wetness = smoothWetness(rainStrength, frameTime);
        var player = minecraft.player;
        float blindness = player.hasEffect(net.minecraft.world.effect.MobEffects.BLINDNESS)
                ? player.getEffectBlendFactor(net.minecraft.world.effect.MobEffects.BLINDNESS, partialTick) : 0.0F;
        float nightVision = player.hasEffect(net.minecraft.world.effect.MobEffects.NIGHT_VISION)
                ? minecraft.gameRenderer.getNightVisionScale(player, partialTick) : 0.0F;
        float darknessFactor = player.hasEffect(net.minecraft.world.effect.MobEffects.DARKNESS)
                ? player.getEffectBlendFactor(net.minecraft.world.effect.MobEffects.DARKNESS, partialTick) : 0.0F;
        int isEyeInWater = player.isEyeInFluid(FluidTags.WATER) ? 1
                : player.isEyeInFluid(FluidTags.LAVA) ? 2 : player.isInPowderSnow ? 3 : 0;
        return new FrameUniforms(frameTimeCounter, frameTime, frameCounter, sunAngle, rainStrength, wetness,
                thunderStrength, worldTime, worldDay, (float) player.getEyeY(),
                (float) (double) minecraft.options.gamma().get(), blindness, nightVision, darknessFactor,
                isEyeInWater, player.isSpectator(), player.isCrouching(), player.isSprinting(), player.onGround(),
                player.hurtTime > 0, player.isInvisible(), player.isOnFire());
    }

    private static float smoothWetness(float rainStrength, float deltaSeconds) {
        if (!wetnessInitialized) {
            smoothedWetness = rainStrength;
            wetnessInitialized = true;
            return smoothedWetness;
        }

        float halfLife = rainStrength > smoothedWetness
                ? WETNESS_RISE_HALF_LIFE_SECONDS : WETNESS_FALL_HALF_LIFE_SECONDS;
        float smoothing = 1.0F - (float) Math.exp(-LN_OF_TWO * Math.max(deltaSeconds, 0.0F) / halfLife);
        smoothedWetness += (rainStrength - smoothedWetness) * smoothing;
        return smoothedWetness;
    }

    private static CachedOrthoProjectionMatrixBuffer projectionBuffer() {
        if (projectionBuffer == null) {
            ShaderManager shaderManager = ((MinecraftAccessor) Minecraft.getInstance()).veyra$getShaderManager();
            projectionBuffer = ((ShaderManagerAccessor) shaderManager).veyra$getPostChainProjectionMatrixBuffer();
        }
        return projectionBuffer;
    }

    private static void ensureSwapTargets(int width, int height) {
        if (swapTargetA == null) {
            swapTargetA = new TextureTarget("Veyra shader pack swap A", width, height, false);
        } else if (swapTargetA.width != width || swapTargetA.height != height) {
            swapTargetA.resize(width, height);
        }
        if (swapTargetB == null) {
            swapTargetB = new TextureTarget("Veyra shader pack swap B", width, height, false);
        } else if (swapTargetB.width != width || swapTargetB.height != height) {
            swapTargetB.resize(width, height);
        }
    }

    private static void replacePipelineSet(PipelineSet next) {
        PipelineSet old = pipelineSet;
        pipelineSet = next;
        if (old != null && old != next) {
            old.close();
        }
        if ((next == null || !next.usesDepthSampler())
                && sampledDepthTextureView != null && !sampledDepthTextureView.isClosed()) {
            sampledDepthTextureView.close();
            sampledDepthTextureView = null;
            sampledDepthTexture = null;
        }
    }

    private static String shortError(Exception exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? exception.getClass().getSimpleName() : message;
    }

    private record AdaptedShader(String source, List<String> uniforms, boolean usesDepthSampler) {
    }

    private record DepthSamplerState(VulkanImage sourceImage, VulkanImage sampledImage) {
    }

    private record PipelineProgram(Identifier shaderId, String fragmentSource, List<String> uniforms,
                                   boolean usesDepthSampler, RenderPipeline pipeline) {
    }

    private record ShaderPass(PostPass pass, PipelineProgram program) {
    }

    private record FrameUniforms(float frameTimeCounter, float frameTime, int frameCounter,
                                 float sunAngle, float rainStrength, float wetness, float thunderStrength,
                                 int worldTime, int worldDay, float eyeAltitude, float screenBrightness,
                                 float blindness, float nightVision, float darknessFactor,
                                 int isEyeInWater, boolean isSpectator, boolean isSneaking, boolean isSprinting,
                                 boolean isOnGround, boolean isHurt, boolean isInvisible, boolean isBurning) {
        private static final FrameUniforms ZERO = new FrameUniforms(0.0F, 0.0F, 0,
                0.0F, 0.0F, 0.0F, 0.0F, 0, 0, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0,
                false, false, false, false, false, false, false);
    }

    private record PipelineSet(List<ShaderPass> shaderPasses, PostPass copyPass,
                               int width, int height, boolean usesDepthSampler) implements AutoCloseable {
        @Override
        public void close() {
            shaderPasses.forEach(shaderPass -> shaderPass.pass().close());
            copyPass.close();
        }
    }

    private record ExternalTextureInput(String samplerName) implements PostPass.Input {
        @Override
        public void addToPass(FramePass pass, Map<Identifier, ResourceHandle<RenderTarget>> targets) {
        }

        @Override
        public GpuTextureView texture(Map<Identifier, ResourceHandle<RenderTarget>> targets) {
            return sampledDepthTextureView();
        }

        @Override
        public boolean bilinear() {
            return false;
        }
    }

}
