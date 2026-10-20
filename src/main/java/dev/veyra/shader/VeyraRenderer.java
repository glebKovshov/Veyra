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
import dev.veyra.vulkan.VeyraDeviceFeatures;
import dev.veyra.mixin.accessor.MinecraftAccessor;
import dev.veyra.mixin.accessor.PostPassAccessor;
import dev.veyra.mixin.accessor.RenderSetupAccessor;
import dev.veyra.mixin.accessor.RenderTargetAccessor;
import dev.veyra.mixin.accessor.RenderTypeAccessor;
import dev.veyra.mixin.accessor.VkGpuTextureAccessor;
import dev.veyra.mixin.accessor.ShaderManagerAccessor;
import dev.veyra.vulkan.VeyraDepthTextureView;
import dev.veyra.vulkan.VeyraDepthTextureSnapshot;
import dev.veyra.vulkan.VeyraColorTextureSnapshot;
import dev.veyra.vulkan.VeyraNoiseTexture;
import dev.veyra.vulkan.VeyraSpecularTextures;
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
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.level.MoonPhase;
import net.vulkanmod.gl.VkGlTexture;
import net.vulkanmod.render.engine.VkGpuDevice;
import net.vulkanmod.render.engine.VkGpuTexture;
import net.vulkanmod.render.engine.VkRenderPass;
import net.vulkanmod.render.engine.VkTextureView;
import net.vulkanmod.render.shader.PipelineConfigs;
import net.vulkanmod.render.shader.PipelineManager;
import net.vulkanmod.render.shader.ShaderLoadUtil;
import net.vulkanmod.render.vertex.TerrainRenderType;
import net.vulkanmod.interfaces.shader.ExtendedRenderPipeline;
import net.vulkanmod.vulkan.Renderer;
import net.vulkanmod.vulkan.VRenderSystem;
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
import org.joml.Vector4f;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkFormatProperties;
import org.lwjgl.vulkan.VK10;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
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
    private static final String CLOUD_SCENE_SAMPLER_NAME = "VeyraCloudSceneSampler";
    private static final int CLOUD_SCENE_TEXTURE_SLOT = 11;
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
    private static final Pattern VERSION_DIRECTIVE = Pattern.compile("(?m)^[\\t ]*#version[^\\r\\n]*(?:\\r?\\n|$)");
    private static final Pattern EXTENSION_DIRECTIVE = Pattern.compile("(?m)^[\\t ]*#extension[^\\r\\n]*(?:\\r?\\n|$)");
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
    private static final Pattern VARYING_DECLARATION = Pattern.compile("(?m)^[\\t ]*(?:varying|in)\\s+vec2\\s+(?:texcoord|texCoord)\\s*;\\s*$");
    private static final Pattern POST_VERTEX_OUTPUT_DECLARATION = Pattern.compile(
            "(?m)^[\\t ]*(?:layout\\s*\\(\\s*location\\s*=\\s*(\\d+)\\s*\\)\\s*)?(?:varying|out)\\s+([A-Za-z_][A-Za-z0-9_]*)\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*;[\\t ]*(?://[^\\r\\n]*)?(?:\\r?\\n|$)");
    private static final Pattern POST_VERTEX_INPUT_DECLARATION = Pattern.compile(
            "(?m)^[\\t ]*(?:layout\\s*\\([^)]*\\)\\s*)?(?:in|attribute)\\s+[^;]+;[\\t ]*(?://[^\\r\\n]*)?(?:\\r?\\n|$)");
    private static final Pattern POST_VERTEX_OTHER_OUTPUT_DECLARATION = Pattern.compile(
            "(?m)^[\\t ]*(?:layout\\s*\\([^)]*\\)\\s*)?(?:varying|out)\\s+[^;]+;[\\t ]*(?://[^\\r\\n]*)?(?:\\r?\\n|$)");
    private static final Pattern POST_VERTEX_UNIFORM_DECLARATION = Pattern.compile(
            "(?m)^[\\t ]*uniform\\s+(?:(?:lowp|mediump|highp)\\s+)?([A-Za-z_][A-Za-z0-9_]*)\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*;[\\t ]*(?://[^\\r\\n]*)?(?:\\r?\\n|$)");
    private static final Pattern UNSUPPORTED_VERTEX_UNIFORM_DECLARATION = Pattern.compile("(?m)^[\\t ]*uniform\\b");
    private static final Pattern POST_VERTEX_MAIN = Pattern.compile("\\bvoid\\s+main\\s*\\(\\s*(?:void\\s*)?\\)\\s*\\{");
    private static final Pattern POST_VERTEX_LEGACY_ATTRIBUTES = Pattern.compile(
            "\\b(?:gl_Normal|gl_Color|gl_MultiTexCoord[1-9][0-9]*|gl_ModelViewMatrix|gl_ProjectionMatrix|gl_ModelViewProjectionMatrix)\\b");
    private static final Pattern MAIN_SAMPLER_DECLARATION = Pattern.compile("(?m)^[\\t ]*uniform\\s+sampler2D\\s+(?:colortex0|Sampler0|InSampler)\\s*;\\s*$");
    private static final Pattern COLORTEX_SAMPLER_DECLARATION = Pattern.compile("(?m)^[\\t ]*uniform\\s+(?:(?:lowp|mediump|highp)\\s+)?sampler2D\\s+colortex(?:[1-9]|1[0-5])\\s*;[^\\r\\n]*(?:\\r?\\n|$)");
    private static final Pattern SUPPORTED_UNIFORM_DECLARATION = Pattern.compile("(?m)^[\\t ]*uniform\\s+(?:float|int|bool)\\s+(?:near|far|heldBlockLightValue2?|heldItemId2?|darknessLightFactor|shadowFade|velocity|playerMood|biome_precipitation|viewWidth|viewHeight|aspectRatio|frameTimeCounter|frameTime|frameCounter|sunAngle|rainStrength|wetness|thunderStrength|worldTime|worldDay|moonPhase|bedrockLevel|cloudHeight|endFlashIntensity|eyeAltitude|screenBrightness|blindness|nightVision|darknessFactor|isEyeInWater|isSpectator|is_sneaking|is_sprinting|is_on_ground|is_hurt|is_invisible|is_burning)\\s*;[^\\r\\n]*(?:\\r?\\n|$)");
    private static final Pattern SUPPORTED_IVEC2_UNIFORM_DECLARATION = Pattern.compile(
            "(?m)^[\\t ]*uniform\\s+(?:(?:lowp|mediump|highp)\\s+)?ivec2\\s+(?:eyeBrightness|eyeBrightnessSmooth|atlasSize)\\s*;[^\\r\\n]*(?:\\r?\\n|$)");
    private static final Pattern SUPPORTED_MATRIX_UNIFORM_DECLARATION = Pattern.compile(
            "(?m)^[\\t ]*uniform\\s+(?:(?:lowp|mediump|highp)\\s+)?mat4\\s+(?:gbufferModelView|gbufferModelViewInverse|gbufferProjection|gbufferProjectionInverse|gbufferPreviousModelView|gbufferPreviousProjection|shadowModelView|shadowModelViewInverse|shadowProjection|shadowProjectionInverse)\\s*;[^\\r\\n]*(?:\\r?\\n|$)");
    private static final Pattern SUPPORTED_CAMERA_UNIFORM_DECLARATION = Pattern.compile(
            "(?m)^[\\t ]*uniform\\s+(?:(?:lowp|mediump|highp)\\s+)?(?:vec3|ivec3|vec4)\\s+(?:lightningBoltPosition|playerLookVector|skyColor|eyePosition|relativeEyePosition|cameraPosition|cameraPositionInt|cameraPositionFract|previousCameraPosition|previousCameraPositionInt|previousCameraPositionFract)\\s*;[^\\r\\n]*(?:\\r?\\n|$)");
    private static final Pattern COLORTEX_SAMPLER = Pattern.compile("\\bcolortex([0-9]+)\\b");
    private static final Pattern UNSUPPORTED_DEPTH_SAMPLER = Pattern.compile("\\bdepthtex(?![012]\\b)[A-Za-z0-9_]*\\b");
    private static final Pattern DEPTH_SAMPLER = Pattern.compile("\\b(?:depthtex0|DepthSampler)\\b");
    private static final Pattern DEPTH1_SAMPLER = Pattern.compile("\\bdepthtex1\\b");
    private static final Pattern DEPTH2_SAMPLER = Pattern.compile("\\bdepthtex2\\b");
    private static final Pattern DEPTH_SAMPLER_DECLARATION = Pattern.compile(
            "(?m)^[\\t ]*uniform\\s+(?:(?:lowp|mediump|highp)\\s+)?sampler2D\\s+(?:depthtex0|DepthSampler)\\s*;[^\\r\\n]*(?:\\r?\\n|$)");
    private static final Pattern DEPTH1_SAMPLER_DECLARATION = Pattern.compile(
            "(?m)^[\\t ]*uniform\\s+(?:(?:lowp|mediump|highp)\\s+)?sampler2D\\s+depthtex1\\s*;[^\\r\\n]*(?:\\r?\\n|$)");
    private static final Pattern DEPTH2_SAMPLER_DECLARATION = Pattern.compile(
            "(?m)^[\\t ]*uniform\\s+(?:(?:lowp|mediump|highp)\\s+)?sampler2D\\s+depthtex2\\s*;[^\\r\\n]*(?:\\r?\\n|$)");
    private static final Pattern NOISETEX_UNIFORM_DECLARATION = Pattern.compile(
            "(?m)^[\\t ]*uniform\\s+(?:(?:lowp|mediump|highp)\\s+)?([A-Za-z_][A-Za-z0-9_]*)\\s+noisetex\\s*;[^\\r\\n]*(?:\\r?\\n|$)");
    private static final Pattern NOISETEX_REFERENCE = Pattern.compile("\\bnoisetex\\b");
    // PostPass appends "Sampler" to every input name when binding it to the render pass.
    private static final String DEPTH_INPUT_NAME = "Sampler1";
    private static final String DEPTH_INPUT_SAMPLER = DEPTH_INPUT_NAME + "Sampler";
    private static final String DEPTH1_INPUT_NAME = "VeyraDepthTex1";
    private static final String DEPTH1_INPUT_SAMPLER = DEPTH1_INPUT_NAME + "Sampler";
    private static final String DEPTH2_INPUT_NAME = "VeyraDepthTex2";
    private static final String DEPTH2_INPUT_SAMPLER = DEPTH2_INPUT_NAME + "Sampler";
    private static final String NOISE_INPUT_NAME = "VeyraNoise";
    private static final List<String> SUPPORTED_UNIFORMS = withIrisExtras(Set.of(IrisExtraUniforms.Kind.values()), List.of(
            "near", "far", "heldBlockLightValue", "heldBlockLightValue2", "heldItemId", "heldItemId2",
            "darknessLightFactor", "shadowFade", "velocity", "playerMood", "biome_precipitation", "atlasSize",
            "lightningBoltPosition", "viewWidth", "viewHeight", "aspectRatio", "frameTimeCounter", "frameTime", "frameCounter",
            "sunAngle", "rainStrength", "wetness", "thunderStrength", "worldTime", "worldDay",
            "moonPhase", "bedrockLevel", "cloudHeight", "endFlashIntensity",
            "eyeAltitude", "eyeBrightness", "eyeBrightnessSmooth", "screenBrightness", "blindness", "nightVision", "darknessFactor", "isEyeInWater",
            "isSpectator", "is_sneaking", "is_sprinting", "is_on_ground", "is_hurt", "is_invisible", "is_burning",
            "gbufferModelView", "gbufferModelViewInverse", "gbufferProjection", "gbufferProjectionInverse",
            "gbufferPreviousModelView", "gbufferPreviousProjection", "shadowModelView", "shadowModelViewInverse",
            "shadowProjection", "shadowProjectionInverse", "cameraPosition", "cameraPositionInt",
            "cameraPositionFract", "previousCameraPosition", "previousCameraPositionInt", "previousCameraPositionFract",
            "eyePosition", "relativeEyePosition", "playerLookVector", "skyColor"));
    private static final List<String> MATRIX_UNIFORMS = List.of(
            "gbufferModelView", "gbufferModelViewInverse", "gbufferProjection", "gbufferProjectionInverse",
            "gbufferPreviousModelView", "gbufferPreviousProjection", "shadowModelView", "shadowModelViewInverse",
            "shadowProjection", "shadowProjectionInverse");
    private static final List<String> VECTOR3_UNIFORMS = withIrisExtras(Set.of(IrisExtraUniforms.Kind.VEC3), List.of(
            "cameraPosition", "cameraPositionFract", "previousCameraPosition", "previousCameraPositionFract",
            "eyePosition", "relativeEyePosition", "playerLookVector", "skyColor"));
    private static final List<String> VECTOR4_UNIFORMS = List.of("lightningBoltPosition");
    private static final List<String> IVECTOR2_UNIFORMS = List.of("eyeBrightness", "eyeBrightnessSmooth", "atlasSize");
    private static final List<String> IVECTOR3_UNIFORMS = List.of("cameraPositionInt", "previousCameraPositionInt");
    private static final List<String> BOOLEAN_UNIFORMS = withIrisExtras(Set.of(IrisExtraUniforms.Kind.BOOL), List.of(
            "isSpectator", "is_sneaking", "is_sprinting", "is_on_ground", "is_hurt", "is_invisible", "is_burning"));
    private static final List<String> INTEGER_UNIFORMS = withIrisExtras(
            Set.of(IrisExtraUniforms.Kind.INT, IrisExtraUniforms.Kind.BOOL), List.of(
            "heldBlockLightValue", "heldBlockLightValue2", "heldItemId", "heldItemId2", "biome_precipitation",
            "frameCounter", "worldTime", "worldDay", "moonPhase", "bedrockLevel", "isEyeInWater", "isSpectator", "is_sneaking",
            "is_sprinting", "is_on_ground", "is_hurt", "is_invisible", "is_burning"));
    private static final List<String> DYNAMIC_UNIFORMS = withIrisExtras(Set.of(IrisExtraUniforms.Kind.values()), List.of(
            "far", "heldBlockLightValue", "heldBlockLightValue2", "heldItemId", "heldItemId2", "darknessLightFactor",
            "playerMood", "biome_precipitation", "playerLookVector", "skyColor", "atlasSize", "lightningBoltPosition",
            "frameTimeCounter", "frameTime", "frameCounter", "sunAngle", "shadowFade", "velocity", "rainStrength", "wetness", "thunderStrength",
            "worldTime", "worldDay", "moonPhase", "bedrockLevel", "cloudHeight", "endFlashIntensity", "eyeAltitude", "eyeBrightness", "eyeBrightnessSmooth", "screenBrightness", "blindness", "nightVision", "darknessFactor",
            "isEyeInWater", "isSpectator",
            "is_sneaking", "is_sprinting", "is_on_ground", "is_hurt", "is_invisible", "is_burning",
            "gbufferModelView", "gbufferModelViewInverse", "gbufferProjection", "gbufferProjectionInverse",
            "gbufferPreviousModelView", "gbufferPreviousProjection", "shadowModelView", "shadowModelViewInverse",
            "shadowProjection", "shadowProjectionInverse", "cameraPosition", "cameraPositionInt",
            "cameraPositionFract", "previousCameraPosition", "previousCameraPositionInt", "previousCameraPositionFract",
            "eyePosition", "relativeEyePosition"));
    /** Post declarations of Iris extras; the generated uniform block provides them. */
    private static final Pattern IRIS_EXTRA_UNIFORM_DECLARATION = Pattern.compile(
            "(?m)^[\\t ]*uniform\\s+(?:(?:lowp|mediump|highp)\\s+)?(?:float|int|bool|vec3)\\s+(?:"
                    + String.join("|", IrisExtraUniforms.definitions().stream().map(IrisExtraUniforms.Definition::name).toList())
                    + ")\\s*;[^\\r\\n]*(?:\\r?\\n|$)");
    private static final Map<String, PipelineProgram> PIPELINE_CACHE = new HashMap<>();
    private static final Set<PipelineProgram> COMPILED_PIPELINES = Collections.newSetFromMap(new IdentityHashMap<>());
    private static final Map<EntityPipelineKey, RenderPipeline> ENTITY_PIPELINE_CACHE = new HashMap<>();
    private static final Map<RenderType, Map<RenderPipeline, RenderType>> ROUTED_RENDER_TYPE_CACHE = new IdentityHashMap<>();
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
    private static final Map<Integer, GeometryColorBinding> geometryColorBindings = new HashMap<>();
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
    private static final VeyraColorTextureSnapshot cloudSceneColorSnapshot =
            new VeyraColorTextureSnapshot("cloud colortex0 scene snapshot");
    private static boolean cloudPipelineUsesSceneColor;
    private static boolean cloudSceneColorCaptured;
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
    private static final ThreadLocal<Deque<Boolean>> HAND_SIDE_SUBMISSION_CONTEXT = ThreadLocal.withInitial(ArrayDeque::new);
    private static final ThreadLocal<Integer> BLOCK_ENTITY_SUBMISSION_DEPTH = ThreadLocal.withInitial(() -> 0);
    private static final ThreadLocal<Deque<Integer>> ENTITY_IDENTIFIER_CONTEXT = ThreadLocal.withInitial(ArrayDeque::new);
    private static final ThreadLocal<Deque<Integer>> BLOCK_ENTITY_IDENTIFIER_CONTEXT = ThreadLocal.withInitial(ArrayDeque::new);
    private static final ThreadLocal<Deque<Integer>> RENDERED_ITEM_IDENTIFIER_CONTEXT = ThreadLocal.withInitial(ArrayDeque::new);
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
    private static final EyeBrightnessSmoother eyeBrightnessSmoother = new EyeBrightnessSmoother();
    private static ClientLevel eyeBrightnessSmoothLevel;
    private static ShaderPack eyeBrightnessHalfLifePack;
    private static float eyeBrightnessHalfLifeTicks = EyeBrightnessSmoother.DEFAULT_HALF_LIFE_TICKS;

    private VeyraRenderer() {
    }

    public static void initialize(ShaderPackRepository packRepository) {
        repository = packRepository;
        registerShaderPackUniforms();
        BlockIdMap previousBlockIds = BlockIdMap.active();
        activateCustomUniforms(packRepository.getSelected().orElse(null));
        boolean blockMappingsChanged = !previousBlockIds.sameMappings(BlockIdMap.active());
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
                .filter(ShaderPack::supportsShaderPrograms)
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
        boolean terrainRebuilt = updateTerrainLayerMode(packRepository.getSelected().orElse(null));
        if (blockMappingsChanged && !terrainRebuilt) rebuildTerrainSections();
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
            } else if (uniform.sourceType().equals("vec4")) {
                registerShaderPackVector4Uniform(uniform.name());
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

    private static void activateCustomUniforms(ShaderPack pack) {
        CustomUniforms uniforms = CustomUniforms.EMPTY;
        if (pack != null) {
            try {
                ItemIdMap itemIds = pack.readItemIds();
                EntityIdMap entityIds = pack.readEntityIds();
                BlockIdMap blockIds = pack.readBlockIds();
                uniforms = pack.readCustomUniforms();
                ItemIdMap.activate(itemIds);
                EntityIdMap.activate(entityIds);
                BlockIdMap.activate(blockIds);
            } catch (IOException exception) {
                VeyraClient.LOGGER.warn("Could not read custom uniforms for '{}'.", pack.name(), exception);
                ItemIdMap.activate(ItemIdMap.EMPTY);
                EntityIdMap.activate(EntityIdMap.EMPTY);
                BlockIdMap.activate(BlockIdMap.EMPTY);
            }
        } else {
            ItemIdMap.activate(ItemIdMap.EMPTY);
            EntityIdMap.activate(EntityIdMap.EMPTY);
            BlockIdMap.activate(BlockIdMap.EMPTY);
        }
        CustomUniforms.activate(uniforms);
        registerCustomUniforms();
    }

    /** VulkanMod resolves uniform block members by name; custom names read the latest evaluated value. */
    private static void registerCustomUniforms() {
        // Both activation paths end here; report definitions that stay 0 the way Iris warns about them.
        CustomUniforms.active().irisUnresolved().forEach((name, reason) -> VeyraClient.LOGGER.warn(
                "Custom uniform '{}' stays 0 because Iris cannot resolve it either: {}.", name, reason));
        for (CustomUniforms.Uniform uniform : CustomUniforms.active().uniforms()) {
            String name = uniform.name();
            if (CustomUniforms.PACK_OVERRIDABLE.contains(name)) {
                // The built-in supplier resolves the pack override itself and must survive pack switches.
                continue;
            }
            if (uniform.type() == CustomUniforms.Type.FLOAT) {
                Uniforms.vec1f_uniformMap.put(name, () -> CustomUniforms.active().floatValue(name));
            } else {
                Uniforms.vec1i_uniformMap.put(name, () -> CustomUniforms.active().intValue(name));
                if (uniform.type() == CustomUniforms.Type.BOOL) {
                    Uniforms.vec1i_uniformMap.put("veyra_" + name,
                            () -> CustomUniforms.active().intValue(name));
                }
            }
        }
    }

    private static Double customUniformInput(String name, FrameUniforms frame) {
        Minecraft minecraft = Minecraft.getInstance();
        CameraUniforms camera = frame.camera();
        return switch (name) {
            case "biome" -> (double) currentBiomeId(minecraft);
            case "near" -> (double) IRIS_NEAR_PLANE;
            case "far" -> (double) farPlane();
            case "darknessLightFactor" -> (double) darknessLightFactor;
            case "shadowFade" -> (double) frame.shadowFade();
            case "velocity" -> (double) cameraVelocity(camera);
            case "playerMood" -> (double) playerMood();
            case "biome_precipitation" -> (double) biomePrecipitation();
            case "heldBlockLightValue" -> (double) heldBlockLightValue(net.minecraft.world.InteractionHand.MAIN_HAND);
            case "heldBlockLightValue2" -> (double) heldBlockLightValue(net.minecraft.world.InteractionHand.OFF_HAND);
            case "heldItemId" -> (double) heldItemId(net.minecraft.world.InteractionHand.MAIN_HAND);
            case "heldItemId2" -> (double) heldItemId(net.minecraft.world.InteractionHand.OFF_HAND);
            case "viewWidth" -> (double) minecraft.getWindow().getWidth();
            case "viewHeight" -> (double) minecraft.getWindow().getHeight();
            case "aspectRatio" -> minecraft.getWindow().getHeight() == 0 ? 0.0
                    : (double) minecraft.getWindow().getWidth() / minecraft.getWindow().getHeight();
            case "frameTimeCounter" -> (double) frame.frameTimeCounter();
            case "frameTime" -> (double) frame.frameTime();
            case "frameCounter" -> (double) frame.frameCounter();
            case "sunAngle" -> (double) frame.sunAngle();
            case "rainStrength" -> (double) frame.rainStrength();
            case "wetness" -> (double) frame.wetness();
            case "thunderStrength" -> (double) frame.thunderStrength();
            case "worldTime" -> (double) frame.worldTime();
            case "worldDay" -> (double) frame.worldDay();
            case "moonPhase" -> (double) frame.moonPhase();
            case "bedrockLevel" -> (double) frame.bedrockLevel();
            case "cloudHeight" -> (double) frame.cloudHeight();
            case "endFlashIntensity" -> (double) frame.endFlashIntensity();
            case "eyeAltitude" -> (double) frame.eyeAltitude();
            case "screenBrightness" -> (double) frame.screenBrightness();
            case "blindness" -> (double) frame.blindness();
            case "nightVision" -> (double) frame.nightVision();
            case "darknessFactor" -> (double) frame.darknessFactor();
            case "isEyeInWater" -> (double) frame.isEyeInWater();
            case "isSpectator" -> frame.isSpectator() ? 1.0 : 0.0;
            case "is_sneaking" -> frame.isSneaking() ? 1.0 : 0.0;
            case "is_sprinting" -> frame.isSprinting() ? 1.0 : 0.0;
            case "is_on_ground" -> frame.isOnGround() ? 1.0 : 0.0;
            case "is_hurt" -> frame.isHurt() ? 1.0 : 0.0;
            case "is_invisible" -> frame.isInvisible() ? 1.0 : 0.0;
            case "is_burning" -> frame.isBurning() ? 1.0 : 0.0;
            case "cameraPosition.x" -> (double) camera.cameraPosition().x;
            case "cameraPosition.y" -> (double) camera.cameraPosition().y;
            case "cameraPosition.z" -> (double) camera.cameraPosition().z;
            case "previousCameraPosition.x" -> (double) camera.previousCameraPosition().x;
            case "previousCameraPosition.y" -> (double) camera.previousCameraPosition().y;
            case "previousCameraPosition.z" -> (double) camera.previousCameraPosition().z;
            case "eyeBrightness.x" -> (double) camera.eyeBrightness().x;
            case "eyeBrightness.y" -> (double) camera.eyeBrightness().y;
            case "eyeBrightnessSmooth.x" -> (double) camera.eyeBrightnessSmooth().x;
            case "eyeBrightnessSmooth.y" -> (double) camera.eyeBrightnessSmooth().y;
            default -> IrisExtraUniforms.expressionInput(name);
        };
    }

    private static final Map<String, MappedBuffer> SHADER_PACK_VECTOR4_BUFFERS = new HashMap<>();

    private static void registerShaderPackVector4Uniform(String name) {
        MappedBuffer buffer = new MappedBuffer(4 * Float.BYTES);
        SHADER_PACK_VECTOR4_BUFFERS.put(name, buffer);
        Uniforms.vec4f_uniformMap.put(name, () -> buffer);
    }

    private static void updateShaderPackVector4Uniforms(Vector4f lightningBolt) {
        SHADER_PACK_VECTOR4_BUFFERS.forEach((name, buffer) -> {
            Vector4f value = switch (name) {
                case "lightningBoltPosition" -> lightningBolt;
                default -> throw new IllegalArgumentException("Unknown shader pack vec4 uniform " + name + ".");
            };
            buffer.putFloat(0, value.x);
            buffer.putFloat(Float.BYTES, value.y);
            buffer.putFloat(Float.BYTES * 2, value.z);
            buffer.putFloat(Float.BYTES * 3, value.w);
        });
    }

    /** Iris: a rendered lightning bolt's position relative to the camera with w = 1, otherwise zero. */
    private static Vector4f lightningBoltPosition(Minecraft minecraft, Vec3 cameraPosition) {
        if (minecraft.level == null) {
            return new Vector4f();
        }
        float partialTick = minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(true);
        for (net.minecraft.world.entity.Entity entity : minecraft.level.entitiesForRendering()) {
            if (entity instanceof net.minecraft.world.entity.LightningBolt bolt) {
                Vec3 position = bolt.getPosition(partialTick);
                return new Vector4f((float) (position.x - cameraPosition.x), (float) (position.y - cameraPosition.y),
                        (float) (position.z - cameraPosition.z), 1.0F);
            }
        }
        return new Vector4f();
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
            case "eyePosition" -> uniforms.eyePosition();
            case "relativeEyePosition" -> uniforms.relativeEyePosition();
            case "playerLookVector" -> uniforms.playerLookVector();
            case "skyColor" -> uniforms.skyColor();
            default -> {
                if (!IrisExtraUniforms.contains(name)) {
                    throw new IllegalArgumentException("Unknown shader pack vector uniform " + name + ".");
                }
                float[] extra = IrisExtraUniforms.vector(name);
                yield new Vector3f(extra[0], extra[1], extra[2]);
            }
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
        Vector2i value = switch (name) {
            case "eyeBrightness" -> uniforms.eyeBrightness();
            case "eyeBrightnessSmooth" -> uniforms.eyeBrightnessSmooth();
            case "atlasSize" -> new Vector2i(blockAtlasWidth, blockAtlasHeight);
            default -> throw new IllegalArgumentException("Unknown shader pack integer vector uniform " + name + ".");
        };
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

    private static float shadowFadeUniform(FrameUniforms frame) {
        return packOverridableFloat("shadowFade", frame.shadowFade());
    }

    /** Iris hardcoded custom uniforms yield to a shaders.properties definition with the same name. */
    private static float packOverridableFloat(String name, float builtInValue) {
        CustomUniforms customUniforms = CustomUniforms.active();
        return customUniforms.uniform(name) == null ? builtInValue : customUniforms.floatValue(name);
    }

    /**
     * Iris' hardcoded velocity: the distance in blocks the camera moved since the previous frame.
     * Integer and fractional parts are subtracted separately to keep precision far from the origin.
     */
    private static float cameraVelocity(CameraUniforms camera) {
        double dx = (camera.cameraPositionInt().x - camera.previousCameraPositionInt().x)
                + (double) (camera.cameraPositionFract().x - camera.previousCameraPositionFract().x);
        double dy = (camera.cameraPositionInt().y - camera.previousCameraPositionInt().y)
                + (double) (camera.cameraPositionFract().y - camera.previousCameraPositionFract().y);
        double dz = (camera.cameraPositionInt().z - camera.previousCameraPositionInt().z)
                + (double) (camera.cameraPositionFract().z - camera.previousCameraPositionFract().z);
        return (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private static float shaderPackFloatUniform(String name) {
        if (IrisExtraUniforms.contains(name)) {
            return (float) irisExtraScalar(name);
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (name.equals("near")) {
            return IRIS_NEAR_PLANE;
        }
        if (name.equals("far")) {
            return farPlane();
        }
        if (name.equals("darknessLightFactor")) {
            return darknessLightFactor;
        }
        if (name.equals("playerMood")) {
            return playerMood();
        }
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
            case "shadowFade" -> shadowFadeUniform(frame);
            case "velocity" -> packOverridableFloat("velocity", cameraVelocity(frame.camera()));
            case "rainStrength" -> frame.rainStrength();
            case "wetness" -> frame.wetness();
            case "thunderStrength" -> frame.thunderStrength();
            case "cloudHeight" -> frame.cloudHeight();
            case "endFlashIntensity" -> frame.endFlashIntensity();
            case "eyeAltitude" -> frame.eyeAltitude();
            case "screenBrightness" -> frame.screenBrightness();
            case "blindness" -> frame.blindness();
            case "nightVision" -> frame.nightVision();
            case "darknessFactor" -> frame.darknessFactor();
            default -> throw new IllegalArgumentException("Unknown shader pack float uniform " + name + ".");
        };
    }

    private static int shaderPackIntUniform(String name) {
        if (IrisExtraUniforms.contains(name)) {
            return (int) irisExtraScalar(name);
        }
        if (name.equals("heldBlockLightValue")) {
            return heldBlockLightValue(net.minecraft.world.InteractionHand.MAIN_HAND);
        }
        if (name.equals("heldBlockLightValue2")) {
            return heldBlockLightValue(net.minecraft.world.InteractionHand.OFF_HAND);
        }
        if (name.equals("biome_precipitation")) {
            return biomePrecipitation();
        }
        if (name.equals("heldItemId")) {
            return heldItemId(net.minecraft.world.InteractionHand.MAIN_HAND);
        }
        if (name.equals("heldItemId2")) {
            return heldItemId(net.minecraft.world.InteractionHand.OFF_HAND);
        }
        FrameUniforms frame = shaderPackFrameUniforms;
        return switch (name) {
            case "frameCounter" -> frame.frameCounter();
            case "worldTime" -> frame.worldTime();
            case "worldDay" -> frame.worldDay();
            case "moonPhase" -> frame.moonPhase();
            case "bedrockLevel" -> frame.bedrockLevel();
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

    /** Iris: registration-order id of the biome at the player's block position. */
    private static int currentBiomeId(Minecraft minecraft) {
        if (minecraft.player == null || minecraft.level == null) {
            return 0;
        }
        return minecraft.level.getBiome(minecraft.player.blockPosition()).unwrapKey()
                .map(key -> BiomeIds.id(key.identifier().toString())).orElse(0);
    }

    private static volatile float darknessLightFactor;
    private static volatile int blockAtlasWidth;
    private static volatile int blockAtlasHeight;

    /** Iris' atlasSize is the size of the pipeline albedo texture, the block atlas. */
    public static void captureBlockAtlasSize(int width, int height) {
        blockAtlasWidth = width;
        blockAtlasHeight = height;
    }

    /** Called from the lightmap update with Iris' darknessLightFactor. */
    public static void captureDarknessLightFactor(float value) {
        darknessLightFactor = value;
    }

    /** Iris: the local player's cave-sound mood in [0, 1]. */
    private static float playerMood() {
        if (!(Minecraft.getInstance().getCameraEntity() instanceof net.minecraft.client.player.LocalPlayer player)) {
            return 0.0F;
        }
        return Math.clamp(player.getCurrentMood(), 0.0F, 1.0F);
    }

    /** Iris: 0 none, 1 rain, 2 snow for the biome at the player's position. */
    private static int biomePrecipitation() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) {
            return 0;
        }
        net.minecraft.core.BlockPos position = minecraft.player.blockPosition();
        return switch (minecraft.level.getBiome(position).value().getPrecipitationAt(position, minecraft.level.getSeaLevel())) {
            case NONE -> 0;
            case RAIN -> 1;
            case SNOW -> 2;
        };
    }

    private static List<String> withIrisExtras(Set<IrisExtraUniforms.Kind> kinds, List<String> builtIns) {
        List<String> names = new ArrayList<>(builtIns);
        for (IrisExtraUniforms.Definition definition : IrisExtraUniforms.definitions()) {
            if (kinds.contains(definition.kind()) && !names.contains(definition.name())) {
                names.add(definition.name());
            }
        }
        return List.copyOf(names);
    }

    /** Iris extras; hardcoded custom uniforms yield to a shaders.properties definition of the same name. */
    private static double irisExtraScalar(String name) {
        CustomUniforms customUniforms = CustomUniforms.active();
        if (IrisExtraUniforms.HARDCODED_CUSTOM.contains(name) && customUniforms.uniform(name) != null) {
            return customUniforms.floatValue(name);
        }
        return IrisExtraUniforms.scalar(name);
    }

    private static UniformValue irisExtraUniformValue(String name) {
        IrisExtraUniforms.Definition definition = IrisExtraUniforms.definition(name);
        return switch (definition.kind()) {
            case FLOAT -> new UniformValue.FloatUniform((float) irisExtraScalar(name));
            case INT, BOOL -> new UniformValue.IntUniform((int) irisExtraScalar(name));
            case VEC3 -> {
                float[] vector = IrisExtraUniforms.vector(name);
                yield new UniformValue.Vec3Uniform(new Vector3f(vector[0], vector[1], vector[2]));
            }
        };
    }

    /** Iris WorldTimeUniforms: fixed-time dimensions other than the Nether and End report 0. */
    private static int irisWorldTime(ClientLevel level, long dayTime) {
        boolean netherOrEnd = level.dimension().equals(net.minecraft.world.level.Level.NETHER)
                || level.dimension().equals(net.minecraft.world.level.Level.END);
        return irisWorldTime(dayTime, level.dimensionType().hasFixedTime(), netherOrEnd);
    }

    static int irisWorldTime(long dayTime, boolean fixedTime, boolean netherOrEnd) {
        if (!netherOrEnd && fixedTime) {
            return 0;
        }
        return (int) (dayTime % 24000L);
    }

    /** Iris CelestialUniforms.getSunAngle: the attribute angle plus 90 degrees, wrapped to [0, 360]. */
    private static float irisCelestialAngle(float attributeAngle) {
        float angle = attributeAngle + 90.0F;
        if (angle < 0.0F) {
            angle += 360.0F;
        } else if (angle > 360.0F) {
            angle -= 360.0F;
        }
        return angle;
    }

    // Iris HardcodedCustomUniforms smoothing state; like Iris' per-pipeline SmoothedFloats it restarts
    // with the world and with each shader pack selection.
    private static final IrisSmoothedFloat IRIS_EYE_IN_CAVE = new IrisSmoothedFloat(6, 12);
    private static final IrisSmoothedFloat IRIS_RAIN_STRENGTH_S = new IrisSmoothedFloat(15, 15);
    private static final IrisSmoothedFloat IRIS_RAIN_STRENGTH_SHINING = new IrisSmoothedFloat(10, 11);
    private static final IrisSmoothedFloat IRIS_RAIN_STRENGTH_S2 = new IrisSmoothedFloat(70, 1);
    private static final IrisSmoothedFloat IRIS_DRY = new IrisSmoothedFloat(20, 10);
    private static final IrisSmoothedFloat IRIS_RAINY = new IrisSmoothedFloat(20, 10);
    private static final IrisSmoothedFloat IRIS_SNOWY = new IrisSmoothedFloat(20, 10);
    private static final IrisSmoothedFloat IRIS_MOVED = new IrisSmoothedFloat(0, 31536000);
    private static final IrisSmoothedFloat IRIS_STARTER = new IrisSmoothedFloat(20, 20);
    private static final IrisSmoothedFloat IRIS_FRAME_TIME_SMOOTH = new IrisSmoothedFloat(5, 5);
    private static final IrisSmoothedFloat IRIS_EYE_BRIGHTNESS_M = new IrisSmoothedFloat(5, 5);
    private static final IrisSmoothedFloat IRIS_PRECIPITATION_RAIN = new IrisSmoothedFloat(6, 6);
    private static final IrisSmoothedFloat IRIS_TOUCH_MY_BODY = new IrisSmoothedFloat(0, 0.1);
    private static final IrisSmoothedFloat IRIS_SNEAK_SMOOTH = new IrisSmoothedFloat(2, 0.9);
    private static final IrisSmoothedFloat IRIS_BURNING_SMOOTH = new IrisSmoothedFloat(1, 2);
    private static final IrisSmoothedFloat IRIS_SMOOTH_SPEED = new IrisSmoothedFloat(1, 1.5);
    private static ShaderPack endFlashShadowsPack;
    private static boolean endFlashShadows;

    private static void resetIrisExtraUniformState() {
        for (IrisSmoothedFloat smoother : List.of(IRIS_EYE_IN_CAVE, IRIS_RAIN_STRENGTH_S, IRIS_RAIN_STRENGTH_SHINING,
                IRIS_RAIN_STRENGTH_S2, IRIS_DRY, IRIS_RAINY, IRIS_SNOWY, IRIS_MOVED, IRIS_STARTER,
                IRIS_FRAME_TIME_SMOOTH, IRIS_EYE_BRIGHTNESS_M, IRIS_PRECIPITATION_RAIN, IRIS_TOUCH_MY_BODY,
                IRIS_SNEAK_SMOOTH, IRIS_BURNING_SMOOTH, IRIS_SMOOTH_SPEED)) {
            smoother.reset();
        }
    }

    private static boolean selectedEndFlashShadows() {
        ShaderPack pack = selectedPack().orElse(null);
        if (pack != endFlashShadowsPack) {
            endFlashShadowsPack = pack;
            try {
                endFlashShadows = pack != null && pack.readEndFlashShadows();
            } catch (IOException exception) {
                endFlashShadows = false;
                VeyraClient.LOGGER.warn("Could not read the endFlashShadows directive; using Iris' default.", exception);
            }
        }
        return endFlashShadows;
    }

    /**
     * Evaluates the Iris 1.21.11 uniforms in {@link IrisExtraUniforms} once per frame, following
     * IrisExclusiveUniforms, CommonUniforms, CelestialUniforms and HardcodedCustomUniforms.
     */
    private static void updateIrisExtraUniforms(Minecraft minecraft, FrameUniforms frame, Vec3 cameraPosition) {
        ClientLevel level = minecraft.level;
        var player = minecraft.player;
        net.minecraft.world.entity.Entity cameraEntity = minecraft.getCameraEntity();
        float partialTick = minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        float frameSeconds = frame.frameTime();
        Map<String, Double> scalars = new HashMap<>();
        Map<String, float[]> vectors = new HashMap<>();

        var dimension = level.dimensionType();
        scalars.put("heavyFog", minecraft.gui.getBossOverlay().shouldCreateWorldFog() ? 1.0 : 0.0);
        scalars.put("heightLimit", (double) dimension.height());
        scalars.put("logicalHeightLimit", (double) dimension.logicalHeight());
        scalars.put("hasCeiling", dimension.hasCeiling() ? 1.0 : 0.0);
        scalars.put("hasSkylight", dimension.hasSkyLight() ? 1.0 : 0.0);
        scalars.put("ambientLight", (double) dimension.ambientLight());
        scalars.put("seaLevel", (double) level.getSeaLevel());
        scalars.put("isElytraFlying", player.isFallFlying() ? 1.0 : 0.0);
        scalars.put("feetInWater", player.isInShallowWater() ? 1.0 : 0.0);
        scalars.put("inSwimmingAnimation", player.isSwimming() ? 1.0 : 0.0);
        scalars.put("isRiding", player.isPassenger() ? 1.0 : 0.0);
        net.minecraft.world.entity.Entity vehicle = player.getVehicle();
        scalars.put("vehicleInWater", vehicle != null && vehicle.isInShallowWater() ? 1.0 : 0.0);
        int vehicleId = 0;
        if (vehicle != null && EntityIdMap.active().size() > 0) {
            var vehicleKey = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(vehicle.getType());
            vehicleId = EntityIdMap.active().id(vehicleKey.getNamespace() + ":" + vehicleKey.getPath());
        }
        scalars.put("vehicleId", (double) vehicleId);
        boolean survival = minecraft.gameMode != null && minecraft.gameMode.getPlayerMode().isSurvival();
        scalars.put("currentPlayerHealth", survival ? (double) (player.getHealth() / player.getMaxHealth()) : -1.0);
        scalars.put("maxPlayerHealth", survival ? (double) player.getMaxHealth() : -1.0);
        scalars.put("currentPlayerHunger", survival ? player.getFoodData().getFoodLevel() / 20.0 : -1.0);
        scalars.put("maxPlayerHunger", 20.0);
        scalars.put("currentPlayerArmor", survival ? player.getArmorValue() / 50.0 : -1.0);
        scalars.put("maxPlayerArmor", 50.0);
        scalars.put("currentPlayerAir", survival
                ? (double) ((float) player.getAirSupply() / (float) player.getMaxAirSupply()) : -1.0);
        scalars.put("maxPlayerAir", survival ? (double) player.getMaxAirSupply() : -1.0);
        var cameraType = minecraft.options.getCameraType();
        scalars.put("firstPersonCamera", cameraType == net.minecraft.client.CameraType.THIRD_PERSON_BACK
                || cameraType == net.minecraft.client.CameraType.THIRD_PERSON_FRONT ? 0.0 : 1.0);
        // Veyra outputs sRGB, Iris' default colour space (ordinal 0).
        scalars.put("currentColorSpace", 0.0);
        scalars.put("chunkFadeTimeInv", 1.0 / (minecraft.options.chunkSectionFadeInTime().get() * 1000.0));
        var filtering = minecraft.options.textureFiltering().get();
        scalars.put("textureFilteringMode", switch (filtering) {
            case NONE -> 0.0;
            case RGSS -> 1.0;
            case ANISOTROPIC -> 2.0;
        });
        scalars.put("anisotropicFiltering", filtering == net.minecraft.client.TextureFilteringMethod.ANISOTROPIC
                ? (double) minecraft.options.maxAnisotropyValue() : 0.0);
        scalars.put("hideGUI", minecraft.options.hideGui ? 1.0 : 0.0);
        scalars.put("isRightHanded", minecraft.options.mainHand().get() == net.minecraft.world.entity.HumanoidArm.RIGHT
                ? 1.0 : 0.0);
        scalars.put("pi", Math.PI);
        // Distant Horizons is not available with VulkanMod; these are Iris' values without it.
        scalars.put("dhFarPlane", 0.01);
        scalars.put("dhNearPlane", 0.01);
        scalars.put("dhRenderDistance", (double) minecraft.options.getEffectiveRenderDistance());

        // Celestial bodies in view space, as CelestialUniforms builds them from gbufferModelView.
        float sunAttribute = minecraft.gameRenderer.getMainCamera().attributeProbe()
                .getValue(EnvironmentAttributes.SUN_ANGLE, partialTick);
        float moonAttribute = minecraft.gameRenderer.getMainCamera().attributeProbe()
                .getValue(EnvironmentAttributes.MOON_ANGLE, partialTick);
        boolean day = irisCelestialAngle(sunAttribute) < 180.0F;
        scalars.put("shadowAngle", (double) (irisCelestialAngle(day ? sunAttribute : moonAttribute) / 360.0F));
        Matrix4f modelView = frame.matrices().gbufferModelView();
        float sunPathRotation = VeyraShadowRenderer.sunPathRotation();
        float[] sunPosition = celestialPosition(modelView, sunPathRotation, sunAttribute);
        float[] moonPosition = celestialPosition(modelView, sunPathRotation, moonAttribute);
        vectors.put("sunPosition", sunPosition);
        vectors.put("moonPosition", moonPosition);
        org.joml.Vector4f up = new Matrix4f(modelView)
                .rotate(com.mojang.math.Axis.YP.rotationDegrees(-90.0F))
                .transform(new org.joml.Vector4f(0.0F, 100.0F, 0.0F, 0.0F));
        vectors.put("upPosition", new float[]{up.x, up.y, up.z});
        boolean end = level.dimension().equals(net.minecraft.world.level.Level.END);
        float[] endFlashPosition = new float[3];
        if (end && level.endFlashState() != null) {
            var flash = level.endFlashState();
            org.joml.Vector4f position = new Matrix4f(modelView)
                    .rotate(com.mojang.math.Axis.YP.rotationDegrees(180.0F - flash.getYAngle()))
                    .rotate(com.mojang.math.Axis.XP.rotationDegrees(-90.0F - flash.getXAngle()))
                    .transform(new org.joml.Vector4f(0.0F, 100.0F, 0.0F, 0.0F));
            endFlashPosition = new float[]{position.x, position.y, position.z};
        }
        vectors.put("endFlashPosition", endFlashPosition);
        vectors.put("shadowLightPosition", end && selectedEndFlashShadows() ? endFlashPosition
                : day ? sunPosition : moonPosition);
        Vec3 forward = cameraEntity != null ? cameraEntity.getForward() : Vec3.ZERO;
        vectors.put("playerBodyVector", new float[]{(float) forward.x, (float) forward.y, (float) forward.z});
        if (vehicle != null) {
            Vec3 vehicleForward = vehicle.getForward();
            Vec3 vehiclePosition = vehicle.getPosition(partialTick);
            vectors.put("vehicleLookVector", new float[]{(float) vehicleForward.x, (float) vehicleForward.y,
                    (float) vehicleForward.z});
            vectors.put("relativeVehiclePosition", new float[]{(float) (cameraPosition.x - vehiclePosition.x),
                    (float) (cameraPosition.y - vehiclePosition.y), (float) (cameraPosition.z - vehiclePosition.z)});
        }

        // HardcodedCustomUniforms.
        int hardcodedDayTime = frame.worldTime();
        double timeAngle = hardcodedDayTime / 24000.0;
        scalars.put("timeAngle", timeAngle);
        scalars.put("timeBrightness", Math.max(Math.sin(timeAngle * Math.PI * 2.0), 0.0));
        scalars.put("moonBrightness", Math.max(Math.sin(timeAngle * Math.PI * -2.0), 0.0));
        double rainStrength = frame.rainStrength();
        double rainStrengthS = IRIS_RAIN_STRENGTH_S.update(rainStrength, frameSeconds);
        scalars.put("rainStrengthS", rainStrengthS);
        scalars.put("rainFactor", rainStrengthS);
        scalars.put("rainStrengthShiningStars", IRIS_RAIN_STRENGTH_SHINING.update(rainStrength, frameSeconds));
        scalars.put("rainStrengthS2", IRIS_RAIN_STRENGTH_S2.update(rainStrength, frameSeconds));
        double blindFactorSqrt = Math.max(0.0, Math.min(1.0, frame.blindness() * 2.0 - 1.0));
        scalars.put("blindFactor", blindFactorSqrt * blindFactorSqrt);
        net.minecraft.core.BlockPos biomePosition = cameraEntity != null ? cameraEntity.blockPosition()
                : player.blockPosition();
        var biome = level.getBiome(biomePosition).value();
        int precipitation = switch (biome.getPrecipitationAt(biomePosition, level.getSeaLevel())) {
            case RAIN -> 1;
            case SNOW -> 2;
            default -> 0;
        };
        scalars.put("BiomeTemp", (double) biome.getBaseTemperature());
        scalars.put("isDry", IRIS_DRY.update(precipitation == 0 ? 1.0 : 0.0, frameSeconds));
        scalars.put("isRainy", IRIS_RAINY.update(precipitation == 1 ? 1.0 : 0.0, frameSeconds));
        scalars.put("isSnowy", IRIS_SNOWY.update(precipitation == 2 ? 1.0 : 0.0, frameSeconds));
        double eyeSkyBrightness = 0.0;
        double eyeY = 0.0;
        if (cameraEntity != null) {
            Vec3 feet = cameraEntity.position();
            eyeY = cameraEntity.getEyeY();
            eyeSkyBrightness = level.getBrightness(net.minecraft.world.level.LightLayer.SKY,
                    net.minecraft.core.BlockPos.containing(feet.x, eyeY, feet.z)) * 16.0;
        }
        double eyeInCave = IRIS_EYE_IN_CAVE.update(eyeY < 5.0 ? 1.0 - eyeSkyBrightness / 240.0 : 0.0, frameSeconds);
        scalars.put("isEyeInCave", frame.isEyeInWater() == 0 ? eyeInCave : 0.0);
        scalars.put("eyeBrightnessM", IRIS_EYE_BRIGHTNESS_M.update(eyeSkyBrightness / 240.0, frameSeconds));
        scalars.put("frameTimeSmooth", IRIS_FRAME_TIME_SMOOTH.update(frameSeconds, frameSeconds));
        CameraUniforms camera = frame.camera();
        double movedX = camera.cameraPosition().x - camera.previousCameraPosition().x;
        double movedY = camera.cameraPosition().y - camera.previousCameraPosition().y;
        double movedZ = camera.cameraPosition().z - camera.previousCameraPosition().z;
        double movedSum = Math.abs(movedX) + Math.abs(movedY) + Math.abs(movedZ);
        double moved = IRIS_MOVED.update(movedSum > 0.0 && movedSum < 1.0 ? 1.0 : 0.0, frameSeconds);
        scalars.put("starter", IRIS_STARTER.update(moved, frameSeconds));
        double adjustedTime = Math.abs(((frame.worldTime() / 1000.0F + 6.0F) % 24.0F) - 12.0F);
        double dayFactor = Math.max(0.0, Math.min(1.0, 5.4 - adjustedTime));
        double nightFactor = Math.max(0.0, Math.min(1.0, adjustedTime - 6.0));
        scalars.put("day", dayFactor);
        scalars.put("night", nightFactor);
        scalars.put("dawnDusk", 1.0 - dayFactor - nightFactor);
        // Iris feeds the sun angle in degrees here, so this stays at 0 for most of the day, as in Iris.
        scalars.put("shdFade", Math.max(0.0, Math.min(1.0,
                1.0 - (Math.abs(Math.abs(irisCelestialAngle(sunAttribute) - 0.5) - 0.25) - 0.225) * 40.0)));
        scalars.put("isPrecipitationRain", IRIS_PRECIPITATION_RAIN.update(
                precipitation == 1 && cameraPosition.y < 96.0 ? 1.0 : 0.0, frameSeconds));
        scalars.put("touchmybody", IRIS_TOUCH_MY_BODY.update(
                player.hurtTime > 0 || player.deathTime > 0 ? 0.4 : 0.0, frameSeconds));
        scalars.put("sneakSmooth", IRIS_SNEAK_SMOOTH.update(player.isCrouching() ? 1.0 : 0.0, frameSeconds));
        scalars.put("burningSmooth", IRIS_BURNING_SMOOTH.update(player.isOnFire() ? 1.0 : 0.0, frameSeconds));
        double speed = frameSeconds > 0.0F ? cameraVelocity(camera) / frameSeconds : 0.0;
        scalars.put("effectStrength", 1.0 - Math.exp(-IRIS_SMOOTH_SPEED.update(speed, frameSeconds) * 0.003906));
        IrisExtraUniforms.publish(scalars, vectors);
    }

    /** Iris CelestialUniforms.getCelestialPosition: (0, 100, 0, 1) through the sky rotation. */
    private static float[] celestialPosition(Matrix4f modelView, float sunPathRotation, float attributeAngle) {
        org.joml.Vector4f position = new Matrix4f(modelView)
                .rotate(com.mojang.math.Axis.YP.rotationDegrees(-90.0F))
                .rotate(com.mojang.math.Axis.ZP.rotationDegrees(sunPathRotation))
                .rotate(com.mojang.math.Axis.XP.rotationDegrees(attributeAngle))
                .transform(new org.joml.Vector4f(0.0F, 100.0F, 0.0F, 1.0F));
        return new float[]{position.x, position.y, position.z};
    }

    /** Iris reports a fixed 0.05 near plane. */
    private static final float IRIS_NEAR_PLANE = 0.05F;

    /** Iris' far uniform is the render distance in blocks. */
    private static float farPlane() {
        return Minecraft.getInstance().options.getEffectiveRenderDistance() * 16.0F;
    }

    /** Iris: the light emission of a held block item, otherwise 0. */
    private static int heldBlockLightValue(net.minecraft.world.InteractionHand hand) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return 0;
        }
        net.minecraft.world.item.ItemStack stack = minecraft.player.getItemInHand(hand);
        if (stack.getItem() instanceof net.minecraft.world.item.BlockItem blockItem) {
            return blockItem.getBlock().defaultBlockState().getLightEmission();
        }
        return 0;
    }

    /** Iris: item.properties id of the held item's model (or registry id), -1 when unmapped. */
    private static int heldItemId(net.minecraft.world.InteractionHand hand) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return ItemIdMap.UNMAPPED;
        }
        net.minecraft.world.item.ItemStack stack = minecraft.player.getItemInHand(hand);
        net.minecraft.resources.Identifier model = stack.get(net.minecraft.core.component.DataComponents.ITEM_MODEL);
        if (model == null) {
            model = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem());
        }
        return ItemIdMap.active().id(model.getNamespace() + ":" + model.getPath());
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
        resetIrisExtraUniformState();
        previousFrameNanos = 0L;
        frameTime = 0.0F;
        eyeBrightnessSmoothLevel = minecraft.level;
        eyeBrightnessSmoother.reset();
        initializedColorTargets.clear();
        closeOpaqueDepthSnapshot();
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
        updateFrameClock();
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
        Minecraft minecraft = Minecraft.getInstance();
        if (eyeBrightnessSmoothLevel != minecraft.level) {
            eyeBrightnessSmoothLevel = minecraft.level;
            eyeBrightnessSmoother.reset();
        }
        Vector2i brightness = eyeBrightness(minecraft, cameraPosition);
        // Iris: eyePosition is the camera entity's interpolated eye; relativeEyePosition is camera minus eye.
        Vector3f eyePosition = new Vector3f();
        Vector3f relativeEyePosition = new Vector3f();
        net.minecraft.world.entity.Entity cameraEntity = minecraft.getCameraEntity();
        float lookPartialTick = minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        // Iris: playerLookVector from a living camera entity; skyColor from the camera's environment probe.
        Vector3f playerLookVector = new Vector3f();
        if (cameraEntity instanceof net.minecraft.world.entity.LivingEntity living) {
            Vec3 look = living.getViewVector(lookPartialTick);
            playerLookVector.set((float) look.x, (float) look.y, (float) look.z);
        }
        Vector3f skyColor = new Vector3f();
        if (minecraft.level != null && cameraEntity != null) {
            int sky = minecraft.gameRenderer.getMainCamera().attributeProbe().getValue(
                    EnvironmentAttributes.SKY_COLOR, lookPartialTick);
            skyColor.set(net.minecraft.util.ARGB.redFloat(sky), net.minecraft.util.ARGB.greenFloat(sky),
                    net.minecraft.util.ARGB.blueFloat(sky));
        }
        if (cameraEntity != null) {
            Vec3 eye = cameraEntity.getEyePosition(minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false));
            eyePosition.set((float) eye.x, (float) eye.y, (float) eye.z);
            relativeEyePosition.set((float) (cameraPosition.x - eye.x), (float) (cameraPosition.y - eye.y),
                    (float) (cameraPosition.z - eye.z));
        }
        Vector2i smoothedBrightness = eyeBrightnessSmoother.update(
                brightness.x, brightness.y, frameTime, selectedEyeBrightnessHalfLifeTicks());
        cameraUniforms = new CameraUniforms(
                currentCameraPosition.position(), currentCameraPosition.integerPosition(), currentCameraPosition.fractionalPosition(),
                previousCameraPosition.position(), previousCameraPosition.integerPosition(), previousCameraPosition.fractionalPosition(),
                brightness, smoothedBrightness, eyePosition, relativeEyePosition, playerLookVector, skyColor,
                lightningBoltPosition(minecraft, cameraPosition));
        cameraUniformsInitialized = true;
        updateShaderPackVectorUniforms(cameraUniforms);
        updateShaderPackVector4Uniforms(cameraUniforms.lightningBoltPosition());
        updateShaderPackIntegerVector2Uniforms(cameraUniforms);
        updateShaderPackIntegerVectorUniforms(cameraUniforms);
        if (minecraft.level != null && minecraft.player != null) {
            shaderPackFrameUniforms = updateFrameUniforms(minecraft);
            FrameUniforms frame = shaderPackFrameUniforms;
            updateIrisExtraUniforms(minecraft, frame, cameraPosition);
            SHADER_PACK_VECTOR_BUFFERS.forEach((name, buffer) -> {
                if (IrisExtraUniforms.contains(name)) updateShaderPackVectorUniform(name, buffer, cameraUniforms);
            });
            CustomUniforms.active().evaluate(name -> customUniformInput(name, frame), frame.frameTime());
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
        if (terrainUsesOpaqueDepthSampler()) return true;
        if (!pipelinesDirty && pipelineSet != null) {
            return pipelineSet.usesDepth1Sampler();
        }
        ShaderPack pack = repository.getSelected().orElse(null);
        if (pack == null) {
            return false;
        }
        try {
            if (pack.terrainPrograms().contains("gbuffers_water") && TerrainShaderAdapter.usesOpaqueDepthSampler(
                    pack.readTerrainFragment("gbuffers_water").orElse(""))) return true;
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
            if (Boolean.getBoolean("veyra.graphicsSmoke")) {
                graph.execute(GraphicsResourceAllocator.UNPOOLED, new FrameGraphBuilder.Inspector() {
                    @Override public void acquireResource(String name) { }
                    @Override public void releaseResource(String name) { }
                    @Override public void beforeExecutePass(String name) { VeyraClient.LOGGER.info("Frame graph pass begins: {}", name); }
                    @Override public void afterExecutePass(String name) { VeyraClient.LOGGER.info("Frame graph pass ends: {}", name); }
                });
            } else {
                graph.execute(GraphicsResourceAllocator.UNPOOLED);
            }
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
                List.of(), true, false, false, false, Set.of(), Set.of(), 0, pipeline);
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

    /** Pushes the logical hand for one first-person item render. */
    public static void beginHandItemSubmission(boolean mainHand) {
        HAND_SIDE_SUBMISSION_CONTEXT.get().push(mainHand);
    }

    public static void endHandItemSubmission() {
        Deque<Boolean> hands = HAND_SIDE_SUBMISSION_CONTEXT.get();
        if (!hands.isEmpty()) hands.pop();
        if (hands.isEmpty()) HAND_SIDE_SUBMISSION_CONTEXT.remove();
    }

    public static void beginBlockEntitySubmission() {
        BLOCK_ENTITY_SUBMISSION_DEPTH.set(BLOCK_ENTITY_SUBMISSION_DEPTH.get() + 1);
        BLOCK_ENTITY_IDENTIFIER_CONTEXT.get().push(BlockIdMap.UNMAPPED);
    }

    public static void beginBlockEntitySubmission(BlockState state) {
        BLOCK_ENTITY_SUBMISSION_DEPTH.set(BLOCK_ENTITY_SUBMISSION_DEPTH.get() + 1);
        BLOCK_ENTITY_IDENTIFIER_CONTEXT.get().push(BlockIdMap.active().id(state));
    }

    public static void endBlockEntitySubmission() {
        Deque<Integer> identifiers = BLOCK_ENTITY_IDENTIFIER_CONTEXT.get();
        if (!identifiers.isEmpty()) identifiers.pop();
        if (identifiers.isEmpty()) BLOCK_ENTITY_IDENTIFIER_CONTEXT.remove();
        int depth = BLOCK_ENTITY_SUBMISSION_DEPTH.get();
        if (depth <= 1) {
            BLOCK_ENTITY_SUBMISSION_DEPTH.remove();
        } else {
            BLOCK_ENTITY_SUBMISSION_DEPTH.set(depth - 1);
        }
    }

    public static void beginEntitySubmission(EntityType<?> type) {
        int id = type == null ? EntityIdMap.UNMAPPED
                : EntityIdMap.active().id(BuiltInRegistries.ENTITY_TYPE.getKey(type).toString());
        ENTITY_IDENTIFIER_CONTEXT.get().push(id);
    }

    public static void endEntitySubmission() {
        Deque<Integer> identifiers = ENTITY_IDENTIFIER_CONTEXT.get();
        if (!identifiers.isEmpty()) identifiers.pop();
        if (identifiers.isEmpty()) ENTITY_IDENTIFIER_CONTEXT.remove();
    }

    /** Pushes the Iris currentRenderedItemId for synchronous item-node submission. */
    public static void beginRenderedItemSubmission(ItemStack stack) {
        int itemId = currentRenderedItemIdentifier(stack);
        RENDERED_ITEM_IDENTIFIER_CONTEXT.get().push(itemId);
    }

    public static void endRenderedItemSubmission() {
        Deque<Integer> identifiers = RENDERED_ITEM_IDENTIFIER_CONTEXT.get();
        if (!identifiers.isEmpty()) identifiers.pop();
        if (identifiers.isEmpty()) RENDERED_ITEM_IDENTIFIER_CONTEXT.remove();
    }

    private static int currentRenderedItemIdentifier(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return ItemIdMap.UNMAPPED;
        }
        return ItemIdMap.active().id(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
    }

    public static RenderType routeBlockEntityRenderType(RenderType originalType) {
        if (!renderingEnabled && !VeyraShadowRenderer.isRendering()) return originalType;
        if (originalType == null) {
            return originalType;
        }
        boolean blockEntity = BLOCK_ENTITY_SUBMISSION_DEPTH.get() > 0;
        Deque<Integer> entityIdentifiers = ENTITY_IDENTIFIER_CONTEXT.get();
        boolean entity = !entityIdentifiers.isEmpty();
        Deque<Integer> itemIdentifiers = RENDERED_ITEM_IDENTIFIER_CONTEXT.get();
        boolean item = !itemIdentifiers.isEmpty();
        int itemId = item ? itemIdentifiers.peek() : ItemIdMap.UNMAPPED;
        Deque<Boolean> handSides = HAND_SIDE_SUBMISSION_CONTEXT.get();
        boolean mainHand = !handSides.isEmpty() && handSides.peek();
        if (handSides.isEmpty()) HAND_SIDE_SUBMISSION_CONTEXT.remove();
        if (!blockEntity && !entity && !(item && isHandRendering())) return originalType;
        RenderPipeline original = originalType.pipeline();
        if (VeyraShadowRenderer.isRendering()) {
            if (!blockEntity && !entity) return originalType;
            RenderPipeline shadowPipeline = getShadowEntityPipeline(original, blockEntity);
            EntityPipelineSet set = blockEntity ? shadowBlockEntityPipelineSet : shadowEntityPipelineSet;
            int entityId = entity ? entityIdentifiers.peek() : EntityIdMap.UNMAPPED;
            int blockId = blockEntity ? currentBlockEntityIdentifier() : BlockIdMap.UNMAPPED;
            shadowPipeline = entityPipelineVariant(set, original, entityId, blockId, itemId, false);
            return shadowPipeline == null ? originalType : replaceRenderTypePipeline(originalType,
                    shadowPipeline, blockEntity ? "_veyra_shadow_block" : "_veyra_shadow_entity");
        }
        ShaderPack pack = selectedPack().orElse(null);
        if (pack == null) {
            return originalType;
        }
        if (isHandRendering()) {
            if (!item || itemId == ItemIdMap.UNMAPPED || !pack.supportsHandPipeline()
                    || !isEntityRenderPipeline(original)) {
                return originalType;
            }
            EntityPipelineSet pipelines = getHandPipelineSet(pack);
            RenderPipeline replacement = entityPipelineVariant(pipelines, original,
                    EntityIdMap.UNMAPPED, BlockIdMap.UNMAPPED, itemId, mainHand);
            return replacement == null ? originalType
                    : replaceRenderTypePipeline(originalType, replacement, "_veyra_hand_item");
        }
        if (blockEntity) {
            if (!pack.supportsBlockEntityPipeline()
                    || !(isEntityRenderPipeline(original) || isSignTextPipeline(original))) {
                return originalType;
            }
            EntityPipelineSet pipelines = getBlockEntityPipelineSet(pack);
            RenderPipeline replacement = entityPipelineVariant(pipelines, original,
                    EntityIdMap.UNMAPPED, currentBlockEntityIdentifier(), itemId, false);
            if (replacement == null) return originalType;
            if (!blockEntityPipelineRoutingLogged) {
                VeyraClient.LOGGER.info("Routing block entity pipeline {} through {} from shader pack '{}'.",
                        original.getLocation(), resolveBlockEntityProgram(pack, original), pack.name());
                blockEntityPipelineRoutingLogged = true;
            }
            return replaceRenderTypePipeline(originalType, replacement, "_veyra_block");
        }

        if (!pack.hasEntityFragment() || !isEntityRenderPipeline(original)) return originalType;
        getEntityPipeline(original);
        RenderPipeline replacement = entityPipelineVariant(entityPipelineSet, original,
                entityIdentifiers.peek(), BlockIdMap.UNMAPPED, itemId, false);
        if (replacement == null) {
            return originalType;
        }
        return replaceRenderTypePipeline(originalType, replacement, "_veyra_entity");
    }

    public static RenderPipeline getSignTextPipeline(RenderPipeline original) {
        if (!renderingEnabled && !VeyraShadowRenderer.isRendering()) return null;
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
        Map<RenderPipeline, RenderType> byPipeline = ROUTED_RENDER_TYPE_CACHE.computeIfAbsent(
                originalType, ignored -> new IdentityHashMap<>());
        RenderType cached = byPipeline.get(replacement);
        if (cached != null) return cached;
        RenderSetup originalSetup = ((RenderTypeAccessor) (Object) originalType).veyra$getSetup();
        RenderSetupAccessor setup = (RenderSetupAccessor) (Object) originalSetup;
        RenderSetup replacementSetup = RenderSetupAccessor.veyra$create(replacement,
                setup.veyra$getTextures(), setup.veyra$getUseLightmap(), setup.veyra$getUseOverlay(),
                setup.veyra$getLayeringTransform(), setup.veyra$getOutputTarget(),
                setup.veyra$getTextureTransform(), setup.veyra$getOutlineProperty(),
                setup.veyra$getAffectsCrumbling(), setup.veyra$getSortOnUpload(), setup.veyra$getBufferSize());
        RenderType routed = RenderTypeAccessor.veyra$create(originalType.toString() + suffix, replacementSetup);
        byPipeline.put(replacement, routed);
        return routed;
    }

    private static int currentBlockEntityIdentifier() {
        Deque<Integer> identifiers = BLOCK_ENTITY_IDENTIFIER_CONTEXT.get();
        return identifiers.isEmpty() ? BlockIdMap.UNMAPPED : identifiers.peek();
    }

    private static boolean isHandRendering() {
        return HAND_RENDER_DEPTH.get() > 0;
    }

    public static GraphicsPipeline getTerrainPipeline(TerrainRenderType terrainRenderType) {
        if (!renderingEnabled && !VeyraShadowRenderer.isRendering()) return null;
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
        if (!renderingEnabled && !VeyraShadowRenderer.isRendering()) return null;
        ShaderPack pack = selectedPack().orElse(null);
        if (pack == null || !pack.hasCloudFragment()) {
            return null;
        }
        GraphicsPipeline pipeline = prepareCloudPipeline(pack);
        if (pipeline != null && cloudPipelineUsesSceneColor && !cloudSceneColorCaptured) {
            return null;
        }
        return pipeline;
    }

    private static GraphicsPipeline prepareCloudPipeline(ShaderPack pack) {
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

    /** Capture the completed scene color before VulkanMod starts drawing clouds. */
    public static void captureCloudSceneColor() {
        cloudSceneColorCaptured = false;
        if (!renderingEnabled) return;
        ShaderPack pack = selectedPack().orElse(null);
        if (pack == null || !pack.hasCloudFragment()) return;
        GraphicsPipeline pipeline = prepareCloudPipeline(pack);
        if (pipeline == null || !cloudPipelineUsesSceneColor) return;

        try {
            var mainPass = Renderer.getInstance().getMainPass();
            var mainFramebuffer = mainPass.getMainFramebuffer();
            if (mainFramebuffer == null) {
                throw new IllegalStateException("The cloud colortex0 MainPass has no framebuffer ("
                        + mainPass.getClass().getName() + ").");
            }
            VulkanImage colorImage = mainFramebuffer.getColorAttachment();
            if (colorImage == null) {
                throw new IllegalStateException("The cloud colortex0 MainPass framebuffer has no active color image ("
                        + mainPass.getClass().getName() + ", " + mainFramebuffer.getClass().getName() + ", "
                        + mainFramebuffer.getWidth() + "x" + mainFramebuffer.getHeight() + ").");
            }
            cloudSceneColorSnapshot.capture(colorImage);
            cloudSceneColorCaptured = true;
        } catch (Exception exception) {
            lastError = shortError(exception);
            VeyraClient.LOGGER.warn("Could not capture scene color for gbuffers_clouds in shader pack '{}': {}",
                    pack.name(), lastError);
        }
    }

    public static boolean hasCapturedCloudSceneColor() {
        return cloudSceneColorCaptured;
    }

    public static void bindCloudSceneColor(VkRenderPass pass, GraphicsPipeline pipeline) {
        bindCloudSceneColor(pipeline);
        var descriptor = pipeline.getImageDescriptor(CLOUD_SCENE_SAMPLER_NAME);
        if (descriptor == null) return;
        pass.bindTexture(CLOUD_SCENE_SAMPLER_NAME, cloudSceneColorSnapshot.sampledView(),
                RenderSystem.getSamplerCache().getClampToEdge(com.mojang.blaze3d.textures.FilterMode.NEAREST));
    }

    /** Bind the scene snapshot for VulkanMod's direct cloud VBO pipeline path. */
    public static void bindCloudSceneColor(GraphicsPipeline pipeline) {
        var descriptor = pipeline.getImageDescriptor(CLOUD_SCENE_SAMPLER_NAME);
        if (descriptor == null) return;
        if (!cloudSceneColorCaptured) {
            throw new IllegalStateException("gbuffers_clouds tried to sample colortex0 before scene color was captured.");
        }
        VkTextureView view = (VkTextureView) cloudSceneColorSnapshot.sampledView();
        var image = view.texture().getVulkanImage();
        descriptor.setLayout(VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL);
        // VulkanMod's descriptor wrapper has independent layout tracking for this external snapshot.
        descriptor.isReadOnlyLayout = false;
        image.setSampler(net.vulkanmod.vulkan.texture.SamplerManager.getSampler(true, true, 0));
        VRenderSystem.setShaderTexture(descriptor.imageIdx, view);
        net.vulkanmod.vulkan.texture.VTextureSelector.bindTexture(descriptor.imageIdx, image);
    }

    public static RenderPipeline getEntityPipeline(RenderPipeline original) {
        if (!renderingEnabled && !VeyraShadowRenderer.isRendering()) return null;
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

    private static RenderPipeline entityPipelineVariant(EntityPipelineSet set, RenderPipeline original,
                                                        int entityId, int blockEntityId,
                                                        int currentRenderedItemId, boolean mainHand) {
        if (set == null) return null;
        RenderPipeline base = set.pipelines().get(original);
        EntityPipelineSpec spec = set.variantSources().get(original);
        if (base == null || spec == null) return base;
        int selectedEntityId = spec.usesEntityId() ? entityId : EntityIdMap.UNMAPPED;
        int selectedBlockId = spec.usesBlockEntityId() ? blockEntityId : BlockIdMap.UNMAPPED;
        int selectedItemId = spec.usesCurrentRenderedItemId()
                ? currentRenderedItemId : ItemIdMap.UNMAPPED;
        boolean selectedMainHand = spec.usesIsMainHand() && mainHand;
        if (selectedEntityId == EntityIdMap.UNMAPPED && selectedBlockId == BlockIdMap.UNMAPPED
                && selectedItemId == ItemIdMap.UNMAPPED && !selectedMainHand) return base;

        EntityIdentifierPair identifiers = new EntityIdentifierPair(
                selectedEntityId, selectedBlockId, selectedItemId, selectedMainHand);
        RenderPipeline cached = set.variants().getOrDefault(original, Map.of()).get(identifiers);
        if (cached != null) return cached;
        if (set.failedVariants().getOrDefault(original, Set.of()).contains(identifiers)) return base;
        try {
            String fragmentSource = spec.textPipeline()
                    ? EntityShaderAdapter.adaptTextFragment(spec.fragmentSource(), spec.programName(),
                            spec.shadowSamplerAvailable(), selectedEntityId, selectedBlockId, selectedItemId)
                    : EntityShaderAdapter.adaptFragment(spec.fragmentSource(), spec.programName(), spec.handPass(),
                            spec.shadowSamplerAvailable(), selectedEntityId, selectedBlockId, selectedItemId,
                            !isItemEntityTranslucentCullPipeline(original), selectedMainHand);
            String sourceHash = sha256(spec.programName() + '\n' + fragmentSource);
            EntityPipelineKey cacheKey = new EntityPipelineKey(original, spec.programName(), sourceHash,
                    spec.outputLayout(), spec.blendSettings(), spec.shadowPass());
            RenderPipeline pipeline = ENTITY_PIPELINE_CACHE.get(cacheKey);
            if (pipeline == null) {
                pipeline = spec.shadowPass()
                        ? compileShadowEntityPipeline(original, spec.programName(), fragmentSource, sourceHash,
                                spec.requiresNormal(), spec.requiresLightmap(), spec.requiresViewPosition(),
                                spec.requiresViewNormal())
                        : compileEntityPipeline(original, fragmentSource, sourceHash,
                                spec.requiresNormal(), spec.requiresLightmap(), spec.requiresViewPosition(),
                                spec.requiresViewNormal(), false, spec.handPass(), spec.programName(),
                                spec.outputLayout(), spec.blendSettings());
                ENTITY_PIPELINE_CACHE.put(cacheKey, pipeline);
            }
            set.variants().computeIfAbsent(original, ignored -> new HashMap<>()).put(identifiers, pipeline);
            return pipeline;
        } catch (IOException exception) {
            set.failedVariants().computeIfAbsent(original, ignored -> new HashSet<>()).add(identifiers);
            VeyraClient.LOGGER.warn("Could not compile {} pipeline variant for entityId={}, blockEntityId={}, "
                            + "currentRenderedItemId={}, isMainHand={}; "
                            + "using the unmapped shader variant: {}",
                    spec.programName(), selectedEntityId, selectedBlockId, selectedItemId, selectedMainHand,
                    exception.getMessage());
            return base;
        }
    }

    public static RenderPipeline getHandPipeline(RenderPipeline original) {
        if (!renderingEnabled && !VeyraShadowRenderer.isRendering()) return null;
        ShaderPack pack = selectedPack().orElse(null);
        if (!isHandRendering() || pack == null || !pack.supportsHandPipeline()
                || !isEntityRenderPipeline(original)) {
            return null;
        }
        EntityPipelineSet pipelines = getHandPipelineSet(pack);
        RenderPipeline replacement = pipelines == null ? null : pipelines.pipelines().get(original);
        if (replacement != null && !handPipelineRoutingLogged) {
            VeyraClient.LOGGER.info("Routing first-person hand pipeline {} through {} from shader pack '{}'.",
                    original.getLocation(), resolveHandProgram(pack, original), pack.name());
            handPipelineRoutingLogged = true;
        }
        return replacement;
    }

    private static EntityPipelineSet getHandPipelineSet(ShaderPack pack) {
        if (handPipelineDirty) {
            handPipelineDirty = false;
            try {
                replaceHandPipelines(compileEntityPipelines(pack, true));
                lastError = "";
                VeyraClient.LOGGER.info("Loaded {} first-person hand render pipelines from shader pack '{}'.",
                        handPipelineSet.pipelines().size(), pack.name());
            } catch (Exception exception) {
                lastError = shortError(exception);
                VeyraClient.LOGGER.error("Could not load first-person hand shader programs from shader pack '{}'.",
                        pack.name(), exception);
                return null;
            }
        }
        return handPipelineSet;
    }

    public static RenderPipeline getDamagedBlockPipeline(RenderPipeline original) {
        if (!renderingEnabled && !VeyraShadowRenderer.isRendering()) return null;
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
        if (!renderingEnabled && !VeyraShadowRenderer.isRendering()) return null;
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
        if (!renderingEnabled && !VeyraShadowRenderer.isRendering()) return null;
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
        if (!renderingEnabled && !VeyraShadowRenderer.isRendering()) return null;
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
        if (!renderingEnabled && !VeyraShadowRenderer.isRendering()) return null;
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
        if (!renderingEnabled && !VeyraShadowRenderer.isRendering()) return null;
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
        if (!renderingEnabled && !VeyraShadowRenderer.isRendering()) return null;
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
        if (!renderingEnabled && !VeyraShadowRenderer.isRendering()) return null;
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
        if (!renderingEnabled && !VeyraShadowRenderer.isRendering()) return null;
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
        if (!renderingEnabled && !VeyraShadowRenderer.isRendering()) return null;
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
        if (!renderingEnabled && !VeyraShadowRenderer.isRendering()) return null;
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
        ROUTED_RENDER_TYPE_CACHE.clear();
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
        if (entityPipelineSet != null) live.addAll(entityPipelineSet.allPipelines());
        if (shadowEntityPipelineSet != null) live.addAll(shadowEntityPipelineSet.allPipelines());
        if (shadowBlockEntityPipelineSet != null) live.addAll(shadowBlockEntityPipelineSet.allPipelines());
        if (blockEntityPipelineSet != null) live.addAll(blockEntityPipelineSet.allPipelines());
        if (handPipelineSet != null) live.addAll(handPipelineSet.allPipelines());
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
        VeyraSpecularTextures.invalidate();
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
        VeyraNoiseTexture.close();
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
        CustomUniforms previousCustomUniforms = CustomUniforms.active();
        ItemIdMap previousItemIds = ItemIdMap.active();
        EntityIdMap previousEntityIds = EntityIdMap.active();
        BlockIdMap previousBlockIds = BlockIdMap.active();
        try {
            pack.validatePipelineCapabilities();
            // Adapters size uniform blocks from the active definitions, so install them before compiling.
            CustomUniforms.activate(pack.readCustomUniforms());
            ItemIdMap.activate(pack.readItemIds());
            EntityIdMap.activate(pack.readEntityIds());
            BlockIdMap nextBlockIds = pack.readBlockIds();
            boolean blockMappingsChanged = !previousBlockIds.sameMappings(nextBlockIds);
            BlockIdMap.activate(nextBlockIds);
            registerCustomUniforms();
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
            boolean terrainRebuilt = updateTerrainLayerMode(pack);
            if (blockMappingsChanged && !terrainRebuilt) rebuildTerrainSections();
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
            if (repository.getSelected().orElse(null) != pack) {
                CustomUniforms.activate(previousCustomUniforms);
                ItemIdMap.activate(previousItemIds);
                EntityIdMap.activate(previousEntityIds);
                BlockIdMap.activate(previousBlockIds);
            }
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
        VeyraSpecularTextures.invalidate();
        CustomUniforms.activate(CustomUniforms.EMPTY);
        ItemIdMap.activate(ItemIdMap.EMPTY);
        EntityIdMap.activate(EntityIdMap.EMPTY);
        BlockIdMap.activate(BlockIdMap.EMPTY);
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
        VeyraSpecularTextures.invalidate();
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
                throw new IllegalStateException("The post program requires shadow inputs, but VulkanMod did not produce a shadow map.");
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
                prepareSampledTargets(graph, targets, shaderPass.sampledTargets());
                shaderPass.pass().addToFrame(graph, targets, samplerInfo);
            }
            if (framePlan.copyPass() != null) {
                prepareSampledTargets(graph, targets, Set.of(framePlan.copyInputTarget()));
                framePlan.copyPass().addToFrame(graph, targets, samplerInfo);
            }
            DepthSamplerState depthSamplerState = null;
            if (framePlan.shaderPasses().stream().anyMatch(pass -> pass.program().usesDepthSampler())) {
                depthSampling = true;
                depthSamplerState = prepareDepthSampler();
            }
            if (Boolean.getBoolean("veyra.graphicsSmoke") && graphTraceRuns < 6) {
                int traceRun = ++graphTraceRuns;
                VeyraClient.LOGGER.info("Tracing shader frame graph run {} for phase {}", traceRun, phase);
                graph.execute(GraphicsResourceAllocator.UNPOOLED, new FrameGraphBuilder.Inspector() {
                    @Override public void acquireResource(String name) { }
                    @Override public void releaseResource(String name) { }
                    @Override public void beforeExecutePass(String name) {
                        VeyraClient.LOGGER.info("Frame graph run {} pass begins: {}", traceRun, name);
                    }
                    @Override public void afterExecutePass(String name) {
                        VeyraClient.LOGGER.info("Frame graph run {} pass ends: {}", traceRun, name);
                    }
                });
            } else {
                graph.execute(GraphicsResourceAllocator.UNPOOLED);
            }
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

    private static int graphTraceRuns;

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

    private static void prepareSampledTargets(FrameGraphBuilder graph,
            Map<Identifier, ResourceHandle<RenderTarget>> targets, Set<Identifier> sampledTargets) {
        FramePass transition = graph.addPass("veyra:sampled-image-transitions");
        List<ResourceHandle<RenderTarget>> images = new ArrayList<>();
        for (Identifier id : sampledTargets) {
            ResourceHandle<RenderTarget> input = targets.get(id);
            if (input == null) throw new IllegalStateException("Missing sampled render target " + id);
            // A layout transition writes resource state. Advancing its graph version orders
            // this barrier after the producer and before PostPass consumes the new handle.
            ResourceHandle<RenderTarget> ready = transition.readsAndWrites(input);
            targets.put(id, ready);
            images.add(ready);
        }
        transition.executes(() -> {
            if (Renderer.getInstance().getBoundRenderPass() != null) Renderer.getInstance().endRenderPass();
            try (MemoryStack stack = MemoryStack.stackPush()) {
                for (ResourceHandle<RenderTarget> handle : images) {
                    GpuTexture color = handle.get().getColorTexture();
                    if (!(color instanceof VkGpuTexture texture)) throw new IllegalStateException("Shaderpack input is not a Vulkan image.");
                    var image = texture.getVulkanImage();
                    if (Boolean.getBoolean("veyra.graphicsSmoke")) {
                        VeyraClient.LOGGER.info("Sample transition: {} image={} layout-before={} command={}",
                                handle.get().getColorTexture().getLabel(), Long.toHexString(image.getId()), image.getCurrentLayout(), Long.toHexString(Renderer.getCommandBuffer().address()));
                    }
                    image.transitionImageLayout(stack, Renderer.getCommandBuffer(), VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL);
                    if (Boolean.getBoolean("veyra.graphicsSmoke")) {
                        VeyraClient.LOGGER.info("Sample transition complete: image={} layout-after={} command={}",
                                Long.toHexString(image.getId()), image.getCurrentLayout(), Long.toHexString(Renderer.getCommandBuffer().address()));
                    }
                }
            }
        });
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
            if (fragment.isPresent()) {
                if (name.equals("gbuffers_water"))
                    colorTargetIndices.addAll(GeometryColorSamplers.indices(GeometryColorSamplers.read(fragment.get())));
                addGeometryColorTargets(name, fragment.get(), width, height,
                        colorTargetSettings, colorTargetIndices);
            }
        }
        collectEntityColorTargets(pack, width, height, colorTargetSettings, colorTargetIndices);
        for (ShaderPack.Program source : sources) {
            if (TerrainShaderAdapter.usesNoiseSampler(source.source())) {
                VeyraNoiseTexture.ensure(pack.readNoiseTextureResolution());
            }
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
                    new AdaptedShader(COPY_FRAGMENT_SHADER, List.of(), false, false, false, false, Set.of(), Set.of(), 0), null);
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

    private static void collectEntityColorTargets(ShaderPack pack, int width, int height,
                                                   Map<Integer, ColorTargetSettings> settings,
                                                   Set<Integer> indices) throws IOException {
        List<RenderPipeline> candidates = RenderPipelines.getStaticPipelines().stream()
                .filter(VeyraRenderer::isEntityRenderPipeline).toList();
        Set<String> entityPrograms = new HashSet<>();
        Set<String> handPrograms = new HashSet<>();
        Set<String> blockPrograms = new HashSet<>();
        for (RenderPipeline candidate : candidates) {
            String entity = resolveEntityProgram(pack, candidate);
            if (entity != null) entityPrograms.add(entity);
            String hand = resolveHandProgram(pack, candidate);
            if (hand != null) handPrograms.add(hand);
            String block = resolveBlockEntityProgram(pack, candidate);
            if (block != null) blockPrograms.add(block);
        }
        String textProgram = resolveBlockTextProgram(pack);
        if (textProgram != null) blockPrograms.add(textProgram);

        for (String program : entityPrograms) {
            var source = pack.readEntityFragment(program);
            if (source.isPresent()) addGeometryColorTargets(program, source.get(), width, height, settings, indices);
        }
        for (String program : handPrograms) {
            var source = pack.readHandFragment(program);
            if (source.isPresent()) addGeometryColorTargets(program, source.get(), width, height, settings, indices);
        }
        for (String program : blockPrograms) {
            var source = pack.readBlockFragment(program);
            if (source.isPresent()) addGeometryColorTargets(program, source.get(), width, height, settings, indices);
        }
    }

    private static void addGeometryColorTargets(String program, String source, int width, int height,
                                                Map<Integer, ColorTargetSettings> settings,
                                                Set<Integer> indices) throws IOException {
        List<Integer> targets = FragmentOutputs.parse(source).targets();
        if (targets.size() > VeyraDeviceFeatures.maxColorAttachments) {
            throw new IOException(program + " uses " + targets.size()
                    + " color attachments, but this Vulkan device supports "
                    + VeyraDeviceFeatures.maxColorAttachments + ".");
        }
        for (int index : targets) {
            if (index == 0) continue;
            indices.add(index);
            ColorTargetSettings target = settings.getOrDefault(index, ColorTargetSettings.defaults(index, width, height));
            if (target.width() != width || target.height() != height) {
                throw new IOException(program + " colortex" + index
                        + " must match the scene resolution; reduced-size geometry targets are not implemented.");
            }
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
                throw new IllegalArgumentException(
                        TerrainShaderAdapter.unsupportedUniformMessage(programName + ".vsh", name));
            }
            boolean validType = isBooleanUniform(name)
                    ? type.equals("bool") || type.equals("int")
                    : type.equals(supportedType);
            if (!validType) {
                throw new IllegalArgumentException(programName + ".vsh uniform '" + name
                        + "' must use " + (isBooleanUniform(name) ? "bool or int" : supportedType) + ".");
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
        return availableUniforms().stream().filter(declaredUniforms::contains).toList();
    }

    /** Built-in post uniforms followed by the active pack's custom uniforms. */
    private static List<String> availableUniforms() {
        return ShaderPackUniformAdapter.mergeUniformNames(
                SUPPORTED_UNIFORMS, CustomUniforms.active().uniforms());
    }

    private static boolean isBooleanUniform(String uniform) {
        if (BOOLEAN_UNIFORMS.contains(uniform)) {
            return true;
        }
        CustomUniforms.Uniform custom = SUPPORTED_UNIFORMS.contains(uniform) ? null
                : CustomUniforms.active().uniform(uniform);
        return custom != null && custom.type() == CustomUniforms.Type.BOOL;
    }

    private static String shaderPackUniformType(String uniform) {
        if (!SUPPORTED_UNIFORMS.contains(uniform)) {
            CustomUniforms.Uniform custom = CustomUniforms.active().uniform(uniform);
            return custom == null ? null : custom.type() == CustomUniforms.Type.FLOAT ? "float" : "int";
        }
        if (MATRIX_UNIFORMS.contains(uniform)) {
            return "mat4";
        }
        if (IVECTOR2_UNIFORMS.contains(uniform)) {
            return "ivec2";
        }
        if (VECTOR4_UNIFORMS.contains(uniform)) {
            return "vec4";
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
            String memberName = isBooleanUniform(uniform) ? "veyra_" + uniform : uniform;
            source.append("    ").append(type).append(' ').append(memberName).append(";\n");
        }
        source.append("};\n");
        for (String uniform : uniforms) {
            if (isBooleanUniform(uniform)) {
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
                boolean usesNoiseSampler = TerrainShaderAdapter.usesNoiseSampler(fragmentSource);
                boolean usesSpecularSampler = TerrainShaderAdapter.usesSpecularSampler(fragmentSource);
                boolean usesShadowColor = ShadowColorSampler.used(fragmentSource);
                boolean usesOpaqueShadow = ShadowDepthSampler.used(fragmentSource);
                if (usesSpecularSampler) VeyraSpecularTextures.prepareTerrain();
                boolean usesOpaqueDepthSampler = programName.equals("gbuffers_water")
                        && TerrainShaderAdapter.usesOpaqueDepthSampler(fragmentSource);
                Set<Integer> colorInputs = programName.equals("gbuffers_water")
                        ? GeometryColorSamplers.indices(GeometryColorSamplers.read(fragmentSource)) : Set.of();
                if (usesNoiseSampler) VeyraNoiseTexture.ensure(pack.readNoiseTextureResolution());
                FragmentOutputs outputLayout = FragmentOutputs.parse(fragmentSource);
                fragmentSource = TerrainShaderAdapter.adaptFragment(fragmentSource, shadowSamplerAvailable,
                        programName.equals("gbuffers_water"));

                Pipeline.Builder builder = new Pipeline.Builder(
                        PipelineManager.getTerrainVertexFormat(), "veyra_" + programName);
                builder.applyConfig(terrainPipelineConfig(shadowSamplerAvailable, usesNoiseSampler, usesOpaqueDepthSampler, usesSpecularSampler, usesShadowColor, usesOpaqueShadow, colorInputs));
                builder.setShaderSrc(SPIRVUtils.ShaderKind.VERTEX_SHADER, vertexSource);
                builder.setShaderSrc(SPIRVUtils.ShaderKind.FRAGMENT_SHADER, fragmentSource);
                GraphicsPipeline pipeline = builder.createGraphicsPipeline();
                pipelines.put(programName, pipeline);
                VeyraGeometryTargets.register(pipeline, outputLayout, pack.readGeometryBlendSettings(programName));
                setShadowSamplerLayout(pipeline, shadowSamplerAvailable);
                setNoiseSamplerLayout(pipeline);
                if (usesSpecularSampler) pipeline.getImageDescriptor(VeyraSpecularTextures.SAMPLER).setLayout(VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL);
                var opaqueDepth = pipeline.getImageDescriptor("VeyraDepthTex1Sampler");
                if (opaqueDepth != null) {
                    opaqueDepth.setLayout(VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL);
                    opaqueDepth.isReadOnlyLayout = false;
                }
                for (int index : colorInputs) {
                    var descriptor = pipeline.getImageDescriptor(GeometryColorSamplers.samplerName(index));
                    descriptor.setLayout(VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL);
                    descriptor.isReadOnlyLayout = false;
                }
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
                    boolean usesNoiseSampler = TerrainShaderAdapter.usesNoiseSampler(fragmentSource);
                    boolean usesSpecularSampler = TerrainShaderAdapter.usesSpecularSampler(fragmentSource);
                    if (usesSpecularSampler) VeyraSpecularTextures.prepareTerrain();
                    if (usesNoiseSampler) VeyraNoiseTexture.ensure(pack.readNoiseTextureResolution());
                    fragmentSource = TerrainShaderAdapter.adaptFragment(fragmentSource, false);
                    Pipeline.Builder builder = new Pipeline.Builder(
                            PipelineManager.getTerrainVertexFormat(), "veyra_" + programName);
                    builder.applyConfig(terrainPipelineConfig(false, usesNoiseSampler, false, usesSpecularSampler, false, false, Set.of()));
                    builder.setShaderSrc(SPIRVUtils.ShaderKind.VERTEX_SHADER, vertexSource);
                    builder.setShaderSrc(SPIRVUtils.ShaderKind.FRAGMENT_SHADER, fragmentSource);
                    GraphicsPipeline shadowPipeline = builder.createGraphicsPipeline();
                    setNoiseSamplerLayout(shadowPipeline);
                    if (usesSpecularSampler) shadowPipeline.getImageDescriptor(VeyraSpecularTextures.SAMPLER).setLayout(VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL);
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

    private static PipelineConfig terrainPipelineConfig(boolean shadowSamplerAvailable, boolean noiseSamplerAvailable) {
        return terrainPipelineConfig(shadowSamplerAvailable, noiseSamplerAvailable, false, false, false, false, Set.of());
    }

    private static PipelineConfig terrainPipelineConfig(boolean shadowSamplerAvailable, boolean noiseSamplerAvailable,
                                                       boolean opaqueDepthSamplerAvailable, boolean specularSamplerAvailable, boolean shadowColorAvailable, boolean opaqueShadowAvailable, Set<Integer> colorInputs) {
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
        int nextBinding = 5;
        if (shadowSamplerAvailable) {
            config.addImageDescriptor(nextBinding++, "sampler2D", "VeyraShadowSampler", 3);
        }
        if (noiseSamplerAvailable) {
            config.addImageDescriptor(nextBinding++, "sampler2D", VeyraNoiseTexture.SAMPLER_NAME, 4);
        }
        if (opaqueDepthSamplerAvailable) {
            config.addImageDescriptor(nextBinding++, "sampler2D", "VeyraDepthTex1Sampler", 5);
        }
        if (specularSamplerAvailable) config.addImageDescriptor(nextBinding++, "sampler2D", VeyraSpecularTextures.SAMPLER, 1);
        if (opaqueShadowAvailable) config.addImageDescriptor(nextBinding++, "sampler2D", ShadowDepthSampler.NAME, ShadowDepthSampler.TERRAIN_SLOT);
        if (shadowColorAvailable) config.addImageDescriptor(nextBinding++, "sampler2D", ShadowColorSampler.NAME, ShadowColorSampler.TERRAIN_SLOT);
        int imageSlot = GeometryColorSamplers.FIRST_IMAGE_SLOT;
        for (int index : new java.util.TreeSet<>(colorInputs)) {
            if (opaqueShadowAvailable && imageSlot == ShadowDepthSampler.TERRAIN_SLOT) imageSlot++;
            if (shadowColorAvailable && imageSlot == ShadowColorSampler.TERRAIN_SLOT) imageSlot++;
            config.addImageDescriptor(nextBinding++, "sampler2D", GeometryColorSamplers.samplerName(index), imageSlot++);
        }
        return config.build();
    }

    /** The capture hook runs before water draws; bind the depth-only borrowed view, never scene depth. */
    public static void bindTerrainOpaqueDepth(GraphicsPipeline pipeline) {
        var descriptor = pipeline.getImageDescriptor("VeyraDepthTex1Sampler");
        if (descriptor == null) return;
        var view = (net.vulkanmod.render.engine.VkTextureView) depthTex1Snapshot.sampledView();
        view.texture().getVulkanImage().setSampler(
                net.vulkanmod.vulkan.texture.SamplerManager.getSampler(true, false, 0));
        // WorldRenderer refreshes the indexed native table from VRenderSystem after pipeline bind.
        // Keep both tables on the same live view, including after a resize or previous post draw.
        VRenderSystem.setShaderTexture(descriptor.imageIdx, view);
        net.vulkanmod.vulkan.texture.VTextureSelector.bindTexture(descriptor.imageIdx,
                view.texture().getVulkanImage());
    }

    private static boolean terrainUsesOpaqueDepthSampler() {
        return terrainPipelineSet != null && terrainPipelineSet.pipelines().values().stream()
                .anyMatch(pipeline -> pipeline.getImageDescriptor("VeyraDepthTex1Sampler") != null);
    }

    public static void shadowInputFailed(Exception failure) {
        renderingEnabled = false;
        lastError = shortError(failure);
    }

    public static void materialInputFailed(Exception failure) {
        renderingEnabled = false;
        lastError = shortError(failure);
        VeyraClient.LOGGER.error("Disabled shader rendering after a material texture failure.", failure);
    }

    private static void closeOpaqueDepthSnapshot() {
        VRenderSystem.setShaderTexture(5, null);
        depthTex1Snapshot.close();
    }

    private static void setNoiseSamplerLayout(GraphicsPipeline pipeline) {
        var noiseSampler = pipeline.getImageDescriptor(VeyraNoiseTexture.SAMPLER_NAME);
        if (noiseSampler != null) noiseSampler.setLayout(VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL);
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
        Optional<String> shaderPackVertex = pack.readCloudVertex();
        if (shaderPackVertex.isPresent()) {
            vertexSource = CloudShaderAdapter.adaptVertex(vertexSource, shaderPackVertex.get());
        }
        String source = pack.readCloudFragment()
                .orElseThrow(() -> new IOException("The pack no longer contains gbuffers_clouds.fsh."));
        CloudShaderAdapter.AdaptedFragment adaptedFragment = CloudShaderAdapter.adapt(source);
        if (adaptedFragment.usesNoiseSampler()) {
            VeyraNoiseTexture.ensure(pack.readNoiseTextureResolution());
        }
        String fragmentSource = adaptedFragment.source();
        PipelineConfig config = PipelineConfig.fromJson("clouds", ShaderLoadUtil.getJsonConfig(shaderPath, "clouds"));
        if (!adaptedFragment.uniformFields().isEmpty()) {
            vertexSource = addCloudUniformFields(vertexSource, adaptedFragment.uniformFields());
            config = addCloudUniformBufferFields(config, adaptedFragment.uniformFields());
        }
        if (adaptedFragment.usesSceneColorSampler() || adaptedFragment.usesNoiseSampler()) {
            List<PipelineConfig.ImageDescriptorInfo> descriptors = new ArrayList<>(config.imageDescriptors);
            if (adaptedFragment.usesSceneColorSampler()) {
                if (descriptors.stream().anyMatch(descriptor -> descriptor.binding() == 2
                        || descriptor.name().equals(CLOUD_SCENE_SAMPLER_NAME))) {
                    throw new IOException("VulkanMod's cloud pipeline already uses the scene-color descriptor binding.");
                }
                descriptors.add(new PipelineConfig.ImageDescriptorInfo(
                        2, "sampler2D", CLOUD_SCENE_SAMPLER_NAME, CLOUD_SCENE_TEXTURE_SLOT));
            }
            if (adaptedFragment.usesNoiseSampler()) {
                if (descriptors.stream().anyMatch(descriptor -> descriptor.binding() == 3
                        || descriptor.name().equals(VeyraNoiseTexture.SAMPLER_NAME))) {
                    throw new IOException("VulkanMod's cloud pipeline already uses the noise texture descriptor binding.");
                }
                descriptors.add(new PipelineConfig.ImageDescriptorInfo(
                        3, "sampler2D", VeyraNoiseTexture.SAMPLER_NAME, 4));
            }
            config = new PipelineConfig(config.shaderPaths, config.ubs, descriptors, config.pushConstantsInfo);
        }

        Pipeline.Builder builder = new Pipeline.Builder(DefaultVertexFormat.POSITION_COLOR, "veyra_clouds");
        builder.applyConfig(config);
        builder.setShaderSrc(SPIRVUtils.ShaderKind.VERTEX_SHADER, vertexSource);
        builder.setShaderSrc(SPIRVUtils.ShaderKind.FRAGMENT_SHADER, fragmentSource);
        GraphicsPipeline pipeline = builder.createGraphicsPipeline();
        if (adaptedFragment.usesNoiseSampler()) setNoiseSamplerLayout(pipeline);
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
        Map<RenderPipeline, EntityPipelineSpec> variantSources = new IdentityHashMap<>();
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
            String rawFragmentSource;
            String fragmentSource;
            boolean requiresNormal = false;
            boolean requiresLightmap = false;
            boolean requiresViewPosition = false;
            boolean requiresViewNormal = false;
            FragmentOutputs outputLayout = null;
            GeometryBlendSettings blendSettings = null;
            try {
                rawFragmentSource = (hand ? pack.readHandFragment(programName) : pack.readEntityFragment(programName))
                        .orElseThrow(() -> new IOException("The pack no longer contains " + programName + ".fsh."));
                outputLayout = FragmentOutputs.parse(rawFragmentSource);
                blendSettings = pack.readGeometryBlendSettings(programName);
                fragmentSource = rawFragmentSource;
                if (EntityShaderAdapter.usesNoiseSampler(fragmentSource)) {
                    VeyraNoiseTexture.ensure(pack.readNoiseTextureResolution());
                }
                requiresNormal = EntityShaderAdapter.requiresNormalInput(fragmentSource);
                requiresLightmap = EntityShaderAdapter.requiresLightmapInput(fragmentSource);
                requiresViewPosition = EntityShaderAdapter.requiresViewPositionInput(fragmentSource);
                requiresViewNormal = EntityShaderAdapter.requiresViewNormalInput(fragmentSource);
                fragmentSource = EntityShaderAdapter.adaptFragment(
                        fragmentSource, programName, hand, pack.hasShadowFragment(), -1, -1,
                        ItemIdMap.UNMAPPED, !isItemEntityTranslucentCullPipeline(original));
            } catch (IOException exception) {
                if (!fallback) {
                    throw exception;
                }
                VeyraClient.LOGGER.warn("Skipping incompatible {} fallback for {}: {}",
                        programName, original.getLocation(), exception.getMessage());
                continue;
            }
            String sourceHash = sha256(programName + '\n' + fragmentSource);
            EntityPipelineKey cacheKey = new EntityPipelineKey(
                    original, programName, sourceHash, outputLayout, blendSettings, false);
            RenderPipeline entityPipeline = ENTITY_PIPELINE_CACHE.get(cacheKey);
            if (entityPipeline == null) {
                try {
                    entityPipeline = compileEntityPipeline(
                            original, fragmentSource, sourceHash, requiresNormal, requiresLightmap,
                            requiresViewPosition, requiresViewNormal, false, hand, programName,
                            outputLayout, blendSettings);
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
            variantSources.put(original, new EntityPipelineSpec(programName, rawFragmentSource,
                    requiresNormal, requiresLightmap, requiresViewPosition, requiresViewNormal,
                    false, hand, false, pack.hasShadowFragment(), outputLayout, blendSettings));
        }
        if (pipelines.isEmpty() && (hand
                ? pack.hasHandFragment() || pack.hasHandWaterFragment()
                : !pack.entityPrograms().isEmpty())) {
            throw new IOException(hand
                    ? "VulkanMod's Minecraft first-person hand render pipelines were not found."
                    : "VulkanMod's Minecraft entity render pipelines were not found.");
        }
        return new EntityPipelineSet(pipelines, variantSources);
    }

    private static EntityPipelineSet compileShadowEntityPipelines(ShaderPack pack, String programName) throws IOException {
        String rawFragmentSource = pack.readShadowFragment(programName)
                .orElseThrow(() -> new IOException("The pack no longer contains a supported shadow fragment."));
        FragmentOutputs outputLayout = FragmentOutputs.parse(rawFragmentSource);
        if (!outputLayout.sceneOnly()) {
            throw new IOException(programName + " shadow fragments can write only color attachment 0.");
        }
        if (EntityShaderAdapter.usesNoiseSampler(rawFragmentSource)) {
            VeyraNoiseTexture.ensure(pack.readNoiseTextureResolution());
        }
        boolean requiresNormal = EntityShaderAdapter.requiresNormalInput(rawFragmentSource);
        boolean requiresLightmap = EntityShaderAdapter.requiresLightmapInput(rawFragmentSource);
        boolean requiresViewPosition = EntityShaderAdapter.requiresViewPositionInput(rawFragmentSource);
        boolean requiresViewNormal = EntityShaderAdapter.requiresViewNormalInput(rawFragmentSource);
        String fragmentSource = EntityShaderAdapter.adaptFragment(rawFragmentSource, programName);
        String sourceHash = sha256(programName + '\n' + fragmentSource);
        List<RenderPipeline> candidates = RenderPipelines.getStaticPipelines().stream()
                .filter(VeyraRenderer::isEntityRenderPipeline)
                .toList();
        Map<RenderPipeline, RenderPipeline> pipelines = new IdentityHashMap<>();
        Map<RenderPipeline, EntityPipelineSpec> variantSources = new IdentityHashMap<>();
        for (RenderPipeline original : candidates) {
            EntityPipelineKey cacheKey = new EntityPipelineKey(
                    original, programName, sourceHash, outputLayout, null, true);
            RenderPipeline shadowPipeline = ENTITY_PIPELINE_CACHE.get(cacheKey);
            if (shadowPipeline == null) {
                shadowPipeline = compileShadowEntityPipeline(original, programName, fragmentSource, sourceHash,
                        requiresNormal, requiresLightmap, requiresViewPosition, requiresViewNormal);
                ENTITY_PIPELINE_CACHE.put(cacheKey, shadowPipeline);
            }
            pipelines.put(original, shadowPipeline);
            variantSources.put(original, new EntityPipelineSpec(programName, rawFragmentSource,
                    requiresNormal, requiresLightmap, requiresViewPosition, requiresViewNormal,
                    true, false, false, false, outputLayout, null));
        }
        if (pipelines.isEmpty()) {
            throw new IOException("Minecraft's entity render pipelines were not found for the shadow pass.");
        }
        return new EntityPipelineSet(pipelines, variantSources);
    }

    private static RenderPipeline compileShadowEntityPipeline(
            RenderPipeline original, String programName, String fragmentSource, String sourceHash,
            boolean requiresNormal, boolean requiresLightmap,
            boolean requiresViewPosition, boolean requiresViewNormal) throws IOException {
        Identifier fragmentShader = Identifier.fromNamespaceAndPath(
                "veyra", "shaderpack/" + programName + "/" + sourceHash);
        Identifier location = Identifier.fromNamespaceAndPath("veyra",
                "pipeline/" + programName + "/" + sourceHash + "/"
                        + original.getLocation().getPath().replace('/', '_'));
        ShaderManager shaderManager = ((MinecraftAccessor) Minecraft.getInstance()).veyra$getShaderManager();
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
                return fragmentSource;
            }
            if (selectedVertexSource != null && identifier.equals(selectedVertexShader)
                    && type == ShaderType.VERTEX) {
                return selectedVertexSource;
            }
            return shaderManager.getShader(identifier, type);
        };
        RenderPipeline shadowPipeline = copyShadowPipelineWithShaders(original, location, vertexShader,
                fragmentShader, fragmentSource.contains("uniform sampler2D VeyraNoiseSampler;"));
        VeyraPipelineResources.track(shadowPipeline);
        CompiledRenderPipeline compiled = RenderSystem.getDevice().precompilePipeline(shadowPipeline, source);
        if (!compiled.isValid()) {
            throw new IOException("VulkanMod could not compile " + original.getLocation()
                    + " with the selected shadow shader.");
        }
        return shadowPipeline;
    }

    private static EntityPipelineSet compileBlockEntityPipelines(ShaderPack pack) throws IOException {
        List<RenderPipeline> candidates = RenderPipelines.getStaticPipelines().stream()
                .filter(VeyraRenderer::isEntityRenderPipeline)
                .toList();
        Map<RenderPipeline, RenderPipeline> pipelines = new IdentityHashMap<>();
        Map<RenderPipeline, EntityPipelineSpec> variantSources = new IdentityHashMap<>();
        for (RenderPipeline original : candidates) {
            String programName = resolveBlockEntityProgram(pack, original);
            if (programName == null) {
                continue;
            }
            boolean fallback = !pack.blockPrograms().contains(programName);
            String rawFragmentSource;
            String fragmentSource;
            boolean requiresNormal = false;
            boolean requiresLightmap = false;
            boolean requiresViewPosition = false;
            boolean requiresViewNormal = false;
            FragmentOutputs outputLayout = null;
            GeometryBlendSettings blendSettings = null;
            try {
                rawFragmentSource = pack.readBlockFragment(programName)
                        .orElseThrow(() -> new IOException("The pack no longer contains " + programName + ".fsh."));
                outputLayout = FragmentOutputs.parse(rawFragmentSource);
                blendSettings = pack.readGeometryBlendSettings(programName);
                fragmentSource = rawFragmentSource;
                if (EntityShaderAdapter.usesNoiseSampler(fragmentSource)) {
                    VeyraNoiseTexture.ensure(pack.readNoiseTextureResolution());
                }
                requiresNormal = EntityShaderAdapter.requiresNormalInput(fragmentSource);
                requiresLightmap = EntityShaderAdapter.requiresLightmapInput(fragmentSource);
                requiresViewPosition = EntityShaderAdapter.requiresViewPositionInput(fragmentSource);
                requiresViewNormal = EntityShaderAdapter.requiresViewNormalInput(fragmentSource);
                fragmentSource = EntityShaderAdapter.adaptFragment(
                        fragmentSource, programName, false, pack.hasShadowFragment(), -1, -1,
                        ItemIdMap.UNMAPPED, !isItemEntityTranslucentCullPipeline(original));
            } catch (IOException exception) {
                if (!fallback) {
                    throw exception;
                }
                VeyraClient.LOGGER.warn("Skipping incompatible {} block entity fallback for {}: {}",
                        programName, original.getLocation(), exception.getMessage());
                continue;
            }
            String sourceHash = sha256(programName + '\n' + fragmentSource);
            EntityPipelineKey cacheKey = new EntityPipelineKey(
                    original, programName, sourceHash, outputLayout, blendSettings, false);
            RenderPipeline blockPipeline = ENTITY_PIPELINE_CACHE.get(cacheKey);
            if (blockPipeline == null) {
                try {
                    blockPipeline = compileEntityPipeline(
                            original, fragmentSource, sourceHash, requiresNormal, requiresLightmap,
                            requiresViewPosition, requiresViewNormal, false, false, programName,
                            outputLayout, blendSettings);
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
            variantSources.put(original, new EntityPipelineSpec(programName, rawFragmentSource,
                    requiresNormal, requiresLightmap, requiresViewPosition, requiresViewNormal,
                    false, false, false, pack.hasShadowFragment(), outputLayout, blendSettings));
        }
        compileSignTextPipelines(pack, pipelines, variantSources);
        if (pipelines.isEmpty() && !pack.blockPrograms().isEmpty()) {
            throw new IOException("Minecraft's block entity render pipelines were not found or do not support this shader interface.");
        }
        return new EntityPipelineSet(pipelines, variantSources);
    }

    private static void compileSignTextPipelines(ShaderPack pack,
                                                 Map<RenderPipeline, RenderPipeline> pipelines,
                                                 Map<RenderPipeline, EntityPipelineSpec> variantSources) {
        String programName = resolveBlockTextProgram(pack);
        if (programName == null) {
            return;
        }
        List<RenderPipeline> candidates = RenderPipelines.getStaticPipelines().stream()
                .filter(VeyraRenderer::isSignTextPipeline)
                .toList();
        String rawFragmentSource;
        String fragmentSource;
        boolean requiresLightmap;
        boolean requiresViewPosition;
        FragmentOutputs outputLayout;
        GeometryBlendSettings blendSettings;
        try {
            rawFragmentSource = pack.readBlockFragment(programName)
                    .orElseThrow(() -> new IOException("The pack no longer contains " + programName + ".fsh."));
            outputLayout = FragmentOutputs.parse(rawFragmentSource);
            blendSettings = pack.readGeometryBlendSettings(programName);
            fragmentSource = rawFragmentSource;
            if (EntityShaderAdapter.usesNoiseSampler(fragmentSource)) {
                VeyraNoiseTexture.ensure(pack.readNoiseTextureResolution());
            }
            requiresLightmap = EntityShaderAdapter.requiresLightmapInput(fragmentSource);
            requiresViewPosition = EntityShaderAdapter.requiresViewPositionInput(fragmentSource);
            fragmentSource = EntityShaderAdapter.adaptTextFragment(
                    fragmentSource, programName, pack.hasShadowFragment(), -1, -1);
        } catch (IOException exception) {
            VeyraClient.LOGGER.warn("Skipping sign text for {} because its inputs are unsupported: {}",
                    programName, exception.getMessage());
            return;
        }
        String sourceHash = sha256("sign_text_" + programName + '\n' + fragmentSource);
        for (RenderPipeline original : candidates) {
            EntityPipelineKey cacheKey = new EntityPipelineKey(original, "sign_text_" + programName,
                    sourceHash, outputLayout, blendSettings, false);
            RenderPipeline textPipeline = ENTITY_PIPELINE_CACHE.get(cacheKey);
            if (textPipeline == null) {
                try {
                    textPipeline = compileEntityPipeline(
                            original, fragmentSource, sourceHash, false, requiresLightmap,
                            requiresViewPosition, false, false, false, programName,
                            outputLayout, blendSettings);
                } catch (IOException exception) {
                    VeyraClient.LOGGER.warn("Skipping sign text pipeline {} for {}: {}",
                            original.getLocation(), programName, exception.getMessage());
                    continue;
                }
                ENTITY_PIPELINE_CACHE.put(cacheKey, textPipeline);
            }
            pipelines.put(original, textPipeline);
            variantSources.put(original, new EntityPipelineSpec(programName, rawFragmentSource,
                    false, requiresLightmap, requiresViewPosition, false,
                    false, false, true, pack.hasShadowFragment(), outputLayout, blendSettings));
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
        if (pipeline == null) {
            return false;
        }
        String location = pipeline.getLocation().getPath();
        boolean entityFragment = pipeline.getFragmentShader()
                .equals(Identifier.withDefaultNamespace("core/entity"));
        boolean itemEntityFragment = location.equals("pipeline/item_entity_translucent_cull")
                && pipeline.getFragmentShader()
                .equals(Identifier.withDefaultNamespace("core/rendertype_item_entity_translucent_cull"));
        if (!entityFragment && !itemEntityFragment) {
            return false;
        }
        return location.startsWith("pipeline/entity_")
                || location.startsWith("pipeline/armor_")
                || location.startsWith("pipeline/item_entity_")
                || location.equals("pipeline/eyes");
    }

    private static boolean isItemEntityTranslucentCullPipeline(RenderPipeline pipeline) {
        return pipeline != null
                && pipeline.getLocation().getPath().equals("pipeline/item_entity_translucent_cull");
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
            RenderPipeline original, String fragmentSource, String sourceHash,
            boolean requiresNormal, boolean requiresLightmap,
            boolean requiresViewPosition, boolean requiresViewNormal,
            boolean shadowPass, boolean handPass, String programName,
            FragmentOutputs outputLayout, GeometryBlendSettings blendSettings) throws IOException {
        Identifier fragmentShader = Identifier.fromNamespaceAndPath(
                "veyra", "shaderpack/entities/" + sourceHash);
        ShaderManager shaderManager = ((MinecraftAccessor) Minecraft.getInstance()).veyra$getShaderManager();
        Identifier vertexShader = original.getVertexShader();
        String adaptedVertex = null;
        boolean itemEntityPipeline = isItemEntityTranslucentCullPipeline(original);
        if (requiresNormal || requiresLightmap || requiresViewPosition || requiresViewNormal || handPass
                || itemEntityPipeline) {
            String vertexSource = shaderManager.getShader(vertexShader, ShaderType.VERTEX);
            if (itemEntityPipeline) {
                vertexSource = EntityShaderAdapter.adaptItemEntityVertexFormat(vertexSource, programName);
            }
            adaptedVertex = EntityShaderAdapter.adaptVertexShader(
                    vertexSource, shadowPass, programName,
                    requiresNormal, requiresLightmap, requiresViewPosition, requiresViewNormal, handPass);
            vertexShader = entityNormalVertexShader(original, sourceHash, shadowPass);
        }
        Identifier selectedVertexShader = vertexShader;
        String selectedVertexSource = adaptedVertex;
        boolean usesNoiseSampler = fragmentSource.contains("uniform sampler2D VeyraNoiseSampler;");
        boolean usesShadowSampler = fragmentSource.contains("uniform sampler2D Sampler3;");
        RenderPipeline pipeline = copyPipelineWithShaders(original,
                selectedVertexSource == null ? original.getVertexShader() : selectedVertexShader,
                fragmentShader, usesNoiseSampler, usesShadowSampler, TerrainShaderAdapter.usesSpecularSampler(fragmentSource), ShadowColorSampler.used(fragmentSource), ShadowDepthSampler.used(fragmentSource));
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
        GraphicsPipeline nativePipeline = ExtendedRenderPipeline.of(pipeline).getPipeline();
        if (nativePipeline == null) {
            throw new IOException("VulkanMod did not create a native pipeline for " + original.getLocation() + ".");
        }
        VeyraGeometryTargets.register(nativePipeline, outputLayout, blendSettings, false);
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
        return copyPipelineWithShaders(original, vertexShader, fragmentShader, false);
    }

    private static RenderPipeline copyPipelineWithShaders(
            RenderPipeline original, Identifier vertexShader, Identifier fragmentShader, boolean useNoiseSampler) {
        return copyPipelineWithShaders(original, vertexShader, fragmentShader, useNoiseSampler, false);
    }

    private static RenderPipeline copyPipelineWithShaders(
            RenderPipeline original, Identifier vertexShader, Identifier fragmentShader,
            boolean useNoiseSampler, boolean useShadowSampler) {
        return copyPipelineWithShaders(original, vertexShader, fragmentShader, useNoiseSampler, useShadowSampler, false, false, false);
    }

    private static RenderPipeline copyPipelineWithShaders(
            RenderPipeline original, Identifier vertexShader, Identifier fragmentShader,
            boolean useNoiseSampler, boolean useShadowSampler, boolean useSpecularSampler, boolean useShadowColor, boolean useOpaqueShadow) {
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
        if (useNoiseSampler && !original.getSamplers().contains(VeyraNoiseTexture.SAMPLER_NAME)) {
            builder.withSampler(VeyraNoiseTexture.SAMPLER_NAME);
        }
        if (useShadowSampler && !original.getSamplers().contains("Sampler3")) {
            builder.withSampler("Sampler3");
        }
        if (useSpecularSampler && !original.getSamplers().contains(VeyraSpecularTextures.SAMPLER))
            builder.withSampler(VeyraSpecularTextures.SAMPLER);
        if (useOpaqueShadow && !original.getSamplers().contains(ShadowDepthSampler.NAME)) builder.withSampler(ShadowDepthSampler.NAME);
        if (useShadowColor && !original.getSamplers().contains(ShadowColorSampler.NAME)) builder.withSampler(ShadowColorSampler.NAME);
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
        return copyShadowPipelineWithShaders(original, location, vertexShader, fragmentShader, false);
    }

    private static RenderPipeline copyShadowPipelineWithShaders(
            RenderPipeline original, Identifier location, Identifier vertexShader, Identifier fragmentShader,
            boolean useNoiseSampler) {
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
        if (useNoiseSampler && !original.getSamplers().contains(VeyraNoiseTexture.SAMPLER_NAME)) {
            builder.withSampler(VeyraNoiseTexture.SAMPLER_NAME);
        }
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

    private static boolean updateTerrainLayerMode(ShaderPack pack) {
        if (Initializer.CONFIG == null) {
            return false;
        }
        boolean needsSeparateOpaqueLayers = pack != null
                && (pack.terrainPrograms().contains("gbuffers_terrain_solid")
                || pack.terrainPrograms().contains("gbuffers_terrain_cutout"));
        if (!needsSeparateOpaqueLayers) {
            return restoreUniqueOpaqueLayer();
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
            return true;
        }
        return false;
    }

    private static boolean restoreUniqueOpaqueLayer() {
        if (previousUniqueOpaqueLayer == null || Initializer.CONFIG == null) {
            return false;
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
        return changed;
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
                    Set<Identifier> sampledTargets = new java.util.LinkedHashSet<>();
                    sampledTargets.add(inputColortex0);
                    List<PostPass.Input> inputs = new ArrayList<>();
                    inputs.add(new PostPass.TargetInput("In", inputColortex0, false, true));
                    for (int samplerIndex : program.colorSamplers()) {
                        if (samplerIndex == 0) {
                            continue;
                        }
                        inputs.add(new PostPass.TargetInput(
                                colorTargetInputName(samplerIndex), currentTargets.get(samplerIndex), false, true));
                        sampledTargets.add(currentTargets.get(samplerIndex));
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
                    for (PostShadowSamplers.Input input : PostShadowSamplers.Input.values())
                        if (program.shadowInputs().contains(input)) inputs.add(new ExternalTextureInput(input.inputName()));
                    if (program.fragmentSource().contains("uniform sampler2D "
                            + VeyraNoiseTexture.SAMPLER_NAME + ";")) {
                        inputs.add(new ExternalTextureInput(NOISE_INPUT_NAME));
                    }
                    PostPass pass = new PostPass(program.pipeline(),
                            outputTargets.get(program.outputTarget()),
                            passUniforms(program, width, height, FrameUniforms.ZERO), inputs);
                    passes.add(new ShaderPass(pass, program, Set.copyOf(sampledTargets)));
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
            return new FramePlan(List.copyOf(passes), copyPass, copyPass == null ? null : finalColortex0,
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
            boolean usesNoiseSampler = shader.source().contains("uniform sampler2D "
                    + VeyraNoiseTexture.SAMPLER_NAME + ";");
            return new PipelineProgram(shaderId, shader.source(), vertexShaderId, vertexSource, shader.uniforms(),
                    shader.usesDepthSampler(), shader.usesDepth1Sampler(), shader.usesDepth2Sampler(),
                    shader.usesShadowSampler(), shader.shadowInputs(),
                    shader.colorSamplers(), shader.outputTarget(),
                    createPipeline(pipelineId, shaderId, vertexShaderId, shader.uniforms(),
                            shader.usesDepthSampler(), shader.usesDepth1Sampler(), shader.usesDepth2Sampler(),
                            shader.shadowInputs(),
                            usesNoiseSampler,
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
            Set<PostShadowSamplers.Input> shadowInputs,
            boolean usesNoiseSampler,
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
        for (PostShadowSamplers.Input input : PostShadowSamplers.Input.values())
            if (shadowInputs.contains(input)) builder.withSampler(input.samplerName());
        if (usesNoiseSampler) {
            builder.withSampler(VeyraNoiseTexture.SAMPLER_NAME);
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
        PostShadowSamplers.Adapted shadows = PostShadowSamplers.adapt(body, shadowPassAvailable);
        body = shadows.source();
        boolean usesNoiseSampler = NOISETEX_REFERENCE.matcher(body).find();
        boolean usesShadowSampler = !shadows.inputs().isEmpty();
        Matcher noiseSamplerDeclaration = NOISETEX_UNIFORM_DECLARATION.matcher(body);
        StringBuffer bodyWithoutNoiseSampler = new StringBuffer();
        while (noiseSamplerDeclaration.find()) {
            if (!noiseSamplerDeclaration.group(1).equals("sampler2D")) {
                throw new IOException("noisetex must be declared as sampler2D.");
            }
            noiseSamplerDeclaration.appendReplacement(bodyWithoutNoiseSampler, "");
        }
        noiseSamplerDeclaration.appendTail(bodyWithoutNoiseSampler);
        body = bodyWithoutNoiseSampler.toString();
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
        PostShadowSamplers.validateBudget(shadows.inputs(), 1 + (usesDepthSampler ? 1 : 0)
                + (usesDepth1Sampler ? 1 : 0) + (usesDepth2Sampler ? 1 : 0) + (usesNoiseSampler ? 1 : 0)
                + (int) colorSamplers.stream().filter(index -> index > 0).count());
        String uniformSource = body;
        List<String> uniforms = availableUniforms().stream()
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
        body = IRIS_EXTRA_UNIFORM_DECLARATION.matcher(body).replaceAll("");
        body = stripCustomUniformDeclarations(body);
        body = SUPPORTED_IVEC2_UNIFORM_DECLARATION.matcher(body).replaceAll("");
        body = SUPPORTED_MATRIX_UNIFORM_DECLARATION.matcher(body).replaceAll("");
        body = SUPPORTED_CAMERA_UNIFORM_DECLARATION.matcher(body).replaceAll("");
        body = LOCATION_LAYOUT.matcher(body).replaceAll("");
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
            adapted.append(PostShadowSamplers.bridge(shadows.inputs()));
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
            if (usesNoiseSampler) {
                adapted.append("uniform sampler2D ").append(VeyraNoiseTexture.SAMPLER_NAME).append(";\n")
                        .append("#define noisetex ").append(VeyraNoiseTexture.SAMPLER_NAME).append('\n');
            }
            adapted.append(outputBody);
            adaptedShaders.add(new AdaptedShader(adapted.toString(), uniforms,
                    usesDepthSampler, usesDepth1Sampler, usesDepth2Sampler, usesShadowSampler, shadows.inputs(),
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
                case "near" -> new UniformValue.FloatUniform(IRIS_NEAR_PLANE);
                case "far" -> new UniformValue.FloatUniform(farPlane());
                case "darknessLightFactor" -> new UniformValue.FloatUniform(darknessLightFactor);
                case "playerMood" -> new UniformValue.FloatUniform(playerMood());
                case "biome_precipitation" -> new UniformValue.IntUniform(biomePrecipitation());
                case "playerLookVector" -> new UniformValue.Vec3Uniform(frameUniforms.camera().playerLookVector());
                case "skyColor" -> new UniformValue.Vec3Uniform(frameUniforms.camera().skyColor());
                case "lightningBoltPosition" -> new UniformValue.Vec4Uniform(frameUniforms.camera().lightningBoltPosition());
                case "heldBlockLightValue" -> new UniformValue.IntUniform(
                        heldBlockLightValue(net.minecraft.world.InteractionHand.MAIN_HAND));
                case "heldBlockLightValue2" -> new UniformValue.IntUniform(
                        heldBlockLightValue(net.minecraft.world.InteractionHand.OFF_HAND));
                case "heldItemId" -> new UniformValue.IntUniform(
                        heldItemId(net.minecraft.world.InteractionHand.MAIN_HAND));
                case "heldItemId2" -> new UniformValue.IntUniform(
                        heldItemId(net.minecraft.world.InteractionHand.OFF_HAND));
                case "viewWidth" -> new UniformValue.FloatUniform(width);
                case "viewHeight" -> new UniformValue.FloatUniform(height);
                case "aspectRatio" -> new UniformValue.FloatUniform((float) width / height);
                case "frameTimeCounter" -> new UniformValue.FloatUniform(frameUniforms.frameTimeCounter());
                case "frameTime" -> new UniformValue.FloatUniform(frameUniforms.frameTime());
                case "frameCounter" -> new UniformValue.IntUniform(frameUniforms.frameCounter());
                case "sunAngle" -> new UniformValue.FloatUniform(frameUniforms.sunAngle());
                case "shadowFade" -> new UniformValue.FloatUniform(shadowFadeUniform(frameUniforms));
                case "velocity" -> new UniformValue.FloatUniform(
                        packOverridableFloat("velocity", cameraVelocity(frameUniforms.camera())));
                case "rainStrength" -> new UniformValue.FloatUniform(frameUniforms.rainStrength());
                case "wetness" -> new UniformValue.FloatUniform(frameUniforms.wetness());
                case "thunderStrength" -> new UniformValue.FloatUniform(frameUniforms.thunderStrength());
                case "worldTime" -> new UniformValue.IntUniform(frameUniforms.worldTime());
                case "worldDay" -> new UniformValue.IntUniform(frameUniforms.worldDay());
                case "moonPhase" -> new UniformValue.IntUniform(frameUniforms.moonPhase());
                case "bedrockLevel" -> new UniformValue.IntUniform(frameUniforms.bedrockLevel());
                case "cloudHeight" -> new UniformValue.FloatUniform(frameUniforms.cloudHeight());
                case "endFlashIntensity" -> new UniformValue.FloatUniform(frameUniforms.endFlashIntensity());
                case "eyeAltitude" -> new UniformValue.FloatUniform(frameUniforms.eyeAltitude());
                case "eyeBrightness" -> new ShaderPackIVec2Uniform(frameUniforms.camera().eyeBrightness());
                case "eyeBrightnessSmooth" -> new ShaderPackIVec2Uniform(frameUniforms.camera().eyeBrightnessSmooth());
                case "atlasSize" -> new ShaderPackIVec2Uniform(new Vector2i(blockAtlasWidth, blockAtlasHeight));
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
                case "eyePosition" -> new UniformValue.Vec3Uniform(frameUniforms.camera().eyePosition());
                case "relativeEyePosition" -> new UniformValue.Vec3Uniform(frameUniforms.camera().relativeEyePosition());
                default -> IrisExtraUniforms.contains(uniform) ? irisExtraUniformValue(uniform)
                        : customUniformValue(uniform);
            });
        }
        return List.copyOf(values);
    }

    /** Removes declarations of active shaders.properties uniforms; the generated block provides them. */
    static String stripCustomUniformDeclarations(String body) throws IOException {
        Matcher matcher = POST_VERTEX_UNIFORM_DECLARATION.matcher(body);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            String name = matcher.group(2);
            CustomUniforms.Uniform custom = SUPPORTED_UNIFORMS.contains(name) ? null
                    : CustomUniforms.active().uniform(name);
            if (custom == null) {
                String reason = CustomUniforms.active().unavailableReason(name);
                if (reason != null) {
                    throw new IOException("Custom uniform '" + name + "' from shaders.properties is unavailable: "
                            + reason + ".");
                }
                continue;
            }
            String declared = matcher.group(1);
            boolean valid = custom.type() == CustomUniforms.Type.BOOL
                    ? declared.equals("bool") || declared.equals("int")
                    : declared.equals(custom.type().glsl());
            if (!valid) {
                throw new IOException("Custom uniform '" + name + "' is declared as " + declared
                        + " but shaders.properties defines it as " + custom.type().glsl() + ".");
            }
            matcher.appendReplacement(result, "");
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private static UniformValue customUniformValue(String uniform) {
        CustomUniforms.Uniform custom = CustomUniforms.active().uniform(uniform);
        if (custom == null) {
            throw new IllegalArgumentException("Unknown shader pack uniform " + uniform + ".");
        }
        return custom.type() == CustomUniforms.Type.FLOAT
                ? new UniformValue.FloatUniform(CustomUniforms.active().floatValue(uniform))
                : new UniformValue.IntUniform(CustomUniforms.active().intValue(uniform));
    }

    private static boolean usesDynamicUniforms(PipelineProgram program) {
        // Custom uniforms are re-evaluated every frame.
        return program.uniforms().stream().anyMatch(uniform -> DYNAMIC_UNIFORMS.contains(uniform)
                || !SUPPORTED_UNIFORMS.contains(uniform));
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
        float partialTick = minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        long dayTime = minecraft.level.getDayTime();
        int worldTime = irisWorldTime(minecraft.level, dayTime);
        MoonPhase moonPhaseValue = minecraft.level.environmentAttributes()
                .getDimensionValue(EnvironmentAttributes.MOON_PHASE);
        int moonPhase = moonPhaseValue.index();
        float cloudHeight = minecraft.level.environmentAttributes()
                .getDimensionValue(EnvironmentAttributes.CLOUD_HEIGHT);
        var endFlashState = minecraft.level.endFlashState();
        float endFlashIntensity = endFlashState == null ? 0.0F : endFlashState.getIntensity(partialTick);
        float currentSunAngle = minecraft.gameRenderer.getMainCamera().attributeProbe()
                .getValue(EnvironmentAttributes.SUN_ANGLE, partialTick);
        // Iris CelestialUniforms: the sun attribute rotated by 90 degrees, as a fraction of a turn.
        float sunAngle = irisCelestialAngle(currentSunAngle) / 360.0F;
        float currentMoonAngle = minecraft.gameRenderer.getMainCamera().attributeProbe()
                .getValue(EnvironmentAttributes.MOON_ANGLE, partialTick);
        float shadowFade = IrisShadowFade.calculate(currentSunAngle, currentMoonAngle);
        int worldDay = (int) (dayTime / 24000L);
        int bedrockLevel = minecraft.level.getMinY();
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
        return new FrameUniforms(frameTimeCounter, frameTime, frameCounter, sunAngle, shadowFade, rainStrength, wetness,
                thunderStrength, worldTime, worldDay, moonPhase, bedrockLevel, cloudHeight, endFlashIntensity,
                (float) player.getEyeY(),
                (float) (double) minecraft.options.gamma().get(), blindness, nightVision, darknessFactor,
                isEyeInWater, player.isSpectator(), player.isCrouching(), player.isSprinting(), player.onGround(),
                player.hurtTime > 0, player.isInvisible(), player.isOnFire(), frameMatrices, cameraUniforms);
    }

    private static void updateFrameClock() {
        long now = System.nanoTime();
        frameTime = previousFrameNanos == 0L ? 0.0F
                : Math.max(0.0F, (now - previousFrameNanos) / 1_000_000_000.0F);
        previousFrameNanos = now;
        frameTimeCounter = (frameTimeCounter + frameTime) % 3600.0F;
        frameCounter = frameCounter >= 720719 ? 0 : frameCounter + 1;
    }

    private static float selectedEyeBrightnessHalfLifeTicks() {
        ShaderPack pack = selectedPack().orElse(null);
        if (pack == eyeBrightnessHalfLifePack) {
            return eyeBrightnessHalfLifeTicks;
        }
        eyeBrightnessHalfLifePack = pack;
        if (pack == null) {
            eyeBrightnessHalfLifeTicks = EyeBrightnessSmoother.DEFAULT_HALF_LIFE_TICKS;
            return eyeBrightnessHalfLifeTicks;
        }
        try {
            eyeBrightnessHalfLifeTicks = pack.readEyeBrightnessHalflife();
        } catch (IOException exception) {
            eyeBrightnessHalfLifeTicks = EyeBrightnessSmoother.DEFAULT_HALF_LIFE_TICKS;
            VeyraClient.LOGGER.warn("Could not read eyeBrightnessHalflife; using Iris' default of ten ticks.", exception);
        }
        return eyeBrightnessHalfLifeTicks;
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
        geometryColorBindings.forEach((slot, binding) -> {
            if (VRenderSystem.getShaderTexture(slot) == binding.view()) VRenderSystem.setShaderTexture(slot, null);
            if (net.vulkanmod.vulkan.texture.VTextureSelector.getImage(slot) == binding.image())
                net.vulkanmod.vulkan.texture.VTextureSelector.bindTexture(slot, null);
        });
        geometryColorBindings.clear();
        colorTargets.values().forEach(pair -> {
            pair.targetA().destroyBuffers();
            pair.targetB().destroyBuffers();
        });
        colorTargets.clear();
        initializedColorTargets.clear();
        currentColorTargets.clear();
    }

    public static net.vulkanmod.vulkan.texture.VulkanImage geometryColorAttachment(int index) {
        return ((VkGpuTexture) geometryColorTarget(index).getColorTexture()).getVulkanImage();
    }

    private static RenderTarget geometryColorTarget(int index) {
        ColorTargetPair pair = colorTargets.get(index);
        if (pair == null) throw new IllegalStateException("Geometry colortex" + index + " has not been allocated.");
        Identifier side = currentColorTargets.get(index);
        if (side == null) throw new IllegalStateException("Geometry color buffers have not been initialized for this frame.");
        return colorTargetAId(index).equals(side) ? pair.targetA() : pair.targetB();
    }

    /** End attachment writes before sampling the current color-buffer sides in water geometry. */
    public static boolean prepareTerrainColorSamplers(GraphicsPipeline pipeline) {
        Renderer renderer = Renderer.getInstance();
        boolean endedPass = false;
        for (int index = 1; index <= MAX_COLORTEX_INDEX; index++) {
            var descriptor = pipeline.getImageDescriptor(GeometryColorSamplers.samplerName(index));
            if (descriptor == null) continue;
            var target = geometryColorTarget(index);
            var image = ((VkGpuTexture) target.getColorTexture()).getVulkanImage();
            if (image.getCurrentLayout() != VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL) {
                if (renderer.getBoundRenderPass() != null) {
                    renderer.endRenderPass();
                    endedPass = true;
                }
                try (MemoryStack stack = MemoryStack.stackPush()) {
                    image.transitionImageLayout(stack, Renderer.getCommandBuffer(), VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL);
                }
            }
            image.setSampler(net.vulkanmod.vulkan.texture.SamplerManager.getSampler(true, false, 0));
            VRenderSystem.setShaderTexture(descriptor.imageIdx, target.getColorTextureView());
            net.vulkanmod.vulkan.texture.VTextureSelector.bindTexture(descriptor.imageIdx, image);
            geometryColorBindings.put(descriptor.imageIdx, new GeometryColorBinding(target.getColorTextureView(), image));
        }
        return endedPass;
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
            clearColorTarget(commandEncoder, pair.targetA(), targetSettings.clearColor());
            clearColorTarget(commandEncoder, pair.targetB(), targetSettings.clearColor());
            initializedColorTargets.add(index);
        }
    }

    private static void clearColorTarget(com.mojang.blaze3d.systems.CommandEncoder commandEncoder,
                                         RenderTarget target, int clearColor) {
        GpuTexture texture = target.getColorTexture();
        commandEncoder.clearColorTexture(texture, clearColor);
        if (!(texture instanceof VkGpuTexture vulkanTexture)) {
            throw new IllegalStateException("VulkanMod did not allocate a Vulkan texture for shaderpack color targets.");
        }
        // VulkanMod clears immediately here but marks the texture for another deferred clear.
        // PostPassM would then open it as an attachment and transition it on ImageUploadHelper's
        // command buffer while the shader pass samples it. The clear already happened, so drop
        // that stale deferred-clear request.
        ((VkGpuTextureAccessor) vulkanTexture).veyra$setNeedsClear(false);
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
        if ((next == null || !next.usesDepth1Sampler()) && !terrainUsesOpaqueDepthSampler()) {
            closeOpaqueDepthSnapshot();
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
        if (!terrainUsesOpaqueDepthSampler() && (pipelineSet == null || !pipelineSet.usesDepth1Sampler())) {
            closeOpaqueDepthSnapshot();
        }
    }

    private static void replaceCloudPipeline(GraphicsPipeline next) {
        GraphicsPipeline old = cloudPipeline;
        cloudPipeline = next;
        cloudPipelineUsesSceneColor = next != null && next.getImageDescriptor(CLOUD_SCENE_SAMPLER_NAME) != null;
        cloudSceneColorCaptured = false;
        if (!cloudPipelineUsesSceneColor) cloudSceneColorSnapshot.close();
        if (old != null && old != next) {
            old.scheduleCleanUp();
        }
    }

    private static void replaceEntityPipelines(EntityPipelineSet next) {
        ROUTED_RENDER_TYPE_CACHE.clear();
        entityPipelineSet = next;
    }

    private static void replaceBlockEntityPipelines(EntityPipelineSet next) {
        ROUTED_RENDER_TYPE_CACHE.clear();
        blockEntityPipelineSet = next;
        blockEntityPipelineRoutingLogged = false;
    }

    private static void replaceHandPipelines(EntityPipelineSet next) {
        ROUTED_RENDER_TYPE_CACHE.clear();
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
                                 boolean usesShadowSampler, Set<PostShadowSamplers.Input> shadowInputs,
                                 Set<Integer> colorSamplers, int outputTarget) {
    }

    private record ExplicitOutput(int location, String name) {
    }

    private record DepthSamplerState(VulkanImage sourceImage, VulkanImage sampledImage) {
    }

    private record PipelineProgram(Identifier shaderId, String fragmentSource,
                                   Identifier vertexShaderId, String vertexSource, List<String> uniforms,
                                   boolean usesDepthSampler, boolean usesDepth1Sampler, boolean usesDepth2Sampler,
                                   boolean usesShadowSampler, Set<PostShadowSamplers.Input> shadowInputs,
                                   Set<Integer> colorSamplers,
                                   int outputTarget, RenderPipeline pipeline) {
    }

    private record EntityPipelineKey(RenderPipeline original, String programName, String sourceHash,
                                    FragmentOutputs outputLayout, GeometryBlendSettings blendSettings,
                                    boolean shadowPass) {
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

    private record GeometryColorBinding(GpuTextureView view, VulkanImage image) { }

    private record OutputMapping(int fragmentOutputIndex, int targetIndex) {
    }

    private record ShaderPass(PostPass pass, PipelineProgram program, Set<Identifier> sampledTargets) {
    }

    private record FrameUniforms(float frameTimeCounter, float frameTime, int frameCounter,
                                 float sunAngle, float shadowFade, float rainStrength, float wetness, float thunderStrength,
                                 int worldTime, int worldDay, int moonPhase, int bedrockLevel,
                                 float cloudHeight, float endFlashIntensity,
                                 float eyeAltitude, float screenBrightness,
                                 float blindness, float nightVision, float darknessFactor,
                                 int isEyeInWater, boolean isSpectator, boolean isSneaking, boolean isSprinting,
                                 boolean isOnGround, boolean isHurt, boolean isInvisible, boolean isBurning,
                                 FrameMatrices matrices, CameraUniforms camera) {
        private static final FrameUniforms ZERO = new FrameUniforms(0.0F, 0.0F, 0,
                0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0, 0, 0, 0, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0,
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
                                  Vector3f previousCameraPositionFract, Vector2i eyeBrightness, Vector2i eyeBrightnessSmooth,
                                  Vector3f eyePosition, Vector3f relativeEyePosition,
                                  Vector3f playerLookVector, Vector3f skyColor, Vector4f lightningBoltPosition) {
        private static final CameraUniforms ZERO = new CameraUniforms(
                new Vector3f(), new Vector3i(), new Vector3f(),
                new Vector3f(), new Vector3i(), new Vector3f(), new Vector2i(), new Vector2i(),
                new Vector3f(), new Vector3f(), new Vector3f(), new Vector3f(), new Vector4f());
    }

    private record FramePlan(List<ShaderPass> shaderPasses, PostPass copyPass, Identifier copyInputTarget,
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

    private static final class EntityPipelineSet {
        private final Map<RenderPipeline, RenderPipeline> pipelines;
        private final Map<RenderPipeline, EntityPipelineSpec> variantSources;
        private final Map<RenderPipeline, Map<EntityIdentifierPair, RenderPipeline>> variants = new IdentityHashMap<>();
        private final Map<RenderPipeline, Set<EntityIdentifierPair>> failedVariants = new IdentityHashMap<>();

        private EntityPipelineSet(Map<RenderPipeline, RenderPipeline> pipelines,
                                  Map<RenderPipeline, EntityPipelineSpec> variantSources) {
            this.pipelines = pipelines;
            this.variantSources = variantSources;
        }

        private Map<RenderPipeline, RenderPipeline> pipelines() {
            return pipelines;
        }

        private Map<RenderPipeline, EntityPipelineSpec> variantSources() {
            return variantSources;
        }

        private Map<RenderPipeline, Map<EntityIdentifierPair, RenderPipeline>> variants() {
            return variants;
        }

        private Map<RenderPipeline, Set<EntityIdentifierPair>> failedVariants() {
            return failedVariants;
        }

        private Set<RenderPipeline> allPipelines() {
            Set<RenderPipeline> all = Collections.newSetFromMap(new IdentityHashMap<>());
            all.addAll(pipelines.values());
            variants.values().forEach(byIdentifier -> all.addAll(byIdentifier.values()));
            return all;
        }
    }

    private record EntityIdentifierPair(int entityId, int blockEntityId, int currentRenderedItemId,
                                        boolean isMainHand) {
    }

    private record EntityPipelineSpec(String programName, String fragmentSource,
                                      boolean requiresNormal, boolean requiresLightmap,
                                      boolean requiresViewPosition, boolean requiresViewNormal,
                                      boolean shadowPass, boolean handPass, boolean textPipeline,
                                      boolean shadowSamplerAvailable, FragmentOutputs outputLayout,
                                      GeometryBlendSettings blendSettings) {
        private boolean usesEntityId() {
            return EntityShaderAdapter.usesEntityIdentifier(fragmentSource, programName, "entityId");
        }

        private boolean usesBlockEntityId() {
            return EntityShaderAdapter.usesEntityIdentifier(fragmentSource, programName, "blockEntityId");
        }

        private boolean usesCurrentRenderedItemId() {
            return EntityShaderAdapter.usesEntityIdentifier(
                    fragmentSource, programName, "currentRenderedItemId");
        }

        private boolean usesIsMainHand() {
            return handPass && EntityShaderAdapter.requiresMainHandInput(fragmentSource);
        }
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
            if (samplerName.equals(NOISE_INPUT_NAME)) {
                return VeyraNoiseTexture.view();
            }
            if (samplerName.equals(DEPTH1_INPUT_NAME)) {
                return depthTex1Snapshot.sampledView();
            }
            if (samplerName.equals(DEPTH2_INPUT_NAME)) {
                return depthTex2Snapshot.sampledView();
            }
            if (samplerName.equals(PostShadowSamplers.Input.OPAQUE_DEPTH.inputName()))
                return VeyraShadowRenderer.opaqueShadowDepthTextureView();
            if (samplerName.equals(PostShadowSamplers.Input.COLOR.inputName()))
                return VeyraShadowRenderer.shadowColorTextureView();
            if (samplerName.equals(PostShadowSamplers.Input.DEPTH.inputName())) {
                return VeyraShadowRenderer.shadowDepthTextureView();
            }
            return sampledDepthTextureView();
        }

        @Override
        public boolean bilinear() {
            return PostShadowSamplers.bilinear(samplerName);
        }
    }

}
