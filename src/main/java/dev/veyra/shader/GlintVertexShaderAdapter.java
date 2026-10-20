package dev.veyra.shader;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Runs the shader-pack armor-glint vertex entrypoint after Minecraft's position and fog setup. */
final class GlintVertexShaderAdapter {
    private static final Pattern VERSION_DIRECTIVE = Pattern.compile("(?m)^[\\t ]*#version[^\\r\\n]*(?:\\r?\\n|$)");
    private static final Pattern EXTENSION_DIRECTIVE = Pattern.compile("(?m)^[\\t ]*#extension[^\\r\\n]*(?:\\r?\\n|$)");
    private static final Pattern PRECISION_DECLARATION = Pattern.compile(
            "(?m)^[\\t ]*precision\\s+(?:lowp|mediump|highp)\\s+\\w+\\s*;[\\t ]*(?:\\r?\\n|$)");
    private static final Pattern SAMPLER_DECLARATION = Pattern.compile(
            "(?m)^[\\t ]*(?:layout\\s*\\([^)]*\\)\\s*)?uniform\\s+(sampler\\w+)\\s+"
                    + "([A-Za-z_][A-Za-z0-9_]*)\\s*;[\\t ]*(?://[^\\r\\n]*)?(?:\\r?\\n|$)");
    private static final Pattern OUTPUT_DECLARATION = Pattern.compile(
            "(?m)^[\\t ]*(?:layout\\s*\\([^)]*\\)\\s*)?(?:(flat|smooth|noperspective)\\s+)?(?:varying|out)\\s+"
                    + "(?:(flat|smooth|noperspective)\\s+)?([A-Za-z_][A-Za-z0-9_]*)\\s+"
                    + "([A-Za-z_][A-Za-z0-9_]*)\\s*;[\\t ]*(?://[^\\r\\n]*)?(?:\\r?\\n|$)");
    private static final Pattern UNSUPPORTED_OUTPUT_DECLARATION = Pattern.compile(
            "(?m)(?:^|;)[\\t ]*(?:layout\\s*\\([^)]*\\)\\s*)?"
                    + "(?:(?:flat|smooth|noperspective|centroid|sample|invariant)\\s+)*(?:varying|out)\\b[^\\r\\n;]*;");
    private static final Pattern ATTRIBUTE_DECLARATION = Pattern.compile(
            "(?m)^[\\t ]*(?:layout\\s*\\([^)]*\\)\\s*)?(?:(?:flat|smooth|noperspective)\\s+)?"
                    + "(?:attribute|in)\\s+([A-Za-z_][A-Za-z0-9_]*)\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*;"
                    + "[\\t ]*(?://[^\\r\\n]*)?(?:\\r?\\n|$)");
    private static final Pattern MAIN_DECLARATION = Pattern.compile("\\bvoid\\s+main\\s*\\(\\s*\\)");
    private static final Pattern BASE_OUTPUT = Pattern.compile(
            "(?m)^([\\t ]*)(?:(?:layout\\s*\\([^)]*\\)\\s*)?)(?:(flat|smooth|noperspective)\\s+)?out\\s+"
                    + "([A-Za-z_][A-Za-z0-9_]*)\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*;");
    private static final Pattern BASE_COLOR_INPUT = Pattern.compile(
            "(?m)^[\\t ]*(?:layout\\s*\\([^)]*\\)\\s*)?in\\s+vec4\\s+Color\\s*;");
    private static final Pattern UNSUPPORTED_TEXTURE_MATRIX = Pattern.compile("\\bgl_TextureMatrix\\s*\\[");
    private static final Pattern LIGHTMAP_COORDINATE_FUNCTION = Pattern.compile("\\bGetLightMapCoordinates\\s*\\(");
    private static final Pattern LEGACY_LIGHTMAP_INPUT = Pattern.compile("\\bgl_MultiTexCoord1\\b");
    private static final String LIGHTMAP_TEXTURE_MATRIX = "mat4("
            + "0.00390625, 0.0, 0.0, 0.0, "
            + "0.0, 0.00390625, 0.0, 0.0, "
            + "0.0, 0.0, 0.00390625, 0.0, "
            + "0.03125, 0.03125, 0.03125, 1.0)";
    private static final Map<String, FixedOutput> FIXED_OUTPUTS = Map.ofEntries(
            Map.entry("color", new FixedOutput("vec4", "vertexColor", 0)),
            Map.entry("glColor", new FixedOutput("vec4", "vertexColor", 0)),
            Map.entry("glcolor", new FixedOutput("vec4", "vertexColor", 0)),
            Map.entry("vertexColor", new FixedOutput("vec4", "vertexColor", 0)),
            Map.entry("texcoord", new FixedOutput("vec2", "texCoord0", 1)),
            Map.entry("texCoord", new FixedOutput("vec2", "texCoord0", 1)),
            Map.entry("texCoord0", new FixedOutput("vec2", "texCoord0", 1)),
            Map.entry("sphericalVertexDistance", new FixedOutput("float", "sphericalVertexDistance", 2)),
            Map.entry("cylindricalVertexDistance", new FixedOutput("float", "cylindricalVertexDistance", 3)));

    private GlintVertexShaderAdapter() {
    }

    static AdaptedStage adaptStage(String minecraftSource, String shaderPackSource, String programName)
            throws IOException {
        if (minecraftSource == null || minecraftSource.isBlank()) {
            throw new IOException(programName + ".vsh cannot be executed because Minecraft's glint vertex shader is empty.");
        }
        if (shaderPackSource == null || shaderPackSource.isBlank()) {
            throw new IOException(programName + ".vsh is empty.");
        }
        if (shaderPackSource.length() > 1024 * 1024) {
            throw new IOException(programName + ".vsh exceeds the 1 MiB size limit.");
        }

        String packSource = GlslPreprocessor.process(programName + ".vsh", shaderPackSource);
        packSource = UnreachableGlslFunctions.removeUnreachable(packSource, "main");
        packSource = UnusedUniformDeclarations.removeUnused(packSource);
        List<String> extensions = new ArrayList<>();
        Matcher extensionMatcher = EXTENSION_DIRECTIVE.matcher(packSource);
        while (extensionMatcher.find()) extensions.add(extensionMatcher.group().strip());
        packSource = extensionMatcher.replaceAll("");
        packSource = VERSION_DIRECTIVE.matcher(packSource).replaceAll("");
        packSource = PRECISION_DECLARATION.matcher(packSource).replaceAll("");
        packSource = Pattern.compile("\\bgl_TextureMatrix\\s*\\[\\s*0\\s*\\]")
                .matcher(packSource).replaceAll("TextureMat");
        packSource = Pattern.compile("\\bgl_TextureMatrix\\s*\\[\\s*1\\s*\\]")
                .matcher(packSource).replaceAll(Matcher.quoteReplacement(LIGHTMAP_TEXTURE_MATRIX));
        if (UNSUPPORTED_TEXTURE_MATRIX.matcher(ShaderSource.maskBlockAndLineComments(packSource)).find()) {
            throw new IOException(programName + ".vsh uses a texture matrix index other than 0 or 1, which Veyra does not expose to armor glint.");
        }
        String maskedBeforeLightmapCheck = ShaderSource.maskBlockAndLineComments(packSource);
        Matcher lightmapFunction = LIGHTMAP_COORDINATE_FUNCTION.matcher(maskedBeforeLightmapCheck);
        int lightmapFunctionReferences = 0;
        while (lightmapFunction.find()) lightmapFunctionReferences++;
        Matcher lightmapInput = LEGACY_LIGHTMAP_INPUT.matcher(maskedBeforeLightmapCheck);
        int lightmapInputReferences = 0;
        while (lightmapInput.find()) lightmapInputReferences++;
        if (lightmapFunctionReferences > 1
                || lightmapFunctionReferences == 0 && lightmapInputReferences > 0
                || lightmapInputReferences > 1) {
            throw new IOException(programName + ".vsh reads gl_MultiTexCoord1, which Minecraft's armor-glint vertex format does not provide.");
        }

        OutputDeclarations outputs = extractOutputs(packSource, programName);
        packSource = outputs.source();
        String uncommented = ShaderSource.maskBlockAndLineComments(packSource);
        Matcher attributes = ATTRIBUTE_DECLARATION.matcher(uncommented);
        if (attributes.find()) {
            throw new IOException(programName + ".vsh declares vertex attribute '" + attributes.group(2)
                    + "', which is not part of Minecraft's armor-glint vertex interface.");
        }
        Matcher main = MAIN_DECLARATION.matcher(uncommented);
        if (!main.find()) throw new IOException(programName + ".vsh has no main() entrypoint.");
        if (main.find()) throw new IOException(programName + ".vsh must contain exactly one active main() entrypoint.");

        packSource = UnusedSamplerDeclarations.removeUnused(packSource);
        Matcher samplerMatcher = SAMPLER_DECLARATION.matcher(ShaderSource.maskBlockAndLineComments(packSource));
        StringBuilder withoutSamplers = new StringBuilder(packSource.length());
        int copiedThrough = 0;
        int noisetexDeclarations = 0;
        while (samplerMatcher.find()) {
            String type = samplerMatcher.group(1);
            String name = samplerMatcher.group(2);
            if (!name.equals("noisetex") || !type.equals("sampler2D") || ++noisetexDeclarations > 1) {
                throw new IOException(programName + ".vsh uses unsupported sampler '" + name + "'.");
            }
            withoutSamplers.append(packSource, copiedThrough, samplerMatcher.start());
            copiedThrough = samplerMatcher.end();
        }
        withoutSamplers.append(packSource, copiedThrough, packSource.length());
        packSource = withoutSamplers.toString()
                .replaceAll("\\btexture2DLod(?:EXT)?\\s*\\(", "textureLod(")
                .replaceAll("\\btexture2DGrad(?:ARB)?\\s*\\(", "textureGrad(")
                .replaceAll("\\btexture2DProj\\s*\\(", "textureProj(")
                .replaceAll("\\btexture2D\\s*\\(", "texture(");
        boolean usesNoiseSampler = TerrainShaderAdapter.usesNoiseSampler(packSource);
        IrisFogUniformAdapter.AdaptedShader fog = IrisFogUniformAdapter.extract(packSource, programName, true);
        packSource = UnusedSamplerDeclarations.removeUnused(fog.source());
        ShaderPackUniformAdapter.AdaptedShader uniforms = ShaderPackUniformAdapter.extract(packSource, programName);
        packSource = uniforms.source();
        if (Pattern.compile("(?m)^[\\t ]*uniform\\s+(?:sampler\\w+|\\w+)\\s+\\w+\\s*;")
                .matcher(ShaderSource.maskBlockAndLineComments(packSource)).find()) {
            // ShaderPackUniformAdapter removes every supported declaration; anything left would be
            // an unsupported descriptor in Minecraft's fixed glint pipeline.
            throw new IOException(programName + ".vsh declares a uniform that is not available to the glint pipeline.");
        }

        String base = patchBaseOutputs(minecraftSource, outputs, programName);
        Matcher version = VERSION_DIRECTIVE.matcher(base);
        if (!version.find()) throw new IOException("Minecraft's glint vertex shader has no #version directive.");
        StringBuilder extensionText = new StringBuilder();
        extensions.forEach(extension -> extensionText.append(extension).append('\n'));
        base = base.substring(0, version.end()) + extensionText + base.substring(version.end());

        MainFunction baseMain = findMain(base, "Minecraft's glint vertex shader");
        base = base.substring(0, baseMain.start()) + "void veyraArmorGlintMain();\n" + base.substring(baseMain.start());
        baseMain = findMain(base, "Minecraft's glint vertex shader");
        base = base.substring(0, baseMain.closeBrace()) + "\n    veyraArmorGlintMain();\n" + base.substring(baseMain.closeBrace());

        StringBuilder bridge = new StringBuilder();
        if (outputs.usesLegacyColor()) {
            Matcher input = BASE_COLOR_INPUT.matcher(base);
            if (!input.find()) bridge.append(parserSafeLocatedDeclaration("VEYRA_GLINT_COLOR_INPUT",
                    "layout(location = 2) in vec4 Color;"));
        }
        bridge.append("#define gl_Vertex vec4(Position, 1.0)\n")
                .append("#define gl_Color Color\n")
                .append("#define gl_MultiTexCoord0 vec4(UV0, 0.0, 1.0)\n")
                .append("#define gl_MultiTexCoord1 vec4(0.0, 0.0, 0.0, 1.0)\n")
                .append("#define gl_ModelViewProjectionMatrix (ProjMat * ModelViewMat)\n")
                .append("#define gl_ModelViewMatrix ModelViewMat\n")
                .append("#define gl_ProjectionMatrix ProjMat\n")
                .append("#define ftransform() ((ProjMat * ModelViewMat) * vec4(Position, 1.0))\n")
                .append(usesNoiseSampler ? "uniform sampler2D VeyraNoiseSampler;\n#define noisetex VeyraNoiseSampler\n" : "")
                .append(outputs.macros());
        return new AdaptedStage(base + "\n" + fog.bridge() + uniforms.declarations() + uniforms.macros()
                + bridge + packSource.replaceFirst("(?m)\\bvoid\\s+main\\s*\\(", "void veyraArmorGlintMain("),
                outputs.colorInterpolation(), outputs.texCoordInterpolation(), outputs.usesLegacyColor(),
                usesNoiseSampler);
    }

    static AdaptedStage validate(String programName, String shaderPackSource) throws IOException {
        // A tiny native shader lets preflight validate the pack interface without allocating GPU objects.
        String base = "#version 460\n"
                + "layout(location=0) in vec3 Position;\nlayout(location=1) in vec2 UV0;\n"
                + "layout(location=1) out vec2 texCoord0;\nlayout(location=2) out float sphericalVertexDistance;\n"
                + "layout(location=3) out float cylindricalVertexDistance;\n"
                + "uniform mat4 ProjMat; uniform mat4 ModelViewMat; uniform mat4 TextureMat;\n"
                + "void main(){gl_Position=ProjMat*ModelViewMat*vec4(Position,1.0);texCoord0=UV0;"
                + "sphericalVertexDistance=0.0;cylindricalVertexDistance=0.0;}\n";
        return adaptStage(base, shaderPackSource, programName);
    }

    private static OutputDeclarations extractOutputs(String source, String programName) throws IOException {
        Matcher matcher = OUTPUT_DECLARATION.matcher(ShaderSource.maskBlockAndLineComments(source));
        StringBuilder withoutOutputs = new StringBuilder(source.length());
        StringBuilder macros = new StringBuilder();
        String colorInterpolation = null;
        String texCoordInterpolation = null;
        boolean usesLegacyColor = false;
        int copiedThrough = 0;
        java.util.HashSet<String> seen = new java.util.HashSet<>();
        while (matcher.find()) {
            String interpolation = mergeInterpolation(matcher.group(1), matcher.group(2), programName, matcher.group(4));
            String type = matcher.group(3);
            String name = matcher.group(4);
            FixedOutput output = FIXED_OUTPUTS.get(name);
            if (output == null) {
                throw new IOException(programName + ".vsh output '" + name + "' is not part of the armor-glint interface.");
            }
            if (!output.type().equals(type)) {
                throw new IOException(programName + ".vsh output '" + name + "' must use " + output.type() + ".");
            }
            if (!seen.add(output.variable())) {
                throw new IOException(programName + ".vsh declares the armor-glint output '" + output.variable() + "' more than once.");
            }
            if (output.variable().equals("vertexColor")) {
                colorInterpolation = interpolation;
            } else if (output.variable().equals("texCoord0")) {
                if (!interpolation.equals("smooth")) {
                    throw new IOException(programName + ".vsh output '" + name + "' must use smooth interpolation on Minecraft's glint UV output.");
                }
                texCoordInterpolation = interpolation;
            } else if (!interpolation.equals("smooth")) {
                throw new IOException(programName + ".vsh distance outputs must use smooth interpolation.");
            }
            macros.append("#define ").append(name).append(' ').append(output.variable()).append('\n');
            if (name.equals("glColor") || name.equals("glcolor")) usesLegacyColor = true;
            withoutOutputs.append(source, copiedThrough, matcher.start());
            copiedThrough = matcher.end();
        }
        withoutOutputs.append(source, copiedThrough, source.length());
        if (UNSUPPORTED_OUTPUT_DECLARATION.matcher(ShaderSource.maskBlockAndLineComments(
                OUTPUT_DECLARATION.matcher(ShaderSource.maskBlockAndLineComments(source)).replaceAll(""))).find()) {
            throw new IOException(programName + ".vsh declares an unsupported output; armor glint supports color, texcoord, and fog distances.");
        }
        String clean = withoutOutputs.toString();
        usesLegacyColor |= Pattern.compile("\\bgl_Color\\b").matcher(ShaderSource.maskBlockAndLineComments(clean)).find();
        macros.append("#define color vertexColor\n#define glColor vertexColor\n#define glcolor vertexColor\n")
                .append("#define texcoord texCoord0\n#define texCoord texCoord0\n");
        return new OutputDeclarations(clean, macros.toString(), colorInterpolation,
                texCoordInterpolation == null ? "smooth" : texCoordInterpolation, usesLegacyColor);
    }

    private static String patchBaseOutputs(String source, OutputDeclarations outputs, String programName) throws IOException {
        String patched = source;
        patched = patchOutput(patched, "vec2", "texCoord0", 1, "smooth", programName);
        patched = patchOutput(patched, "float", "sphericalVertexDistance", 2, "smooth", programName);
        patched = patchOutput(patched, "float", "cylindricalVertexDistance", 3, "smooth", programName);
        if (outputs.colorInterpolation() != null) {
            Matcher existing = BASE_OUTPUT.matcher(patched);
            boolean found = false;
            while (existing.find()) {
                if (existing.group(3).equals("vec4") && existing.group(4).equals("vertexColor")) {
                    found = true;
                    break;
                }
            }
            if (found) patched = patchOutput(patched, "vec4", "vertexColor", 0, outputs.colorInterpolation(), programName);
            else patched += "\n" + parserSafeLocatedDeclaration("VEYRA_GLINT_COLOR_OUTPUT",
                    "layout(location = 0) " + outputs.colorInterpolation() + " out vec4 vertexColor;");
        }
        return patched;
    }

    private static String patchOutput(String source, String type, String name, int location,
                                     String interpolation, String programName) throws IOException {
        Matcher matcher = BASE_OUTPUT.matcher(source);
        StringBuffer result = new StringBuffer();
        boolean found = false;
        while (matcher.find()) {
            if (!matcher.group(3).equals(type) || !matcher.group(4).equals(name)) continue;
            found = true;
            matcher.appendReplacement(result, Matcher.quoteReplacement(matcher.group(1)
                    + parserSafeLocatedDeclaration("VEYRA_GLINT_OUTPUT_" + location,
                    "layout(location = " + location + ") " + interpolation + " out " + type + " " + name + ";")));
        }
        if (found) {
            matcher.appendTail(result);
            return result.toString();
        }
        if (name.equals("texCoord0") || name.equals("sphericalVertexDistance") || name.equals("cylindricalVertexDistance")) {
            throw new IOException("Minecraft's glint vertex shader does not declare the " + name + " output expected by " + programName + ".");
        }
        return source;
    }

    /** VulkanMod's RenderSystem parser treats a source-level {@code layout(...)} as a UBO declaration. */
    private static String parserSafeLocatedDeclaration(String macroName, String declaration) {
        return "#define " + macroName + " " + declaration + '\n' + macroName + '\n';
    }

    private static String mergeInterpolation(String first, String second, String programName, String name) throws IOException {
        if (first != null && second != null && !first.equals(second)) {
            throw new IOException(programName + ".vsh output '" + name + "' has conflicting interpolation qualifiers.");
        }
        return first != null ? first : second != null ? second : "smooth";
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
            else if (c == '}' && --depth == 0) return new MainFunction(matcher.start(), i);
        }
        throw new IOException(name + " main() entrypoint has unmatched braces.");
    }

    record AdaptedStage(String source, String colorInterpolation, String texCoordInterpolation,
                        boolean usesLegacyColor, boolean usesNoiseSampler) {
        boolean outputsColor() {
            return colorInterpolation != null;
        }
    }

    private record FixedOutput(String type, String variable, int location) {
    }

    private record OutputDeclarations(String source, String macros, String colorInterpolation,
                                      String texCoordInterpolation, boolean usesLegacyColor) {
    }

    private record MainFunction(int start, int closeBrace) {
    }
}
