package dev.veyra.compat;

import dev.veyra.mixin.accessor.BooleanStateShardAccessor;
import dev.veyra.mixin.accessor.CompositeStateAccessor;
import dev.veyra.mixin.accessor.DepthTestStateShardAccessor;
import dev.veyra.mixin.accessor.RenderStateShardAccessor;
import dev.veyra.mixin.accessor.RenderTypeAccessor;
import dev.veyra.mixin.accessor.ShaderStateShardAccessor;
import dev.veyra.mixin.accessor.WriteMaskStateShardAccessor;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderProgram;
import net.minecraft.client.renderer.ShaderProgramConfig;
import net.minecraft.resources.ResourceLocation;

/**
 * Describes vanilla render types as the pipelines Minecraft 1.21.5 later made of them. A render type
 * here is a name, a shader program and render state; its 1.21.5 pipeline has the same name, so the
 * shared renderer can classify draws on every version by pipeline location.
 */
public final class VanillaPipelines {
    private static final ResourceLocation TEXTURE = ResourceLocation.withDefaultNamespace("textures/misc/white.png");
    private static final Map<RenderType, RenderPipeline> BY_TYPE = new IdentityHashMap<>();
    private static final Map<String, RenderPipeline> BY_KEY = new LinkedHashMap<>();
    private static final Map<RenderType, RenderPipeline> FORCED = new IdentityHashMap<>();
    // A 1.21.4 shader id to the merged shader 1.21.5 draws the same render types with.
    private static final Map<ResourceLocation, String> MODERN_SHADERS = new java.util.concurrent.ConcurrentHashMap<>();
    private static Object describedConfigs;

    private VanillaPipelines() { }

    /** Every pipeline for the render types vanilla creates, like the later static pipeline list. */
    public static synchronized List<RenderPipeline> all() {
        dropStaleDescriptions();
        for (RenderType type : VanillaRenderTypes.types()) of(type);
        VanillaRenderTypes.programPipelines();
        return new ArrayList<>(BY_KEY.values());
    }

    /** Makes draws of {@code type} use {@code pipeline} without consulting the routing chain. */
    public static synchronized void force(RenderType type, RenderPipeline pipeline) { FORCED.put(type, pipeline); }

    public static synchronized RenderPipeline forced(RenderType type) { return FORCED.get(type); }

    /** The pipeline description of a composite render type, or null when it has no shader program. */
    public static synchronized RenderPipeline of(RenderType type) {
        dropStaleDescriptions();
        RenderPipeline known = BY_TYPE.get(type);
        if (known != null || BY_TYPE.containsKey(type)) return known;
        RenderPipeline described = describe(type);
        BY_TYPE.put(type, described);
        return described;
    }

    // Program configs are reloaded with resources; descriptions made from the old ones would be stale.
    private static void dropStaleDescriptions() {
        Object configs = ShaderSources.configs();
        if (configs == describedConfigs) return;
        describedConfigs = configs;
        BY_TYPE.clear();
        BY_KEY.clear();
    }

    private static RenderPipeline describe(RenderType type) {
        if (!(type instanceof RenderTypeAccessor composite)) return null;
        var state = (CompositeStateAccessor) (Object) composite.veyra$getSetup();
        ShaderProgram program = ((ShaderStateShardAccessor) state.veyra$getShaderState()).veyra$getShader().orElse(null);
        if (program == null) return null;
        ShaderProgramConfig config = ShaderSources.program(program.configId());
        if (config == null) return null;

        String name = ((RenderStateShardAccessor) type).veyra$getName();
        boolean blend = !"no_transparency".equals(
                ((RenderStateShardAccessor) state.veyra$getTransparencyState()).veyra$getName());
        var writeMask = (WriteMaskStateShardAccessor) state.veyra$getWriteMaskState();
        boolean cull = ((BooleanStateShardAccessor) state.veyra$getCullState()).veyra$isEnabled();
        String depth = ((DepthTestStateShardAccessor) state.veyra$getDepthTestState()).veyra$getFunctionName();
        String location = location(name, blend, writeMask.veyra$writesDepth());
        String modern = modernShader(location);
        if (modern != null) {
            MODERN_SHADERS.put(config.vertex(), modern);
            MODERN_SHADERS.put(config.fragment(), modern);
        }
        String key = location + '|' + program.configId() + '|' + blend + '|' + cull + '|' + depth + '|'
                + writeMask.veyra$writesColor() + writeMask.veyra$writesDepth() + '|' + type.format() + '|' + type.mode();
        RenderPipeline existing = BY_KEY.get(key);
        if (existing != null) return existing;

        RenderPipeline.Builder builder = RenderPipeline.builder()
                .withLocation(ResourceLocation.withDefaultNamespace("pipeline/" + location))
                .withVertexShader(config.vertex())
                .withFragmentShader(config.fragment())
                .withVertexFormat(type.format(), type.mode())
                .withCull(cull)
                .withColorWrite(writeMask.veyra$writesColor())
                .withDepthWrite(writeMask.veyra$writesDepth())
                .withDepthTestFunction(switch (depth) {
                    case "always" -> DepthTestFunction.NO_DEPTH_TEST;
                    case "==" -> DepthTestFunction.EQUAL_DEPTH_TEST;
                    case ">" -> DepthTestFunction.GREATER_DEPTH_TEST;
                    default -> DepthTestFunction.LEQUAL_DEPTH_TEST;
                });
        if (blend) builder.withBlend(BlendFunction.TRANSLUCENT);
        var defines = config.defines().withOverrides(program.defines());
        defines.values().forEach((define, value) -> builder.withShaderDefine(define, value));
        defines.flags().forEach(builder::withShaderDefine);
        entityDefines(location, builder);
        config.samplers().forEach(sampler -> builder.withSampler(sampler.name()));
        for (ShaderProgramConfig.Uniform uniform : config.uniforms()) {
            builder.withUniform(uniform.name(), uniformType(uniform));
        }
        RenderPipeline pipeline = builder.build();
        BY_KEY.put(key, pipeline);
        return pipeline;
    }

    /**
     * Describes a draw that binds a shader program without a render type. The state fields only
     * classify the pipeline; the draw keeps the render state its caller set.
     */
    public static synchronized RenderPipeline ofProgram(String location, ShaderProgram program,
            com.mojang.blaze3d.vertex.VertexFormat.Mode mode, boolean blend) {
        dropStaleDescriptions();
        String key = location + '|' + program.configId() + '|' + mode;
        RenderPipeline existing = BY_KEY.get(key);
        if (existing != null) return existing;
        ShaderProgramConfig config = ShaderSources.program(program.configId());
        if (config == null) return null;
        String modern = modernShader(location);
        if (modern != null) {
            MODERN_SHADERS.put(config.vertex(), modern);
            MODERN_SHADERS.put(config.fragment(), modern);
        }
        RenderPipeline.Builder builder = RenderPipeline.builder()
                .withLocation(ResourceLocation.withDefaultNamespace("pipeline/" + location))
                .withVertexShader(config.vertex())
                .withFragmentShader(config.fragment())
                .withVertexFormat(program.vertexFormat(), mode)
                .withDepthWrite(false);
        if (blend) builder.withBlend(BlendFunction.TRANSLUCENT);
        var defines = config.defines().withOverrides(program.defines());
        defines.values().forEach((define, value) -> builder.withShaderDefine(define, value));
        defines.flags().forEach(builder::withShaderDefine);
        config.samplers().forEach(sampler -> builder.withSampler(sampler.name()));
        for (ShaderProgramConfig.Uniform uniform : config.uniforms()) builder.withUniform(uniform.name(), uniformType(uniform));
        RenderPipeline pipeline = builder.build();
        BY_KEY.put(key, pipeline);
        return pipeline;
    }

    /** The 1.21.5 pipeline name of a 1.21.4 render type; most are unchanged. */
    private static String location(String name, boolean blend, boolean writeDepth) {
        return switch (name) {
            case "beacon_beam" -> blend ? "beacon_beam_translucent" : "beacon_beam_opaque";
            case "weather" -> writeDepth ? "weather_depth_write" : "weather_no_depth_write";
            case "entity_alpha" -> "dragon_explosion_alpha";
            case "entity_solid_z_offset_forward" -> "entity_solid_offset_forward";
            case "glint_translucent", "entity_glint", "armor_entity_glint" -> "glint";
            default -> name;
        };
    }

    /**
     * Whether {@code shader} is the vanilla shader 1.21.5 calls {@code modernPath}: the same id, or a
     * 1.21.4 shader of a render type 1.21.5 draws with that merged shader.
     */
    public static boolean shaderIs(ResourceLocation shader, String modernPath) {
        if (shader == null) return false;
        if (shader.getNamespace().equals("minecraft") && shader.getPath().equals(modernPath)) return true;
        return modernPath.equals(MODERN_SHADERS.get(shader));
    }

    /** The 1.21.5 id of a vanilla shader, or {@code shader} itself when 1.21.5 uses the same one. */
    public static ResourceLocation modern(ResourceLocation shader) {
        String modern = shader == null ? null : MODERN_SHADERS.get(shader);
        return modern == null ? shader : ResourceLocation.withDefaultNamespace(modern);
    }

    // The 1.21.5 shader of a pipeline whose 1.21.4 render type had its own shader; null when unchanged.
    private static String modernShader(String location) {
        return switch (location) {
            case "entity_shadow", "entity_decal", "item_entity_translucent_cull", "dragon_explosion_alpha" -> null;
            case "eyes", "breeze_wind", "energy_swirl" -> "core/entity";
            case "opaque_particle", "translucent_particle", "weather_depth_write", "weather_no_depth_write" -> "core/particle";
            case "sky", "stars", "dragon_rays_depth" -> "core/position";
            case "sunrise_sunset" -> "core/position_color";
            case "glint" -> "core/glint";
            default -> location.startsWith("entity_") || location.startsWith("armor_") ? "core/entity" : null;
        };
    }

    // 1.21.5 merged the entity shaders into one source selected by these defines. The 1.21.4 sources
    // are already specialized, so the defines only tell Veyra's generated stages which inputs exist.
    private static void entityDefines(String location, RenderPipeline.Builder builder) {
        switch (location) {
            case "entity_cutout", "entity_cutout_no_cull", "entity_cutout_no_cull_z_offset", "entity_smooth_cutout",
                    "entity_translucent" -> builder.withShaderDefine("ALPHA_CUTOUT", 0.1F);
            case "entity_translucent_emissive" -> builder.withShaderDefine("ALPHA_CUTOUT", 0.1F).withShaderDefine("EMISSIVE");
            case "armor_cutout_no_cull", "armor_decal_cutout_no_cull", "armor_translucent" ->
                    builder.withShaderDefine("ALPHA_CUTOUT", 0.1F).withShaderDefine("NO_OVERLAY");
            case "entity_no_outline" -> builder.withShaderDefine("NO_OVERLAY");
            case "eyes" -> builder.withShaderDefine("EMISSIVE").withShaderDefine("NO_OVERLAY")
                    .withShaderDefine("NO_CARDINAL_LIGHTING");
            default -> { }
        }
    }

    static UniformType uniformType(ShaderProgramConfig.Uniform uniform) {
        return switch (uniform.type()) {
            case "int" -> uniform.count() == 1 ? UniformType.INT : UniformType.IVEC3;
            case "matrix4x4" -> UniformType.MATRIX4X4;
            default -> switch (uniform.count()) {
                case 1 -> UniformType.FLOAT;
                case 2 -> UniformType.VEC2;
                case 3 -> UniformType.VEC3;
                default -> UniformType.VEC4;
            };
        };
    }

}
