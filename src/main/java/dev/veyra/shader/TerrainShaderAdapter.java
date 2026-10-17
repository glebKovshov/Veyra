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
    private static final Pattern SAMPLER_DECLARATION = Pattern.compile(
            "(?m)^[\\t ]*uniform\\s+(?:(?:lowp|mediump|highp)\\s+)?(sampler\\w+)\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*;[\\t ]*(?://[^\\r\\n]*)?(?:\\r?\\n|$)");
    private static final Pattern UNSUPPORTED_UNIFORM_DECLARATION = Pattern.compile("(?m)^\\s*uniform\\b");
    private static final Pattern VARYING_DECLARATION = Pattern.compile(
            "(?m)^[\\t ]*(?:layout\\s*\\(\\s*location\\s*=\\s*(\\d+)\\s*\\)\\s*)?(?:varying|in)\\s+(?:(?:flat|smooth|noperspective)\\s+)?([A-Za-z_][A-Za-z0-9_]*)\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*;[\\t ]*(?://[^\\r\\n]*)?(?:\\r?\\n|$)");
    private static final Pattern OUTPUT_DECLARATION = Pattern.compile(
            "(?m)^[\\t ]*(?:layout\\s*\\(\\s*location\\s*=\\s*(\\d+)\\s*\\)\\s*)?out\\s+vec4\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*;[\\t ]*(?://[^\\r\\n]*)?(?:\\r?\\n|$)");
    private static final Pattern UNSUPPORTED_OUTPUT_DECLARATION = Pattern.compile(
            "(?m)^[\\t ]*(?:layout\\s*\\([^)]*\\)\\s*)?out\\s+(?!vec4\\s+[A-Za-z_][A-Za-z0-9_]*\\s*;)[^\\r\\n]*;");
    private static final Pattern UNSUPPORTED_GBUFFER_INPUT = Pattern.compile(
            "\\b(?:gl_FragData|mc_Entity|mc_midTexCoord|midTexCoord|normal|viewPos|viewNormal|tangent|at_tangent)\\b");
    private static final Pattern UNSUPPORTED_TEXTURE_FUNCTION = Pattern.compile(
            "\\btexture(?:Gather|Offset|Proj|Size|QueryLod)\\w*\\s*\\(");
    private static final Pattern COMMENTS = Pattern.compile("(?s)/\\*.*?\\*/|//[^\\r\\n]*");
    private static final Map<String, Varying> SUPPORTED_VARYINGS = Map.ofEntries(
            Map.entry("texcoord", new Varying("vec2", 1)),
            Map.entry("texCoord", new Varying("vec2", 1)),
            Map.entry("texCoord0", new Varying("vec2", 1)),
            Map.entry("lmcoord", new Varying("vec2", 5)),
            Map.entry("lightMapCoord", new Varying("vec2", 5)),
            Map.entry("glcolor", new Varying("vec4", 0)),
            Map.entry("glColor", new Varying("vec4", 0)),
            Map.entry("gl_Color", new Varying("vec4", 0)));

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
            };
            layout(binding = 3) uniform sampler2D Sampler0;
            layout(binding = 4) uniform sampler2D LightTexture;
            layout(location = 0) in vec4 veyraVertexColor;
            layout(location = 1) in vec2 veyraTexCoord;
            layout(location = 5) in vec2 veyraLightmapCoord;
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
            #define texture Sampler0
            #define gtexture Sampler0
            #define lightmap LightTexture
            #define alphaTestRef AlphaCutout
            #define texcoord veyraTexCoord
            #define texCoord veyraTexCoord
            #define texCoord0 veyraTexCoord
            #define lmcoord veyraLightmapCoord
            #define lightMapCoord veyraLightmapCoord
            #define glcolor veyraVertexColor
            #define glColor veyraVertexColor
            #define gl_Color veyraVertexColor
            #define gl_FragColor veyraFragColor
            """;

    private TerrainShaderAdapter() {
    }

    public static String adaptFragment(String source) throws IOException {
        String adapted = VERSION_DIRECTIVE.matcher(source).replaceAll("");
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
            if (!sampler.equals("texture") && !sampler.equals("gtexture") && !sampler.equals("lightmap")) {
                throw new IOException("gbuffers_terrain uses unsupported sampler '" + sampler + "'.");
            }
            if (!samplerMatcher.group(1).equals("sampler2D")) {
                throw new IOException("gbuffers_terrain sampler '" + sampler + "' must use sampler2D.");
            }
            samplerMatcher.appendReplacement(withoutSamplers, "");
        }
        samplerMatcher.appendTail(withoutSamplers);
        adapted = withoutSamplers.toString();
        if (UNSUPPORTED_UNIFORM_DECLARATION.matcher(adapted).find()) {
            throw new IOException("gbuffers_terrain custom uniforms are not supported yet; alphaTestRef and the built-in samplers are available.");
        }

        Matcher varyingMatcher = VARYING_DECLARATION.matcher(adapted);
        StringBuffer withoutVaryings = new StringBuffer();
        while (varyingMatcher.find()) {
            String type = varyingMatcher.group(2);
            String name = varyingMatcher.group(3);
            Varying varying = SUPPORTED_VARYINGS.get(name);
            if (varying == null) {
                throw new IOException("gbuffers_terrain input '" + name + "' is not provided by Veyra's terrain bridge yet.");
            }
            if (!varying.type().equals(type)) {
                throw new IOException("gbuffers_terrain input '" + name + "' must use " + varying.type() + ".");
            }
            String location = varyingMatcher.group(1);
            if (location != null && Integer.parseInt(location) != varying.location()) {
                throw new IOException("gbuffers_terrain input '" + name + "' uses an unsupported location.");
            }
            varyingMatcher.appendReplacement(withoutVaryings, "");
        }
        varyingMatcher.appendTail(withoutVaryings);
        adapted = withoutVaryings.toString();

        String outputName = "veyraFragColor";
        Matcher outputMatcher = OUTPUT_DECLARATION.matcher(adapted);
        StringBuffer withoutOutputs = new StringBuffer();
        int outputCount = 0;
        while (outputMatcher.find()) {
            String location = outputMatcher.group(1);
            if (location != null && !location.equals("0")) {
                throw new IOException("gbuffers_terrain can write only color attachment 0.");
            }
            outputName = outputMatcher.group(2);
            outputCount++;
            outputMatcher.appendReplacement(withoutOutputs, "");
        }
        outputMatcher.appendTail(withoutOutputs);
        if (outputCount > 1) {
            throw new IOException("gbuffers_terrain can declare only one fragment output.");
        }
        adapted = withoutOutputs.toString();
        if (UNSUPPORTED_OUTPUT_DECLARATION.matcher(adapted).find()) {
            throw new IOException("gbuffers_terrain supports one vec4 fragment output.");
        }
        String uncommented = COMMENTS.matcher(adapted).replaceAll("");
        if (UNSUPPORTED_GBUFFER_INPUT.matcher(uncommented).find()) {
            throw new IOException("gbuffers_terrain uses normals, entity data, or multiple outputs that are not provided by Veyra's terrain bridge yet.");
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
        result.append(FRAGMENT_BRIDGE
                .replace("veyraFragColor;", outputName + ";")
                .replace("#define gl_FragColor veyraFragColor", "#define gl_FragColor " + outputName));
        return result.append(adapted).toString();
    }

    public static String adaptVertex(String source) throws IOException {
        String outputMarker = "layout (location = 4) out flat float fadeFactor;";
        if (!source.contains(outputMarker)) {
            throw new IOException("VulkanMod's built-in terrain vertex shader changed; Veyra cannot bridge gbuffers_terrain inputs.");
        }
        String adapted = source.replace(outputMarker,
                outputMarker + "\nlayout (location = 5) out vec2 veyraLightmapCoord;");
        String lightmapMarker = "vertexColor = Color * sample_lightmap2(Sampler2, Position.a);";
        if (!adapted.contains(lightmapMarker)) {
            throw new IOException("VulkanMod's terrain lightmap encoding changed; Veyra cannot bridge gbuffers_terrain inputs.");
        }
        return adapted.replace(lightmapMarker, """
                vertexColor = Color;
                const ivec2 lightCoord = ivec2(
                    bitfieldExtract(uint(Position.a), 4, 4),
                    bitfieldExtract(uint(Position.a), 12, 4));
                veyraLightmapCoord = (vec2(lightCoord) + vec2(0.5)) / 16.0;
                """);
    }

    private record Varying(String type, int location) {
    }
}
