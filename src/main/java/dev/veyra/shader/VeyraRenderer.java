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
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.CachedOrthoProjectionMatrixBuffer;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.PostPass;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.ShaderManager;
import net.minecraft.resources.Identifier;
import dev.veyra.VeyraClient;
import dev.veyra.mixin.accessor.MinecraftAccessor;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class VeyraRenderer {
    private static final Identifier FINAL_SHADER_ID = Identifier.fromNamespaceAndPath("veyra", "shaderpack/final");
    private static final Identifier COPY_SHADER_ID = Identifier.fromNamespaceAndPath("veyra", "shaderpack/copy");
    private static final Identifier FINAL_PIPELINE_ID = Identifier.fromNamespaceAndPath("veyra", "pipeline/shaderpack_final");
    private static final Identifier COPY_PIPELINE_ID = Identifier.fromNamespaceAndPath("veyra", "pipeline/shaderpack_copy");
    private static final Identifier SWAP_TARGET_ID = Identifier.fromNamespaceAndPath("veyra", "swap");
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
    private static final RenderPipeline FINAL_PIPELINE = createPipeline(FINAL_PIPELINE_ID, FINAL_SHADER_ID);
    private static final RenderPipeline COPY_PIPELINE = createPipeline(COPY_PIPELINE_ID, COPY_SHADER_ID);

    private static ShaderPackRepository repository;
    private static RenderTarget swapTarget;
    private static CachedOrthoProjectionMatrixBuffer projectionBuffer;
    private static String compiledShader;
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
        try {
            String shader = pack.readFinalShader();
            String adapted = adaptShader(shader);
            compilePipelines(adapted);
            repository.select(pack);
            compiledShader = adapted;
            pipelinesDirty = false;
            renderingEnabled = true;
            lastError = "";
            VeyraClient.LOGGER.info("Enabled final pass from shader pack '{}'.", pack.name());
            return "Enabled " + pack.name() + ".";
        } catch (Exception exception) {
            renderingEnabled = false;
            lastError = shortError(exception);
            VeyraClient.LOGGER.error("Could not enable shader pack '{}'.", pack.name(), exception);
            return "Shader compile failed: " + lastError;
        }
    }

    public static String disable() {
        try {
            repository.select(null);
            renderingEnabled = false;
            lastError = "";
            return "Shader pack disabled.";
        } catch (IOException exception) {
            lastError = shortError(exception);
            return "Could not save the setting: " + lastError;
        }
    }

    public static void invalidatePipelines() {
        pipelinesDirty = true;
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
            ensureSwapTarget(mainTarget.width, mainTarget.height);
            ResourceHandle<RenderTarget> swap = graph.importExternal("veyra:shaderpack-swap", swapTarget);
            Map<Identifier, ResourceHandle<RenderTarget>> targets = new HashMap<>();
            targets.put(PostChain.MAIN_TARGET_ID, main);
            targets.put(SWAP_TARGET_ID, swap);

            PostPass shaderPass = new PostPass(FINAL_PIPELINE, SWAP_TARGET_ID, Map.of(),
                    List.of(new PostPass.TargetInput("In", PostChain.MAIN_TARGET_ID, false, true)));
            PostPass copyPass = new PostPass(COPY_PIPELINE, PostChain.MAIN_TARGET_ID, Map.of(),
                    List.of(new PostPass.TargetInput("In", SWAP_TARGET_ID, false, true)));

            try {
                shaderPass.addToFrame(graph, targets, projectionBuffer().getBuffer(mainTarget.width, mainTarget.height));
                copyPass.addToFrame(graph, targets, projectionBuffer().getBuffer(mainTarget.width, mainTarget.height));
                graph.execute(GraphicsResourceAllocator.UNPOOLED);
            } finally {
                shaderPass.close();
                copyPass.close();
            }
        } catch (Exception exception) {
            renderingEnabled = false;
            lastError = shortError(exception);
            VeyraClient.LOGGER.error("Disabled the shader pass after a rendering failure.", exception);
        }
    }

    private static CachedOrthoProjectionMatrixBuffer projectionBuffer() {
        if (projectionBuffer == null) {
            ShaderManager shaderManager = ((MinecraftAccessor) Minecraft.getInstance()).veyra$getShaderManager();
            projectionBuffer = ((dev.veyra.mixin.accessor.ShaderManagerAccessor) shaderManager)
                    .veyra$getPostChainProjectionMatrixBuffer();
        }
        return projectionBuffer;
    }

    private static void ensureSwapTarget(int width, int height) {
        if (swapTarget == null) {
            swapTarget = new TextureTarget("Veyra shader pack swap", width, height, false);
        } else if (swapTarget.width != width || swapTarget.height != height) {
            swapTarget.resize(width, height);
        }
    }

    private static void ensurePipelines() throws IOException {
        ShaderPack pack = repository.getSelected().orElse(null);
        if (pack == null) {
            renderingEnabled = false;
            return;
        }
        if (!pipelinesDirty && compiledShader != null) {
            return;
        }

        String adapted = adaptShader(pack.readFinalShader());
        compilePipelines(adapted);
        compiledShader = adapted;
        pipelinesDirty = false;
        lastError = "";
    }

    private static void compilePipelines(String finalShader) {
        Minecraft minecraft = Minecraft.getInstance();
        ShaderManager shaderManager = ((MinecraftAccessor) minecraft).veyra$getShaderManager();
        ShaderSource source = (identifier, type) -> {
            if (identifier.equals(FINAL_SHADER_ID) && type == ShaderType.FRAGMENT) {
                return finalShader;
            }
            if (identifier.equals(COPY_SHADER_ID) && type == ShaderType.FRAGMENT) {
                return COPY_FRAGMENT_SHADER;
            }
            return shaderManager.getShader(identifier, type);
        };

        CompiledRenderPipeline finalPipeline = RenderSystem.getDevice().precompilePipeline(FINAL_PIPELINE, source);
        CompiledRenderPipeline copyPipeline = RenderSystem.getDevice().precompilePipeline(COPY_PIPELINE, source);
        if (!finalPipeline.isValid() || !copyPipeline.isValid()) {
            throw new IllegalStateException("VulkanMod could not compile one of the post-processing pipelines.");
        }
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

    private static String adaptShader(String source) throws IOException {
        if (source == null || source.isBlank()) {
            throw new IOException("final.fsh is empty.");
        }
        if (source.length() > 1024 * 1024) {
            throw new IOException("final.fsh exceeds the 1 MiB size limit.");
        }
        if (source.contains("gl_FragData")) {
            throw new IOException("Multiple fragment outputs (gl_FragData) are not supported by the single-target final pass.");
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
                .append("#define texcoord texCoord\n")
                .append("#define gl_FragColor ").append(outputName).append('\n')
                .append("#define texture2D texture\n")
                .append(body);
        return adapted.toString();
    }

    private static String shortError(Exception exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? exception.getClass().getSimpleName() : message;
    }
}
