package dev.veyra.shader;

import com.mojang.blaze3d.framegraph.FrameGraphBuilder;
import com.mojang.blaze3d.pipeline.CompiledRenderPipeline;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
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
import com.mojang.blaze3d.textures.TextureFormat;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import dev.veyra.VeyraClient;
import dev.veyra.vulkan.VeyraPipelineResources;
import dev.veyra.vulkan.VeyraGeometryTargets;
import dev.veyra.mixin.accessor.MinecraftAccessor;
import dev.veyra.mixin.accessor.PostPassAccessor;
import dev.veyra.mixin.accessor.RenderSetupAccessor;
import dev.veyra.mixin.accessor.RenderTargetAccessor;
import dev.veyra.mixin.accessor.RenderTypeAccessor;
import dev.veyra.mixin.accessor.ShaderManagerAccessor;
import dev.veyra.vulkan.VeyraDepthTextureView;
import dev.veyra.vulkan.VeyraDepthTextureSnapshot;
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
import net.minecraft.core.BlockPos;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.LightLayer;
import net.vulkanmod.gl.VkGlTexture;
import net.vulkanmod.render.engine.VkGpuDevice;
import net.vulkanmod.render.engine.VkGpuTexture;
import net.vulkanmod.render.shader.PipelineConfigs;
import net.vulkanmod.render.shader.PipelineManager;
import net.vulkanmod.render.shader.ShaderLoadUtil;
import net.vulkanmod.render.vertex.TerrainRenderType;
import net.vulkanmod.vulkan.Renderer;
import net.vulkanmod.vulkan.device.DeviceManager;
import net.vulkanmod.vulkan.shader.GraphicsPipeline;
import net.vulkanmod.vulkan.shader.Pipeline;
import net.vulkanmod.vulkan.shader.PipelineConfig;
import net.vulkanmod.vulkan.shader.SPIRVUtils;
import net.vulkanmod.vulkan.shader.Uniforms;
import net.vulkanmod.vulkan.shader.descriptor.UBO;
import net.vulkanmod.vulkan.texture.VulkanImage;
import net.vulkanmod.vulkan.util.MappedBuffer;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector2i;
import org.joml.Vector3f;
import org.joml.Vector3i;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkFormatProperties;
import org.lwjgl.vulkan.VK10;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL;

public final class VeyraRenderer {
    private static final Identifier SWAP_TARGET_A_ID = Identifier.fromNamespaceAndPath("veyra", "swap_a");
    private static final Identifier SWAP_TARGET_B_ID = Identifier.fromNamespaceAndPath("veyra", "swap_b");
    private static final Identifier DEPTH1_TARGET_ID = Identifier.fromNamespaceAndPath("veyra", "depthtex1_snapshot");
    private static final Identifier DEPTH2_TARGET_ID = Identifier.fromNamespaceAndPath("veyra", "depthtex2_snapshot");
    private static final Identifier DEPTH1_CAPTURE_SHADER_ID = Identifier.fromNamespaceAndPath("veyra", "depthtex1_capture");
    private static final Identifier DEPTH1_CAPTURE_PIPELINE_ID = Identifier.fromNamespaceAndPath("veyra", "depthtex1_capture");
    private static final String DEPTH1_CAPTURE_FRAGMENT_SOURCE = """
            #version 450
            in vec2 texCoord;
            out vec4 outColor;
            uniform sampler2D Sampler1Sampler;
            void main() {
                gl_FragDepth = texture(Sampler1Sampler, texCoord).r;
                outColor = vec4(0.0);
            }
            """;
    private static final int MAX_COLORTEX_INDEX = 15;
    private static final String SHADERPACK_UNIFORMS = "VeyraParameters";
    private static final String HAND_PROGRAM = "gbuffers_hand";
    private static final String HAND_WATER_PROGRAM = "gbuffers_hand_water";
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
    private static final Pattern OUTPUT_DECLARATION = Pattern.compile(
            "(?m)^[\\t ]*(?:layout\\s*\\(\\s*location\\s*=\\s*(\\d+)\\s*\\)\\s*)?out\\s+vec4\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*;[\\t ]*$");
    private static final Pattern UNSUPPORTED_OUTPUT_DECLARATION = Pattern.compile(
            "(?m)^[\\t ]*(?:layout\\s*\\([^)]*\\)\\s*)?out\\s+(?!vec4\\s+[A-Za-z_][A-Za-z0-9_]*\\s*;)[^\\r\\n]*;");
    private static final Pattern OUTPUT_COLOR_INDEX = Pattern.compile("outColor([0-9]+)");
    private static final Pattern GL_FRAG_DATA_INDEX = Pattern.compile("\\bgl_FragData\\s*\\[\\s*(\\d+)\\s*\\]");
    private static final Pattern RENDERTARGETS_DIRECTIVE = Pattern.compile("(?is)/\\*\\s*RENDERTARGETS\\s*:\\s*([0-9,\\s]+)\\s*\\*/");
    private static final Pattern DRAWBUFFERS_DIRECTIVE = Pattern.compile("(?is)/\\*\\s*DRAWBUFFERS\\s*:\\s*([0-9]+)\\s*\\*/");
    private static final Pattern COLOR_TARGET_FORMAT_DECLARATION = Pattern.compile(
            "(?m)\\bconst\\s+int\\s+colortex\\d+Format\\s*=\\s*[A-Za-z_][A-Za-z0-9_]*\\s*;");
    private static final Pattern COMMENTS = Pattern.compile("(?s)/\\*.*?\\*/|//[^\\r\\n]*");
    private static final Pattern LOCATION_LAYOUT = Pattern.compile("layout\\s*\\(\\s*location\\s*=\\s*\\d+\\s*\\)\\s*");
    private static final Pattern VARYING_DECLARATION = Pattern.compile("(?m)^\\s*(?:varying|in)\\s+vec2\\s+(?:texcoord|texCoord)\\s*;\\s*$");
    private static final Pattern POST_VERTEX_OUTPUT_DECLARATION = Pattern.compile(
            "(?m)^[\\t ]*(?:layout\\s*\\(\\s*location\\s*=\\s*(\\d+)\\s*\\)\\s*)?(?:varying|out)\\s+([A-Za-z_][A-Za-z0-9_]*)\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*;[\\t ]*(?://[^\\r\\n]*)?(?:\\r?\\n|$)");
    private static final Pattern POST_VERTEX_INPUT_DECLARATION = Pattern.compile(
            "(?m)^[\\t ]*(?:layout\\s*\\([^)]*\\)\\s*)?(?:in|attribute)\\s+[^;]+;[\\t ]*(?://[^\\r\\n]*)?(?:\\r?\\n|$)");
    private static final Pattern POST_VERTEX_OTHER_OUTPUT_DECLARATION = Pattern.compile(
            "(?m)^[\\t ]*(?:layout\\s*\\([^)]*\\)\\s*)?(?:varying|out)\\s+[^;]+;[\\t ]*(?://[^\\r\\n]*)?(?:\\r?\\n|$)");
    private static final Pattern POST_VERTEX_UNIFORM_DECLARATION = Pattern.compile(
            "(?m)^[\\t ]*uniform\\s+(?:(?:lowp|mediump|highp)\\s+)?([A-Za-z_][A-Za-z0-9_]*)\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*;[\\t ]*(?://[^\\r\\n]*)?(?:\\r?\\n|$)");
    private static final Pattern UNSUPPORTED_VERTEX_UNIFORM_DECLARATION = Pattern.compile("(?m)^\\s*uniform\\b");
    private static final Pattern POST_VERTEX_MAIN = Pattern.compile("\\bvoid\\s+main\\s*\\(\\s*(?:void\\s*)?\\)\\s*\\{");
    private static final Pattern POST_VERTEX_LEGACY_ATTRIBUTES = Pattern.compile(
            "\\b(?:gl_Normal|gl_Color|gl_MultiTexCoord[1-9][0-9]*|gl_ModelViewMatrix|gl_ProjectionMatrix|gl_ModelViewProjectionMatrix)\\b");
    private static final Pattern MAIN_SAMPLER_DECLARATION = Pattern.compile("(?m)^\\s*uniform\\s+sampler2D\\s+(?:colortex0|Sampler0|InSampler)\\s*;\\s*$");
    private static final Pattern COLORTEX_SAMPLER_DECLARATION = Pattern.compile("(?m)^\\s*uniform\\s+(?:(?:lowp|mediump|highp)\\s+)?sampler2D\\s+colortex(?:[1-9]|1[0-5])\\s*;[^\\r\\n]*(?:\\r?\\n|$)");
    private static final Pattern SUPPORTED_UNIFORM_DECLARATION = Pattern.compile("(?m)^\\s*uniform\\s+(?:float|int|bool)\\s+(?:viewWidth|viewHeight|aspectRatio|frameTimeCounter|frameTime|frameCounter|sunAngle|rainStrength|wetness|thunderStrength|worldTime|worldDay|eyeAltitude|screenBrightness|blindness|nightVision|darknessFactor|isEyeInWater|isSpectator|is_sneaking|is_sprinting|is_on_ground|is_hurt|is_invisible|is_burning)\\s*;[^\\r\\n]*(?:\\r?\\n|$)");
    private static final Pattern SUPPORTED_MATRIX_UNIFORM_DECLARATION = Pattern.compile(
            "(?m)^\\s*uniform\\s+(?:(?:lowp|mediump|highp)\\s+)?mat4\\s+(?:gbufferModelView|gbufferModelViewInverse|gbufferProjection|gbufferProjectionInverse|gbufferPreviousModelView|gbufferPreviousProjection|shadowModelView|shadowModelViewInverse|shadowProjection|shadowProjectionInverse)\\s*;[^\\r\\n]*(?:\\r?\\n|$)");
    private static final Pattern SUPPORTED_CAMERA_UNIFORM_DECLARATION = Pattern.compile(
            "(?m)^\\s*uniform\\s+(?:(?:lowp|mediump|highp)\\s+)?(?:vec3|ivec3)\\s+(?:cameraPosition|cameraPositionInt|cameraPositionFract|previousCameraPosition|previousCameraPositionInt|previousCameraPositionFract)\\s*;[^\\r\\n]*(?:\\r?\\n|$)");
    private static final Pattern COLORTEX_SAMPLER = Pattern.compile("\\bcolortex([0-9]+)\\b");
    private static final Pattern UNSUPPORTED_DEPTH_SAMPLER = Pattern.compile("\\bdepthtex(?![012]\\b)[A-Za-z0-9_]*\\b");
    private static final Pattern DEPTH_SAMPLER = Pattern.compile("\\b(?:depthtex0|DepthSampler)\\b");
    private static final Pattern DEPTH1_SAMPLER = Pattern.compile("\\bdepthtex1\\b");
    private static final Pattern DEPTH2_SAMPLER = Pattern.compile("\\bdepthtex2\\b");
    private static final Pattern UNSUPPORTED_SHADOW_SAMPLER = Pattern.compile(
            "\\b(?:shadowtex(?!0\\b)[A-Za-z0-9_]*|shadowcolor[A-Za-z0-9_]*)\\b");
    private static final Pattern SHADOW_DEPTH_SAMPLER = Pattern.compile("\\bshadowtex0\\b");
    private static final Pattern SHADOW_DEPTH_UNIFORM_DECLARATION = Pattern.compile(
            "(?m)^[\\t ]*uniform\\s+(?:(?:lowp|mediump|highp)\\s+)?([A-Za-z_][A-Za-z0-9_]*)\\s+shadowtex0\\s*;[\\t ]*(?://[^\\r\\n]*)?(?:\\r?\\n|$)");
    private static final Pattern DEPTH_SAMPLER_DECLARATION = Pattern.compile(
            "(?m)^\\s*uniform\\s+(?:(?:lowp|mediump|highp)\\s+)?sampler2D\\s+(?:depthtex0|DepthSampler)\\s*;[^\\r\\n]*(?:\\r?\\n|$)");
    private static final Pattern DEPTH1_SAMPLER_DECLARATION = Pattern.compile(
            "(?m)^\\s*uniform\\s+(?:(?:lowp|mediump|highp)\\s+)?sampler2D\\s+depthtex1\\s*;[^\\r\\n]*(?:\\r?\\n|$)");
    private static final Pattern DEPTH2_SAMPLER_DECLARATION = Pattern.compile(
            "(?m)^\\s*uniform\\s+(?:(?:lowp|mediump|highp)\\s+)?sampler2D\\s+depthtex2\\s*;[^\\r\\n]*(?:\\r?\\n|$)");
    // PostPass appends "Sampler" to every input name when binding it to the render pass.
    private static final String DEPTH_INPUT_NAME = "Sampler1";
    private static final String DEPTH_INPUT_SAMPLER = DEPTH_INPUT_NAME + "Sampler";
    private static final String DEPTH1_INPUT_NAME = "VeyraDepthTex1";
    private static final String DEPTH1_INPUT_SAMPLER = DEPTH1_INPUT_NAME + "Sampler";
    private static final String DEPTH2_INPUT_NAME = "VeyraDepthTex2";
    private static final String DEPTH2_INPUT_SAMPLER = DEPTH2_INPUT_NAME + "Sampler";
    private static final String SHADOW_DEPTH_INPUT_NAME = "VeyraShadowDepth";
    private static final String SHADOW_DEPTH_INPUT_SAMPLER = SHADOW_DEPTH_INPUT_NAME + "Sampler";
    private static final List<String> SUPPORTED_UNIFORMS = List.of(
            "viewWidth", "viewHeight", "aspectRatio", "frameTimeCounter", "frameTime", "frameCounter",
            "sunAngle", "rainStrength", "wetness", "thunderStrength", "worldTime", "worldDay",
            "eyeAltitude", "screenBrightness", "blindness", "nightVision", "darknessFactor", "isEyeInWater",
            "isSpectator", "is_sneaking", "is_sprinting", "is_on_ground", "is_hurt", "is_invisible", "is_burning",
            "gbufferModelView", "gbufferModelViewInverse", "gbufferProjection", "gbufferProjectionInverse",
            "gbufferPreviousModelView", "gbufferPreviousProjection", "shadowModelView", "shadowModelViewInverse",
            "shadowProjection", "shadowProjectionInverse", "cameraPosition", "cameraPositionInt",
            "cameraPositionFract", "previousCameraPosition", "previousCameraPositionInt", "previousCameraPositionFract");
    private static final List<String> MATRIX_UNIFORMS = List.of(
            "gbufferModelView", "gbufferModelViewInverse", "gbufferProjection", "gbufferProjectionInverse",
            "gbufferPreviousModelView", "gbufferPreviousProjection", "shadowModelView", "shadowModelViewInverse",
            "shadowProjection", "shadowProjectionInverse");
    private static final List<String> VECTOR3_UNIFORMS = List.of(
            "cameraPosition", "cameraPositionFract", "previousCameraPosition", "previousCameraPositionFract");
    private static final List<String> IVECTOR3_UNIFORMS = List.of("cameraPositionInt", "previousCameraPositionInt");
    private static final List<String> BOOLEAN_UNIFORMS = List.of(
            "isSpectator", "is_sneaking", "is_sprinting", "is_on_ground", "is_hurt", "is_invisible", "is_burning");
    private static final List<String> INTEGER_UNIFORMS = List.of(
            "frameCounter", "worldTime", "worldDay", "isEyeInWater", "isSpectator", "is_sneaking",
            "is_sprinting", "is_on_ground", "is_hurt", "is_invisible", "is_burning");
    private static final List<String> DYNAMIC_UNIFORMS = List.of(
            "frameTimeCounter", "frameTime", "frameCounter", "sunAngle", "rainStrength", "wetness", "thunderStrength",
            "worldTime", "worldDay", "eyeAltitude", "screenBrightness", "blindness", "nightVision", "darknessFactor",
            "isEyeInWater", "isSpectator",
            "is_sneaking", "is_sprinting", "is_on_ground", "is_hurt", "is_invisible", "is_burning",
            "gbufferModelView", "gbufferModelViewInverse", "gbufferProjection", "gbufferProjectionInverse",
            "gbufferPreviousModelView", "gbufferPreviousProjection", "shadowModelView", "shadowModelViewInverse",
            "shadowProjection", "shadowProjectionInverse", "cameraPosition", "cameraPositionInt",
            "cameraPositionFract", "previousCameraPosition", "previousCameraPositionInt", "previousCameraPositionFract");
    private static final Map<String, PipelineProgram> PIPELINE_CACHE = new HashMap<>();
    private static final Set<PipelineProgram> COMPILED_PIPELINES = Collections.newSetFromMap(new IdentityHashMap<>());
    private static final Map<EntityPipelineKey, RenderPipeline> ENTITY_PIPELINE_CACHE = new HashMap<>();
    private static final Map<ParticlePipelineKey, RenderPipeline> PARTICLE_PIPELINE_CACHE = new HashMap<>();
    private static final Map<WeatherPipelineKey, RenderPipeline> WEATHER_PIPELINE_CACHE = new HashMap<>();
    private static final Map<LightningPipelineKey, RenderPipeline> LIGHTNING_PIPELINE_CACHE = new HashMap<>();
    private static final Map<ColorDistancePipelineKey, RenderPipeline> COLOR_DISTANCE_PIPELINE_CACHE = new HashMap<>();
    private static final Map<GlintPipelineKey, RenderPipeline> GLINT_PIPELINE_CACHE = new HashMap<>();
    private static final Map<SkyPipelineKey, RenderPipeline> SKY_BASIC_PIPELINE_CACHE = new HashMap<>();
    private static final Map<SkyPipelineKey, RenderPipeline> SKY_TEXTURED_PIPELINE_CACHE = new HashMap<>();
    private static final Map<TexturedPipelineKey, RenderPipeline> TEXTURED_PIPELINE_CACHE = new HashMap<>();
    private static final Map<BeaconBeamPipelineKey, RenderPipeline> BEACON_BEAM_PIPELINE_CACHE = new HashMap<>();

    private static ShaderPackRepository repository;
    private static RenderTarget swapTargetA;
    private static RenderTarget swapTargetB;
    private static final Map<Integer, ColorTargetPair> colorTargets = new HashMap<>();
    private static final Map<Integer, Identifier> currentColorTargets = new HashMap<>();
    private static final Set<Integer> initializedColorTargets = new java.util.HashSet<>();
    private static CachedOrthoProjectionMatrixBuffer projectionBuffer;
    private static PipelineSet pipelineSet;
    private static PipelineProgram depthTex1CaptureProgram;
    private static PostPass depthTex1CapturePass;
    private static PostPass depthTex2CapturePass;
    private static TerrainPipelineSet terrainPipelineSet;
    private static EntityPipelineSet entityPipelineSet;
    private static EntityPipelineSet shadowEntityPipelineSet;
    private static EntityPipelineSet shadowBlockEntityPipelineSet;
    private static ShaderPack shadowEntityPipelinePack;
    private static boolean shadowEntityPipelineAttempted;
    private static boolean shadowBlockEntityPipelineAttempted;
    private static boolean shadowEntityRoutingLogged;
    private static boolean shadowBlockEntityRoutingLogged;
    private static BeaconBeamPipelineSet shadowBeaconBeamPipelineSet;
    private static ShaderPack shadowBeaconBeamPipelinePack;
    private static boolean shadowBeaconBeamPipelineAttempted;
    private static boolean shadowBeaconBeamRoutingLogged;
    private static EntityPipelineSet blockEntityPipelineSet;
    private static EntityPipelineSet handPipelineSet;
    private static ParticlePipelineSet particlePipelineSet;
    private static WeatherPipelineSet weatherPipelineSet;
    private static LightningPipelineSet lightningPipelineSet;
    private static ColorDistancePipelineSet basicPipelineSet;
    private static ColorDistancePipelineSet linePipelineSet;
    private static GlintPipelineSet glintPipelineSet;
    private static SkyPipelineSet skyBasicPipelineSet;
    private static SkyPipelineSet skyTexturedPipelineSet;
    private static WorldBorderPipelineSet worldBorderPipelineSet;
    private static DamagedBlockPipelineSet damagedBlockPipelineSet;
    private static BeaconBeamPipelineSet beaconBeamPipelineSet;
    private static GraphicsPipeline cloudPipeline;
    private static GpuTexture sampledDepthTexture;
    private static GpuTextureView sampledDepthTextureView;
    private static final VeyraDepthTextureSnapshot depthTex1Snapshot = new VeyraDepthTextureSnapshot("depthtex1");
    private static final VeyraDepthTextureSnapshot depthTex2Snapshot = new VeyraDepthTextureSnapshot("depthtex2");
    private static FrameMatrices frameMatrices = FrameMatrices.IDENTITY;
    private static boolean frameMatricesInitialized;
    private static final Map<String, MappedBuffer> SHADER_PACK_MATRIX_BUFFERS = new HashMap<>();
    private static final Map<String, MappedBuffer> SHADER_PACK_VECTOR_BUFFERS = new HashMap<>();
    private static final Map<String, MappedBuffer> SHADER_PACK_INTEGER_VECTOR2_BUFFERS = new HashMap<>();
    private static final Map<String, MappedBuffer> SHADER_PACK_INTEGER_VECTOR_BUFFERS = new HashMap<>();
    private static CameraUniforms cameraUniforms = CameraUniforms.ZERO;
    private static boolean cameraUniformsInitialized;
    private static volatile FrameUniforms shaderPackFrameUniforms = FrameUniforms.ZERO;
    private static boolean shaderPackUniformsRegistered;
    private static boolean pipelinesDirty = true;
    private static boolean terrainPipelineDirty = true;
    private static boolean entityPipelineDirty = true;
    private static boolean blockEntityPipelineDirty = true;
    private static boolean handPipelineDirty = true;
    private static boolean particlePipelineDirty = true;
    private static boolean weatherPipelineDirty = true;
    private static boolean lightningPipelineDirty = true;
    private static boolean basicPipelineDirty = true;
    private static boolean linePipelineDirty = true;
    private static boolean glintPipelineDirty = true;
    private static boolean skyBasicPipelineDirty = true;
    private static boolean skyTexturedPipelineDirty = true;
    private static boolean texturedPipelineDirty = true;
    private static boolean damagedBlockPipelineDirty = true;
    private static boolean beaconBeamPipelineDirty = true;
    private static boolean cloudPipelineDirty = true;
    private static Boolean previousUniqueOpaqueLayer;
    private static boolean renderingEnabled;
    private static final ThreadLocal<Integer> HAND_RENDER_DEPTH = ThreadLocal.withInitial(() -> 0);
    private static final ThreadLocal<Integer> BLOCK_ENTITY_SUBMISSION_DEPTH = ThreadLocal.withInitial(() -> 0);
    private static boolean handPipelineRoutingLogged;
    private static boolean blockEntityPipelineRoutingLogged;
    private static boolean signTextPipelineRoutingLogged;
    private static boolean damagedBlockRoutingLogged;
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
        registerShaderPackUniforms();
        VeyraShadowRenderer.initialize();
        packRepository.getSelected().ifPresent(pack -> {
            try {
                VeyraShadowRenderer.configure(pack.readShadowSettings());
            } catch (IOException exception) {
                VeyraClient.LOGGER.warn("Could not read Iris shadow settings for '{}'; using defaults.",
                        pack.name(), exception);
            }
        });
        renderingEnabled = packRepository.getSelected()
                .filter(ShaderPack::hasPostProcessing)
                .isPresent();
        terrainPipelineDirty = packRepository.getSelected()
                .map(pack -> pack.hasTerrainFragment() || pack.hasShadowFragment())
                .orElse(false);
        entityPipelineDirty = packRepository.getSelected()
                .map(ShaderPack::hasEntityFragment)
                .orElse(false);
        blockEntityPipelineDirty = packRepository.getSelected()
                .map(ShaderPack::supportsBlockEntityPipeline)
                .orElse(false);
        handPipelineDirty = packRepository.getSelected()
                .map(ShaderPack::supportsHandPipeline)
                .orElse(false);
        particlePipelineDirty = packRepository.getSelected()
                .map(ShaderPack::hasParticleFragment)
                .orElse(false);
        weatherPipelineDirty = packRepository.getSelected()
                .map(ShaderPack::hasWeatherFragment)
                .orElse(false);
        lightningPipelineDirty = packRepository.getSelected()
                .map(ShaderPack::hasLightningFragment)
                .orElse(false);
        basicPipelineDirty = packRepository.getSelected()
                .map(ShaderPack::hasBasicFragment)
                .orElse(false);
        linePipelineDirty = packRepository.getSelected()
                .map(pack -> pack.hasLineFragment() || pack.hasBasicFragment())
                .orElse(false);
        glintPipelineDirty = packRepository.getSelected()
                .map(pack -> pack.hasGlintFragment() || pack.hasTexturedFragment())
                .orElse(false);
        skyBasicPipelineDirty = packRepository.getSelected()
                .map(pack -> pack.hasSkyBasicFragment() || pack.hasBasicFragment())
                .orElse(false);
        skyTexturedPipelineDirty = packRepository.getSelected()
                .map(pack -> pack.hasSkyTexturedFragment() || pack.hasTexturedFragment())
                .orElse(false);
        texturedPipelineDirty = packRepository.getSelected()
                .map(ShaderPack::hasTexturedFragment)
                .orElse(false);
        damagedBlockPipelineDirty = packRepository.getSelected()
                .map(ShaderPack::hasDamagedBlockPipeline)
                .orElse(false);
        beaconBeamPipelineDirty = packRepository.getSelected()
                .map(ShaderPack::hasBeaconBeamFragment)
                .orElse(false);
        cloudPipelineDirty = packRepository.getSelected()
                .map(ShaderPack::hasCloudFragment)
                .orElse(false);
        updateTerrainLayerMode(packRepository.getSelected().orElse(null));
    }

    private static void registerShaderPackUniforms() {
        if (shaderPackUniformsRegistered) {
            return;
        }
        for (TerrainShaderAdapter.ShaderUniform uniform : TerrainShaderAdapter.shaderPackUniforms()) {
            if (uniform.sourceType().equals("float")) {
                Uniforms.vec1f_uniformMap.put(uniform.name(), () -> shaderPackFloatUniform(uniform.name()));
            } else if (uniform.sourceType().equals("vec3")) {
                registerShaderPackVectorUniform(uniform.name());
            } else if (uniform.sourceType().equals("ivec2")) {
                registerShaderPackIntegerVector2Uniform(uniform.name());
            } else if (uniform.sourceType().equals("ivec3")) {
                registerShaderPackIntegerVectorUniform(uniform.name());
            } else if (uniform.sourceType().equals("mat4")) {
                registerShaderPackMatrixUniform(uniform.name());
            } else {
                Uniforms.vec1i_uniformMap.put(uniform.name(), () -> shaderPackIntUniform(uniform.name()));
                if (uniform.booleanField()) {
                    Uniforms.vec1i_uniformMap.put(uniform.blockMemberName(),
                            () -> shaderPackIntUniform(uniform.name()));
                }
            }
        }
        shaderPackUniformsRegistered = true;
    }

    private static void registerShaderPackVectorUniform(String name) {
        MappedBuffer buffer = new MappedBuffer(4 * Float.BYTES);
        SHADER_PACK_VECTOR_BUFFERS.put(name, buffer);
        updateShaderPackVectorUniform(name, buffer, cameraUniforms);
        Uniforms.vec3f_uniformMap.put(name, () -> buffer);
    }

    private static void updateShaderPackVectorUniforms(CameraUniforms uniforms) {
        SHADER_PACK_VECTOR_BUFFERS.forEach((name, buffer) -> updateShaderPackVectorUniform(name, buffer, uniforms));
    }

    private static void updateShaderPackVectorUniform(String name, MappedBuffer buffer, CameraUniforms uniforms) {
        Vector3f value = switch (name) {
            case "cameraPosition" -> uniforms.cameraPosition();
            case "cameraPositionFract" -> uniforms.cameraPositionFract();
            case "previousCameraPosition" -> uniforms.previousCameraPosition();
            case "previousCameraPositionFract" -> uniforms.previousCameraPositionFract();
            default -> throw new IllegalArgumentException("Unknown shader pack vector uniform " + name + ".");
        };
        buffer.putFloat(0, value.x);
        buffer.putFloat(Float.BYTES, value.y);
        buffer.putFloat(Float.BYTES * 2, value.z);
    }

    private static void registerShaderPackIntegerVectorUniform(String name) {
        MappedBuffer buffer = new MappedBuffer(4 * Integer.BYTES);
        SHADER_PACK_INTEGER_VECTOR_BUFFERS.put(name, buffer);
        updateShaderPackIntegerVectorUniform(name, buffer, cameraUniforms);
    }

    private static void registerShaderPackIntegerVector2Uniform(String name) {
        MappedBuffer buffer = new MappedBuffer(2 * Integer.BYTES);
        SHADER_PACK_INTEGER_VECTOR2_BUFFERS.put(name, buffer);
        updateShaderPackIntegerVector2Uniform(name, buffer, cameraUniforms);
    }

    private static void updateShaderPackIntegerVector2Uniforms(CameraUniforms uniforms) {
        SHADER_PACK_INTEGER_VECTOR2_BUFFERS.forEach((name, buffer) ->
                updateShaderPackIntegerVector2Uniform(name, buffer, uniforms));
    }

    private static void updateShaderPackIntegerVector2Uniform(String name, MappedBuffer buffer, CameraUniforms uniforms) {
        if (!name.equals("eyeBrightness")) {
            throw new IllegalArgumentException("Unknown shader pack integer vector uniform " + name + ".");
        }
        Vector2i value = uniforms.eyeBrightness();
        buffer.putInt(0, value.x);
        buffer.putInt(Integer.BYTES, value.y);
    }

    public static MappedBuffer shaderPackIntegerVector2Uniform(String name) {
        return SHADER_PACK_INTEGER_VECTOR2_BUFFERS.get(name);
    }

    private static void updateShaderPackIntegerVectorUniforms(CameraUniforms uniforms) {
        SHADER_PACK_INTEGER_VECTOR_BUFFERS.forEach((name, buffer) ->
                updateShaderPackIntegerVectorUniform(name, buffer, uniforms));
    }

    private static void updateShaderPackIntegerVectorUniform(String name, MappedBuffer buffer, CameraUniforms uniforms) {
        Vector3i value = switch (name) {
            case "cameraPositionInt" -> uniforms.cameraPositionInt();
            case "previousCameraPositionInt" -> uniforms.previousCameraPositionInt();
            default -> throw new IllegalArgumentException("Unknown shader pack integer vector uniform " + name + ".");
        };
        buffer.putInt(0, value.x);
        buffer.putInt(Integer.BYTES, value.y);
        buffer.putInt(Integer.BYTES * 2, value.z);
    }

    public static MappedBuffer shaderPackIntegerVectorUniform(String name) {
        return SHADER_PACK_INTEGER_VECTOR_BUFFERS.get(name);
    }

    private static void registerShaderPackMatrixUniform(String name) {
        if (name.equals("shadowModelViewInverse") || name.equals("shadowProjectionInverse")) {
            return;
        }
        MappedBuffer buffer = new MappedBuffer(16 * Float.BYTES);
        shaderPackMatrix(name, frameMatrices).get(buffer.buffer.asFloatBuffer());
        SHADER_PACK_MATRIX_BUFFERS.put(name, buffer);
        Uniforms.mat4f_uniformMap.put(name, () -> buffer);
    }

    private static void updateShaderPackMatrixUniforms(FrameMatrices matrices) {
        SHADER_PACK_MATRIX_BUFFERS.forEach((name, buffer) ->
                shaderPackMatrix(name, matrices).get(buffer.buffer.asFloatBuffer()));
    }

    private static Matrix4f shaderPackMatrix(String name, FrameMatrices matrices) {
        return switch (name) {
            case "gbufferModelView" -> matrices.gbufferModelView();
            case "gbufferModelViewInverse" -> matrices.gbufferModelViewInverse();
            case "gbufferProjection" -> isHandRendering()
                    ? handAdjustedProjection(matrices.gbufferProjection()) : matrices.gbufferProjection();
            case "gbufferProjectionInverse" -> isHandRendering()
                    ? handAdjustedProjection(matrices.gbufferProjection()).invert() : matrices.gbufferProjectionInverse();
            case "gbufferPreviousModelView" -> matrices.gbufferPreviousModelView();
            case "gbufferPreviousProjection" -> matrices.gbufferPreviousProjection();
            default -> throw new IllegalArgumentException("Unknown shader pack matrix uniform " + name + ".");
        };
    }

    private static Matrix4f handAdjustedProjection(Matrix4f projection) {
        return new Matrix4f().scale(1.0F, 1.0F, EntityShaderAdapter.MC_HAND_DEPTH).mul(projection);
    }

    private static float shaderPackFloatUniform(String name) {
        Minecraft minecraft = Minecraft.getInstance();
        if (name.equals("viewWidth")) {
            return minecraft.getWindow().getWidth();
        }
        if (name.equals("viewHeight")) {
            return minecraft.getWindow().getHeight();
        }
        if (name.equals("aspectRatio")) {
            int height = minecraft.getWindow().getHeight();
            return height == 0 ? 0.0F : (float) minecraft.getWindow().getWidth() / height;
        }

        FrameUniforms frame = shaderPackFrameUniforms;
        return switch (name) {
            case "frameTimeCounter" -> frame.frameTimeCounter();
            case "frameTime" -> frame.frameTime();
            case "sunAngle" -> frame.sunAngle();
            case "rainStrength" -> frame.rainStrength();
            case "wetness" -> frame.wetness();
            case "thunderStrength" -> frame.thunderStrength();
            case "eyeAltitude" -> frame.eyeAltitude();
            case "screenBrightness" -> frame.screenBrightness();
            case "blindness" -> frame.blindness();
            case "nightVision" -> frame.nightVision();
            case "darknessFactor" -> frame.darknessFactor();
            default -> throw new IllegalArgumentException("Unknown shader pack float uniform " + name + ".");
        };
    }

    private static int shaderPackIntUniform(String name) {
        FrameUniforms frame = shaderPackFrameUniforms;
        return switch (name) {
            case "frameCounter" -> frame.frameCounter();
            case "worldTime" -> frame.worldTime();
            case "worldDay" -> frame.worldDay();
            case "isEyeInWater" -> frame.isEyeInWater();
            case "isSpectator" -> frame.isSpectator() ? 1 : 0;
            case "is_sneaking" -> frame.isSneaking() ? 1 : 0;
            case "is_sprinting" -> frame.isSprinting() ? 1 : 0;
            case "is_on_ground" -> frame.isOnGround() ? 1 : 0;
            case "is_hurt" -> frame.isHurt() ? 1 : 0;
            case "is_invisible" -> frame.isInvisible() ? 1 : 0;
            case "is_burning" -> frame.isBurning() ? 1 : 0;
            default -> throw new IllegalArgumentException("Unknown shader pack integer uniform " + name + ".");
        };
    }

    public static Optional<ShaderPack> selectedPack() {
        return repository == null ? Optional.empty() : repository.getSelected();
    }

    public static boolean hasShadowPass() {
        return selectedPack().map(ShaderPack::hasShadowFragment).orElse(false);
    }

    private static Object pipelineLevel;

    private static void synchronizeWorld() {
        Minecraft minecraft = Minecraft.getInstance();
        if (repository == null || minecraft.level == null || pipelineLevel == minecraft.level) return;
        pipelineLevel = minecraft.level;
        frameMatricesInitialized = false;
        cameraUniformsInitialized = false;
        previousFrameNanos = 0L;
        initializedColorTargets.clear();
        depthTex1Snapshot.close();
        depthTex2Snapshot.close();
        int dimension = minecraft.level.dimension().equals(net.minecraft.world.level.Level.NETHER) ? -1
                : minecraft.level.dimension().equals(net.minecraft.world.level.Level.END) ? 1 : 0;
        repository.setDimension(dimension);
        ShaderPack active = selectedPack().orElse(null);
        if (active != null && active.configuration().dimension() != dimension) {
            try {
                repository.refresh();
                ShaderPack candidate = active.configured(active.configuration().options(), dimension);
                if (!candidate.supportsShaderPrograms()) {
                    disable();
                    lastError = "This shader pack has no supported programs for world" + dimension;
                } else {
                    String result = select(candidate);
                    // Old-dimension pipelines must never remain installed after a failed transition.
                    if (selectedPack().map(pack -> pack.configuration().dimension() != dimension).orElse(false)) {
                        disable();
                        lastError = result;
                    }
                }
            } catch (IOException exception) {
                disable();
                lastError = shortError(exception);
                VeyraClient.LOGGER.error("Could not switch shader dimension", exception);
            }
        }
    }

    public static void captureFrameMatrices(Matrix4fc modelView, Matrix4fc projection, Vec3 cameraPosition) {
        synchronizeWorld();
        nextPostPhase = 0;
        Matrix4f currentModelView = new Matrix4f(modelView);
        Matrix4f currentProjection = new Matrix4f(projection);
        Matrix4f previousModelView = frameMatricesInitialized
                ? new Matrix4f(frameMatrices.gbufferModelView()) : new Matrix4f(currentModelView);
        Matrix4f previousProjection = frameMatricesInitialized
                ? new Matrix4f(frameMatrices.gbufferProjection()) : new Matrix4f(currentProjection);
        Matrix4f shadowModelView = VeyraShadowRenderer.shadowModelView();
        Matrix4f shadowProjection = VeyraShadowRenderer.shadowProjection();
        frameMatrices = new FrameMatrices(
                currentModelView, new Matrix4f(currentModelView).invert(),
                currentProjection, new Matrix4f(currentProjection).invert(),
                previousModelView, previousProjection,
                shadowModelView, new Matrix4f(shadowModelView).invert(),
                shadowProjection, new Matrix4f(shadowProjection).invert());
        frameMatricesInitialized = true;
        updateShaderPackMatrixUniforms(frameMatrices);

        CameraPosition currentCameraPosition = cameraPosition(cameraPosition.x, cameraPosition.y, cameraPosition.z);
        CameraPosition previousCameraPosition = cameraUniformsInitialized
                ? new CameraPosition(cameraUniforms.cameraPosition(), cameraUniforms.cameraPositionInt(), cameraUniforms.cameraPositionFract())
                : currentCameraPosition;
        cameraUniforms = new CameraUniforms(
                currentCameraPosition.position(), currentCameraPosition.integerPosition(), currentCameraPosition.fractionalPosition(),
                previousCameraPosition.position(), previousCameraPosition.integerPosition(), previousCameraPosition.fractionalPosition(),
                eyeBrightness(Minecraft.getInstance(), cameraPosition));
        cameraUniformsInitialized = true;
        updateShaderPackVectorUniforms(cameraUniforms);
        updateShaderPackIntegerVector2Uniforms(cameraUniforms);
        updateShaderPackIntegerVectorUniforms(cameraUniforms);
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level != null && minecraft.player != null) {
            shaderPackFrameUniforms = updateFrameUniforms(minecraft);
        }
    }

    public static void captureDepthTex1() {
        if (repository == null || repository.getSelected().isEmpty() || !selectedPackUsesDepth1Sampler()) {
            return;
        }
        try {
            captureDepthTex1Snapshot();
        } catch (RuntimeException exception) {
            renderingEnabled = false;
            lastError = shortError(exception);
            VeyraClient.LOGGER.error("Could not capture the depthtex1 snapshot.", exception);
        }
    }

    public static void captureDepthTex2() {
        if (repository == null || repository.getSelected().isEmpty() || !selectedPackUsesDepth2Sampler()) {
            return;
        }
        try {
            captureDepthTex2Snapshot();
        } catch (RuntimeException exception) {
            renderingEnabled = false;
            lastError = shortError(exception);
            VeyraClient.LOGGER.error("Could not capture the depthtex2 snapshot.", exception);
        }
    }

    private static boolean selectedPackUsesDepth1Sampler() {
        if (!pipelinesDirty && pipelineSet != null) {
            return pipelineSet.usesDepth1Sampler();
        }
        ShaderPack pack = repository.getSelected().orElse(null);
        if (pack == null) {
            return false;
        }
        try {
            return pack.readPrograms().stream().anyMatch(program -> DEPTH1_SAMPLER.matcher(
                    COMMENTS.matcher(program.source()).replaceAll("")).find());
        } catch (IOException exception) {
            return false;
        }
    }

    private static boolean selectedPackUsesDepth2Sampler() {
        if (!pipelinesDirty && pipelineSet != null) {
            return pipelineSet.usesDepth2Sampler();
        }
        ShaderPack pack = repository.getSelected().orElse(null);
        if (pack == null) {
            return false;
        }
        try {
            return pack.readPrograms().stream().anyMatch(program -> DEPTH2_SAMPLER.matcher(
                    COMMENTS.matcher(program.source()).replaceAll("")).find());
        } catch (IOException exception) {
            return false;
        }
    }

    private static void captureDepthTex1Snapshot() {
        captureDepthSnapshot(depthTex1Snapshot, DEPTH1_TARGET_ID, false);
    }

    private static void captureDepthTex2Snapshot() {
        captureDepthSnapshot(depthTex2Snapshot, DEPTH2_TARGET_ID, true);
    }

    private static void captureDepthSnapshot(VeyraDepthTextureSnapshot snapshot,
                                             Identifier targetId, boolean depth2) {
        RenderTarget mainTarget = Minecraft.getInstance().getMainRenderTarget();
        GpuTexture mainDepth = Renderer.getInstance().getMainPass().getDepthAttachment();
        if (!(mainDepth instanceof VkGpuTexture vulkanDepth) || vulkanDepth.isClosed()) {
            throw new IllegalStateException("VulkanMod's main depth attachment is unavailable for "
                    + (depth2 ? "depthtex2" : "depthtex1") + ".");
        }
        snapshot.ensure(mainTarget.width, mainTarget.height, vulkanDepth.getVulkanImage().format);

        PipelineProgram copyProgram = depthTex1CaptureProgram();
        PostPass capturePass;
        if (depth2) {
            if (depthTex2CapturePass == null) {
                depthTex2CapturePass = createDepthCapturePass(copyProgram.pipeline(), DEPTH2_TARGET_ID);
            }
            capturePass = depthTex2CapturePass;
        } else {
            if (depthTex1CapturePass == null) {
                depthTex1CapturePass = createDepthCapturePass(copyProgram.pipeline(), DEPTH1_TARGET_ID);
            }
            capturePass = depthTex1CapturePass;
        }

        DepthSamplerState depthSamplerState = prepareDepthSampler();
        try {
            RenderSystem.getDevice().createCommandEncoder().clearDepthTexture(snapshot.depthTexture(), 1.0);
            snapshot.transitionToAttachment();

            FrameGraphBuilder graph = new FrameGraphBuilder();
            Map<Identifier, ResourceHandle<RenderTarget>> targets = new HashMap<>();
            String targetName = depth2 ? "depthtex2" : "depthtex1";
            targets.put(PostChain.MAIN_TARGET_ID, graph.importExternal(
                    "veyra:" + targetName + "-source", mainTarget));
            targets.put(targetId, graph.importExternal(
                    "veyra:" + targetName + "-target", snapshot.renderTarget()));
            GpuBufferSlice samplerInfo = projectionBuffer().getBuffer(mainTarget.width, mainTarget.height);
            capturePass.addToFrame(graph, targets, samplerInfo);
            graph.execute(GraphicsResourceAllocator.UNPOOLED);
        } finally {
            if (Renderer.getInstance().getBoundRenderPass() != null) {
                Renderer.getInstance().endRenderPass();
            }
            try {
                snapshot.transitionToSampling();
            } finally {
                restoreDepthSampler(depthSamplerState);
            }
        }
    }

    private static PostPass createDepthCapturePass(RenderPipeline pipeline, Identifier targetId) {
        return new PostPass(pipeline, targetId, Map.of(), List.of(
                    new PostPass.TargetInput("In", PostChain.MAIN_TARGET_ID, false, true),
                    new ExternalTextureInput(DEPTH_INPUT_NAME)));
    }

    private static PipelineProgram depthTex1CaptureProgram() {
        if (depthTex1CaptureProgram != null) {
            return depthTex1CaptureProgram;
        }

        RenderPipeline pipeline = RenderPipeline.builder(RenderPipelines.POST_PROCESSING_SNIPPET)
                .withFragmentShader(DEPTH1_CAPTURE_SHADER_ID)
                .withVertexShader(BLIT_VERTEX_ID)
                .withLocation(DEPTH1_CAPTURE_PIPELINE_ID)
                .withSampler("InSampler")
                .withSampler(DEPTH_INPUT_SAMPLER)
                .withUniform("SamplerInfo", UniformType.UNIFORM_BUFFER)
                .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
                .withDepthWrite(true)
                .withColorWrite(false)
                .build();
        PipelineProgram program = new PipelineProgram(DEPTH1_CAPTURE_SHADER_ID,
                DEPTH1_CAPTURE_FRAGMENT_SOURCE, BLIT_VERTEX_ID, null,
                List.of(), true, false, false, false, Set.of(), 0, pipeline);
        compilePipeline(program);
        depthTex1CaptureProgram = program;
        return program;
    }

    private static CameraPosition cameraPosition(double x, double y, double z) {
        int integerX = (int) Math.floor(x);
        int integerY = (int) Math.floor(y);
        int integerZ = (int) Math.floor(z);
        return new CameraPosition(
                new Vector3f((float) x, (float) y, (float) z),
                new Vector3i(integerX, integerY, integerZ),
                new Vector3f((float) (x - integerX), (float) (y - integerY), (float) (z - integerZ)));
    }

    private static Vector2i eyeBrightness(Minecraft minecraft, Vec3 cameraPosition) {
        ClientLevel level = minecraft.level;
        if (level == null) {
            return new Vector2i();
        }
        BlockPos position = BlockPos.containing(cameraPosition);
        return new Vector2i(
                level.getBrightness(LightLayer.BLOCK, position) * 16,
                level.getBrightness(LightLayer.SKY, position) * 16);
    }

    public static boolean isRenderingEnabled() {
        return renderingEnabled && selectedPack().isPresent();
    }

    public static void beginHandRendering() {
        int depth = HAND_RENDER_DEPTH.get();
        HAND_RENDER_DEPTH.set(depth + 1);
        if (depth == 0 && frameMatricesInitialized) {
            updateShaderPackMatrixUniforms(frameMatrices);
        }
    }

    public static void endHandRendering() {
        int depth = HAND_RENDER_DEPTH.get();
        if (depth <= 1) {
            HAND_RENDER_DEPTH.remove();
        } else {
            HAND_RENDER_DEPTH.set(depth - 1);
        }
        if (depth == 1 && frameMatricesInitialized) {
            updateShaderPackMatrixUniforms(frameMatrices);
        }
    }

    public static void beginBlockEntitySubmission() {
        BLOCK_ENTITY_SUBMISSION_DEPTH.set(BLOCK_ENTITY_SUBMISSION_DEPTH.get() + 1);
    }

    public static void endBlockEntitySubmission() {
        int depth = BLOCK_ENTITY_SUBMISSION_DEPTH.get();
        if (depth <= 1) {
            BLOCK_ENTITY_SUBMISSION_DEPTH.remove();
        } else {
            BLOCK_ENTITY_SUBMISSION_DEPTH.set(depth - 1);
        }
    }

    public static RenderType routeBlockEntityRenderType(RenderType originalType) {
        if (originalType == null || BLOCK_ENTITY_SUBMISSION_DEPTH.get() == 0) {
            return originalType;
        }
        RenderPipeline original = originalType.pipeline();
        if (VeyraShadowRenderer.isRendering()) {
            RenderPipeline shadowPipeline = getShadowEntityPipeline(original, true);
            return shadowPipeline == null ? originalType
                    : replaceRenderTypePipeline(originalType, shadowPipeline, "_veyra_shadow_block");
        }
        ShaderPack pack = selectedPack().orElse(null);
        if (pack == null || !pack.supportsBlockEntityPipeline() || !isEntityRenderPipeline(original)) {
            return originalType;
        }
        EntityPipelineSet pipelines = getBlockEntityPipelineSet(pack);
        RenderPipeline replacement = pipelines == null ? null : pipelines.pipelines().get(original);
        if (replacement == null) {
            return originalType;
        }
        if (!blockEntityPipelineRoutingLogged) {
            VeyraClient.LOGGER.info("Routing block entity pipeline {} through {} from shader pack '{}'.",
                    original.getLocation(), resolveBlockEntityProgram(pack, original), pack.name());
            blockEntityPipelineRoutingLogged = true;
        }
        return replaceRenderTypePipeline(originalType, replacement, "_veyra_block");
    }

    public static RenderPipeline getSignTextPipeline(RenderPipeline original) {
        if (!isSignTextPipeline(original)) {
            return null;
        }
        ShaderPack pack = selectedPack().orElse(null);
        if (pack == null || !pack.supportsBlockEntityPipeline()) {
            return null;
        }
        EntityPipelineSet pipelines = getBlockEntityPipelineSet(pack);
        RenderPipeline replacement = pipelines == null ? null : pipelines.pipelines().get(original);
        if (replacement != null && !signTextPipelineRoutingLogged) {
            VeyraClient.LOGGER.info("Routing sign text through {} from shader pack '{}'.",
                    resolveBlockTextProgram(pack), pack.name());
            signTextPipelineRoutingLogged = true;
        }
        return replacement;
    }

    private static EntityPipelineSet getBlockEntityPipelineSet(ShaderPack pack) {
        if (blockEntityPipelineDirty) {
            blockEntityPipelineDirty = false;
            try {
                replaceBlockEntityPipelines(compileBlockEntityPipelines(pack));
                lastError = "";
                VeyraClient.LOGGER.info("Loaded {} block entity model and sign text pipelines from shader pack '{}'.",
                        blockEntityPipelineSet.pipelines().size(), pack.name());
            } catch (Exception exception) {
                lastError = shortError(exception);
                VeyraClient.LOGGER.error("Could not load block entity and sign text shader programs from shader pack '{}'.",
                        pack.name(), exception);
                return null;
            }
        }
        return blockEntityPipelineSet;
    }

    private static RenderType replaceRenderTypePipeline(RenderType originalType,
                                                        RenderPipeline replacement, String suffix) {
        RenderSetup originalSetup = ((RenderTypeAccessor) (Object) originalType).veyra$getSetup();
        RenderSetupAccessor setup = (RenderSetupAccessor) (Object) originalSetup;
        RenderSetup replacementSetup = RenderSetupAccessor.veyra$create(replacement,
                setup.veyra$getTextures(), setup.veyra$getUseLightmap(), setup.veyra$getUseOverlay(),
                setup.veyra$getLayeringTransform(), setup.veyra$getOutputTarget(),
                setup.veyra$getTextureTransform(), setup.veyra$getOutlineProperty(),
                setup.veyra$getAffectsCrumbling(), setup.veyra$getSortOnUpload(), setup.veyra$getBufferSize());
        return RenderTypeAccessor.veyra$create(originalType.toString() + suffix, replacementSetup);
    }

    private static boolean isHandRendering() {
        return HAND_RENDER_DEPTH.get() > 0;
    }

    public static GraphicsPipeline getTerrainPipeline(TerrainRenderType terrainRenderType) {
        ShaderPack pack = selectedPack().orElse(null);
        if (VeyraShadowRenderer.isRendering()) {
            if (pack == null || !pack.hasShadowFragment()) {
                return null;
            }
            if (terrainPipelineDirty) {
                terrainPipelineDirty = false;
                try {
                    replaceTerrainPipelines(compileTerrainPipelines(pack));
                    lastError = "";
                    VeyraClient.LOGGER.info("Loaded shadow and terrain shader programs from shader pack '{}'.", pack.name());
                } catch (Exception exception) {
                    lastError = shortError(exception);
                    VeyraClient.LOGGER.error("Could not load shadow shader program from shader pack '{}'.", pack.name(), exception);
                    return null;
                }
            }
            return terrainPipelineSet == null ? null : terrainPipelineSet.shadowPipeline(terrainRenderType);
        }
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
        if (VeyraShadowRenderer.isRendering()) {
            return getShadowEntityPipeline(original, false);
        }
        ShaderPack pack = selectedPack().orElse(null);
        if (isHandRendering() || pack == null || !pack.hasEntityFragment() || !isEntityRenderPipeline(original)) {
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

    private static RenderPipeline getShadowEntityPipeline(RenderPipeline original, boolean blockEntity) {
        ShaderPack pack = selectedPack().orElse(null);
        if (pack == null || !pack.hasShadowFragment() || !isEntityRenderPipeline(original)) {
            return null;
        }
        if (shadowEntityPipelinePack != pack) {
            shadowEntityPipelinePack = pack;
            shadowEntityPipelineSet = null;
            shadowBlockEntityPipelineSet = null;
            shadowEntityPipelineAttempted = false;
            shadowBlockEntityPipelineAttempted = false;
            shadowEntityRoutingLogged = false;
            shadowBlockEntityRoutingLogged = false;
        }
        String programName = blockEntity ? "shadow_block" : "shadow_entities";
        EntityPipelineSet pipelineSet;
        boolean pipelineAttempted;
        if (blockEntity) {
            pipelineSet = shadowBlockEntityPipelineSet;
            pipelineAttempted = shadowBlockEntityPipelineAttempted;
        } else {
            pipelineSet = shadowEntityPipelineSet;
            pipelineAttempted = shadowEntityPipelineAttempted;
        }
        if (!pipelineAttempted) {
            try {
                pipelineSet = compileShadowEntityPipelines(pack, programName);
                lastError = "";
                VeyraClient.LOGGER.info("Loaded {} {} shadow pipelines from shader pack '{}'.",
                        pipelineSet.pipelines().size(), blockEntity ? "block entity" : "entity", pack.name());
            } catch (Exception exception) {
                lastError = shortError(exception);
                VeyraClient.LOGGER.error("Could not load {} shadow program from shader pack '{}'.",
                        programName, pack.name(), exception);
            }
            if (blockEntity) {
                shadowBlockEntityPipelineSet = pipelineSet;
                shadowBlockEntityPipelineAttempted = true;
            } else {
                shadowEntityPipelineSet = pipelineSet;
                shadowEntityPipelineAttempted = true;
            }
        }
        RenderPipeline replacement = pipelineSet == null ? null : pipelineSet.pipelines().get(original);
        if (replacement != null) {
            if (blockEntity && !shadowBlockEntityRoutingLogged) {
                VeyraClient.LOGGER.info("Rendering block entities with the {} fragment from shader pack '{}'.",
                        programName, pack.name());
                shadowBlockEntityRoutingLogged = true;
            } else if (!blockEntity && !shadowEntityRoutingLogged) {
                VeyraClient.LOGGER.info("Rendering entities with the {} fragment from shader pack '{}'.",
                        programName, pack.name());
                shadowEntityRoutingLogged = true;
            }
        }
        return replacement;
    }

    public static RenderPipeline getHandPipeline(RenderPipeline original) {
        ShaderPack pack = selectedPack().orElse(null);
        if (!isHandRendering() || pack == null || !pack.supportsHandPipeline()
                || !isEntityRenderPipeline(original)) {
            return null;
        }
        if (handPipelineDirty) {
            handPipelineDirty = false;
            try {
                replaceHandPipelines(compileEntityPipelines(pack, true));
                lastError = "";
                VeyraClient.LOGGER.info("Loaded {} first-person hand render pipelines from shader pack '{}'.",
                        handPipelineSet.pipelines().size(), pack.name());
            } catch (Exception exception) {
                lastError = shortError(exception);
                VeyraClient.LOGGER.error("Could not load first-person hand shader programs from shader pack '{}'.", pack.name(), exception);
                return null;
            }
        }
        RenderPipeline replacement = handPipelineSet == null ? null : handPipelineSet.pipelines().get(original);
        if (replacement != null && !handPipelineRoutingLogged) {
            VeyraClient.LOGGER.info("Routing first-person hand pipeline {} through {} from shader pack '{}'.",
                    original.getLocation(), resolveHandProgram(pack, original), pack.name());
            handPipelineRoutingLogged = true;
        }
        return replacement;
    }

    public static RenderPipeline getDamagedBlockPipeline(RenderPipeline original) {
        ShaderPack pack = selectedPack().orElse(null);
        if (pack == null || !pack.hasDamagedBlockPipeline() || !isDamagedBlockPipeline(original)) {
            return null;
        }
        if (damagedBlockPipelineDirty) {
            damagedBlockPipelineDirty = false;
            try {
                replaceDamagedBlockPipelines(compileDamagedBlockPipelines(pack));
                lastError = "";
                VeyraClient.LOGGER.info("Loaded {} damaged-block render pipeline(s) from shader pack '{}'{}.",
                        damagedBlockPipelineSet.pipelines().size(), pack.name(),
                        pack.hasDamagedBlockFragment() ? "" : " using gbuffers_terrain fallback");
            } catch (Exception exception) {
                lastError = shortError(exception);
                VeyraClient.LOGGER.error("Could not load gbuffers_damagedblock from shader pack '{}'.", pack.name(), exception);
                return null;
            }
        }
        RenderPipeline replacement = damagedBlockPipelineSet == null
                ? null : damagedBlockPipelineSet.pipelines().get(original);
        if (replacement != null && !damagedBlockRoutingLogged) {
            VeyraClient.LOGGER.info("Routing block-breaking overlay pipeline {} through gbuffers_damagedblock from shader pack '{}'.",
                    original.getLocation(), pack.name());
            damagedBlockRoutingLogged = true;
        }
        return replacement;
    }

    public static RenderPipeline getBeaconBeamPipeline(RenderPipeline original) {
        if (VeyraShadowRenderer.isRendering()) {
            return getShadowBeaconBeamPipeline(original);
        }
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

    private static RenderPipeline getShadowBeaconBeamPipeline(RenderPipeline original) {
        ShaderPack pack = selectedPack().orElse(null);
        if (pack == null || !pack.hasShadowFragment() || !isBeaconBeamPipeline(original)) {
            return null;
        }
        if (shadowBeaconBeamPipelinePack != pack) {
            shadowBeaconBeamPipelinePack = pack;
            shadowBeaconBeamPipelineSet = null;
            shadowBeaconBeamPipelineAttempted = false;
            shadowBeaconBeamRoutingLogged = false;
        }
        if (!shadowBeaconBeamPipelineAttempted) {
            shadowBeaconBeamPipelineAttempted = true;
            try {
                shadowBeaconBeamPipelineSet = compileShadowBeaconBeamPipelines(pack);
                VeyraClient.LOGGER.info("Loaded {} beacon beam shadow pipelines from shader pack '{}'.",
                        shadowBeaconBeamPipelineSet.pipelines().size(), pack.name());
            } catch (Exception exception) {
                lastError = shortError(exception);
                VeyraClient.LOGGER.error("Could not load the shadow_entities program for beacon beams from shader pack '{}'.",
                        pack.name(), exception);
            }
        }
        RenderPipeline replacement = shadowBeaconBeamPipelineSet == null
                ? null : shadowBeaconBeamPipelineSet.pipelines().get(original);
        if (replacement != null && !shadowBeaconBeamRoutingLogged) {
            VeyraClient.LOGGER.info("Rendering beacon beams with shadow_entities from shader pack '{}'.", pack.name());
            shadowBeaconBeamRoutingLogged = true;
        }
        return replacement;
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

    public static RenderPipeline getLightningPipeline(RenderPipeline original) {
        ShaderPack pack = selectedPack().orElse(null);
        if (pack == null || !pack.hasLightningFragment() || !isLightningPipeline(original)) {
            return null;
        }
        if (lightningPipelineDirty) {
            lightningPipelineDirty = false;
            try {
                replaceLightningPipelines(compileLightningPipelines(pack));
                lastError = "";
                VeyraClient.LOGGER.info("Loaded {} lightning render pipelines from shader pack '{}'.",
                        lightningPipelineSet.pipelines().size(), pack.name());
            } catch (Exception exception) {
                lastError = shortError(exception);
                VeyraClient.LOGGER.error("Could not load gbuffers_lightning from shader pack '{}'.", pack.name(), exception);
                return null;
            }
        }
        return lightningPipelineSet == null ? null : lightningPipelineSet.pipelines().get(original);
    }

    public static RenderPipeline getBasicPipeline(RenderPipeline original) {
        ShaderPack pack = selectedPack().orElse(null);
        if (pack == null || !pack.hasBasicFragment() || !isLeashPipeline(original)) {
            return null;
        }
        if (basicPipelineDirty) {
            basicPipelineDirty = false;
            try {
                replaceBasicPipelines(compileBasicPipelines(pack));
                lastError = "";
                VeyraClient.LOGGER.info("Loaded {} basic render pipeline(s) from shader pack '{}'.",
                        basicPipelineSet.pipelines().size(), pack.name());
            } catch (Exception exception) {
                lastError = shortError(exception);
                VeyraClient.LOGGER.error("Could not load gbuffers_basic from shader pack '{}'.", pack.name(), exception);
                return null;
            }
        }
        return basicPipelineSet == null ? null : basicPipelineSet.pipelines().get(original);
    }

    public static RenderPipeline getLinePipeline(RenderPipeline original) {
        ShaderPack pack = selectedPack().orElse(null);
        if (pack == null || !(pack.hasLineFragment() || pack.hasBasicFragment()) || !isLinePipeline(original)) {
            return null;
        }
        if (linePipelineDirty) {
            linePipelineDirty = false;
            try {
                replaceLinePipelines(compileLinePipelines(pack));
                lastError = "";
                VeyraClient.LOGGER.info("Loaded {} line render pipeline(s) from shader pack '{}'.",
                        linePipelineSet.pipelines().size(), pack.name());
            } catch (Exception exception) {
                lastError = shortError(exception);
                VeyraClient.LOGGER.error("Could not load gbuffers_line from shader pack '{}'.", pack.name(), exception);
                return null;
            }
        }
        return linePipelineSet == null ? null : linePipelineSet.pipelines().get(original);
    }

    public static RenderPipeline getGlintPipeline(RenderPipeline original) {
        ShaderPack pack = selectedPack().orElse(null);
        if (pack == null || !(pack.hasGlintFragment() || pack.hasTexturedFragment()) || !isGlintPipeline(original)) {
            return null;
        }
        if (glintPipelineDirty) {
            glintPipelineDirty = false;
            try {
                replaceGlintPipelines(compileGlintPipelines(pack));
                lastError = "";
                VeyraClient.LOGGER.info("Loaded {} glint render pipeline(s) from shader pack '{}'{}.",
                        glintPipelineSet.pipelines().size(), pack.name(),
                        pack.hasGlintFragment() ? "" : " using gbuffers_textured fallback");
            } catch (Exception exception) {
                lastError = shortError(exception);
                VeyraClient.LOGGER.error("Could not load gbuffers_armor_glint from shader pack '{}'.", pack.name(), exception);
                return null;
            }
        }
        return glintPipelineSet == null ? null : glintPipelineSet.pipelines().get(original);
    }

    public static RenderPipeline getSkyBasicPipeline(RenderPipeline original) {
        ShaderPack pack = selectedPack().orElse(null);
        if (pack == null || !(pack.hasSkyBasicFragment() || pack.hasBasicFragment()) || !isSkyBasicPipeline(original)) {
            return null;
        }
        if (skyBasicPipelineDirty) {
            skyBasicPipelineDirty = false;
            try {
                replaceSkyBasicPipelines(compileSkyBasicPipelines(pack));
                lastError = "";
                VeyraClient.LOGGER.info("Loaded {} basic sky render pipeline(s) from shader pack '{}'{}.",
                        skyBasicPipelineSet.pipelines().size(), pack.name(),
                        pack.hasSkyBasicFragment() ? "" : " using gbuffers_basic fallback");
            } catch (Exception exception) {
                lastError = shortError(exception);
                VeyraClient.LOGGER.error("Could not load gbuffers_skybasic from shader pack '{}'.", pack.name(), exception);
                return null;
            }
        }
        return skyBasicPipelineSet == null ? null : skyBasicPipelineSet.pipelines().get(original);
    }

    public static RenderPipeline getSkyTexturedPipeline(RenderPipeline original) {
        ShaderPack pack = selectedPack().orElse(null);
        if (pack == null || !(pack.hasSkyTexturedFragment() || pack.hasTexturedFragment()) || !isSkyTexturedPipeline(original)) {
            return null;
        }
        if (skyTexturedPipelineDirty) {
            skyTexturedPipelineDirty = false;
            try {
                replaceSkyTexturedPipelines(compileSkyTexturedPipelines(pack));
                lastError = "";
                VeyraClient.LOGGER.info("Loaded {} textured sky render pipeline(s) from shader pack '{}'{}.",
                        skyTexturedPipelineSet.pipelines().size(), pack.name(),
                        pack.hasSkyTexturedFragment() ? "" : " using gbuffers_textured fallback");
            } catch (Exception exception) {
                lastError = shortError(exception);
                VeyraClient.LOGGER.error("Could not load gbuffers_skytextured from shader pack '{}'.", pack.name(), exception);
                return null;
            }
        }
        return skyTexturedPipelineSet == null ? null : skyTexturedPipelineSet.pipelines().get(original);
    }

    public static RenderPipeline getWorldBorderPipeline(RenderPipeline original) {
        ShaderPack pack = selectedPack().orElse(null);
        if (pack == null || !pack.hasTexturedFragment() || !isWorldBorderPipeline(original)) {
            return null;
        }
        if (texturedPipelineDirty) {
            texturedPipelineDirty = false;
            try {
                replaceWorldBorderPipelines(compileWorldBorderPipelines(pack));
                lastError = "";
                VeyraClient.LOGGER.info("Loaded {} gbuffers_textured pipeline(s) from shader pack '{}'.",
                        worldBorderPipelineSet.pipelines().size(), pack.name());
            } catch (Exception exception) {
                lastError = shortError(exception);
                VeyraClient.LOGGER.error("Could not load gbuffers_textured from shader pack '{}'.", pack.name(), exception);
                return null;
            }
        }
        return worldBorderPipelineSet == null ? null : worldBorderPipelineSet.pipelines().get(original);
    }

    public static String getLastError() {
        return lastError;
    }

    private static void resetShadowPipelineState() {
        shadowEntityPipelinePack = null;
        shadowEntityPipelineSet = null;
        shadowBlockEntityPipelineSet = null;
        shadowEntityPipelineAttempted = false;
        shadowBlockEntityPipelineAttempted = false;
        shadowBeaconBeamPipelinePack = null;
        shadowBeaconBeamPipelineSet = null;
        shadowBeaconBeamPipelineAttempted = false;
    }

    private static void releaseDepthCapturePasses() {
        if (depthTex1CapturePass != null) depthTex1CapturePass.close();
        if (depthTex2CapturePass != null) depthTex2CapturePass.close();
        depthTex1CapturePass = null;
        depthTex2CapturePass = null;
        depthTex1CaptureProgram = null;
    }

    private static void purgeUnusedPipelines() {
        Set<RenderPipeline> live = Collections.newSetFromMap(new IdentityHashMap<>());
        if (pipelineSet != null) {
            pipelineSet.plansA().forEach(plan -> plan.shaderPasses().forEach(pass -> live.add(pass.program().pipeline())));
            pipelineSet.plansB().forEach(plan -> plan.shaderPasses().forEach(pass -> live.add(pass.program().pipeline())));
            PIPELINE_CACHE.values().stream().filter(program -> program.pipeline().getLocation().getPath().contains("/copy_"))
                    .forEach(program -> live.add(program.pipeline()));
        }
        if (depthTex1CaptureProgram != null) live.add(depthTex1CaptureProgram.pipeline());
        if (entityPipelineSet != null) live.addAll(entityPipelineSet.pipelines().values());
        if (shadowEntityPipelineSet != null) live.addAll(shadowEntityPipelineSet.pipelines().values());
        if (shadowBlockEntityPipelineSet != null) live.addAll(shadowBlockEntityPipelineSet.pipelines().values());
        if (blockEntityPipelineSet != null) live.addAll(blockEntityPipelineSet.pipelines().values());
        if (handPipelineSet != null) live.addAll(handPipelineSet.pipelines().values());
        if (particlePipelineSet != null) live.addAll(particlePipelineSet.pipelines().values());
        if (weatherPipelineSet != null) live.addAll(weatherPipelineSet.pipelines().values());
        if (lightningPipelineSet != null) live.addAll(lightningPipelineSet.pipelines().values());
        if (basicPipelineSet != null) live.addAll(basicPipelineSet.pipelines().values());
        if (linePipelineSet != null) live.addAll(linePipelineSet.pipelines().values());
        if (glintPipelineSet != null) live.addAll(glintPipelineSet.pipelines().values());
        if (skyBasicPipelineSet != null) live.addAll(skyBasicPipelineSet.pipelines().values());
        if (skyTexturedPipelineSet != null) live.addAll(skyTexturedPipelineSet.pipelines().values());
        if (worldBorderPipelineSet != null) live.addAll(worldBorderPipelineSet.pipelines().values());
        if (damagedBlockPipelineSet != null) live.addAll(damagedBlockPipelineSet.pipelines().values());
        if (beaconBeamPipelineSet != null) live.addAll(beaconBeamPipelineSet.pipelines().values());
        if (shadowBeaconBeamPipelineSet != null) live.addAll(shadowBeaconBeamPipelineSet.pipelines().values());
        ENTITY_PIPELINE_CACHE.values().removeIf(pipeline -> !live.contains(pipeline));
        PARTICLE_PIPELINE_CACHE.values().removeIf(pipeline -> !live.contains(pipeline));
        WEATHER_PIPELINE_CACHE.values().removeIf(pipeline -> !live.contains(pipeline));
        LIGHTNING_PIPELINE_CACHE.values().removeIf(pipeline -> !live.contains(pipeline));
        COLOR_DISTANCE_PIPELINE_CACHE.values().removeIf(pipeline -> !live.contains(pipeline));
        GLINT_PIPELINE_CACHE.values().removeIf(pipeline -> !live.contains(pipeline));
        SKY_BASIC_PIPELINE_CACHE.values().removeIf(pipeline -> !live.contains(pipeline));
        SKY_TEXTURED_PIPELINE_CACHE.values().removeIf(pipeline -> !live.contains(pipeline));
        TEXTURED_PIPELINE_CACHE.values().removeIf(pipeline -> !live.contains(pipeline));
        BEACON_BEAM_PIPELINE_CACHE.values().removeIf(pipeline -> !live.contains(pipeline));
        PIPELINE_CACHE.values().removeIf(program -> !live.contains(program.pipeline()));
        COMPILED_PIPELINES.removeIf(program -> !live.contains(program.pipeline()));
        VeyraPipelineResources.retain(live);
    }

    public static String selectWithSavedOptions(ShaderPack pack) {
        try { return select(repository.loadConfiguration(pack)); }
        catch (IOException exception) {
            lastError = shortError(exception);
            VeyraClient.LOGGER.error("Could not read shader options for {}", pack.name(), exception);
            return "Could not load options: " + lastError;
        }
    }

    public static String applyOptions(ShaderPack pack, Map<String, String> changed) {
        ShaderPack active = selectedPack().orElse(null);
        if (active == null || !active.path().equals(pack.path())) return "Select this shader pack before applying its options.";
        try {
            Map<String, String> validated = pack.readOptions().validate(changed);
            if (validated.equals(active.configuration().options())) return "Shader options unchanged.";
            return select(active.configured(validated, active.configuration().dimension()));
        } catch (IOException exception) {
            lastError = shortError(exception);
            return "Could not apply options: " + lastError;
        }
    }

    public static String refreshPacks() {
        ShaderPack previous = selectedPack().orElse(null);
        try {
            repository.refresh();
        } catch (IOException exception) {
            lastError = shortError(exception);
            VeyraClient.LOGGER.error("Could not refresh shader packs.", exception);
            return "Could not refresh packs: " + lastError;
        }
        if (previous == null) {
            return "Shader pack list refreshed.";
        }
        ShaderPack refreshed = repository.findAvailable(previous.path()).orElse(null);
        if (refreshed == null) {
            return "The active pack is no longer available. " + disable();
        }
        // select() compiles before committing the selection, retaining the old pipelines on failure.
        return selectWithSavedOptions(refreshed);
    }

    public static void onClientShutdown() {
        restoreUniqueOpaqueLayer();
        replacePipelineSet(null);
        replaceTerrainPipelines(null);
        replaceEntityPipelines(null);
        replaceBlockEntityPipelines(null);
        replaceHandPipelines(null);
        replaceParticlePipelines(null);
        replaceWeatherPipelines(null);
        replaceLightningPipelines(null);
        replaceBasicPipelines(null);
        replaceLinePipelines(null);
        replaceGlintPipelines(null);
        replaceSkyBasicPipelines(null);
        replaceSkyTexturedPipelines(null);
        replaceWorldBorderPipelines(null);
        replaceDamagedBlockPipelines(null);
        replaceBeaconBeamPipelines(null);
        replaceCloudPipeline(null);
        releaseColorTargets();
        releaseSwapTargets();
        VeyraShadowRenderer.release();
        resetShadowPipelineState();
        releaseDepthCapturePasses();
        purgeUnusedPipelines();
    }

    public static String select(ShaderPack pack) {
        PipelineSet prepared = null;
        TerrainPipelineSet preparedTerrain = null;
        EntityPipelineSet preparedEntities = null;
        EntityPipelineSet preparedBlockEntities = null;
        EntityPipelineSet preparedHand = null;
        ParticlePipelineSet preparedParticles = null;
        WeatherPipelineSet preparedWeather = null;
        LightningPipelineSet preparedLightning = null;
        ColorDistancePipelineSet preparedBasic = null;
        ColorDistancePipelineSet preparedLines = null;
        GlintPipelineSet preparedGlint = null;
        SkyPipelineSet preparedSkyBasic = null;
        SkyPipelineSet preparedSkyTextured = null;
        WorldBorderPipelineSet preparedTextured = null;
        DamagedBlockPipelineSet preparedDamagedBlocks = null;
        BeaconBeamPipelineSet preparedBeaconBeams = null;
        GraphicsPipeline preparedClouds = null;
        try {
            pack.validatePipelineCapabilities();
            ShadowSettings preparedShadowSettings = pack.readShadowSettings();
            RenderTarget mainTarget = Minecraft.getInstance().getMainRenderTarget();
            prepared = compilePipelines(pack, mainTarget.width, mainTarget.height);
            if (pack.hasTerrainFragment() || pack.hasShadowFragment()) {
                preparedTerrain = compileTerrainPipelines(pack);
            }
            if (pack.hasEntityFragment()) {
                preparedEntities = compileEntityPipelines(pack);
            }
            if (pack.supportsBlockEntityPipeline()) {
                preparedBlockEntities = compileBlockEntityPipelines(pack);
            }
            if (pack.supportsHandPipeline()) {
                preparedHand = compileEntityPipelines(pack, true);
            }
            if (pack.hasParticleFragment()) {
                preparedParticles = compileParticlePipelines(pack);
            }
            if (pack.hasWeatherFragment()) {
                preparedWeather = compileWeatherPipelines(pack);
            }
            if (pack.hasLightningFragment()) {
                preparedLightning = compileLightningPipelines(pack);
            }
            if (pack.hasBasicFragment()) {
                preparedBasic = compileBasicPipelines(pack);
            }
            if (pack.hasLineFragment() || pack.hasBasicFragment()) {
                preparedLines = compileLinePipelines(pack);
            }
            if (pack.hasGlintFragment() || pack.hasTexturedFragment()) {
                preparedGlint = compileGlintPipelines(pack);
            }
            if (pack.hasSkyBasicFragment() || pack.hasBasicFragment()) {
                preparedSkyBasic = compileSkyBasicPipelines(pack);
            }
            if (pack.hasSkyTexturedFragment() || pack.hasTexturedFragment()) {
                preparedSkyTextured = compileSkyTexturedPipelines(pack);
            }
            if (pack.hasTexturedFragment()) {
                preparedTextured = compileWorldBorderPipelines(pack);
            }
            if (pack.hasDamagedBlockPipeline()) {
                preparedDamagedBlocks = compileDamagedBlockPipelines(pack);
            }
            if (pack.hasBeaconBeamFragment()) {
                preparedBeaconBeams = compileBeaconBeamPipelines(pack);
            }
            if (pack.hasCloudFragment()) {
                preparedClouds = compileCloudPipeline(pack);
            }
            repository.select(pack);
            VeyraShadowRenderer.configure(preparedShadowSettings);
            updateTerrainLayerMode(pack);
            replacePipelineSet(prepared);
            prepared = null;
            replaceTerrainPipelines(preparedTerrain);
            preparedTerrain = null;
            replaceEntityPipelines(preparedEntities);
            preparedEntities = null;
            replaceBlockEntityPipelines(preparedBlockEntities);
            preparedBlockEntities = null;
            replaceHandPipelines(preparedHand);
            preparedHand = null;
            replaceParticlePipelines(preparedParticles);
            preparedParticles = null;
            replaceWeatherPipelines(preparedWeather);
            preparedWeather = null;
            replaceLightningPipelines(preparedLightning);
            preparedLightning = null;
            replaceBasicPipelines(preparedBasic);
            preparedBasic = null;
            replaceLinePipelines(preparedLines);
            preparedLines = null;
            replaceGlintPipelines(preparedGlint);
            preparedGlint = null;
            replaceSkyBasicPipelines(preparedSkyBasic);
            preparedSkyBasic = null;
            replaceSkyTexturedPipelines(preparedSkyTextured);
            preparedSkyTextured = null;
            replaceWorldBorderPipelines(preparedTextured);
            preparedTextured = null;
            replaceDamagedBlockPipelines(preparedDamagedBlocks);
            preparedDamagedBlocks = null;
            replaceBeaconBeamPipelines(preparedBeaconBeams);
            preparedBeaconBeams = null;
            replaceCloudPipeline(preparedClouds);
            preparedClouds = null;
            pipelinesDirty = false;
            terrainPipelineDirty = false;
            entityPipelineDirty = false;
            blockEntityPipelineDirty = false;
            handPipelineDirty = false;
            particlePipelineDirty = false;
            weatherPipelineDirty = false;
            lightningPipelineDirty = false;
            basicPipelineDirty = false;
            linePipelineDirty = false;
            glintPipelineDirty = false;
            skyBasicPipelineDirty = false;
            skyTexturedPipelineDirty = false;
            texturedPipelineDirty = false;
            damagedBlockPipelineDirty = false;
            beaconBeamPipelineDirty = false;
            cloudPipelineDirty = false;
            nextPostPhase = 0;
            renderingEnabled = pack.supportsShaderPrograms();
            lastError = "";
            boolean hasTerrainPipelines = terrainPipelineSet != null && !terrainPipelineSet.pipelines().isEmpty();
            int terrainProgramCount = hasTerrainPipelines ? terrainPipelineSet.pipelines().size() : 0;
            int entityPipelineCount = entityPipelineSet == null ? 0 : entityPipelineSet.pipelines().size();
            int blockEntityPipelineCount = blockEntityPipelineSet == null ? 0 : blockEntityPipelineSet.pipelines().size();
            int handPipelineCount = handPipelineSet == null ? 0 : handPipelineSet.pipelines().size();
            int particlePipelineCount = particlePipelineSet == null ? 0 : particlePipelineSet.pipelines().size();
            int weatherPipelineCount = weatherPipelineSet == null ? 0 : weatherPipelineSet.pipelines().size();
            int lightningPipelineCount = lightningPipelineSet == null ? 0 : lightningPipelineSet.pipelines().size();
            int basicPipelineCount = basicPipelineSet == null ? 0 : basicPipelineSet.pipelines().size();
            int linePipelineCount = linePipelineSet == null ? 0 : linePipelineSet.pipelines().size();
            int glintPipelineCount = glintPipelineSet == null ? 0 : glintPipelineSet.pipelines().size();
            int skyBasicPipelineCount = skyBasicPipelineSet == null ? 0 : skyBasicPipelineSet.pipelines().size();
            int skyTexturedPipelineCount = skyTexturedPipelineSet == null ? 0 : skyTexturedPipelineSet.pipelines().size();
            int texturedPipelineCount = worldBorderPipelineSet == null ? 0 : worldBorderPipelineSet.pipelines().size();
            int damagedBlockPipelineCount = damagedBlockPipelineSet == null ? 0 : damagedBlockPipelineSet.pipelines().size();
            int beaconBeamPipelineCount = beaconBeamPipelineSet == null ? 0 : beaconBeamPipelineSet.pipelines().size();
            VeyraClient.LOGGER.info("Enabled {} post-processing pass(es), {} terrain program(s), {} entity pipeline(s), {} block entity pipeline(s), {} hand pipeline(s), {} damaged-block pipeline(s), {} particle pipeline(s), {} weather pipeline(s), {} lightning pipeline(s), {} basic pipeline(s), {} line pipeline(s), {} glint pipeline(s), {} basic sky pipeline(s), {} textured sky pipeline(s), {} textured world-border pipeline(s), {} beacon beam pipeline(s), clouds={} from shader pack '{}'.",
                    pipelineSet.passCount(), terrainProgramCount, entityPipelineCount, blockEntityPipelineCount, handPipelineCount, damagedBlockPipelineCount,
                    particlePipelineCount, weatherPipelineCount, lightningPipelineCount, basicPipelineCount, linePipelineCount,
                    glintPipelineCount, skyBasicPipelineCount, skyTexturedPipelineCount,
                    texturedPipelineCount, beaconBeamPipelineCount, cloudPipeline != null, pack.name());
            List<String> features = new ArrayList<>();
            if (pipelineSet.passCount() > 0) {
                features.add(pipelineSet.passCount() + " post passes");
            }
            if (terrainProgramCount > 0) {
                features.add(terrainProgramCount + " terrain programs");
            }
            if (terrainPipelineSet != null && terrainPipelineSet.hasShadowPipelines()) {
                features.add("terrain shadow map");
            }
            if (cloudPipeline != null) {
                features.add("cloud shader");
            }
            if (entityPipelineCount > 0) {
                features.add(entityPipelineCount + " entity pipelines");
            }
            if (blockEntityPipelineCount > 0) {
                features.add(blockEntityPipelineCount + " block entity pipelines");
            }
            if (handPipelineCount > 0) {
                features.add(handPipelineCount + " first-person hand pipelines");
            }
            if (damagedBlockPipelineCount > 0) {
                features.add(damagedBlockPipelineCount + " damaged-block pipelines");
            }
            if (particlePipelineCount > 0) {
                features.add(particlePipelineCount + " particle pipelines");
            }
            if (weatherPipelineCount > 0) {
                features.add(weatherPipelineCount + " weather pipelines");
            }
            if (lightningPipelineCount > 0) {
                features.add(lightningPipelineCount + " lightning pipelines");
            }
            if (basicPipelineCount > 0) {
                features.add(basicPipelineCount + " basic pipelines");
            }
            if (linePipelineCount > 0) {
                features.add(linePipelineCount + " line pipelines");
            }
            if (glintPipelineCount > 0) {
                features.add(glintPipelineCount + " glint pipelines");
            }
            if (skyBasicPipelineCount > 0) {
                features.add(skyBasicPipelineCount + " basic sky pipelines");
            }
            if (skyTexturedPipelineCount > 0) {
                features.add(skyTexturedPipelineCount + " textured sky pipelines");
            }
            if (texturedPipelineCount > 0) {
                features.add(texturedPipelineCount + " gbuffers_textured pipelines");
            }
            if (beaconBeamPipelineCount > 0) {
                features.add(beaconBeamPipelineCount + " beacon beam pipelines");
            }
            resetShadowPipelineState();
            purgeUnusedPipelines();
            return "Enabled " + pack.name() + " (" + String.join(", ", features) + ").";
        } catch (Exception exception) {
            if (prepared != null) {
                prepared.close();
            }
            if (preparedTerrain != null) {
                preparedTerrain.close();
            }
            preparedEntities = null;
            preparedHand = null;
            preparedParticles = null;
            preparedWeather = null;
            preparedLightning = null;
            preparedBasic = null;
            preparedLines = null;
            preparedGlint = null;
            preparedSkyBasic = null;
            preparedSkyTextured = null;
            preparedTextured = null;
            preparedDamagedBlocks = null;
            preparedBeaconBeams = null;
            if (preparedClouds != null) {
                preparedClouds.scheduleCleanUp();
            }
            purgeUnusedPipelines();
            lastError = shortError(exception);
            VeyraClient.LOGGER.error("Could not enable shader pack '{}'.", pack.name(), exception);
            return "Shader compile failed: " + lastError;
        }
    }

    public static String disable() {
        IOException saveFailure = null;
        try {
            repository.select(null);
        } catch (IOException exception) {
            saveFailure = exception;
            VeyraClient.LOGGER.error("Disabled shaders, but could not save the selection.", exception);
        }
        // Disabling must remain possible even when the configuration directory is read-only.
        repository.clearSelection();
        VeyraShadowRenderer.configure(ShadowSettings.DEFAULT);
        updateTerrainLayerMode(null);
        renderingEnabled = false;
        pipelinesDirty = false;
        terrainPipelineDirty = false;
        entityPipelineDirty = false;
        blockEntityPipelineDirty = false;
        handPipelineDirty = false;
        particlePipelineDirty = false;
        weatherPipelineDirty = false;
        lightningPipelineDirty = false;
        basicPipelineDirty = false;
        linePipelineDirty = false;
        glintPipelineDirty = false;
        skyBasicPipelineDirty = false;
        skyTexturedPipelineDirty = false;
        texturedPipelineDirty = false;
        damagedBlockPipelineDirty = false;
        beaconBeamPipelineDirty = false;
        cloudPipelineDirty = false;
        replacePipelineSet(null);
        replaceTerrainPipelines(null);
        replaceEntityPipelines(null);
        replaceBlockEntityPipelines(null);
        replaceHandPipelines(null);
        replaceParticlePipelines(null);
        replaceWeatherPipelines(null);
        replaceLightningPipelines(null);
        replaceBasicPipelines(null);
        replaceLinePipelines(null);
        replaceGlintPipelines(null);
        replaceSkyBasicPipelines(null);
        replaceSkyTexturedPipelines(null);
        replaceWorldBorderPipelines(null);
        replaceDamagedBlockPipelines(null);
        replaceBeaconBeamPipelines(null);
        replaceCloudPipeline(null);
        releaseColorTargets();
        releaseSwapTargets();
        VeyraShadowRenderer.release();
        resetShadowPipelineState();
        releaseDepthCapturePasses();
        purgeUnusedPipelines();
        lastError = saveFailure == null ? "" : shortError(saveFailure);
        return saveFailure == null ? "Shader pack disabled."
                : "Shaders disabled for this session. Could not save the setting: " + lastError;
    }

    public static void invalidatePipelines() {
        nextPostPhase = 0;
        pipelinesDirty = true;
        projectionBuffer = null;
        resetShadowPipelineState();
        releaseDepthCapturePasses();
        PIPELINE_CACHE.clear();
        COMPILED_PIPELINES.clear();
        replacePipelineSet(null);
        replaceTerrainPipelines(null);
        replaceEntityPipelines(null);
        replaceBlockEntityPipelines(null);
        replaceHandPipelines(null);
        replaceParticlePipelines(null);
        replaceWeatherPipelines(null);
        replaceLightningPipelines(null);
        replaceBasicPipelines(null);
        replaceLinePipelines(null);
        replaceGlintPipelines(null);
        replaceSkyBasicPipelines(null);
        replaceSkyTexturedPipelines(null);
        replaceWorldBorderPipelines(null);
        replaceDamagedBlockPipelines(null);
        replaceBeaconBeamPipelines(null);
        replaceCloudPipeline(null);
        purgeUnusedPipelines();
        terrainPipelineDirty = selectedPack().map(pack -> pack.hasTerrainFragment() || pack.hasShadowFragment()).orElse(false);
        entityPipelineDirty = selectedPack().map(ShaderPack::hasEntityFragment).orElse(false);
        blockEntityPipelineDirty = selectedPack().map(ShaderPack::supportsBlockEntityPipeline).orElse(false);
        handPipelineDirty = selectedPack().map(ShaderPack::supportsHandPipeline).orElse(false);
        particlePipelineDirty = selectedPack().map(ShaderPack::hasParticleFragment).orElse(false);
        weatherPipelineDirty = selectedPack().map(ShaderPack::hasWeatherFragment).orElse(false);
        lightningPipelineDirty = selectedPack().map(ShaderPack::hasLightningFragment).orElse(false);
        basicPipelineDirty = selectedPack().map(ShaderPack::hasBasicFragment).orElse(false);
        linePipelineDirty = selectedPack().map(pack -> pack.hasLineFragment() || pack.hasBasicFragment()).orElse(false);
        glintPipelineDirty = selectedPack().map(pack -> pack.hasGlintFragment() || pack.hasTexturedFragment()).orElse(false);
        skyBasicPipelineDirty = selectedPack().map(pack -> pack.hasSkyBasicFragment() || pack.hasBasicFragment()).orElse(false);
        skyTexturedPipelineDirty = selectedPack().map(pack -> pack.hasSkyTexturedFragment() || pack.hasTexturedFragment()).orElse(false);
        texturedPipelineDirty = selectedPack().map(ShaderPack::hasTexturedFragment).orElse(false);
        damagedBlockPipelineDirty = selectedPack().map(ShaderPack::hasDamagedBlockPipeline).orElse(false);
        beaconBeamPipelineDirty = selectedPack().map(ShaderPack::hasBeaconBeamFragment).orElse(false);
        cloudPipelineDirty = selectedPack().map(ShaderPack::hasCloudFragment).orElse(false);
    }

    private static int nextPostPhase;

    public static void renderPrepare(Minecraft minecraft) { renderThroughPhase(minecraft, ShaderPassPhase.PREPARE); }
    public static void renderDeferred(Minecraft minecraft) { renderThroughPhase(minecraft, ShaderPassPhase.DEFERRED); }
    public static void render(Minecraft minecraft) { renderThroughPhase(minecraft, ShaderPassPhase.COMPOSITE); }

    private static void renderThroughPhase(Minecraft minecraft, ShaderPassPhase requested) {
        while (nextPostPhase <= requested.ordinal() && isRenderingEnabled()) {
            ShaderPassPhase phase = ShaderPassPhase.values()[nextPostPhase++];
            renderPhase(minecraft, phase);
        }
    }

    private static void renderPhase(Minecraft minecraft, ShaderPassPhase phase) {
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
            if (pipelineSet.usesShadowSampler() && VeyraShadowRenderer.shadowDepthTextureView() == null) {
                throw new IllegalStateException("The shader pack samples shadowtex0, but VulkanMod did not produce a shadow map.");
            }
            ensureSwapTargets(mainTarget.width, mainTarget.height);
            ensureColorTargets(mainTarget.width, mainTarget.height, pipelineSet.colorTargetIndices(),
                    pipelineSet.colorTargetSettings());
            if (phase == ShaderPassPhase.PREPARE) clearColorTargetBuffers(pipelineSet.colorTargetIndices(), pipelineSet.colorTargetSettings());
            FramePlan framePlan = pipelineSet.framePlan(phase, currentColorTargets);
            if (framePlan.shaderPasses().isEmpty()) return;
            ensureInitialDepthSnapshots(framePlan, mainTarget.width, mainTarget.height);
            if (Renderer.getInstance().getBoundRenderPass() != null) Renderer.getInstance().endRenderPass();

            FrameGraphBuilder graph = new FrameGraphBuilder();
            ResourceHandle<RenderTarget> main = graph.importExternal("main", mainTarget);
            ResourceHandle<RenderTarget> swapA = graph.importExternal("veyra:shaderpack-swap-a", swapTargetA);
            ResourceHandle<RenderTarget> swapB = graph.importExternal("veyra:shaderpack-swap-b", swapTargetB);
            Map<Identifier, ResourceHandle<RenderTarget>> targets = new HashMap<>();
            targets.put(PostChain.MAIN_TARGET_ID, main);
            targets.put(SWAP_TARGET_A_ID, swapA);
            targets.put(SWAP_TARGET_B_ID, swapB);
            for (int index : pipelineSet.colorTargetIndices()) {
                ColorTargetPair pair = colorTargets.get(index);
                targets.put(colorTargetAId(index), graph.importExternal(
                        "veyra:shaderpack-colortex" + index + "-a", pair.targetA()));
                targets.put(colorTargetBId(index), graph.importExternal(
                        "veyra:shaderpack-colortex" + index + "-b", pair.targetB()));
            }

            var samplerInfo = projectionBuffer().getBuffer(mainTarget.width, mainTarget.height);
            FrameUniforms frameUniforms = shaderPackFrameUniforms;
            for (ShaderPass shaderPass : framePlan.shaderPasses()) {
                updateDynamicUniforms(shaderPass, mainTarget.width, mainTarget.height, frameUniforms);
                shaderPass.pass().addToFrame(graph, targets, samplerInfo);
            }
            if (framePlan.copyPass() != null) {
                framePlan.copyPass().addToFrame(graph, targets, samplerInfo);
            }
            DepthSamplerState depthSamplerState = null;
            if (framePlan.shaderPasses().stream().anyMatch(pass -> pass.program().usesDepthSampler())) {
                depthSampling = true;
                depthSamplerState = prepareDepthSampler();
            }
            graph.execute(GraphicsResourceAllocator.UNPOOLED);
            if (Renderer.getInstance().getBoundRenderPass() != null) Renderer.getInstance().endRenderPass();
            if (depthSamplerState != null) {
                restoreDepthSampler(depthSamplerState);
            }
            if (!pipelineSet.colorTargetIndices().isEmpty()) {
                currentColorTargets.clear();
                currentColorTargets.putAll(framePlan.outputColorTargets());
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
        renderingEnabled = pack.supportsShaderPrograms();
        lastError = "";
        VeyraClient.LOGGER.info("Loaded {} post-processing pass(es) from shader pack '{}'.",
                pipelineSet.passCount(), pack.name());
    }

    private static PipelineSet compilePipelines(ShaderPack pack, int width, int height) throws IOException {
        pack.validatePipelineCapabilities();
        List<ShaderPack.Program> sources = pack.readPrograms();
        Map<Integer, ColorTargetSettings> colorTargetSettings = new HashMap<>(pack.readColorTargetSettings(
                width, height, RenderSystem.getDevice().getMaxTextureSize()));
        List<ProgramGroup> programs = new ArrayList<>(sources.size());
        boolean usesDepthSampler = false;
        boolean usesDepth1Sampler = false;
        boolean usesDepth2Sampler = false;
        boolean usesShadowSampler = false;
        Set<Integer> colorTargetIndices = new java.util.TreeSet<>();
        for (String name : pack.terrainPrograms()) {
            var fragment = pack.readTerrainFragment(name);
            if (fragment.isPresent()) FragmentOutputs.parse(fragment.get()).targets().stream()
                    .filter(index -> index > 0).forEach(colorTargetIndices::add);
        }
        for (ShaderPack.Program source : sources) {
            List<PipelineProgram> outputs = new ArrayList<>();
            List<String> vertexUniforms = source.vertexSource()
                    .map(shader -> readPostVertexUniforms(shader, source.name()))
                    .orElse(List.of());
            for (AdaptedShader adapted : adaptShader(source.source(), vertexUniforms, pack.hasShadowFragment())) {
                String vertexSource = source.vertexSource()
                        .map(shader -> adaptPostVertexShader(shader, source.name(), adapted.uniforms()))
                        .orElse(null);
                String variantName = source.name() + "_colortex" + adapted.outputTarget();
                PipelineProgram program = pipelineProgram(variantName, adapted, vertexSource);
                compilePipeline(program);
                outputs.add(program);
                usesDepthSampler |= adapted.usesDepthSampler();
                usesDepth1Sampler |= adapted.usesDepth1Sampler();
                usesDepth2Sampler |= adapted.usesDepth2Sampler();
                usesShadowSampler |= adapted.usesShadowSampler();
                adapted.colorSamplers().stream().filter(index -> index > 0).forEach(colorTargetIndices::add);
                if (adapted.outputTarget() > 0) {
                    colorTargetIndices.add(adapted.outputTarget());
                }
            }
            programs.add(new ProgramGroup(ShaderPassPhase.forProgram(source.name()), List.copyOf(outputs)));
        }

        for (int index : colorTargetIndices) {
            colorTargetSettings.putIfAbsent(index, ColorTargetSettings.defaults(index, width, height));
        }
        validateColorTargetFormats(colorTargetIndices, colorTargetSettings);

        PipelineProgram copyProgram = null;
        if (!programs.isEmpty()) {
            copyProgram = pipelineProgram("copy",
                    new AdaptedShader(COPY_FRAGMENT_SHADER, List.of(), false, false, false, false, Set.of(), 0), null);
            compilePipeline(copyProgram);
        }

        List<FramePlan> firstPlans = null;
        List<FramePlan> secondPlans = null;
        try {
            Map<Integer, Identifier> initialColorTargets = new HashMap<>();
            for (int index : colorTargetIndices) initialColorTargets.put(index, colorTargetAId(index));
            firstPlans = buildPhasePlans(programs, copyProgram, width, height, initialColorTargets);
            Map<Integer, Identifier> secondInputs = firstPlans.getLast().outputColorTargets();
            secondPlans = initialColorTargets.equals(secondInputs) ? firstPlans
                    : buildPhasePlans(programs, copyProgram, width, height, secondInputs);
            return new PipelineSet(firstPlans, secondPlans, width, height,
                    usesDepthSampler, usesDepth1Sampler, usesDepth2Sampler, usesShadowSampler,
                    Map.copyOf(colorTargetSettings), Set.copyOf(colorTargetIndices));
        } catch (RuntimeException exception) {
            if (firstPlans != null) firstPlans.forEach(FramePlan::close);
            if (secondPlans != null && secondPlans != firstPlans) secondPlans.forEach(FramePlan::close);
            throw exception;
        }
    }

    private static List<FramePlan> buildPhasePlans(List<ProgramGroup> groups, PipelineProgram copy,
            int width, int height, Map<Integer, Identifier> initial) {
        List<FramePlan> plans = new ArrayList<>();
        Map<Integer, Identifier> current = initial;
        try {
            for (ShaderPassPhase phase : ShaderPassPhase.values()) {
                // Geometry between phases writes the main scene target. Rebase colortex0 to main,
                // while preserving the physical ping-pong sides of every auxiliary color buffer.
                FramePlan plan = buildFramePlan(groups.stream().filter(group -> group.phase() == phase).toList(),
                        copy, width, height, current);
                plans.add(plan);
                current = plan.outputColorTargets();
            }
            return List.copyOf(plans);
        } catch (RuntimeException exception) { plans.forEach(FramePlan::close); throw exception; }
    }

    private static void ensureInitialDepthSnapshots(FramePlan plan, int width, int height) {
        GpuTexture depth = Renderer.getInstance().getMainPass().getDepthAttachment();
        if (!(depth instanceof VkGpuTexture vulkanDepth)) return;
        int format = vulkanDepth.getVulkanImage().format;
        boolean needs1 = plan.shaderPasses().stream().anyMatch(pass -> pass.program().usesDepth1Sampler());
        boolean needs2 = plan.shaderPasses().stream().anyMatch(pass -> pass.program().usesDepth2Sampler());
        if (needs1) initializeDepthHistory(depthTex1Snapshot, width, height, format);
        if (needs2) initializeDepthHistory(depthTex2Snapshot, width, height, format);
    }

    private static void initializeDepthHistory(VeyraDepthTextureSnapshot snapshot, int width, int height, int format) {
        if (snapshot.isReady(width, height, format)) return;
        snapshot.ensure(width, height, format);
        snapshot.clearToFarDepth();
    }

    private static void validateColorTargetFormats(Set<Integer> indices,
                                                   Map<Integer, ColorTargetSettings> settings) throws IOException {
        if (DeviceManager.physicalDevice == null) {
            throw new IOException("VulkanMod has not selected a Vulkan device yet.");
        }
        int requiredFeatures = VK10.VK_FORMAT_FEATURE_COLOR_ATTACHMENT_BIT
                | VK10.VK_FORMAT_FEATURE_SAMPLED_IMAGE_BIT
                | VK10.VK_FORMAT_FEATURE_SAMPLED_IMAGE_FILTER_LINEAR_BIT;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkFormatProperties properties = VkFormatProperties.calloc(stack);
            for (int index : indices) {
                ColorTargetSettings target = settings.get(index);
                VK10.vkGetPhysicalDeviceFormatProperties(DeviceManager.physicalDevice, target.format(), properties);
                if ((properties.optimalTilingFeatures() & requiredFeatures) != requiredFeatures) {
                    throw new IOException("colortex" + index + " format " + ColorTargetSettings.formatName(target.format())
                            + " does not support sampled, linearly-filtered color attachments on this Vulkan device.");
                }
            }
        }
    }

    private static List<String> readPostVertexUniforms(String source, String programName) {
        if (source == null || source.isBlank()) {
            throw new IllegalArgumentException(programName + ".vsh is empty.");
        }
        if (source.length() > 1024 * 1024) {
            throw new IllegalArgumentException(programName + ".vsh exceeds the 1 MiB size limit.");
        }

        String uncommented = COMMENTS.matcher(source.replace("\uFEFF", "")).replaceAll("");
        Matcher matcher = POST_VERTEX_UNIFORM_DECLARATION.matcher(uncommented);
        Set<String> declaredUniforms = new HashSet<>();
        while (matcher.find()) {
            String type = matcher.group(1);
            String name = matcher.group(2);
            String supportedType = shaderPackUniformType(name);
            if (supportedType == null) {
                throw new IllegalArgumentException(programName + ".vsh declares unsupported custom uniform '" + name + "'.");
            }
            boolean validType = BOOLEAN_UNIFORMS.contains(name)
                    ? type.equals("bool") || type.equals("int")
                    : type.equals(supportedType);
            if (!validType) {
                throw new IllegalArgumentException(programName + ".vsh uniform '" + name
                        + "' must use " + (BOOLEAN_UNIFORMS.contains(name) ? "bool or int" : supportedType) + ".");
            }
            if (!declaredUniforms.add(name)) {
                throw new IllegalArgumentException(programName + ".vsh declares uniform '" + name + "' more than once.");
            }
        }
        String withoutSupportedUniforms = POST_VERTEX_UNIFORM_DECLARATION.matcher(uncommented).replaceAll("");
        if (UNSUPPORTED_VERTEX_UNIFORM_DECLARATION.matcher(withoutSupportedUniforms).find()) {
            throw new IllegalArgumentException(programName
                    + ".vsh uses an unsupported uniform declaration; only Veyra's built-in shader-pack uniforms are available.");
        }
        return SUPPORTED_UNIFORMS.stream().filter(declaredUniforms::contains).toList();
    }

    private static String shaderPackUniformType(String uniform) {
        if (MATRIX_UNIFORMS.contains(uniform)) {
            return "mat4";
        }
        if (IVECTOR3_UNIFORMS.contains(uniform)) {
            return "ivec3";
        }
        if (VECTOR3_UNIFORMS.contains(uniform)) {
            return "vec3";
        }
        if (INTEGER_UNIFORMS.contains(uniform)) {
            return "int";
        }
        return SUPPORTED_UNIFORMS.contains(uniform) ? "float" : null;
    }

    private static void appendShaderPackUniformBlock(StringBuilder source, List<String> uniforms) {
        if (uniforms.isEmpty()) {
            return;
        }
        source.append("layout(std140) uniform ").append(SHADERPACK_UNIFORMS).append(" {\n");
        for (String uniform : uniforms) {
            String type = shaderPackUniformType(uniform);
            // VulkanMod's shader parser rejects bool members in uniform blocks, so expose them through int fields.
            String memberName = BOOLEAN_UNIFORMS.contains(uniform) ? "veyra_" + uniform : uniform;
            source.append("    ").append(type).append(' ').append(memberName).append(";\n");
        }
        source.append("};\n");
        for (String uniform : uniforms) {
            if (BOOLEAN_UNIFORMS.contains(uniform)) {
                source.append("#define ").append(uniform).append(" (veyra_")
                        .append(uniform).append(" != 0)\n");
            }
        }
    }

    private static String adaptPostVertexShader(String source, String programName, List<String> uniforms) {
        if (source == null || source.isBlank()) {
            throw new IllegalArgumentException(programName + ".vsh is empty.");
        }
        if (source.length() > 1024 * 1024) {
            throw new IllegalArgumentException(programName + ".vsh exceeds the 1 MiB size limit.");
        }

        String body = source.replace("\uFEFF", "");
        StringBuilder extensions = new StringBuilder();
        Matcher extensionMatcher = EXTENSION_DIRECTIVE.matcher(body);
        while (extensionMatcher.find()) {
            extensions.append(extensionMatcher.group());
        }
        body = extensionMatcher.replaceAll("");
        body = VERSION_DIRECTIVE.matcher(body).replaceAll("");
        body = Pattern.compile("(?m)^[\\t ]*precision\\s+(?:lowp|mediump|highp)\\s+\\w+\\s*;[\\t ]*(?:\\r?\\n|$)")
                .matcher(body).replaceAll("");
        body = COMMENTS.matcher(body).replaceAll("");

        if (UNSUPPORTED_VERTEX_UNIFORM_DECLARATION.matcher(POST_VERTEX_UNIFORM_DECLARATION.matcher(body).replaceAll(""))
                .find()) {
            throw new IllegalArgumentException(programName
                    + ".vsh uses an unsupported uniform declaration; only Veyra's built-in shader-pack uniforms are available.");
        }
        body = POST_VERTEX_UNIFORM_DECLARATION.matcher(body).replaceAll("");
        if (POST_VERTEX_INPUT_DECLARATION.matcher(body).find()) {
            throw new IllegalArgumentException(programName
                    + ".vsh vertex attributes are not available in Veyra's full-screen triangle bridge.");
        }
        if (POST_VERTEX_LEGACY_ATTRIBUTES.matcher(body).find()) {
            throw new IllegalArgumentException(programName
                    + ".vsh uses legacy vertex attributes or matrices that Veyra does not provide for post passes.");
        }

        Matcher outputMatcher = POST_VERTEX_OUTPUT_DECLARATION.matcher(body);
        StringBuffer withoutOutputs = new StringBuffer();
        int outputCount = 0;
        while (outputMatcher.find()) {
            String location = outputMatcher.group(1);
            String type = outputMatcher.group(2);
            String name = outputMatcher.group(3);
            if (location != null && !location.equals("0")) {
                throw new IllegalArgumentException(programName + ".vsh can output only texcoord at location 0.");
            }
            if (!type.equals("vec2") || !(name.equals("texcoord") || name.equals("texCoord"))) {
                throw new IllegalArgumentException(programName
                        + ".vsh output '" + name + "' is unsupported; declare only vec2 texcoord or vec2 texCoord.");
            }
            outputCount++;
            outputMatcher.appendReplacement(withoutOutputs, "");
        }
        outputMatcher.appendTail(withoutOutputs);
        body = withoutOutputs.toString();
        if (outputCount > 1) {
            throw new IllegalArgumentException(programName + ".vsh can declare only one texture-coordinate output.");
        }
        if (POST_VERTEX_OTHER_OUTPUT_DECLARATION.matcher(body).find()) {
            throw new IllegalArgumentException(programName
                    + ".vsh declares unsupported outputs; use only vec2 texcoord or vec2 texCoord.");
        }

        Matcher mainMatcher = POST_VERTEX_MAIN.matcher(body);
        if (!mainMatcher.find()) {
            throw new IllegalArgumentException(programName + ".vsh must define void main().");
        }
        mainMatcher.reset();
        body = mainMatcher.replaceFirst("void main() {\n    texCoord = veyraFullscreenUv();");
        body = body.replaceAll("\\bgl_VertexID\\b", "gl_VertexIndex")
                .replaceAll("\\bgl_TexCoord\\s*\\[\\s*0\\s*\\]", "texCoord")
                .replaceAll("\\bgl_MultiTexCoord0\\b", "vec4(veyraFullscreenUv(), 0.0, 1.0)")
                .replaceAll("\\bgl_Vertex\\b", "veyraFullscreenPosition()")
                .replaceAll("\\bftransform\\s*\\(\\s*\\)", "veyraFullscreenPosition()")
                .replaceAll("\\btexcoord\\b", "texCoord")
                .replaceAll("texture2D\\s*\\(", "texture(");

        StringBuilder adapted = new StringBuilder("#version 450\n").append(extensions);
        appendShaderPackUniformBlock(adapted, uniforms);
        return adapted.append("""
                out vec2 texCoord;
                vec2 veyraFullscreenUv() {
                    return vec2((gl_VertexIndex << 1) & 2, 1.0 - (gl_VertexIndex & 2));
                }
                vec4 veyraFullscreenPosition() {
                    vec2 uv = vec2((gl_VertexIndex << 1) & 2, gl_VertexIndex & 2);
                    return vec4(uv * 2.0 - 1.0, 0.0, 1.0);
                }
                """).append(body).toString();
    }

    private static TerrainPipelineSet compileTerrainPipelines(ShaderPack pack) throws IOException {
        if (PipelineManager.getTerrainVertexFormat() == null) {
            throw new IOException("VulkanMod has not initialized its terrain vertex format yet.");
        }

        String shaderPath = ShaderLoadUtil.resolveShaderPath("basic");
        String vertexSource = ShaderLoadUtil.loadShader(shaderPath, "terrain/terrain.vsh");
        vertexSource = TerrainShaderAdapter.adaptVertex(vertexSource);
        Map<String, GraphicsPipeline> pipelines = new HashMap<>();
        Map<String, GraphicsPipeline> shadowPipelines = new HashMap<>();
        boolean shadowSamplerAvailable = pack.hasShadowFragment();
        try {
            for (String programName : List.of(
                    "gbuffers_terrain", "gbuffers_terrain_solid", "gbuffers_terrain_cutout", "gbuffers_water")) {
                if (!pack.terrainPrograms().contains(programName)) {
                    continue;
                }
                String fragmentSource = pack.readTerrainFragment(programName)
                        .orElseThrow(() -> new IOException("The pack no longer contains " + programName + ".fsh."));
                FragmentOutputs outputLayout = FragmentOutputs.parse(fragmentSource);
                fragmentSource = TerrainShaderAdapter.adaptFragment(fragmentSource, shadowSamplerAvailable);

                Pipeline.Builder builder = new Pipeline.Builder(
                        PipelineManager.getTerrainVertexFormat(), "veyra_" + programName);
                builder.applyConfig(terrainPipelineConfig(shadowSamplerAvailable));
                builder.setShaderSrc(SPIRVUtils.ShaderKind.VERTEX_SHADER, vertexSource);
                builder.setShaderSrc(SPIRVUtils.ShaderKind.FRAGMENT_SHADER, fragmentSource);
                GraphicsPipeline pipeline = builder.createGraphicsPipeline();
                pipelines.put(programName, pipeline);
                VeyraGeometryTargets.register(pipeline, outputLayout);
                setShadowSamplerLayout(pipeline, shadowSamplerAvailable);
                for (UBO ubo : pipeline.getBuffers()) {
                    ubo.setUseGlobalBuffer(true);
                }
            }
            if (shadowSamplerAvailable) {
                for (String programName : List.of("shadow_solid", "shadow_cutout", "shadow_water")) {
                    String fragmentSource = pack.readShadowFragment(programName)
                            .orElseThrow(() -> new IOException("The pack no longer contains a supported shadow fragment."));
                    if (!FragmentOutputs.parse(fragmentSource).sceneOnly())
                        throw new IOException("Multiple shadow color attachments are not implemented yet.");
                    fragmentSource = TerrainShaderAdapter.adaptFragment(fragmentSource, false);
                    Pipeline.Builder builder = new Pipeline.Builder(
                            PipelineManager.getTerrainVertexFormat(), "veyra_" + programName);
                    builder.applyConfig(terrainPipelineConfig(false));
                    builder.setShaderSrc(SPIRVUtils.ShaderKind.VERTEX_SHADER, vertexSource);
                    builder.setShaderSrc(SPIRVUtils.ShaderKind.FRAGMENT_SHADER, fragmentSource);
                    GraphicsPipeline shadowPipeline = builder.createGraphicsPipeline();
                    for (UBO ubo : shadowPipeline.getBuffers()) {
                        ubo.setUseGlobalBuffer(true);
                    }
                    shadowPipelines.put(programName, shadowPipeline);
                }
            }
            return new TerrainPipelineSet(Map.copyOf(pipelines), Map.copyOf(shadowPipelines));
        } catch (IOException | RuntimeException exception) {
            pipelines.values().forEach(VeyraGeometryTargets::unregister);
            pipelines.values().forEach(GraphicsPipeline::scheduleCleanUp);
            shadowPipelines.values().forEach(GraphicsPipeline::scheduleCleanUp);
            throw exception;
        }
    }

    private static PipelineConfig terrainPipelineConfig(boolean shadowSamplerAvailable) {
        PipelineConfig.UB.Builder terrainUniforms = PipelineConfig.UB.builder(
                        1, org.lwjgl.vulkan.VK10.VK_SHADER_STAGE_ALL_GRAPHICS)
                .addUniform("vec4", "FogColor")
                .addUniform("float", "FogEnvironmentalStart")
                .addUniform("float", "FogEnvironmentalEnd")
                .addUniform("float", "FogRenderDistanceStart")
                .addUniform("float", "FogRenderDistanceEnd")
                .addUniform("float", "FogSkyEnd")
                .addUniform("float", "FogCloudsEnd")
                .addUniform("float", "AlphaCutout")
                .addUniform("vec2", "TextureSize")
                .addUniform("vec2", "TexelSize")
                .addUniform("int", "UseRgss")
                .addUniform("mat4", "shadowModelView")
                .addUniform("mat4", "shadowProjection");
        for (TerrainShaderAdapter.ShaderUniform uniform : TerrainShaderAdapter.shaderPackUniforms()) {
            terrainUniforms.addUniform(uniform.bufferType(), uniform.name());
        }
        PipelineConfig.UB terrainUniformBuffer = terrainUniforms.build();
        PipelineConfig.Builder config = PipelineConfig.builder()
                .addUB(PipelineConfigs.TERRAIN_UB0)
                .addUB(terrainUniformBuffer)
                .addUB(PipelineConfigs.TERRAIN_UB2)
                .setPushConstants(PipelineConfigs.TERRAIN_PC)
                .addImageDescriptor(3, "sampler2D", "Sampler0", 0)
                .addImageDescriptor(4, "sampler2D", "LightTexture", 2);
        if (shadowSamplerAvailable) {
            config.addImageDescriptor(5, "sampler2D", "VeyraShadowSampler", 3);
        }
        return config.build();
    }

    private static void setShadowSamplerLayout(GraphicsPipeline pipeline, boolean shadowSamplerAvailable) {
        if (shadowSamplerAvailable) {
            var shadowSampler = pipeline.getImageDescriptor("VeyraShadowSampler");
            shadowSampler.setLayout(VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL);
            // VulkanMod wraps the depth-only view in a separate VulkanImage object. The render pass
            // already transitions the shared image to this final layout; asking the wrapper to
            // transition it again uses stale per-wrapper layout tracking and can crash native drivers.
            shadowSampler.isReadOnlyLayout = false;
        }
    }

    private static GraphicsPipeline compileCloudPipeline(ShaderPack pack) throws IOException {
        String shaderPath = ShaderLoadUtil.resolveShaderPath("basic/clouds");
        String vertexSource = ShaderLoadUtil.loadShader(shaderPath, "clouds.vsh");
        String source = pack.readCloudFragment()
                .orElseThrow(() -> new IOException("The pack no longer contains gbuffers_clouds.fsh."));
        CloudShaderAdapter.AdaptedFragment adaptedFragment = CloudShaderAdapter.adapt(source);
        String fragmentSource = adaptedFragment.source();
        PipelineConfig config = PipelineConfig.fromJson("clouds", ShaderLoadUtil.getJsonConfig(shaderPath, "clouds"));
        if (!adaptedFragment.uniformFields().isEmpty()) {
            vertexSource = addCloudUniformFields(vertexSource, adaptedFragment.uniformFields());
            config = addCloudUniformBufferFields(config, adaptedFragment.uniformFields());
        }

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

    private static String addCloudUniformFields(
            String vertexSource, List<ShaderPackUniformAdapter.UniformField> fields) throws IOException {
        String marker = "FogCloudsEnd;";
        int markerIndex = vertexSource.indexOf(marker);
        if (markerIndex < 0 || vertexSource.indexOf(marker, markerIndex + marker.length()) >= 0) {
            throw new IOException("VulkanMod's cloud vertex shader does not contain one FogCloudsEnd field.");
        }
        int lineEnd = vertexSource.indexOf('\n', markerIndex);
        if (lineEnd < 0) {
            throw new IOException("VulkanMod's cloud vertex shader has an incomplete cloud uniform block.");
        }
        StringBuilder additions = new StringBuilder();
        for (ShaderPackUniformAdapter.UniformField field : fields) {
            additions.append("    ").append(field.type()).append(' ').append(field.name()).append(";\n");
        }
        return vertexSource.substring(0, lineEnd + 1) + additions + vertexSource.substring(lineEnd + 1);
    }

    private static PipelineConfig addCloudUniformBufferFields(
            PipelineConfig config, List<ShaderPackUniformAdapter.UniformField> fields) throws IOException {
        List<PipelineConfig.UB> buffers = new ArrayList<>(config.ubs.size());
        boolean replacedUniforms = false;
        for (PipelineConfig.UB uniformBuffer : config.ubs) {
            if (uniformBuffer.binding != 1) {
                buffers.add(uniformBuffer);
                continue;
            }
            if (replacedUniforms) {
                throw new IOException("VulkanMod's cloud pipeline contains duplicate binding 1 uniform buffers.");
            }
            PipelineConfig.UB.Builder builder = PipelineConfig.UB.builder(uniformBuffer.binding, uniformBuffer.stage)
                    .addUniform("vec4", "ColorModulator")
                    .addUniform("float", "FogCloudsEnd");
            for (ShaderPackUniformAdapter.UniformField field : fields) {
                builder.addUniform(field.type(), field.name());
            }
            buffers.add(builder.build());
            replacedUniforms = true;
        }
        if (!replacedUniforms) {
            throw new IOException("VulkanMod's cloud pipeline does not declare its binding 1 uniform buffer.");
        }
        return new PipelineConfig(config.shaderPaths, buffers, config.imageDescriptors, config.pushConstantsInfo);
    }

    private static EntityPipelineSet compileEntityPipelines(ShaderPack pack) throws IOException {
        return compileEntityPipelines(pack, false);
    }

    private static EntityPipelineSet compileEntityPipelines(ShaderPack pack, boolean hand) throws IOException {
        List<RenderPipeline> candidates = RenderPipelines.getStaticPipelines().stream()
                .filter(VeyraRenderer::isEntityRenderPipeline)
                .toList();
        Map<RenderPipeline, RenderPipeline> pipelines = new IdentityHashMap<>();
        for (RenderPipeline original : candidates) {
            String programName = hand
                    ? resolveHandProgram(pack, original)
                    : resolveEntityProgram(pack, original);
            if (programName == null) {
                continue;
            }
            boolean fallback = hand
                    ? !(HAND_PROGRAM.equals(programName) && pack.hasHandFragment()
                    || HAND_WATER_PROGRAM.equals(programName) && pack.hasHandWaterFragment())
                    : !pack.entityPrograms().contains(programName);
            String fragmentSource;
            boolean requiresNormal = false;
            boolean requiresLightmap = false;
            boolean requiresViewPosition = false;
            boolean requiresViewNormal = false;
            try {
                fragmentSource = (hand ? pack.readHandFragment(programName) : pack.readEntityFragment(programName))
                        .orElseThrow(() -> new IOException("The pack no longer contains " + programName + ".fsh."));
                requiresNormal = EntityShaderAdapter.requiresNormalInput(fragmentSource);
                requiresLightmap = EntityShaderAdapter.requiresLightmapInput(fragmentSource);
                requiresViewPosition = EntityShaderAdapter.requiresViewPositionInput(fragmentSource);
                requiresViewNormal = EntityShaderAdapter.requiresViewNormalInput(fragmentSource);
                fragmentSource = EntityShaderAdapter.adaptFragment(fragmentSource, programName, hand);
            } catch (IOException exception) {
                if (!fallback) {
                    throw exception;
                }
                VeyraClient.LOGGER.warn("Skipping incompatible {} fallback for {}: {}",
                        programName, original.getLocation(), exception.getMessage());
                continue;
            }
            String sourceHash = sha256(programName + '\n' + fragmentSource);
            EntityPipelineKey cacheKey = new EntityPipelineKey(original, programName, sourceHash);
            RenderPipeline entityPipeline = ENTITY_PIPELINE_CACHE.get(cacheKey);
            if (entityPipeline == null) {
                try {
                    entityPipeline = compileEntityPipeline(
                            original, fragmentSource, sourceHash, requiresNormal, requiresLightmap,
                            requiresViewPosition, requiresViewNormal, false, hand, programName);
                } catch (IOException exception) {
                    if (!fallback) {
                        throw exception;
                    }
                    VeyraClient.LOGGER.warn("Skipping incompatible {} fallback for {}: {}",
                            programName, original.getLocation(), exception.getMessage());
                    continue;
                }
                ENTITY_PIPELINE_CACHE.put(cacheKey, entityPipeline);
            }
            pipelines.put(original, entityPipeline);
        }
        if (pipelines.isEmpty() && (hand
                ? pack.hasHandFragment() || pack.hasHandWaterFragment()
                : !pack.entityPrograms().isEmpty())) {
            throw new IOException(hand
                    ? "VulkanMod's Minecraft first-person hand render pipelines were not found."
                    : "VulkanMod's Minecraft entity render pipelines were not found.");
        }
        return new EntityPipelineSet(pipelines);
    }

    private static EntityPipelineSet compileShadowEntityPipelines(ShaderPack pack, String programName) throws IOException {
        String fragmentSource = pack.readShadowFragment(programName)
                .orElseThrow(() -> new IOException("The pack no longer contains a supported shadow fragment."));
        boolean requiresNormal = EntityShaderAdapter.requiresNormalInput(fragmentSource);
        boolean requiresLightmap = EntityShaderAdapter.requiresLightmapInput(fragmentSource);
        boolean requiresViewPosition = EntityShaderAdapter.requiresViewPositionInput(fragmentSource);
        boolean requiresViewNormal = EntityShaderAdapter.requiresViewNormalInput(fragmentSource);
        fragmentSource = EntityShaderAdapter.adaptFragment(fragmentSource, programName);
        String sourceHash = sha256(programName + '\n' + fragmentSource);
        Identifier fragmentShader = Identifier.fromNamespaceAndPath(
                "veyra", "shaderpack/" + programName + "/" + sourceHash);
        String adaptedFragment = fragmentSource;
        ShaderManager shaderManager = ((MinecraftAccessor) Minecraft.getInstance()).veyra$getShaderManager();

        List<RenderPipeline> candidates = RenderPipelines.getStaticPipelines().stream()
                .filter(VeyraRenderer::isEntityRenderPipeline)
                .toList();
        Map<RenderPipeline, RenderPipeline> pipelines = new IdentityHashMap<>();
        for (RenderPipeline original : candidates) {
            EntityPipelineKey cacheKey = new EntityPipelineKey(original, programName, sourceHash);
            RenderPipeline shadowPipeline = ENTITY_PIPELINE_CACHE.get(cacheKey);
            if (shadowPipeline == null) {
                Identifier location = Identifier.fromNamespaceAndPath("veyra",
                        "pipeline/" + programName + "/" + sourceHash + "/"
                                + original.getLocation().getPath().replace('/', '_'));
                Identifier vertexShader = original.getVertexShader();
                String adaptedVertex = null;
                if (requiresNormal || requiresLightmap || requiresViewPosition || requiresViewNormal) {
                    adaptedVertex = EntityShaderAdapter.adaptVertexShader(
                            shaderManager.getShader(vertexShader, ShaderType.VERTEX), true, programName,
                            requiresNormal, requiresLightmap, requiresViewPosition, requiresViewNormal);
                    vertexShader = entityNormalVertexShader(original, sourceHash, true);
                }
                Identifier selectedVertexShader = vertexShader;
                String selectedVertexSource = adaptedVertex;
                ShaderSource source = (identifier, type) -> {
                    if (identifier.equals(fragmentShader) && type == ShaderType.FRAGMENT) {
                        return adaptedFragment;
                    }
                    if (selectedVertexSource != null && identifier.equals(selectedVertexShader)
                            && type == ShaderType.VERTEX) {
                        return selectedVertexSource;
                    }
                    return shaderManager.getShader(identifier, type);
                };
                shadowPipeline = copyShadowPipelineWithShaders(original, location, vertexShader, fragmentShader);
                VeyraPipelineResources.track(shadowPipeline);
                CompiledRenderPipeline compiled = RenderSystem.getDevice().precompilePipeline(shadowPipeline, source);
                if (!compiled.isValid()) {
                    throw new IOException("VulkanMod could not compile " + original.getLocation()
                            + " with the selected shadow shader.");
                }
                ENTITY_PIPELINE_CACHE.put(cacheKey, shadowPipeline);
            }
            pipelines.put(original, shadowPipeline);
        }
        if (pipelines.isEmpty()) {
            throw new IOException("Minecraft's entity render pipelines were not found for the shadow pass.");
        }
        return new EntityPipelineSet(pipelines);
    }

    private static EntityPipelineSet compileBlockEntityPipelines(ShaderPack pack) throws IOException {
        List<RenderPipeline> candidates = RenderPipelines.getStaticPipelines().stream()
                .filter(VeyraRenderer::isEntityRenderPipeline)
                .toList();
        Map<RenderPipeline, RenderPipeline> pipelines = new IdentityHashMap<>();
        for (RenderPipeline original : candidates) {
            String programName = resolveBlockEntityProgram(pack, original);
            if (programName == null) {
                continue;
            }
            boolean fallback = !pack.blockPrograms().contains(programName);
            String fragmentSource;
            boolean requiresNormal = false;
            boolean requiresLightmap = false;
            boolean requiresViewPosition = false;
            boolean requiresViewNormal = false;
            try {
                fragmentSource = pack.readBlockFragment(programName)
                        .orElseThrow(() -> new IOException("The pack no longer contains " + programName + ".fsh."));
                requiresNormal = EntityShaderAdapter.requiresNormalInput(fragmentSource);
                requiresLightmap = EntityShaderAdapter.requiresLightmapInput(fragmentSource);
                requiresViewPosition = EntityShaderAdapter.requiresViewPositionInput(fragmentSource);
                requiresViewNormal = EntityShaderAdapter.requiresViewNormalInput(fragmentSource);
                fragmentSource = EntityShaderAdapter.adaptFragment(fragmentSource, programName);
            } catch (IOException exception) {
                if (!fallback) {
                    throw exception;
                }
                VeyraClient.LOGGER.warn("Skipping incompatible {} block entity fallback for {}: {}",
                        programName, original.getLocation(), exception.getMessage());
                continue;
            }
            String sourceHash = sha256(programName + '\n' + fragmentSource);
            EntityPipelineKey cacheKey = new EntityPipelineKey(original, programName, sourceHash);
            RenderPipeline blockPipeline = ENTITY_PIPELINE_CACHE.get(cacheKey);
            if (blockPipeline == null) {
                try {
                    blockPipeline = compileEntityPipeline(
                            original, fragmentSource, sourceHash, requiresNormal, requiresLightmap,
                            requiresViewPosition, requiresViewNormal, false, false, programName);
                } catch (IOException exception) {
                    if (!fallback) {
                        throw exception;
                    }
                    VeyraClient.LOGGER.warn("Skipping incompatible {} block entity fallback for {}: {}",
                            programName, original.getLocation(), exception.getMessage());
                    continue;
                }
                ENTITY_PIPELINE_CACHE.put(cacheKey, blockPipeline);
            }
            pipelines.put(original, blockPipeline);
        }
        compileSignTextPipelines(pack, pipelines);
        if (pipelines.isEmpty() && !pack.blockPrograms().isEmpty()) {
            throw new IOException("Minecraft's block entity render pipelines were not found or do not support this shader interface.");
        }
        return new EntityPipelineSet(pipelines);
    }

    private static void compileSignTextPipelines(ShaderPack pack,
                                                 Map<RenderPipeline, RenderPipeline> pipelines) {
        String programName = resolveBlockTextProgram(pack);
        if (programName == null) {
            return;
        }
        List<RenderPipeline> candidates = RenderPipelines.getStaticPipelines().stream()
                .filter(VeyraRenderer::isSignTextPipeline)
                .toList();
        String fragmentSource;
        boolean requiresLightmap;
        boolean requiresViewPosition;
        try {
            fragmentSource = pack.readBlockFragment(programName)
                    .orElseThrow(() -> new IOException("The pack no longer contains " + programName + ".fsh."));
            requiresLightmap = EntityShaderAdapter.requiresLightmapInput(fragmentSource);
            requiresViewPosition = EntityShaderAdapter.requiresViewPositionInput(fragmentSource);
            fragmentSource = EntityShaderAdapter.adaptTextFragment(fragmentSource, programName);
        } catch (IOException exception) {
            VeyraClient.LOGGER.warn("Skipping sign text for {} because its inputs are unsupported: {}",
                    programName, exception.getMessage());
            return;
        }
        String sourceHash = sha256("sign_text_" + programName + '\n' + fragmentSource);
        for (RenderPipeline original : candidates) {
            EntityPipelineKey cacheKey = new EntityPipelineKey(original, "sign_text_" + programName, sourceHash);
            RenderPipeline textPipeline = ENTITY_PIPELINE_CACHE.get(cacheKey);
            if (textPipeline == null) {
                try {
                    textPipeline = compileEntityPipeline(
                            original, fragmentSource, sourceHash, false, requiresLightmap,
                            requiresViewPosition, false, false, false, programName);
                } catch (IOException exception) {
                    VeyraClient.LOGGER.warn("Skipping sign text pipeline {} for {}: {}",
                            original.getLocation(), programName, exception.getMessage());
                    continue;
                }
                ENTITY_PIPELINE_CACHE.put(cacheKey, textPipeline);
            }
            pipelines.put(original, textPipeline);
        }
    }

    private static String resolveBlockTextProgram(ShaderPack pack) {
        if (pack.blockPrograms().contains("gbuffers_block")) {
            return "gbuffers_block";
        }
        if (pack.terrainPrograms().contains("gbuffers_terrain")) {
            return "gbuffers_terrain";
        }
        if (pack.hasTexturedLitFragment()) {
            return "gbuffers_textured_lit";
        }
        return pack.hasTexturedFragment() ? "gbuffers_textured" : null;
    }

    private static boolean isSignTextPipeline(RenderPipeline pipeline) {
        if (pipeline == null) {
            return false;
        }
        String location = pipeline.getLocation().getPath();
        String fragment = pipeline.getFragmentShader().getPath();
        return switch (location) {
            case "pipeline/text", "pipeline/text_polygon_offset" -> fragment.equals("core/rendertype_text");
            case "pipeline/text_see_through" -> fragment.equals("core/rendertype_text_see_through");
            case "pipeline/text_intensity" -> fragment.equals("core/rendertype_text_intensity");
            case "pipeline/text_intensity_see_through" -> fragment.equals("core/rendertype_text_intensity_see_through");
            default -> false;
        };
    }

    private static String resolveBlockEntityProgram(ShaderPack pack, RenderPipeline pipeline) {
        if (pipeline.getBlendFunction().isPresent()
                && pack.blockPrograms().contains("gbuffers_block_translucent")) {
            return "gbuffers_block_translucent";
        }
        if (pack.blockPrograms().contains("gbuffers_block")) {
            return "gbuffers_block";
        }
        if (pack.terrainPrograms().contains("gbuffers_terrain")) {
            return "gbuffers_terrain";
        }
        if (pack.hasTexturedLitFragment()) {
            return "gbuffers_textured_lit";
        }
        return pack.hasTexturedFragment() ? "gbuffers_textured" : null;
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
        boolean eyes = pipeline.getLocation().getPath().equals("pipeline/eyes");
        if (eyes
                && pack.entityPrograms().contains("gbuffers_spidereyes")) {
            return "gbuffers_spidereyes";
        }
        if (pipeline.getBlendFunction().isPresent()
                && pack.entityPrograms().contains("gbuffers_entities_translucent")) {
            return "gbuffers_entities_translucent";
        }
        if (pack.entityPrograms().contains("gbuffers_entities")) {
            return "gbuffers_entities";
        }
        if (eyes) {
            return null;
        }
        if (pack.hasTexturedLitFragment()) {
            return "gbuffers_textured_lit";
        }
        return pack.hasTexturedFragment() ? "gbuffers_textured" : null;
    }

    private static String resolveHandProgram(ShaderPack pack, RenderPipeline pipeline) {
        if (pipeline.getLocation().getPath().equals("pipeline/eyes")) {
            return null;
        }
        if (pipeline.getBlendFunction().isPresent() && pack.hasHandWaterFragment()) {
            return HAND_WATER_PROGRAM;
        }
        if (pack.hasHandFragment()) {
            return HAND_PROGRAM;
        }
        if (pack.hasTexturedLitFragment()) {
            return "gbuffers_textured_lit";
        }
        return pack.hasTexturedFragment() ? "gbuffers_textured" : null;
    }

    private static RenderPipeline compileEntityPipeline(
            RenderPipeline original, String fragmentSource, String sourceHash) throws IOException {
        return compileEntityPipeline(original, fragmentSource, sourceHash,
                false, false, false, false, false, false, "gbuffers_entities");
    }

    private static RenderPipeline compileEntityPipeline(
            RenderPipeline original, String fragmentSource, String sourceHash,
            boolean requiresNormal, boolean requiresLightmap,
            boolean requiresViewPosition, boolean requiresViewNormal,
            boolean shadowPass, boolean handPass, String programName) throws IOException {
        Identifier fragmentShader = Identifier.fromNamespaceAndPath(
                "veyra", "shaderpack/entities/" + sourceHash);
        ShaderManager shaderManager = ((MinecraftAccessor) Minecraft.getInstance()).veyra$getShaderManager();
        Identifier vertexShader = original.getVertexShader();
        String adaptedVertex = null;
        if (requiresNormal || requiresLightmap || requiresViewPosition || requiresViewNormal || handPass) {
            adaptedVertex = EntityShaderAdapter.adaptVertexShader(
                    shaderManager.getShader(vertexShader, ShaderType.VERTEX), shadowPass, programName,
                    requiresNormal, requiresLightmap, requiresViewPosition, requiresViewNormal, handPass);
            vertexShader = entityNormalVertexShader(original, sourceHash, shadowPass);
        }
        Identifier selectedVertexShader = vertexShader;
        String selectedVertexSource = adaptedVertex;
        RenderPipeline pipeline = selectedVertexSource == null
                ? copyPipelineWithFragment(original, fragmentShader)
                : copyPipelineWithShaders(original, selectedVertexShader, fragmentShader);
        ShaderSource source = (identifier, type) -> {
            if (identifier.equals(fragmentShader) && type == ShaderType.FRAGMENT) {
                return fragmentSource;
            }
            if (selectedVertexSource != null && identifier.equals(selectedVertexShader)
                    && type == ShaderType.VERTEX) {
                return selectedVertexSource;
            }
            return shaderManager.getShader(identifier, type);
        };
        VeyraPipelineResources.track(pipeline);
        CompiledRenderPipeline compiled = RenderSystem.getDevice().precompilePipeline(pipeline, source);
        if (!compiled.isValid()) {
            throw new IOException("VulkanMod could not compile " + original.getLocation()
                    + " with the selected entity shader.");
        }
        return pipeline;
    }

    private static Identifier entityNormalVertexShader(RenderPipeline original, String sourceHash, boolean shadowPass) {
        String vertexPath = original.getVertexShader().getPath().replace('/', '_');
        return Identifier.fromNamespaceAndPath("veyra",
                "shaderpack/entities_vertex/" + (shadowPass ? "shadow_" : "") + vertexPath + "/" + sourceHash);
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

    private static BeaconBeamPipelineSet compileShadowBeaconBeamPipelines(ShaderPack pack) throws IOException {
        List<RenderPipeline> candidates = RenderPipelines.getStaticPipelines().stream()
                .filter(VeyraRenderer::isBeaconBeamPipeline)
                .toList();
        String fragmentSource = pack.readShadowFragment("shadow_entities")
                .orElseThrow(() -> new IOException("The pack no longer contains a supported shadow fragment."));
        fragmentSource = BeaconBeamShaderAdapter.adaptFragment(fragmentSource);
        String sourceHash = sha256("shadow_entities_beacon\n" + fragmentSource);
        Identifier fragmentShader = Identifier.fromNamespaceAndPath(
                "veyra", "shaderpack/shadow_entities_beacon/" + sourceHash);
        String adaptedFragment = fragmentSource;
        ShaderManager shaderManager = ((MinecraftAccessor) Minecraft.getInstance()).veyra$getShaderManager();
        ShaderSource source = (identifier, type) -> {
            if (identifier.equals(fragmentShader) && type == ShaderType.FRAGMENT) {
                return adaptedFragment;
            }
            return shaderManager.getShader(identifier, type);
        };

        Map<RenderPipeline, RenderPipeline> pipelines = new IdentityHashMap<>();
        for (RenderPipeline original : candidates) {
            BeaconBeamPipelineKey cacheKey = new BeaconBeamPipelineKey(original, sourceHash);
            RenderPipeline shadowPipeline = BEACON_BEAM_PIPELINE_CACHE.get(cacheKey);
            if (shadowPipeline == null) {
                Identifier location = Identifier.fromNamespaceAndPath("veyra",
                        "pipeline/shadow_entities_beacon/" + sourceHash + "/"
                                + original.getLocation().getPath().replace('/', '_'));
                shadowPipeline = copyShadowPipelineWithFragment(original, location, fragmentShader);
                VeyraPipelineResources.track(shadowPipeline);
                CompiledRenderPipeline compiled = RenderSystem.getDevice().precompilePipeline(shadowPipeline, source);
                if (!compiled.isValid()) {
                    throw new IOException("VulkanMod could not compile " + original.getLocation()
                            + " with the shadow_entities shader.");
                }
                BEACON_BEAM_PIPELINE_CACHE.put(cacheKey, shadowPipeline);
            }
            pipelines.put(original, shadowPipeline);
        }
        if (pipelines.isEmpty()) {
            throw new IOException("VulkanMod's Minecraft beacon beam render pipelines were not found for the shadow pass.");
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
        VeyraPipelineResources.track(pipeline);
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
            boolean fallback = !pack.particlePrograms().contains(programName);
            String fragmentSource;
            try {
                fragmentSource = pack.readParticleFragment(programName)
                        .orElseThrow(() -> new IOException("The pack no longer contains " + programName + ".fsh."));
                fragmentSource = TextureColorDistanceShaderAdapter.adaptFragment(fragmentSource, programName, true);
            } catch (IOException exception) {
                if (!fallback) {
                    throw exception;
                }
                VeyraClient.LOGGER.warn("Skipping incompatible {} fallback for {}: {}",
                        programName, original.getLocation(), exception.getMessage());
                continue;
            }
            String sourceHash = sha256(programName + '\n' + fragmentSource);
            ParticlePipelineKey cacheKey = new ParticlePipelineKey(original, programName, sourceHash);
            RenderPipeline particlePipeline = PARTICLE_PIPELINE_CACHE.get(cacheKey);
            if (particlePipeline == null) {
                try {
                    particlePipeline = compileParticlePipeline(original, fragmentSource, sourceHash);
                } catch (IOException exception) {
                    if (!fallback) {
                        throw exception;
                    }
                    VeyraClient.LOGGER.warn("Skipping incompatible {} fallback for {}: {}",
                            programName, original.getLocation(), exception.getMessage());
                    continue;
                }
                PARTICLE_PIPELINE_CACHE.put(cacheKey, particlePipeline);
            }
            pipelines.put(original, particlePipeline);
        }
        if (pipelines.isEmpty() && !pack.particlePrograms().isEmpty()) {
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
        if (pack.particlePrograms().contains("gbuffers_particles")) {
            return "gbuffers_particles";
        }
        if (pack.hasTexturedLitFragment()) {
            return "gbuffers_textured_lit";
        }
        return pack.hasTexturedFragment() ? "gbuffers_textured" : null;
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
        VeyraPipelineResources.track(pipeline);
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
            String programName = pack.weatherPrograms().contains("gbuffers_weather")
                    ? "gbuffers_weather"
                    : pack.hasTexturedLitFragment() ? "gbuffers_textured_lit" : "gbuffers_textured";
            boolean fallback = !pack.weatherPrograms().contains(programName);
            String fragmentSource;
            try {
                fragmentSource = pack.readWeatherFragment(programName)
                        .orElseThrow(() -> new IOException("The pack no longer contains " + programName + ".fsh."));
                fragmentSource = TextureColorDistanceShaderAdapter.adaptFragment(fragmentSource, programName, true);
            } catch (IOException exception) {
                if (!fallback) {
                    throw exception;
                }
                VeyraClient.LOGGER.warn("Skipping incompatible {} fallback for {}: {}",
                        programName, original.getLocation(), exception.getMessage());
                continue;
            }
            String sourceHash = sha256(programName + '\n' + fragmentSource);
            WeatherPipelineKey cacheKey = new WeatherPipelineKey(original, sourceHash);
            RenderPipeline weatherPipeline = WEATHER_PIPELINE_CACHE.get(cacheKey);
            if (weatherPipeline == null) {
                try {
                    weatherPipeline = compileWeatherPipeline(original, fragmentSource, sourceHash);
                } catch (IOException exception) {
                    if (!fallback) {
                        throw exception;
                    }
                    VeyraClient.LOGGER.warn("Skipping incompatible {} fallback for {}: {}",
                            programName, original.getLocation(), exception.getMessage());
                    continue;
                }
                WEATHER_PIPELINE_CACHE.put(cacheKey, weatherPipeline);
            }
            pipelines.put(original, weatherPipeline);
        }
        if (pipelines.isEmpty() && !pack.weatherPrograms().isEmpty()) {
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
        VeyraPipelineResources.track(pipeline);
        CompiledRenderPipeline compiled = RenderSystem.getDevice().precompilePipeline(pipeline, source);
        if (!compiled.isValid()) {
            throw new IOException("VulkanMod could not compile " + original.getLocation()
                    + " with the selected weather shader.");
        }
        return pipeline;
    }

    private static LightningPipelineSet compileLightningPipelines(ShaderPack pack) throws IOException {
        List<RenderPipeline> candidates = RenderPipelines.getStaticPipelines().stream()
                .filter(VeyraRenderer::isLightningPipeline)
                .toList();
        String fragmentSource = pack.readLightningFragment()
                .orElseThrow(() -> new IOException("The pack no longer contains gbuffers_lightning.fsh."));
        fragmentSource = ColorDistanceShaderAdapter.adaptFragment(fragmentSource, "gbuffers_lightning", false);
        String sourceHash = sha256("gbuffers_lightning\n" + fragmentSource);
        Map<RenderPipeline, RenderPipeline> pipelines = new IdentityHashMap<>();
        for (RenderPipeline original : candidates) {
            LightningPipelineKey cacheKey = new LightningPipelineKey(original, sourceHash);
            RenderPipeline lightningPipeline = LIGHTNING_PIPELINE_CACHE.get(cacheKey);
            if (lightningPipeline == null) {
                lightningPipeline = compileLightningPipeline(original, fragmentSource, sourceHash);
                LIGHTNING_PIPELINE_CACHE.put(cacheKey, lightningPipeline);
            }
            pipelines.put(original, lightningPipeline);
        }
        if (pipelines.isEmpty()) {
            throw new IOException("VulkanMod's Minecraft lightning render pipelines were not found.");
        }
        return new LightningPipelineSet(pipelines);
    }

    private static boolean isLightningPipeline(RenderPipeline pipeline) {
        if (pipeline == null
                || !pipeline.getFragmentShader().equals(Identifier.withDefaultNamespace("core/rendertype_lightning"))) {
            return false;
        }
        String location = pipeline.getLocation().getPath();
        return location.equals("pipeline/lightning") || location.equals("pipeline/dragon_rays");
    }

    private static RenderPipeline compileLightningPipeline(
            RenderPipeline original, String fragmentSource, String sourceHash) throws IOException {
        Identifier fragmentShader = Identifier.fromNamespaceAndPath(
                "veyra", "shaderpack/lightning/" + sourceHash);
        RenderPipeline pipeline = copyPipelineWithFragment(original, fragmentShader);
        ShaderManager shaderManager = ((MinecraftAccessor) Minecraft.getInstance()).veyra$getShaderManager();
        ShaderSource source = (identifier, type) -> {
            if (identifier.equals(fragmentShader) && type == ShaderType.FRAGMENT) {
                return fragmentSource;
            }
            return shaderManager.getShader(identifier, type);
        };
        VeyraPipelineResources.track(pipeline);
        CompiledRenderPipeline compiled = RenderSystem.getDevice().precompilePipeline(pipeline, source);
        if (!compiled.isValid()) {
            throw new IOException("VulkanMod could not compile " + original.getLocation()
                    + " with the selected lightning shader.");
        }
        return pipeline;
    }

    private static ColorDistancePipelineSet compileBasicPipelines(ShaderPack pack) throws IOException {
        List<RenderPipeline> candidates = RenderPipelines.getStaticPipelines().stream()
                .filter(VeyraRenderer::isLeashPipeline)
                .toList();
        return compileColorDistancePipelines(pack, "gbuffers_basic", candidates);
    }

    private static ColorDistancePipelineSet compileLinePipelines(ShaderPack pack) throws IOException {
        List<RenderPipeline> candidates = RenderPipelines.getStaticPipelines().stream()
                .filter(VeyraRenderer::isLinePipeline)
                .toList();
        String programName = pack.hasLineFragment() ? "gbuffers_line" : "gbuffers_basic";
        return compileColorDistancePipelines(pack, programName, candidates);
    }

    private static ColorDistancePipelineSet compileColorDistancePipelines(
            ShaderPack pack, String programName, List<RenderPipeline> candidates) throws IOException {
        String source = switch (programName) {
            case "gbuffers_basic" -> pack.readBasicFragment().orElseThrow(
                    () -> new IOException("The pack no longer contains gbuffers_basic.fsh."));
            case "gbuffers_line" -> pack.readLineFragment().orElseThrow(
                    () -> new IOException("The pack no longer contains gbuffers_line.fsh."));
            default -> throw new IOException("Unsupported color-distance program '" + programName + "'.");
        };
        Map<RenderPipeline, RenderPipeline> pipelines = new IdentityHashMap<>();
        for (RenderPipeline original : candidates) {
            String fragmentSource = ColorDistanceShaderAdapter.adaptFragment(
                    source, programName, isLeashPipeline(original));
            String sourceHash = sha256(programName + "\n" + fragmentSource);
            ColorDistancePipelineKey cacheKey = new ColorDistancePipelineKey(original, sourceHash);
            RenderPipeline replacement = COLOR_DISTANCE_PIPELINE_CACHE.get(cacheKey);
            if (replacement == null) {
                replacement = compileColorDistancePipeline(original, programName, fragmentSource, sourceHash);
                COLOR_DISTANCE_PIPELINE_CACHE.put(cacheKey, replacement);
            }
            pipelines.put(original, replacement);
        }
        if (pipelines.isEmpty()) {
            throw new IOException("VulkanMod's Minecraft pipelines for " + programName + " were not found.");
        }
        return new ColorDistancePipelineSet(pipelines);
    }

    private static boolean isLeashPipeline(RenderPipeline pipeline) {
        return pipeline != null
                && pipeline.getLocation().getPath().equals("pipeline/leash")
                && pipeline.getVertexShader().equals(Identifier.withDefaultNamespace("core/rendertype_leash"));
    }

    private static boolean isLinePipeline(RenderPipeline pipeline) {
        if (pipeline == null
                || !pipeline.getVertexShader().equals(Identifier.withDefaultNamespace("core/rendertype_lines"))) {
            return false;
        }
        String location = pipeline.getLocation().getPath();
        return location.equals("pipeline/lines")
                || location.equals("pipeline/lines_translucent")
                || location.equals("pipeline/secondary_block_outline");
    }

    private static RenderPipeline compileColorDistancePipeline(
            RenderPipeline original, String programName, String fragmentSource, String sourceHash) throws IOException {
        Identifier fragmentShader = Identifier.fromNamespaceAndPath(
                "veyra", "shaderpack/color_distance/" + sourceHash);
        RenderPipeline pipeline = copyPipelineWithFragment(original, fragmentShader);
        ShaderManager shaderManager = ((MinecraftAccessor) Minecraft.getInstance()).veyra$getShaderManager();
        ShaderSource source = (identifier, type) -> {
            if (identifier.equals(fragmentShader) && type == ShaderType.FRAGMENT) {
                return fragmentSource;
            }
            return shaderManager.getShader(identifier, type);
        };
        VeyraPipelineResources.track(pipeline);
        CompiledRenderPipeline compiled = RenderSystem.getDevice().precompilePipeline(pipeline, source);
        if (!compiled.isValid()) {
            throw new IOException("VulkanMod could not compile " + original.getLocation()
                    + " with the selected " + programName + " shader.");
        }
        return pipeline;
    }

    private static GlintPipelineSet compileGlintPipelines(ShaderPack pack) throws IOException {
        List<RenderPipeline> candidates = RenderPipelines.getStaticPipelines().stream()
                .filter(VeyraRenderer::isGlintPipeline)
                .toList();
        String source = pack.readGlintFragment()
                .orElseThrow(() -> new IOException("The pack no longer contains gbuffers_armor_glint.fsh."));
        String programName = pack.hasGlintFragment() ? "gbuffers_armor_glint" : "gbuffers_textured";
        String fragmentSource;
        try {
            fragmentSource = TextureColorDistanceShaderAdapter.adaptFragment(source, programName, false);
        } catch (IOException exception) {
            if (pack.hasGlintFragment()) {
                throw exception;
            }
            VeyraClient.LOGGER.debug("Skipping the gbuffers_textured armor-glint fallback: {}", exception.getMessage());
            return new GlintPipelineSet(Map.of());
        }
        String sourceHash = sha256(programName + '\n' + fragmentSource);
        Map<RenderPipeline, RenderPipeline> pipelines = new IdentityHashMap<>();
        for (RenderPipeline original : candidates) {
            GlintPipelineKey cacheKey = new GlintPipelineKey(original, sourceHash);
            RenderPipeline replacement = GLINT_PIPELINE_CACHE.get(cacheKey);
            if (replacement == null) {
                replacement = compileColorDistancePipeline(
                        original, programName, fragmentSource, sourceHash);
                GLINT_PIPELINE_CACHE.put(cacheKey, replacement);
            }
            pipelines.put(original, replacement);
        }
        if (pipelines.isEmpty()) {
            throw new IOException("VulkanMod's Minecraft glint render pipeline was not found.");
        }
        return new GlintPipelineSet(pipelines);
    }

    private static boolean isGlintPipeline(RenderPipeline pipeline) {
        return pipeline != null
                && pipeline.getLocation().getPath().equals("pipeline/glint")
                && pipeline.getFragmentShader().equals(Identifier.withDefaultNamespace("core/glint"));
    }

    private static SkyPipelineSet compileSkyBasicPipelines(ShaderPack pack) throws IOException {
        List<RenderPipeline> candidates = RenderPipelines.getStaticPipelines().stream()
                .filter(VeyraRenderer::isSkyBasicPipeline)
                .toList();
        String source = pack.readSkyBasicFragment()
                .orElseThrow(() -> new IOException("The pack no longer contains gbuffers_skybasic.fsh."));
        Map<RenderPipeline, RenderPipeline> pipelines = new IdentityHashMap<>();
        for (RenderPipeline original : candidates) {
            String location = original.getLocation().getPath();
            boolean hasDistances = location.equals("pipeline/sky");
            boolean hasColor = location.equals("pipeline/sunrise_sunset");
            String programName = pack.hasSkyBasicFragment() ? "gbuffers_skybasic" : "gbuffers_basic";
            String fragmentSource;
            try {
                fragmentSource = ColorDistanceShaderAdapter.adaptFragment(
                        source, programName, false, hasColor, hasDistances);
            } catch (IOException exception) {
                if (!pack.hasSkyBasicFragment()) {
                    VeyraClient.LOGGER.debug("Skipping {} because the gbuffers_basic fallback requires inputs the sky pipeline does not provide: {}",
                            location, exception.getMessage());
                    continue;
                }
                throw exception;
            }
            String sourceHash = sha256(programName + '\n' + fragmentSource);
            SkyPipelineKey cacheKey = new SkyPipelineKey(original, sourceHash);
            RenderPipeline replacement = SKY_BASIC_PIPELINE_CACHE.get(cacheKey);
            if (replacement == null) {
                replacement = compileColorDistancePipeline(
                        original, programName, fragmentSource, sourceHash);
                SKY_BASIC_PIPELINE_CACHE.put(cacheKey, replacement);
            }
            pipelines.put(original, replacement);
        }
        if (pipelines.isEmpty() && pack.hasSkyBasicFragment()) {
            throw new IOException("VulkanMod's Minecraft basic sky render pipelines were not found.");
        }
        return new SkyPipelineSet(pipelines);
    }

    private static boolean isSkyBasicPipeline(RenderPipeline pipeline) {
        if (pipeline == null) {
            return false;
        }
        String location = pipeline.getLocation().getPath();
        Identifier vertex = pipeline.getVertexShader();
        Identifier fragment = pipeline.getFragmentShader();
        return (location.equals("pipeline/sky")
                    && vertex.equals(Identifier.withDefaultNamespace("core/sky"))
                    && fragment.equals(Identifier.withDefaultNamespace("core/sky")))
                || (location.equals("pipeline/stars")
                    && vertex.equals(Identifier.withDefaultNamespace("core/stars"))
                    && fragment.equals(Identifier.withDefaultNamespace("core/stars")))
                || (location.equals("pipeline/sunrise_sunset")
                    && vertex.equals(Identifier.withDefaultNamespace("core/position_color"))
                    && fragment.equals(Identifier.withDefaultNamespace("core/position_color")));
    }

    private static SkyPipelineSet compileSkyTexturedPipelines(ShaderPack pack) throws IOException {
        List<RenderPipeline> candidates = RenderPipelines.getStaticPipelines().stream()
                .filter(VeyraRenderer::isSkyTexturedPipeline)
                .toList();
        String source = pack.readSkyTexturedFragment()
                .orElseThrow(() -> new IOException("The pack no longer contains gbuffers_skytextured.fsh or its gbuffers_textured fallback."));
        String programName = pack.hasSkyTexturedFragment() ? "gbuffers_skytextured" : "gbuffers_textured";
        boolean isFallback = !pack.hasSkyTexturedFragment();
        Map<RenderPipeline, RenderPipeline> pipelines = new IdentityHashMap<>();
        for (RenderPipeline original : candidates) {
            boolean hasColor = original.getVertexShader()
                    .equals(Identifier.withDefaultNamespace("core/position_tex_color"));
            String fragmentSource;
            try {
                fragmentSource = TextureColorDistanceShaderAdapter.adaptFragment(
                        source, programName, hasColor, false);
            } catch (IOException exception) {
                if (!isFallback) {
                    throw exception;
                }
                VeyraClient.LOGGER.debug("Skipping {} because the gbuffers_textured fallback requires inputs the sky pipeline does not provide: {}",
                        original.getLocation().getPath(), exception.getMessage());
                continue;
            }
            String sourceHash = sha256(programName + '\n' + fragmentSource);
            SkyPipelineKey cacheKey = new SkyPipelineKey(original, sourceHash);
            RenderPipeline replacement = SKY_TEXTURED_PIPELINE_CACHE.get(cacheKey);
            if (replacement == null) {
                replacement = compileColorDistancePipeline(
                        original, programName, fragmentSource, sourceHash);
                SKY_TEXTURED_PIPELINE_CACHE.put(cacheKey, replacement);
            }
            pipelines.put(original, replacement);
        }
        if (pipelines.isEmpty() && !isFallback) {
            throw new IOException("VulkanMod's Minecraft textured sky render pipelines were not found.");
        }
        return new SkyPipelineSet(pipelines);
    }

    private static boolean isSkyTexturedPipeline(RenderPipeline pipeline) {
        return pipeline != null
                && (pipeline.getLocation().getPath().equals("pipeline/celestial")
                        && pipeline.getVertexShader().equals(Identifier.withDefaultNamespace("core/position_tex"))
                        && pipeline.getFragmentShader().equals(Identifier.withDefaultNamespace("core/position_tex"))
                    || pipeline.getLocation().getPath().equals("pipeline/end_sky")
                        && pipeline.getVertexShader().equals(Identifier.withDefaultNamespace("core/position_tex_color"))
                        && pipeline.getFragmentShader().equals(Identifier.withDefaultNamespace("core/position_tex_color")));
    }

    private static WorldBorderPipelineSet compileWorldBorderPipelines(ShaderPack pack) throws IOException {
        List<RenderPipeline> candidates = RenderPipelines.getStaticPipelines().stream()
                .filter(VeyraRenderer::isWorldBorderPipeline)
                .toList();
        String source = pack.readTexturedFragment()
                .orElseThrow(() -> new IOException("The pack no longer contains gbuffers_textured.fsh."));
        Map<RenderPipeline, RenderPipeline> pipelines = new IdentityHashMap<>();
        for (RenderPipeline original : candidates) {
            String fragmentSource;
            try {
                fragmentSource = TextureColorDistanceShaderAdapter.adaptFragment(
                        source, "gbuffers_textured", false, false);
            } catch (IOException exception) {
                VeyraClient.LOGGER.debug("Skipping the gbuffers_textured world-border pipeline because its inputs are unsupported: {}",
                        exception.getMessage());
                return new WorldBorderPipelineSet(Map.of());
            }
            String sourceHash = sha256("gbuffers_textured\n" + fragmentSource);
            TexturedPipelineKey cacheKey = new TexturedPipelineKey(original, sourceHash);
            RenderPipeline replacement = TEXTURED_PIPELINE_CACHE.get(cacheKey);
            if (replacement == null) {
                replacement = compileColorDistancePipeline(
                        original, "gbuffers_textured", fragmentSource, sourceHash);
                TEXTURED_PIPELINE_CACHE.put(cacheKey, replacement);
            }
            pipelines.put(original, replacement);
        }
        if (pipelines.isEmpty() && candidates.isEmpty()) {
            throw new IOException("VulkanMod's Minecraft world border render pipeline was not found.");
        }
        return new WorldBorderPipelineSet(pipelines);
    }

    private static boolean isWorldBorderPipeline(RenderPipeline pipeline) {
        return pipeline != null
                && pipeline.getLocation().getPath().equals("pipeline/world_border")
                && pipeline.getVertexShader().equals(Identifier.withDefaultNamespace("core/rendertype_world_border"))
                && pipeline.getFragmentShader().equals(Identifier.withDefaultNamespace("core/rendertype_world_border"));
    }

    private static DamagedBlockPipelineSet compileDamagedBlockPipelines(ShaderPack pack) throws IOException {
        List<RenderPipeline> candidates = RenderPipelines.getStaticPipelines().stream()
                .filter(VeyraRenderer::isDamagedBlockPipeline)
                .toList();
        boolean fallback = !pack.hasDamagedBlockFragment();
        String programName = fallback ? "gbuffers_terrain" : "gbuffers_damagedblock";
        String source = pack.readDamagedBlockFragment()
                .orElseThrow(() -> new IOException("The pack no longer contains a damaged-block shader or terrain fallback."));
        Map<RenderPipeline, RenderPipeline> pipelines = new IdentityHashMap<>();
        for (RenderPipeline original : candidates) {
            String fragmentSource;
            try {
                fragmentSource = DamagedBlockShaderAdapter.adaptFragment(source, programName);
            } catch (IOException exception) {
                if (!fallback) {
                    throw exception;
                }
                VeyraClient.LOGGER.debug("Skipping the gbuffers_terrain damaged-block fallback because its inputs are unsupported: {}",
                        exception.getMessage());
                return new DamagedBlockPipelineSet(Map.of());
            }
            String sourceHash = sha256("gbuffers_damagedblock\n" + fragmentSource);
            ColorDistancePipelineKey cacheKey = new ColorDistancePipelineKey(original, sourceHash);
            RenderPipeline replacement = COLOR_DISTANCE_PIPELINE_CACHE.get(cacheKey);
            if (replacement == null) {
                replacement = compileColorDistancePipeline(
                        original, "gbuffers_damagedblock", fragmentSource, sourceHash);
                COLOR_DISTANCE_PIPELINE_CACHE.put(cacheKey, replacement);
            }
            pipelines.put(original, replacement);
        }
        if (pipelines.isEmpty() && !fallback) {
            throw new IOException("Minecraft's block-breaking overlay render pipeline was not found.");
        }
        return new DamagedBlockPipelineSet(pipelines);
    }

    private static boolean isDamagedBlockPipeline(RenderPipeline pipeline) {
        return pipeline != null
                && pipeline.getLocation().getPath().equals("pipeline/crumbling")
                && pipeline.getVertexShader().equals(Identifier.withDefaultNamespace("core/rendertype_crumbling"))
                && pipeline.getFragmentShader().equals(Identifier.withDefaultNamespace("core/rendertype_crumbling"));
    }

    private static RenderPipeline copyPipelineWithFragment(RenderPipeline original, Identifier fragmentShader) {
        return copyPipelineWithShaders(original, original.getVertexShader(), fragmentShader);
    }

    private static RenderPipeline copyPipelineWithShaders(
            RenderPipeline original, Identifier vertexShader, Identifier fragmentShader) {
        RenderPipeline.Builder builder = RenderPipeline.builder()
                .withLocation(original.getLocation())
                .withVertexShader(vertexShader)
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

    private static RenderPipeline copyShadowPipelineWithFragment(
            RenderPipeline original, Identifier location, Identifier fragmentShader) {
        return copyShadowPipelineWithShaders(original, location, original.getVertexShader(), fragmentShader);
    }

    private static RenderPipeline copyShadowPipelineWithShaders(
            RenderPipeline original, Identifier location, Identifier vertexShader, Identifier fragmentShader) {
        RenderPipeline.Builder builder = RenderPipeline.builder()
                .withLocation(location)
                .withVertexShader(vertexShader)
                .withFragmentShader(fragmentShader)
                .withDepthTestFunction(original.getDepthTestFunction())
                .withPolygonMode(original.getPolygonMode())
                .withCull(false)
                .withColorWrite(false, false)
                .withDepthWrite(true)
                .withVertexFormat(original.getVertexFormat(), original.getVertexFormatMode())
                .withDepthBias(original.getDepthBiasScaleFactor(), original.getDepthBiasConstant());

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
            int width, int height, Map<Integer, Identifier> initialColorTargets) {
        List<ShaderPass> passes = new ArrayList<>();
        PostPass copyPass = null;
        Map<Integer, Identifier> currentTargets = new HashMap<>();
        currentTargets.put(0, PostChain.MAIN_TARGET_ID);
        currentTargets.putAll(initialColorTargets);
        try {
            for (ProgramGroup group : programs) {
                Map<Integer, Identifier> outputTargets = new HashMap<>();
                for (PipelineProgram program : group.outputs()) {
                    outputTargets.computeIfAbsent(program.outputTarget(), outputTarget ->
                            nextColorTarget(outputTarget, currentTargets.get(outputTarget)));
                }

                Identifier inputColortex0 = currentTargets.get(0);
                for (PipelineProgram program : group.outputs()) {
                    List<PostPass.Input> inputs = new ArrayList<>();
                    inputs.add(new PostPass.TargetInput("In", inputColortex0, false, true));
                    for (int samplerIndex : program.colorSamplers()) {
                        if (samplerIndex == 0) {
                            continue;
                        }
                        inputs.add(new PostPass.TargetInput(
                                colorTargetInputName(samplerIndex), currentTargets.get(samplerIndex), false, true));
                    }
                    if (program.usesDepthSampler()) {
                        inputs.add(new ExternalTextureInput(DEPTH_INPUT_NAME));
                    }
                    if (program.usesDepth1Sampler()) {
                        inputs.add(new ExternalTextureInput(DEPTH1_INPUT_NAME));
                    }
                    if (program.usesDepth2Sampler()) {
                        inputs.add(new ExternalTextureInput(DEPTH2_INPUT_NAME));
                    }
                    if (program.usesShadowSampler()) {
                        inputs.add(new ExternalTextureInput(SHADOW_DEPTH_INPUT_NAME));
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
            Map<Integer, Identifier> outputColorTargets = new HashMap<>();
            currentTargets.forEach((index, target) -> {
                if (index > 0) {
                    outputColorTargets.put(index, target);
                }
            });
            return new FramePlan(List.copyOf(passes), copyPass,
                    Map.copyOf(initialColorTargets), Map.copyOf(outputColorTargets));
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
        if (targetIndex > MAX_COLORTEX_INDEX) {
            throw new IllegalArgumentException("Veyra only allocates colortex0 through colortex" + MAX_COLORTEX_INDEX + ".");
        }
        return currentTarget.equals(colorTargetAId(targetIndex))
                ? colorTargetBId(targetIndex) : colorTargetAId(targetIndex);
    }

    private static Identifier colorTargetAId(int index) {
        return Identifier.fromNamespaceAndPath("veyra", "colortex" + index + "_a");
    }

    private static Identifier colorTargetBId(int index) {
        return Identifier.fromNamespaceAndPath("veyra", "colortex" + index + "_b");
    }

    private static String colorTargetInputName(int index) {
        return "VeyraColortex" + index;
    }

    private static String colorTargetInputSampler(int index) {
        return colorTargetInputName(index) + "Sampler";
    }

    private static PipelineProgram pipelineProgram(String name, AdaptedShader shader, String vertexSource) {
        String hash = sha256(name + '\n' + (vertexSource == null ? "" : vertexSource) + '\n' + shader.source());
        String cacheKey = name + ':' + hash;
        return PIPELINE_CACHE.computeIfAbsent(cacheKey, ignored -> {
            String suffix = name + '_' + hash;
            Identifier shaderId = Identifier.fromNamespaceAndPath("veyra", "shaderpack/" + suffix);
            Identifier vertexShaderId = vertexSource == null
                    ? BLIT_VERTEX_ID
                    : Identifier.fromNamespaceAndPath("veyra", "shaderpack_vertex/" + suffix);
            Identifier pipelineId = Identifier.fromNamespaceAndPath("veyra", "pipeline/shaderpack/" + suffix);
            return new PipelineProgram(shaderId, shader.source(), vertexShaderId, vertexSource, shader.uniforms(),
                    shader.usesDepthSampler(), shader.usesDepth1Sampler(), shader.usesDepth2Sampler(),
                    shader.usesShadowSampler(),
                    shader.colorSamplers(), shader.outputTarget(),
                    createPipeline(pipelineId, shaderId, vertexShaderId, shader.uniforms(),
                            shader.usesDepthSampler(), shader.usesDepth1Sampler(), shader.usesDepth2Sampler(),
                            shader.usesShadowSampler(),
                            shader.colorSamplers()));
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
            if (program.vertexSource() != null
                    && identifier.equals(program.vertexShaderId()) && type == ShaderType.VERTEX) {
                return program.vertexSource();
            }
            return shaderManager.getShader(identifier, type);
        };

        VeyraPipelineResources.track(program.pipeline());

        CompiledRenderPipeline compiled = RenderSystem.getDevice().precompilePipeline(program.pipeline(), source);
        if (!compiled.isValid()) {
            throw new IllegalStateException("VulkanMod could not compile pipeline " + program.pipeline().getLocation() + ".");
        }
        COMPILED_PIPELINES.add(program);
    }

    private static RenderPipeline createPipeline(
            Identifier pipeline, Identifier fragmentShader, Identifier vertexShader, List<String> uniforms,
            boolean usesDepthSampler, boolean usesDepth1Sampler, boolean usesDepth2Sampler,
            boolean usesShadowSampler,
            Set<Integer> colorSamplers) {
        RenderPipeline.Builder builder = RenderPipeline.builder(RenderPipelines.POST_PROCESSING_SNIPPET)
                .withFragmentShader(fragmentShader)
                .withVertexShader(vertexShader)
                .withLocation(pipeline)
                .withSampler("InSampler")
                .withUniform("SamplerInfo", UniformType.UNIFORM_BUFFER);
        if (usesDepthSampler) {
            builder.withSampler(DEPTH_INPUT_SAMPLER);
        }
        if (usesDepth1Sampler) {
            builder.withSampler(DEPTH1_INPUT_SAMPLER);
        }
        if (usesDepth2Sampler) {
            builder.withSampler(DEPTH2_INPUT_SAMPLER);
        }
        if (usesShadowSampler) {
            builder.withSampler(SHADOW_DEPTH_INPUT_SAMPLER);
        }
        for (int colorSampler : colorSamplers) {
            if (colorSampler > 0) {
                builder.withSampler(colorTargetInputSampler(colorSampler));
            }
        }
        if (!uniforms.isEmpty()) {
            builder.withUniform(SHADERPACK_UNIFORMS, UniformType.UNIFORM_BUFFER);
        }
        return builder.build();
    }

    private static List<AdaptedShader> adaptShader(
            String source, List<String> additionalUniforms, boolean shadowPassAvailable) throws IOException {
        if (source == null || source.isBlank()) {
            throw new IOException("Fragment shader is empty.");
        }
        if (source.length() > 1024 * 1024) {
            throw new IOException("Fragment shader exceeds the 1 MiB size limit.");
        }
        String original = source.replace("\uFEFF", "");
        String body = COLOR_TARGET_FORMAT_DECLARATION.matcher(original).replaceAll("");
        body = RENDERTARGETS_DIRECTIVE.matcher(body).replaceAll("");
        body = DRAWBUFFERS_DIRECTIVE.matcher(body).replaceAll("");
        body = COMMENTS.matcher(body).replaceAll("");

        Matcher unsupportedDepthSampler = UNSUPPORTED_DEPTH_SAMPLER.matcher(body);
        if (unsupportedDepthSampler.find()) {
            throw new IOException("Veyra supports depthtex0, depthtex1, and depthtex2 for depth texture sampling on VulkanMod.");
        }
        Matcher unsupportedShadowSampler = UNSUPPORTED_SHADOW_SAMPLER.matcher(body);
        if (unsupportedShadowSampler.find()) {
            throw new IOException("Veyra currently supports only the shadowtex0 depth sampler in post-processing programs.");
        }
        boolean usesShadowSampler = SHADOW_DEPTH_SAMPLER.matcher(body).find();
        if (usesShadowSampler && !shadowPassAvailable) {
            throw new IOException("The post-processing program samples shadowtex0, but the shader pack has no shadow pass.");
        }
        Matcher shadowSamplerDeclaration = SHADOW_DEPTH_UNIFORM_DECLARATION.matcher(body);
        StringBuffer bodyWithoutShadowSampler = new StringBuffer();
        boolean declaredShadowSampler = false;
        while (shadowSamplerDeclaration.find()) {
            String samplerType = shadowSamplerDeclaration.group(1);
            if (!samplerType.equals("sampler2D") && !samplerType.equals("sampler2DShadow")) {
                throw new IOException("shadowtex0 must be declared as sampler2D or sampler2DShadow.");
            }
            if (declaredShadowSampler) {
                throw new IOException("shadowtex0 is declared more than once.");
            }
            declaredShadowSampler = true;
            shadowSamplerDeclaration.appendReplacement(bodyWithoutShadowSampler, "");
        }
        shadowSamplerDeclaration.appendTail(bodyWithoutShadowSampler);
        body = bodyWithoutShadowSampler.toString();
        Matcher depthSampler = DEPTH_SAMPLER.matcher(body);
        boolean usesDepthSampler = depthSampler.find();
        Matcher depth1Sampler = DEPTH1_SAMPLER.matcher(body);
        boolean usesDepth1Sampler = depth1Sampler.find();
        Matcher depth2Sampler = DEPTH2_SAMPLER.matcher(body);
        boolean usesDepth2Sampler = depth2Sampler.find();
        Set<Integer> colorSamplers = new java.util.TreeSet<>();
        Matcher colorSamplerMatcher = COLORTEX_SAMPLER.matcher(body);
        while (colorSamplerMatcher.find()) {
            int index = Integer.parseInt(colorSamplerMatcher.group(1));
            if (index > MAX_COLORTEX_INDEX) {
                throw new IOException("Veyra supports color samplers colortex0 through colortex" + MAX_COLORTEX_INDEX + ".");
            }
            colorSamplers.add(index);
        }
        String uniformSource = body;
        List<String> uniforms = SUPPORTED_UNIFORMS.stream()
                .filter(uniform -> containsIdentifier(uniformSource, uniform) || additionalUniforms.contains(uniform))
                .toList();
        if (UNSUPPORTED_OUTPUT_DECLARATION.matcher(body).find()) {
            throw new IOException("Veyra only supports vec4 fragment output declarations.");
        }
        List<ExplicitOutput> explicitOutputs = explicitOutputs(body);
        List<OutputMapping> outputMappings = outputMappings(original, body, explicitOutputs);
        boolean usesFragData = body.contains("gl_FragData");
        if (usesFragData && body.matches("(?s).*\\bgl_FragColor\\b.*")) {
            throw new IOException("A pass cannot mix gl_FragData and gl_FragColor outputs.");
        }
        if (usesFragData && !explicitOutputs.isEmpty()) {
            throw new IOException("A pass cannot mix gl_FragData with explicit out variables.");
        }
        if (explicitOutputs.size() > 1 && body.matches("(?s).*\\bgl_FragColor\\b.*")) {
            throw new IOException("A pass cannot mix gl_FragColor with multiple explicit out variables.");
        }
        String outputName = explicitOutputs.isEmpty() ? "outColor" : explicitOutputs.getFirst().name();

        body = VERSION_DIRECTIVE.matcher(body).replaceAll("");
        Matcher extensionMatcher = EXTENSION_DIRECTIVE.matcher(body);
        StringBuilder extensions = new StringBuilder();
        while (extensionMatcher.find()) {
            extensions.append(extensionMatcher.group());
        }
        body = extensionMatcher.replaceAll("");
        body = VARYING_DECLARATION.matcher(body).replaceAll("");
        body = MAIN_SAMPLER_DECLARATION.matcher(body).replaceAll("");
        body = COLORTEX_SAMPLER_DECLARATION.matcher(body).replaceAll("");
        body = DEPTH_SAMPLER_DECLARATION.matcher(body).replaceAll("");
        body = DEPTH1_SAMPLER_DECLARATION.matcher(body).replaceAll("");
        body = DEPTH2_SAMPLER_DECLARATION.matcher(body).replaceAll("");
        body = SUPPORTED_UNIFORM_DECLARATION.matcher(body).replaceAll("");
        body = SUPPORTED_MATRIX_UNIFORM_DECLARATION.matcher(body).replaceAll("");
        body = SUPPORTED_CAMERA_UNIFORM_DECLARATION.matcher(body).replaceAll("");
        body = LOCATION_LAYOUT.matcher(body).replaceAll("");
        if (usesShadowSampler) {
            body = body.replaceAll("\\b(?:texture2D|texture)\\s*\\(\\s*shadowtex0\\s*,", "veyraSample(shadowtex0,");
        }
        body = body.replaceAll("\\btexture2D\\s*\\(", "texture(");

        List<AdaptedShader> adaptedShaders = new ArrayList<>(outputMappings.size());
        for (OutputMapping output : outputMappings) {
            String outputBody = usesFragData
                    ? lowerFragData(body, output.fragmentOutputIndex())
                    : explicitOutputs.size() > 1
                    ? lowerExplicitOutputs(body, explicitOutputs, output.fragmentOutputIndex()) : body;
            StringBuilder adapted = new StringBuilder("#version 450\n").append(extensions)
                    .append("in vec2 texCoord;\n");
            if (explicitOutputs.isEmpty()) {
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
            appendShaderPackUniformBlock(adapted, uniforms);
            if (usesShadowSampler) {
                adapted.append("""
                        vec4 veyraSample(sampler2D sourceSampler, vec2 sourceUv) {
                            return texture(sourceSampler, sourceUv);
                        }
                        vec4 veyraSample(sampler2D sourceSampler, vec2 sourceUv, float lodBias) {
                            return texture(sourceSampler, sourceUv, lodBias);
                        }
                        vec4 veyraSample(sampler2D sourceSampler, vec3 sourceCoord) {
                            float visibility = step(sourceCoord.z - 0.0005, texture(sourceSampler, sourceCoord.xy).r);
                            return vec4(visibility);
                        }
                        vec4 veyraSample(sampler2D sourceSampler, vec3 sourceCoord, float lodBias) {
                            float visibility = step(sourceCoord.z - 0.0005,
                                    textureLod(sourceSampler, sourceCoord.xy, lodBias).r);
                            return vec4(visibility);
                        }
                        """);
            }
            adapted.append("uniform sampler2D InSampler;\n")
                    .append("#define colortex0 InSampler\n")
                    .append("#define Sampler0 InSampler\n")
                    .append("#define texcoord texCoord\n")
                    .append("#define gl_FragColor ").append(outputName).append('\n')
                    .append("#define texture2D texture\n");
            for (int colorSampler : colorSamplers) {
                if (colorSampler > 0) {
                    String samplerName = colorTargetInputSampler(colorSampler);
                    adapted.append("uniform sampler2D ").append(samplerName).append(";\n")
                            .append("#define colortex").append(colorSampler).append(' ')
                            .append(samplerName).append('\n');
                }
            }
            if (usesDepthSampler) {
                adapted.append("uniform sampler2D ").append(DEPTH_INPUT_SAMPLER).append(";\n")
                        .append("#define depthtex0 ").append(DEPTH_INPUT_SAMPLER).append('\n')
                        .append("#define DepthSampler ").append(DEPTH_INPUT_SAMPLER).append('\n');
            }
            if (usesDepth1Sampler) {
                adapted.append("uniform sampler2D ").append(DEPTH1_INPUT_SAMPLER).append(";\n")
                        .append("#define depthtex1 ").append(DEPTH1_INPUT_SAMPLER).append('\n');
            }
            if (usesDepth2Sampler) {
                adapted.append("uniform sampler2D ").append(DEPTH2_INPUT_SAMPLER).append(";\n")
                        .append("#define depthtex2 ").append(DEPTH2_INPUT_SAMPLER).append('\n');
            }
            if (usesShadowSampler) {
                adapted.append("uniform sampler2D ").append(SHADOW_DEPTH_INPUT_SAMPLER).append(";\n")
                        .append("#define shadowtex0 ").append(SHADOW_DEPTH_INPUT_SAMPLER).append('\n')
                        .append("#define shadow2D(shadowSampler, coord) veyraSample(shadowSampler, coord)\n");
            }
            adapted.append(outputBody);
            adaptedShaders.add(new AdaptedShader(adapted.toString(), uniforms,
                    usesDepthSampler, usesDepth1Sampler, usesDepth2Sampler, usesShadowSampler,
                    Set.copyOf(colorSamplers), output.targetIndex()));
        }
        return List.copyOf(adaptedShaders);
    }

    private static List<ExplicitOutput> explicitOutputs(String body) throws IOException {
        List<ExplicitOutput> outputs = new ArrayList<>();
        Matcher matcher = OUTPUT_DECLARATION.matcher(body);
        while (matcher.find()) {
            String name = matcher.group(2);
            int location;
            if (matcher.group(1) != null) {
                try {
                    location = Integer.parseInt(matcher.group(1));
                } catch (NumberFormatException exception) {
                    throw new IOException("Fragment output location is outside the supported integer range.", exception);
                }
            } else {
                Matcher colorIndex = OUTPUT_COLOR_INDEX.matcher(name);
                if (colorIndex.matches()) {
                    try {
                        location = Integer.parseInt(colorIndex.group(1));
                    } catch (NumberFormatException exception) {
                        throw new IOException("Fragment output location is outside the supported integer range.", exception);
                    }
                } else if (outputs.isEmpty()) {
                    location = 0;
                } else {
                    throw new IOException("Multiple fragment outputs need layout(location = N) or the Iris outColorN names.");
                }
            }
            if (outputs.stream().anyMatch(output -> output.location() == location)) {
                throw new IOException("Fragment output locations must be unique.");
            }
            outputs.add(new ExplicitOutput(location, name));
        }
        outputs.sort(java.util.Comparator.comparingInt(ExplicitOutput::location));
        return List.copyOf(outputs);
    }

    private static List<OutputMapping> outputMappings(String original, String body,
                                                      List<ExplicitOutput> explicitOutputs) throws IOException {
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
        if (usesFragData && !explicitOutputs.isEmpty()) {
            throw new IOException("A pass cannot mix gl_FragData with explicit out variables.");
        }

        List<OutputMapping> mappings = new ArrayList<>();
        if (!explicitOutputs.isEmpty()) {
            Set<Integer> declaredLocations = new java.util.HashSet<>();
            for (ExplicitOutput output : explicitOutputs) {
                int targetIndex = output.location();
                if (declaredTargets != null) {
                    if (output.location() >= declaredTargets.size()) {
                        throw new IOException("Explicit fragment output location " + output.location()
                                + " is missing from RENDERTARGETS/DRAWBUFFERS.");
                    }
                    targetIndex = declaredTargets.get(output.location());
                }
                declaredLocations.add(output.location());
                mappings.add(new OutputMapping(output.location(), targetIndex));
            }
            if (declaredTargets != null) {
                for (int location = 0; location < declaredTargets.size(); location++) {
                    if (!declaredLocations.contains(location)) {
                        throw new IOException("RENDERTARGETS/DRAWBUFFERS declares output location " + location
                                + " but the shader has no matching explicit out variable.");
                    }
                }
            }
        } else if (declaredTargets != null) {
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
            mappings.add(new OutputMapping(-1, 0));
        }

        Set<Integer> uniqueTargets = new java.util.HashSet<>();
        for (OutputMapping mapping : mappings) {
            if (mapping.targetIndex() < 0 || mapping.targetIndex() > MAX_COLORTEX_INDEX) {
                throw new IOException("Veyra supports render targets colortex0 through colortex" + MAX_COLORTEX_INDEX + ".");
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

    private static String lowerExplicitOutputs(String source, List<ExplicitOutput> outputs, int selectedLocation) {
        ExplicitOutput selected = outputs.stream()
                .filter(output -> output.location() == selectedLocation)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No explicit output for location " + selectedLocation + "."));
        String lowered = source;
        for (ExplicitOutput output : outputs) {
            Pattern declaration = Pattern.compile("(?m)^[\\t ]*out\\s+vec4\\s+"
                    + Pattern.quote(output.name()) + "\\s*;[\\t ]*$");
            Matcher matcher = declaration.matcher(lowered);
            if (!matcher.find()) {
                throw new IllegalArgumentException("Could not find explicit output declaration " + output.name() + ".");
            }
            String replacement = output.location() == selectedLocation
                    ? "out vec4 outColor;" : "vec4 " + output.name() + ";";
            lowered = matcher.replaceFirst(Matcher.quoteReplacement(replacement));
        }
        if (!selected.name().equals("outColor")) {
            lowered = Pattern.compile("\\b" + Pattern.quote(selected.name()) + "\\b")
                    .matcher(lowered).replaceAll(Matcher.quoteReplacement("outColor"));
        }
        return lowered;
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
                case "gbufferModelView" -> new UniformValue.Matrix4x4Uniform(frameUniforms.matrices().gbufferModelView());
                case "gbufferModelViewInverse" -> new UniformValue.Matrix4x4Uniform(frameUniforms.matrices().gbufferModelViewInverse());
                case "gbufferProjection" -> new UniformValue.Matrix4x4Uniform(frameUniforms.matrices().gbufferProjection());
                case "gbufferProjectionInverse" -> new UniformValue.Matrix4x4Uniform(frameUniforms.matrices().gbufferProjectionInverse());
                case "gbufferPreviousModelView" -> new UniformValue.Matrix4x4Uniform(frameUniforms.matrices().gbufferPreviousModelView());
                case "gbufferPreviousProjection" -> new UniformValue.Matrix4x4Uniform(frameUniforms.matrices().gbufferPreviousProjection());
                case "shadowModelView" -> new UniformValue.Matrix4x4Uniform(frameUniforms.matrices().shadowModelView());
                case "shadowModelViewInverse" -> new UniformValue.Matrix4x4Uniform(frameUniforms.matrices().shadowModelViewInverse());
                case "shadowProjection" -> new UniformValue.Matrix4x4Uniform(frameUniforms.matrices().shadowProjection());
                case "shadowProjectionInverse" -> new UniformValue.Matrix4x4Uniform(frameUniforms.matrices().shadowProjectionInverse());
                case "cameraPosition" -> new UniformValue.Vec3Uniform(frameUniforms.camera().cameraPosition());
                case "cameraPositionInt" -> new UniformValue.IVec3Uniform(frameUniforms.camera().cameraPositionInt());
                case "cameraPositionFract" -> new UniformValue.Vec3Uniform(frameUniforms.camera().cameraPositionFract());
                case "previousCameraPosition" -> new UniformValue.Vec3Uniform(frameUniforms.camera().previousCameraPosition());
                case "previousCameraPositionInt" -> new UniformValue.IVec3Uniform(frameUniforms.camera().previousCameraPositionInt());
                case "previousCameraPositionFract" -> new UniformValue.Vec3Uniform(frameUniforms.camera().previousCameraPositionFract());
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
                player.hurtTime > 0, player.isInvisible(), player.isOnFire(), frameMatrices, cameraUniforms);
    }

    private static boolean containsIdentifier(String source, String identifier) {
        int index = source.indexOf(identifier);
        while (index >= 0) {
            int end = index + identifier.length();
            boolean startsIdentifier = index > 0 && isIdentifierCharacter(source.charAt(index - 1));
            boolean endsIdentifier = end < source.length() && isIdentifierCharacter(source.charAt(end));
            if (!startsIdentifier && !endsIdentifier) {
                return true;
            }
            index = source.indexOf(identifier, index + 1);
        }
        return false;
    }

    private static boolean isIdentifierCharacter(char character) {
        return character == '_' || Character.isLetterOrDigit(character);
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

    private static void releaseSwapTargets() {
        if (swapTargetA != null) {
            swapTargetA.destroyBuffers();
            swapTargetA = null;
        }
        if (swapTargetB != null) {
            swapTargetB.destroyBuffers();
            swapTargetB = null;
        }
    }

    private static void releaseColorTargets() {
        VeyraGeometryTargets.releaseTargets();
        colorTargets.values().forEach(pair -> {
            pair.targetA().destroyBuffers();
            pair.targetB().destroyBuffers();
        });
        colorTargets.clear();
        initializedColorTargets.clear();
        currentColorTargets.clear();
    }

    public static net.vulkanmod.vulkan.texture.VulkanImage geometryColorAttachment(int index) {
        ColorTargetPair pair = colorTargets.get(index);
        if (pair == null) throw new IllegalStateException("Geometry colortex" + index + " has not been allocated.");
        Identifier side = currentColorTargets.get(index);
        RenderTarget target = colorTargetAId(index).equals(side) ? pair.targetA() : pair.targetB();
        return ((VkGpuTexture) target.getColorTexture()).getVulkanImage();
    }

    private static void ensureColorTargets(int width, int height, Set<Integer> indices,
                                          Map<Integer, ColorTargetSettings> settings) {
        boolean resized = false;
        for (int index : indices) {
            ColorTargetSettings targetSettings = settings.getOrDefault(
                    index, ColorTargetSettings.defaults(index, width, height));
            ColorTargetPair pair = colorTargets.get(index);
            boolean changed = false;
            if (pair == null) {
                pair = new ColorTargetPair(
                        new TextureTarget("Veyra shader pack colortex" + index + " A",
                                targetSettings.width(), targetSettings.height(), false),
                        new TextureTarget("Veyra shader pack colortex" + index + " B",
                                targetSettings.width(), targetSettings.height(), false));
                colorTargets.put(index, pair);
                initializedColorTargets.remove(index);
                changed = true;
            }
            changed |= ensureColorTarget(pair.targetA(), index, "A", targetSettings);
            changed |= ensureColorTarget(pair.targetB(), index, "B", targetSettings);
            if (changed) {
                VeyraGeometryTargets.releaseTargets();
                initializedColorTargets.remove(index);
                resized = true;
                VeyraClient.LOGGER.info("Configured colortex{} {}x{} {}.", index,
                        targetSettings.width(), targetSettings.height(),
                        ColorTargetSettings.formatName(targetSettings.format()));
            }
        }
        if (resized) {
            currentColorTargets.clear();
            for (int index : indices) {
                currentColorTargets.put(index, colorTargetAId(index));
            }
        }
    }

    private static boolean ensureColorTarget(RenderTarget target, int index, String side,
                                             ColorTargetSettings settings) {
        boolean changed = false;
        if (target.width != settings.width() || target.height != settings.height()) {
            target.resize(settings.width(), settings.height());
            changed = true;
        }
        GpuTexture colorTexture = target.getColorTexture();
        if (!(colorTexture instanceof VkGpuTexture vulkanTexture)) {
            throw new IllegalStateException("VulkanMod did not create a Vulkan texture for colortex" + index + ".");
        }
        if (vulkanTexture.getVulkanImage().format != settings.format()) {
            replaceColorTargetTexture(target, "Veyra shader pack colortex" + index + " " + side,
                    settings.format());
            changed = true;
        }
        return changed;
    }

    private static void replaceColorTargetTexture(RenderTarget target, String name, int format) {
        if (!(RenderSystem.getDevice() instanceof VkGpuDevice device)) {
            throw new IllegalStateException("The active GPU device is not VulkanMod's Vulkan device.");
        }
        RenderTargetAccessor accessor = (RenderTargetAccessor) target;
        GpuTexture oldTexture = accessor.veyra$getColorTexture();
        GpuTextureView oldView = accessor.veyra$getColorTextureView();
        int usage = oldTexture == null
                ? GpuTexture.USAGE_RENDER_ATTACHMENT | GpuTexture.USAGE_TEXTURE_BINDING
                : oldTexture.usage();
        // Keep Minecraft's texture metadata on a supported format; the Vulkan image below carries the actual format.
        GpuTexture replacement = device.createTexture(name, usage, TextureFormat.RGBA8,
                target.width, target.height, 1, 1);
        if (!(replacement instanceof VkGpuTexture vulkanTexture)) {
            replacement.close();
            throw new IllegalStateException("VulkanMod did not create a Vulkan texture for " + name + ".");
        }

        VulkanImage defaultImage = vulkanTexture.getVulkanImage();
        VulkanImage colorImage = null;
        GpuTextureView replacementView = null;
        boolean installed = false;
        try {
            colorImage = VulkanImage.builder(target.width, target.height)
                    .setName(name)
                    .setFormat(format)
                    .addUsage(VK10.VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT)
                    .createVulkanImage();
            VkGlTexture.setVulkanImage(vulkanTexture.glId(), colorImage);
            installed = true;
            defaultImage.free();
            replacementView = device.createTextureView(replacement);
        } catch (RuntimeException | Error exception) {
            if (replacementView != null && !replacementView.isClosed()) {
                replacementView.close();
            }
            if (!installed && colorImage != null) {
                colorImage.free();
            }
            if (!replacement.isClosed()) {
                replacement.close();
            }
            throw exception;
        }

        accessor.veyra$setColorTexture(replacement);
        accessor.veyra$setColorTextureView(replacementView);
        if (oldView != null && !oldView.isClosed()) {
            oldView.close();
        }
        if (oldTexture != null && !oldTexture.isClosed()) {
            oldTexture.close();
        }
    }

    private static void clearColorTargetBuffers(Set<Integer> indices,
                                                Map<Integer, ColorTargetSettings> settings) {
        boolean hasTargetsToClear = indices.stream().anyMatch(index -> {
            ColorTargetPair pair = colorTargets.get(index);
            ColorTargetSettings targetSettings = settings.getOrDefault(index,
                    ColorTargetSettings.defaults(index, pair.targetA().width, pair.targetA().height));
            return !initializedColorTargets.contains(index) || targetSettings.clearEachFrame();
        });
        if (!hasTargetsToClear) {
            return;
        }

        Renderer renderer = Renderer.getInstance();
        if (renderer.getBoundRenderPass() != null) {
            renderer.endRenderPass();
        }
        var commandEncoder = RenderSystem.getDevice().createCommandEncoder();
        for (int index : indices) {
            ColorTargetPair pair = colorTargets.get(index);
            ColorTargetSettings targetSettings = settings.getOrDefault(index,
                    ColorTargetSettings.defaults(index, pair.targetA().width, pair.targetA().height));
            if (initializedColorTargets.contains(index) && !targetSettings.clearEachFrame()) {
                continue;
            }
            commandEncoder.clearColorTexture(pair.targetA().getColorTexture(), targetSettings.clearColor());
            commandEncoder.clearColorTexture(pair.targetB().getColorTexture(), targetSettings.clearColor());
            initializedColorTargets.add(index);
        }
    }

    private static void replacePipelineSet(PipelineSet next) {
        PipelineSet old = pipelineSet;
        pipelineSet = next;
        if (old != null && old != next) {
            old.close();
        }
        if (old != next) {
            currentColorTargets.clear();
            if (next != null) {
                for (int index : next.colorTargetIndices()) {
                    currentColorTargets.put(index, colorTargetAId(index));
                }
                initializedColorTargets.removeAll(next.colorTargetIndices());
            }
        }
        if ((next == null || !next.usesDepthSampler())
                && sampledDepthTextureView != null && !sampledDepthTextureView.isClosed()) {
            sampledDepthTextureView.close();
            sampledDepthTextureView = null;
            sampledDepthTexture = null;
        }
        if (next == null || !next.usesDepth1Sampler()) {
            depthTex1Snapshot.close();
        }
        if (next == null || !next.usesDepth2Sampler()) {
            depthTex2Snapshot.close();
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

    private static void replaceBlockEntityPipelines(EntityPipelineSet next) {
        blockEntityPipelineSet = next;
        blockEntityPipelineRoutingLogged = false;
    }

    private static void replaceHandPipelines(EntityPipelineSet next) {
        handPipelineSet = next;
        handPipelineRoutingLogged = false;
    }

    private static void replaceParticlePipelines(ParticlePipelineSet next) {
        particlePipelineSet = next;
    }

    private static void replaceWeatherPipelines(WeatherPipelineSet next) {
        weatherPipelineSet = next;
    }

    private static void replaceLightningPipelines(LightningPipelineSet next) {
        lightningPipelineSet = next;
    }

    private static void replaceBasicPipelines(ColorDistancePipelineSet next) {
        basicPipelineSet = next;
    }

    private static void replaceLinePipelines(ColorDistancePipelineSet next) {
        linePipelineSet = next;
    }

    private static void replaceGlintPipelines(GlintPipelineSet next) {
        glintPipelineSet = next;
    }

    private static void replaceSkyBasicPipelines(SkyPipelineSet next) {
        skyBasicPipelineSet = next;
    }

    private static void replaceSkyTexturedPipelines(SkyPipelineSet next) {
        skyTexturedPipelineSet = next;
    }

    private static void replaceWorldBorderPipelines(WorldBorderPipelineSet next) {
        worldBorderPipelineSet = next;
    }

    private static void replaceDamagedBlockPipelines(DamagedBlockPipelineSet next) {
        damagedBlockPipelineSet = next;
        damagedBlockRoutingLogged = false;
    }

    private static void replaceBeaconBeamPipelines(BeaconBeamPipelineSet next) {
        beaconBeamPipelineSet = next;
    }

    private static String shortError(Exception exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? exception.getClass().getSimpleName() : message;
    }

    private record AdaptedShader(String source, List<String> uniforms,
                                 boolean usesDepthSampler, boolean usesDepth1Sampler, boolean usesDepth2Sampler,
                                 boolean usesShadowSampler,
                                 Set<Integer> colorSamplers, int outputTarget) {
    }

    private record ExplicitOutput(int location, String name) {
    }

    private record DepthSamplerState(VulkanImage sourceImage, VulkanImage sampledImage) {
    }

    private record PipelineProgram(Identifier shaderId, String fragmentSource,
                                   Identifier vertexShaderId, String vertexSource, List<String> uniforms,
                                   boolean usesDepthSampler, boolean usesDepth1Sampler, boolean usesDepth2Sampler,
                                   boolean usesShadowSampler,
                                   Set<Integer> colorSamplers,
                                   int outputTarget, RenderPipeline pipeline) {
    }

    private record EntityPipelineKey(RenderPipeline original, String programName, String sourceHash) {
    }

    private record ParticlePipelineKey(RenderPipeline original, String programName, String sourceHash) {
    }

    private record WeatherPipelineKey(RenderPipeline original, String sourceHash) {
    }

    private record LightningPipelineKey(RenderPipeline original, String sourceHash) {
    }

    private record ColorDistancePipelineKey(RenderPipeline original, String sourceHash) {
    }

    private record GlintPipelineKey(RenderPipeline original, String sourceHash) {
    }

    private record SkyPipelineKey(RenderPipeline original, String sourceHash) {
    }

    private record TexturedPipelineKey(RenderPipeline original, String sourceHash) {
    }

    private record BeaconBeamPipelineKey(RenderPipeline original, String sourceHash) {
    }

    private record ProgramGroup(ShaderPassPhase phase, List<PipelineProgram> outputs) {
    }

    private record ColorTargetPair(RenderTarget targetA, RenderTarget targetB) {
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
                                 boolean isOnGround, boolean isHurt, boolean isInvisible, boolean isBurning,
                                 FrameMatrices matrices, CameraUniforms camera) {
        private static final FrameUniforms ZERO = new FrameUniforms(0.0F, 0.0F, 0,
                0.0F, 0.0F, 0.0F, 0.0F, 0, 0, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0,
                false, false, false, false, false, false, false, FrameMatrices.IDENTITY, CameraUniforms.ZERO);
    }

    private record FrameMatrices(Matrix4f gbufferModelView, Matrix4f gbufferModelViewInverse,
                                 Matrix4f gbufferProjection, Matrix4f gbufferProjectionInverse,
                                 Matrix4f gbufferPreviousModelView, Matrix4f gbufferPreviousProjection,
                                 Matrix4f shadowModelView, Matrix4f shadowModelViewInverse,
                                 Matrix4f shadowProjection, Matrix4f shadowProjectionInverse) {
        private static final FrameMatrices IDENTITY = new FrameMatrices(
                new Matrix4f(), new Matrix4f(), new Matrix4f(), new Matrix4f(), new Matrix4f(), new Matrix4f(),
                new Matrix4f(), new Matrix4f(), new Matrix4f(), new Matrix4f());
    }

    private record CameraPosition(Vector3f position, Vector3i integerPosition, Vector3f fractionalPosition) {
    }

    private record CameraUniforms(Vector3f cameraPosition, Vector3i cameraPositionInt, Vector3f cameraPositionFract,
                                  Vector3f previousCameraPosition, Vector3i previousCameraPositionInt,
                                  Vector3f previousCameraPositionFract, Vector2i eyeBrightness) {
        private static final CameraUniforms ZERO = new CameraUniforms(
                new Vector3f(), new Vector3i(), new Vector3f(),
                new Vector3f(), new Vector3i(), new Vector3f(), new Vector2i());
    }

    private record FramePlan(List<ShaderPass> shaderPasses, PostPass copyPass,
                             Map<Integer, Identifier> initialColorTargets,
                             Map<Integer, Identifier> outputColorTargets) implements AutoCloseable {
        @Override
        public void close() {
            shaderPasses.forEach(shaderPass -> shaderPass.pass().close());
            if (copyPass != null) {
                copyPass.close();
            }
        }
    }

    private record PipelineSet(List<FramePlan> plansA, List<FramePlan> plansB, int width, int height,
                               boolean usesDepthSampler, boolean usesDepth1Sampler, boolean usesDepth2Sampler,
                               boolean usesShadowSampler, Map<Integer, ColorTargetSettings> colorTargetSettings,
                               Set<Integer> colorTargetIndices) implements AutoCloseable {
        private FramePlan framePlan(ShaderPassPhase phase, Map<Integer, Identifier> current) {
            FramePlan a = plansA.get(phase.ordinal()), b = plansB.get(phase.ordinal());
            if (a.initialColorTargets().equals(current)) return a;
            if (b.initialColorTargets().equals(current)) return b;
            throw new IllegalStateException("The current colortex buffers do not match " + phase + " frame plans.");
        }
        private int passCount() { return plansA.stream().mapToInt(plan -> plan.shaderPasses().size()).sum(); }
        @Override public void close() {
            Set<FramePlan> all = Collections.newSetFromMap(new IdentityHashMap<>());
            all.addAll(plansA); all.addAll(plansB); all.forEach(FramePlan::close);
        }
    }

    private record TerrainPipelineSet(Map<String, GraphicsPipeline> pipelines,
                                      Map<String, GraphicsPipeline> shadowPipelines) implements AutoCloseable {
        private boolean hasShadowPipelines() {
            return !shadowPipelines.isEmpty();
        }

        private GraphicsPipeline shadowPipeline(TerrainRenderType terrainRenderType) {
            if (terrainRenderType == null) {
                return null;
            }
            String programName = switch (terrainRenderType) {
                case CUTOUT, TRIPWIRE -> "shadow_cutout";
                case TRANSLUCENT -> "shadow_water";
                case SOLID -> "shadow_solid";
            };
            return shadowPipelines.get(programName);
        }

        @Override
        public void close() {
            pipelines.values().forEach(VeyraGeometryTargets::unregister);
            pipelines.values().forEach(GraphicsPipeline::scheduleCleanUp);
            shadowPipelines.values().forEach(GraphicsPipeline::scheduleCleanUp);
        }
    }

    private record EntityPipelineSet(Map<RenderPipeline, RenderPipeline> pipelines) {
    }

    private record ParticlePipelineSet(Map<RenderPipeline, RenderPipeline> pipelines) {
    }

    private record WeatherPipelineSet(Map<RenderPipeline, RenderPipeline> pipelines) {
    }

    private record LightningPipelineSet(Map<RenderPipeline, RenderPipeline> pipelines) {
    }

    private record ColorDistancePipelineSet(Map<RenderPipeline, RenderPipeline> pipelines) {
    }

    private record GlintPipelineSet(Map<RenderPipeline, RenderPipeline> pipelines) {
    }

    private record SkyPipelineSet(Map<RenderPipeline, RenderPipeline> pipelines) {
    }

    private record WorldBorderPipelineSet(Map<RenderPipeline, RenderPipeline> pipelines) {
    }

    private record DamagedBlockPipelineSet(Map<RenderPipeline, RenderPipeline> pipelines) {
    }

    private record BeaconBeamPipelineSet(Map<RenderPipeline, RenderPipeline> pipelines) {
    }

    private record ExternalTextureInput(String samplerName) implements PostPass.Input {
        @Override
        public void addToPass(FramePass pass, Map<Identifier, ResourceHandle<RenderTarget>> targets) {
        }

        @Override
        public GpuTextureView texture(Map<Identifier, ResourceHandle<RenderTarget>> targets) {
            if (samplerName.equals(DEPTH1_INPUT_NAME)) {
                return depthTex1Snapshot.sampledView();
            }
            if (samplerName.equals(DEPTH2_INPUT_NAME)) {
                return depthTex2Snapshot.sampledView();
            }
            if (samplerName.equals(SHADOW_DEPTH_INPUT_NAME)) {
                return VeyraShadowRenderer.shadowDepthTextureView();
            }
            return sampledDepthTextureView();
        }

        @Override
        public boolean bilinear() {
            return false;
        }
    }

}
