package dev.veyra.shader;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
    private static final Pattern COMMENTS = Pattern.compile("(?s)/\\*.*?\\*/|//[^\\r\\n]*");
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
            layout(location = 0) out vec4 veyraFragColor;
            vec4 veyraSample(sampler2D sourceSampler, vec2 sourceUv) {
                return texture(sourceSampler, sourceUv);
            }
            vec4 veyraSampleLod(sampler2D sourceSampler, vec2 sourceUv, float lod) {
                return textureLod(sourceSampler, sourceUv, lod);
            }
            vec4 veyraSampleGrad(sampler2D sourceSampler, vec2 sourceUv, vec2 dx, vec2 dy) {
                return textureGrad(sourceSampler, sourceUv, dx, dy);
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
            #define shadowPos veyraShadowPos
            #define shadowCoord veyraShadowPos
            #define viewPos veyraViewPosition
            #define dist veyraTerrainDistance
            #define vTexCoord veyraBslTexCoord
            #define vTexCoordAM veyraBslTexCoordAM
            #define vertexPos veyraTerrainPosition
            #define playerPos veyraTerrainPosition
            #define signMidCoordPos veyraSignMidCoordPos
            #define absMidCoordPos veyraAbsMidCoordPos
            #define midCoord veyraMidCoord
            #define shadow2D(sampler, coord) veyraSample(sampler, coord)
            #define sphericalVertexDistance veyraSphericalVertexDistance
            #define cylindricalVertexDistance veyraCylindricalVertexDistance
            #define alphaTestRef AlphaCutout
            #define texcoord veyraTexCoord
            #define texCoord veyraTexCoord
            #define texCoord0 veyraTexCoord
            #define lmcoord veyraLightmapCoord
            #define lmCoord veyraLightmapCoord
            #define lightMapCoord veyraLightmapCoord
            #define color veyraVertexColor
            #define glColorRaw veyraVertexColor
            #define glcolor veyraVertexColor
            #define glColor veyraVertexColor
            #define gl_Color veyraVertexColor
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
        boolean noiseSamplerAvailable = usesNoiseSampler(adapted);
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
                    && !(((sampler.equals("shadowtex0") || sampler.equals("shadowtex1")) || (sampler.equals("shadowcolor0") || sampler.equals("shadowcolor"))) && shadowSamplerAvailable)) {
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

        Matcher varyingMatcher = VARYING_DECLARATION.matcher(expandCommaSeparatedVaryings(adapted));
        StringBuffer withoutVaryings = new StringBuffer();
        List<String> declaredVaryings = new ArrayList<>();
        Map<String, String> declaredVaryingTypes = new java.util.HashMap<>();
        while (varyingMatcher.find()) {
            String type = varyingMatcher.group(2);
            String name = varyingMatcher.group(3);
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
            declaredVaryingTypes.put(name, type);
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
        StringBuilder terrainMaterialMacros = new StringBuilder();
        if (declaredVaryings.contains("mat")) {
            terrainMaterialMacros.append("#define mat ");
            terrainMaterialMacros.append(declaredVaryingTypes.get("mat").equals("float")
                    ? "float(veyraTerrainMaterialId)\n" : "veyraTerrainMaterialId\n");
        }
        if (declaredVaryings.contains("mc_Entity")) {
            terrainMaterialMacros.append("#define mc_Entity veyraMcEntity\n");
        }
        if (declaredVaryings.contains("recolor")) {
            terrainMaterialMacros.append("#define recolor ((veyraTerrainMaterialId / 100 == 151 || "
                    + "veyraTerrainMaterialId / 100 == 155 || veyraTerrainMaterialId / 100 == 156) ? 1.0 : 0.0)\n");
        }
        if (declaredVaryings.contains("sunVec")) {
            terrainMaterialMacros.append("#define sunVec normalize(sunPosition)\n");
        }
        if (declaredVaryings.contains("upVec")) {
            terrainMaterialMacros.append("#define upVec normalize(gbufferModelView[1].xyz)\n");
        }
        if (declaredVaryings.contains("eastVec")) {
            terrainMaterialMacros.append("#define eastVec normalize(gbufferModelView[0].xyz)\n");
        }
        String bridge = FRAGMENT_BRIDGE
                .replace("__VEYRA_SHADERPACK_UNIFORMS__", shaderPackUniformFields())
                .replace("#define texture Sampler0", shaderPackUniformMacros(declaredUniforms)
                        + fogUniforms.bridge() + terrainMaterialMacros + "#define texture Sampler0");
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
        if (ShadowColorSampler.used(source)) {
            bridge = "layout(binding = " + colorBinding++ + ") uniform sampler2D " + ShadowColorSampler.NAME + ";\n"
                    + "#define shadowcolor0 " + ShadowColorSampler.NAME + "\n#define shadowcolor " + ShadowColorSampler.NAME + "\n" + bridge;
        }
        StringBuilder colorBridge = new StringBuilder();
        for (int index : GeometryColorSamplers.indices(colorInputs)) {
            colorBridge.append("layout(binding = ").append(colorBinding++).append(") uniform sampler2D ")
                    .append(GeometryColorSamplers.samplerName(index)).append(";\n");
        }
        colorInputs.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(input -> colorBridge
                .append("#define ").append(input.getKey()).append(' ').append(GeometryColorSamplers.samplerName(input.getValue())).append('\n'));
        bridge = colorBridge + bridge;
        boolean hasTangentSpaceInputs = declaredVaryings.contains("tangent")
                || declaredVaryings.contains("binormal") || declaredVaryings.contains("viewVector");
        if (hasTangentSpaceInputs) {
            StringBuilder tangentSpaceMacros = new StringBuilder(TANGENT_SPACE_BRIDGE);
            if (declaredVaryings.contains("normal")) {
                tangentSpaceMacros.append("#define normal veyraTerrainNormal()\n");
            }
            if (declaredVaryings.contains("tangent")) {
                tangentSpaceMacros.append("#define tangent veyraTerrainTangent()\n");
            }
            if (declaredVaryings.contains("binormal")) {
                tangentSpaceMacros.append("#define binormal veyraTerrainBinormal()\n");
            }
            if (declaredVaryings.contains("viewVector")) {
                tangentSpaceMacros.append("#define viewVector veyraTerrainViewVector()\n");
            }
            tangentSpaceMacros.append("#define texture Sampler0");
            bridge = bridge.replace("#define texture Sampler0", tangentSpaceMacros.toString());
        } else if (declaredVaryings.contains("normal")) {
            bridge = bridge.replace("#define texture Sampler0", """
                    vec3 veyraTerrainNormal() {
                        vec3 faceNormal = cross(dFdx(veyraTerrainPosition), dFdy(veyraTerrainPosition));
                        return normalize(gl_FrontFacing ? faceNormal : -faceNormal);
                    }
                    #define normal veyraTerrainNormal()
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
        if (hasTangentSpaceInputs) {
            adapted = renameMainForTangentSpace(adapted);
        }
        result.append(bridge).append(adapted);
        if (hasTangentSpaceInputs) {
            result.append("\nvoid main() {\n    veyraInitializeTerrainTangentFrame();\n    veyraShaderPackMain();\n}\n");
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
            if (SHADERPACK_UNIFORMS.stream().anyMatch(builtIn -> builtIn.name().equals(uniform.name()))) {
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
        StringBuilder fields = new StringBuilder();
        for (ShaderUniform uniform : shaderPackUniforms()) {
            fields.append("    ").append(uniform.bufferType()).append(' ')
                    .append(uniform.blockMemberName()).append(";\n");
        }
        return fields.toString().stripTrailing();
    }

    private static String shaderPackUniformMacros(Map<String, String> declaredUniforms) {
        StringBuilder macros = new StringBuilder();
        for (ShaderUniform uniform : shaderPackUniforms()) {
            if (uniform.booleanField()) {
                macros.append("#define ").append(uniform.name()).append(' ');
                if ("int".equals(declaredUniforms.get(uniform.name()))) {
                    macros.append(uniform.blockMemberName());
                } else {
                    macros.append('(').append(uniform.blockMemberName()).append(" != 0)");
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
                        + "\n    layout (location = 5) in float VeyraTerrainRenderType;");
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
                        + "\nlayout (location = 18) out vec4 veyraBslTexCoordAM;");
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
                        + "\n    veyraBslTexCoordAM = vec4(min(texCoord0, veyraMidCoord - veyraMidCoordOffset), abs(veyraMidCoordOffset) * 2.0);");
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

    private static String renameMainForTangentSpace(String source) throws IOException {
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
            throw new IOException("gbuffers_terrain tangent-space inputs require exactly one top-level void main() entrypoint.");
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
