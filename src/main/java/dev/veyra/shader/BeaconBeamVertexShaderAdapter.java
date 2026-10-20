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

/** Executes Iris beacon-beam vertex stages on Minecraft's position/color/UV0 vertex interface. */
final class BeaconBeamVertexShaderAdapter {
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
                    + "(?:varying|out)\\s+[A-Za-z_][A-Za-z0-9_]*\\s+)"
                    + "([A-Za-z_][A-Za-z0-9_]*(?:\\s*,\\s*[A-Za-z_][A-Za-z0-9_]*)+)"
                    + "(\\s*;[\\t ]*(?://[^\\r\\n]*)?(?:\\r?\\n|$))");
    private static final Pattern UNSUPPORTED_OUTPUT = Pattern.compile(
            "(?m)(?:^|;)[\\t ]*(?:layout\\s*\\([^)]*\\)\\s*)?"
                    + "(?:(?:flat|smooth|noperspective|centroid|sample|invariant)\\s+)*(?:varying|out)\\b[^\\r\\n;]*;");
    private static final Pattern ATTRIBUTE = Pattern.compile(
            "(?m)^[\\t ]*(?:layout\\s*\\(\\s*location\\s*=\\s*(\\d+)\\s*\\)\\s*)?"
                    + "(?:(?:flat|smooth|noperspective)\\s+)*(?:attribute|in)\\s+"
                    + "([A-Za-z_][A-Za-z0-9_]*)\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*;"
                    + "[\\t ]*(?://[^\\r\\n]*)?(?:\\r?\\n|$)");
    private static final Pattern UNSUPPORTED_ATTRIBUTE = Pattern.compile(
            "(?m)^[\\t ]*(?:layout\\s*\\([^)]*\\)\\s*)?(?:(?:flat|smooth|noperspective)\\s+)*"
                    + "(?:attribute|in)\\b[^\\r\\n;]*;");
    private static final Pattern UNSUPPORTED_UNIFORM = Pattern.compile("(?m)^[\\t ]*uniform\\b");
    private static final Pattern SAMPLER = Pattern.compile(
            "(?m)^[\\t ]*(?:layout\\s*\\([^)]*\\)\\s*)?uniform\\s+(sampler\\w+)\\s+"
                    + "([A-Za-z_][A-Za-z0-9_]*)\\s*;[\\t ]*(?://[^\\r\\n]*)?(?:\\r?\\n|$)");
    private static final Pattern MAIN = Pattern.compile("\\bvoid\\s+main\\s*\\(\\s*\\)");
    private static final Pattern BASE_OUTPUT = Pattern.compile(
            "(?m)^([\\t ]*)(?:(?:layout\\s*\\([^)]*\\)\\s*)?)(?:(flat|smooth|noperspective)\\s+)?"
                    + "out\\s+([A-Za-z_][A-Za-z0-9_]*)\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*;");
    private static final Pattern TEXTURE_MATRIX = Pattern.compile("\\bgl_TextureMatrix\\s*\\[\\s*(\\d+)\\s*]");
    private static final Pattern UNSUPPORTED_LEGACY_INPUT = Pattern.compile(
            "\\b(gl_Normal|gl_MultiTexCoord[1-7]|gl_VertexID|gl_InstanceID)\\b");
    private static final Pattern COMMA_OUTPUT_PREFIX = Pattern.compile(
            "(?m)^([\\t ]*(?:(?:layout\\s*\\([^)]*\\)\\s*)?)(?:(?:flat|smooth|noperspective)\\s+)?"
                    + "(?:varying|out)\\s+[A-Za-z_][A-Za-z0-9_]*\\s+)");
    private static final Pattern OUTPUT_NAMES = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");
    private static final Map<String, FixedOutput> FIXED_OUTPUTS = Map.ofEntries(
            Map.entry("color", new FixedOutput("vec4", "vertexColor", 0)),
            Map.entry("glColor", new FixedOutput("vec4", "vertexColor", 0)),
            Map.entry("glcolor", new FixedOutput("vec4", "vertexColor", 0)),
            Map.entry("vertexColor", new FixedOutput("vec4", "vertexColor", 0)),
            Map.entry("texcoord", new FixedOutput("vec2", "texCoord0", 1)),
            Map.entry("texCoord", new FixedOutput("vec2", "texCoord0", 1)),
            Map.entry("texCoord0", new FixedOutput("vec2", "texCoord0", 1)));
    private static final Map<String, FixedAttribute> FIXED_ATTRIBUTES = Map.ofEntries(
            Map.entry("Position", new FixedAttribute("vec3", 0, "Position")),
            Map.entry("vaPosition", new FixedAttribute("vec3", 0, "Position")),
            Map.entry("Color", new FixedAttribute("vec4", 1, "Color")),
            Map.entry("vaColor", new FixedAttribute("vec4", 1, "Color")),
            Map.entry("UV0", new FixedAttribute("vec2", 2, "UV0")),
            Map.entry("vaUV0", new FixedAttribute("vec2", 2, "UV0")));

    private BeaconBeamVertexShaderAdapter() { }

    static AdaptedStage adaptStage(String minecraftSource, String shaderPackSource, String programName)
            throws IOException {
        if (minecraftSource == null || minecraftSource.isBlank()) {
            throw new IOException("Minecraft's beacon-beam vertex shader is empty.");
        }
        PreparedStage pack = prepare(shaderPackSource, programName);
        Matcher version = VERSION.matcher(minecraftSource);
        if (!version.find()) throw new IOException("Minecraft's beacon-beam vertex shader has no #version directive.");

        String base = minecraftSource.substring(0, version.end()) + pack.extensions().stream()
                .reduce(new StringBuilder(), (builder, extension) -> builder.append(extension).append('\n'),
                        StringBuilder::append) + minecraftSource.substring(version.end());
        base = patchNativeOutput(base, "vec4", "vertexColor", 0, pack.colorInterpolation(), programName);
        base = patchNativeOutput(base, "vec2", "texCoord0", 1, pack.texCoordInterpolation(), programName);

        MainFunction baseMain = findMain(base, "Minecraft's beacon-beam vertex shader");
        base = base.substring(0, baseMain.start()) + "void veyraBeaconShaderMain();\n" + base.substring(baseMain.start());
        baseMain = findMain(base, "Minecraft's beacon-beam vertex shader");
        base = base.substring(0, baseMain.closeBrace()) + "\n    veyraBeaconShaderMain();\n" + base.substring(baseMain.closeBrace());

        String bridge = """
                #define gl_Vertex vec4(Position, 1.0)
                #define gl_Color Color
                #define gl_MultiTexCoord0 vec4(UV0, 0.0, 1.0)
                #define gl_ModelViewProjectionMatrix (ProjMat * ModelViewMat)
                #define gl_ModelViewMatrix ModelViewMat
                #define gl_ProjectionMatrix ProjMat
                #define ftransform() ((ProjMat * ModelViewMat) * vec4(Position, 1.0))
                #define vaPosition Position
                #define vaColor Color
                #define vaUV0 UV0
                """ + (pack.usesNoiseSampler()
                ? "uniform sampler2D VeyraNoiseSampler;\n#define noisetex VeyraNoiseSampler\n" : "");
        String source = base + "\n" + pack.outputs().declarationsAndMacros() + bridge
                + pack.uniformBridge() + pack.source().replaceFirst("(?m)\\bvoid\\s+main\\s*\\(", "void veyraBeaconShaderMain(");
        return new AdaptedStage(source, effectiveOutputs(pack.outputs().varyings(), pack.colorInterpolation(),
                pack.texCoordInterpolation()), pack.usesNoiseSampler());
    }

    static AdaptedStage validate(String programName, String shaderPackSource) throws IOException {
        String base = """
                #version 460
                layout(location=0) in vec3 Position;
                layout(location=1) in vec4 Color;
                layout(location=2) in vec2 UV0;
                layout(location=0) out vec4 vertexColor;
                layout(location=1) out vec2 texCoord0;
                layout(set=0,binding=0,std140) uniform NativeTransforms {
                    mat4 ModelViewMat; mat4 ProjMat; mat4 TextureMat;
                };
                void main(){gl_Position=ProjMat*ModelViewMat*vec4(Position,1.0);vertexColor=Color;texCoord0=UV0;}
                """;
        PreparedStage prepared = prepare(shaderPackSource, programName);
        return new AdaptedStage(base + "\n" + prepared.outputs().declarationsAndMacros()
                + prepared.uniformBridge() + prepared.source(),
                effectiveOutputs(prepared.outputs().varyings(), prepared.colorInterpolation(),
                        prepared.texCoordInterpolation()), prepared.usesNoiseSampler());
    }

    static Map<String, TerrainVertexShaderAdapter.PackVarying> nativeVaryings() {
        return effectiveOutputs(Map.of(), "smooth", "smooth");
    }

    private static PreparedStage prepare(String shaderPackSource, String programName) throws IOException {
        if (shaderPackSource == null || shaderPackSource.isBlank()) throw new IOException(programName + ".vsh is empty.");
        if (shaderPackSource.length() > 1024 * 1024) throw new IOException(programName + ".vsh exceeds the 1 MiB size limit.");

        String source = GlslPreprocessor.process(programName + ".vsh", shaderPackSource);
        source = UnreachableGlslFunctions.removeUnreachable(source, "main");
        source = UnusedUniformDeclarations.removeUnused(source);
        List<String> extensions = new ArrayList<>();
        Matcher extensionMatcher = EXTENSION.matcher(source);
        while (extensionMatcher.find()) extensions.add(extensionMatcher.group().strip());
        source = EXTENSION.matcher(source).replaceAll("");
        source = VERSION.matcher(source).replaceAll("");
        source = PRECISION.matcher(source).replaceAll("");
        source = source.replaceAll("\\bgl_TextureMatrix\\s*\\[\\s*0\\s*]", "TextureMat")
                .replaceAll("\\btexture2DLod(?:EXT)?\\s*\\(", "textureLod(")
                .replaceAll("\\btexture2DGrad(?:ARB)?\\s*\\(", "textureGrad(")
                .replaceAll("\\btexture2DProj\\s*\\(", "textureProj(")
                .replaceAll("\\btexture2D\\s*\\(", "texture(");
        Matcher textureMatrix = TEXTURE_MATRIX.matcher(ShaderSource.maskBlockAndLineComments(source));
        while (textureMatrix.find()) {
            if (!textureMatrix.group(1).equals("0")) {
                throw new IOException(programName + ".vsh uses a texture matrix index other than 0, which the beacon vertex format does not provide.");
            }
        }
        Matcher legacyInput = UNSUPPORTED_LEGACY_INPUT.matcher(ShaderSource.maskBlockAndLineComments(source));
        if (legacyInput.find()) throw new IOException(programName + ".vsh reads unsupported legacy input '"
                + legacyInput.group(1) + "'; Minecraft's beacon vertex format provides only position, color, and UV0.");

        OutputDeclarations outputs = extractOutputs(expandCommaOutputs(source), programName);
        source = outputs.source();
        source = adaptAttributes(source, programName);
        String uncommented = ShaderSource.maskBlockAndLineComments(source);
        Matcher main = MAIN.matcher(uncommented);
        if (!main.find()) throw new IOException(programName + ".vsh has no main() entrypoint.");
        if (main.find()) throw new IOException(programName + ".vsh must contain exactly one active main() entrypoint.");

        source = UnusedSamplerDeclarations.removeUnused(source);
        Matcher samplerMatcher = SAMPLER.matcher(ShaderSource.maskBlockAndLineComments(source));
        StringBuilder withoutSamplers = new StringBuilder(source.length());
        int copiedThrough = 0;
        int noiseCount = 0;
        while (samplerMatcher.find()) {
            String type = samplerMatcher.group(1), name = samplerMatcher.group(2);
            if (!name.equals("noisetex") || !type.equals("sampler2D") || ++noiseCount > 1) {
                throw new IOException(programName + ".vsh uses unsupported sampler '" + name + "'.");
            }
            withoutSamplers.append(source, copiedThrough, samplerMatcher.start());
            copiedThrough = samplerMatcher.end();
        }
        withoutSamplers.append(source, copiedThrough, source.length());
        source = withoutSamplers.toString();
        boolean usesNoise = TerrainShaderAdapter.usesNoiseSampler(source);
        IrisFogUniformAdapter.AdaptedShader fog = IrisFogUniformAdapter.extract(source, programName, true);
        ShaderPackUniformAdapter.AdaptedShader uniforms = ShaderPackUniformAdapter.extract(fog.source(), programName);
        source = uniforms.source();
        if (UNSUPPORTED_UNIFORM.matcher(ShaderSource.maskBlockAndLineComments(source)).find()) {
            throw new IOException(programName + ".vsh declares an unsupported uniform.");
        }
        return new PreparedStage(source, List.copyOf(extensions), outputs, fog.bridge()
                + uniforms.declarations() + uniforms.macros(), usesNoise,
                outputs.colorInterpolation(), outputs.texCoordInterpolation());
    }

    private static String adaptAttributes(String source, String programName) throws IOException {
        Matcher matcher = ATTRIBUTE.matcher(ShaderSource.maskBlockAndLineComments(source));
        StringBuilder clean = new StringBuilder(source.length());
        int copiedThrough = 0;
        Set<String> seen = new HashSet<>();
        while (matcher.find()) {
            String type = matcher.group(2), name = matcher.group(3);
            FixedAttribute attribute = FIXED_ATTRIBUTES.get(name);
            String activeSource = ShaderSource.maskBlockAndLineComments(source);
            String withoutDeclaration = activeSource.substring(0, matcher.start()) + activeSource.substring(matcher.end());
            if (attribute == null) {
                if (Pattern.compile("\\b" + Pattern.quote(name) + "\\b").matcher(withoutDeclaration).find()) {
                    throw new IOException(programName + ".vsh reads attribute '" + name
                            + "', which is not part of Minecraft's beacon vertex format.");
                }
            } else {
                if (!attribute.type().equals(type)) throw new IOException(programName + ".vsh attribute '" + name
                        + "' must use " + attribute.type() + ".");
                if (matcher.group(1) != null && Integer.parseInt(matcher.group(1)) != attribute.location()) {
                    throw new IOException(programName + ".vsh attribute '" + name + "' uses an unsupported location.");
                }
                if (!seen.add(attribute.vertexVariable())) throw new IOException(programName + ".vsh declares the native beacon attribute '"
                        + attribute.vertexVariable() + "' more than once.");
            }
            clean.append(source, copiedThrough, matcher.start());
            copiedThrough = matcher.end();
        }
        clean.append(source, copiedThrough, source.length());
        String result = clean.toString();
        Matcher unsupported = UNSUPPORTED_ATTRIBUTE.matcher(ShaderSource.maskBlockAndLineComments(result));
        if (unsupported.find()) throw new IOException(programName + ".vsh declares an unsupported vertex attribute.");
        return result;
    }

    private static OutputDeclarations extractOutputs(String source, String programName) throws IOException {
        Matcher matcher = OUTPUT.matcher(ShaderSource.maskBlockAndLineComments(source));
        StringBuilder clean = new StringBuilder(source.length());
        StringBuilder declarations = new StringBuilder();
        StringBuilder macros = new StringBuilder();
        Map<String, TerrainVertexShaderAdapter.PackVarying> varyings = new LinkedHashMap<>();
        Set<Integer> occupied = new HashSet<>(Set.of(0, 1));
        Set<String> emittedVariables = new HashSet<>();
        Set<String> seenFixed = new HashSet<>();
        int nextLocation = 2;
        int copiedThrough = 0;
        String colorInterpolation = null;
        String texCoordInterpolation = null;
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
            int location = fixed == null
                    ? matcher.group(1) == null ? nextFree(occupied, nextLocation) : Integer.parseInt(matcher.group(1))
                    : fixed.location();
            if (fixed != null && !seenFixed.add(fixed.vertexVariable())) {
                throw new IOException(programName + ".vsh declares more than one alias for output '" + fixed.vertexVariable() + "'.");
            }
            if (matcher.group(1) != null && Integer.parseInt(matcher.group(1)) != location) {
                throw new IOException(programName + ".vsh output '" + name + "' uses an unsupported location.");
            }
            if (location < 0 || location >= 32 || !occupied.add(location) && fixed == null) {
                throw new IOException(programName + ".vsh has a duplicate or unsupported output location " + location + ".");
            }
            if (fixed != null && location != fixed.location()) {
                throw new IOException(programName + ".vsh fixed output '" + name + "' must use location " + fixed.location() + ".");
            }
            String variable = fixed == null ? "veyraBeaconVarying_" + name : fixed.vertexVariable();
            if (!emittedVariables.add(variable)) throw new IOException(programName + ".vsh declares duplicate output aliases for '" + variable + "'.");
            varyings.put(name, new TerrainVertexShaderAdapter.PackVarying(type, interpolation, location, variable, fixed != null));
            if (fixed == null) {
                declarations.append(parserSafeOutput("VEYRA_BEACON_OUTPUT_" + location + "_" + sanitize(name),
                        "layout(location = " + location + ") " + interpolation + " out " + type + " " + variable + ";"));
                nextLocation = Math.max(nextLocation, location + 1);
            } else if (fixed.vertexVariable().equals("vertexColor")) {
                colorInterpolation = interpolation;
            } else {
                texCoordInterpolation = interpolation;
            }
            macros.append("#define ").append(name).append(' ').append(variable).append('\n');
            clean.append(source, copiedThrough, matcher.start());
            copiedThrough = matcher.end();
        }
        clean.append(source, copiedThrough, source.length());
        String uncommentedWithoutKnown = ShaderSource.maskBlockAndLineComments(
                OUTPUT.matcher(ShaderSource.maskBlockAndLineComments(source)).replaceAll(""));
        if (UNSUPPORTED_OUTPUT.matcher(uncommentedWithoutKnown).find()) {
            throw new IOException(programName + ".vsh declares an unsupported vertex output.");
        }
        if (varyings.containsKey("color") || varyings.containsKey("glColor") || varyings.containsKey("glcolor")
                || varyings.containsKey("vertexColor")) {
            macros.append("#define color vertexColor\n#define glColor vertexColor\n#define glcolor vertexColor\n");
        }
        if (varyings.containsKey("texcoord") || varyings.containsKey("texCoord") || varyings.containsKey("texCoord0")) {
            macros.append("#define texcoord texCoord0\n#define texCoord texCoord0\n");
        }
        return new OutputDeclarations(clean.toString(), declarations.toString() + macros, Map.copyOf(varyings),
                colorInterpolation, texCoordInterpolation);
    }

    private static Map<String, TerrainVertexShaderAdapter.PackVarying> effectiveOutputs(
            Map<String, TerrainVertexShaderAdapter.PackVarying> custom, String colorInterpolation,
            String texCoordInterpolation) {
        Map<String, TerrainVertexShaderAdapter.PackVarying> outputs = new LinkedHashMap<>();
        TerrainVertexShaderAdapter.PackVarying color = new TerrainVertexShaderAdapter.PackVarying(
                "vec4", colorInterpolation == null ? "smooth" : colorInterpolation, 0, "vertexColor", true);
        TerrainVertexShaderAdapter.PackVarying texCoord = new TerrainVertexShaderAdapter.PackVarying(
                "vec2", texCoordInterpolation == null ? "smooth" : texCoordInterpolation, 1, "texCoord0", true);
        outputs.put("vertexColor", color);
        outputs.put("texCoord0", texCoord);
        outputs.putAll(custom);
        return Map.copyOf(outputs);
    }

    private static String patchNativeOutput(String source, String type, String name, int location,
                                            String interpolation, String programName) throws IOException {
        Matcher matcher = BASE_OUTPUT.matcher(source);
        StringBuilder result = new StringBuilder(source.length());
        int copiedThrough = 0;
        boolean found = false;
        while (matcher.find()) {
            if (!matcher.group(3).equals(type) || !matcher.group(4).equals(name)) continue;
            if (found) throw new IOException("Minecraft's beacon vertex shader declares " + name + " more than once.");
            found = true;
            result.append(source, copiedThrough, matcher.start()).append(matcher.group(1))
                    .append(parserSafeOutput("VEYRA_BEACON_NATIVE_OUTPUT_" + location,
                            "layout(location = " + location + ") " + interpolation + " out " + type + " " + name + ";"));
            copiedThrough = matcher.end();
        }
        if (!found) return source + "\n" + parserSafeOutput("VEYRA_BEACON_NATIVE_OUTPUT_" + location,
                "layout(location = " + location + ") " + interpolation + " out " + type + " " + name + ";");
        return result.append(source.substring(copiedThrough)).toString();
    }

    private static String expandCommaOutputs(String source) {
        Matcher matcher = COMMA_OUTPUT.matcher(source);
        StringBuilder expanded = new StringBuilder(source.length());
        int copiedThrough = 0;
        while (matcher.find()) {
            expanded.append(source, copiedThrough, matcher.start());
            Matcher prefix = COMMA_OUTPUT_PREFIX.matcher(matcher.group());
            if (!prefix.find()) continue;
            String declarationPrefix = prefix.group(1);
            Matcher names = OUTPUT_NAMES.matcher(matcher.group(2));
            while (names.find()) expanded.append(declarationPrefix).append(names.group()).append(matcher.group(3));
            copiedThrough = matcher.end();
        }
        return expanded.append(source.substring(copiedThrough)).toString();
    }

    private static int nextFree(Set<Integer> occupied, int start) {
        int location = start;
        while (occupied.contains(location)) location++;
        return location;
    }

    private static boolean supportedType(String type) {
        return type.matches("(?:float|int|uint|vec[234]|ivec[234]|uvec[234])");
    }

    private static boolean integerType(String type) {
        return type.equals("int") || type.equals("uint") || type.startsWith("ivec") || type.startsWith("uvec");
    }

    private static String parserSafeOutput(String macro, String declaration) {
        return "#define " + macro + " " + declaration + '\n' + macro + '\n';
    }

    private static String sanitize(String name) { return name.replaceAll("[^A-Za-z0-9_]", "_"); }

    private static String mergeInterpolation(String first, String second, String programName, String name)
            throws IOException {
        if (first != null && second != null && !first.equals(second)) {
            throw new IOException(programName + ".vsh output '" + name + "' has conflicting interpolation qualifiers.");
        }
        return first != null ? first : second != null ? second : "smooth";
    }

    private static MainFunction findMain(String source, String name) throws IOException {
        String uncommented = ShaderSource.maskBlockAndLineComments(source);
        Matcher matcher = MAIN.matcher(uncommented);
        if (!matcher.find()) throw new IOException(name + " has no main() entrypoint.");
        int openBrace = uncommented.indexOf('{', matcher.end());
        if (openBrace < 0) throw new IOException(name + " main() entrypoint has no body.");
        int depth = 0;
        for (int index = openBrace; index < uncommented.length(); index++) {
            char value = uncommented.charAt(index);
            if (value == '{') depth++;
            else if (value == '}' && --depth == 0) return new MainFunction(matcher.start(), index);
        }
        throw new IOException(name + " main() entrypoint has unmatched braces.");
    }

    record AdaptedStage(String source, Map<String, TerrainVertexShaderAdapter.PackVarying> varyings,
                        boolean usesNoiseSampler) { }

    private record PreparedStage(String source, List<String> extensions, OutputDeclarations outputs,
                                 String uniformBridge, boolean usesNoiseSampler,
                                 String colorInterpolation, String texCoordInterpolation) { }
    private record OutputDeclarations(String source, String declarationsAndMacros,
                                      Map<String, TerrainVertexShaderAdapter.PackVarying> varyings,
                                      String colorInterpolation, String texCoordInterpolation) { }
    private record FixedOutput(String type, String vertexVariable, int location) { }
    private record FixedAttribute(String type, int location, String vertexVariable) { }
    private record MainFunction(int start, int closeBrace) { }
}
