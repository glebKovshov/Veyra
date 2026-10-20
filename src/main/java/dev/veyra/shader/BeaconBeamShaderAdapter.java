package dev.veyra.shader;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Adapts the common Iris beacon beam fragment inputs to Minecraft's beacon vertex outputs. */
public final class BeaconBeamShaderAdapter {
    private static final Pattern VERSION_DIRECTIVE = Pattern.compile("(?m)^[\\t ]*#version[^\\r\\n]*(?:\\r?\\n|$)");
    private static final Pattern EXTENSION_DIRECTIVE = Pattern.compile("(?m)^[\\t ]*#extension[^\\r\\n]*(?:\\r?\\n|$)");
    private static final Pattern PRECISION_DECLARATION = Pattern.compile(
            "(?m)^[\\t ]*precision\\s+(?:lowp|mediump|highp)\\s+\\w+\\s*;[\\t ]*(?:\\r?\\n|$)");
    private static final Pattern ALPHA_TEST_UNIFORM = Pattern.compile(
            "(?m)^[\\t ]*uniform\\s+(?:(?:lowp|mediump|highp)\\s+)?float\\s+alphaTestRef\\s*;[\\t ]*(?://[^\\r\\n]*)?(?:\\r?\\n|$)");
    private static final Pattern SAMPLER_DECLARATION = Pattern.compile(
            "(?m)^[\\t ]*uniform\\s+(?:(?:lowp|mediump|highp)\\s+)?(sampler\\w+)\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*;[\\t ]*(?://[^\\r\\n]*)?(?:\\r?\\n|$)");
    private static final Pattern UNSUPPORTED_UNIFORM_DECLARATION = Pattern.compile("(?m)^[\\t ]*uniform\\b");
    private static final Pattern VARYING_DECLARATION = Pattern.compile(
            "(?m)^[\\t ]*(?:layout\\s*\\(\\s*location\\s*=\\s*(\\d+)\\s*\\)\\s*)?(?:varying|in)\\s+(?:(?:flat|smooth|noperspective)\\s+)?([A-Za-z_][A-Za-z0-9_]*)\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*;[\\t ]*(?://[^\\r\\n]*)?(?:\\r?\\n|$)");
    private static final Pattern UNSUPPORTED_INPUT_DECLARATION = Pattern.compile(
            "(?m)^[\\t ]*(?:layout\\s*\\([^)]*\\)\\s*)?(?:varying|in)\\b[^\\r\\n]*;?");
    private static final Pattern OUTPUT_DECLARATION = Pattern.compile(
            "(?m)^[\\t ]*(?:layout\\s*\\(\\s*location\\s*=\\s*(\\d+)\\s*\\)\\s*)?out\\s+vec4\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*;[\\t ]*(?://[^\\r\\n]*)?(?:\\r?\\n|$)");
    private static final Pattern UNSUPPORTED_OUTPUT_DECLARATION = Pattern.compile(
            "(?m)^[\\t ]*(?:layout\\s*\\([^)]*\\)\\s*)?out\\s+(?!vec4\\s+[A-Za-z_][A-Za-z0-9_]*\\s*;)[^\\r\\n]*;?");
    private static final Pattern GL_FRAG_DATA_INDEX = Pattern.compile("\\bgl_FragData\\s*\\[\\s*(\\d+)\\s*\\]");
    private static final Pattern RENDERTARGETS_DIRECTIVE = Pattern.compile("(?is)/\\*\\s*RENDERTARGETS\\s*:\\s*([0-9,\\s]+)\\s*\\*/");
    private static final Pattern DRAWBUFFERS_DIRECTIVE = Pattern.compile("(?is)/\\*\\s*DRAWBUFFERS\\s*:\\s*([0-9]+)\\s*\\*/");
    private static final Pattern COMMENTS = Pattern.compile("(?s)/\\*.*?\\*/|//[^\\r\\n]*");
    private static final Pattern UNSUPPORTED_TEXTURE_FUNCTION = Pattern.compile(
            "\\btexture(?:Gather|Offset|Proj|Size|QueryLod)\\w*\\s*\\(");
    private static final Map<String, String> INPUT_TYPES = Map.ofEntries(
            Map.entry("texcoord", "vec2"),
            Map.entry("texCoord", "vec2"),
            Map.entry("texCoord0", "vec2"),
            Map.entry("color", "vec4"),
            Map.entry("glcolor", "vec4"),
            Map.entry("glColor", "vec4"),
            Map.entry("gl_Color", "vec4"),
            Map.entry("vertexColor", "vec4"));

    private BeaconBeamShaderAdapter() {
    }

    public static String adaptFragment(String source) throws IOException {
        if (source == null || source.isBlank()) {
            throw new IOException("gbuffers_beaconbeam fragment shader is empty.");
        }
        if (source.length() > 1024 * 1024) {
            throw new IOException("gbuffers_beaconbeam fragment shader exceeds the 1 MiB size limit.");
        }

        String adapted = VERSION_DIRECTIVE.matcher(source.replace("\uFEFF", "")).replaceAll("");
        List<String> extensions = new ArrayList<>();
        Matcher extensionMatcher = EXTENSION_DIRECTIVE.matcher(adapted);
        while (extensionMatcher.find()) {
            extensions.add(extensionMatcher.group().strip());
        }
        adapted = PRECISION_DECLARATION.matcher(extensionMatcher.replaceAll("")).replaceAll("");
        adapted = ALPHA_TEST_UNIFORM.matcher(adapted).replaceAll("");

        Matcher samplerMatcher = SAMPLER_DECLARATION.matcher(adapted);
        StringBuffer withoutSamplers = new StringBuffer();
        while (samplerMatcher.find()) {
            String sampler = samplerMatcher.group(2);
            if (!sampler.equals("texture") && !sampler.equals("gtexture") && !sampler.equals("Sampler0")) {
                throw new IOException("gbuffers_beaconbeam uses unsupported sampler '" + sampler + "'.");
            }
            if (!samplerMatcher.group(1).equals("sampler2D")) {
                throw new IOException("gbuffers_beaconbeam sampler '" + sampler + "' must use sampler2D.");
            }
            samplerMatcher.appendReplacement(withoutSamplers, "");
        }
        samplerMatcher.appendTail(withoutSamplers);
        adapted = withoutSamplers.toString();
        ShaderPackUniformAdapter.AdaptedShader shaderPackUniforms =
                ShaderPackUniformAdapter.extract(adapted, "gbuffers_beaconbeam");
        adapted = shaderPackUniforms.source();
        if (UNSUPPORTED_UNIFORM_DECLARATION.matcher(adapted).find()) {
            throw new IOException("gbuffers_beaconbeam has an unsupported uniform declaration.");
        }

        Matcher varyingMatcher = VARYING_DECLARATION.matcher(adapted);
        StringBuffer withoutVaryings = new StringBuffer();
        while (varyingMatcher.find()) {
            String type = varyingMatcher.group(2);
            String name = varyingMatcher.group(3);
            String expectedType = INPUT_TYPES.get(name);
            if (expectedType == null) {
                throw new IOException("gbuffers_beaconbeam input '" + name + "' is not provided by Veyra's beacon bridge yet.");
            }
            if (!expectedType.equals(type)) {
                throw new IOException("gbuffers_beaconbeam input '" + name + "' must use " + expectedType + ".");
            }
            varyingMatcher.appendReplacement(withoutVaryings, "");
        }
        varyingMatcher.appendTail(withoutVaryings);
        adapted = withoutVaryings.toString();
        if (UNSUPPORTED_INPUT_DECLARATION.matcher(adapted).find()) {
            throw new IOException("gbuffers_beaconbeam declares inputs that are not provided by Veyra's beacon bridge yet.");
        }

        String outputName = "veyraBeaconFragColor";
        Matcher outputMatcher = OUTPUT_DECLARATION.matcher(adapted);
        StringBuffer withoutOutputs = new StringBuffer();
        int outputCount = 0;
        while (outputMatcher.find()) {
            String location = outputMatcher.group(1);
            if (location != null && !location.equals("0")) {
                throw new IOException("gbuffers_beaconbeam can write only color attachment 0.");
            }
            outputName = outputMatcher.group(2);
            outputCount++;
            outputMatcher.appendReplacement(withoutOutputs, "");
        }
        outputMatcher.appendTail(withoutOutputs);
        if (outputCount > 1) {
            throw new IOException("gbuffers_beaconbeam can declare only one fragment output.");
        }
        adapted = withoutOutputs.toString();
        if (UNSUPPORTED_OUTPUT_DECLARATION.matcher(adapted).find()) {
            throw new IOException("gbuffers_beaconbeam supports one vec4 fragment output.");
        }

        String uncommented = COMMENTS.matcher(adapted).replaceAll("");
        if (UNSUPPORTED_TEXTURE_FUNCTION.matcher(uncommented).find()) {
            throw new IOException("gbuffers_beaconbeam uses a texture sampling function that Veyra's beacon bridge does not support yet.");
        }
        validateTargetComments(source);
        Matcher fragDataMatcher = GL_FRAG_DATA_INDEX.matcher(uncommented);
        while (fragDataMatcher.find()) {
            if (!fragDataMatcher.group(1).equals("0")) {
                throw new IOException("gbuffers_beaconbeam can write only color attachment 0.");
            }
        }

        adapted = adapted
                .replaceAll("\\bgl_FragData\\s*\\[\\s*0\\s*\\]", outputName)
                .replaceAll("\\btexture2DLod(?:EXT)?\\s*\\(", "veyraBeaconSampleLod(")
                .replaceAll("\\btextureLod\\s*\\(", "veyraBeaconSampleLod(")
                .replaceAll("\\btexture2DGrad(?:ARB)?\\s*\\(", "veyraBeaconSampleGrad(")
                .replaceAll("\\btextureGrad\\s*\\(", "veyraBeaconSampleGrad(")
                .replaceAll("\\btexture2D\\s*\\(", "veyraBeaconSample(")
                .replaceAll("\\btexture\\s*\\(", "veyraBeaconSample(")
                .replaceAll("\\bgl_FragColor\\b", outputName)
                .replaceAll("\\btexcoord\\b|\\btexCoord\\b", "texCoord0")
                .replaceAll("\\bcolor\\b|\\bglcolor\\b|\\bglColor\\b|\\bgl_Color\\b", "vertexColor");

        String bridge = """
                in vec4 vertexColor;
                in vec2 texCoord0;
                out vec4 veyraBeaconFragColor;
                uniform sampler2D Sampler0;
                vec4 veyraBeaconSample(sampler2D sourceSampler, vec2 sourceUv) {
                    return texture(sourceSampler, sourceUv);
                }
                vec4 veyraBeaconSampleLod(sampler2D sourceSampler, vec2 sourceUv, float lod) {
                    return textureLod(sourceSampler, sourceUv, lod);
                }
                vec4 veyraBeaconSampleGrad(sampler2D sourceSampler, vec2 sourceUv, vec2 dx, vec2 dy) {
                    return textureGrad(sourceSampler, sourceUv, dx, dy);
                }
                #define texture Sampler0
                #define gtexture Sampler0
                #ifndef ALPHA_CUTOUT
                #define ALPHA_CUTOUT 0.1
                #endif
                #define alphaTestRef ALPHA_CUTOUT
                """.replace("veyraBeaconFragColor;", outputName + ";");
        StringBuilder result = new StringBuilder("#version 460\n");
        for (String extension : extensions) {
            result.append(extension).append('\n');
        }
        result.append(bridge);
        result.append(shaderPackUniforms.declarations());
        result.append(shaderPackUniforms.macros());
        return result.append(adapted).toString();
    }

    private static void validateTargetComments(String source) throws IOException {
        Matcher renderTargets = RENDERTARGETS_DIRECTIVE.matcher(source);
        while (renderTargets.find()) {
            String[] targets = renderTargets.group(1).split(",");
            if (targets.length != 1 || !targets[0].trim().equals("0")) {
                throw new IOException("gbuffers_beaconbeam can write only color attachment 0.");
            }
        }
        Matcher drawBuffers = DRAWBUFFERS_DIRECTIVE.matcher(source);
        while (drawBuffers.find()) {
            if (!drawBuffers.group(1).equals("0")) {
                throw new IOException("gbuffers_beaconbeam can write only color attachment 0.");
            }
        }
    }
}
