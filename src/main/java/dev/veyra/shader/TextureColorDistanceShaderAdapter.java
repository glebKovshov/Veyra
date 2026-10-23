package dev.veyra.shader;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Adapts Iris texture, color, and fog-distance inputs to Minecraft's vertex outputs. */
public final class TextureColorDistanceShaderAdapter {
    private static final Pattern VERSION_DIRECTIVE = Pattern.compile("(?m)^[\\t ]*#version[^\\r\\n]*(?:\\r?\\n|$)");
    private static final Pattern EXTENSION_DIRECTIVE = Pattern.compile("(?m)^[\\t ]*#extension[^\\r\\n]*(?:\\r?\\n|$)");
    private static final Pattern PRECISION_DECLARATION = Pattern.compile(
            "(?m)^[\\t ]*precision\\s+(?:lowp|mediump|highp)\\s+\\w+\\s*;[\\t ]*(?:\\r?\\n|$)");
    private static final Pattern SAMPLER_DECLARATION = Pattern.compile(
            "(?m)^[\\t ]*uniform\\s+(?:(?:lowp|mediump|highp)\\s+)?(sampler\\w+)\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*;[\\t ]*(?://[^\\r\\n]*)?(?:\\r?\\n|$)");
    private static final Pattern UNSUPPORTED_UNIFORM_DECLARATION = Pattern.compile("(?m)^[\\t ]*uniform\\b");
    private static final Pattern VARYING_DECLARATION = Pattern.compile(
            "(?m)^[\\t ]*(?:layout\\s*\\(\\s*location\\s*=\\s*(\\d+)\\s*\\)\\s*)?"
                    + "(?:(flat|smooth|noperspective)\\s+)?(?:varying|in)\\s+"
                    + "(?:(flat|smooth|noperspective)\\s+)?([A-Za-z_][A-Za-z0-9_]*)\\s+"
                    + "([A-Za-z_][A-Za-z0-9_]*)\\s*;[\\t ]*(?://[^\\r\\n]*)?(?:\\r?\\n|$)");
    private static final Pattern COMMA_VARYING_DECLARATION = Pattern.compile(
            "(?m)^([\\t ]*(?:(?:layout\\s*\\([^)]*\\)\\s*)?)(?:(?:flat|smooth|noperspective)\\s+)?"
                    + "(?:varying|in)\\s+[A-Za-z_][A-Za-z0-9_]*\\s+)"
                    + "([A-Za-z_][A-Za-z0-9_]*(?:\\s*,\\s*[A-Za-z_][A-Za-z0-9_]*)+)"
                    + "(\\s*;[\\t ]*(?://[^\\r\\n]*)?(?:\\r?\\n|$))");
    private static final Pattern UNSUPPORTED_INPUT_DECLARATION = Pattern.compile(
            "(?m)^[\\t ]*(?:layout\\s*\\([^)]*\\)\\s*)?(?:(?:flat|smooth|noperspective)\\s+)?(?:varying|in)\\b[^\\r\\n]*;?");
    private static final Pattern OUTPUT_DECLARATION = Pattern.compile(
            "(?m)^[\\t ]*(?:layout\\s*\\(\\s*location\\s*=\\s*(\\d+)\\s*\\)\\s*)?out\\s+vec4\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*;[\\t ]*(?://[^\\r\\n]*)?(?:\\r?\\n|$)");
    private static final Pattern UNSUPPORTED_OUTPUT_DECLARATION = Pattern.compile(
            "(?m)^[\\t ]*(?:layout\\s*\\([^)]*\\)\\s*)?out\\s+(?!vec4\\s+[A-Za-z_][A-Za-z0-9_]*\\s*;)[^\\r\\n]*;?");
    private static final Pattern COMMENTS = Pattern.compile("(?s)/\\*.*?\\*/|//[^\\r\\n]*");
    private static final Pattern UNSUPPORTED_TEXTURE_FUNCTION = Pattern.compile(
            "\\btexture(?:(?:2D)?(?:Gather|Offset|Proj)|QueryLod)\\w*\\s*\\(");
    private static final Pattern LIGHTMAP_TEXTURE_SAMPLE = Pattern.compile(
            "\\b(?:texture|texture2D|textureLod|texture2DLod|textureGrad|texture2DGrad|textureProj|texture2DProj|texelFetch)"
                    + "\\s*\\(\\s*lightmap\\b");
    private static final Map<String, String> INPUT_TYPES = Map.ofEntries(
            Map.entry("texcoord", "vec2"),
            Map.entry("texCoord", "vec2"),
            Map.entry("texCoord0", "vec2"),
            Map.entry("color", "vec4"),
            Map.entry("glcolor", "vec4"),
            Map.entry("glColor", "vec4"),
            Map.entry("gl_Color", "vec4"),
            Map.entry("vertexColor", "vec4"),
            Map.entry("sphericalVertexDistance", "float"),
            Map.entry("cylindricalVertexDistance", "float"));
    private static final Set<String> COLOR_INPUTS = Set.of("color", "glcolor", "glColor", "gl_Color", "vertexColor");

    private TextureColorDistanceShaderAdapter() {
    }

    public static String adaptFragment(String source) throws IOException {
        return adaptFragment(source, "gbuffers_particles", true);
    }

    public static String adaptFragment(String source, String programName) throws IOException {
        return adaptFragment(source, programName, true);
    }

    public static String adaptFragment(String source, String programName, boolean hasVertexColor) throws IOException {
        return adaptFragment(source, programName, hasVertexColor, true);
    }

    public static String adaptFragment(String source, String programName,
                                       boolean hasVertexColor, boolean hasVertexDistances) throws IOException {
        return adaptFragment(source, programName, hasVertexColor, hasVertexDistances,
                "smooth", "smooth", false);
    }

    static String adaptFragment(String source, String programName,
                                boolean hasVertexColor, boolean hasVertexDistances,
                                String vertexColorInterpolation, String texCoordInterpolation,
                                boolean explicitVertexLocations) throws IOException {
        return adaptFragment(source, programName, hasVertexColor, hasVertexDistances,
                vertexColorInterpolation, texCoordInterpolation, explicitVertexLocations, null,
                false, false, false, null);
    }

    static String adaptFragment(String source, String programName,
                                boolean hasVertexColor, boolean hasVertexDistances,
                                boolean allowIrisSamplers, boolean shadowPassAvailable,
                                boolean lightmapSamplerAvailable) throws IOException {
        return adaptFragment(source, programName, hasVertexColor, hasVertexDistances,
                "smooth", "smooth", false, null, allowIrisSamplers,
                shadowPassAvailable, lightmapSamplerAvailable, null);
    }

    static String adaptFragment(String source, String programName,
                                boolean hasVertexColor, boolean hasVertexDistances,
                                boolean allowIrisSamplers, boolean shadowPassAvailable,
                                boolean lightmapSamplerAvailable, boolean entityColorAvailable) throws IOException {
        return adaptFragment(source, programName, hasVertexColor, hasVertexDistances,
                "smooth", "smooth", false, null, allowIrisSamplers,
                shadowPassAvailable, lightmapSamplerAvailable, null, entityColorAvailable);
    }

    static String adaptFragment(String source, String programName,
                                boolean hasVertexColor, boolean hasVertexDistances,
                                boolean allowIrisSamplers, boolean shadowPassAvailable,
                                boolean lightmapSamplerAvailable, int renderStage) throws IOException {
        return adaptFragment(source, programName, hasVertexColor, hasVertexDistances,
                "smooth", "smooth", false, null, allowIrisSamplers,
                shadowPassAvailable, lightmapSamplerAvailable, renderStage);
    }

    static String adaptFragment(String source, String programName,
                                boolean hasVertexColor, boolean hasVertexDistances,
                                int renderStage) throws IOException {
        return adaptFragment(source, programName, hasVertexColor, hasVertexDistances,
                "smooth", "smooth", false, null, false, false, false, renderStage);
    }

    static String adaptFragment(String source, String programName,
                                Map<String, TerrainVertexShaderAdapter.PackVarying> vertexOutputs) throws IOException {
        return adaptFragment(source, programName, vertexOutputs, true, true);
    }

    static String adaptFragment(String source, String programName,
                                Map<String, TerrainVertexShaderAdapter.PackVarying> vertexOutputs,
                                boolean shadowPassAvailable, boolean lightmapSamplerAvailable) throws IOException {
        return adaptFragment(source, programName, true, true, "smooth", "smooth", true,
                Map.copyOf(vertexOutputs), true, shadowPassAvailable, lightmapSamplerAvailable, null);
    }

    static String adaptFragment(String source, String programName,
                                Map<String, TerrainVertexShaderAdapter.PackVarying> vertexOutputs,
                                boolean shadowPassAvailable, boolean lightmapSamplerAvailable,
                                boolean entityColorAvailable) throws IOException {
        return adaptFragment(source, programName, true, true, "smooth", "smooth", true,
                Map.copyOf(vertexOutputs), true, shadowPassAvailable, lightmapSamplerAvailable, null,
                entityColorAvailable);
    }

    static String adaptFragment(String source, String programName,
                                Map<String, TerrainVertexShaderAdapter.PackVarying> vertexOutputs,
                                boolean shadowPassAvailable, boolean lightmapSamplerAvailable,
                                int renderStage) throws IOException {
        return adaptFragment(source, programName, true, true, "smooth", "smooth", true,
                Map.copyOf(vertexOutputs), true, shadowPassAvailable, lightmapSamplerAvailable, renderStage);
    }

    private static String adaptFragment(String source, String programName,
                                        boolean hasVertexColor, boolean hasVertexDistances,
                                        String vertexColorInterpolation, String texCoordInterpolation,
                                        boolean explicitVertexLocations,
                                        Map<String, TerrainVertexShaderAdapter.PackVarying> vertexOutputs,
                                        boolean allowIrisSamplers,
                                        boolean shadowPassAvailable, boolean lightmapSamplerAvailable,
                                        Integer renderStage)
            throws IOException {
        return adaptFragment(source, programName, hasVertexColor, hasVertexDistances,
                vertexColorInterpolation, texCoordInterpolation, explicitVertexLocations, vertexOutputs,
                allowIrisSamplers, shadowPassAvailable, lightmapSamplerAvailable, renderStage,
                ShaderPackUniformAdapter.supportsEntityColorProgram(programName));
    }

    private static String adaptFragment(String source, String programName,
                                        boolean hasVertexColor, boolean hasVertexDistances,
                                        String vertexColorInterpolation, String texCoordInterpolation,
                                        boolean explicitVertexLocations,
                                        Map<String, TerrainVertexShaderAdapter.PackVarying> vertexOutputs,
                                        boolean allowIrisSamplers,
                                        boolean shadowPassAvailable, boolean lightmapSamplerAvailable,
                                        Integer renderStage, boolean entityColorAvailable)
            throws IOException {
        if (source == null || source.isBlank()) {
            throw new IOException(programName + " fragment shader is empty.");
        }
        if (source.length() > 1024 * 1024) {
            throw new IOException(programName + " fragment shader exceeds the 1 MiB size limit.");
        }

        String adapted = GlslPreprocessor.process(programName + ".fsh", source.replace("\uFEFF", ""));
        adapted = UnreachableGlslFunctions.removeUnreachable(adapted, "main");
        FragmentOutputs outputLayout = FragmentOutputs.parse(adapted);
        adapted = outputLayout.rewriteLegacy(adapted);
        IrisFogUniformAdapter.AdaptedShader fog = IrisFogUniformAdapter.extract(adapted, programName);
        adapted = fog.source();
        adapted = UnusedUniformDeclarations.removeUnused(adapted);
        adapted = IrisRenderStages.bindUniform(adapted, renderStage, programName);
        adapted = UnusedSamplerDeclarations.removeUnused(adapted);
        SamplerBridge samplers = adaptSamplers(adapted, programName, allowIrisSamplers,
                shadowPassAvailable, lightmapSamplerAvailable);
        adapted = samplers.source();
        adapted = VERSION_DIRECTIVE.matcher(adapted).replaceAll("");
        List<String> extensions = new ArrayList<>();
        Matcher extensionMatcher = EXTENSION_DIRECTIVE.matcher(adapted);
        while (extensionMatcher.find()) {
            extensions.add(extensionMatcher.group().strip());
        }
        adapted = PRECISION_DECLARATION.matcher(extensionMatcher.replaceAll("")).replaceAll("");

        Matcher samplerMatcher = SAMPLER_DECLARATION.matcher(ShaderSource.maskBlockAndLineComments(adapted));
        StringBuilder withoutSamplers = new StringBuilder(adapted.length());
        int samplerCursor = 0;
        while (samplerMatcher.find()) {
            String sampler = samplerMatcher.group(2);
            if (!sampler.equals("texture") && !sampler.equals("gtexture")
                    && !sampler.equals("tex") && !sampler.equals("Sampler0")) {
                throw new IOException(programName + " uses unsupported sampler '" + sampler + "'.");
            }
            if (!samplerMatcher.group(1).equals("sampler2D")) {
                throw new IOException(programName + " sampler '" + sampler + "' must use sampler2D.");
            }
            withoutSamplers.append(adapted, samplerCursor, samplerMatcher.start());
            samplerCursor = samplerMatcher.end();
        }
        withoutSamplers.append(adapted, samplerCursor, adapted.length());
        adapted = withoutSamplers.toString();
        ShaderPackUniformAdapter.AdaptedShader shaderPackUniforms =
                ShaderPackUniformAdapter.extract(adapted, programName, entityColorAvailable);
        adapted = shaderPackUniforms.source();
        if (UNSUPPORTED_UNIFORM_DECLARATION.matcher(ShaderSource.maskBlockAndLineComments(adapted)).find()) {
            throw new IOException(programName + " has an unsupported uniform declaration.");
        }

        adapted = UnusedVaryingDeclarations.removeUnused(expandCommaVaryings(adapted));
        Matcher varyingMatcher = VARYING_DECLARATION.matcher(ShaderSource.maskBlockAndLineComments(adapted));
        StringBuffer withoutVaryings = new StringBuffer();
        StringBuilder linkedVertexInputs = new StringBuilder();
        Set<String> linkedInterfaceLocations = new java.util.HashSet<>();
        while (varyingMatcher.find()) {
            String type = varyingMatcher.group(4);
            String name = varyingMatcher.group(5);
            String interpolation = mergeInterpolation(varyingMatcher.group(2), varyingMatcher.group(3),
                    programName, name);
            if (vertexOutputs != null) {
                TerrainVertexShaderAdapter.PackVarying vertexOutput = findVertexOutput(vertexOutputs, name);
                if (vertexOutput == null) {
                    throw new IOException(programName + " input '" + name
                            + "' is not written by its gbuffers vertex shader.");
                }
                if (!vertexOutput.type().equals(type)) {
                    throw new IOException(programName + " input '" + name + "' uses " + type
                            + " but its vertex output uses " + vertexOutput.type() + ".");
                }
                if (!vertexOutput.interpolation().equals(interpolation)) {
                    throw new IOException(programName + " input '" + name
                            + "' has an interpolation qualifier that does not match its vertex output.");
                }
                String declaredLocation = varyingMatcher.group(1);
                if (declaredLocation != null && Integer.parseInt(declaredLocation) != vertexOutput.location()) {
                    throw new IOException(programName + " input '" + name
                            + "' declares a location that does not match its vertex output.");
                }
                String fragmentVariable = canonicalFragmentVariable(name);
                String interfaceKey = Integer.toString(vertexOutput.location());
                if (!linkedInterfaceLocations.add(interfaceKey)) {
                    throw new IOException(programName + " declares more than one fragment input for location "
                            + vertexOutput.location() + ".");
                }
                linkedVertexInputs.append(parserSafeLocatedDeclaration(
                        "VEYRA_BASIC_FRAGMENT_INPUT_" + vertexOutput.location() + "_" + sanitize(name),
                        "layout(location = " + vertexOutput.location() + ") "
                                + (vertexOutput.interpolation().equals("smooth") ? ""
                                        : vertexOutput.interpolation() + " ")
                                + "in " + type + " " + fragmentVariable + ";"));
                varyingMatcher.appendReplacement(withoutVaryings, "");
                continue;
            }
            String expectedType = INPUT_TYPES.get(name);
            if (expectedType == null) {
                throw new IOException(programName + " input '" + name + "' is not provided by Veyra's texture-distance bridge yet.");
            }
            if (!hasVertexColor && COLOR_INPUTS.contains(name)) {
                throw new IOException(programName + " does not receive vertex color from Minecraft's vertex shader.");
            }
            if (!hasVertexDistances
                    && (name.equals("sphericalVertexDistance") || name.equals("cylindricalVertexDistance"))) {
                throw new IOException(programName + " does not receive vertex distances from Minecraft's vertex shader.");
            }
            if (!expectedType.equals(type)) {
                throw new IOException(programName + " input '" + name + "' must use " + expectedType + ".");
            }
            if (COLOR_INPUTS.contains(name)) {
                if (explicitVertexLocations && !interpolation.equals(vertexColorInterpolation)) {
                    throw new IOException(programName + " input '" + name
                            + "' interpolation does not match its vertex-stage output.");
                }
            } else if (name.equals("texcoord") || name.equals("texCoord") || name.equals("texCoord0")) {
                if (explicitVertexLocations && !interpolation.equals(texCoordInterpolation)) {
                    throw new IOException(programName + " input '" + name
                            + "' interpolation does not match its vertex-stage output.");
                }
            } else if (explicitVertexLocations && !interpolation.equals("smooth")) {
                throw new IOException(programName + " input '" + name + "' must use smooth interpolation.");
            }
            if (explicitVertexLocations && varyingMatcher.group(1) != null) {
                Integer expectedLocation = switch (name) {
                    case "color", "glcolor", "glColor", "gl_Color", "vertexColor" -> 0;
                    case "texcoord", "texCoord", "texCoord0" -> 1;
                    case "sphericalVertexDistance" -> 2;
                    case "cylindricalVertexDistance" -> 3;
                    default -> null;
                };
                if (expectedLocation == null || Integer.parseInt(varyingMatcher.group(1)) != expectedLocation) {
                    throw new IOException(programName + " input '" + name + "' declares a location that does not match Minecraft's glint vertex interface.");
                }
            }
            varyingMatcher.appendReplacement(withoutVaryings, "");
        }
        varyingMatcher.appendTail(withoutVaryings);
        adapted = withoutVaryings.toString();
        if (UNSUPPORTED_INPUT_DECLARATION.matcher(adapted).find()) {
            throw new IOException(programName + " declares inputs that are not provided by Veyra's texture-distance bridge yet.");
        }

        String outputName = outputLayout.variables().getOrDefault(0, "veyraTextureFragColor");
        Matcher outputMatcher = OUTPUT_DECLARATION.matcher(adapted);
        StringBuffer withoutOutputs = new StringBuffer();
        while (outputMatcher.find()) {
            String location = outputMatcher.group(1);
            outputMatcher.appendReplacement(withoutOutputs, "");
        }
        outputMatcher.appendTail(withoutOutputs);
        adapted = withoutOutputs.toString();
        if (UNSUPPORTED_OUTPUT_DECLARATION.matcher(adapted).find()) {
            throw new IOException(programName + " supports vec4 fragment outputs at locations 0..7.");
        }

        String uncommented = COMMENTS.matcher(adapted).replaceAll("");
        if (UNSUPPORTED_TEXTURE_FUNCTION.matcher(uncommented).find()) {
            throw new IOException(programName + " uses a texture sampling function that Veyra's texture-distance bridge does not support yet.");
        }
        adapted = adapted
                .replaceAll("\\btexture2DLod(?:EXT)?\\s*\\(", "veyraTextureSampleLod(")
                .replaceAll("\\btextureLod\\s*\\(", "veyraTextureSampleLod(")
                .replaceAll("\\btexture2DGrad(?:ARB)?\\s*\\(", "veyraTextureSampleGrad(")
                .replaceAll("\\btextureGrad\\s*\\(", "veyraTextureSampleGrad(")
                .replaceAll("\\btexture2D\\s*\\(", "veyraTextureSample(")
                .replaceAll("\\btexture\\s*\\(", "veyraTextureSample(")
                .replaceAll("\\bgl_FragColor\\b", outputName)
                .replaceAll("\\btexcoord\\b|\\btexCoord\\b", "texCoord0")
                .replaceAll("\\bcolor\\b|\\bglcolor\\b|\\bglColor\\b|\\bgl_Color\\b", "vertexColor");

        String distanceInputs = vertexOutputs != null ? linkedVertexInputs.toString() : hasVertexDistances ? explicitVertexLocations
                ? parserSafeLocatedDeclaration("VEYRA_GLINT_SPHERICAL_DISTANCE",
                        "layout(location = 2) in float sphericalVertexDistance;")
                    + parserSafeLocatedDeclaration("VEYRA_GLINT_CYLINDRICAL_DISTANCE",
                        "layout(location = 3) in float cylindricalVertexDistance;") : """
                in float sphericalVertexDistance;
                in float cylindricalVertexDistance;
                """ : "";
        String colorInput = vertexOutputs != null ? "" : hasVertexColor ? explicitVertexLocations
                ? parserSafeLocatedDeclaration("VEYRA_GLINT_COLOR_INPUT",
                        "layout(location = 0) " + vertexColorInterpolation + " in vec4 vertexColor;")
                : "in vec4 vertexColor;\n" : "";
        String texCoordInput = vertexOutputs != null ? "" : explicitVertexLocations
                ? parserSafeLocatedDeclaration("VEYRA_GLINT_TEXCOORD_INPUT",
                        "layout(location = 1) " + texCoordInterpolation + " in vec2 texCoord0;")
                : "in vec2 texCoord0;\n";
        String output = outputLayout.entityPipelineDeclarations();
        String bridge = """
                %s%s
                %s%s
                uniform sampler2D Sampler0;
                vec4 veyraTextureSample(sampler2D sourceSampler, vec2 sourceUv) {
                    return texture(sourceSampler, sourceUv);
                }
                vec4 veyraTextureSampleLod(sampler2D sourceSampler, vec2 sourceUv, float lod) {
                    return textureLod(sourceSampler, sourceUv, lod);
                }
                vec4 veyraTextureSampleGrad(sampler2D sourceSampler, vec2 sourceUv, vec2 dx, vec2 dy) {
                    return textureGrad(sourceSampler, sourceUv, dx, dy);
                }
                """.formatted(distanceInputs, colorInput, texCoordInput, output)
                + samplers.bridge()
                + "#define texture Sampler0\n#define gtexture Sampler0\n#define tex Sampler0\n";
        StringBuilder result = new StringBuilder("#version 460\n");
        for (String extension : extensions) {
            result.append(extension).append('\n');
        }
        result.append(bridge);
        result.append(fog.bridge());
        result.append(shaderPackUniforms.declarations());
        result.append(shaderPackUniforms.macros());
        return result.append(adapted).toString();
    }

    private static SamplerBridge adaptSamplers(String source, String programName, boolean allowIrisSamplers,
                                               boolean shadowPassAvailable, boolean lightmapSamplerAvailable)
            throws IOException {
        Map<String, Integer> geometryInputs = allowIrisSamplers
                ? GeometryColorSamplers.read(source) : Map.of();
        Set<Integer> geometryIndices = GeometryColorSamplers.indices(geometryInputs);
        if (allowIrisSamplers) {
            ShadowColorSampler.validate(source, shadowPassAvailable, geometryIndices.size());
            ShadowDepthSampler.validate(source, shadowPassAvailable, geometryIndices.size());
            if (EntityShaderAdapter.usesShadowSampler(source) && !shadowPassAvailable) {
                throw new IOException(programName + " samples shadowtex0, but this shader pack has no supported shadow pass.");
            }
        }

        Matcher matcher = SAMPLER_DECLARATION.matcher(ShaderSource.maskBlockAndLineComments(source));
        StringBuilder stripped = new StringBuilder(source.length());
        int copiedThrough = 0;
        boolean usesLightmap = false;
        boolean usesSampler2DShadow = false;
        while (matcher.find()) {
            String type = matcher.group(1);
            String name = matcher.group(2);
            boolean recognized = name.equals("texture") || name.equals("gtexture") || name.equals("tex")
                    || name.equals("Sampler0");
            boolean irisSampler = allowIrisSamplers && (name.equals("lightmap") || name.equals("noisetex")
                    || name.equals("specular") || name.equals("shadowtex0") || name.equals("shadowtex1")
                    || ShadowColorSampler.isSampler(name)
                    || geometryInputs.containsKey(name));
            if (!recognized && !irisSampler) {
                throw new IOException(programName + " uses unsupported sampler '" + name + "'.");
            }
            boolean shadowCompare = allowIrisSamplers
                    && (name.equals("shadowtex0") || name.equals("shadowtex1"))
                    && type.equals("sampler2DShadow");
            if (!type.equals("sampler2D") && !shadowCompare) {
                throw new IOException(programName + " sampler '" + name + "' must use sampler2D.");
            }
            if (name.equals("lightmap")) usesLightmap = true;
            if (shadowCompare) usesSampler2DShadow = true;
            stripped.append(source, copiedThrough, matcher.start());
            copiedThrough = matcher.end();
        }
        stripped.append(source, copiedThrough, source.length());

        String uncommented = ShaderSource.maskBlockAndLineComments(stripped.toString());
        usesLightmap |= allowIrisSamplers && LIGHTMAP_TEXTURE_SAMPLE.matcher(uncommented).find();
        if (usesLightmap && !lightmapSamplerAvailable) {
            throw new IOException(programName + " requires Minecraft's lightmap sampler, which this pipeline does not provide.");
        }
        boolean usesNoise = allowIrisSamplers && TerrainShaderAdapter.usesNoiseSampler(uncommented);
        boolean usesSpecular = allowIrisSamplers && TerrainShaderAdapter.usesSpecularSampler(uncommented);
        boolean usesShadow = allowIrisSamplers && EntityShaderAdapter.usesShadowSampler(uncommented);
        boolean usesOpaqueShadow = allowIrisSamplers && ShadowDepthSampler.used(uncommented);
        Set<Integer> shadowColorInputs = allowIrisSamplers
                ? ShadowColorSampler.indices(uncommented) : Set.of();
        boolean usesShadowColor = !shadowColorInputs.isEmpty();
        StringBuilder bridge = new StringBuilder();
        if (usesLightmap) bridge.append("uniform sampler2D Sampler2;\n#define lightmap Sampler2\n");
        if (usesNoise) bridge.append("uniform sampler2D VeyraNoiseSampler;\n#define noisetex VeyraNoiseSampler\n");
        if (usesSpecular) bridge.append("uniform sampler2D VeyraSpecularSampler;\n#define specular VeyraSpecularSampler\n");
        if (usesShadow) bridge.append("uniform sampler2D Sampler3;\n#define shadowtex0 Sampler3\n");
        if (usesOpaqueShadow) bridge.append("uniform sampler2D ").append(ShadowDepthSampler.NAME)
                .append(";\n#define shadowtex1 ").append(ShadowDepthSampler.NAME).append('\n');
        bridge.append(ShadowColorSampler.bridge(shadowColorInputs));
        for (Map.Entry<String, Integer> input : geometryInputs.entrySet()) {
            bridge.append("uniform sampler2D ").append(GeometryColorSamplers.samplerName(input.getValue()))
                    .append(";\n#define ").append(input.getKey()).append(' ')
                    .append(GeometryColorSamplers.samplerName(input.getValue())).append('\n');
        }
        if (usesSampler2DShadow) {
            bridge.append("""
                    float veyraTextureShadowCompare(sampler2D sourceSampler, vec3 sourceCoord) {
                        return step(sourceCoord.z - 0.0005, texture(sourceSampler, sourceCoord.xy).r);
                    }
                    vec4 veyraTextureShadowSample(sampler2D sourceSampler, vec3 sourceCoord) {
                        return vec4(veyraTextureShadowCompare(sourceSampler, sourceCoord));
                    }
                    vec4 veyraTextureSample(sampler2D sourceSampler, vec3 sourceCoord) {
                        return veyraTextureShadowSample(sourceSampler, sourceCoord);
                    }
                    """);
        }
        String result = renamePackShadowComparisonFunction(stripped.toString())
                .replaceAll("\\bsampler2DShadow\\b", "sampler2D")
                .replaceAll("\\btexture2DShadow\\s*\\(", "veyraTextureShadowCompare(")
                .replaceAll("\\bshadow2D\\s*\\(", "veyraTextureShadowSample(");
        return new SamplerBridge(result, bridge.toString(), new SamplerInputs(
                usesNoise, usesShadow, usesSpecular, usesLightmap, usesShadowColor, usesOpaqueShadow,
                Set.copyOf(geometryIndices), Set.of(), Set.copyOf(shadowColorInputs)));
    }

    private static String renamePackShadowComparisonFunction(String source) {
        String analysis = ShaderSource.maskBlockAndLineComments(source);
        Pattern declaration = Pattern.compile(
                "\\b(?!(?:return|if|while|for|switch|case)\\b)[A-Za-z_][A-Za-z0-9_]*\\s+texture2DShadow\\s*\\(");
        if (!declaration.matcher(analysis).find()) return source;

        String aliasBase = "veyraShaderPackTexture2DShadow";
        String alias = aliasBase;
        int suffix = 2;
        while (Pattern.compile("\\b" + Pattern.quote(alias) + "\\b").matcher(analysis).find()) {
            alias = aliasBase + "_" + suffix++;
        }

        List<Integer> offsets = new ArrayList<>();
        Matcher references = Pattern.compile("\\btexture2DShadow\\b(?=\\s*\\()").matcher(analysis);
        while (references.find()) offsets.add(references.start());
        StringBuilder renamed = new StringBuilder(source);
        for (int index = offsets.size() - 1; index >= 0; index--) {
            int start = offsets.get(index);
            renamed.replace(start, start + "texture2DShadow".length(), alias);
        }
        return renamed.toString();
    }

    static SamplerInputs inspectSamplers(String source, String programName,
                                         boolean shadowPassAvailable, boolean lightmapSamplerAvailable)
            throws IOException {
        String active = GlslPreprocessor.process(programName + ".fsh", source.replace("\uFEFF", ""));
        active = UnreachableGlslFunctions.removeUnreachable(active, "main");
        active = UnusedSamplerDeclarations.removeUnused(active);
        return adaptSamplers(active, programName, true, shadowPassAvailable, lightmapSamplerAvailable).inputs();
    }

    record SamplerInputs(boolean usesNoiseSampler, boolean usesShadowSampler, boolean usesSpecularSampler,
                         boolean usesLightmapSampler, boolean usesShadowColor, boolean usesOpaqueShadow,
                         Set<Integer> geometryColorInputs, Set<String> customImageSamplers,
                         Set<Integer> shadowColorInputs) {
        static SamplerInputs empty() {
            return new SamplerInputs(false, false, false, false, false, false, Set.of(), Set.of(), Set.of());
        }

        SamplerInputs withCustomImageSamplers(Set<String> samplers) {
            return new SamplerInputs(usesNoiseSampler, usesShadowSampler, usesSpecularSampler,
                    usesLightmapSampler, usesShadowColor, usesOpaqueShadow,
                    geometryColorInputs, Set.copyOf(samplers), shadowColorInputs);
        }
    }

    private record SamplerBridge(String source, String bridge, SamplerInputs inputs) { }

    /** VulkanMod's RenderSystem parser treats a source-level {@code layout(...)} as a UBO declaration. */
    private static String parserSafeLocatedDeclaration(String macroName, String declaration) {
        return "#define " + macroName + " " + declaration + '\n' + macroName + '\n';
    }

    private static TerrainVertexShaderAdapter.PackVarying findVertexOutput(
            Map<String, TerrainVertexShaderAdapter.PackVarying> outputs, String name) {
        TerrainVertexShaderAdapter.PackVarying direct = outputs.get(name);
        if (direct != null) return direct;
        Set<String> aliases = switch (name) {
            case "color", "glcolor", "glColor", "gl_Color", "vertexColor" ->
                    Set.of("color", "glcolor", "glColor", "gl_Color", "vertexColor");
            case "texcoord", "texCoord", "texCoord0" -> Set.of("texcoord", "texCoord", "texCoord0");
            case "lmcoord", "lmCoord", "lightMapCoord" -> Set.of("lmcoord", "lmCoord", "lightMapCoord");
            default -> Set.of(name);
        };
        return aliases.stream().map(outputs::get).filter(java.util.Objects::nonNull).findFirst().orElse(null);
    }

    private static String canonicalFragmentVariable(String name) {
        return switch (name) {
            case "color", "glcolor", "glColor", "gl_Color", "vertexColor" -> "vertexColor";
            case "texcoord", "texCoord", "texCoord0" -> "texCoord0";
            default -> name;
        };
    }

    private static String expandCommaVaryings(String source) {
        Matcher matcher = COMMA_VARYING_DECLARATION.matcher(source);
        StringBuilder expanded = new StringBuilder(source.length());
        int copiedThrough = 0;
        while (matcher.find()) {
            expanded.append(source, copiedThrough, matcher.start());
            String prefix = matcher.group(1);
            String suffix = matcher.group(3);
            for (String name : matcher.group(2).split(",")) {
                expanded.append(prefix).append(name.trim()).append(suffix);
            }
            copiedThrough = matcher.end();
        }
        return expanded.append(source, copiedThrough, source.length()).toString();
    }

    private static String sanitize(String name) {
        return name.replaceAll("[^A-Za-z0-9_]", "_");
    }

    private static String mergeInterpolation(String first, String second, String programName, String name)
            throws IOException {
        if (first != null && second != null && !first.equals(second)) {
            throw new IOException(programName + " input '" + name + "' has conflicting interpolation qualifiers.");
        }
        return first != null ? first : second != null ? second : "smooth";
    }

}
