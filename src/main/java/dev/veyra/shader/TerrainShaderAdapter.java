package dev.veyra.shader;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Bridges the small, common Iris terrain fragment interface to VulkanMod's compressed vertex format. */
public final class TerrainShaderAdapter {
    private static final Pattern VERSION_DIRECTIVE = Pattern.compile("(?m)^[\\t ]*#version[^\\r\\n]*(?:\\r?\\n|$)");
    private static final Pattern EXTENSION_DIRECTIVE = Pattern.compile("(?m)^[\\t ]*#extension[^\\r\\n]*(?:\\r?\\n|$)");
    private static final Pattern PRECISION_DECLARATION = Pattern.compile("(?m)^[\\t ]*precision\\s+(?:lowp|mediump|highp)\\s+\\w+\\s*;[\\t ]*(?:\\r?\\n|$)");
    private static final Pattern ALPHA_TEST_UNIFORM = Pattern.compile(
            "(?m)^[\\t ]*uniform\\s+(?:(?:lowp|mediump|highp)\\s+)?float\\s+alphaTestRef\\s*;[\\t ]*(?://[^\\r\\n]*)?(?:\\r?\\n|$)");
    private static final Pattern SHADOW_MATRIX_UNIFORM = Pattern.compile(
            "(?m)^[\\t ]*uniform\\s+mat4\\s+(?:shadowModelView|shadowProjection)\\s*;[\\t ]*(?://[^\\r\\n]*)?(?:\\r?\\n|$)");
    private static final Pattern SHADERPACK_UNIFORM_DECLARATION = Pattern.compile(
            "(?m)^[\\t ]*uniform\\s+(?:(?:lowp|mediump|highp)\\s+)?([A-Za-z_][A-Za-z0-9_]*)\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*;[\\t ]*(?://[^\\r\\n]*)?(?:\\r?\\n|$)");
    private static final Pattern SAMPLER_DECLARATION = Pattern.compile(
            "(?m)^[\\t ]*uniform\\s+(?:(?:lowp|mediump|highp)\\s+)?(sampler\\w+)\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*;[\\t ]*(?://[^\\r\\n]*)?(?:\\r?\\n|$)");
    private static final Pattern UNSUPPORTED_UNIFORM_DECLARATION = Pattern.compile("(?m)^[\\t ]*uniform\\b");
    private static final Pattern VARYING_DECLARATION = Pattern.compile(
            "(?m)^[\\t ]*(?:layout\\s*\\(\\s*location\\s*=\\s*(\\d+)\\s*\\)\\s*)?(?:(?:flat|smooth|noperspective)\\s+)?(?:varying|in)\\s+(?:(?:flat|smooth|noperspective)\\s+)?([A-Za-z_][A-Za-z0-9_]*)\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*;[\\t ]*(?://[^\\r\\n]*)?(?:\\r?\\n|$)");
    private static final Pattern COMMA_SEPARATED_VARYING_DECLARATION = Pattern.compile(
            "(?m)^([\\t ]*(?:layout\\s*\\(\\s*location\\s*=\\s*\\d+\\s*\\)\\s*)?(?:(?:flat|smooth|noperspective)\\s+)?(?:varying|in)\\s+(?:(?:flat|smooth|noperspective)\\s+)?[A-Za-z_][A-Za-z0-9_]*\\s+)"
                    + "([A-Za-z_][A-Za-z0-9_]*(?:\\s*,\\s*[A-Za-z_][A-Za-z0-9_]*)+)\\s*;"
                    + "([\\t ]*(?://[^\\r\\n]*)?(?:\\r?\\n|$))");
    private static final Pattern MAIN_FUNCTION_DECLARATION = Pattern.compile(
            "(?m)^([\\t ]*void\\s+)main(\\s*\\()");
    private static final Pattern OUTPUT_DECLARATION = Pattern.compile(
            "(?m)^[\\t ]*(?:layout\\s*\\(\\s*location\\s*=\\s*(\\d+)\\s*\\)\\s*)?out\\s+vec4\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*;[\\t ]*(?://[^\\r\\n]*)?(?:\\r?\\n|$)");
    private static final Pattern UNSUPPORTED_OUTPUT_DECLARATION = Pattern.compile(
            "(?m)^[\\t ]*(?:layout\\s*\\([^)]*\\)\\s*)?out\\s+(?!vec4\\s+[A-Za-z_][A-Za-z0-9_]*\\s*;)[^\\r\\n]*;");
    private static final Pattern UNSUPPORTED_GBUFFER_INPUT = Pattern.compile(
            "\\b(mc_Entity|mc_midTexCoord|midTexCoord|viewNormal|tangent|binormal|viewVector|at_tangent)\\b");
    private static final Pattern UNSUPPORTED_TEXTURE_FUNCTION = Pattern.compile(
            "\\btexture(?:Gather|Offset|Proj|QueryLod)\\w*\\s*\\(");
    private static final Pattern NOISETEX_REFERENCE = Pattern.compile("\\bnoisetex\\b");
    private static final Pattern TERRAIN_VERTEX_OUTPUT_DECLARATION = Pattern.compile(
            "(?m)^([\\t ]*)layout\\s*\\(\\s*location\\s*=\\s*(\\d+)\\s*\\)\\s*out\\s+"
                    + "(?:flat\\s+)?([A-Za-z_][A-Za-z0-9_]*)\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*;");
    private static final Pattern FRAGMENT_INPUT_LOCATION = Pattern.compile(
            "(?m)^\\s*layout\\s*\\(\\s*location\\s*=\\s*(\\d+)\\s*\\)\\s*"
                    + "(?:(?:flat|smooth|noperspective)\\s+)?in\\s+[A-Za-z_][A-Za-z0-9_]*\\s+"
                    + "([A-Za-z_][A-Za-z0-9_]*)\\s*;");
    private static final Pattern TERRAIN_FRAGMENT_BRIDGE_INPUT = Pattern.compile(
            "(?m)^([\\t ]*)layout\\s*\\(\\s*location\\s*=\\s*(\\d+)\\s*\\)\\s*"
                    + "(?:(?:flat|smooth|noperspective)\\s+)?in\\s+[A-Za-z_][A-Za-z0-9_]*\\s+"
                    + "([A-Za-z_][A-Za-z0-9_]*)\\s*;");
    private static final Set<Integer> TERRAIN_VERTEX_OUTPUT_LOCATIONS = Set.of(
            0, 1, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19);
    private static final Set<String> TERRAIN_FRAGMENT_BRIDGE_INPUT_NAMES = Set.of(
            "veyraVertexColor", "veyraTexCoord", "veyraLightmapCoord", "veyraShadowPos",
            "veyraTerrainPosition", "veyraSphericalVertexDistance", "veyraCylindricalVertexDistance",
            "veyraViewPosition", "veyraTerrainMaterialId", "veyraSignMidCoordPos", "veyraMcEntity",
            "veyraAbsMidCoordPos", "veyraMidCoord", "veyraTerrainDistance", "veyraBslTexCoord",
            "veyraBslTexCoordAM", "veyraSpriteBounds");
    private static final Pattern COMMENTS = Pattern.compile("(?s)/\\*.*?\\*/|//[^\\r\\n]*");
    private static final Pattern TOP_LEVEL_VALUE_DECLARATION = Pattern.compile(
            "^[\\t ]*(?:(?:const|invariant|precise|lowp|mediump|highp)\\s+)*(?:double|float|int|uint|bool|[biu]?vec[234]|mat[234](?:x[234])?)\\s+"
                    + "([A-Za-z_][A-Za-z0-9_]*)\\s*(?:\\[[^]]*])?\\s*(?:=[^;]*)?;[\\t ]*$");
    private static final Map<String, Varying> SUPPORTED_VARYINGS = Map.ofEntries(
            Map.entry("texcoord", new Varying("vec2", 1)),
            Map.entry("texCoord", new Varying("vec2", 1)),
            Map.entry("texCoord0", new Varying("vec2", 1)),
            Map.entry("lmcoord", new Varying("vec2", 5)),
            Map.entry("lmCoord", new Varying("vec2", 5)),
            Map.entry("lightMapCoord", new Varying("vec2", 5)),
            Map.entry("color", new Varying("vec4", 0)),
            Map.entry("glColorRaw", new Varying("vec4", 0)),
            Map.entry("glcolor", new Varying("vec4", 0)),
            Map.entry("glColor", new Varying("vec4", 0)),
            Map.entry("gl_Color", new Varying("vec4", 0)),
            Map.entry("shadowPos", new Varying("vec4", 6)),
            Map.entry("shadowCoord", new Varying("vec4", 6)),
            Map.entry("normal", new Varying("vec3", 7)),
            Map.entry("tangent", new Varying("vec3", -1)),
            Map.entry("binormal", new Varying("vec3", -1)),
            Map.entry("viewVector", new Varying("vec3", -1)),
            Map.entry("sunVec", new Varying("vec3", -1)),
            Map.entry("upVec", new Varying("vec3", -1)),
            Map.entry("eastVec", new Varying("vec3", -1)),
            Map.entry("vertexPos", new Varying("vec3", 7)),
            Map.entry("playerPos", new Varying("vec3", 7)),
            Map.entry("viewPos", new Varying("vec3", 10)),
            Map.entry("dist", new Varying("float", 16)),
            Map.entry("recolor", new Varying("float", -1)),
            Map.entry("vTexCoord", new Varying("vec4", 17)),
            Map.entry("vTexCoordAM", new Varying("vec4", 18)),
            Map.entry("spriteBounds", new Varying("vec4", 19)),
            Map.entry("mat", new Varying("int", 11)),
            Map.entry("mc_Entity", new Varying("vec2", 13)),
            Map.entry("signMidCoordPos", new Varying("vec2", 12)),
            Map.entry("absMidCoordPos", new Varying("vec2", 14)),
            Map.entry("midCoord", new Varying("vec2", 15)),
            Map.entry("sphericalVertexDistance", new Varying("float", 8)),
            Map.entry("cylindricalVertexDistance", new Varying("float", 9)));
    private static final List<ShaderUniform> SHADERPACK_UNIFORMS = withIrisExtras(List.of(
            new ShaderUniform("near", "float", false),
            new ShaderUniform("far", "float", false),
            new ShaderUniform("darknessLightFactor", "float", false),
            new ShaderUniform("shadowFade", "float", false),
            new ShaderUniform("velocity", "float", false),
            new ShaderUniform("playerMood", "float", false),
            new ShaderUniform("biome_precipitation", "int", false),
            new ShaderUniform("playerLookVector", "vec3", false),
            new ShaderUniform("skyColor", "vec3", false),
            new ShaderUniform("atlasSize", "ivec2", false),
            new ShaderUniform("lightningBoltPosition", "vec4", false),
            new ShaderUniform("heldBlockLightValue", "int", false),
            new ShaderUniform("heldBlockLightValue2", "int", false),
            new ShaderUniform("heldItemId", "int", false),
            new ShaderUniform("heldItemId2", "int", false),
            new ShaderUniform("viewWidth", "float", false),
            new ShaderUniform("viewHeight", "float", false),
            new ShaderUniform("aspectRatio", "float", false),
            new ShaderUniform("frameTimeCounter", "float", false),
            new ShaderUniform("frameTime", "float", false),
            new ShaderUniform("frameCounter", "int", false),
            new ShaderUniform("sunAngle", "float", false),
            new ShaderUniform("rainStrength", "float", false),
            new ShaderUniform("wetness", "float", false),
            new ShaderUniform("thunderStrength", "float", false),
            new ShaderUniform("worldTime", "int", false),
            new ShaderUniform("worldDay", "int", false),
            new ShaderUniform("moonPhase", "int", false),
            new ShaderUniform("bedrockLevel", "int", false),
            new ShaderUniform("cloudHeight", "float", false),
            new ShaderUniform("endFlashIntensity", "float", false),
            new ShaderUniform("eyeAltitude", "float", false),
            new ShaderUniform("eyeBrightness", "ivec2", false),
            new ShaderUniform("eyeBrightnessSmooth", "ivec2", false),
            new ShaderUniform("cameraPosition", "vec3", false),
            new ShaderUniform("cameraPositionInt", "ivec3", false),
            new ShaderUniform("cameraPositionFract", "vec3", false),
            new ShaderUniform("previousCameraPosition", "vec3", false),
            new ShaderUniform("previousCameraPositionInt", "ivec3", false),
            new ShaderUniform("previousCameraPositionFract", "vec3", false),
            new ShaderUniform("eyePosition", "vec3", false),
            new ShaderUniform("relativeEyePosition", "vec3", false),
            new ShaderUniform("screenBrightness", "float", false),
            new ShaderUniform("blindness", "float", false),
            new ShaderUniform("nightVision", "float", false),
            new ShaderUniform("darknessFactor", "float", false),
            new ShaderUniform("isEyeInWater", "int", false),
            new ShaderUniform("isSpectator", "bool", true),
            new ShaderUniform("is_sneaking", "bool", true),
            new ShaderUniform("is_sprinting", "bool", true),
            new ShaderUniform("is_on_ground", "bool", true),
            new ShaderUniform("is_hurt", "bool", true),
            new ShaderUniform("is_invisible", "bool", true),
            new ShaderUniform("is_burning", "bool", true),
            new ShaderUniform("gbufferModelView", "mat4", false),
            new ShaderUniform("textureMatrix", "mat4", false),
            new ShaderUniform("gbufferModelViewInverse", "mat4", false),
            new ShaderUniform("gbufferProjection", "mat4", false),
            new ShaderUniform("gbufferProjectionInverse", "mat4", false),
            new ShaderUniform("gbufferPreviousModelView", "mat4", false),
            new ShaderUniform("gbufferPreviousProjection", "mat4", false),
            new ShaderUniform("shadowModelViewInverse", "mat4", false),
            new ShaderUniform("shadowProjectionInverse", "mat4", false)));

    private static final String FRAGMENT_BRIDGE = """
            layout(binding = 1) uniform VeyraTerrainUniformBuffer {
                vec4 FogColor;
                float FogEnvironmentalStart;
                float FogEnvironmentalEnd;
                float FogRenderDistanceStart;
                float FogRenderDistanceEnd;
                float FogSkyEnd;
                float FogCloudsEnd;
                float AlphaCutout;
                ivec2 TextureSize;
                vec2 TexelSize;
                int UseRgss;
                mat4 shadowModelView;
                mat4 shadowProjection;
                __VEYRA_SHADERPACK_UNIFORMS__
            };
            layout(binding = 3) uniform sampler2D Sampler0;
            layout(binding = 4) uniform sampler2D LightTexture;
            layout(binding = 5) uniform sampler2D VeyraShadowSampler;
            layout(binding = 6) uniform sampler2D VeyraNoiseSampler;
            layout(location = 0) in vec4 veyraVertexColor;
            layout(location = 1) in vec2 veyraTexCoord;
            layout(location = 5) in vec2 veyraLightmapCoord;
            layout(location = 6) in vec4 veyraShadowPos;
            layout(location = 7) in vec3 veyraTerrainPosition;
            layout(location = 8) in float veyraSphericalVertexDistance;
            layout(location = 9) in float veyraCylindricalVertexDistance;
            layout(location = 10) in vec3 veyraViewPosition;
            layout(location = 11) flat in int veyraTerrainMaterialId;
            layout(location = 12) in vec2 veyraSignMidCoordPos;
            layout(location = 13) flat in vec2 veyraMcEntity;
            layout(location = 14) flat in vec2 veyraAbsMidCoordPos;
            layout(location = 15) flat in vec2 veyraMidCoord;
            layout(location = 16) in float veyraTerrainDistance;
            layout(location = 17) in vec4 veyraBslTexCoord;
            layout(location = 18) in vec4 veyraBslTexCoordAM;
            layout(location = 19) in vec4 veyraSpriteBounds;
            layout(location = 0) out vec4 veyraFragColor;
            vec4 veyraSample(sampler2D sourceSampler, vec2 sourceUv) {
                return texture(sourceSampler, sourceUv);
            }
            vec4 veyraSample(sampler3D sourceSampler, vec3 sourceCoord) {
                return texture(sourceSampler, sourceCoord);
            }
            uvec4 veyraSample(usampler2D sourceSampler, vec2 sourceUv) {
                return texture(sourceSampler, sourceUv);
            }
            uvec4 veyraSample(usampler3D sourceSampler, vec3 sourceCoord) {
                return texture(sourceSampler, sourceCoord);
            }
            vec4 veyraSampleLod(sampler2D sourceSampler, vec2 sourceUv, float lod) {
                return textureLod(sourceSampler, sourceUv, lod);
            }
            vec4 veyraSampleLod(sampler3D sourceSampler, vec3 sourceCoord, float lod) {
                return textureLod(sourceSampler, sourceCoord, lod);
            }
            uvec4 veyraSampleLod(usampler2D sourceSampler, vec2 sourceUv, float lod) {
                return textureLod(sourceSampler, sourceUv, lod);
            }
            uvec4 veyraSampleLod(usampler3D sourceSampler, vec3 sourceCoord, float lod) {
                return textureLod(sourceSampler, sourceCoord, lod);
            }
            vec4 veyraSampleGrad(sampler2D sourceSampler, vec2 sourceUv, vec2 dx, vec2 dy) {
                return textureGrad(sourceSampler, sourceUv, dx, dy);
            }
            vec4 veyraSampleGrad(sampler3D sourceSampler, vec3 sourceCoord, vec3 dx, vec3 dy) {
                return textureGrad(sourceSampler, sourceCoord, dx, dy);
            }
            uvec4 veyraSampleGrad(usampler2D sourceSampler, vec2 sourceUv, vec2 dx, vec2 dy) {
                return textureGrad(sourceSampler, sourceUv, dx, dy);
            }
            uvec4 veyraSampleGrad(usampler3D sourceSampler, vec3 sourceCoord, vec3 dx, vec3 dy) {
                return textureGrad(sourceSampler, sourceCoord, dx, dy);
            }
            vec4 veyraSample(sampler2D sourceSampler, vec3 sourceCoord) {
                float visibility = step(sourceCoord.z - 0.0005, texture(sourceSampler, sourceCoord.xy).r);
                return vec4(visibility);
            }
            #define texture Sampler0
            #define tex Sampler0
            #define gtexture Sampler0
            #define lightmap LightTexture
            #define shadowtex0 VeyraShadowSampler
            #define noisetex VeyraNoiseSampler
            #define shadow2D(sampler, coord) veyraSample(sampler, coord)
            #define alphaTestRef AlphaCutout
            #define gl_FragColor veyraFragColor
            """;

    /** Reconstructs Iris' terrain tangent frame from the rasterized position and UV gradients. */
    private static final String TANGENT_SPACE_BRIDGE = """
            vec3 veyraTerrainNormalValue;
            vec3 veyraTerrainTangentValue;
            vec3 veyraTerrainBinormalValue;
            vec3 veyraTerrainViewVectorValue;
            void veyraInitializeTerrainTangentFrame() {
                vec3 positionDx = dFdx(veyraViewPosition);
                vec3 positionDy = dFdy(veyraViewPosition);
                vec2 texCoordDx = dFdx(veyraTexCoord);
                vec2 texCoordDy = dFdy(veyraTexCoord);
                vec3 faceNormal = cross(positionDx, positionDy);
                if (!gl_FrontFacing) faceNormal = -faceNormal;
                float normalLengthSquared = dot(faceNormal, faceNormal);
                veyraTerrainNormalValue = normalLengthSquared > 1e-12
                        ? faceNormal * inversesqrt(normalLengthSquared) : vec3(0.0, 0.0, 1.0);
                float determinant = texCoordDx.x * texCoordDy.y - texCoordDx.y * texCoordDy.x;
                vec3 fallbackAxis = abs(veyraTerrainNormalValue.z) < 0.999
                        ? vec3(0.0, 0.0, 1.0) : vec3(0.0, 1.0, 0.0);
                vec3 tangentDirection;
                vec3 binormalDirection;
                if (abs(determinant) < 1e-8) {
                    tangentDirection = normalize(cross(fallbackAxis, veyraTerrainNormalValue));
                    binormalDirection = normalize(cross(tangentDirection, veyraTerrainNormalValue));
                } else {
                    tangentDirection = (positionDx * texCoordDy.y - positionDy * texCoordDx.y) / determinant;
                    tangentDirection -= veyraTerrainNormalValue * dot(veyraTerrainNormalValue, tangentDirection);
                    float tangentLengthSquared = dot(tangentDirection, tangentDirection);
                    tangentDirection = tangentLengthSquared > 1e-12
                            ? tangentDirection * inversesqrt(tangentLengthSquared)
                            : normalize(cross(fallbackAxis, veyraTerrainNormalValue));
                    binormalDirection = (positionDy * texCoordDx.x - positionDx * texCoordDy.x) / determinant;
                    binormalDirection -= veyraTerrainNormalValue * dot(veyraTerrainNormalValue, binormalDirection);
                    binormalDirection -= tangentDirection * dot(tangentDirection, binormalDirection);
                    float binormalLengthSquared = dot(binormalDirection, binormalDirection);
                    binormalDirection = binormalLengthSquared > 1e-12
                            ? binormalDirection * inversesqrt(binormalLengthSquared)
                            : normalize(cross(tangentDirection, veyraTerrainNormalValue));
                }
                veyraTerrainTangentValue = tangentDirection;
                veyraTerrainBinormalValue = binormalDirection;
                veyraTerrainViewVectorValue = vec3(
                        dot(tangentDirection, veyraViewPosition),
                        dot(binormalDirection, veyraViewPosition),
                        dot(veyraTerrainNormalValue, veyraViewPosition));
            }
            vec3 veyraTerrainNormal() {
                return veyraTerrainNormalValue;
            }
            vec3 veyraTerrainTangent() {
                return veyraTerrainTangentValue;
            }
            vec3 veyraTerrainBinormal() {
                return veyraTerrainBinormalValue;
            }
            vec3 veyraTerrainViewVector() {
                return veyraTerrainViewVectorValue;
            }
            """;

    private TerrainShaderAdapter() {
    }

    public static String adaptFragment(String source) throws IOException {
        return adaptFragment(source, true);
    }

    public static String adaptFragment(String source, boolean shadowSamplerAvailable) throws IOException {
        return adaptFragment(source, shadowSamplerAvailable, false);
    }

    /** Water inputs are available after the opaque copy and deferred phase. */
    public static String adaptFragment(String source, boolean shadowSamplerAvailable, boolean waterInputsAvailable) throws IOException {
        return adaptFragment(source, shadowSamplerAvailable, waterInputsAvailable, Map.of(), false);
    }

    static String adaptFragment(String source, boolean shadowSamplerAvailable, boolean waterInputsAvailable,
                                boolean pipelineNoiseSamplerAvailable) throws IOException {
        return adaptFragment(source, shadowSamplerAvailable, waterInputsAvailable, Map.of(),
                pipelineNoiseSamplerAvailable);
    }

    static void validatePackVaryingLinks(String programName, String fragmentSource,
                                         Map<String, TerrainVertexShaderAdapter.PackVarying> packVaryings)
            throws IOException {
        String processed = GlslPreprocessor.process(programName + ".fsh", fragmentSource);
        Matcher matcher = VARYING_DECLARATION.matcher(expandCommaSeparatedVaryings(processed));
        while (matcher.find()) {
            String type = matcher.group(2);
            String name = matcher.group(3);
            TerrainVertexShaderAdapter.PackVarying vertexOutput = packVaryings.get(name);
            if (vertexOutput == null) continue;
            if (!vertexOutput.type().equals(type)) {
                throw new IOException(programName + ".fsh input '" + name + "' uses " + type
                        + " but the vertex output uses " + vertexOutput.type() + ".");
            }
            if (!vertexOutput.interpolation().equals(interpolationQualifier(matcher.group()))) {
                throw new IOException(programName + ".fsh input '" + name
                        + "' has an interpolation qualifier that does not match its vertex output.");
            }
        }
    }

    static String adaptFragment(String source, boolean shadowSamplerAvailable, boolean waterInputsAvailable,
                                Map<String, TerrainVertexShaderAdapter.PackVarying> packVaryings) throws IOException {
        return adaptFragment(source, shadowSamplerAvailable, waterInputsAvailable, packVaryings, false);
    }

    static String adaptFragment(String source, boolean shadowSamplerAvailable, boolean waterInputsAvailable,
                                Map<String, TerrainVertexShaderAdapter.PackVarying> packVaryings,
                                boolean pipelineNoiseSamplerAvailable) throws IOException {
        return adaptFragment(source, shadowSamplerAvailable, waterInputsAvailable, packVaryings,
                pipelineNoiseSamplerAvailable, "gbuffers_terrain");
    }

    static String adaptFragment(String source, boolean shadowSamplerAvailable, boolean waterInputsAvailable,
                                Map<String, TerrainVertexShaderAdapter.PackVarying> packVaryings,
                                boolean pipelineNoiseSamplerAvailable, String programName) throws IOException {
        source = IrisRenderStages.bindUniform(source, IrisRenderStages.forProgram(programName), programName);
        Set<String> conflictingShaderPackUniforms = conflictingShaderPackUniforms(source);
        Map<String, Integer> colorInputs = waterInputsAvailable ? GeometryColorSamplers.read(source) : Map.of();
        FragmentOutputs fragmentOutputs = FragmentOutputs.parse(source);
        source = fragmentOutputs.rewriteLegacy(source);
        String adapted = VERSION_DIRECTIVE.matcher(source).replaceAll("");
        List<String> extensions = new ArrayList<>();
        Matcher extensionMatcher = EXTENSION_DIRECTIVE.matcher(adapted);
        while (extensionMatcher.find()) {
            extensions.add(extensionMatcher.group().strip());
        }
        adapted = extensionMatcher.replaceAll("");
        adapted = PRECISION_DECLARATION.matcher(adapted).replaceAll("");
        adapted = ALPHA_TEST_UNIFORM.matcher(adapted).replaceAll("");
        adapted = SHADOW_MATRIX_UNIFORM.matcher(adapted).replaceAll("");
        adapted = UnusedSamplerDeclarations.removeUnused(adapted);
        boolean noiseSamplerAvailable = usesNoiseSampler(adapted) || pipelineNoiseSamplerAvailable;
        ShadowColorSampler.validate(source, shadowSamplerAvailable, GeometryColorSamplers.indices(colorInputs).size());
        ShadowDepthSampler.validate(source, shadowSamplerAvailable, GeometryColorSamplers.indices(colorInputs).size());

        Matcher samplerMatcher = SAMPLER_DECLARATION.matcher(adapted);
        StringBuffer withoutSamplers = new StringBuffer();
        while (samplerMatcher.find()) {
            String sampler = samplerMatcher.group(2);
            if (!sampler.equals("texture") && !sampler.equals("tex") && !sampler.equals("gtexture") && !sampler.equals("lightmap")
                    && !sampler.equals("noisetex")
                    && !sampler.equals("specular")
                    && !colorInputs.containsKey(sampler)
                    && !(sampler.equals("depthtex1") && waterInputsAvailable)
                    && !(((sampler.equals("shadowtex0") || sampler.equals("shadowtex1"))
                    || ShadowColorSampler.isSampler(sampler)) && shadowSamplerAvailable)) {
                throw new IOException("gbuffers_terrain uses unsupported sampler '" + sampler + "'.");
            }
            String samplerType = samplerMatcher.group(1);
            if (!samplerType.equals("sampler2D")
                    && !((sampler.equals("shadowtex0") || sampler.equals("shadowtex1")) && shadowSamplerAvailable
                    && samplerType.equals("sampler2DShadow"))) {
                throw new IOException("gbuffers_terrain sampler '" + sampler + "' must use sampler2D.");
            }
            samplerMatcher.appendReplacement(withoutSamplers, "");
        }
        samplerMatcher.appendTail(withoutSamplers);
        // The Vulkan bridge samples the depth image as sampler2D and emulates the
        // shadow comparison in veyraSample(..., vec3). Lower helper-function
        // parameters too; otherwise Iris/OptiFine helpers such as
        // texture2DShadow(sampler2DShadow depthMap, ...) retain an incompatible
        // comparison-sampler type after the uniform declaration is removed.
        adapted = withoutSamplers.toString().replaceAll("\\bsampler2DShadow\\b", "sampler2D");
        IrisFogUniformAdapter.AdaptedShader fogUniforms =
                IrisFogUniformAdapter.extract(adapted, "gbuffers_terrain", true);
        adapted = fogUniforms.source();
        Matcher uniformMatcher = SHADERPACK_UNIFORM_DECLARATION.matcher(adapted);
        StringBuffer withoutShaderPackUniforms = new StringBuffer();
        java.util.Map<String, String> declaredUniforms = new java.util.HashMap<>();
        while (uniformMatcher.find()) {
            String type = uniformMatcher.group(1);
            String name = uniformMatcher.group(2);
            String unbound = ShaderPackUniformAdapter.unboundEntityDeclaration("gbuffers_terrain", type, name);
            if (unbound != null) {
                uniformMatcher.appendReplacement(withoutShaderPackUniforms, unbound);
                continue;
            }
            ShaderUniform uniform = shaderPackUniform(name);
            if (uniform == null) {
                throw new IOException(unsupportedUniformMessage("gbuffers_terrain", name));
            }
            if (!uniform.accepts(type)) {
                throw new IOException("gbuffers_terrain uniform '" + name + "' must use " + uniform.sourceTypeDescription() + ".");
            }
            if (declaredUniforms.putIfAbsent(name, type) != null) {
                throw new IOException("gbuffers_terrain declares uniform '" + name + "' more than once.");
            }
            uniformMatcher.appendReplacement(withoutShaderPackUniforms, "");
        }
        uniformMatcher.appendTail(withoutShaderPackUniforms);
        adapted = withoutShaderPackUniforms.toString();
        Matcher unsupportedUniform = UNSUPPORTED_UNIFORM_DECLARATION.matcher(adapted);
        if (unsupportedUniform.find()) {
            int lineEnd = adapted.indexOf('\n', unsupportedUniform.start());
            String declaration = adapted.substring(unsupportedUniform.start(), lineEnd < 0 ? adapted.length() : lineEnd)
                    .strip();
            if (declaration.length() > 120) declaration = declaration.substring(0, 120) + "...";
            throw new IOException("gbuffers_terrain has an unsupported uniform declaration '" + declaration
                    + "'; supported Iris scalar/matrix uniforms and the built-in samplers are available.");
        }

        adapted = UnusedVaryingDeclarations.removeUnused(expandCommaSeparatedVaryings(adapted));
        Matcher varyingMatcher = VARYING_DECLARATION.matcher(expandCommaSeparatedVaryings(adapted));
        StringBuffer withoutVaryings = new StringBuffer();
        List<String> declaredVaryings = new ArrayList<>();
        Map<String, String> linkedPackVaryings = new java.util.LinkedHashMap<>();
        Map<Integer, String> fixedInputAliases = new java.util.LinkedHashMap<>();
        Map<String, String> computedVaryingTypes = new java.util.LinkedHashMap<>();
        Map<String, String> computedVaryingExpressions = new java.util.LinkedHashMap<>();
        StringBuilder packVaryingDeclarations = new StringBuilder();
        Set<Integer> requiredTerrainInputLocations = new HashSet<>();
        while (varyingMatcher.find()) {
            String type = varyingMatcher.group(2);
            String name = varyingMatcher.group(3);
            TerrainVertexShaderAdapter.PackVarying packVarying = packVaryings.get(name);
            if (packVarying != null) {
                if (!packVarying.type().equals(type)) {
                    throw new IOException("gbuffers_terrain input '" + name + "' uses " + type
                            + " but its vertex output uses " + packVarying.type() + ".");
                }
                String interpolation = interpolationQualifier(varyingMatcher.group());
                if (!packVarying.interpolation().equals(interpolation)) {
                    throw new IOException("gbuffers_terrain input '" + name
                            + "' has an interpolation qualifier that does not match its vertex output.");
                }
                String fragmentVariable = packVarying.fixedBridge()
                        ? fixedFragmentVariable(packVarying.location()) : null;
                if (packVarying.fixedBridge()) {
                    requiredTerrainInputLocations.add(packVarying.location());
                }
                if (fragmentVariable == null) {
                    packVaryingDeclarations.append("layout(location = ").append(packVarying.location()).append(") ");
                    if (!packVarying.interpolation().equals("smooth")) {
                        packVaryingDeclarations.append(packVarying.interpolation()).append(' ');
                    }
                    packVaryingDeclarations.append("in ").append(type).append(' ').append(name).append(";\n");
                    fragmentVariable = name;
                } else {
                    addFixedTerrainInputAlias(packVarying.location(), name, type, fixedInputAliases,
                            computedVaryingTypes, computedVaryingExpressions);
                }
                linkedPackVaryings.put(name, name);
                declaredVaryings.add(name);
                varyingMatcher.appendReplacement(withoutVaryings, "");
                continue;
            }
            Varying varying = SUPPORTED_VARYINGS.get(name);
            if (varying == null) {
                throw new IOException("gbuffers_terrain input '" + name + "' is not provided by Veyra's terrain bridge yet.");
            }
            boolean floatMaterialId = name.equals("mat") && type.equals("float");
            if (!varying.type().equals(type) && !floatMaterialId) {
                String expected = name.equals("mat") ? "int or float" : varying.type();
                throw new IOException("gbuffers_terrain input '" + name + "' must use " + expected + ".");
            }
            String location = varyingMatcher.group(1);
            if (location != null && varying.location() >= 0 && Integer.parseInt(location) != varying.location()) {
                throw new IOException("gbuffers_terrain input '" + name + "' uses an unsupported location.");
            }
            declaredVaryings.add(name);
            if (name.equals("recolor")) {
                requiredTerrainInputLocations.add(11);
            } else if (varying.location() >= 0 && !name.equals("normal")) {
                requiredTerrainInputLocations.add(varying.location());
            }
            String computedExpression = computedTerrainVaryingExpression(name, type);
            if (computedExpression != null) {
                computedVaryingTypes.put(name, type);
                computedVaryingExpressions.put(name, computedExpression);
            } else if (varying.location() >= 0) {
                addFixedTerrainInputAlias(varying.location(), name, type, fixedInputAliases,
                        computedVaryingTypes, computedVaryingExpressions);
            }
            varyingMatcher.appendReplacement(withoutVaryings, "");
        }
        varyingMatcher.appendTail(withoutVaryings);
        adapted = withoutVaryings.toString();

        String outputName = fragmentOutputs.variables().getOrDefault(0, "veyraFragColor");
        Matcher outputMatcher = OUTPUT_DECLARATION.matcher(adapted);
        StringBuffer withoutOutputs = new StringBuffer();
        while (outputMatcher.find()) {
            outputMatcher.appendReplacement(withoutOutputs, "");
        }
        outputMatcher.appendTail(withoutOutputs);
        adapted = withoutOutputs.toString();
        if (UNSUPPORTED_OUTPUT_DECLARATION.matcher(adapted).find()) {
            throw new IOException("gbuffers_terrain requires vec4 fragment outputs with supported locations.");
        }
        String uncommented = COMMENTS.matcher(adapted).replaceAll("");
        Matcher unsupportedInput = UNSUPPORTED_GBUFFER_INPUT.matcher(uncommented);
        while (unsupportedInput.find()) {
            String name = unsupportedInput.group(1);
            if ((name.equals("mc_Entity")
                    || name.equals("tangent") || name.equals("binormal") || name.equals("viewVector"))
                    && declaredVaryings.contains(name)) {
                continue;
            }
            throw new IOException("gbuffers_terrain input '" + name + "' is not provided by Veyra's terrain bridge yet.");
        }
        if (UNSUPPORTED_TEXTURE_FUNCTION.matcher(uncommented).find()) {
            throw new IOException("gbuffers_terrain uses a texture sampling function that Veyra's terrain bridge does not support yet.");
        }

        adapted = adapted
                .replaceAll("\\btexture2DLod(?:EXT)?\\s*\\(", "veyraSampleLod(")
                .replaceAll("\\btextureLod\\s*\\(", "veyraSampleLod(")
                .replaceAll("\\btexture2DGrad(?:ARB)?\\s*\\(", "veyraSampleGrad(")
                .replaceAll("\\btextureGrad\\s*\\(", "veyraSampleGrad(")
                .replaceAll("\\btexture2D\\s*\\(", "veyraSample(")
                .replaceAll("\\btexture\\s*\\(", "veyraSample(");

        StringBuilder result = new StringBuilder("#version 460\n");
        for (String extension : extensions) {
            result.append(extension).append('\n');
        }
        boolean hasTangentSpaceInputs = (declaredVaryings.contains("tangent") && !linkedPackVaryings.containsKey("tangent"))
                || (declaredVaryings.contains("binormal") && !linkedPackVaryings.containsKey("binormal"))
                || (declaredVaryings.contains("viewVector") && !linkedPackVaryings.containsKey("viewVector"));
        boolean hasDerivedNormal = declaredVaryings.contains("normal") && !linkedPackVaryings.containsKey("normal");
        if (hasTangentSpaceInputs) {
            requiredTerrainInputLocations.add(1);
            requiredTerrainInputLocations.add(7);
            requiredTerrainInputLocations.add(10);
        } else if (hasDerivedNormal) {
            requiredTerrainInputLocations.add(7);
        }
        String bridge = pruneUnusedTerrainFragmentInputs(FRAGMENT_BRIDGE, requiredTerrainInputLocations)
                .replace("__VEYRA_SHADERPACK_UNIFORMS__", shaderPackUniformFields(conflictingShaderPackUniforms))
                .replace("#define texture Sampler0", shaderPackUniformMacros(declaredUniforms, conflictingShaderPackUniforms)
                        + fogUniforms.bridge()
                        + "#define texture Sampler0");
        boolean usesDepth = waterInputsAvailable && usesOpaqueDepthSampler(source);
        if (usesDepth) {
            int depthBinding = 5 + (shadowSamplerAvailable ? 1 : 0) + (noiseSamplerAvailable ? 1 : 0);
            bridge = "layout(binding = " + depthBinding + ") uniform sampler2D VeyraDepthTex1Sampler;\n"
                    + "#define depthtex1 VeyraDepthTex1Sampler\n" + bridge;
        }
        int colorBinding = 5 + (shadowSamplerAvailable ? 1 : 0) + (noiseSamplerAvailable ? 1 : 0) + (usesDepth ? 1 : 0);
        if (usesSpecularSampler(source)) {
            bridge = "layout(binding = " + colorBinding++ + ") uniform sampler2D VeyraSpecularSampler;\n"
                    + "#define specular VeyraSpecularSampler\n" + bridge;
        }
        if (ShadowDepthSampler.used(source)) {
            bridge = "layout(binding = " + colorBinding++ + ") uniform sampler2D " + ShadowDepthSampler.NAME + ";\n"
                    + "#define shadowtex1 " + ShadowDepthSampler.NAME + "\n" + bridge;
        }
        for (int index : new java.util.TreeSet<>(ShadowColorSampler.indices(source))) {
            String sampler = ShadowColorSampler.samplerName(index);
            bridge = "layout(binding = " + colorBinding++ + ") uniform sampler2D " + sampler + ";\n"
                    + "#define shadowcolor" + index + " " + sampler + "\n"
                    + (index == 0 ? "#define shadowcolor " + sampler + "\n" : "") + bridge;
        }
        StringBuilder colorBridge = new StringBuilder();
        for (int index : GeometryColorSamplers.indices(colorInputs)) {
            colorBridge.append("layout(binding = ").append(colorBinding++).append(") uniform sampler2D ")
                    .append(GeometryColorSamplers.samplerName(index)).append(";\n");
        }
        colorInputs.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(input -> colorBridge
                .append("#define ").append(input.getKey()).append(' ').append(GeometryColorSamplers.samplerName(input.getValue())).append('\n'));
        bridge = colorBridge + bridge;
        if (hasTangentSpaceInputs) {
            bridge = bridge.replace("#define texture Sampler0", TANGENT_SPACE_BRIDGE + "\n#define texture Sampler0");
        } else if (hasDerivedNormal) {
            bridge = bridge.replace("#define texture Sampler0", """
                    vec3 veyraTerrainNormal() {
                        vec3 faceNormal = cross(dFdx(veyraTerrainPosition), dFdy(veyraTerrainPosition));
                        return normalize(gl_FrontFacing ? faceNormal : -faceNormal);
                    }
                    #define texture Sampler0
                    """);
        }
        if (!shadowSamplerAvailable) {
            bridge = bridge.replaceAll(
                    "(?m)^[\\t ]*layout\\(binding = 5\\) uniform sampler2D VeyraShadowSampler;[\\t ]*\\R", "")
                    .replaceAll("(?m)^[\\t ]*#define shadowtex0 VeyraShadowSampler[\\t ]*\\R", "")
                    .replaceAll("(?m)^[\\t ]*#define shadow2D\\(sampler, coord\\) veyraSample\\(sampler, coord\\)[\\t ]*\\R", "");
        }
        if (!noiseSamplerAvailable) {
            bridge = bridge.replaceAll(
                    "(?m)^[\\t ]*layout\\(binding = 6\\) uniform sampler2D VeyraNoiseSampler;[\\t ]*\\R", "")
                    .replaceAll("(?m)^[\\t ]*#define noisetex VeyraNoiseSampler[\\t ]*\\R", "");
        } else if (!shadowSamplerAvailable) {
            // VulkanMod 0.6.8 indexes its layout array by binding, so optional images
            // must remain contiguous after the three UBOs and two base terrain samplers.
            bridge = bridge.replace("layout(binding = 6) uniform sampler2D VeyraNoiseSampler;",
                    "layout(binding = 5) uniform sampler2D VeyraNoiseSampler;");
        }
        bridge = bridge
                .replace("layout(location = 0) out vec4 veyraFragColor;", fragmentOutputs.declarations())
                .replace("#define gl_FragColor veyraFragColor", "#define gl_FragColor " + outputName);
        for (Map.Entry<Integer, String> alias : fixedInputAliases.entrySet()) {
            String bridgeVariable = fixedFragmentVariable(alias.getKey());
            if (bridgeVariable != null) {
                bridge = replaceIdentifier(bridge, bridgeVariable, alias.getValue());
            }
        }
        StringBuilder computedVaryingDeclarations = new StringBuilder();
        computedVaryingTypes.forEach((name, type) -> computedVaryingDeclarations
                .append(type).append(' ').append(name).append(";\n"));
        bridge += packVaryingDeclarations.toString() + computedVaryingDeclarations;
        boolean initializePackVaryings = !computedVaryingExpressions.isEmpty();
        if (hasTangentSpaceInputs || initializePackVaryings) {
            adapted = renameMainForTerrainInitialization(adapted);
        }
        result.append(bridge).append(adapted);
        if (hasTangentSpaceInputs || initializePackVaryings) {
            result.append("\nvoid main() {\n");
            if (hasTangentSpaceInputs) result.append("    veyraInitializeTerrainTangentFrame();\n");
            computedVaryingExpressions.forEach((name, expression) -> result.append("    ")
                    .append(name).append(" = ").append(expression).append(";\n"));
            result.append("    veyraShaderPackMain();\n}\n");
        }
        return result.toString();
    }

    /** Appends the per-frame Iris uniforms evaluated by the renderer (see IrisExtraUniforms). */
    private static List<ShaderUniform> withIrisExtras(List<ShaderUniform> builtIns) {
        List<ShaderUniform> uniforms = new ArrayList<>(builtIns);
        for (IrisExtraUniforms.Definition definition : IrisExtraUniforms.definitions()) {
            if (uniforms.stream().noneMatch(uniform -> uniform.name().equals(definition.name()))) {
                uniforms.add(new ShaderUniform(definition.name(), definition.kind().glsl(),
                        definition.kind() == IrisExtraUniforms.Kind.BOOL));
            }
        }
        return List.copyOf(uniforms);
    }

    /** Built-in Iris uniforms followed by the active shader pack's custom uniforms. */
    public static List<ShaderUniform> shaderPackUniforms() {
        List<CustomUniforms.Uniform> custom = CustomUniforms.active().uniforms();
        if (custom.isEmpty()) {
            return SHADERPACK_UNIFORMS;
        }
        List<ShaderUniform> uniforms = new ArrayList<>(SHADERPACK_UNIFORMS);
        for (CustomUniforms.Uniform uniform : custom) {
            int builtInIndex = -1;
            for (int index = 0; index < uniforms.size(); index++) {
                if (uniforms.get(index).name().equals(uniform.name())) {
                    builtInIndex = index;
                    break;
                }
            }
            if (builtInIndex >= 0) {
                if (IrisExtraUniforms.VEYRA_COMPATIBILITY_UNIFORMS.contains(uniform.name())) {
                    uniforms.set(builtInIndex, new ShaderUniform(uniform.name(), uniform.type().glsl(),
                            uniform.type() == CustomUniforms.Type.BOOL));
                }
                continue;
            }
            uniforms.add(new ShaderUniform(uniform.name(), uniform.type().glsl(),
                    uniform.type() == CustomUniforms.Type.BOOL));
        }
        return List.copyOf(uniforms);
    }

    /** Describes why a declared uniform is unavailable, including unsupported custom definitions. */
    public static String unsupportedUniformMessage(String programName, String name) {
        String reason = CustomUniforms.active().unavailableReason(name);
        return reason == null
                ? programName + " custom uniform '" + name + "' is not supported yet."
                : programName + " custom uniform '" + name + "' from shaders.properties is unavailable: " + reason + ".";
    }

    public static boolean usesOpaqueDepthSampler(String source) {
        return Pattern.compile("\\bdepthtex1\\b").matcher(
                UnusedSamplerDeclarations.removeUnused(COMMENTS.matcher(source).replaceAll(""))).find();
    }

    public static boolean usesSpecularSampler(String source) {
        return Pattern.compile("\\bspecular\\b").matcher(UnusedSamplerDeclarations.removeUnused(
                ShaderSource.maskBlockAndLineComments(source))).find();
    }

    public static boolean usesNoiseSampler(String source) {
        return NOISETEX_REFERENCE.matcher(UnusedSamplerDeclarations.removeUnused(
                        COMMENTS.matcher(source == null ? "" : source).replaceAll("")))
                .find();
    }

    private static ShaderUniform shaderPackUniform(String name) {
        return shaderPackUniforms().stream()
                .filter(uniform -> uniform.name().equals(name))
                .findFirst()
                .orElse(null);
    }

    private static String shaderPackUniformFields() {
        return shaderPackUniformFields(Set.of());
    }

    private static String shaderPackUniformFields(Set<String> conflicts) {
        StringBuilder fields = new StringBuilder();
        for (ShaderUniform uniform : shaderPackUniforms()) {
            String memberName = conflicts.contains(uniform.name())
                    ? reservedShaderPackUniformName(uniform.name()) : uniform.blockMemberName();
            fields.append("    ").append(uniform.bufferType()).append(' ')
                    .append(memberName).append(";\n");
        }
        return fields.toString().stripTrailing();
    }

    /** Finds pack globals that would collide with same-named Iris fields in Veyra's terrain UBO. */
    static Set<String> conflictingShaderPackUniforms(String source) {
        Set<String> supported = shaderPackUniforms().stream().map(ShaderUniform::name)
                .collect(java.util.stream.Collectors.toSet());
        Set<String> conflicts = new java.util.LinkedHashSet<>();
        int braceDepth = 0;
        for (String line : ShaderSource.maskBlockAndLineComments(source).split("\\R", -1)) {
            if (braceDepth == 0) {
                Matcher declaration = TOP_LEVEL_VALUE_DECLARATION.matcher(line);
                if (declaration.matches() && supported.contains(declaration.group(1))) {
                    conflicts.add(declaration.group(1));
                }
            }
            for (int index = 0; index < line.length(); index++) {
                if (line.charAt(index) == '{') braceDepth++;
                else if (line.charAt(index) == '}') braceDepth = Math.max(0, braceDepth - 1);
            }
        }
        return Set.copyOf(conflicts);
    }

    /** Renames only colliding UBO members; field types and order keep the Vulkan block layout intact. */
    static String renameConflictingShaderPackUniformFields(String source, String packSource) {
        for (String name : conflictingShaderPackUniforms(packSource)) {
            ShaderUniform uniform = shaderPackUniform(name);
            if (uniform == null) continue;
            Pattern field = Pattern.compile("(?m)^([\\t ]*)" + Pattern.quote(uniform.bufferType()) + "[\\t ]+"
                    + Pattern.quote(uniform.blockMemberName()) + "([\\t ]*;[\\t ]*)$");
            Matcher matcher = field.matcher(source);
            if (matcher.find()) {
                source = matcher.replaceAll(Matcher.quoteReplacement(matcher.group(1)
                        + uniform.bufferType() + " " + reservedShaderPackUniformName(name) + matcher.group(2)));
                source = Pattern.compile("\\b" + Pattern.quote(name) + "\\b")
                        .matcher(source).replaceAll(Matcher.quoteReplacement(reservedShaderPackUniformName(name)));
            }
        }
        return source;
    }

    private static String reservedShaderPackUniformName(String name) {
        return "veyraReservedShaderPack_" + name;
    }

    private static void addFixedTerrainInputAlias(int location, String name, String type,
                                                 Map<Integer, String> fixedInputAliases,
                                                 Map<String, String> computedVaryingTypes,
                                                 Map<String, String> computedVaryingExpressions) {
        String existing = fixedInputAliases.putIfAbsent(location, name);
        if (existing != null && !existing.equals(name)) {
            computedVaryingTypes.put(name, type);
            computedVaryingExpressions.put(name, existing);
        }
    }

    private static String computedTerrainVaryingExpression(String name, String type) {
        return switch (name) {
            case "mat" -> type.equals("float") ? "float(veyraTerrainMaterialId)"
                    : type.equals("int") ? "veyraTerrainMaterialId" : null;
            case "recolor" -> "((veyraTerrainMaterialId / 100 == 151 || veyraTerrainMaterialId / 100 == 155 || "
                    + "veyraTerrainMaterialId / 100 == 156) ? 1.0 : 0.0)";
            case "sunVec" -> "normalize(sunPosition)";
            case "upVec" -> "normalize(gbufferModelView[1].xyz)";
            case "eastVec" -> "normalize(gbufferModelView[0].xyz)";
            case "normal" -> "veyraTerrainNormal()";
            case "tangent" -> "veyraTerrainTangent()";
            case "binormal" -> "veyraTerrainBinormal()";
            case "viewVector" -> "veyraTerrainViewVector()";
            default -> null;
        };
    }

    private static String replaceIdentifier(String source, String original, String replacement) {
        return Pattern.compile("\\b" + Pattern.quote(original) + "\\b")
                .matcher(source).replaceAll(Matcher.quoteReplacement(replacement));
    }

    private static String shaderPackUniformMacros(Map<String, String> declaredUniforms, Set<String> conflicts) {
        StringBuilder macros = new StringBuilder();
        for (ShaderUniform uniform : shaderPackUniforms()) {
            String memberName = conflicts.contains(uniform.name())
                    ? reservedShaderPackUniformName(uniform.name()) : uniform.blockMemberName();
            if (uniform.booleanField()) {
                macros.append("#define ").append(uniform.name()).append(' ');
                if ("int".equals(declaredUniforms.get(uniform.name()))) {
                    macros.append(memberName);
                } else {
                    macros.append('(').append(memberName).append(" != 0)");
                }
                macros.append('\n');
            }
        }
        return macros.toString();
    }

    public static String adaptVertex(String source) throws IOException {
        String outputMarker = "layout (location = 4) out flat float fadeFactor;";
        if (!source.contains(outputMarker)) {
            throw new IOException("VulkanMod's built-in terrain vertex shader changed; Veyra cannot bridge gbuffers_terrain inputs.");
        }
        String compressedColorMarker = "layout (location = 2) in uint PackedColor;";
        if (!source.contains(compressedColorMarker)) {
            throw new IOException("VulkanMod's compressed terrain vertex format changed; Veyra cannot read per-quad texture coordinates or block IDs.");
        }
        String adapted = source.replace(compressedColorMarker,
                compressedColorMarker + "\n    layout (location = 3) in uvec2 MidTexCoord;"
                        + "\n    layout (location = 4) in int VeyraTerrainMaterialId;"
                        + "\n    layout (location = 5) in float VeyraTerrainRenderType;"
                        + "\n    layout (location = 6) in int VeyraTerrainPackedNormal;"
                        + "\n    layout (location = 7) in float VeyraTerrainTangentX;"
                        + "\n    layout (location = 8) in float VeyraTerrainTangentY;"
                        + "\n    layout (location = 9) in float VeyraTerrainTangentZ;"
                        + "\n    layout (location = 10) in float VeyraTerrainTangentHandedness;"
                        + "\n    layout (location = 11) in int VeyraTerrainPackedMidBlock;"
                        + "\n    #define VeyraTerrainMidBlock vec4(float(bitfieldExtract(VeyraTerrainPackedMidBlock, 0, 8)),"
                        + " float(bitfieldExtract(VeyraTerrainPackedMidBlock, 8, 8)),"
                        + " float(bitfieldExtract(VeyraTerrainPackedMidBlock, 16, 8)),"
                        + " float(bitfieldExtract(uint(VeyraTerrainPackedMidBlock), 24, 8)))"
                        + "\n    #define VeyraTerrainTangent vec4(VeyraTerrainTangentX, VeyraTerrainTangentY,"
                        + " VeyraTerrainTangentZ, VeyraTerrainTangentHandedness)");
        adapted = adapted.replace(outputMarker,
                outputMarker + "\nlayout (location = 5) out vec2 veyraLightmapCoord;"
                        + "\nlayout (location = 6) out vec4 veyraShadowPos;"
                        + "\nlayout (location = 7) out vec3 veyraTerrainPosition;"
                        + "\nlayout (location = 8) out float veyraSphericalVertexDistance;"
                        + "\nlayout (location = 9) out float veyraCylindricalVertexDistance;"
                        + "\nlayout (location = 10) out vec3 veyraViewPosition;"
                        + "\nlayout (location = 11) out flat int veyraTerrainMaterialId;"
                        + "\nlayout (location = 12) out vec2 veyraSignMidCoordPos;"
                        + "\nlayout (location = 13) out flat vec2 veyraMcEntity;"
                        + "\nlayout (location = 14) out flat vec2 veyraAbsMidCoordPos;"
                        + "\nlayout (location = 15) out flat vec2 veyraMidCoord;"
                        + "\nlayout (location = 16) out float veyraTerrainDistance;"
                        + "\nlayout (location = 17) out vec4 veyraBslTexCoord;"
                        + "\nlayout (location = 18) out vec4 veyraBslTexCoordAM;"
                        + "\nlayout (location = 19) out vec4 veyraSpriteBounds;");
        String lightmapMarker = "vertexColor = Color * sample_lightmap2(Sampler2, Position.a);";
        if (!adapted.contains(lightmapMarker)) {
            throw new IOException("VulkanMod's terrain lightmap encoding changed; Veyra cannot bridge gbuffers_terrain inputs.");
        }
        adapted = adapted.replace(lightmapMarker, """
                vertexColor = Color;
                const ivec2 lightCoord = ivec2(
                    bitfieldExtract(uint(Position.a), 4, 4),
                    bitfieldExtract(uint(Position.a), 12, 4));
                veyraLightmapCoord = (vec2(lightCoord) + vec2(0.5)) / 16.0;
                """);
        String uniformMarker = "    int UseRgss;";
        if (!adapted.contains(uniformMarker)) {
            throw new IOException("VulkanMod's terrain uniform buffer changed; Veyra cannot provide shadow matrices.");
        }
        adapted = adapted.replace(uniformMarker,
                uniformMarker + "\n    mat4 shadowModelView;\n    mat4 shadowProjection;\n" + shaderPackUniformFields());
        String positionMarker = "    const vec3 pos = getVertexPosition();";
        if (!adapted.contains(positionMarker)) {
            throw new IOException("VulkanMod's terrain vertex position changed; Veyra cannot calculate shadow coordinates.");
        }
        adapted = adapted.replace(positionMarker,
                positionMarker + "\n    veyraTerrainPosition = pos;"
                        + "\n    veyraShadowPos = shadowProjection * shadowModelView * vec4(pos, 1.0);"
                        + "\n    veyraSphericalVertexDistance = length(pos);"
                        + "\n    veyraCylindricalVertexDistance = max(length(pos.xz), abs(pos.y));"
                        + "\n    veyraViewPosition = (gbufferModelView * vec4(pos, 1.0)).xyz;"
                        + "\n    veyraTerrainDistance = length(veyraViewPosition);"
                        + "\n    veyraTerrainMaterialId = VeyraTerrainMaterialId;"
                        + "\n    veyraMcEntity = vec2(float(VeyraTerrainMaterialId), VeyraTerrainRenderType);");
        String texCoordMarker = "texCoord0 = UV0 * UV_INV;";
        if (!adapted.contains(texCoordMarker)) {
            throw new IOException("VulkanMod's compressed terrain texture coordinates changed; Veyra cannot compute the Iris per-quad midpoint.");
        }
        return adapted.replace(texCoordMarker,
                "const vec2 veyraRawTexCoord = UV0 * UV_INV;"
                        + "\n    const vec2 veyraRawMidTexCoord = vec2(MidTexCoord) * UV_INV;"
                        + "\n    texCoord0 = (textureMatrix * vec4(veyraRawTexCoord, 0.0, 1.0)).xy;"
                        + "\n    veyraMidCoord = (textureMatrix * vec4(veyraRawMidTexCoord, 0.0, 1.0)).xy;"
                        + "\n    const vec2 veyraMidCoordOffset = texCoord0 - veyraMidCoord;"
                        + "\n    veyraSignMidCoordPos = sign(veyraMidCoordOffset);"
                        + "\n    veyraAbsMidCoordPos = abs(veyraMidCoordOffset);"
                        + "\n    veyraBslTexCoord = vec4(sign(veyraMidCoordOffset) * 0.5 + 0.5, 0.0, 0.0);"
                        + "\n    veyraBslTexCoordAM = vec4(min(texCoord0, veyraMidCoord - veyraMidCoordOffset), abs(veyraMidCoordOffset) * 2.0);"
                        + "\n    const vec2 veyraSpriteRadius = abs(texCoord0 - veyraMidCoord);"
                        + "\n    veyraSpriteBounds = vec4(veyraMidCoord - veyraSpriteRadius, veyraMidCoord + veyraSpriteRadius);");
    }

    private static String pruneUnusedTerrainFragmentInputs(String bridge, Set<Integer> requiredLocations) {
        Matcher matcher = TERRAIN_FRAGMENT_BRIDGE_INPUT.matcher(ShaderSource.maskBlockAndLineComments(bridge));
        StringBuilder pruned = new StringBuilder(bridge.length());
        int copiedThrough = 0;
        while (matcher.find()) {
            int location = Integer.parseInt(matcher.group(2));
            String name = matcher.group(3);
            if (!TERRAIN_FRAGMENT_BRIDGE_INPUT_NAMES.contains(name) || requiredLocations.contains(location)) continue;
            pruned.append(bridge, copiedThrough, matcher.start());
            copiedThrough = matcher.end();
        }
        if (copiedThrough == 0) return bridge;
        pruned.append(bridge, copiedThrough, bridge.length());
        return pruned.toString();
    }

    static Set<Integer> unusedTerrainVertexOutputs(String adaptedFragmentSource) {
        Set<Integer> consumed = new HashSet<>();
        Matcher inputs = FRAGMENT_INPUT_LOCATION.matcher(ShaderSource.maskBlockAndLineComments(adaptedFragmentSource));
        while (inputs.find()) {
            int location = Integer.parseInt(inputs.group(1));
            String name = inputs.group(2);
            if (name.startsWith("veyraPackVarying_")
                    && !(location == 4 && name.equals("veyraPackVarying_chunkFade"))) continue;
            consumed.add(location);
        }
        Set<Integer> unused = new HashSet<>(TERRAIN_VERTEX_OUTPUT_LOCATIONS);
        unused.removeAll(consumed);
        return Set.copyOf(unused);
    }

    static Set<Integer> unusedTerrainVertexOutputs(String fragmentSource,
            Map<String, TerrainVertexShaderAdapter.PackVarying> packVaryings) throws IOException {
        String processed = GlslPreprocessor.process("gbuffers_terrain.fsh", fragmentSource);
        Matcher inputs = VARYING_DECLARATION.matcher(expandCommaSeparatedVaryings(processed));
        Set<Integer> required = new HashSet<>();
        boolean derivedNormal = false;
        boolean derivedTangentFrame = false;
        while (inputs.find()) {
            String type = inputs.group(2);
            String name = inputs.group(3);
            TerrainVertexShaderAdapter.PackVarying packVarying = packVaryings.get(name);
            if (packVarying != null) {
                if (packVarying.fixedBridge()) required.add(packVarying.location());
                continue;
            }
            Varying varying = SUPPORTED_VARYINGS.get(name);
            if (varying == null) continue;
            if (name.equals("recolor")) {
                required.add(11);
            } else if (name.equals("normal")) {
                derivedNormal = true;
            } else if (name.equals("tangent") || name.equals("binormal") || name.equals("viewVector")) {
                derivedTangentFrame = true;
            } else if (varying.location() >= 0) {
                required.add(varying.location());
            }
        }
        if (derivedNormal || derivedTangentFrame) required.add(7);
        if (derivedTangentFrame) {
            required.add(1);
            required.add(10);
        }
        Set<Integer> unused = new HashSet<>(TERRAIN_VERTEX_OUTPUT_LOCATIONS);
        unused.removeAll(required);
        return Set.copyOf(unused);
    }

    static String pruneUnusedTerrainVertexOutputs(String source, Set<Integer> unusedLocations) {
        Matcher matcher = TERRAIN_VERTEX_OUTPUT_DECLARATION.matcher(ShaderSource.maskBlockAndLineComments(source));
        StringBuilder pruned = new StringBuilder(source.length());
        int copiedThrough = 0;
        while (matcher.find()) {
            int location = Integer.parseInt(matcher.group(2));
            if (!unusedLocations.contains(location)) continue;
            pruned.append(source, copiedThrough, matcher.start())
                    .append(matcher.group(1)).append(matcher.group(3)).append(' ').append(matcher.group(4)).append(';');
            copiedThrough = matcher.end();
        }
        if (copiedThrough == 0) return source;
        pruned.append(source, copiedThrough, source.length());
        return pruned.toString();
    }

    /** Adds an active Iris/OptiFine terrain vertex entrypoint to VulkanMod's terrain stage. */
    public static String adaptVertex(String source, String shaderPackSource, String programName) throws IOException {
        return TerrainVertexShaderAdapter.adapt(adaptVertex(source), shaderPackSource, programName);
    }

    private static String expandCommaSeparatedVaryings(String source) {
        Matcher matcher = COMMA_SEPARATED_VARYING_DECLARATION.matcher(source);
        StringBuffer expanded = new StringBuffer();
        while (matcher.find()) {
            String prefix = matcher.group(1);
            String[] names = matcher.group(2).split("\\s*,\\s*");
            String suffix = matcher.group(3);
            StringBuilder declarations = new StringBuilder();
            for (int index = 0; index < names.length; index++) {
                if (index > 0) declarations.append('\n');
                declarations.append(prefix).append(names[index]).append(';');
                if (index == names.length - 1) declarations.append(suffix);
            }
            matcher.appendReplacement(expanded, Matcher.quoteReplacement(declarations.toString()));
        }
        matcher.appendTail(expanded);
        return expanded.toString();
    }

    private static String renameMainForTerrainInitialization(String source) throws IOException {
        Matcher matcher = MAIN_FUNCTION_DECLARATION.matcher(maskComments(source));
        StringBuilder renamed = new StringBuilder(source.length() + 32);
        int copiedThrough = 0;
        int declarations = 0;
        while (matcher.find()) {
            declarations++;
            int nameStart = matcher.start(1) + matcher.group(1).length();
            int nameEnd = nameStart + "main".length();
            renamed.append(source, copiedThrough, nameStart).append("veyraShaderPackMain");
            copiedThrough = nameEnd;
        }
        if (declarations != 1) {
            throw new IOException("gbuffers_terrain compatibility varyings require exactly one top-level void main() entrypoint.");
        }
        return renamed.append(source, copiedThrough, source.length()).toString();
    }

    private static String maskComments(String source) {
        Matcher matcher = COMMENTS.matcher(source);
        StringBuffer masked = new StringBuffer();
        while (matcher.find()) {
            String comment = matcher.group();
            StringBuilder whitespace = new StringBuilder(comment.length());
            for (int index = 0; index < comment.length(); index++) {
                char character = comment.charAt(index);
                whitespace.append(character == '\r' || character == '\n' ? character : ' ');
            }
            matcher.appendReplacement(masked, Matcher.quoteReplacement(whitespace.toString()));
        }
        matcher.appendTail(masked);
        return masked.toString();
    }

    private static String interpolationQualifier(String declaration) {
        Matcher matcher = Pattern.compile("\\b(flat|smooth|noperspective)\\b").matcher(declaration);
        return matcher.find() ? matcher.group(1) : "smooth";
    }

    private static String fixedFragmentVariable(int location) {
        return switch (location) {
            case 0 -> "veyraVertexColor";
            case 1 -> "veyraTexCoord";
            case 5 -> "veyraLightmapCoord";
            case 6 -> "veyraShadowPos";
            case 7 -> "veyraTerrainPosition";
            case 8 -> "veyraSphericalVertexDistance";
            case 9 -> "veyraCylindricalVertexDistance";
            case 10 -> "veyraViewPosition";
            case 11 -> "veyraTerrainMaterialId";
            case 12 -> "veyraSignMidCoordPos";
            case 13 -> "veyraMcEntity";
            case 14 -> "veyraAbsMidCoordPos";
            case 15 -> "veyraMidCoord";
            case 16 -> "veyraTerrainDistance";
            case 17 -> "veyraBslTexCoord";
            case 18 -> "veyraBslTexCoordAM";
            case 19 -> "veyraSpriteBounds";
            default -> null;
        };
    }

    private record Varying(String type, int location) {
    }

    public record ShaderUniform(String name, String sourceType, boolean booleanField) {
        public String bufferType() {
            return booleanField ? "int" : sourceType;
        }

        public String blockMemberName() {
            return booleanField ? "veyra_" + name : name;
        }

        public boolean accepts(String type) {
            return booleanField ? type.equals("bool") || type.equals("int") : type.equals(sourceType);
        }

        public String sourceTypeDescription() {
            return booleanField ? "bool or int" : sourceType;
        }
    }
}
