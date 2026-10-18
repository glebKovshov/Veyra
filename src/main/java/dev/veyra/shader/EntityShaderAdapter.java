package dev.veyra.shader;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Bridges a small Iris entity-fragment interface to Minecraft's entity vertex outputs. */
public final class EntityShaderAdapter {
    private static final Pattern VERSION_DIRECTIVE = Pattern.compile("(?m)^[\\t ]*#version[^\\r\\n]*(?:\\r?\\n|$)");
    private static final Pattern EXTENSION_DIRECTIVE = Pattern.compile("(?m)^[\\t ]*#extension[^\\r\\n]*(?:\\r?\\n|$)");
    private static final Pattern PRECISION_DECLARATION = Pattern.compile("(?m)^[\\t ]*precision\\s+(?:lowp|mediump|highp)\\s+\\w+\\s*;[\\t ]*(?:\\r?\\n|$)");
    private static final Pattern ALPHA_TEST_UNIFORM = Pattern.compile(
            "(?m)^[\\t ]*uniform\\s+(?:(?:lowp|mediump|highp)\\s+)?float\\s+alphaTestRef\\s*;[\\t ]*(?://[^\\r\\n]*)?(?:\\r?\\n|$)");
    private static final Pattern SAMPLER_DECLARATION = Pattern.compile(
            "(?m)^[\\t ]*uniform\\s+(?:(?:lowp|mediump|highp)\\s+)?(sampler\\w+)\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*;[\\t ]*(?://[^\\r\\n]*)?(?:\\r?\\n|$)");
    private static final Pattern UNSUPPORTED_UNIFORM_DECLARATION = Pattern.compile("(?m)^\\s*uniform\\b");
    private static final Pattern VARYING_DECLARATION = Pattern.compile(
            "(?m)^[\\t ]*(?:layout\\s*\\(\\s*location\\s*=\\s*(\\d+)\\s*\\)\\s*)?(?:varying|in)\\s+(?:(?:flat|smooth|noperspective)\\s+)?([A-Za-z_][A-Za-z0-9_]*)\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*;[\\t ]*(?://[^\\r\\n]*)?(?:\\r?\\n|$)");
    private static final Pattern UNSUPPORTED_INPUT_DECLARATION = Pattern.compile(
            "(?m)^[\\t ]*(?:layout\\s*\\([^)]*\\)\\s*)?(?:varying|in)\\b[^\\r\\n]*;?");
    private static final Pattern OUTPUT_DECLARATION = Pattern.compile(
            "(?m)^[\\t ]*(?:layout\\s*\\(\\s*location\\s*=\\s*(\\d+)\\s*\\)\\s*)?out\\s+vec4\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*;[\\t ]*(?://[^\\r\\n]*)?(?:\\r?\\n|$)");
    private static final Pattern UNSUPPORTED_OUTPUT_DECLARATION = Pattern.compile(
            "(?m)^[\\t ]*(?:layout\\s*\\([^)]*\\)\\s*)?out\\s+(?!vec4\\s+[A-Za-z_][A-Za-z0-9_]*\\s*;)[^\\r\\n]*;");
    private static final Pattern GL_FRAG_DATA_INDEX = Pattern.compile("\\bgl_FragData\\s*\\[\\s*(\\d+)\\s*\\]");
    private static final Pattern RENDERTARGETS_DIRECTIVE = Pattern.compile("(?is)/\\*\\s*RENDERTARGETS\\s*:\\s*([0-9,\\s]+)\\s*\\*/");
    private static final Pattern DRAWBUFFERS_DIRECTIVE = Pattern.compile("(?is)/\\*\\s*DRAWBUFFERS\\s*:\\s*([0-9]+)\\s*\\*/");
    private static final Pattern COMMENTS = Pattern.compile("(?s)/\\*.*?\\*/|//[^\\r\\n]*");
    private static final Pattern UNSUPPORTED_ENTITY_INPUT = Pattern.compile(
            "\\b(?:mc_Entity|mc_midTexCoord|midTexCoord|viewPos|viewNormal|tangent|at_tangent|lmcoord|lightmap|gl_Fog\\w*|fogColor|fogStart|fogEnd)\\b");
    private static final Pattern UNSUPPORTED_TEXT_INPUT = Pattern.compile(
            "\\b(?:mc_Entity|mc_midTexCoord|midTexCoord|normal|viewPos|viewNormal|tangent|at_tangent|lmcoord|lightmap|lightMapColor|overlayColor|gl_Fog\\w*|fogColor|fogStart|fogEnd)\\b");
    private static final Pattern UNSUPPORTED_TEXTURE_FUNCTION = Pattern.compile(
            "\\btexture(?:Gather|Offset|Proj|Size|QueryLod)\\w*\\s*\\(");
    private static final Pattern NORMAL_VERTEX_ATTRIBUTE = Pattern.compile(
            "(?m)^[\\t ]*(?:layout\\s*\\([^)]*\\)\\s*)?in\\s+vec3\\s+Normal\\s*;");
    private static final Pattern NORMAL_VERTEX_OUTPUT = Pattern.compile(
            "(?m)^[\\t ]*(?:layout\\s*\\([^)]*\\)\\s*)?out\\s+vec3\\s+normal\\s*;");
    private static final Pattern MAIN_FUNCTION = Pattern.compile("\\bvoid\\s+main\\s*\\(\\s*(?:void\\s*)?\\)\\s*\\{");
    private static final Pattern NORMAL_REFERENCE = Pattern.compile("\\bnormal\\b");
    private static final Map<String, Varying> SUPPORTED_VARYINGS = Map.ofEntries(
            Map.entry("texcoord", new Varying("vec2")),
            Map.entry("texCoord", new Varying("vec2")),
            Map.entry("texCoord0", new Varying("vec2")),
            Map.entry("glcolor", new Varying("vec4")),
            Map.entry("glColor", new Varying("vec4")),
            Map.entry("gl_Color", new Varying("vec4")),
            Map.entry("sphericalVertexDistance", new Varying("float")),
            Map.entry("cylindricalVertexDistance", new Varying("float")),
            Map.entry("lightMapColor", new Varying("vec4")),
            Map.entry("overlayColor", new Varying("vec4")),
            Map.entry("normal", new Varying("vec3")));
    private static final Map<String, Varying> SUPPORTED_TEXT_VARYINGS = Map.ofEntries(
            Map.entry("texcoord", new Varying("vec2")),
            Map.entry("texCoord", new Varying("vec2")),
            Map.entry("texCoord0", new Varying("vec2")),
            Map.entry("glcolor", new Varying("vec4")),
            Map.entry("glColor", new Varying("vec4")),
            Map.entry("gl_Color", new Varying("vec4")),
            Map.entry("sphericalVertexDistance", new Varying("float")),
            Map.entry("cylindricalVertexDistance", new Varying("float")));

    private EntityShaderAdapter() {
    }

    public static String adaptFragment(String source) throws IOException {
        return adaptFragment(source, "gbuffers_entities");
    }

    public static String adaptFragment(String source, String programName) throws IOException {
        return adaptFragment(source, programName, false);
    }

    public static String adaptTextFragment(String source, String programName) throws IOException {
        return adaptFragment(source, programName, true);
    }

    public static boolean requiresNormalInput(String source) {
        String uncommented = COMMENTS.matcher(source == null ? "" : source).replaceAll("");
        Matcher matcher = VARYING_DECLARATION.matcher(uncommented);
        while (matcher.find()) {
            if (matcher.group(3).equals("normal")) {
                return true;
            }
        }
        return false;
    }

    public static String adaptVertexShader(String source, boolean shadowPass, String programName) throws IOException {
        if (source == null || source.isBlank()) {
            throw new IOException(programName + " requires a normal input, but its VulkanMod vertex shader is empty.");
        }
        String uncommented = COMMENTS.matcher(source).replaceAll("");
        if (NORMAL_VERTEX_OUTPUT.matcher(uncommented).find()) {
            return source;
        }
        if (!NORMAL_VERTEX_ATTRIBUTE.matcher(uncommented).find()) {
            throw new IOException(programName + " requires normals, but VulkanMod's vertex format does not provide them.");
        }
        Matcher main = MAIN_FUNCTION.matcher(source);
        if (!main.find()) {
            throw new IOException(programName + " requires normals, but its VulkanMod vertex shader has no main function.");
        }

        String inverseMatrix = shadowPass ? "shadowModelViewInverse" : "gbufferModelViewInverse";
        Pattern inverseMatrixUniform = Pattern.compile("(?m)^[\\t ]*uniform\\s+mat4\\s+"
                + Pattern.quote(inverseMatrix) + "\\s*;");
        String declarations = (inverseMatrixUniform.matcher(uncommented).find()
                ? "" : "uniform mat4 " + inverseMatrix + ";\n") + "out vec3 normal;\n";
        String withDeclarations = source.substring(0, main.start()) + declarations + source.substring(main.start());
        Matcher adaptedMain = MAIN_FUNCTION.matcher(withDeclarations);
        if (!adaptedMain.find()) {
            throw new IOException(programName + " requires normals, but its VulkanMod vertex shader could not be adapted.");
        }
        String transformedNormal = "normal = normalize(mat3(" + inverseMatrix
                + ") * transpose(inverse(mat3(ModelViewMat))) * Normal);\n";
        return withDeclarations.substring(0, adaptedMain.end()) + transformedNormal
                + withDeclarations.substring(adaptedMain.end());
    }

    private static String adaptFragment(String source, String programName, boolean textPipeline) throws IOException {
        if (source == null || source.isBlank()) {
            throw new IOException(programName + " fragment shader is empty.");
        }
        if (source.length() > 1024 * 1024) {
            throw new IOException(programName + " fragment shader exceeds the 1 MiB size limit.");
        }

        String adapted = VERSION_DIRECTIVE.matcher(source.replace("\uFEFF", "")).replaceAll("");
        List<String> extensions = new ArrayList<>();
        Matcher extensionMatcher = EXTENSION_DIRECTIVE.matcher(adapted);
        while (extensionMatcher.find()) {
            extensions.add(extensionMatcher.group().strip());
        }
        adapted = extensionMatcher.replaceAll("");
        adapted = PRECISION_DECLARATION.matcher(adapted).replaceAll("");
        adapted = ALPHA_TEST_UNIFORM.matcher(adapted).replaceAll("");

        Matcher samplerMatcher = SAMPLER_DECLARATION.matcher(adapted);
        StringBuffer withoutSamplers = new StringBuffer();
        while (samplerMatcher.find()) {
            String sampler = samplerMatcher.group(2);
            if (!sampler.equals("texture") && !sampler.equals("gtexture") && !sampler.equals("Sampler0")) {
                throw new IOException(programName + " uses unsupported sampler '" + sampler + "'.");
            }
            if (!samplerMatcher.group(1).equals("sampler2D")) {
                throw new IOException(programName + " sampler '" + sampler + "' must use sampler2D.");
            }
            samplerMatcher.appendReplacement(withoutSamplers, "");
        }
        samplerMatcher.appendTail(withoutSamplers);
        adapted = withoutSamplers.toString();
        ShaderPackUniformAdapter.AdaptedShader shaderPackUniforms =
                ShaderPackUniformAdapter.extract(adapted, programName);
        adapted = shaderPackUniforms.source();
        if (UNSUPPORTED_UNIFORM_DECLARATION.matcher(adapted).find()) {
            throw new IOException(programName + " has an unsupported uniform declaration.");
        }

        Matcher varyingMatcher = VARYING_DECLARATION.matcher(adapted);
        StringBuffer withoutVaryings = new StringBuffer();
        boolean suppliesNormal = false;
        while (varyingMatcher.find()) {
            String type = varyingMatcher.group(2);
            String name = varyingMatcher.group(3);
            Varying varying = (textPipeline ? SUPPORTED_TEXT_VARYINGS : SUPPORTED_VARYINGS).get(name);
            if (varying == null) {
                throw new IOException(programName + " input '" + name + "' is not provided by Veyra's "
                        + (textPipeline ? "sign-text" : "entity") + " bridge yet.");
            }
            if (!varying.type().equals(type)) {
                throw new IOException(programName + " input '" + name + "' must use " + varying.type() + ".");
            }
            if (name.equals("normal")) {
                suppliesNormal = true;
            }
            varyingMatcher.appendReplacement(withoutVaryings, "");
        }
        varyingMatcher.appendTail(withoutVaryings);
        adapted = withoutVaryings.toString();
        if (UNSUPPORTED_INPUT_DECLARATION.matcher(adapted).find()) {
            throw new IOException(programName + " declares inputs that are not provided by Veyra's entity bridge yet.");
        }

        String outputName = "veyraEntityFragColor";
        Matcher outputMatcher = OUTPUT_DECLARATION.matcher(adapted);
        StringBuffer withoutOutputs = new StringBuffer();
        int outputCount = 0;
        while (outputMatcher.find()) {
            String location = outputMatcher.group(1);
            if (location != null && !location.equals("0")) {
                throw new IOException(programName + " can write only color attachment 0.");
            }
            outputName = outputMatcher.group(2);
            outputCount++;
            outputMatcher.appendReplacement(withoutOutputs, "");
        }
        outputMatcher.appendTail(withoutOutputs);
        if (outputCount > 1) {
            throw new IOException(programName + " can declare only one fragment output.");
        }
        adapted = withoutOutputs.toString();
        if (UNSUPPORTED_OUTPUT_DECLARATION.matcher(adapted).find()) {
            throw new IOException(programName + " supports one vec4 fragment output.");
        }

        String uncommented = COMMENTS.matcher(adapted).replaceAll("");
        if ((textPipeline ? UNSUPPORTED_TEXT_INPUT : UNSUPPORTED_ENTITY_INPUT).matcher(uncommented).find()) {
            throw new IOException(programName + " uses inputs that Veyra's "
                    + (textPipeline ? "sign-text" : "entity") + " bridge does not provide yet.");
        }
        if (!suppliesNormal && !textPipeline && NORMAL_REFERENCE.matcher(uncommented).find()) {
            throw new IOException(programName + " uses 'normal' without declaring it as a fragment input.");
        }
        if (UNSUPPORTED_TEXTURE_FUNCTION.matcher(uncommented).find()) {
            throw new IOException(programName + " uses a texture sampling function that Veyra's entity bridge does not support yet.");
        }

        Matcher renderTargets = RENDERTARGETS_DIRECTIVE.matcher(source);
        while (renderTargets.find()) {
            String[] targets = renderTargets.group(1).split(",");
            if (targets.length != 1 || !targets[0].trim().equals("0")) {
                throw new IOException(programName + " can write only color attachment 0.");
            }
        }
        Matcher drawBuffers = DRAWBUFFERS_DIRECTIVE.matcher(source);
        while (drawBuffers.find()) {
            if (!drawBuffers.group(1).equals("0")) {
                throw new IOException(programName + " can write only color attachment 0.");
            }
        }
        Matcher fragDataMatcher = GL_FRAG_DATA_INDEX.matcher(uncommented);
        while (fragDataMatcher.find()) {
            if (!fragDataMatcher.group(1).equals("0")) {
                throw new IOException(programName + " can write only color attachment 0.");
            }
        }

        adapted = adapted
                .replaceAll("\\bgl_FragData\\s*\\[\\s*0\\s*\\]", outputName)
                .replaceAll("\\btexture2DLod(?:EXT)?\\s*\\(", "veyraEntitySampleLod(")
                .replaceAll("\\btextureLod\\s*\\(", "veyraEntitySampleLod(")
                .replaceAll("\\btexture2DGrad(?:ARB)?\\s*\\(", "veyraEntitySampleGrad(")
                .replaceAll("\\btextureGrad\\s*\\(", "veyraEntitySampleGrad(")
                .replaceAll("\\btexture2D\\s*\\(", "veyraEntitySample(")
                .replaceAll("\\btexture\\s*\\(", "veyraEntitySample(")
                .replaceAll("\\bgl_FragColor\\b", outputName)
                .replaceAll("\\btexcoord\\b", "texCoord0")
                .replaceAll("\\btexCoord\\b", "texCoord0")
                .replaceAll("\\bglcolor\\b|\\bglColor\\b|\\bgl_Color\\b", "veyraEntityColor");

        String bridge = textPipeline ? """
                in float sphericalVertexDistance;
                in float cylindricalVertexDistance;
                in vec4 vertexColor;
                in vec2 texCoord0;
                out vec4 veyraEntityFragColor;
                uniform sampler2D Sampler0;
                vec4 veyraEntitySample(sampler2D sourceSampler, vec2 sourceUv) {
                    return texture(sourceSampler, sourceUv);
                }
                vec4 veyraEntitySampleLod(sampler2D sourceSampler, vec2 sourceUv, float lod) {
                    return textureLod(sourceSampler, sourceUv, lod);
                }
                vec4 veyraEntitySampleGrad(sampler2D sourceSampler, vec2 sourceUv, vec2 dx, vec2 dy) {
                    return textureGrad(sourceSampler, sourceUv, dx, dy);
                }
                #define texture Sampler0
                #define gtexture Sampler0
                #define veyraEntityColor vertexColor
                #ifndef ALPHA_CUTOUT
                #define ALPHA_CUTOUT 0.1
                #endif
                #define alphaTestRef ALPHA_CUTOUT
                """.replace("veyraEntityFragColor;", outputName + ";") : """
                in float sphericalVertexDistance;
                in float cylindricalVertexDistance;
                #ifdef PER_FACE_LIGHTING
                in vec4 vertexPerFaceColorBack;
                in vec4 vertexPerFaceColorFront;
                #define veyraEntityColor (gl_FrontFacing ? vertexPerFaceColorFront : vertexPerFaceColorBack)
                #else
                in vec4 vertexColor;
                #define veyraEntityColor vertexColor
                #endif
                #ifndef EMISSIVE
                in vec4 lightMapColor;
                #else
                #define lightMapColor vec4(1.0)
                #endif
                #ifndef NO_OVERLAY
                in vec4 overlayColor;
                #else
                #define overlayColor vec4(0.0)
                #endif
                __VEYRA_NORMAL_INPUT__
                in vec2 texCoord0;
                out vec4 veyraEntityFragColor;
                uniform sampler2D Sampler0;
                vec4 veyraEntitySample(sampler2D sourceSampler, vec2 sourceUv) {
                    return texture(sourceSampler, sourceUv);
                }
                vec4 veyraEntitySampleLod(sampler2D sourceSampler, vec2 sourceUv, float lod) {
                    return textureLod(sourceSampler, sourceUv, lod);
                }
                vec4 veyraEntitySampleGrad(sampler2D sourceSampler, vec2 sourceUv, vec2 dx, vec2 dy) {
                    return textureGrad(sourceSampler, sourceUv, dx, dy);
                }
                #define texture Sampler0
                #define gtexture Sampler0
                #ifndef ALPHA_CUTOUT
                #define ALPHA_CUTOUT 0.1
                #endif
                #define alphaTestRef ALPHA_CUTOUT
                """.replace("veyraEntityFragColor;", outputName + ";");
        bridge = bridge.replace("__VEYRA_NORMAL_INPUT__", suppliesNormal ? "in vec3 normal;\n" : "");
        StringBuilder result = new StringBuilder("#version 460\n");
        for (String extension : extensions) {
            result.append(extension).append('\n');
        }
        result.append(bridge);
        result.append(shaderPackUniforms.declarations());
        result.append(shaderPackUniforms.macros());
        return result.append(adapted).toString();
    }

    private record Varying(String type) {
    }
}
