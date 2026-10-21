package dev.veyra.shader;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Adapts Iris basic/leash and line vertex stages to Minecraft's distinct native vertex formats. */
final class BasicVertexShaderAdapter {
    enum Format { LEASH, LINE, SKY, STARS, SUNRISE_SUNSET }

    private static final Pattern VERSION = Pattern.compile("(?m)^[\\t ]*#version[^\\r\\n]*(?:\\r?\\n|$)");
    private static final Pattern EXTENSION = Pattern.compile("(?m)^[\\t ]*#extension[^\\r\\n]*(?:\\r?\\n|$)");
    private static final Pattern PRECISION = Pattern.compile(
            "(?m)^[\\t ]*precision\\s+(?:lowp|mediump|highp)\\s+\\w+\\s*;[\\t ]*(?:\\r?\\n|$)");
    private static final Pattern OUTPUT = Pattern.compile(
            "(?m)^[\\t ]*(?:layout\\s*\\(\\s*location\\s*=\\s*(\\d+)\\s*\\)\\s*)?"
                    + "(?:(flat|smooth|noperspective)\\s+)?(?:varying|out)\\s+"
                    + "(?:(flat|smooth|noperspective)\\s+)?([A-Za-z_][A-Za-z0-9_]*)\\s+"
                    + "([A-Za-z_][A-Za-z0-9_]*)\\s*;[\\t ]*(?://[^\\r\\n]*)?(?:\\r?\\n|$)");
    private static final Pattern COMMA_OUTPUT = Pattern.compile(
            "(?m)^([\\t ]*(?:(?:layout\\s*\\([^)]*\\)\\s*)?)(?:(?:flat|smooth|noperspective)\\s+)?"
                    + "(?:varying|out|attribute|in)\\s+[A-Za-z_][A-Za-z0-9_]*\\s+)"
                    + "([A-Za-z_][A-Za-z0-9_]*(?:\\s*,\\s*[A-Za-z_][A-Za-z0-9_]*)+)"
                    + "(\\s*;[\\t ]*(?://[^\\r\\n]*)?(?:\\r?\\n|$))");
    private static final Pattern UNSUPPORTED_OUTPUT = Pattern.compile(
            "(?m)(?:^|;)[\\t ]*(?:layout\\s*\\([^)]*\\)\\s*)?"
                    + "(?:(?:flat|smooth|noperspective|centroid|sample|invariant)\\s+)*(?:varying|out)\\b[^\\r\\n;]*;");
    private static final Pattern ATTRIBUTE = Pattern.compile(
            "(?m)^[\\t ]*(?:layout\\s*\\([^)]*\\)\\s*)?(?:(?:flat|smooth|noperspective)\\s+)*"
                    + "(?:attribute|in)\\s+([A-Za-z_][A-Za-z0-9_]*)\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*;"
                    + "[\\t ]*(?://[^\\r\\n]*)?(?:\\r?\\n|$)");
    private static final Pattern MAIN = Pattern.compile("\\bvoid\\s+main\\s*\\(\\s*\\)");
    private static final Pattern BASE_OUTPUT = Pattern.compile(
            "(?m)^([\\t ]*)(?:(?:layout\\s*\\([^)]*\\)\\s*)?)(?:(flat|smooth|noperspective)\\s+)?"
                    + "out\\s+([A-Za-z_][A-Za-z0-9_]*)\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*;");
    private static final Pattern UNSUPPORTED_LEGACY_INPUT = Pattern.compile(
            "\\b(gl_MultiTexCoord[3-7]|at_midBlock|mc_midBlock)\\b");
    private static final Pattern UNSUPPORTED_SKY_INPUT = Pattern.compile(
            "\\b(vaNormal|Normal|UV2|LineWidth)\\b");
    private static final Pattern TEXTURE_MATRIX = Pattern.compile("\\bgl_TextureMatrix\\s*\\[\\s*(\\d+)\\s*]");
    private static final Pattern FOG_COORD = Pattern.compile("\\bgl_FogFragCoord\\b");
    private static final Pattern UNSUPPORTED_UNIFORM = Pattern.compile(
            "(?m)^[\\t ]*uniform\\s+(?:sampler\\w+|\\w+)\\s+\\w+\\s*;");
    private static final String LIGHTMAP_TEXTURE_MATRIX = "mat4("
            + "0.00390625, 0.0, 0.0, 0.0, "
            + "0.0, 0.00390625, 0.0, 0.0, "
            + "0.0, 0.0, 0.00390625, 0.0, "
            + "0.03125, 0.03125, 0.03125, 1.0)";
    private static final Map<String, FixedOutput> FIXED_OUTPUTS = Map.ofEntries(
            Map.entry("color", new FixedOutput("vec4", 0, "vertexColor")),
            Map.entry("glColor", new FixedOutput("vec4", 0, "vertexColor")),
            Map.entry("glcolor", new FixedOutput("vec4", 0, "vertexColor")),
            Map.entry("vertexColor", new FixedOutput("vec4", 0, "vertexColor")),
            Map.entry("sphericalVertexDistance", new FixedOutput("float", 1, "sphericalVertexDistance")),
            Map.entry("cylindricalVertexDistance", new FixedOutput("float", 2, "cylindricalVertexDistance")),
            Map.entry("lmCoord", new FixedOutput("vec2", 3, "veyraBasicLightmapCoord")),
            Map.entry("lmcoord", new FixedOutput("vec2", 3, "veyraBasicLightmapCoord")),
            Map.entry("lightMapCoord", new FixedOutput("vec2", 3, "veyraBasicLightmapCoord")),
            Map.entry("normal", new FixedOutput("vec3", 4, "veyraBasicNormal")));

    private BasicVertexShaderAdapter() { }

    static AdaptedStage adaptStage(String minecraftSource, String shaderPackSource, String programName, Format format)
            throws IOException {
        return adaptStage(minecraftSource, shaderPackSource, programName, format, false);
    }

    static AdaptedStage adaptStage(String minecraftSource, String shaderPackSource, String programName, Format format,
                                   boolean widenFallbackLines)
            throws IOException {
        if (minecraftSource == null || minecraftSource.isBlank()) {
            throw new IOException("Minecraft's " + format.name().toLowerCase() + " vertex shader is empty.");
        }
        PreparedStage pack = prepare(shaderPackSource, programName, format);
        Matcher version = VERSION.matcher(minecraftSource);
        if (!version.find()) throw new IOException("Minecraft's " + format.name().toLowerCase() + " vertex shader has no #version directive.");

        StringBuilder extensions = new StringBuilder();
        pack.extensions().forEach(extension -> extensions.append(extension).append('\n'));
        String base = minecraftSource.substring(0, version.end()) + extensions + minecraftSource.substring(version.end());
        base = patchBaseOutputs(base, pack.outputs(), programName, format);
        MainFunction baseMain = findMain(base, "Minecraft's " + format.name().toLowerCase() + " vertex shader");
        String nativeMainName = switch (format) {
            case LINE -> "veyraMinecraftLineMain";
            case LEASH -> "veyraMinecraftLeashMain";
            default -> "veyraMinecraftSkyMain";
        };
        String generatedMain;
        base = base.substring(0, baseMain.start()) + "void " + nativeMainName + "();\n" + base.substring(baseMain.start());
        baseMain = findMain(base, "Minecraft's " + format.name().toLowerCase() + " vertex shader");
        base = base.substring(0, baseMain.start()) + "void " + nativeMainName + "()" + base.substring(baseMain.end());
        generatedMain = switch (format) {
            case LINE -> lineMain(widenFallbackLines);
            case LEASH -> leashMain();
            default -> skyMain(format);
        };

        String bridge = nativeInputBridge(format);
        return new AdaptedStage(base + "\n" + pack.outputDeclarations() + bridge + pack.uniformBridge()
                + renameMain(pack.source(), "veyraShaderPackMain", programName)
                + "\n" + generatedMain, effectiveOutputs(pack.outputs(), format), pack.usesNoiseSampler());
    }

    static AdaptedStage validate(String programName, String shaderPackSource, Format format) throws IOException {
        String inputs = switch (format) {
            case LEASH -> "layout(location=0) in vec3 Position;\nlayout(location=1) in vec4 Color;\n"
                    + "layout(location=2) in ivec2 UV2;\n";
            case LINE -> "layout(location=0) in vec3 Position;\nlayout(location=1) in vec4 Color;\n"
                    + "layout(location=2) in vec3 Normal;\nlayout(location=3) in float LineWidth;\n"
                    + "uniform vec2 ScreenSize;\n";
            case SKY, STARS -> "layout(location=0) in vec3 Position;\n";
            case SUNRISE_SUNSET -> "layout(location=0) in vec3 Position;\n"
                    + "layout(location=1) in vec4 Color;\n";
        };
        String outputs = switch (format) {
            case LEASH, LINE, SUNRISE_SUNSET -> "layout(location=0) out vec4 vertexColor;\n"
                    + "layout(location=1) out float sphericalVertexDistance;\n"
                    + "layout(location=2) out float cylindricalVertexDistance;\n";
            case SKY -> "out float sphericalVertexDistance;\nout float cylindricalVertexDistance;\n";
            case STARS -> "";
        };
        String base = "#version 460\n" + inputs + outputs
                + "layout(set=0,binding=0,std140) uniform NativeTransforms { mat4 ModelViewMat; mat4 ProjMat; "
                + "mat4 TextureMat; vec4 ColorModulator; vec2 ScreenSize; };\n"
                + "void main(){gl_Position=ProjMat*ModelViewMat*vec4(Position,1.0);"
                + (format == Format.SUNRISE_SUNSET ? "vertexColor=Color;" : "")
                + (format == Format.SKY ? "sphericalVertexDistance=1.0;cylindricalVertexDistance=1.0;" : "")
                + "}\n";
        return adaptStage(base, shaderPackSource, programName, format);
    }

    static Map<String, TerrainVertexShaderAdapter.PackVarying> describeVaryings(
            String programName, String shaderPackSource, Format format) throws IOException {
        return effectiveOutputs(prepare(shaderPackSource, programName, format).outputs(), format);
    }

    static Map<String, TerrainVertexShaderAdapter.PackVarying> nativeVaryings(Format format) {
        return effectiveOutputs(Map.of(), format);
    }

    private static PreparedStage prepare(String shaderPackSource, String programName, Format format) throws IOException {
        if (shaderPackSource == null || shaderPackSource.isBlank()) throw new IOException(programName + ".vsh is empty.");
        if (shaderPackSource.length() > 1024 * 1024) throw new IOException(programName + ".vsh exceeds the 1 MiB size limit.");

        String source = GlslPreprocessor.process(programName + ".vsh", shaderPackSource);
        source = UnreachableGlslFunctions.removeUnreachable(source, "main");
        source = UnusedUniformDeclarations.removeUnused(source);
        source = IrisRenderStages.bindUniform(source, IrisRenderStages.forSkyFormat(format), programName);
        List<String> extensions = new ArrayList<>();
        Matcher extensionMatcher = EXTENSION.matcher(source);
        while (extensionMatcher.find()) extensions.add(extensionMatcher.group().strip());
        source = extensionMatcher.replaceAll("");
        source = VERSION.matcher(source).replaceAll("");
        source = PRECISION.matcher(source).replaceAll("");
        source = source.replaceAll("\\bgl_TextureMatrix\\s*\\[\\s*0\\s*]", "TextureMat")
                .replaceAll("\\bgl_TextureMatrix\\s*\\[\\s*1\\s*]", Matcher.quoteReplacement(LIGHTMAP_TEXTURE_MATRIX))
                .replaceAll("\\bgl_MultiTexCoord2\\b", "gl_MultiTexCoord1");
        source = expandCommaOutputs(source);
        String uncommented = ShaderSource.maskBlockAndLineComments(source);
        Matcher matrix = TEXTURE_MATRIX.matcher(uncommented);
        while (matrix.find()) {
            if (!matrix.group(1).equals("0") && !matrix.group(1).equals("1")) {
                throw new IOException(programName + ".vsh uses a texture matrix index other than 0 or 1.");
            }
        }
        Matcher legacy = UNSUPPORTED_LEGACY_INPUT.matcher(uncommented);
        if (legacy.find()) throw new IOException(programName + ".vsh input '" + legacy.group(1)
                + "' is not present in Minecraft's " + format.name().toLowerCase() + " vertex format.");
        if (isSkyFormat(format)) {
            Matcher unsupportedSkyInput = UNSUPPORTED_SKY_INPUT.matcher(uncommented);
            if (unsupportedSkyInput.find()) throw new IOException(programName + ".vsh input '"
                    + unsupportedSkyInput.group(1) + "' is not present in Minecraft's "
                    + format.name().toLowerCase() + " vertex format.");
        }

        OutputBridge outputs = extractOutputs(source, programName);
        source = outputs.source();
        source = removeUnusedAttributes(source, programName, format);
        uncommented = ShaderSource.maskBlockAndLineComments(source);
        if (UNSUPPORTED_OUTPUT.matcher(OUTPUT.matcher(uncommented).replaceAll("")).find()) {
            throw new IOException(programName + ".vsh declares an unsupported vertex output.");
        }
        Matcher main = MAIN.matcher(uncommented);
        if (!main.find()) throw new IOException(programName + ".vsh has no main() entrypoint.");
        if (main.find()) throw new IOException(programName + ".vsh must contain exactly one active main() entrypoint.");

        source = UnusedSamplerDeclarations.removeUnused(source);
        SamplerBridge samplers = adaptSamplers(programName, source);
        source = samplers.source();
        IrisFogUniformAdapter.AdaptedShader fog = IrisFogUniformAdapter.extract(source, programName, true);
        source = UnusedSamplerDeclarations.removeUnused(fog.source());
        ShaderPackUniformAdapter.AdaptedShader uniforms = ShaderPackUniformAdapter.extract(source, programName);
        source = uniforms.source();
        if (UNSUPPORTED_UNIFORM.matcher(ShaderSource.maskBlockAndLineComments(source)).find()) {
            throw new IOException(programName + ".vsh declares an unsupported uniform.");
        }
        boolean usesFogCoord = FOG_COORD.matcher(ShaderSource.maskBlockAndLineComments(source)).find();
        return new PreparedStage(source, List.copyOf(extensions), fog.bridge() + uniforms.declarations()
                + uniforms.macros() + samplers.bridge(), outputs.declarationsAndMacros(), outputs.varyings(),
                samplers.usesNoiseSampler(), usesFogCoord);
    }

    private static SamplerBridge adaptSamplers(String programName, String source) throws IOException {
        Pattern samplerPattern = Pattern.compile(
                "(?m)^[\\t ]*(?:layout\\s*\\([^)]*\\)\\s*)?uniform\\s+(sampler\\w+)\\s+"
                        + "([A-Za-z_][A-Za-z0-9_]*)\\s*;[\\t ]*(?://[^\\r\\n]*)?(?:\\r?\\n|$)");
        Matcher matcher = samplerPattern.matcher(ShaderSource.maskBlockAndLineComments(source));
        StringBuilder clean = new StringBuilder(source.length());
        int cursor = 0;
        int noiseCount = 0;
        while (matcher.find()) {
            String type = matcher.group(1), name = matcher.group(2);
            if (!name.equals("noisetex") || !type.equals("sampler2D") || ++noiseCount > 1) {
                throw new IOException(programName + ".vsh uses unsupported sampler '" + name + "'.");
            }
            clean.append(source, cursor, matcher.start());
            cursor = matcher.end();
        }
        clean.append(source, cursor, source.length());
        String adapted = clean.toString().replaceAll("\\btexture2DLod(?:EXT)?\\s*\\(", "textureLod(")
                .replaceAll("\\btexture2DGrad(?:ARB)?\\s*\\(", "textureGrad(")
                .replaceAll("\\btexture2DProj\\s*\\(", "textureProj(")
                .replaceAll("\\btexture2D\\s*\\(", "texture(");
        boolean usesNoise = TerrainShaderAdapter.usesNoiseSampler(adapted);
        String bridge = usesNoise
                ? "uniform sampler2D VeyraNoiseSampler;\n#define noisetex VeyraNoiseSampler\n" : "";
        return new SamplerBridge(adapted, bridge, usesNoise);
    }

    private static OutputBridge extractOutputs(String source, String programName) throws IOException {
        Matcher matcher = OUTPUT.matcher(ShaderSource.maskBlockAndLineComments(source));
        StringBuilder clean = new StringBuilder(source.length());
        StringBuilder declarations = new StringBuilder();
        Map<String, TerrainVertexShaderAdapter.PackVarying> varyings = new LinkedHashMap<>();
        Set<Integer> occupied = new HashSet<>(Set.of(0, 1, 2, 3, 4, 5));
        Set<String> emittedVariables = new HashSet<>();
        int nextLocation = 6;
        int cursor = 0;
        while (matcher.find()) {
            String interpolation = mergeInterpolation(matcher.group(2), matcher.group(3), programName, matcher.group(5));
            String type = matcher.group(4), name = matcher.group(5);
            if (name.startsWith("gl_")) throw new IOException(programName + ".vsh cannot redeclare reserved output '" + name + "'.");
            if (!supportedType(type)) throw new IOException(programName + ".vsh output '" + name + "' uses unsupported type " + type + ".");
            if (varyings.containsKey(name)) throw new IOException(programName + ".vsh declares output '" + name + "' more than once.");
            if (integerType(type) && !interpolation.equals("flat")) {
                throw new IOException(programName + ".vsh integer output '" + name + "' must use flat interpolation.");
            }
            FixedOutput fixed = FIXED_OUTPUTS.get(name);
            if (fixed != null && !fixed.type().equals(type)) {
                throw new IOException(programName + ".vsh output '" + name + "' must use " + fixed.type() + ".");
            }
            int span = locationSpan(type);
            Integer explicit = matcher.group(1) == null ? null : Integer.parseInt(matcher.group(1));
            int location = fixed != null ? fixed.location() : explicit == null ? nextFree(occupied, nextLocation, span) : explicit;
            if (explicit != null && explicit != location) {
                throw new IOException(programName + ".vsh output '" + name + "' uses an unsupported location.");
            }
            if (location < 0 || location + span > 32) throw new IOException(programName + ".vsh has too many vertex outputs.");
            for (int i = location; i < location + span; i++) {
                if (!occupied.add(i) && fixed == null) throw new IOException(programName + ".vsh has duplicate output location " + i + ".");
            }
            if (fixed == null) nextLocation = Math.max(nextLocation, location + span);
            String variable = fixed == null ? "veyraPackVarying_" + name : fixed.variable();
            if (!emittedVariables.add(variable)) {
                throw new IOException(programName + ".vsh declares more than one output alias for '" + variable + "'.");
            }
            varyings.put(name, new TerrainVertexShaderAdapter.PackVarying(type, interpolation, location, variable, fixed != null));
            if (fixed == null || !Set.of("vertexColor", "sphericalVertexDistance", "cylindricalVertexDistance")
                    .contains(fixed.variable())) {
                declarations.append(parserSafeOutput("VEYRA_BASIC_OUTPUT_" + location + "_" + sanitize(name),
                        location, interpolation, type, variable));
            }
            if (!name.equals(variable)) declarations.append("#define ").append(name).append(' ').append(variable).append('\n');
            clean.append(source, cursor, matcher.start());
            cursor = matcher.end();
        }
        clean.append(source, cursor, source.length());
        return new OutputBridge(clean.toString(), declarations.toString(), Map.copyOf(varyings));
    }

    private static String patchBaseOutputs(String source,
            Map<String, TerrainVertexShaderAdapter.PackVarying> packOutputs, String programName, Format format)
            throws IOException {
        Map<String, TerrainVertexShaderAdapter.PackVarying> required = new LinkedHashMap<>();
        required.put("vertexColor", aliasedOutput(packOutputs, Set.of("color", "glColor", "glcolor", "vertexColor"),
                defaultOutput("vec4", format == Format.LEASH ? "flat" : "smooth", 0, "vertexColor")));
        required.put("sphericalVertexDistance", packOutputs.getOrDefault("sphericalVertexDistance",
                defaultOutput("float", "smooth", 1, "sphericalVertexDistance")));
        required.put("cylindricalVertexDistance", packOutputs.getOrDefault("cylindricalVertexDistance",
                defaultOutput("float", "smooth", 2, "cylindricalVertexDistance")));
        String masked = ShaderSource.maskBlockAndLineComments(source);
        Matcher matcher = BASE_OUTPUT.matcher(masked);
        StringBuilder rewritten = new StringBuilder(source.length());
        Set<String> found = new HashSet<>();
        int copiedThrough = 0;
        while (matcher.find()) {
            String name = matcher.group(4), type = matcher.group(3);
            TerrainVertexShaderAdapter.PackVarying output = required.get(name);
            if (output == null) continue;
            if (!output.type().equals(type)) throw new IOException(programName + " native output '" + name + "' has type " + type + ".");
            if (!found.add(name)) throw new IOException("Minecraft's " + programName + " vertex shader declares output '" + name + "' twice.");
            String declaration = parserSafeOutput("VEYRA_BASIC_NATIVE_OUTPUT_" + output.location(), output.location(),
                    output.interpolation(), type, name);
            rewritten.append(source, copiedThrough, matcher.start()).append(matcher.group(1)).append(declaration);
            copiedThrough = matcher.end();
        }
        rewritten.append(source, copiedThrough, source.length());
        for (String requiredName : required.keySet()) {
            if (!found.contains(requiredName)) {
                if (!isSkyFormat(format)) throw new IOException("Minecraft's " + programName
                        + " vertex shader does not declare " + requiredName + ".");
                TerrainVertexShaderAdapter.PackVarying output = required.get(requiredName);
                rewritten.append("\n").append(parserSafeOutput("VEYRA_BASIC_NATIVE_OUTPUT_"
                        + output.location(), output.location(), output.interpolation(), output.type(), requiredName));
            }
        }
        rewritten.append("\n").append(parserSafeOutput("VEYRA_BASIC_FOG_COORD", 5, "smooth", "float", "veyraFogFragCoord"));
        return rewritten.toString();
    }

    private static String nativeInputBridge(Format format) {
        String lightmap = format == Format.LEASH ? "vec4(vec2(UV2), 0.0, 1.0)" : "vec4(240.0, 240.0, 0.0, 1.0)";
        String color = switch (format) {
            case SKY, STARS -> "ColorModulator";
            case SUNRISE_SUNSET -> "Color";
            case LEASH, LINE -> "vec4((Color * ColorModulator).rgb, ColorModulator.a)";
        };
        return """
                vec3 veyraVertexOffset = vec3(0.0);
                #define gl_Vertex vec4(Position + veyraVertexOffset, 1.0)
                #define gl_Color %s
                #define gl_Normal vec3(0.0, 0.0, 1.0)
                #define gl_MultiTexCoord0 vec4(0.5, 0.5, 0.0, 1.0)
                #define gl_MultiTexCoord1 %s
                #define gl_ModelViewProjectionMatrix (ProjMat * ModelViewMat)
                #define gl_ModelViewMatrix ModelViewMat
                #define gl_ProjectionMatrix ProjMat
                #define gl_NormalMatrix transpose(inverse(mat3(ModelViewMat)))
                #define gl_VertexID gl_VertexIndex
                #define vaPosition Position
                #define vaNormal Normal
                #define projectionMatrix ProjMat
                #define modelViewMatrix ModelViewMat
                #define ftransform() ((ProjMat * ModelViewMat) * gl_Vertex)
                #define gl_FogFragCoord veyraFogFragCoord
                """.formatted(color, lightmap);
    }

    private static String leashMain() {
        return "void main() { veyraMinecraftLeashMain(); veyraFogFragCoord = sphericalVertexDistance; veyraShaderPackMain(); }\n";
    }

    private static String lineMain(boolean widen) {
        if (!widen) {
            return "void main() { veyraMinecraftLineMain(); veyraFogFragCoord = sphericalVertexDistance; veyraShaderPackMain(); }\n";
        }
        return """
                void main() {
                    veyraMinecraftLineMain();
                    veyraFogFragCoord = sphericalVertexDistance;
                    veyraVertexOffset = Normal;
                    veyraShaderPackMain();
                    vec4 linePosEnd = gl_Position;
                    veyraVertexOffset = vec3(0.0);
                    veyraShaderPackMain();
                    vec4 linePosStart = gl_Position;
                    vec3 ndc1 = linePosStart.xyz / linePosStart.w;
                    vec3 ndc2 = linePosEnd.xyz / linePosEnd.w;
                    vec2 lineScreenDirection = normalize((ndc2.xy - ndc1.xy) * ScreenSize);
                    vec2 lineOffset = vec2(-lineScreenDirection.y, lineScreenDirection.x) * LineWidth / ScreenSize;
                    if (lineOffset.x < 0.0) lineOffset *= -1.0;
                    if (gl_VertexID % 2 == 0) {
                        gl_Position = vec4((ndc1 + vec3(lineOffset, 0.0)) * linePosStart.w, linePosStart.w);
                    } else {
                        gl_Position = vec4((ndc1 - vec3(lineOffset, 0.0)) * linePosStart.w, linePosStart.w);
                    }
                }
                """;
    }

    private static String skyMain(Format format) {
        String initializeColor = format == Format.SKY || format == Format.STARS
                ? "vertexColor = ColorModulator; " : "";
        String initializeDistances = format == Format.STARS || format == Format.SUNRISE_SUNSET
                ? "sphericalVertexDistance = 0.0; cylindricalVertexDistance = 0.0; " : "";
        return "void main() { veyraMinecraftSkyMain(); " + initializeColor + initializeDistances
                + "veyraFogFragCoord = sphericalVertexDistance; veyraShaderPackMain(); }\n";
    }

    private static boolean isSkyFormat(Format format) {
        return format == Format.SKY || format == Format.STARS || format == Format.SUNRISE_SUNSET;
    }

    private static String parserSafeOutput(String macro, int location, String interpolation, String type, String name) {
        return "#define " + macro + " layout(location = " + location + ") "
                + (interpolation.equals("smooth") ? "" : interpolation + " ") + "out " + type + " " + name + ";\n"
                + macro + "\n";
    }

    private static String mergeInterpolation(String first, String second, String programName, String name) throws IOException {
        if (first != null && second != null && !first.equals(second)) {
            throw new IOException(programName + ".vsh output '" + name + "' has conflicting interpolation qualifiers.");
        }
        return first != null ? first : second != null ? second : "smooth";
    }

    private static TerrainVertexShaderAdapter.PackVarying defaultOutput(String type, String interpolation,
                                                                        int location, String name) {
        return new TerrainVertexShaderAdapter.PackVarying(type, interpolation, location, name, true);
    }

    private static TerrainVertexShaderAdapter.PackVarying aliasedOutput(
            Map<String, TerrainVertexShaderAdapter.PackVarying> outputs, Set<String> names,
            TerrainVertexShaderAdapter.PackVarying fallback) {
        return names.stream().map(outputs::get).filter(java.util.Objects::nonNull).findFirst().orElse(fallback);
    }

    private static Map<String, TerrainVertexShaderAdapter.PackVarying> effectiveOutputs(
            Map<String, TerrainVertexShaderAdapter.PackVarying> outputs, Format format) {
        Map<String, TerrainVertexShaderAdapter.PackVarying> effective = new LinkedHashMap<>(outputs);
        TerrainVertexShaderAdapter.PackVarying color = aliasedOutput(outputs,
                Set.of("color", "glColor", "glcolor", "vertexColor"),
                defaultOutput("vec4", format == Format.LEASH ? "flat" : "smooth", 0, "vertexColor"));
        TerrainVertexShaderAdapter.PackVarying spherical = outputs.getOrDefault("sphericalVertexDistance",
                defaultOutput("float", "smooth", 1, "sphericalVertexDistance"));
        TerrainVertexShaderAdapter.PackVarying cylindrical = outputs.getOrDefault("cylindricalVertexDistance",
                defaultOutput("float", "smooth", 2, "cylindricalVertexDistance"));
        effective.put("vertexColor", color);
        effective.put("color", color);
        effective.put("glColor", color);
        effective.put("glcolor", color);
        effective.put("gl_Color", color);
        effective.put("sphericalVertexDistance", spherical);
        effective.put("cylindricalVertexDistance", cylindrical);
        if (outputs.containsKey("texcoord") || outputs.containsKey("texCoord") || outputs.containsKey("texCoord0")) {
            TerrainVertexShaderAdapter.PackVarying texCoord = aliasedOutput(outputs,
                    Set.of("texcoord", "texCoord", "texCoord0"), null);
            if (texCoord != null) {
                effective.put("texcoord", texCoord);
                effective.put("texCoord", texCoord);
                effective.put("texCoord0", texCoord);
            }
        }
        if (outputs.containsKey("lmcoord") || outputs.containsKey("lmCoord") || outputs.containsKey("lightMapCoord")) {
            TerrainVertexShaderAdapter.PackVarying lightmap = aliasedOutput(outputs,
                    Set.of("lmCoord", "lmcoord", "lightMapCoord"), null);
            if (lightmap != null) {
                effective.put("lmCoord", lightmap);
                effective.put("lmcoord", lightmap);
                effective.put("lightMapCoord", lightmap);
            }
        }
        return Map.copyOf(effective);
    }

    private static int nextFree(Set<Integer> occupied, int start, int span) {
        for (int candidate = Math.max(5, start); candidate + span <= 32; candidate++) {
            boolean free = true;
            for (int i = candidate; i < candidate + span; i++) free &= !occupied.contains(i);
            if (free) return candidate;
        }
        return -1;
    }

    private static boolean supportedType(String type) {
        return Set.of("float", "vec2", "vec3", "vec4", "int", "ivec2", "ivec3", "ivec4",
                "uint", "uvec2", "uvec3", "uvec4", "mat2", "mat3", "mat4").contains(type);
    }
    private static boolean integerType(String type) { return type.equals("int") || type.startsWith("i") || type.equals("uint") || type.startsWith("uvec"); }
    private static int locationSpan(String type) { return type.equals("mat2") ? 2 : type.equals("mat3") ? 3 : type.equals("mat4") ? 4 : 1; }
    private static String sanitize(String name) { return name.replaceAll("[^A-Za-z0-9_]", "_"); }

    private static String renameMain(String source, String replacement, String programName) throws IOException {
        String masked = ShaderSource.maskBlockAndLineComments(source);
        Matcher matcher = MAIN.matcher(masked);
        if (!matcher.find()) throw new IOException(programName + ".vsh has no main() entrypoint.");
        int start = matcher.start(), end = matcher.end();
        if (matcher.find()) throw new IOException(programName + ".vsh must contain exactly one active main() entrypoint.");
        return source.substring(0, start) + "void " + replacement + "()" + source.substring(end);
    }

    private static String expandCommaOutputs(String source) {
        Matcher matcher = COMMA_OUTPUT.matcher(source);
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

    private static String removeUnusedAttributes(String source, String programName, Format format) throws IOException {
        String masked = ShaderSource.maskBlockAndLineComments(source);
        Matcher matcher = ATTRIBUTE.matcher(masked);
        StringBuilder cleaned = new StringBuilder(source.length());
        int copiedThrough = 0;
        while (matcher.find()) {
            String name = matcher.group(2);
            String withoutDeclaration = masked.substring(0, matcher.start()) + " ".repeat(matcher.end() - matcher.start())
                    + masked.substring(matcher.end());
            if (Pattern.compile("\\b" + Pattern.quote(name) + "\\b").matcher(withoutDeclaration).find()) {
                throw new IOException(programName + ".vsh vertex input '" + name
                        + "' is not present in Minecraft's " + format.name().toLowerCase() + " vertex format.");
            }
            cleaned.append(source, copiedThrough, matcher.start());
            copiedThrough = matcher.end();
        }
        return cleaned.append(source, copiedThrough, source.length()).toString();
    }

    private static MainFunction findMain(String source, String owner) throws IOException {
        String uncommented = ShaderSource.maskBlockAndLineComments(source);
        Matcher matcher = MAIN.matcher(uncommented);
        if (!matcher.find()) throw new IOException(owner + " has no main() entrypoint.");
        int open = uncommented.indexOf('{', matcher.end());
        if (open < 0) throw new IOException(owner + " main() has no body.");
        int depth = 0;
        for (int i = open; i < uncommented.length(); i++) {
            if (uncommented.charAt(i) == '{') depth++;
            else if (uncommented.charAt(i) == '}' && --depth == 0) return new MainFunction(matcher.start(), matcher.end(), i);
        }
        throw new IOException(owner + " main() has unmatched braces.");
    }

    record AdaptedStage(String source, Map<String, TerrainVertexShaderAdapter.PackVarying> varyings,
                        boolean usesNoiseSampler) { }
    private record PreparedStage(String source, List<String> extensions, String uniformBridge,
                                 String outputDeclarations, Map<String, TerrainVertexShaderAdapter.PackVarying> outputs,
                                 boolean usesNoiseSampler, boolean usesFogCoord) { }
    private record OutputBridge(String source, String declarationsAndMacros,
                                Map<String, TerrainVertexShaderAdapter.PackVarying> varyings) { }
    private record SamplerBridge(String source, String bridge, boolean usesNoiseSampler) { }
    private record FixedOutput(String type, int location, String variable) { }
    private record MainFunction(int start, int end, int closeBrace) { }
}
