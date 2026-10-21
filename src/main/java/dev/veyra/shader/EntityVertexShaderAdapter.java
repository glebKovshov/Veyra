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

/** Runs Iris block/entity/hand vertex programs after Minecraft's native NEW_ENTITY setup. */
final class EntityVertexShaderAdapter {
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
    private static final Pattern ATTRIBUTE = Pattern.compile(
            "(?m)^[\\t ]*(?:layout\\s*\\(\\s*location\\s*=\\s*(\\d+)\\s*\\)\\s*)?"
                    + "(?:(?:flat|smooth|noperspective)\\s+)*(?:attribute|in)\\s+"
                    + "([A-Za-z_][A-Za-z0-9_]*)\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*;"
                    + "[\\t ]*(?://[^\\r\\n]*)?(?:\\r?\\n|$)");
    private static final Pattern UNSUPPORTED_ATTRIBUTE = Pattern.compile(
            "(?m)^[\\t ]*(?:layout\\s*\\([^)]*\\)\\s*)?(?:(?:flat|smooth|noperspective)\\s+)*"
                    + "(?:attribute|in)\\b[^\\r\\n;]*;");
    private static final Pattern UNSUPPORTED_OUTPUT = Pattern.compile(
            "(?m)(?:^|;)[\\t ]*(?:layout\\s*\\([^)]*\\)\\s*)?"
                    + "(?:(?:flat|smooth|noperspective|centroid|sample|invariant)\\s+)*(?:varying|out)\\b[^\\r\\n;]*;");
    private static final Pattern UNSUPPORTED_UNIFORM = Pattern.compile(
            "(?m)^[\\t ]*uniform\\s+(?:sampler\\w+|\\w+)\\s+\\w+\\s*;");
    private static final Pattern MAIN = Pattern.compile("\\bvoid\\s+main\\s*\\(\\s*(?:void\\s*)?\\)");
    private static final Pattern TEXTURE_MATRIX = Pattern.compile("\\bgl_TextureMatrix\\s*\\[\\s*(\\d+)\\s*]");
    private static final Pattern LEGACY_INPUT = Pattern.compile(
            "\\b(gl_MultiTexCoord[3-7]|gl_VertexID|gl_InstanceID|gl_ClipVertex|gl_FrontColor|gl_BackColor|"
                    + "mc_Entity|at_tangent|mc_midTexCoord|at_midBlock|mc_midBlock)\\b");
    private static final Pattern SAMPLER = Pattern.compile(
            "(?m)^[\\t ]*(?:layout\\s*\\([^)]*\\)\\s*)?uniform\\s+(sampler\\w+)\\s+"
                    + "([A-Za-z_][A-Za-z0-9_]*)\\s*;[\\t ]*(?://[^\\r\\n]*)?(?:\\r?\\n|$)");
    private static final Pattern NORMAL_MATRIX = Pattern.compile("\\bgl_NormalMatrix\\b");
    private static final String LIGHTMAP_TEXTURE_MATRIX = "mat4("
            + "0.00390625, 0.0, 0.0, 0.0, "
            + "0.0, 0.00390625, 0.0, 0.0, "
            + "0.0, 0.0, 0.00390625, 0.0, "
            + "0.03125, 0.03125, 0.03125, 1.0)";
    private static final Map<String, NativeAttribute> NATIVE_ATTRIBUTES = Map.of(
            "Position", new NativeAttribute("vec3", 0),
            "Color", new NativeAttribute("vec4", 1),
            "UV0", new NativeAttribute("vec2", 2),
            "UV1", new NativeAttribute("ivec2", 3),
            "UV2", new NativeAttribute("ivec2", 4),
            "Normal", new NativeAttribute("vec3", 5));

    private EntityVertexShaderAdapter() { }

    static boolean supportsProgram(String programName) {
        return programName.startsWith("gbuffers_entities") || programName.startsWith("gbuffers_block")
                || programName.startsWith("gbuffers_hand") || programName.equals("gbuffers_spidereyes")
                || programName.equals("gbuffers_damagedblock");
    }

    static AdaptedStage adaptStage(String minecraftSource, String shaderPackSource, String programName,
                                   boolean handPass) throws IOException {
        PreparedStage pack = prepare(shaderPackSource, programName);
        if (minecraftSource == null || minecraftSource.isBlank()) {
            throw new IOException("Minecraft's " + programName + " vertex shader is empty.");
        }
        Matcher version = VERSION.matcher(minecraftSource);
        if (!version.find()) throw new IOException("Minecraft's " + programName + " vertex shader has no #version directive.");
        StringBuilder extensions = new StringBuilder();
        pack.extensions().forEach(extension -> extensions.append(extension).append('\n'));
        String nativeSource = minecraftSource.substring(0, version.end()) + extensions
                + minecraftSource.substring(version.end());
        MainFunction nativeMain = findMain(nativeSource, "Minecraft's " + programName + " vertex shader");
        nativeSource = nativeSource.substring(0, nativeMain.start()) + "void veyraMinecraftEntityMain()"
                + nativeSource.substring(nativeMain.end());

        String bridge = """
                #define gl_Vertex vec4(Position, 1.0)
                #define gl_Color Color
                #define gl_Normal Normal
                #define gl_MultiTexCoord0 vec4(UV0, 0.0, 1.0)
                #define gl_MultiTexCoord1 vec4(vec2(UV2), 0.0, 1.0)
                #define gl_MultiTexCoord2 vec4(vec2(UV1), 0.0, 1.0)
                #define gl_ModelViewMatrix ModelViewMat
                #define gl_ProjectionMatrix ProjMat
                #define gl_ModelViewProjectionMatrix (ProjMat * ModelViewMat)
                #define gl_NormalMatrix transpose(inverse(mat3(ModelViewMat)))
                #define ftransform() ((ProjMat * ModelViewMat) * vec4(Position, 1.0))
                """;
        String wrapper = "\nvoid main() { veyraMinecraftEntityMain(); veyraShaderPackMain();"
                + (handPass ? " gl_Position.z *= 0.125;" : "") + " }\n";
        String source = nativeSource + "\n" + pack.outputDeclarations() + bridge
                + pack.uniformBridge() + pack.source().replaceFirst(
                        "(?m)\\bvoid\\s+main\\s*\\(", "void veyraShaderPackMain(") + wrapper;
        return new AdaptedStage(source, pack.varyings(), pack.usesNoiseSampler());
    }

    static AdaptedStage validate(String programName, String shaderPackSource, boolean handPass) throws IOException {
        String base = """
                #version 460
                layout(location=0) in vec3 Position;
                layout(location=1) in vec4 Color;
                layout(location=2) in vec2 UV0;
                layout(location=3) in ivec2 UV1;
                layout(location=4) in ivec2 UV2;
                layout(location=5) in vec3 Normal;
                layout(location=0) out vec4 vertexColor;
                layout(location=1) out vec2 texCoord0;
                layout(location=2) out vec4 lightMapColor;
                layout(location=3) out vec4 overlayColor;
                uniform mat4 ModelViewMat;
                uniform mat4 ProjMat;
                uniform mat4 TextureMat;
                void main() { gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0); }
                """;
        PreparedStage prepared = prepare(shaderPackSource, programName);
        String adapted = base + "\n" + prepared.outputDeclarations()
                + "#define gl_Vertex vec4(Position, 1.0)\n#define gl_Color Color\n#define gl_Normal Normal\n"
                + "#define gl_MultiTexCoord0 vec4(UV0, 0.0, 1.0)\n"
                + "#define gl_MultiTexCoord1 vec4(vec2(UV2), 0.0, 1.0)\n"
                + "#define gl_MultiTexCoord2 vec4(vec2(UV1), 0.0, 1.0)\n"
                + "#define gl_ModelViewMatrix ModelViewMat\n#define gl_ProjectionMatrix ProjMat\n"
                + "#define gl_ModelViewProjectionMatrix (ProjMat * ModelViewMat)\n"
                + "#define gl_NormalMatrix transpose(inverse(mat3(ModelViewMat)))\n"
                + "#define ftransform() ((ProjMat * ModelViewMat) * vec4(Position, 1.0))\n"
                + prepared.uniformBridge() + prepared.source().replaceFirst(
                        "(?m)\\bvoid\\s+main\\s*\\(", "void veyraShaderPackMain(");
        if (handPass) adapted += "\n// hand-depth is applied by the runtime wrapper after the pack entrypoint\n";
        return new AdaptedStage(adapted, prepared.varyings(), prepared.usesNoiseSampler());
    }

    static Map<String, TerrainVertexShaderAdapter.PackVarying> describeVaryings(
            String programName, String shaderPackSource) throws IOException {
        return prepare(shaderPackSource, programName).varyings();
    }

    private static PreparedStage prepare(String shaderPackSource, String programName) throws IOException {
        if (shaderPackSource == null || shaderPackSource.isBlank()) throw new IOException(programName + ".vsh is empty.");
        if (shaderPackSource.length() > 1024 * 1024) throw new IOException(programName + ".vsh exceeds the 1 MiB size limit.");
        String source = GlslPreprocessor.process(programName + ".vsh", shaderPackSource);
        source = UnreachableGlslFunctions.removeUnreachable(source, "main");
        source = UnusedUniformDeclarations.removeUnused(source);
        List<String> extensions = new ArrayList<>();
        Matcher extension = EXTENSION.matcher(source);
        while (extension.find()) extensions.add(extension.group().strip());
        source = extension.reset().replaceAll("");
        source = VERSION.matcher(source).replaceAll("");
        source = PRECISION.matcher(source).replaceAll("");
        source = source.replace("\uFEFF", "")
                .replaceAll("\\bgl_TextureMatrix\\s*\\[\\s*0\\s*]", "TextureMat")
                .replaceAll("\\bgl_TextureMatrix\\s*\\[\\s*1\\s*]", Matcher.quoteReplacement(LIGHTMAP_TEXTURE_MATRIX))
                .replaceAll("\\bgl_ModelViewProjectionMatrix\\b", "(ProjMat * ModelViewMat)")
                .replaceAll("\\bgl_ModelViewMatrix\\b", "ModelViewMat")
                .replaceAll("\\bgl_ProjectionMatrix\\b", "ProjMat")
                .replaceAll("\\bftransform\\s*\\(\\s*\\)", "((ProjMat * ModelViewMat) * vec4(Position, 1.0))")
                .replaceAll("\\btexture2DLod(?:EXT)?\\s*\\(", "textureLod(")
                .replaceAll("\\btexture2DGrad(?:ARB)?\\s*\\(", "textureGrad(")
                .replaceAll("\\btexture2DProj\\s*\\(", "textureProj(")
                .replaceAll("\\btexture2D\\s*\\(", "texture(");
        Matcher textureMatrix = TEXTURE_MATRIX.matcher(ShaderSource.maskBlockAndLineComments(source));
        while (textureMatrix.find()) {
            if (!Set.of("0", "1").contains(textureMatrix.group(1))) {
                throw new IOException(programName + ".vsh uses an unsupported gl_TextureMatrix index "
                        + textureMatrix.group(1) + ".");
            }
        }
        Matcher unsupportedInput = LEGACY_INPUT.matcher(ShaderSource.maskBlockAndLineComments(source));
        if (unsupportedInput.find()) throw new IOException(programName + ".vsh uses legacy interface '"
                + unsupportedInput.group(1) + "', which is not provided by Minecraft's NEW_ENTITY vertex format.");

        source = expandCommaOutputs(source);
        OutputBridge outputs = extractOutputs(source, programName);
        source = removeAttributes(outputs.source(), programName);
        Matcher unsupportedAttribute = UNSUPPORTED_ATTRIBUTE.matcher(ShaderSource.maskBlockAndLineComments(source));
        if (unsupportedAttribute.find()) throw new IOException(programName + ".vsh declares an unsupported vertex attribute.");
        Matcher main = MAIN.matcher(ShaderSource.maskBlockAndLineComments(source));
        if (!main.find()) throw new IOException(programName + ".vsh has no main() entrypoint.");
        if (main.find()) throw new IOException(programName + ".vsh must contain exactly one active main() entrypoint.");

        source = UnusedSamplerDeclarations.removeUnused(source);
        SamplerBridge samplers = adaptSamplers(source, programName);
        source = samplers.source();
        IrisFogUniformAdapter.AdaptedShader fog = IrisFogUniformAdapter.extract(source, programName, true);
        ShaderPackUniformAdapter.AdaptedShader uniforms = ShaderPackUniformAdapter.extract(fog.source(), programName);
        source = uniforms.source();
        if (UNSUPPORTED_UNIFORM.matcher(ShaderSource.maskBlockAndLineComments(source)).find()) {
            throw new IOException(programName + ".vsh declares an unsupported uniform.");
        }
        if (NORMAL_MATRIX.matcher(ShaderSource.maskBlockAndLineComments(source)).find()) {
            source = NORMAL_MATRIX.matcher(source).replaceAll("transpose(inverse(mat3(ModelViewMat)))");
        }
        String uniformBridge = fog.bridge() + uniforms.declarations() + uniforms.macros() + samplers.bridge();
        return new PreparedStage(source, List.copyOf(extensions), uniformBridge,
                outputs.declarationsAndMacros(), outputs.varyings(), samplers.usesNoiseSampler());
    }

    private static SamplerBridge adaptSamplers(String source, String programName) throws IOException {
        Matcher matcher = SAMPLER.matcher(ShaderSource.maskBlockAndLineComments(source));
        StringBuilder clean = new StringBuilder(source.length());
        int cursor = 0;
        boolean noise = false;
        while (matcher.find()) {
            String type = matcher.group(1), name = matcher.group(2);
            if (!name.equals("noisetex") || !type.equals("sampler2D") || noise) {
                throw new IOException(programName + ".vsh uses unsupported sampler '" + name + "'.");
            }
            noise = true;
            clean.append(source, cursor, matcher.start());
            cursor = matcher.end();
        }
        clean.append(source, cursor, source.length());
        String adapted = clean.toString();
        return new SamplerBridge(adapted, noise ? "uniform sampler2D VeyraNoiseSampler;\n#define noisetex VeyraNoiseSampler\n" : "",
                TerrainShaderAdapter.usesNoiseSampler(adapted));
    }

    private static OutputBridge extractOutputs(String source, String programName) throws IOException {
        Matcher matcher = OUTPUT.matcher(ShaderSource.maskBlockAndLineComments(source));
        StringBuilder clean = new StringBuilder(source.length());
        StringBuilder declarations = new StringBuilder();
        Map<String, TerrainVertexShaderAdapter.PackVarying> varyings = new LinkedHashMap<>();
        Set<Integer> occupied = new HashSet<>();
        int nextLocation = 16;
        int cursor = 0;
        while (matcher.find()) {
            String type = matcher.group(4), name = matcher.group(5);
            String interpolation = mergeInterpolation(matcher.group(2), matcher.group(3), programName, name);
            if (name.startsWith("gl_")) throw new IOException(programName + ".vsh cannot redeclare reserved output '" + name + "'.");
            if (!supportedType(type)) throw new IOException(programName + ".vsh output '" + name + "' uses unsupported type " + type + ".");
            if (varyings.containsKey(name)) throw new IOException(programName + ".vsh declares output '" + name + "' more than once.");
            if (integerType(type) && !interpolation.equals("flat")) {
                throw new IOException(programName + ".vsh integer output '" + name + "' must use flat interpolation.");
            }
            int span = locationSpan(type);
            int requested = matcher.group(1) == null ? -1 : Integer.parseInt(matcher.group(1));
            int location = requested < 0 ? nextFree(occupied, nextLocation, span) : requested;
            if (location < 16 || location + span > 32) {
                throw new IOException(programName + ".vsh output '" + name + "' uses a location reserved by Minecraft or outside Veyra's varying limit.");
            }
            for (int i = location; i < location + span; i++) {
                if (!occupied.add(i)) throw new IOException(programName + ".vsh has duplicate output location " + i + ".");
            }
            nextLocation = Math.max(nextLocation, location + span);
            String variable = "veyraEntityVarying_" + name;
            varyings.put(name, new TerrainVertexShaderAdapter.PackVarying(type, interpolation, location, variable, false));
            String macro = "VEYRA_ENTITY_OUTPUT_" + location + "_" + sanitize(name);
            declarations.append("#define ").append(macro).append(" layout(location = ").append(location).append(") ")
                    .append(interpolation.equals("smooth") ? "" : interpolation + " ")
                    .append("out ").append(type).append(' ').append(variable).append(";\n")
                    .append(macro).append("\n#define ").append(name).append(' ').append(variable).append('\n');
            clean.append(source, cursor, matcher.start());
            cursor = matcher.end();
        }
        clean.append(source, cursor, source.length());
        String uncommentedWithoutKnown = ShaderSource.maskBlockAndLineComments(
                OUTPUT.matcher(ShaderSource.maskBlockAndLineComments(source)).replaceAll(""));
        if (UNSUPPORTED_OUTPUT.matcher(uncommentedWithoutKnown).find()) {
            throw new IOException(programName + ".vsh declares an unsupported vertex output.");
        }
        return new OutputBridge(clean.toString(), declarations.toString(), Map.copyOf(varyings));
    }

    private static String removeAttributes(String source, String programName) throws IOException {
        Matcher matcher = ATTRIBUTE.matcher(ShaderSource.maskBlockAndLineComments(source));
        StringBuilder clean = new StringBuilder(source.length());
        int cursor = 0;
        while (matcher.find()) {
            String name = matcher.group(3), type = matcher.group(2);
            NativeAttribute attribute = NATIVE_ATTRIBUTES.get(name);
            if (attribute == null) throw new IOException(programName + ".vsh attribute '" + name
                    + "' is not provided by Minecraft's NEW_ENTITY vertex format.");
            if (!attribute.type().equals(type) && !(name.equals("UV1") && type.equals("vec2"))
                    && !(name.equals("UV2") && type.equals("vec2"))) {
                throw new IOException(programName + ".vsh attribute '" + name + "' must use " + attribute.type() + ".");
            }
            if (matcher.group(1) != null && Integer.parseInt(matcher.group(1)) != attribute.location()) {
                throw new IOException(programName + ".vsh attribute '" + name + "' uses an unsupported location.");
            }
            clean.append(source, cursor, matcher.start());
            cursor = matcher.end();
        }
        clean.append(source, cursor, source.length());
        return clean.toString();
    }

    private static String expandCommaOutputs(String source) {
        Matcher matcher = COMMA_OUTPUT.matcher(source);
        StringBuilder expanded = new StringBuilder(source.length());
        int cursor = 0;
        while (matcher.find()) {
            expanded.append(source, cursor, matcher.start());
            for (String name : matcher.group(2).split(",")) {
                expanded.append(matcher.group(1)).append(name.trim()).append(matcher.group(3));
            }
            cursor = matcher.end();
        }
        return expanded.append(source.substring(cursor)).toString();
    }

    private static int nextFree(Set<Integer> occupied, int start, int span) {
        for (int candidate = Math.max(16, start); candidate + span <= 32; candidate++) {
            boolean free = true;
            for (int i = candidate; i < candidate + span; i++) free &= !occupied.contains(i);
            if (free) return candidate;
        }
        return -1;
    }

    private static MainFunction findMain(String source, String owner) throws IOException {
        String masked = ShaderSource.maskBlockAndLineComments(source);
        Matcher matcher = MAIN.matcher(masked);
        if (!matcher.find()) throw new IOException(owner + " has no main() entrypoint.");
        int start = matcher.start(), end = matcher.end();
        int open = masked.indexOf('{', end);
        if (open < 0) throw new IOException(owner + " main() has no body.");
        int depth = 0;
        for (int i = open; i < masked.length(); i++) {
            if (masked.charAt(i) == '{') depth++;
            else if (masked.charAt(i) == '}' && --depth == 0) return new MainFunction(start, end, i);
        }
        throw new IOException(owner + " main() has unmatched braces.");
    }

    private static String mergeInterpolation(String first, String second, String program, String name) throws IOException {
        if (first != null && second != null && !first.equals(second)) {
            throw new IOException(program + ".vsh output '" + name + "' has conflicting interpolation qualifiers.");
        }
        return first != null ? first : second != null ? second : "smooth";
    }

    private static boolean supportedType(String type) {
        return type.matches("(?:float|int|uint|vec[234]|ivec[234]|uvec[234])");
    }
    private static boolean integerType(String type) {
        return type.equals("int") || type.equals("uint") || type.startsWith("ivec") || type.startsWith("uvec");
    }
    private static int locationSpan(String type) { return type.matches("(?:d?mat)([234])") ? Integer.parseInt(type.substring(type.length() - 1)) : 1; }
    private static String sanitize(String name) { return name.replaceAll("[^A-Za-z0-9_]", "_"); }

    record AdaptedStage(String source, Map<String, TerrainVertexShaderAdapter.PackVarying> varyings,
                        boolean usesNoiseSampler) { }
    private record PreparedStage(String source, List<String> extensions, String uniformBridge,
                                 String outputDeclarations,
                                 Map<String, TerrainVertexShaderAdapter.PackVarying> varyings,
                                 boolean usesNoiseSampler) { }
    private record OutputBridge(String source, String declarationsAndMacros,
                                Map<String, TerrainVertexShaderAdapter.PackVarying> varyings) { }
    private record SamplerBridge(String source, String bridge, boolean usesNoiseSampler) { }
    private record NativeAttribute(String type, int location) { }
    private record MainFunction(int start, int end, int closeBrace) { }
}
