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
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import dev.veyra.VeyraClient;
import dev.veyra.mixin.accessor.MinecraftAccessor;
import dev.veyra.mixin.accessor.PostPassAccessor;
import dev.veyra.mixin.accessor.ShaderManagerAccessor;
import dev.veyra.vulkan.VeyraDepthTextureView;
import net.vulkanmod.Initializer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.CachedOrthoProjectionMatrixBuffer;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.PostPass;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.ShaderDefines;
import net.minecraft.client.renderer.ShaderManager;
import net.minecraft.client.renderer.UniformValue;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.FluidTags;
import net.vulkanmod.gl.VkGlTexture;
import net.vulkanmod.render.engine.VkGpuTexture;
import net.vulkanmod.render.shader.PipelineConfigs;
import net.vulkanmod.render.shader.PipelineManager;
import net.vulkanmod.render.shader.ShaderLoadUtil;
import net.vulkanmod.render.vertex.TerrainRenderType;
import net.vulkanmod.vulkan.Renderer;
import net.vulkanmod.vulkan.shader.GraphicsPipeline;
import net.vulkanmod.vulkan.shader.Pipeline;
import net.vulkanmod.vulkan.shader.PipelineConfig;
import net.vulkanmod.vulkan.shader.SPIRVUtils;
import net.vulkanmod.vulkan.shader.descriptor.UBO;
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
    private static final Identifier COLORTEX1_TARGET_A_ID = Identifier.fromNamespaceAndPath("veyra", "colortex1_a");
    private static final Identifier COLORTEX1_TARGET_B_ID = Identifier.fromNamespaceAndPath("veyra", "colortex1_b");
    private static final String SHADERPACK_UNIFORMS = "VeyraParameters";
    private static final String COLORTEX1_INPUT_NAME = "VeyraColortex1";
    private static final String COLORTEX1_INPUT_SAMPLER = COLORTEX1_INPUT_NAME + "Sampler";
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
    private static final Pattern EXPLICIT_OUTPUT_LOCATION = Pattern.compile("layout\\s*\\(\\s*location\\s*=\\s*(\\d+)\\s*\\)\\s*out\\s+vec4\\s+[A-Za-z_][A-Za-z0-9_]*\\s*;");
    private static final Pattern GL_FRAG_DATA_INDEX = Pattern.compile("\\bgl_FragData\\s*\\[\\s*(\\d+)\\s*\\]");
    private static final Pattern RENDERTARGETS_DIRECTIVE = Pattern.compile("(?is)/\\*\\s*RENDERTARGETS\\s*:\\s*([0-9,\\s]+)\\s*\\*/");
    private static final Pattern DRAWBUFFERS_DIRECTIVE = Pattern.compile("(?is)/\\*\\s*DRAWBUFFERS\\s*:\\s*([0-9]+)\\s*\\*/");
    private static final Pattern COMMENTS = Pattern.compile("(?s)/\\*.*?\\*/|//[^\\r\\n]*");
    private static final Pattern LOCATION_LAYOUT = Pattern.compile("layout\\s*\\(\\s*location\\s*=\\s*\\d+\\s*\\)\\s*");
    private static final Pattern VARYING_DECLARATION = Pattern.compile("(?m)^\\s*(?:varying|in)\\s+vec2\\s+(?:texcoord|texCoord)\\s*;\\s*$");
    private static final Pattern MAIN_SAMPLER_DECLARATION = Pattern.compile("(?m)^\\s*uniform\\s+sampler2D\\s+(?:colortex0|Sampler0|InSampler)\\s*;\\s*$");
    private static final Pattern COLORTEX1_SAMPLER_DECLARATION = Pattern.compile("(?m)^\\s*uniform\\s+(?:(?:lowp|mediump|highp)\\s+)?sampler2D\\s+colortex1\\s*;[^\\r\\n]*(?:\\r?\\n|$)");
    private static final Pattern SUPPORTED_UNIFORM_DECLARATION = Pattern.compile("(?m)^\\s*uniform\\s+(?:float|int|bool)\\s+(?:viewWidth|viewHeight|aspectRatio|frameTimeCounter|frameTime|frameCounter|sunAngle|rainStrength|wetness|thunderStrength|worldTime|worldDay|eyeAltitude|screenBrightness|blindness|nightVision|darknessFactor|isEyeInWater|isSpectator|is_sneaking|is_sprinting|is_on_ground|is_hurt|is_invisible|is_burning)\\s*;[^\\r\\n]*(?:\\r?\\n|$)");
    private static final Pattern UNSUPPORTED_COLOR_SAMPLER = Pattern.compile("\\bcolortex(?:[2-9]|[1-9][0-9]+)\\b");
    private static final Pattern COLORTEX1_SAMPLER = Pattern.compile("\\bcolortex1\\b");
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
    private static final Map<EntityPipelineKey, RenderPipeline> ENTITY_PIPELINE_CACHE = new HashMap<>();
    private static final Map<ParticlePipelineKey, RenderPipeline> PARTICLE_PIPELINE_CACHE = new HashMap<>();
    private static final Map<WeatherPipelineKey, RenderPipeline> WEATHER_PIPELINE_CACHE = new HashMap<>();
    private static final Map<BeaconBeamPipelineKey, RenderPipeline> BEACON_BEAM_PIPELINE_CACHE = new HashMap<>();

    private static ShaderPackRepository repository;
    private static RenderTarget swapTargetA;
    private static RenderTarget swapTargetB;
    private static RenderTarget colortex1TargetA;
    private static RenderTarget colortex1TargetB;
    private static Identifier currentColortex1Target = COLORTEX1_TARGET_A_ID;
    private static boolean clearColortex1Targets;
    private static CachedOrthoProjectionMatrixBuffer projectionBuffer;
    private static PipelineSet pipelineSet;
    private static TerrainPipelineSet terrainPipelineSet;
    private static EntityPipelineSet entityPipelineSet;
    private static ParticlePipelineSet particlePipelineSet;
    private static WeatherPipelineSet weatherPipelineSet;
    private static BeaconBeamPipelineSet beaconBeamPipelineSet;
    private static GraphicsPipeline cloudPipeline;
    private static GpuTexture sampledDepthTexture;
    private static GpuTextureView sampledDepthTextureView;
    private static boolean pipelinesDirty = true;
    private static boolean terrainPipelineDirty = true;
    private static boolean entityPipelineDirty = true;
    private static boolean particlePipelineDirty = true;
    private static boolean weatherPipelineDirty = true;
    private static boolean beaconBeamPipelineDirty = true;
    private static boolean cloudPipelineDirty = true;
    private static Boolean previousUniqueOpaqueLayer;
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
        renderingEnabled = packRepository.getSelected()
                .filter(ShaderPack::hasPostProcessing)
                .isPresent();
        terrainPipelineDirty = packRepository.getSelected()
                .map(ShaderPack::hasTerrainFragment)
                .orElse(false);
        entityPipelineDirty = packRepository.getSelected()
                .map(ShaderPack::hasEntityFragment)
                .orElse(false);
        particlePipelineDirty = packRepository.getSelected()
                .map(ShaderPack::hasParticleFragment)
                .orElse(false);
        weatherPipelineDirty = packRepository.getSelected()
                .map(ShaderPack::hasWeatherFragment)
                .orElse(false);
        beaconBeamPipelineDirty = packRepository.getSelected()
                .map(ShaderPack::hasBeaconBeamFragment)
                .orElse(false);
        cloudPipelineDirty = packRepository.getSelected()
                .map(ShaderPack::hasCloudFragment)
                .orElse(false);
        updateTerrainLayerMode(packRepository.getSelected().orElse(null));
    }

    public static Optional<ShaderPack> selectedPack() {
        return repository == null ? Optional.empty() : repository.getSelected();
    }

    public static boolean isRenderingEnabled() {
        return renderingEnabled && selectedPack().isPresent();
    }

    public static GraphicsPipeline getTerrainPipeline(TerrainRenderType terrainRenderType) {
        ShaderPack pack = selectedPack().orElse(null);
        if (pack == null || !pack.hasTerrainFragment() || terrainRenderType == null) {
            return null;
        }
        updateTerrainLayerMode(pack);
        String programName = resolveTerrainProgram(pack, terrainRenderType);
        if (programName == null) {
            return null;
        }
        if (!terrainPipelineDirty) {
            return terrainPipelineSet == null ? null : terrainPipelineSet.pipelines().get(programName);
        }

        terrainPipelineDirty = false;
        try {
            replaceTerrainPipelines(compileTerrainPipelines(pack));
            lastError = "";
            VeyraClient.LOGGER.info("Loaded terrain shader programs {} from shader pack '{}'.",
                    terrainPipelineSet.pipelines().keySet(), pack.name());
        } catch (Exception exception) {
            lastError = shortError(exception);
            VeyraClient.LOGGER.error("Could not load terrain shader programs from shader pack '{}'.", pack.name(), exception);
        }
        return terrainPipelineSet == null ? null : terrainPipelineSet.pipelines().get(programName);
    }

    public static GraphicsPipeline getCloudsPipeline() {
        ShaderPack pack = selectedPack().orElse(null);
        if (pack == null || !pack.hasCloudFragment()) {
            return null;
        }
        if (!cloudPipelineDirty) {
            return cloudPipeline;
        }

        cloudPipelineDirty = false;
        try {
            replaceCloudPipeline(compileCloudPipeline(pack));
            lastError = "";
            VeyraClient.LOGGER.info("Loaded gbuffers_clouds from shader pack '{}'.", pack.name());
        } catch (Exception exception) {
            lastError = shortError(exception);
            VeyraClient.LOGGER.error("Could not load gbuffers_clouds from shader pack '{}'.", pack.name(), exception);
        }
        return cloudPipeline;
    }

    public static RenderPipeline getEntityPipeline(RenderPipeline original) {
        ShaderPack pack = selectedPack().orElse(null);
        if (pack == null || !pack.hasEntityFragment() || !isEntityRenderPipeline(original)) {
            return null;
        }
        if (entityPipelineDirty) {
            entityPipelineDirty = false;
            try {
                replaceEntityPipelines(compileEntityPipelines(pack));
                lastError = "";
                VeyraClient.LOGGER.info("Loaded {} entity render pipelines from shader pack '{}'.",
                        entityPipelineSet.pipelines().size(), pack.name());
            } catch (Exception exception) {
                lastError = shortError(exception);
                VeyraClient.LOGGER.error("Could not load entity shader programs from shader pack '{}'.", pack.name(), exception);
                return null;
            }
        }
        return entityPipelineSet == null ? null : entityPipelineSet.pipelines().get(original);
    }

    public static RenderPipeline getBeaconBeamPipeline(RenderPipeline original) {
        ShaderPack pack = selectedPack().orElse(null);
        if (pack == null || !pack.hasBeaconBeamFragment() || !isBeaconBeamPipeline(original)) {
            return null;
        }
        if (beaconBeamPipelineDirty) {
            beaconBeamPipelineDirty = false;
            try {
                replaceBeaconBeamPipelines(compileBeaconBeamPipelines(pack));
                lastError = "";
                VeyraClient.LOGGER.info("Loaded {} beacon beam render pipelines from shader pack '{}'.",
                        beaconBeamPipelineSet.pipelines().size(), pack.name());
            } catch (Exception exception) {
                lastError = shortError(exception);
                VeyraClient.LOGGER.error("Could not load beacon beam shader program from shader pack '{}'.", pack.name(), exception);
                return null;
            }
        }
        return beaconBeamPipelineSet == null ? null : beaconBeamPipelineSet.pipelines().get(original);
    }

    public static RenderPipeline getParticlePipeline(RenderPipeline original) {
        ShaderPack pack = selectedPack().orElse(null);
        if (pack == null || !pack.hasParticleFragment() || !isParticlePipeline(original)) {
            return null;
        }
        if (particlePipelineDirty) {
            particlePipelineDirty = false;
            try {
                replaceParticlePipelines(compileParticlePipelines(pack));
                lastError = "";
                VeyraClient.LOGGER.info("Loaded {} particle render pipelines from shader pack '{}'.",
                        particlePipelineSet.pipelines().size(), pack.name());
            } catch (Exception exception) {
                lastError = shortError(exception);
                VeyraClient.LOGGER.error("Could not load particle shader programs from shader pack '{}'.", pack.name(), exception);
                return null;
            }
        }
        return particlePipelineSet == null ? null : particlePipelineSet.pipelines().get(original);
    }

    public static RenderPipeline getWeatherPipeline(RenderPipeline original) {
        ShaderPack pack = selectedPack().orElse(null);
        if (pack == null || !pack.hasWeatherFragment() || !isWeatherPipeline(original)) {
            return null;
        }
        if (weatherPipelineDirty) {
            weatherPipelineDirty = false;
            try {
                replaceWeatherPipelines(compileWeatherPipelines(pack));
                lastError = "";
                VeyraClient.LOGGER.info("Loaded {} weather render pipelines from shader pack '{}'.",
                        weatherPipelineSet.pipelines().size(), pack.name());
            } catch (Exception exception) {
                lastError = shortError(exception);
                VeyraClient.LOGGER.error("Could not load gbuffers_weather from shader pack '{}'.", pack.name(), exception);
                return null;
            }
        }
        return weatherPipelineSet == null ? null : weatherPipelineSet.pipelines().get(original);
    }

    public static String getLastError() {
        return lastError;
    }

    public static void onClientShutdown() {
        restoreUniqueOpaqueLayer();
        replaceEntityPipelines(null);
        replaceParticlePipelines(null);
        replaceWeatherPipelines(null);
        replaceBeaconBeamPipelines(null);
        replaceCloudPipeline(null);
    }

    public static String select(ShaderPack pack) {
        PipelineSet prepared = null;
        TerrainPipelineSet preparedTerrain = null;
        EntityPipelineSet preparedEntities = null;
        ParticlePipelineSet preparedParticles = null;
        WeatherPipelineSet preparedWeather = null;
        BeaconBeamPipelineSet preparedBeaconBeams = null;
        GraphicsPipeline preparedClouds = null;
        try {
            RenderTarget mainTarget = Minecraft.getInstance().getMainRenderTarget();
            prepared = compilePipelines(pack, mainTarget.width, mainTarget.height);
            if (pack.hasTerrainFragment()) {
                preparedTerrain = compileTerrainPipelines(pack);
            }
            if (pack.hasEntityFragment()) {
                preparedEntities = compileEntityPipelines(pack);
            }
            if (pack.hasParticleFragment()) {
                preparedParticles = compileParticlePipelines(pack);
            }
            if (pack.hasWeatherFragment()) {
                preparedWeather = compileWeatherPipelines(pack);
            }
            if (pack.hasBeaconBeamFragment()) {
                preparedBeaconBeams = compileBeaconBeamPipelines(pack);
            }
            if (pack.hasCloudFragment()) {
                preparedClouds = compileCloudPipeline(pack);
            }
            repository.select(pack);
            updateTerrainLayerMode(pack);
            replacePipelineSet(prepared);
            prepared = null;
            replaceTerrainPipelines(preparedTerrain);
            preparedTerrain = null;
            replaceEntityPipelines(preparedEntities);
            preparedEntities = null;
            replaceParticlePipelines(preparedParticles);
            preparedParticles = null;
            replaceWeatherPipelines(preparedWeather);
            preparedWeather = null;
            replaceBeaconBeamPipelines(preparedBeaconBeams);
            preparedBeaconBeams = null;
            replaceCloudPipeline(preparedClouds);
            preparedClouds = null;
            pipelinesDirty = false;
            terrainPipelineDirty = false;
            entityPipelineDirty = false;
            particlePipelineDirty = false;
            weatherPipelineDirty = false;
            beaconBeamPipelineDirty = false;
            cloudPipelineDirty = false;
            renderingEnabled = pipelineSet.passCount() > 0;
            lastError = "";
            boolean hasTerrainPipelines = terrainPipelineSet != null && !terrainPipelineSet.pipelines().isEmpty();
            int terrainProgramCount = hasTerrainPipelines ? terrainPipelineSet.pipelines().size() : 0;
            int entityPipelineCount = entityPipelineSet == null ? 0 : entityPipelineSet.pipelines().size();
            int particlePipelineCount = particlePipelineSet == null ? 0 : particlePipelineSet.pipelines().size();
            int weatherPipelineCount = weatherPipelineSet == null ? 0 : weatherPipelineSet.pipelines().size();
            int beaconBeamPipelineCount = beaconBeamPipelineSet == null ? 0 : beaconBeamPipelineSet.pipelines().size();
            VeyraClient.LOGGER.info("Enabled {} post-processing pass(es), {} terrain program(s), {} entity pipeline(s), {} particle pipeline(s), {} weather pipeline(s), {} beacon beam pipeline(s), clouds={} from shader pack '{}'.",
                    pipelineSet.passCount(), terrainProgramCount, entityPipelineCount, particlePipelineCount,
                    weatherPipelineCount, beaconBeamPipelineCount, cloudPipeline != null, pack.name());
            List<String> features = new ArrayList<>();
            if (pipelineSet.passCount() > 0) {
                features.add(pipelineSet.passCount() + " post passes");
            }
            if (terrainProgramCount > 0) {
                features.add(terrainProgramCount + " terrain programs");
            }
            if (cloudPipeline != null) {
                features.add("cloud shader");
            }
            if (entityPipelineCount > 0) {
                features.add(entityPipelineCount + " entity pipelines");
            }
            if (particlePipelineCount > 0) {
                features.add(particlePipelineCount + " particle pipelines");
            }
            if (weatherPipelineCount > 0) {
                features.add(weatherPipelineCount + " weather pipelines");
            }
            if (beaconBeamPipelineCount > 0) {
                features.add(beaconBeamPipelineCount + " beacon beam pipelines");
            }
            return "Enabled " + pack.name() + " (" + String.join(", ", features) + ").";
        } catch (Exception exception) {
            if (prepared != null) {
                prepared.close();
            }
            if (preparedTerrain != null) {
                preparedTerrain.close();
            }
            preparedEntities = null;
            preparedParticles = null;
            preparedWeather = null;
            preparedBeaconBeams = null;
            if (preparedClouds != null) {
                preparedClouds.scheduleCleanUp();
            }
            lastError = shortError(exception);
            VeyraClient.LOGGER.error("Could not enable shader pack '{}'.", pack.name(), exception);
            return "Shader compile failed: " + lastError;
        }
    }

    public static String disable() {
        try {
            repository.select(null);
            updateTerrainLayerMode(null);
            renderingEnabled = false;
            pipelinesDirty = false;
            terrainPipelineDirty = false;
            entityPipelineDirty = false;
            particlePipelineDirty = false;
            weatherPipelineDirty = false;
            beaconBeamPipelineDirty = false;
            replacePipelineSet(null);
            replaceTerrainPipelines(null);
            replaceEntityPipelines(null);
            replaceParticlePipelines(null);
            replaceWeatherPipelines(null);
            replaceBeaconBeamPipelines(null);
            replaceCloudPipeline(null);
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
        replaceTerrainPipelines(null);
        replaceEntityPipelines(null);
        replaceParticlePipelines(null);
        replaceWeatherPipelines(null);
        replaceBeaconBeamPipelines(null);
        replaceCloudPipeline(null);
        terrainPipelineDirty = selectedPack().map(ShaderPack::hasTerrainFragment).orElse(false);
        entityPipelineDirty = selectedPack().map(ShaderPack::hasEntityFragment).orElse(false);
        particlePipelineDirty = selectedPack().map(ShaderPack::hasParticleFragment).orElse(false);
        weatherPipelineDirty = selectedPack().map(ShaderPack::hasWeatherFragment).orElse(false);
        beaconBeamPipelineDirty = selectedPack().map(ShaderPack::hasBeaconBeamFragment).orElse(false);
        cloudPipelineDirty = selectedPack().map(ShaderPack::hasCloudFragment).orElse(false);
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
            ensureSwapTargets(mainTarget.width, mainTarget.height);
            if (pipelineSet.usesColortex1()) {
                ensureColortex1Targets(mainTarget.width, mainTarget.height);
            }
            FramePlan framePlan = pipelineSet.framePlan(currentColortex1Target);

            FrameGraphBuilder graph = new FrameGraphBuilder();
            ResourceHandle<RenderTarget> main = graph.importExternal("main", mainTarget);
            ResourceHandle<RenderTarget> swapA = graph.importExternal("veyra:shaderpack-swap-a", swapTargetA);
            ResourceHandle<RenderTarget> swapB = graph.importExternal("veyra:shaderpack-swap-b", swapTargetB);
            Map<Identifier, ResourceHandle<RenderTarget>> targets = new HashMap<>();
            targets.put(PostChain.MAIN_TARGET_ID, main);
            targets.put(SWAP_TARGET_A_ID, swapA);
            targets.put(SWAP_TARGET_B_ID, swapB);
            if (pipelineSet.usesColortex1()) {
                ensureColortex1Targets(mainTarget.width, mainTarget.height);
                targets.put(COLORTEX1_TARGET_A_ID,
                        graph.importExternal("veyra:shaderpack-colortex1-a", colortex1TargetA));
                targets.put(COLORTEX1_TARGET_B_ID,
                        graph.importExternal("veyra:shaderpack-colortex1-b", colortex1TargetB));
            }

            var samplerInfo = projectionBuffer().getBuffer(mainTarget.width, mainTarget.height);
            FrameUniforms frameUniforms = updateFrameUniforms(minecraft);
            for (ShaderPass shaderPass : framePlan.shaderPasses()) {
                updateDynamicUniforms(shaderPass, mainTarget.width, mainTarget.height, frameUniforms);
                shaderPass.pass().addToFrame(graph, targets, samplerInfo);
            }
            if (framePlan.copyPass() != null) {
                framePlan.copyPass().addToFrame(graph, targets, samplerInfo);
            }
            DepthSamplerState depthSamplerState = null;
            if (pipelineSet.usesDepthSampler()) {
                depthSampling = true;
                depthSamplerState = prepareDepthSampler();
            }
            graph.execute(GraphicsResourceAllocator.UNPOOLED);
            if (depthSamplerState != null) {
                restoreDepthSampler(depthSamplerState);
            }
            if (pipelineSet.usesColortex1()) {
                currentColortex1Target = framePlan.colortex1OutputTarget();
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
        renderingEnabled = pipelineSet.passCount() > 0;
        lastError = "";
        VeyraClient.LOGGER.info("Loaded {} post-processing pass(es) from shader pack '{}'.",
                pipelineSet.passCount(), pack.name());
    }

    private static PipelineSet compilePipelines(ShaderPack pack, int width, int height) throws IOException {
        List<ShaderPack.Program> sources = pack.readPrograms();
        List<ProgramGroup> programs = new ArrayList<>(sources.size());
        boolean usesDepthSampler = false;
        boolean usesColortex1 = false;
        for (ShaderPack.Program source : sources) {
            List<PipelineProgram> outputs = new ArrayList<>();
            for (AdaptedShader adapted : adaptShader(source.source())) {
                String variantName = source.name() + "_colortex" + adapted.outputTarget();
                PipelineProgram program = pipelineProgram(variantName, adapted);
                compilePipeline(program);
                outputs.add(program);
                usesDepthSampler |= adapted.usesDepthSampler();
                usesColortex1 |= adapted.usesColortex1Sampler() || adapted.outputTarget() == 1;
            }
            programs.add(new ProgramGroup(List.copyOf(outputs)));
        }

        PipelineProgram copyProgram = null;
        if (!programs.isEmpty()) {
            copyProgram = pipelineProgram("copy",
                    new AdaptedShader(COPY_FRAGMENT_SHADER, List.of(), false, false, 0));
            compilePipeline(copyProgram);
        }

        FramePlan firstPlan = null;
        FramePlan secondPlan = null;
        try {
            firstPlan = buildFramePlan(programs, copyProgram, width, height, COLORTEX1_TARGET_A_ID);
            secondPlan = usesColortex1
                    ? buildFramePlan(programs, copyProgram, width, height, COLORTEX1_TARGET_B_ID)
                    : firstPlan;
            return new PipelineSet(firstPlan, secondPlan, width, height, usesDepthSampler, usesColortex1);
        } catch (RuntimeException exception) {
            if (firstPlan != null) {
                firstPlan.close();
            }
            if (secondPlan != null && secondPlan != firstPlan) {
                secondPlan.close();
            }
            throw exception;
        }
    }

    private static TerrainPipelineSet compileTerrainPipelines(ShaderPack pack) throws IOException {
        if (PipelineManager.getTerrainVertexFormat() == null) {
            throw new IOException("VulkanMod has not initialized its terrain vertex format yet.");
        }

        String shaderPath = ShaderLoadUtil.resolveShaderPath("basic");
        String vertexSource = ShaderLoadUtil.loadShader(shaderPath, "terrain/terrain.vsh");
        vertexSource = TerrainShaderAdapter.adaptVertex(vertexSource);
        Map<String, GraphicsPipeline> pipelines = new HashMap<>();
        try {
            for (String programName : List.of(
                    "gbuffers_terrain", "gbuffers_terrain_solid", "gbuffers_terrain_cutout", "gbuffers_water")) {
                if (!pack.terrainPrograms().contains(programName)) {
                    continue;
                }
                String fragmentSource = pack.readTerrainFragment(programName)
                        .orElseThrow(() -> new IOException("The pack no longer contains " + programName + ".fsh."));
                fragmentSource = TerrainShaderAdapter.adaptFragment(fragmentSource);

                Pipeline.Builder builder = new Pipeline.Builder(
                        PipelineManager.getTerrainVertexFormat(), "veyra_" + programName);
                builder.applyConfig(PipelineConfigs.TERRAIN);
                builder.setShaderSrc(SPIRVUtils.ShaderKind.VERTEX_SHADER, vertexSource);
                builder.setShaderSrc(SPIRVUtils.ShaderKind.FRAGMENT_SHADER, fragmentSource);
                GraphicsPipeline pipeline = builder.createGraphicsPipeline();
                for (UBO ubo : pipeline.getBuffers()) {
                    ubo.setUseGlobalBuffer(true);
                }
                pipelines.put(programName, pipeline);
            }
            return new TerrainPipelineSet(Map.copyOf(pipelines));
        } catch (IOException | RuntimeException exception) {
            pipelines.values().forEach(GraphicsPipeline::scheduleCleanUp);
            throw exception;
        }
    }

    private static GraphicsPipeline compileCloudPipeline(ShaderPack pack) throws IOException {
        String shaderPath = ShaderLoadUtil.resolveShaderPath("basic/clouds");
        String vertexSource = ShaderLoadUtil.loadShader(shaderPath, "clouds.vsh");
        String fragmentSource = pack.readCloudFragment()
                .orElseThrow(() -> new IOException("The pack no longer contains gbuffers_clouds.fsh."));
        fragmentSource = CloudShaderAdapter.adaptFragment(fragmentSource);
        PipelineConfig config = PipelineConfig.fromJson("clouds", ShaderLoadUtil.getJsonConfig(shaderPath, "clouds"));

        Pipeline.Builder builder = new Pipeline.Builder(DefaultVertexFormat.POSITION_COLOR, "veyra_clouds");
        builder.applyConfig(config);
        builder.setShaderSrc(SPIRVUtils.ShaderKind.VERTEX_SHADER, vertexSource);
        builder.setShaderSrc(SPIRVUtils.ShaderKind.FRAGMENT_SHADER, fragmentSource);
        GraphicsPipeline pipeline = builder.createGraphicsPipeline();
        for (UBO ubo : pipeline.getBuffers()) {
            ubo.setUseGlobalBuffer(true);
        }
        return pipeline;
    }

    private static EntityPipelineSet compileEntityPipelines(ShaderPack pack) throws IOException {
        List<RenderPipeline> candidates = RenderPipelines.getStaticPipelines().stream()
                .filter(VeyraRenderer::isEntityRenderPipeline)
                .toList();
        Map<RenderPipeline, RenderPipeline> pipelines = new IdentityHashMap<>();
        for (RenderPipeline original : candidates) {
            String programName = resolveEntityProgram(pack, original);
            if (programName == null) {
                continue;
            }
            String fragmentSource = pack.readEntityFragment(programName)
                    .orElseThrow(() -> new IOException("The pack no longer contains " + programName + ".fsh."));
            fragmentSource = EntityShaderAdapter.adaptFragment(fragmentSource, programName);
            String sourceHash = sha256(programName + '\n' + fragmentSource);
            EntityPipelineKey cacheKey = new EntityPipelineKey(original, programName, sourceHash);
            RenderPipeline entityPipeline = ENTITY_PIPELINE_CACHE.get(cacheKey);
            if (entityPipeline == null) {
                entityPipeline = compileEntityPipeline(original, fragmentSource, sourceHash);
                ENTITY_PIPELINE_CACHE.put(cacheKey, entityPipeline);
            }
            pipelines.put(original, entityPipeline);
        }
        if (pipelines.isEmpty()) {
            throw new IOException("VulkanMod's Minecraft entity render pipelines were not found.");
        }
        return new EntityPipelineSet(pipelines);
    }

    private static boolean isEntityRenderPipeline(RenderPipeline pipeline) {
        if (pipeline == null
                || !pipeline.getFragmentShader().equals(Identifier.withDefaultNamespace("core/entity"))) {
            return false;
        }
        String location = pipeline.getLocation().getPath();
        return location.startsWith("pipeline/entity_")
                || location.startsWith("pipeline/armor_")
                || location.startsWith("pipeline/item_entity_")
                || location.equals("pipeline/eyes");
    }

    private static String resolveEntityProgram(ShaderPack pack, RenderPipeline pipeline) {
        if (pipeline.getLocation().getPath().equals("pipeline/eyes")
                && pack.entityPrograms().contains("gbuffers_spidereyes")) {
            return "gbuffers_spidereyes";
        }
        if (pipeline.getBlendFunction().isPresent()
                && pack.entityPrograms().contains("gbuffers_entities_translucent")) {
            return "gbuffers_entities_translucent";
        }
        return pack.entityPrograms().contains("gbuffers_entities") ? "gbuffers_entities" : null;
    }

    private static RenderPipeline compileEntityPipeline(
            RenderPipeline original, String fragmentSource, String sourceHash) throws IOException {
        Identifier fragmentShader = Identifier.fromNamespaceAndPath(
                "veyra", "shaderpack/entities/" + sourceHash);
        RenderPipeline pipeline = copyPipelineWithFragment(original, fragmentShader);
        ShaderManager shaderManager = ((MinecraftAccessor) Minecraft.getInstance()).veyra$getShaderManager();
        ShaderSource source = (identifier, type) -> {
            if (identifier.equals(fragmentShader) && type == ShaderType.FRAGMENT) {
                return fragmentSource;
            }
            return shaderManager.getShader(identifier, type);
        };
        CompiledRenderPipeline compiled = RenderSystem.getDevice().precompilePipeline(pipeline, source);
        if (!compiled.isValid()) {
            throw new IOException("VulkanMod could not compile " + original.getLocation()
                    + " with the selected entity shader.");
        }
        return pipeline;
    }

    private static BeaconBeamPipelineSet compileBeaconBeamPipelines(ShaderPack pack) throws IOException {
        List<RenderPipeline> candidates = RenderPipelines.getStaticPipelines().stream()
                .filter(VeyraRenderer::isBeaconBeamPipeline)
                .toList();
        String fragmentSource = pack.readBeaconBeamFragment()
                .orElseThrow(() -> new IOException("The pack no longer contains gbuffers_beaconbeam.fsh."));
        fragmentSource = BeaconBeamShaderAdapter.adaptFragment(fragmentSource);
        String sourceHash = sha256("gbuffers_beaconbeam\n" + fragmentSource);
        Map<RenderPipeline, RenderPipeline> pipelines = new IdentityHashMap<>();
        for (RenderPipeline original : candidates) {
            BeaconBeamPipelineKey cacheKey = new BeaconBeamPipelineKey(original, sourceHash);
            RenderPipeline beaconPipeline = BEACON_BEAM_PIPELINE_CACHE.get(cacheKey);
            if (beaconPipeline == null) {
                beaconPipeline = compileBeaconBeamPipeline(original, fragmentSource, sourceHash);
                BEACON_BEAM_PIPELINE_CACHE.put(cacheKey, beaconPipeline);
            }
            pipelines.put(original, beaconPipeline);
        }
        if (pipelines.isEmpty()) {
            throw new IOException("VulkanMod's Minecraft beacon beam render pipelines were not found.");
        }
        return new BeaconBeamPipelineSet(pipelines);
    }

    private static boolean isBeaconBeamPipeline(RenderPipeline pipeline) {
        return pipeline != null
                && pipeline.getFragmentShader().equals(Identifier.withDefaultNamespace("core/rendertype_beacon_beam"))
                && pipeline.getLocation().getPath().startsWith("pipeline/beacon_beam_");
    }

    private static RenderPipeline compileBeaconBeamPipeline(
            RenderPipeline original, String fragmentSource, String sourceHash) throws IOException {
        Identifier fragmentShader = Identifier.fromNamespaceAndPath(
                "veyra", "shaderpack/beaconbeam/" + sourceHash);
        RenderPipeline pipeline = copyPipelineWithFragment(original, fragmentShader);
        ShaderManager shaderManager = ((MinecraftAccessor) Minecraft.getInstance()).veyra$getShaderManager();
        ShaderSource source = (identifier, type) -> {
            if (identifier.equals(fragmentShader) && type == ShaderType.FRAGMENT) {
                return fragmentSource;
            }
            return shaderManager.getShader(identifier, type);
        };
        CompiledRenderPipeline compiled = RenderSystem.getDevice().precompilePipeline(pipeline, source);
        if (!compiled.isValid()) {
            throw new IOException("VulkanMod could not compile " + original.getLocation()
                    + " with the selected beacon beam shader.");
        }
        return pipeline;
    }

    private static ParticlePipelineSet compileParticlePipelines(ShaderPack pack) throws IOException {
        List<RenderPipeline> candidates = RenderPipelines.getStaticPipelines().stream()
                .filter(VeyraRenderer::isParticlePipeline)
                .toList();
        Map<RenderPipeline, RenderPipeline> pipelines = new IdentityHashMap<>();
        for (RenderPipeline original : candidates) {
            String programName = resolveParticleProgram(pack, original);
            if (programName == null) {
                continue;
            }
            String fragmentSource = pack.readParticleFragment(programName)
                    .orElseThrow(() -> new IOException("The pack no longer contains " + programName + ".fsh."));
            fragmentSource = ParticleShaderAdapter.adaptFragment(fragmentSource);
            String sourceHash = sha256(programName + '\n' + fragmentSource);
            ParticlePipelineKey cacheKey = new ParticlePipelineKey(original, programName, sourceHash);
            RenderPipeline particlePipeline = PARTICLE_PIPELINE_CACHE.get(cacheKey);
            if (particlePipeline == null) {
                particlePipeline = compileParticlePipeline(original, fragmentSource, sourceHash);
                PARTICLE_PIPELINE_CACHE.put(cacheKey, particlePipeline);
            }
            pipelines.put(original, particlePipeline);
        }
        if (pipelines.isEmpty()) {
            throw new IOException("VulkanMod's Minecraft particle render pipelines were not found.");
        }
        return new ParticlePipelineSet(pipelines);
    }

    private static boolean isParticlePipeline(RenderPipeline pipeline) {
        if (pipeline == null
                || !pipeline.getFragmentShader().equals(Identifier.withDefaultNamespace("core/particle"))) {
            return false;
        }
        String location = pipeline.getLocation().getPath();
        return location.equals("pipeline/opaque_particle")
                || location.equals("pipeline/translucent_particle");
    }

    private static String resolveParticleProgram(ShaderPack pack, RenderPipeline pipeline) {
        if (pipeline.getBlendFunction().isPresent()
                && pack.particlePrograms().contains("gbuffers_particles_translucent")) {
            return "gbuffers_particles_translucent";
        }
        return pack.particlePrograms().contains("gbuffers_particles") ? "gbuffers_particles" : null;
    }

    private static RenderPipeline compileParticlePipeline(
            RenderPipeline original, String fragmentSource, String sourceHash) throws IOException {
        Identifier fragmentShader = Identifier.fromNamespaceAndPath(
                "veyra", "shaderpack/particles/" + sourceHash);
        RenderPipeline pipeline = copyPipelineWithFragment(original, fragmentShader);
        ShaderManager shaderManager = ((MinecraftAccessor) Minecraft.getInstance()).veyra$getShaderManager();
        ShaderSource source = (identifier, type) -> {
            if (identifier.equals(fragmentShader) && type == ShaderType.FRAGMENT) {
                return fragmentSource;
            }
            return shaderManager.getShader(identifier, type);
        };
        CompiledRenderPipeline compiled = RenderSystem.getDevice().precompilePipeline(pipeline, source);
        if (!compiled.isValid()) {
            throw new IOException("VulkanMod could not compile " + original.getLocation()
                    + " with the selected particle shader.");
        }
        return pipeline;
    }

    private static WeatherPipelineSet compileWeatherPipelines(ShaderPack pack) throws IOException {
        List<RenderPipeline> candidates = RenderPipelines.getStaticPipelines().stream()
                .filter(VeyraRenderer::isWeatherPipeline)
                .toList();
        Map<RenderPipeline, RenderPipeline> pipelines = new IdentityHashMap<>();
        for (RenderPipeline original : candidates) {
            String programName = "gbuffers_weather";
            String fragmentSource = pack.readWeatherFragment(programName)
                    .orElseThrow(() -> new IOException("The pack no longer contains " + programName + ".fsh."));
            fragmentSource = ParticleShaderAdapter.adaptFragment(fragmentSource, programName);
            String sourceHash = sha256(programName + '\n' + fragmentSource);
            WeatherPipelineKey cacheKey = new WeatherPipelineKey(original, sourceHash);
            RenderPipeline weatherPipeline = WEATHER_PIPELINE_CACHE.get(cacheKey);
            if (weatherPipeline == null) {
                weatherPipeline = compileWeatherPipeline(original, fragmentSource, sourceHash);
                WEATHER_PIPELINE_CACHE.put(cacheKey, weatherPipeline);
            }
            pipelines.put(original, weatherPipeline);
        }
        if (pipelines.isEmpty()) {
            throw new IOException("VulkanMod's Minecraft weather render pipelines were not found.");
        }
        return new WeatherPipelineSet(pipelines);
    }

    private static boolean isWeatherPipeline(RenderPipeline pipeline) {
        return pipeline != null
                && pipeline.getFragmentShader().equals(Identifier.withDefaultNamespace("core/particle"))
                && pipeline.getLocation().getPath().startsWith("pipeline/weather_");
    }

    private static RenderPipeline compileWeatherPipeline(
            RenderPipeline original, String fragmentSource, String sourceHash) throws IOException {
        Identifier fragmentShader = Identifier.fromNamespaceAndPath(
                "veyra", "shaderpack/weather/" + sourceHash);
        RenderPipeline pipeline = copyPipelineWithFragment(original, fragmentShader);
        ShaderManager shaderManager = ((MinecraftAccessor) Minecraft.getInstance()).veyra$getShaderManager();
        ShaderSource source = (identifier, type) -> {
            if (identifier.equals(fragmentShader) && type == ShaderType.FRAGMENT) {
                return fragmentSource;
            }
            return shaderManager.getShader(identifier, type);
        };
        CompiledRenderPipeline compiled = RenderSystem.getDevice().precompilePipeline(pipeline, source);
        if (!compiled.isValid()) {
            throw new IOException("VulkanMod could not compile " + original.getLocation()
                    + " with the selected weather shader.");
        }
        return pipeline;
    }

    private static RenderPipeline copyPipelineWithFragment(RenderPipeline original, Identifier fragmentShader) {
        RenderPipeline.Builder builder = RenderPipeline.builder()
                .withLocation(original.getLocation())
                .withVertexShader(original.getVertexShader())
                .withFragmentShader(fragmentShader)
                .withDepthTestFunction(original.getDepthTestFunction())
                .withPolygonMode(original.getPolygonMode())
                .withCull(original.isCull())
                .withColorWrite(original.isWriteColor(), original.isWriteAlpha())
                .withDepthWrite(original.isWriteDepth())
                .withVertexFormat(original.getVertexFormat(), original.getVertexFormatMode())
                .withDepthBias(original.getDepthBiasScaleFactor(), original.getDepthBiasConstant());

        original.getBlendFunction().ifPresent(builder::withBlend);
        if (original.getColorLogic() != null) {
            builder.withColorLogic(original.getColorLogic());
        }
        ShaderDefines defines = original.getShaderDefines();
        defines.values().forEach((name, value) -> {
            try {
                builder.withShaderDefine(name, Integer.parseInt(value));
            } catch (NumberFormatException exception) {
                builder.withShaderDefine(name, Float.parseFloat(value));
            }
        });
        defines.flags().forEach(builder::withShaderDefine);
        original.getSamplers().forEach(builder::withSampler);
        for (RenderPipeline.UniformDescription uniform : original.getUniforms()) {
            if (uniform.type() == UniformType.TEXEL_BUFFER) {
                builder.withUniform(uniform.name(), uniform.type(), uniform.textureFormat());
            } else {
                builder.withUniform(uniform.name(), uniform.type());
            }
        }
        return builder.build();
    }

    private static String resolveTerrainProgram(ShaderPack pack, TerrainRenderType terrainRenderType) {
        String specializedProgram = switch (terrainRenderType) {
            case SOLID -> "gbuffers_terrain_solid";
            case CUTOUT, TRIPWIRE -> "gbuffers_terrain_cutout";
            case TRANSLUCENT -> "gbuffers_water";
        };
        if (pack.terrainPrograms().contains(specializedProgram)) {
            return specializedProgram;
        }
        return pack.terrainPrograms().contains("gbuffers_terrain") ? "gbuffers_terrain" : null;
    }

    private static void updateTerrainLayerMode(ShaderPack pack) {
        if (Initializer.CONFIG == null) {
            return;
        }
        boolean needsSeparateOpaqueLayers = pack != null
                && (pack.terrainPrograms().contains("gbuffers_terrain_solid")
                || pack.terrainPrograms().contains("gbuffers_terrain_cutout"));
        if (!needsSeparateOpaqueLayers) {
            restoreUniqueOpaqueLayer();
            return;
        }

        if (previousUniqueOpaqueLayer == null) {
            previousUniqueOpaqueLayer = Initializer.CONFIG.uniqueOpaqueLayer;
        }
        if (Initializer.CONFIG.uniqueOpaqueLayer) {
            Initializer.CONFIG.uniqueOpaqueLayer = false;
            TerrainRenderType.updateMapping();
            rebuildTerrainSections();
            VeyraClient.LOGGER.info(
                    "Temporarily enabled separate solid and cutout terrain layers for the active shader pack.");
        }
    }

    private static void restoreUniqueOpaqueLayer() {
        if (previousUniqueOpaqueLayer == null || Initializer.CONFIG == null) {
            return;
        }
        boolean originalValue = previousUniqueOpaqueLayer;
        boolean changed = Initializer.CONFIG.uniqueOpaqueLayer != originalValue;
        Initializer.CONFIG.uniqueOpaqueLayer = originalValue;
        previousUniqueOpaqueLayer = null;
        if (changed) {
            TerrainRenderType.updateMapping();
            rebuildTerrainSections();
            VeyraClient.LOGGER.info(
                    "Restored VulkanMod uniqueOpaqueLayer to {} after shader pack use.", originalValue);
        }
    }

    private static void rebuildTerrainSections() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level != null && minecraft.levelRenderer != null) {
            minecraft.levelRenderer.allChanged();
        }
    }

    private static FramePlan buildFramePlan(
            List<ProgramGroup> programs, PipelineProgram copyProgram,
            int width, int height, Identifier initialColortex1Target) {
        List<ShaderPass> passes = new ArrayList<>();
        PostPass copyPass = null;
        Map<Integer, Identifier> currentTargets = new HashMap<>();
        currentTargets.put(0, PostChain.MAIN_TARGET_ID);
        currentTargets.put(1, initialColortex1Target);
        try {
            for (ProgramGroup group : programs) {
                Map<Integer, Identifier> outputTargets = new HashMap<>();
                for (PipelineProgram program : group.outputs()) {
                    outputTargets.computeIfAbsent(program.outputTarget(), outputTarget ->
                            nextColorTarget(outputTarget, currentTargets.get(outputTarget)));
                }

                Identifier inputColortex0 = currentTargets.get(0);
                Identifier inputColortex1 = currentTargets.get(1);
                for (PipelineProgram program : group.outputs()) {
                    List<PostPass.Input> inputs = new ArrayList<>();
                    inputs.add(new PostPass.TargetInput("In", inputColortex0, false, true));
                    if (program.usesColortex1Sampler()) {
                        inputs.add(new PostPass.TargetInput(
                                COLORTEX1_INPUT_NAME, inputColortex1, false, true));
                    }
                    if (program.usesDepthSampler()) {
                        inputs.add(new ExternalTextureInput(DEPTH_INPUT_NAME));
                    }
                    PostPass pass = new PostPass(program.pipeline(),
                            outputTargets.get(program.outputTarget()),
                            passUniforms(program, width, height, FrameUniforms.ZERO), inputs);
                    passes.add(new ShaderPass(pass, program));
                    if (usesDynamicUniforms(program)) {
                        makeDynamicUniformBuffer(pass, program, width, height);
                    }
                }
                currentTargets.putAll(outputTargets);
            }

            Identifier finalColortex0 = currentTargets.get(0);
            if (!PostChain.MAIN_TARGET_ID.equals(finalColortex0)) {
                copyPass = new PostPass(copyProgram.pipeline(), PostChain.MAIN_TARGET_ID, Map.of(),
                        List.of(new PostPass.TargetInput("In", finalColortex0, false, true)));
            }
            return new FramePlan(List.copyOf(passes), copyPass, currentTargets.get(1));
        } catch (RuntimeException exception) {
            passes.forEach(shaderPass -> shaderPass.pass().close());
            if (copyPass != null) {
                copyPass.close();
            }
            throw exception;
        }
    }

    private static Identifier nextColorTarget(int targetIndex, Identifier currentTarget) {
        if (targetIndex == 0) {
            return currentTarget.equals(SWAP_TARGET_A_ID) ? SWAP_TARGET_B_ID : SWAP_TARGET_A_ID;
        }
        if (targetIndex == 1) {
            return currentTarget.equals(COLORTEX1_TARGET_A_ID)
                    ? COLORTEX1_TARGET_B_ID : COLORTEX1_TARGET_A_ID;
        }
        throw new IllegalArgumentException("Veyra only allocates colortex0 and colortex1.");
    }

    private static PipelineProgram pipelineProgram(String name, AdaptedShader shader) {
        String hash = sha256(name + '\n' + shader.source());
        String cacheKey = name + ':' + hash;
        return PIPELINE_CACHE.computeIfAbsent(cacheKey, ignored -> {
            String suffix = name + '_' + hash;
            Identifier shaderId = Identifier.fromNamespaceAndPath("veyra", "shaderpack/" + suffix);
            Identifier pipelineId = Identifier.fromNamespaceAndPath("veyra", "pipeline/shaderpack/" + suffix);
            return new PipelineProgram(shaderId, shader.source(), shader.uniforms(),
                    shader.usesDepthSampler(), shader.usesColortex1Sampler(), shader.outputTarget(),
                    createPipeline(pipelineId, shaderId, shader.uniforms(),
                            shader.usesDepthSampler(), shader.usesColortex1Sampler()));
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
            Identifier pipeline, Identifier fragmentShader, List<String> uniforms,
            boolean usesDepthSampler, boolean usesColortex1Sampler) {
        RenderPipeline.Builder builder = RenderPipeline.builder(RenderPipelines.POST_PROCESSING_SNIPPET)
                .withFragmentShader(fragmentShader)
                .withVertexShader(BLIT_VERTEX_ID)
                .withLocation(pipeline)
                .withSampler("InSampler")
                .withUniform("SamplerInfo", UniformType.UNIFORM_BUFFER);
        if (usesDepthSampler) {
            builder.withSampler(DEPTH_INPUT_SAMPLER);
        }
        if (usesColortex1Sampler) {
            builder.withSampler(COLORTEX1_INPUT_SAMPLER);
        }
        if (!uniforms.isEmpty()) {
            builder.withUniform(SHADERPACK_UNIFORMS, UniformType.UNIFORM_BUFFER);
        }
        return builder.build();
    }

    private static List<AdaptedShader> adaptShader(String source) throws IOException {
        if (source == null || source.isBlank()) {
            throw new IOException("Fragment shader is empty.");
        }
        if (source.length() > 1024 * 1024) {
            throw new IOException("Fragment shader exceeds the 1 MiB size limit.");
        }
        String original = source.replace("\uFEFF", "");
        String body = RENDERTARGETS_DIRECTIVE.matcher(original).replaceAll("");
        body = DRAWBUFFERS_DIRECTIVE.matcher(body).replaceAll("");
        body = COMMENTS.matcher(body).replaceAll("");

        Matcher unsupportedColorSampler = UNSUPPORTED_COLOR_SAMPLER.matcher(body);
        if (unsupportedColorSampler.find()) {
            throw new IOException("Only colortex0 and colortex1 are currently available to post-processing passes.");
        }
        Matcher unsupportedDepthSampler = UNSUPPORTED_DEPTH_SAMPLER.matcher(body);
        if (unsupportedDepthSampler.find()) {
            throw new IOException("Only depthtex0 is currently available for depth texture sampling on VulkanMod.");
        }
        Matcher depthSampler = DEPTH_SAMPLER.matcher(body);
        boolean usesDepthSampler = depthSampler.find();
        boolean usesColortex1Sampler = COLORTEX1_SAMPLER.matcher(body).find();
        List<String> uniforms = SUPPORTED_UNIFORMS.stream().filter(body::contains).toList();
        List<OutputMapping> outputMappings = outputMappings(original, body);
        boolean usesFragData = body.contains("gl_FragData");
        if (usesFragData && body.matches("(?s).*\\bgl_FragColor\\b.*")) {
            throw new IOException("A pass cannot mix gl_FragData and gl_FragColor outputs.");
        }

        Matcher outputMatcher = OUTPUT_DECLARATION.matcher(body);
        int outputDeclarationCount = 0;
        String outputName = "outColor";
        while (outputMatcher.find()) {
            outputDeclarationCount++;
            if (outputDeclarationCount == 1) {
                outputName = outputMatcher.group(1);
            }
        }
        if (outputDeclarationCount > 1) {
            throw new IOException("Multiple explicit GLSL outputs are not supported; use gl_FragData with DRAWBUFFERS or RENDERTARGETS.");
        }
        if (usesFragData && outputDeclarationCount != 0) {
            throw new IOException("A pass cannot mix gl_FragData with explicit out variables.");
        }

        body = VERSION_DIRECTIVE.matcher(body).replaceAll("");
        Matcher extensionMatcher = EXTENSION_DIRECTIVE.matcher(body);
        StringBuilder extensions = new StringBuilder();
        while (extensionMatcher.find()) {
            extensions.append(extensionMatcher.group());
        }
        body = extensionMatcher.replaceAll("");
        body = VARYING_DECLARATION.matcher(body).replaceAll("");
        body = MAIN_SAMPLER_DECLARATION.matcher(body).replaceAll("");
        body = COLORTEX1_SAMPLER_DECLARATION.matcher(body).replaceAll("");
        body = DEPTH_SAMPLER_DECLARATION.matcher(body).replaceAll("");
        body = SUPPORTED_UNIFORM_DECLARATION.matcher(body).replaceAll("");
        body = LOCATION_LAYOUT.matcher(body).replaceAll("");
        body = body.replace("texture2D(", "texture(");

        List<AdaptedShader> adaptedShaders = new ArrayList<>(outputMappings.size());
        for (OutputMapping output : outputMappings) {
            String outputBody = usesFragData
                    ? lowerFragData(body, output.fragmentOutputIndex()) : body;
            StringBuilder adapted = new StringBuilder("#version 450\n").append(extensions)
                    .append("in vec2 texCoord;\n");
            if (outputDeclarationCount == 0) {
                adapted.append("out vec4 outColor;\n");
            }
            if (usesFragData) {
                Matcher outputIndices = GL_FRAG_DATA_INDEX.matcher(body);
                Set<Integer> discardedOutputs = new java.util.TreeSet<>();
                while (outputIndices.find()) {
                    int index = Integer.parseInt(outputIndices.group(1));
                    if (index != output.fragmentOutputIndex()) {
                        discardedOutputs.add(index);
                    }
                }
                for (int discarded : discardedOutputs) {
                    adapted.append("vec4 veyraDiscardOutput").append(discarded).append(";\n");
                }
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
            if (usesColortex1Sampler) {
                adapted.append("uniform sampler2D ").append(COLORTEX1_INPUT_SAMPLER).append(";\n")
                        .append("#define colortex1 ").append(COLORTEX1_INPUT_SAMPLER).append('\n');
            }
            if (usesDepthSampler) {
                adapted.append("uniform sampler2D ").append(DEPTH_INPUT_SAMPLER).append(";\n")
                        .append("#define depthtex0 ").append(DEPTH_INPUT_SAMPLER).append('\n')
                        .append("#define DepthSampler ").append(DEPTH_INPUT_SAMPLER).append('\n');
            }
            adapted.append(outputBody);
            adaptedShaders.add(new AdaptedShader(adapted.toString(), uniforms,
                    usesDepthSampler, usesColortex1Sampler, output.targetIndex()));
        }
        return List.copyOf(adaptedShaders);
    }

    private static List<OutputMapping> outputMappings(String original, String body) throws IOException {
        List<Integer> declaredTargets = declaredColorTargets(original);
        Set<Integer> fragmentOutputs = new java.util.TreeSet<>();
        Matcher fragmentData = GL_FRAG_DATA_INDEX.matcher(body);
        while (fragmentData.find()) {
            fragmentOutputs.add(Integer.parseInt(fragmentData.group(1)));
        }
        boolean usesFragData = body.contains("gl_FragData");
        if (usesFragData && fragmentOutputs.isEmpty()) {
            throw new IOException("gl_FragData must use constant output indices.");
        }

        List<OutputMapping> mappings = new ArrayList<>();
        if (declaredTargets != null) {
            if (usesFragData) {
                for (int output = 0; output < declaredTargets.size(); output++) {
                    if (!fragmentOutputs.contains(output)) {
                        throw new IOException("DRAWBUFFERS/RENDERTARGETS declares gl_FragData[" + output
                                + "] but the shader does not write that output.");
                    }
                    mappings.add(new OutputMapping(output, declaredTargets.get(output)));
                }
                if (fragmentOutputs.stream().anyMatch(output -> output >= declaredTargets.size())) {
                    throw new IOException("The shader writes a gl_FragData index missing from RENDERTARGETS.");
                }
            } else {
                if (declaredTargets.size() != 1) {
                    throw new IOException("Multiple render targets require gl_FragData outputs.");
                }
                mappings.add(new OutputMapping(-1, declaredTargets.getFirst()));
            }
        } else if (usesFragData) {
            for (int output : fragmentOutputs) {
                mappings.add(new OutputMapping(output, output));
            }
        } else {
            int target = 0;
            Matcher explicitLocation = EXPLICIT_OUTPUT_LOCATION.matcher(body);
            if (explicitLocation.find()) {
                target = Integer.parseInt(explicitLocation.group(1));
            }
            mappings.add(new OutputMapping(-1, target));
        }

        if (mappings.size() > 2) {
            throw new IOException("Veyra currently supports at most two outputs: colortex0 and colortex1.");
        }
        Set<Integer> uniqueTargets = new java.util.HashSet<>();
        for (OutputMapping mapping : mappings) {
            if (mapping.targetIndex() < 0 || mapping.targetIndex() > 1) {
                throw new IOException("Only colortex0 and colortex1 are currently available as render targets.");
            }
            if (!uniqueTargets.add(mapping.targetIndex())) {
                throw new IOException("A pass cannot write to the same render target more than once.");
            }
        }
        return List.copyOf(mappings);
    }

    private static List<Integer> declaredColorTargets(String source) throws IOException {
        Matcher renderTargets = RENDERTARGETS_DIRECTIVE.matcher(source);
        Matcher drawBuffers = DRAWBUFFERS_DIRECTIVE.matcher(source);
        boolean hasRenderTargets = renderTargets.find();
        boolean hasDrawBuffers = drawBuffers.find();
        if (hasRenderTargets && hasDrawBuffers) {
            throw new IOException("A shader cannot declare both RENDERTARGETS and DRAWBUFFERS.");
        }
        if (!hasRenderTargets && !hasDrawBuffers) {
            return null;
        }
        List<Integer> targets = new ArrayList<>();
        if (hasRenderTargets) {
            String[] values = renderTargets.group(1).split(",");
            for (String value : values) {
                if (value.isBlank()) {
                    throw new IOException("RENDERTARGETS contains an empty target index.");
                }
                targets.add(Integer.parseInt(value.trim()));
            }
            if (renderTargets.find()) {
                throw new IOException("A shader can declare only one RENDERTARGETS directive.");
            }
        } else {
            String value = drawBuffers.group(1);
            for (int index = 0; index < value.length(); index++) {
                targets.add(Character.digit(value.charAt(index), 10));
            }
            if (drawBuffers.find()) {
                throw new IOException("A shader can declare only one DRAWBUFFERS directive.");
            }
        }
        if (targets.isEmpty()) {
            throw new IOException("The render-target directive must name at least one target.");
        }
        return List.copyOf(targets);
    }

    private static String lowerFragData(String source, int selectedOutput) {
        Matcher matcher = GL_FRAG_DATA_INDEX.matcher(source);
        StringBuilder lowered = new StringBuilder(source.length());
        int copiedThrough = 0;
        while (matcher.find()) {
            int output = Integer.parseInt(matcher.group(1));
            lowered.append(source, copiedThrough, matcher.start());
            lowered.append(output == selectedOutput ? "outColor" : "veyraDiscardOutput" + output);
            copiedThrough = matcher.end();
        }
        lowered.append(source, copiedThrough, source.length());
        return lowered.toString();
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

    private static void ensureColortex1Targets(int width, int height) {
        boolean resized = false;
        if (colortex1TargetA == null) {
            colortex1TargetA = new TextureTarget("Veyra shader pack colortex1 A", width, height, false);
            resized = true;
        } else if (colortex1TargetA.width != width || colortex1TargetA.height != height) {
            colortex1TargetA.resize(width, height);
            resized = true;
        }
        if (colortex1TargetB == null) {
            colortex1TargetB = new TextureTarget("Veyra shader pack colortex1 B", width, height, false);
            resized = true;
        } else if (colortex1TargetB.width != width || colortex1TargetB.height != height) {
            colortex1TargetB.resize(width, height);
            resized = true;
        }
        if (resized) {
            currentColortex1Target = COLORTEX1_TARGET_A_ID;
            clearColortex1Targets = true;
        }
        if (clearColortex1Targets) {
            Renderer renderer = Renderer.getInstance();
            if (renderer.getBoundRenderPass() != null) {
                renderer.endRenderPass();
            }
            var commandEncoder = RenderSystem.getDevice().createCommandEncoder();
            commandEncoder.clearColorTexture(colortex1TargetA.getColorTexture(), 0);
            commandEncoder.clearColorTexture(colortex1TargetB.getColorTexture(), 0);
            clearColortex1Targets = false;
        }
    }

    private static void replacePipelineSet(PipelineSet next) {
        PipelineSet old = pipelineSet;
        pipelineSet = next;
        if (old != null && old != next) {
            old.close();
        }
        if (old != next) {
            currentColortex1Target = COLORTEX1_TARGET_A_ID;
            clearColortex1Targets = next != null && next.usesColortex1();
        }
        if ((next == null || !next.usesDepthSampler())
                && sampledDepthTextureView != null && !sampledDepthTextureView.isClosed()) {
            sampledDepthTextureView.close();
            sampledDepthTextureView = null;
            sampledDepthTexture = null;
        }
    }

    private static void replaceTerrainPipelines(TerrainPipelineSet next) {
        TerrainPipelineSet old = terrainPipelineSet;
        terrainPipelineSet = next;
        if (old != null && old != next) {
            old.close();
        }
    }

    private static void replaceCloudPipeline(GraphicsPipeline next) {
        GraphicsPipeline old = cloudPipeline;
        cloudPipeline = next;
        if (old != null && old != next) {
            old.scheduleCleanUp();
        }
    }

    private static void replaceEntityPipelines(EntityPipelineSet next) {
        entityPipelineSet = next;
    }

    private static void replaceParticlePipelines(ParticlePipelineSet next) {
        particlePipelineSet = next;
    }

    private static void replaceWeatherPipelines(WeatherPipelineSet next) {
        weatherPipelineSet = next;
    }

    private static void replaceBeaconBeamPipelines(BeaconBeamPipelineSet next) {
        beaconBeamPipelineSet = next;
    }

    private static String shortError(Exception exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? exception.getClass().getSimpleName() : message;
    }

    private record AdaptedShader(String source, List<String> uniforms,
                                 boolean usesDepthSampler, boolean usesColortex1Sampler, int outputTarget) {
    }

    private record DepthSamplerState(VulkanImage sourceImage, VulkanImage sampledImage) {
    }

    private record PipelineProgram(Identifier shaderId, String fragmentSource, List<String> uniforms,
                                   boolean usesDepthSampler, boolean usesColortex1Sampler,
                                   int outputTarget, RenderPipeline pipeline) {
    }

    private record EntityPipelineKey(RenderPipeline original, String programName, String sourceHash) {
    }

    private record ParticlePipelineKey(RenderPipeline original, String programName, String sourceHash) {
    }

    private record WeatherPipelineKey(RenderPipeline original, String sourceHash) {
    }

    private record BeaconBeamPipelineKey(RenderPipeline original, String sourceHash) {
    }

    private record ProgramGroup(List<PipelineProgram> outputs) {
    }

    private record OutputMapping(int fragmentOutputIndex, int targetIndex) {
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

    private record FramePlan(List<ShaderPass> shaderPasses, PostPass copyPass,
                             Identifier colortex1OutputTarget) implements AutoCloseable {
        @Override
        public void close() {
            shaderPasses.forEach(shaderPass -> shaderPass.pass().close());
            if (copyPass != null) {
                copyPass.close();
            }
        }
    }

    private record PipelineSet(FramePlan planA, FramePlan planB, int width, int height,
                               boolean usesDepthSampler, boolean usesColortex1) implements AutoCloseable {
        private FramePlan framePlan(Identifier currentColortex1Target) {
            return COLORTEX1_TARGET_B_ID.equals(currentColortex1Target) ? planB : planA;
        }

        private int passCount() {
            return planA.shaderPasses().size();
        }

        @Override
        public void close() {
            planA.close();
            if (planB != planA) {
                planB.close();
            }
        }
    }

    private record TerrainPipelineSet(Map<String, GraphicsPipeline> pipelines) implements AutoCloseable {
        @Override
        public void close() {
            pipelines.values().forEach(GraphicsPipeline::scheduleCleanUp);
        }
    }

    private record EntityPipelineSet(Map<RenderPipeline, RenderPipeline> pipelines) {
    }

    private record ParticlePipelineSet(Map<RenderPipeline, RenderPipeline> pipelines) {
    }

    private record WeatherPipelineSet(Map<RenderPipeline, RenderPipeline> pipelines) {
    }

    private record BeaconBeamPipelineSet(Map<RenderPipeline, RenderPipeline> pipelines) {
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
