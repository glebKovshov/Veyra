package dev.veyra.shader;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Executes a supported shader-pack terrain vertex entrypoint after VulkanMod's terrain setup. */
final class TerrainVertexShaderAdapter {
    private static final Pattern VERSION_DIRECTIVE = Pattern.compile("(?m)^[\\t ]*#version[^\\r\\n]*(?:\\r?\\n|$)");
    private static final Pattern EXTENSION_DIRECTIVE = Pattern.compile("(?m)^[\\t ]*#extension[^\\r\\n]*(?:\\r?\\n|$)");
    private static final Pattern PRECISION_DECLARATION = Pattern.compile(
            "(?m)^[\\t ]*precision\\s+(?:lowp|mediump|highp)\\s+\\w+\\s*;[\\t ]*(?:\\r?\\n|$)");
    private static final Pattern ATTRIBUTE_DECLARATION = Pattern.compile(
            "(?m)^[\\t ]*(?:(?:layout\\s*\\([^)]*\\)\\s*)?)(?:(?:flat|smooth|noperspective)\\s+)?"
                    + "(?:attribute|in)\\s+([A-Za-z_][A-Za-z0-9_]*)\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*;"
                    + "[\\t ]*(?://[^\\r\\n]*)?(?:\\r?\\n|$)");
    private static final Pattern OUTPUT_DECLARATION = Pattern.compile(
            "(?m)^[\\t ]*(?:layout\\s*\\(\\s*location\\s*=\\s*(\\d+)\\s*\\)\\s*)?"
                    + "(?:(flat|smooth|noperspective)\\s+)?(?:varying|out)\\s+"
                    + "(?:(flat|smooth|noperspective)\\s+)?"
                    + "([A-Za-z_][A-Za-z0-9_]*)\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*;"
                    + "[\\t ]*(?://[^\\r\\n]*)?(?:\\r?\\n|$)");
    private static final Pattern UNSUPPORTED_OUTPUT_DECLARATION = Pattern.compile(
            "(?m)(?:^|;)[\\t ]*(?:layout\\s*\\([^)]*\\)\\s*)?"
                    + "(?:(?:flat|smooth|noperspective|centroid|sample|invariant)\\s+)*"
                    + "(?:varying|out)\\b[^\\r\\n;]*;");
    private static final Pattern SAMPLER_DECLARATION = Pattern.compile(
            "(?m)^[\\t ]*(?:layout\\s*\\([^)]*\\)\\s*)?uniform\\s+"
                    + "(sampler\\w+)\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*;"
                    + "[\\t ]*(?://[^\\r\\n]*)?(?:\\r?\\n|$)");
    private static final Pattern COMMA_DECLARATION = Pattern.compile(
            "(?m)^([\\t ]*(?:(?:layout\\s*\\([^)]*\\)\\s*)?)(?:(?:flat|smooth|noperspective)\\s+)?"
                    + "(?:varying|out|attribute|in)\\s+[A-Za-z_][A-Za-z0-9_]*\\s+)"
                    + "([A-Za-z_][A-Za-z0-9_]*(?:\\s*,\\s*[A-Za-z_][A-Za-z0-9_]*)+)\\s*;"
                    + "([\\t ]*(?://[^\\r\\n]*)?(?:\\r?\\n|$))");
    private static final Pattern MAIN_DECLARATION = Pattern.compile("(?m)\\bvoid\\s+main\\s*\\(\\s*\\)");
    private static final Pattern UNSUPPORTED_LEGACY_INPUT = Pattern.compile(
            "\\b(gl_MultiTexCoord[2-7]|mc_midBlock)\\b");
    private static final String LIGHTMAP_TEXTURE_MATRIX = "mat4("
            + "0.00390625, 0.0, 0.0, 0.0, "
            + "0.0, 0.00390625, 0.0, 0.0, "
            + "0.0, 0.0, 0.00390625, 0.0, "
            + "0.03125, 0.03125, 0.03125, 1.0)";

    private TerrainVertexShaderAdapter() {
    }

    static void validate(String programName, String shaderPackSource) throws IOException {
        prepare(programName, shaderPackSource, 6, Set.of());
    }

    static Map<String, PackVarying> validateAndGetVaryings(String programName, String shaderPackSource)
            throws IOException {
        return validateAndGetVaryings(programName, shaderPackSource, Set.of());
    }

    static Map<String, PackVarying> validateAndGetVaryings(String programName, String shaderPackSource,
                                                            Set<Integer> freeOutputLocations) throws IOException {
        return prepare(programName, shaderPackSource, 6, freeOutputLocations).varyings();
    }

    static Map<String, PackVarying> describeVaryings(String programName, String shaderPackSource)
            throws IOException {
        String source = expandCommaDeclarations(GlslPreprocessor.process(programName + ".vsh", shaderPackSource));
        Matcher matcher = OUTPUT_DECLARATION.matcher(ShaderSource.maskBlockAndLineComments(source));
        Map<String, PackVarying> varyings = new java.util.LinkedHashMap<>();
        while (matcher.find()) {
            String type = matcher.group(4);
            String name = matcher.group(5);
            String interpolation = mergeInterpolation(matcher.group(2), matcher.group(3), programName, name);
            FixedOutput fixed = fixedOutput(name, type, interpolation);
            if (varyings.putIfAbsent(name, new PackVarying(type, interpolation,
                    fixed == null ? -1 : fixed.location(),
                    fixed == null ? "veyraPackVarying_" + name : fixed.vertexVariable(), fixed != null)) != null) {
                throw new IOException(programName + ".vsh declares vertex output '" + name + "' more than once.");
            }
        }
        return Map.copyOf(varyings);
    }

    static boolean usesNoiseSampler(String programName, String shaderPackSource) throws IOException {
        String source = GlslPreprocessor.process(programName + ".vsh", shaderPackSource);
        return TerrainShaderAdapter.usesNoiseSampler(source);
    }

    static String adapt(String vulkanModSource, String shaderPackSource, String programName) throws IOException {
        return adaptStage(vulkanModSource, shaderPackSource, programName).source();
    }

    /**
     * Preserve OpenGL shadow texture coordinates under VulkanMod's negative-height viewport,
     * and map OpenGL clip depth [-w, w] to Vulkan's [0, w].
     */
    static final String SHADOW_CLIP_REMAP = "    gl_Position.y = -gl_Position.y;\n"
            + "    gl_Position.z = (gl_Position.z + gl_Position.w) * 0.5;\n";

    static AdaptedStage adaptStage(String vulkanModSource, String shaderPackSource, String programName) throws IOException {
        return adaptStage(vulkanModSource, shaderPackSource, programName, 6);
    }

    static AdaptedStage adaptStage(String vulkanModSource, String shaderPackSource, String programName,
                                   int noiseSamplerBinding) throws IOException {
        return adaptStage(vulkanModSource, shaderPackSource, programName, noiseSamplerBinding, Set.of());
    }

    static AdaptedStage adaptStage(String vulkanModSource, String shaderPackSource, String programName,
                                   int noiseSamplerBinding, Set<Integer> freeOutputLocations) throws IOException {
        PreparedStage pack = prepare(programName, shaderPackSource, noiseSamplerBinding, freeOutputLocations);
        String source = TerrainShaderAdapter.renameConflictingShaderPackUniformFields(
                vulkanModSource, pack.source());
        Matcher version = VERSION_DIRECTIVE.matcher(source);
        if (!version.find()) {
            throw new IOException("VulkanMod's terrain vertex shader has no #version directive.");
        }
        StringBuilder extensions = new StringBuilder();
        pack.extensions().forEach(extension -> extensions.append(extension).append('\n'));
        source = source.substring(0, version.end()) + extensions + source.substring(version.end());

        MainFunction baseMain = findMain(source, "VulkanMod terrain");
        source = source.substring(0, baseMain.start()) + "void veyraShaderPackMain();\n"
                + source.substring(baseMain.start());
        baseMain = findMain(source, "VulkanMod terrain");
        source = source.substring(0, baseMain.closeBrace()) + "\n    veyraShaderPackMain();\n"
                + (programName.startsWith("shadow") ? SHADOW_CLIP_REMAP : "")
                + source.substring(baseMain.closeBrace());
        return new AdaptedStage(source + "\n" + pack.uniformMacros() + legacyInputBridge(programName) + pack.outputBridge() + "\n"
                + pack.source().replaceFirst("(?m)\\bvoid\\s+main\\s*\\(", "void veyraShaderPackMain("),
                pack.varyings());
    }

    private static PreparedStage prepare(String programName, String shaderPackSource, int noiseSamplerBinding,
                                        Set<Integer> freeOutputLocations) throws IOException {
        if (shaderPackSource == null || shaderPackSource.isBlank()) {
            throw new IOException(programName + ".vsh is empty.");
        }
        if (shaderPackSource.length() > 1024 * 1024) {
            throw new IOException(programName + ".vsh exceeds the 1 MiB size limit.");
        }

        String source = GlslPreprocessor.process(programName + ".vsh", shaderPackSource);
        source = IrisRenderStages.bindUniform(source, IrisRenderStages.forProgram(programName), programName);
        List<String> extensions = new ArrayList<>();
        Matcher extensionMatcher = EXTENSION_DIRECTIVE.matcher(source);
        while (extensionMatcher.find()) extensions.add(extensionMatcher.group().strip());
        source = extensionMatcher.replaceAll("");
        source = VERSION_DIRECTIVE.matcher(source).replaceAll("");
        source = PRECISION_DECLARATION.matcher(source).replaceAll("");
        source = Pattern.compile("\\bgl_TextureMatrix\\s*\\[\\s*0\\s*\\]")
                .matcher(source).replaceAll("textureMatrix");
        source = Pattern.compile("\\bgl_TextureMatrix\\s*\\[\\s*1\\s*\\]")
                .matcher(source).replaceAll(Matcher.quoteReplacement(LIGHTMAP_TEXTURE_MATRIX));
        source = expandCommaDeclarations(source);
        source = source.replaceAll("\\bgl_VertexID\\b", "gl_VertexIndex");
        String uncommented = ShaderSource.maskBlockAndLineComments(source);
        if (noiseSamplerBinding != 5 && noiseSamplerBinding != 6) {
            throw new IOException("Veyra's terrain noisetex sampler must use binding 5 or 6.");
        }

        Matcher unsupportedInput = UNSUPPORTED_LEGACY_INPUT.matcher(uncommented);
        if (unsupportedInput.find()) {
            throw new IOException(programName + ".vsh input '" + unsupportedInput.group(1)
                    + "' is not present in VulkanMod's terrain vertex format yet.");
        }
        if (Pattern.compile("\\bgl_TextureMatrix\\s*\\[").matcher(uncommented).find()) {
            throw new IOException(programName + ".vsh uses a texture matrix index other than 0 or 1, which Veyra does not expose.");
        }

        IrisFogUniformAdapter.AdaptedShader fog = IrisFogUniformAdapter.extract(source, programName, true);
        source = UnusedSamplerDeclarations.removeUnused(fog.source());
        SamplerBridge samplers = adaptSamplers(programName, source, noiseSamplerBinding);
        source = samplers.source();
        ShaderPackUniformAdapter.AdaptedShader uniforms = ShaderPackUniformAdapter.extract(source, programName);
        source = uniforms.source();
        OutputBridge outputBridge = extractOutputs(programName, source, freeOutputLocations);
        source = outputBridge.source();

        Matcher attributes = ATTRIBUTE_DECLARATION.matcher(ShaderSource.maskBlockAndLineComments(source));
        StringBuffer withoutAttributes = new StringBuffer();
        List<String> declaredAttributes = new ArrayList<>();
        String midBlockType = null;
        while (attributes.find()) {
            String type = attributes.group(1);
            String name = attributes.group(2);
            String expected = switch (name) {
                case "mc_Entity", "mc_midTexCoord", "at_tangent" -> "vec4";
                case "at_midBlock" -> type.equals("vec4") ? "vec4" : "vec3";
                default -> null;
            };
            if (expected == null) {
                throw new IOException(programName + ".vsh attribute '" + name
                        + "' is not present in VulkanMod's terrain vertex format yet.");
            }
            if (!type.equals(expected)) {
                throw new IOException(programName + ".vsh attribute '" + name + "' must use " + expected + ".");
            }
            if (declaredAttributes.contains(name)) {
                throw new IOException(programName + ".vsh declares attribute '" + name + "' more than once.");
            }
            declaredAttributes.add(name);
            if (name.equals("at_midBlock")) midBlockType = type;
            attributes.appendReplacement(withoutAttributes, "");
        }
        attributes.appendTail(withoutAttributes);
        source = withoutAttributes.toString();
        if (midBlockType != null) {
            source = source.replaceAll("\\bat_midBlock\\b",
                    midBlockType.equals("vec4") ? "VeyraTerrainMidBlock" : "VeyraTerrainMidBlock.xyz");
        } else if (Pattern.compile("\\bat_midBlock\\b").matcher(ShaderSource.maskBlockAndLineComments(source)).find()) {
            throw new IOException(programName + ".vsh uses at_midBlock without a vec3 or vec4 attribute declaration.");
        }

        MainFunction main = findMain(source, programName + ".vsh");
        if (MAIN_DECLARATION.matcher(ShaderSource.maskBlockAndLineComments(source)).find()
                && MAIN_DECLARATION.matcher(ShaderSource.maskBlockAndLineComments(source)).results().count() != 1) {
            throw new IOException(programName + ".vsh must contain exactly one active main() entrypoint.");
        }
        String uniformMacros = fog.bridge() + uniforms.macros() + samplers.declarationsAndMacros();
        return new PreparedStage(source, List.copyOf(extensions), uniformMacros,
                outputBridge.declarationsAndMacros(), outputBridge.varyings());
    }

    private static SamplerBridge adaptSamplers(String programName, String source, int noiseSamplerBinding)
            throws IOException {
        Matcher matcher = SAMPLER_DECLARATION.matcher(ShaderSource.maskBlockAndLineComments(source));
        StringBuilder withoutSamplers = new StringBuilder(source.length());
        int copiedThrough = 0;
        int noiseDeclarations = 0;
        Set<String> nativeSamplers = new HashSet<>();
        while (matcher.find()) {
            String type = matcher.group(1);
            String name = matcher.group(2);
            boolean nativeSampler = Set.of("tex", "texture", "gtexture", "lightmap").contains(name);
            if (!name.equals("noisetex") && !nativeSampler) {
                throw new IOException(programName + ".vsh uses unsupported sampler '" + name + "'.");
            }
            if (!type.equals("sampler2D")) {
                throw new IOException(programName + ".vsh sampler '" + name + "' must use sampler2D.");
            }
            if (nativeSampler && !nativeSamplers.add(name)) {
                throw new IOException(programName + ".vsh declares sampler '" + name + "' more than once.");
            }
            if (!nativeSampler && ++noiseDeclarations > 1) {
                throw new IOException(programName + ".vsh declares sampler 'noisetex' more than once.");
            }
            withoutSamplers.append(source, copiedThrough, matcher.start());
            copiedThrough = matcher.end();
        }
        withoutSamplers.append(source, copiedThrough, source.length());

        String adapted = withoutSamplers.toString()
                .replaceAll("\\btexture2DLod(?:EXT)?\\s*\\(", "textureLod(")
                .replaceAll("\\btexture2DGrad(?:ARB)?\\s*\\(", "textureGrad(")
                .replaceAll("\\btexture2DProj\\s*\\(", "textureProj(")
                .replaceAll("\\btexture2D\\s*\\(", "texture(");
        for (String name : nativeSamplers) {
            // The legacy variable named texture must not replace the modern texture() function.
            adapted = adapted.replaceAll("\\b" + name + "\\b" + (name.equals("texture") ? "(?!\\s*\\()" : ""),
                    name.equals("lightmap") ? "VeyraTerrainVertexLightmap" : "VeyraTerrainVertexAlbedo");
        }
        boolean usesNoiseSampler = TerrainShaderAdapter.usesNoiseSampler(adapted);
        String declarationsAndMacros = usesNoiseSampler
                ? "layout(binding = " + noiseSamplerBinding + ") uniform sampler2D VeyraNoiseSampler;\n"
                + "#define noisetex VeyraNoiseSampler\n"
                : "";
        if (nativeSamplers.stream().anyMatch(name -> !name.equals("lightmap"))) {
            declarationsAndMacros += "layout(binding = 3) uniform sampler2D VeyraTerrainVertexAlbedo;\n";
        }
        if (nativeSamplers.contains("lightmap")) {
            declarationsAndMacros += "layout(binding = 4) uniform sampler2D VeyraTerrainVertexLightmap;\n";
        }
        return new SamplerBridge(adapted, declarationsAndMacros);
    }

    private static OutputBridge extractOutputs(String programName, String source, Set<Integer> freeOutputLocations) throws IOException {
        String masked = ShaderSource.maskBlockAndLineComments(source);
        Matcher matcher = OUTPUT_DECLARATION.matcher(masked);
        StringBuilder outputSource = new StringBuilder(source.length());
        StringBuilder declarationsAndMacros = new StringBuilder();
        Map<String, PackVarying> varyings = new java.util.LinkedHashMap<>();
        int copiedThrough = 0;
        int nextLocation = 4;
        Set<Integer> occupiedLocations = new HashSet<>();
        // VulkanMod's terrain vertex base stage always writes its distance outputs at locations 2 and 3.
        // Unlike the optional Veyra bridge outputs, those locations are never pruned.
        for (int location = 0; location <= 3; location++) occupiedLocations.add(location);
        for (int location = 4; location <= 19; location++) occupiedLocations.add(location);
        occupiedLocations.removeAll(freeOutputLocations);
        // Prefer pruned bridge slots to high locations, which also count toward Vulkan's
        // interface-component limit. Reserve fixed aliases before allocating custom outputs.
        Matcher fixedMatcher = OUTPUT_DECLARATION.matcher(masked);
        while (fixedMatcher.find()) {
            String interpolation = mergeInterpolation(fixedMatcher.group(2), fixedMatcher.group(3),
                    programName, fixedMatcher.group(5));
            FixedOutput fixed = fixedOutput(fixedMatcher.group(5), fixedMatcher.group(4), interpolation);
            if (fixed != null) occupiedLocations.add(fixed.location());
        }
        while (matcher.find()) {
            String interpolation = mergeInterpolation(matcher.group(2), matcher.group(3),
                    programName, matcher.group(5));
            String type = matcher.group(4);
            String name = matcher.group(5);
            if (name.equals("mc_Entity")) {
                throw new IOException(programName
                        + ".vsh cannot redeclare mc_Entity as an output because it is the terrain material vertex input.");
            }
            if (!isSupportedVaryingType(type)) {
                throw new IOException(programName + ".vsh output '" + name
                        + "' uses unsupported varying type " + type + ".");
            }
            if (name.startsWith("gl_")) {
                throw new IOException(programName + ".vsh cannot redeclare the reserved GLSL output '" + name + "'.");
            }
            if (varyings.containsKey(name)) {
                throw new IOException(programName + ".vsh declares vertex output '" + name + "' more than once.");
            }
            if (isIntegerVaryingType(type) && !interpolation.equals("flat")) {
                throw new IOException(programName + ".vsh integer output '" + name + "' must use flat interpolation.");
            }

            FixedOutput fixed = fixedOutput(name, type, interpolation);
            int locationSpan = varyingLocationSpan(type);
            int location = fixed == null ? allocateOutputLocation(occupiedLocations, nextLocation, locationSpan)
                    : fixed.location();
            if (location < 0) {
                throw new IOException(programName + ".vsh has too many vertex outputs for Veyra's terrain interface.");
            }
            if (fixed == null) {
                for (int occupied = location; occupied < location + locationSpan; occupied++) {
                    occupiedLocations.add(occupied);
                }
                if (location >= 20) nextLocation = location + locationSpan;
            }
            String vertexVariable = fixed == null ? "veyraPackVarying_" + name : fixed.vertexVariable();
            varyings.put(name, new PackVarying(type, interpolation, location, vertexVariable, fixed != null));
            if (fixed == null) {
                declarationsAndMacros.append("layout(location = ").append(location).append(") ");
                if (!interpolation.equals("smooth")) declarationsAndMacros.append(interpolation).append(' ');
                declarationsAndMacros.append("out ").append(type).append(' ').append(vertexVariable).append(";\n");
            }
            if (!name.equals(vertexVariable)) {
                declarationsAndMacros.append("#define ").append(name).append(' ').append(vertexVariable).append('\n');
            }
            outputSource.append(source, copiedThrough, matcher.start());
            copiedThrough = matcher.end();
        }
        outputSource.append(source, copiedThrough, source.length());
        String unparsedDeclarations = OUTPUT_DECLARATION.matcher(masked).replaceAll("");
        if (UNSUPPORTED_OUTPUT_DECLARATION.matcher(unparsedDeclarations).find()) {
            throw new IOException(programName + ".vsh declares an unsupported vertex output; use scalar or vector varyings.");
        }
        return new OutputBridge(outputSource.toString(), declarationsAndMacros.toString(), Map.copyOf(varyings));
    }

    private static int allocateOutputLocation(Set<Integer> occupiedLocations, int preferredLocation, int span) {
        int preferred = Math.max(2, preferredLocation);
        for (int start = preferred; start + span <= 32; start++) {
            if (hasFreeLocationSpan(occupiedLocations, start, span)) return start;
        }
        for (int start = 2; start < preferred && start + span <= 32; start++) {
            if (hasFreeLocationSpan(occupiedLocations, start, span)) return start;
        }
        return -1;
    }

    private static boolean hasFreeLocationSpan(Set<Integer> occupiedLocations, int start, int span) {
        for (int location = start; location < start + span; location++) {
            if (occupiedLocations.contains(location)) return false;
        }
        return true;
    }

    private static String mergeInterpolation(String first, String second, String programName, String name) throws IOException {
        if (first != null && second != null && !first.equals(second)) {
            throw new IOException(programName + ".vsh output '" + name + "' has conflicting interpolation qualifiers.");
        }
        return first != null ? first : second != null ? second : "smooth";
    }

    private static boolean isSupportedVaryingType(String type) {
        return switch (type) {
            case "float", "vec2", "vec3", "vec4", "int", "ivec2", "ivec3", "ivec4",
                    "uint", "uvec2", "uvec3", "uvec4", "mat2", "mat3", "mat4" -> true;
            default -> false;
        };
    }

    private static int varyingLocationSpan(String type) {
        return switch (type) {
            case "mat2" -> 2;
            case "mat3" -> 3;
            case "mat4" -> 4;
            default -> 1;
        };
    }

    private static boolean isIntegerVaryingType(String type) {
        return type.equals("int") || type.startsWith("ivec") || type.equals("uint") || type.startsWith("uvec");
    }

    private static FixedOutput fixedOutput(String name, String type, String interpolation) {
        FixedOutput output = FIXED_OUTPUTS.get(name);
        return output != null && output.type().equals(type) && output.interpolation().equals(interpolation)
                ? output : null;
    }

    private static final Map<String, FixedOutput> FIXED_OUTPUTS = Map.ofEntries(
            Map.entry("color", new FixedOutput("vec4", "smooth", 0, "vertexColor")),
            Map.entry("glColorRaw", new FixedOutput("vec4", "smooth", 0, "vertexColor")),
            Map.entry("texCoord", new FixedOutput("vec2", "smooth", 1, "texCoord0")),
            Map.entry("texcoord", new FixedOutput("vec2", "smooth", 1, "texCoord0")),
            Map.entry("texCoord0", new FixedOutput("vec2", "smooth", 1, "texCoord0")),
            Map.entry("lmCoord", new FixedOutput("vec2", "smooth", 5, "veyraLightmapCoord")),
            Map.entry("lmcoord", new FixedOutput("vec2", "smooth", 5, "veyraLightmapCoord")),
            Map.entry("lightMapCoord", new FixedOutput("vec2", "smooth", 5, "veyraLightmapCoord")),
            Map.entry("shadowPos", new FixedOutput("vec4", "smooth", 6, "veyraShadowPos")),
            Map.entry("shadowCoord", new FixedOutput("vec4", "smooth", 6, "veyraShadowPos")),
            Map.entry("vertexPos", new FixedOutput("vec3", "smooth", 7, "veyraTerrainPosition")),
            Map.entry("playerPos", new FixedOutput("vec3", "smooth", 7, "veyraTerrainPosition")),
            Map.entry("viewPos", new FixedOutput("vec3", "smooth", 10, "veyraViewPosition")),
            Map.entry("mat", new FixedOutput("int", "flat", 11, "veyraTerrainMaterialId")),
            Map.entry("signMidCoordPos", new FixedOutput("vec2", "smooth", 12, "veyraSignMidCoordPos")),
            Map.entry("mc_Entity", new FixedOutput("vec2", "flat", 13, "veyraMcEntity")),
            Map.entry("absMidCoordPos", new FixedOutput("vec2", "flat", 14, "veyraAbsMidCoordPos")),
            Map.entry("midCoord", new FixedOutput("vec2", "flat", 15, "veyraMidCoord")),
            Map.entry("sphericalVertexDistance", new FixedOutput("float", "smooth", 8, "veyraSphericalVertexDistance")),
            Map.entry("cylindricalVertexDistance", new FixedOutput("float", "smooth", 9, "veyraCylindricalVertexDistance")),
            Map.entry("dist", new FixedOutput("float", "smooth", 16, "veyraTerrainDistance")),
            Map.entry("vTexCoord", new FixedOutput("vec4", "smooth", 17, "veyraBslTexCoord")),
            Map.entry("vTexCoordAM", new FixedOutput("vec4", "smooth", 18, "veyraBslTexCoordAM")),
            Map.entry("spriteBounds", new FixedOutput("vec4", "smooth", 19, "veyraSpriteBounds")),
            Map.entry("chunkFade", new FixedOutput("float", "flat", 4, "fadeFactor")));

    record PackVarying(String type, String interpolation, int location, String vertexVariable,
                       boolean fixedBridge) {
    }

    record AdaptedStage(String source, Map<String, PackVarying> varyings) {
    }

    private record FixedOutput(String type, String interpolation, int location, String vertexVariable) {
    }

    private record OutputBridge(String source, String declarationsAndMacros,
                                Map<String, PackVarying> varyings) {
    }

    private record SamplerBridge(String source, String declarationsAndMacros) {
    }

    private static String legacyInputBridge(String programName) {
        String bridge = """
                #define gl_Vertex vec4(getVertexPosition(), 1.0)
                #define gl_Color unpackUnorm4x8(PackedColor)
                #define gl_ModelViewProjectionMatrix (gbufferProjection * gbufferModelView)
                #define gl_ModelViewMatrix gbufferModelView
                #define gl_ProjectionMatrix gbufferProjection
                #define gl_NormalMatrix transpose(inverse(mat3(gbufferModelView)))
                #define gl_Normal (vec3(float(bitfieldExtract(VeyraTerrainPackedNormal, 0, 8)) / 127.0, float(bitfieldExtract(VeyraTerrainPackedNormal, 8, 8)) / 127.0, float(bitfieldExtract(VeyraTerrainPackedNormal, 16, 8)) / 127.0))
                #define gl_MultiTexCoord0 vec4(vec2(UV0) * UV_INV, 0.0, 1.0)
                #define gl_MultiTexCoord1 vec4(float(bitfieldExtract(uint(Position.a), 4, 4)) * 16.0, float(bitfieldExtract(uint(Position.a), 12, 4)) * 16.0, 0.0, 1.0)
                #define at_tangent VeyraTerrainTangent
                #define mc_Entity vec4(float(VeyraTerrainMaterialId), VeyraTerrainRenderType, 0.0, 0.0)
                #define mc_midTexCoord vec4(vec2(MidTexCoord) * UV_INV, 0.0, 1.0)
                #define mc_chunkFade fadeFactor
                #define ftransform() ((gbufferProjection * gbufferModelView) * vec4(getVertexPosition(), 1.0))
                """;
        return programName.startsWith("shadow")
                ? bridge.replace("gbufferProjection", "shadowProjection").replace("gbufferModelView", "shadowModelView")
                : bridge;
    }

    private static String expandCommaDeclarations(String source) {
        Matcher matcher = COMMA_DECLARATION.matcher(source);
        StringBuffer expanded = new StringBuffer();
        while (matcher.find()) {
            StringBuilder declarations = new StringBuilder();
            String[] names = matcher.group(2).split("\\s*,\\s*");
            for (int i = 0; i < names.length; i++) {
                if (i > 0) declarations.append('\n');
                declarations.append(matcher.group(1)).append(names[i]).append(';');
                if (i == names.length - 1) declarations.append(matcher.group(3));
            }
            matcher.appendReplacement(expanded, Matcher.quoteReplacement(declarations.toString()));
        }
        matcher.appendTail(expanded);
        return expanded.toString();
    }

    private static MainFunction findMain(String source, String name) throws IOException {
        String uncommented = ShaderSource.maskBlockAndLineComments(source);
        Matcher matcher = MAIN_DECLARATION.matcher(uncommented);
        if (!matcher.find()) throw new IOException(name + " has no main() entrypoint.");
        int openBrace = uncommented.indexOf('{', matcher.end());
        if (openBrace < 0) throw new IOException(name + " main() entrypoint has no body.");
        int depth = 0;
        for (int i = openBrace; i < uncommented.length(); i++) {
            char c = uncommented.charAt(i);
            if (c == '{') depth++;
            else if (c == '}' && --depth == 0) return new MainFunction(matcher.start(), openBrace, i);
        }
        throw new IOException(name + " main() entrypoint has unmatched braces.");
    }

    private record PreparedStage(String source, List<String> extensions, String uniformMacros,
                                 String outputBridge, Map<String, PackVarying> varyings) {
    }

    private record MainFunction(int start, int openBrace, int closeBrace) {
    }
}
