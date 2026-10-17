package dev.veyra.shader;

import com.mojang.blaze3d.framegraph.FrameGraphBuilder;
import com.mojang.blaze3d.pipeline.CompiledRenderPipeline;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import com.mojang.blaze3d.resource.ResourceHandle;
import com.mojang.blaze3d.shaders.ShaderSource;
import com.mojang.blaze3d.shaders.ShaderType;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.veyra.VeyraClient;
import dev.veyra.mixin.accessor.MinecraftAccessor;
import dev.veyra.mixin.accessor.ShaderManagerAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.CachedOrthoProjectionMatrixBuffer;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.PostPass;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.ShaderManager;
import net.minecraft.resources.Identifier;

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
    private static final Pattern OTHER_COLOR_SAMPLER = Pattern.compile("\\bcolortex([1-9][0-9]*)\\b");
    private static final Pattern DEPTH_SAMPLER = Pattern.compile("\\b(?:depthtex[0-9]*|DepthSampler)\\b");
    private static final Map<String, PipelineProgram> PIPELINE_CACHE = new HashMap<>();
    private static final Set<PipelineProgram> COMPILED_PIPELINES = Collections.newSetFromMap(new IdentityHashMap<>());

    private static ShaderPackRepository repository;
    private static RenderTarget swapTargetA;
    private static RenderTarget swapTargetB;
    private static CachedOrthoProjectionMatrixBuffer projectionBuffer;
    private static PipelineSet pipelineSet;
    private static boolean pipelinesDirty = true;
    private static boolean renderingEnabled;
    private static String lastError = "";

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
            prepared = compilePipelines(pack);
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

        try {
            ensurePipelines();
            RenderTarget mainTarget = minecraft.getMainRenderTarget();
            if (mainTarget.width <= 0 || mainTarget.height <= 0) {
                return;
            }

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
            for (PostPass pass : pipelineSet.shaderPasses()) {
                pass.addToFrame(graph, targets, samplerInfo);
            }
            pipelineSet.copyPass().addToFrame(graph, targets, samplerInfo);
            graph.execute(GraphicsResourceAllocator.UNPOOLED);
        } catch (Exception exception) {
            renderingEnabled = false;
            lastError = shortError(exception);
            VeyraClient.LOGGER.error("Disabled the shader pass after a rendering failure.", exception);
        }
    }

    private static void ensurePipelines() throws IOException {
        if (!pipelinesDirty && pipelineSet != null) {
            return;
        }
        ShaderPack pack = repository.getSelected().orElse(null);
        if (pack == null) {
            renderingEnabled = false;
            replacePipelineSet(null);
            return;
        }
        PipelineSet prepared = compilePipelines(pack);
        replacePipelineSet(prepared);
        pipelinesDirty = false;
        lastError = "";
        VeyraClient.LOGGER.info("Loaded {} post-processing pass(es) from shader pack '{}'.",
                pipelineSet.shaderPasses().size(), pack.name());
    }

    private static PipelineSet compilePipelines(ShaderPack pack) throws IOException {
        List<ShaderPack.Program> sources = pack.readPrograms();
        List<PipelineProgram> programs = new ArrayList<>(sources.size());
        for (ShaderPack.Program source : sources) {
            AdaptedShader adapted = adaptShader(source.source());
            PipelineProgram program = pipelineProgram(source.name(), adapted);
            compilePipeline(program);
            programs.add(program);
        }

        PipelineProgram copyProgram = pipelineProgram("copy", new AdaptedShader(COPY_FRAGMENT_SHADER));
        compilePipeline(copyProgram);

        List<PostPass> passes = new ArrayList<>(programs.size());
        try {
            Identifier inputTarget = PostChain.MAIN_TARGET_ID;
            for (int index = 0; index < programs.size(); index++) {
                PipelineProgram program = programs.get(index);
                Identifier outputTarget = index % 2 == 0 ? SWAP_TARGET_A_ID : SWAP_TARGET_B_ID;
                List<PostPass.Input> inputs = new ArrayList<>();
                inputs.add(new PostPass.TargetInput("In", inputTarget, false, true));
                passes.add(new PostPass(program.pipeline(), outputTarget, Map.of(), inputs));
                inputTarget = outputTarget;
            }
        } catch (RuntimeException exception) {
            passes.forEach(PostPass::close);
            throw exception;
        }
        PostPass copyPass = new PostPass(copyProgram.pipeline(), PostChain.MAIN_TARGET_ID, Map.of(),
                List.of(new PostPass.TargetInput("In", programs.size() % 2 == 1 ? SWAP_TARGET_A_ID : SWAP_TARGET_B_ID, false, true)));
        return new PipelineSet(List.copyOf(passes), copyPass);
    }

    private static PipelineProgram pipelineProgram(String name, AdaptedShader shader) {
        String hash = sha256(name + '\n' + shader.source());
        String cacheKey = name + ':' + hash;
        return PIPELINE_CACHE.computeIfAbsent(cacheKey, ignored -> {
            String suffix = name + '_' + hash;
            Identifier shaderId = Identifier.fromNamespaceAndPath("veyra", "shaderpack/" + suffix);
            Identifier pipelineId = Identifier.fromNamespaceAndPath("veyra", "pipeline/shaderpack/" + suffix);
            return new PipelineProgram(shaderId, shader.source(), createPipeline(pipelineId, shaderId));
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

    private static RenderPipeline createPipeline(Identifier pipeline, Identifier fragmentShader) {
        return RenderPipeline.builder(RenderPipelines.POST_PROCESSING_SNIPPET)
                .withFragmentShader(fragmentShader)
                .withVertexShader(BLIT_VERTEX_ID)
                .withLocation(pipeline)
                .withSampler("InSampler")
                .withUniform("SamplerInfo", UniformType.UNIFORM_BUFFER)
                .build();
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
        Matcher depthSampler = DEPTH_SAMPLER.matcher(source);
        if (depthSampler.find()) {
            throw new IOException("Veyra does not currently support depth texture sampling on VulkanMod.");
        }

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
        adapted.append("uniform sampler2D InSampler;\n")
                .append("#define colortex0 InSampler\n")
                .append("#define Sampler0 InSampler\n")
                .append("#define texcoord texCoord\n")
                .append("#define gl_FragColor ").append(outputName).append('\n')
                .append("#define texture2D texture\n");
        adapted.append(body);
        return new AdaptedShader(adapted.toString());
    }

    private static String sha256(String source) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(source.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable.", exception);
        }
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
    }

    private static String shortError(Exception exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? exception.getClass().getSimpleName() : message;
    }

    private record AdaptedShader(String source) {
    }

    private record PipelineProgram(Identifier shaderId, String fragmentSource, RenderPipeline pipeline) {
    }

    private record PipelineSet(List<PostPass> shaderPasses, PostPass copyPass) implements AutoCloseable {
        @Override
        public void close() {
            shaderPasses.forEach(PostPass::close);
            copyPass.close();
        }
    }

}
