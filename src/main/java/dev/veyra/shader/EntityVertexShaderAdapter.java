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

/** Runs Iris block/entity/hand vertex programs after Minecraft's native vertex setup. */
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
    private static final Pattern FUNCTION_INPUT_QUALIFIER = Pattern.compile(
            "([,(][\\t \\r\\n]*)in\\b(?=[\\t \\r\\n]+[A-Za-z_][A-Za-z0-9_]*[\\t \\r\\n]+[A-Za-z_][A-Za-z0-9_]*)");
    private static final Pattern SHADER_HEADER = Pattern.compile(
            "(?m)^[\\t ]*#version[^\\r\\n]*(?:\\r?\\n|$)(?:(?:[\\t ]*#extension[^\\r\\n]*)(?:\\r?\\n|$))*");
    private static final Pattern LEGACY_BLOCK_OVERLAY_INPUT = Pattern.compile("\\bgl_MultiTexCoord2\\b");
    private static final Pattern LEGACY_INPUT = Pattern.compile(
            "\\b(gl_MultiTexCoord[3-7]|gl_VertexID|gl_InstanceID|gl_ClipVertex|gl_FrontColor|gl_BackColor|"
                    + "mc_Entity|at_tangent|at_midBlock|mc_midBlock)\\b");
    private static final Pattern MC_ENTITY_ATTRIBUTE_DECLARATION = Pattern.compile(
            "(?m)^[\\t ]*(?:(?:flat|smooth|noperspective|centroid|sample|invariant)\\s+)*"
                    + "(?:attribute|in)\\s+vec4\\s+mc_Entity\\s*;"
                    + "[\\t ]*(?://[^\\r\\n]*)?(?:\\r?\\n|$)");
    private static final Pattern ENTITY_MID_BLOCK_ATTRIBUTE_DECLARATION = Pattern.compile(
            "(?m)^[\\t ]*(?:layout\\s*\\([^)]*\\)\\s*)?(?:(?:flat|smooth|noperspective|centroid|sample|invariant)\\s+)*"
                    + "(?:attribute|in)\\s+vec[34]\\s+at_midBlock\\s*;"
                    + "[\\t ]*(?://[^\\r\\n]*)?(?:\\r?\\n|$)");
    private static final Pattern ENTITY_VERTEX_ID = Pattern.compile("\\bgl_VertexID\\b");
    private static final Pattern MID_BLOCK_REFERENCE = Pattern.compile("\\bat_midBlock\\b");
    private static final Pattern SAMPLER = Pattern.compile(
            "(?m)^[\\t ]*(?:layout\\s*\\([^)]*\\)\\s*)?uniform\\s+(sampler\\w+)\\s+"
                    + "([A-Za-z_][A-Za-z0-9_]*)\\s*;[\\t ]*(?://[^\\r\\n]*)?(?:\\r?\\n|$)");
    private static final Pattern SAMPLER_ARGUMENT = Pattern.compile(
            "\\b(texture|textureLod|textureGrad|textureProj|textureOffset|textureLodOffset|"
                    + "textureProjLod|textureProjOffset|textureProjLodOffset|textureGather|"
                    + "textureGatherOffset|textureGatherOffsets|textureSize|texelFetch|texelFetchOffset)"
                    + "\\s*\\(\\s*(tex|texture|gtexture|Sampler0|lightmap|Sampler2)\\b");
    private static final Pattern NORMAL_MATRIX = Pattern.compile("\\bgl_NormalMatrix\\b");
    private static final String LIGHTMAP_TEXTURE_MATRIX = "mat4("
            + "0.00390625, 0.0, 0.0, 0.0, "
            + "0.0, 0.00390625, 0.0, 0.0, "
            + "0.0, 0.0, 0.00390625, 0.0, "
            + "0.03125, 0.03125, 0.03125, 1.0)";
    private static final Map<String, NativeAttribute> NEW_ENTITY_ATTRIBUTES = Map.of(
            "Position", new NativeAttribute("vec3", 0),
            "Color", new NativeAttribute("vec4", 1),
            "UV0", new NativeAttribute("vec2", 2),
            "UV1", new NativeAttribute("ivec2", 3),
            "UV2", new NativeAttribute("ivec2", 4),
            "Normal", new NativeAttribute("vec3", 5),
            "mc_midTexCoord", new NativeAttribute("vec4", 6));
    private static final Map<String, NativeAttribute> BLOCK_ATTRIBUTES = Map.of(
            "Position", new NativeAttribute("vec3", 0),
            "Color", new NativeAttribute("vec4", 1),
            "UV0", new NativeAttribute("vec2", 2),
            "UV2", new NativeAttribute("ivec2", 3),
            "Normal", new NativeAttribute("vec3", 4));

    private EntityVertexShaderAdapter() { }

    static boolean supportsProgram(String programName) {
        return programName.startsWith("gbuffers_entities") || programName.startsWith("gbuffers_block")
                || programName.startsWith("gbuffers_hand") || programName.equals("gbuffers_spidereyes")
                || programName.equals("gbuffers_damagedblock");
    }

    static AdaptedStage adaptStage(String minecraftSource, String shaderPackSource, String programName,
                                   boolean handPass) throws IOException {
        return adaptStage(minecraftSource, shaderPackSource, programName, handPass,
                EntityIdMap.UNMAPPED, BlockIdMap.UNMAPPED, ItemIdMap.UNMAPPED);
    }

    static AdaptedStage adaptStage(String minecraftSource, String shaderPackSource, String programName,
                                   boolean handPass, int entityId, int blockEntityId,
                                   int currentRenderedItemId) throws IOException {
        return adaptStage(minecraftSource, shaderPackSource, programName, handPass,
                entityId, blockEntityId, currentRenderedItemId, Set.of());
    }

    static AdaptedStage adaptStage(String minecraftSource, String shaderPackSource, String programName,
                                   boolean handPass, int entityId, int blockEntityId,
                                   int currentRenderedItemId, Set<String> customTextureSamplers) throws IOException {
        if (minecraftSource == null || minecraftSource.isBlank()) {
            throw new IOException("Minecraft's " + programName + " vertex shader is empty.");
        }
        PreparedStage pack = prepare(shaderPackSource, programName,
                entityId, blockEntityId, currentRenderedItemId,
                IrisFogUniformAdapter.nativeFogValuesDeclared(minecraftSource), customTextureSamplers);
        Matcher version = VERSION.matcher(minecraftSource);
        if (!version.find()) throw new IOException("Minecraft's " + programName + " vertex shader has no #version directive.");
        StringBuilder extensions = new StringBuilder();
        pack.extensions().forEach(extension -> extensions.append(extension).append('\n'));
        String nativeSource = minecraftSource.substring(0, version.end()) + extensions
                + minecraftSource.substring(version.end());
        MainFunction nativeMain = findMain(nativeSource, "Minecraft's " + programName + " vertex shader");
        nativeSource = nativeSource.substring(0, nativeMain.start()) + "void veyraMinecraftEntityMain()"
                + nativeSource.substring(nativeMain.end());

        if (pack.usesMidTexCoord()) {
            Matcher header = SHADER_HEADER.matcher(nativeSource);
            if (!header.find()) throw new IOException("Minecraft's " + programName + " vertex shader has no #version header.");
            nativeSource = nativeSource.substring(0, header.end()) + "in vec2 MidTexCoord;\n"
                    + nativeSource.substring(header.end());
        }
        String bridge = nativeBridge(programName, pack.usesMidTexCoord());
        String wrapper = "\nvoid main() { veyraMinecraftEntityMain(); veyraShaderPackMain();"
                + (handPass ? " gl_Position.z *= 0.125;" : "") + " }\n";
        String uniformBridge = ShaderStageUniformDeduplicator.removeFragmentDuplicates(
                nativeSource, pack.uniformBridge(), programName + " vertex bridge");
        uniformBridge = ShaderStageUniformDeduplicator.removeDuplicateSamplers(
                nativeSource, uniformBridge, programName + " vertex bridge");
        String source = nativeSource + "\n" + pack.outputDeclarations() + bridge
                + uniformBridge + pack.source().replaceFirst(
                        "(?m)\\bvoid\\s+main\\s*\\(", "void veyraShaderPackMain(") + wrapper;
        return new AdaptedStage(source, pack.varyings(), pack.usesNoiseSampler(), pack.usesMidTexCoord());
    }

    static AdaptedStage validate(String programName, String shaderPackSource, boolean handPass) throws IOException {
        PreparedStage prepared = prepare(shaderPackSource, programName,
                EntityIdMap.UNMAPPED, BlockIdMap.UNMAPPED, ItemIdMap.UNMAPPED, false, Set.of());
        String base = validationNativeSource(programName, prepared.usesMidTexCoord());
        String adapted = base + "\n" + prepared.outputDeclarations()
                + nativeBridge(programName, prepared.usesMidTexCoord())
                + prepared.uniformBridge() + prepared.source().replaceFirst(
                        "(?m)\\bvoid\\s+main\\s*\\(", "void veyraShaderPackMain(");
        if (handPass) adapted += "\n// hand-depth is applied by the runtime wrapper after the pack entrypoint\n";
        return new AdaptedStage(adapted, prepared.varyings(), prepared.usesNoiseSampler(),
                prepared.usesMidTexCoord());
    }

    static Map<String, TerrainVertexShaderAdapter.PackVarying> describeVaryings(
            String programName, String shaderPackSource) throws IOException {
        return describeVaryings(programName, shaderPackSource, Set.of());
    }

    static Map<String, TerrainVertexShaderAdapter.PackVarying> describeVaryings(
            String programName, String shaderPackSource, Set<String> customTextureSamplers) throws IOException {
        return prepare(shaderPackSource, programName,
                EntityIdMap.UNMAPPED, BlockIdMap.UNMAPPED, ItemIdMap.UNMAPPED, false,
                customTextureSamplers).varyings();
    }

    private static PreparedStage prepare(String shaderPackSource, String programName,
                                         int entityId, int blockEntityId,
                                         int currentRenderedItemId,
                                         boolean fogValuesAlreadyDeclared,
                                         Set<String> customTextureSamplers) throws IOException {
        if (shaderPackSource == null || shaderPackSource.isBlank()) throw new IOException(programName + ".vsh is empty.");
        if (shaderPackSource.length() > 1024 * 1024) throw new IOException(programName + ".vsh exceeds the 1 MiB size limit.");
        String source = GlslPreprocessor.process(programName + ".vsh", shaderPackSource);
        source = removeDefaultFunctionInputQualifiers(source);
        source = IrisRenderStages.bindUniform(source, IrisRenderStages.forProgram(programName), programName);
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
        source = isEntityShadowProgram(programName)
                ? replaceShadowEntityMaterialInput(source, programName)
                : removeUnusedMcEntityDeclaration(source, programName);
        if (isEntityShadowProgram(programName)) {
            // Entity and block-entity draws have no block material or mid-block attribute. The
            // material bridge above maps mc_Entity to zero, so voxel writers take their existing
            // non-block early return; this zero vector keeps their shared GLSL helpers compilable.
            source = replaceShadowEntityMidBlockInput(source);
            source = replaceEntityVertexId(source);
        }
        Matcher textureMatrix = TEXTURE_MATRIX.matcher(ShaderSource.maskBlockAndLineComments(source));
        while (textureMatrix.find()) {
            if (!Set.of("0", "1").contains(textureMatrix.group(1))) {
                throw new IOException(programName + ".vsh uses an unsupported gl_TextureMatrix index "
                        + textureMatrix.group(1) + ".");
            }
        }
        Matcher unsupportedInput = LEGACY_INPUT.matcher(ShaderSource.maskBlockAndLineComments(source));
        if (unsupportedInput.find()) throw new IOException(programName + ".vsh uses legacy interface '"
                + unsupportedInput.group(1) + "', which is not provided by Minecraft's "
                + vertexFormatName(programName) + " vertex format.");
        boolean usesMidTexCoord = Pattern.compile("\\bmc_midTexCoord\\b")
                .matcher(ShaderSource.maskBlockAndLineComments(source)).find();
        if (usesMidTexCoord && usesBlockFormat(programName)) {
            throw new IOException(programName + ".vsh uses legacy interface 'mc_midTexCoord', which is not provided by Minecraft's BLOCK vertex format.");
        }
        if (usesBlockFormat(programName)
                && LEGACY_BLOCK_OVERLAY_INPUT.matcher(ShaderSource.maskBlockAndLineComments(source)).find()) {
            throw new IOException(programName + ".vsh uses legacy interface 'gl_MultiTexCoord2', which is not provided by Minecraft's BLOCK vertex format.");
        }

        source = expandCommaOutputs(source);
        OutputBridge outputs = extractOutputs(source, programName);
        source = removeAttributes(outputs.source(), programName);
        Matcher unsupportedAttribute = UNSUPPORTED_ATTRIBUTE.matcher(ShaderSource.maskBlockAndLineComments(source));
        if (unsupportedAttribute.find()) throw new IOException(programName + ".vsh declares an unsupported vertex attribute.");
        Matcher main = MAIN.matcher(ShaderSource.maskBlockAndLineComments(source));
        if (!main.find()) throw new IOException(programName + ".vsh has no main() entrypoint.");
        if (main.find()) throw new IOException(programName + ".vsh must contain exactly one active main() entrypoint.");

        source = UnusedSamplerDeclarations.removeUnused(source);
        SamplerBridge samplers = adaptSamplers(source, programName, customTextureSamplers);
        source = samplers.source();
        source = EntityShaderAdapter.bindEntityIdentifiers(source, programName,
                entityId, blockEntityId, currentRenderedItemId);
        IrisFogUniformAdapter.AdaptedShader fog = IrisFogUniformAdapter.extract(
                source, programName, fogValuesAlreadyDeclared);
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
                outputs.declarationsAndMacros(), outputs.varyings(), samplers.usesNoiseSampler(), usesMidTexCoord);
    }

    private static String removeUnusedMcEntityDeclaration(String source, String programName) throws IOException {
        String uncommented = ShaderSource.maskBlockAndLineComments(source);
        Matcher declaration = MC_ENTITY_ATTRIBUTE_DECLARATION.matcher(uncommented);
        List<int[]> declarations = new ArrayList<>();
        StringBuilder withoutDeclarations = new StringBuilder(uncommented);
        while (declaration.find()) {
            declarations.add(new int[] {declaration.start(), declaration.end()});
            for (int i = declaration.start(); i < declaration.end(); i++) {
                if (withoutDeclarations.charAt(i) != '\n' && withoutDeclarations.charAt(i) != '\r') {
                    withoutDeclarations.setCharAt(i, ' ');
                }
            }
        }
        if (declarations.isEmpty()) return source;
        if (Pattern.compile("\\bmc_Entity\\b").matcher(withoutDeclarations).find()) {
            throw new IOException(programName + ".vsh uses legacy interface 'mc_Entity', which is not provided by Minecraft's "
                    + vertexFormatName(programName) + " vertex format.");
        }
        StringBuilder clean = new StringBuilder(source.length());
        int cursor = 0;
        for (int[] span : declarations) {
            clean.append(source, cursor, span[0]);
            cursor = span[1];
        }
        clean.append(source, cursor, source.length());
        return clean.toString();
    }

    private static String removeDefaultFunctionInputQualifiers(String source) {
        Matcher matcher = FUNCTION_INPUT_QUALIFIER.matcher(ShaderSource.maskBlockAndLineComments(source));
        StringBuilder normalized = new StringBuilder(source.length());
        int cursor = 0;
        while (matcher.find()) {
            normalized.append(source, cursor, matcher.end(1));
            cursor = matcher.end();
        }
        return normalized.append(source, cursor, source.length()).toString();
    }

    /** Entity and block-entity shadow draws have no block-material vertex attribute; zero is the Iris sentinel. */
    private static String replaceShadowEntityMaterialInput(String source, String programName) throws IOException {
        String masked = ShaderSource.maskBlockAndLineComments(source);
        Matcher declarations = MC_ENTITY_ATTRIBUTE_DECLARATION.matcher(masked);
        StringBuilder withoutDeclarations = new StringBuilder(source.length());
        int cursor = 0;
        while (declarations.find()) {
            withoutDeclarations.append(source, cursor, declarations.start());
            cursor = declarations.end();
        }
        withoutDeclarations.append(source, cursor, source.length());
        String clean = withoutDeclarations.toString();
        String cleanMasked = ShaderSource.maskBlockAndLineComments(clean);
        if (Pattern.compile("(?m)^[\\t ]*(?:layout\\s*\\([^)]*\\)\\s*)?(?:attribute|in)\\s+[^;]*\\bmc_Entity\\b[^;]*;")
                .matcher(cleanMasked).find()) {
            throw new IOException(programName + ".vsh declares mc_Entity with an unsupported vertex attribute layout.");
        }
        Matcher references = Pattern.compile("\\bmc_Entity\\b").matcher(cleanMasked);
        StringBuilder replaced = new StringBuilder(clean.length());
        cursor = 0;
        while (references.find()) {
            replaced.append(clean, cursor, references.start()).append("vec4(0.0)");
            cursor = references.end();
        }
        return replaced.append(clean, cursor, clean.length()).toString();
    }

    private static String replaceShadowEntityMidBlockInput(String source) {
        Matcher declarations = ENTITY_MID_BLOCK_ATTRIBUTE_DECLARATION.matcher(
                ShaderSource.maskBlockAndLineComments(source));
        StringBuilder withoutDeclaration = new StringBuilder(source);
        while (declarations.find()) {
            for (int index = declarations.start(); index < declarations.end(); index++) {
                char character = withoutDeclaration.charAt(index);
                if (character != '\n' && character != '\r') withoutDeclaration.setCharAt(index, ' ');
            }
        }

        String clean = withoutDeclaration.toString();
        Matcher references = MID_BLOCK_REFERENCE.matcher(ShaderSource.maskBlockAndLineComments(clean));
        StringBuilder replaced = new StringBuilder(clean.length());
        int cursor = 0;
        while (references.find()) {
            replaced.append(clean, cursor, references.start()).append("vec4(0.0)");
            cursor = references.end();
        }
        return replaced.append(clean, cursor, clean.length()).toString();
    }

    private static String replaceEntityVertexId(String source) {
        Matcher references = ENTITY_VERTEX_ID.matcher(ShaderSource.maskBlockAndLineComments(source));
        StringBuilder replaced = new StringBuilder(source.length());
        int cursor = 0;
        while (references.find()) {
            replaced.append(source, cursor, references.start()).append("gl_VertexIndex");
            cursor = references.end();
        }
        return replaced.append(source, cursor, source.length()).toString();
    }

    private static boolean isEntityShadowProgram(String programName) {
        return programName.equals("shadow_entities") || programName.equals("shadow_block");
    }

    private static SamplerBridge adaptSamplers(String source, String programName,
                                               Set<String> customTextureSamplers) throws IOException {
        Matcher matcher = SAMPLER.matcher(ShaderSource.maskBlockAndLineComments(source));
        StringBuilder clean = new StringBuilder(source.length());
        int cursor = 0;
        boolean noise = false;
        Set<String> nativeSamplers = new HashSet<>();
        Set<String> customSamplers = new java.util.TreeSet<>();
        while (matcher.find()) {
            String type = matcher.group(1), name = matcher.group(2);
            if (!type.equals("sampler2D")) {
                throw new IOException(programName + ".vsh uses unsupported sampler '" + name + "'.");
            }
            if (name.equals("noisetex")) {
                if (noise) throw new IOException(programName + ".vsh declares sampler 'noisetex' more than once.");
                noise = true;
            } else if (customTextureSamplers.contains(name)) {
                customSamplers.add(name);
            } else if (Set.of("tex", "texture", "gtexture", "Sampler0", "lightmap", "Sampler2").contains(name)) {
                nativeSamplers.add(name);
            } else {
                throw new IOException(programName + ".vsh uses unsupported sampler '" + name + "'.");
            }
            clean.append(source, cursor, matcher.start());
            cursor = matcher.end();
        }
        clean.append(source, cursor, source.length());
        SamplerArguments arguments = rewriteSamplerArguments(clean.toString(), nativeSamplers);
        StringBuilder bridge = new StringBuilder();
        if (arguments.usesEntityTexture()) bridge.append("uniform sampler2D Sampler0;\n");
        if (arguments.usesLightmap()) bridge.append("uniform sampler2D Sampler2;\n");
        if (noise) bridge.append("uniform sampler2D VeyraNoiseSampler;\n#define noisetex VeyraNoiseSampler\n");
        for (String sampler : customSamplers) bridge.append("uniform sampler2D ").append(sampler).append(";\n");
        return new SamplerBridge(arguments.source(), bridge.toString(),
                TerrainShaderAdapter.usesNoiseSampler(arguments.source()));
    }

    private static SamplerArguments rewriteSamplerArguments(String source, Set<String> declaredSamplers) {
        Matcher matcher = SAMPLER_ARGUMENT.matcher(ShaderSource.maskBlockAndLineComments(source));
        StringBuilder rewritten = new StringBuilder(source.length());
        Set<String> nativeSamplers = new HashSet<>(declaredSamplers);
        int cursor = 0;
        while (matcher.find()) {
            String alias = matcher.group(2);
            String nativeName = alias.equals("lightmap") || alias.equals("Sampler2") ? "Sampler2" : "Sampler0";
            nativeSamplers.add(nativeName);
            rewritten.append(source, cursor, matcher.start(2)).append(nativeName);
            cursor = matcher.end(2);
        }
        rewritten.append(source, cursor, source.length());
        boolean usesEntityTexture = nativeSamplers.stream()
                .anyMatch(name -> Set.of("tex", "texture", "gtexture", "Sampler0").contains(name));
        boolean usesLightmap = nativeSamplers.stream()
                .anyMatch(name -> name.equals("lightmap") || name.equals("Sampler2"));
        return new SamplerArguments(rewritten.toString(), usesEntityTexture, usesLightmap);
    }

    private static OutputBridge extractOutputs(String source, String programName) throws IOException {
        String maskedSource = ShaderSource.maskBlockAndLineComments(source);
        Matcher matcher = OUTPUT.matcher(maskedSource);
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
        String uncommentedWithoutKnown = ShaderSource.maskBlockAndLineComments(clean.toString());
        if (UNSUPPORTED_OUTPUT.matcher(uncommentedWithoutKnown).find()) {
            throw new IOException(programName + ".vsh declares an unsupported vertex output.");
        }
        return new OutputBridge(clean.toString(), declarations.toString(), Map.copyOf(varyings));
    }

    private static String removeAttributes(String source, String programName) throws IOException {
        Matcher matcher = ATTRIBUTE.matcher(ShaderSource.maskBlockAndLineComments(source));
        Map<String, NativeAttribute> nativeAttributes = nativeAttributes(programName);
        StringBuilder clean = new StringBuilder(source.length());
        int cursor = 0;
        while (matcher.find()) {
            String name = matcher.group(3), type = matcher.group(2);
            NativeAttribute attribute = nativeAttributes.get(name);
            if (attribute == null) throw new IOException(programName + ".vsh attribute '" + name
                    + "' is not provided by Minecraft's " + vertexFormatName(programName) + " vertex format.");
            boolean legacyFloatUv = !usesBlockFormat(programName)
                    && (name.equals("UV1") || name.equals("UV2")) && type.equals("vec2");
            if (!attribute.type().equals(type) && !legacyFloatUv) {
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

    private static boolean usesBlockFormat(String programName) {
        return programName.equals("gbuffers_damagedblock");
    }

    private static String vertexFormatName(String programName) {
        return usesBlockFormat(programName) ? "BLOCK" : "NEW_ENTITY";
    }

    private static Map<String, NativeAttribute> nativeAttributes(String programName) {
        return usesBlockFormat(programName) ? BLOCK_ATTRIBUTES : NEW_ENTITY_ATTRIBUTES;
    }

    private static String nativeBridge(String programName, boolean usesMidTexCoord) {
        String lightmapAliases = usesBlockFormat(programName)
                ? """
                #define gl_MultiTexCoord1 vec4(vec2(UV2), 0.0, 1.0)
                """
                : """
                #define gl_MultiTexCoord1 vec4(vec2(UV2), 0.0, 1.0)
                #define gl_MultiTexCoord2 vec4(vec2(UV1), 0.0, 1.0)
                """;
        return """
                #define gl_Vertex vec4(Position, 1.0)
                #define gl_Color Color
                #define gl_Normal Normal
                #define gl_MultiTexCoord0 vec4(UV0, 0.0, 1.0)
                """ + lightmapAliases + (usesMidTexCoord
                ? "#define mc_midTexCoord vec4(MidTexCoord, 0.0, 1.0)\n" : "") + """
                #define gl_ModelViewMatrix ModelViewMat
                #define gl_ProjectionMatrix ProjMat
                #define gl_ModelViewProjectionMatrix (ProjMat * ModelViewMat)
                #define gl_NormalMatrix transpose(inverse(mat3(ModelViewMat)))
                #define ftransform() ((ProjMat * ModelViewMat) * vec4(Position, 1.0))
                """;
    }

    private static String validationNativeSource(String programName) {
        return validationNativeSource(programName, false);
    }

    private static String validationNativeSource(String programName, boolean usesMidTexCoord) {
        StringBuilder source = new StringBuilder("""
                #version 460
                """);
        nativeAttributes(programName).entrySet().stream()
                .filter(entry -> !usesMidTexCoord || !entry.getKey().equals("mc_midTexCoord"))
                .sorted(java.util.Comparator.comparingInt(entry -> entry.getValue().location()))
                .forEach(entry -> source.append("layout(location=").append(entry.getValue().location())
                        .append(") in ").append(entry.getValue().type()).append(' ').append(entry.getKey()).append(';')
                        .append('\n'));
        if (usesMidTexCoord) {
            source.append("in vec2 MidTexCoord;\n");
        }
        return source.append("""
                layout(location=0) out vec4 vertexColor;
                layout(location=1) out vec2 texCoord0;
                layout(location=2) out vec4 lightMapColor;
                layout(location=3) out vec4 overlayColor;
                uniform mat4 ModelViewMat;
                uniform mat4 ProjMat;
                uniform mat4 TextureMat;
                void main() { gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0); }
                """).toString();
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
                        boolean usesNoiseSampler, boolean usesMidTexCoord) { }
    private record PreparedStage(String source, List<String> extensions, String uniformBridge,
                                 String outputDeclarations,
                                 Map<String, TerrainVertexShaderAdapter.PackVarying> varyings,
                                 boolean usesNoiseSampler, boolean usesMidTexCoord) { }
    private record OutputBridge(String source, String declarationsAndMacros,
                                Map<String, TerrainVertexShaderAdapter.PackVarying> varyings) { }
    private record SamplerBridge(String source, String bridge, boolean usesNoiseSampler) { }
    private record SamplerArguments(String source, boolean usesEntityTexture, boolean usesLightmap) { }
    private record NativeAttribute(String type, int location) { }
    private record MainFunction(int start, int end, int closeBrace) { }
}
