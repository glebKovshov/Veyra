package dev.veyra.shader;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Adapts the color-only cloud inputs exposed by VulkanMod to a small Iris fragment interface. */
public final class CloudShaderAdapter {
    private static final Pattern VERSION_DIRECTIVE = Pattern.compile("(?m)^[\\t ]*#version[^\\r\\n]*(?:\\r?\\n|$)");
    private static final Pattern EXTENSION_DIRECTIVE = Pattern.compile("(?m)^[\\t ]*#extension[^\\r\\n]*(?:\\r?\\n|$)");
    private static final Pattern PRECISION_DECLARATION = Pattern.compile("(?m)^[\\t ]*precision\\s+(?:lowp|mediump|highp)\\s+\\w+\\s*;[\\t ]*(?:\\r?\\n|$)");
    private static final Pattern VARYING_DECLARATION = Pattern.compile(
            "(?m)^[\\t ]*(?:layout\\s*\\(\\s*location\\s*=\\s*(\\d+)\\s*\\)\\s*)?(?:varying|in)\\s+(?:(?:flat|smooth|noperspective)\\s+)?([A-Za-z_][A-Za-z0-9_]*)\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*;[\\t ]*(?://[^\\r\\n]*)?(?:\\r?\\n|$)");
    private static final Pattern UNSUPPORTED_INPUT_DECLARATION = Pattern.compile(
            "(?m)^[\\t ]*(?:layout\\s*\\([^)]*\\)\\s*)?(?:varying|in)\\b[^\\r\\n]*;?");
    private static final Pattern OUTPUT_DECLARATION = Pattern.compile(
            "(?m)^[\\t ]*(?:layout\\s*\\(\\s*location\\s*=\\s*(\\d+)\\s*\\)\\s*)?out\\s+vec4\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*;[\\t ]*(?://[^\\r\\n]*)?(?:\\r?\\n|$)");
    private static final Pattern UNSUPPORTED_OUTPUT_DECLARATION = Pattern.compile(
            "(?m)^[\\t ]*(?:layout\\s*\\([^)]*\\)\\s*)?out\\s+(?!vec4\\s+[A-Za-z_][A-Za-z0-9_]*\\s*;)[^\\r\\n]*;");
    private static final Pattern UNSUPPORTED_UNIFORM_DECLARATION = Pattern.compile("(?m)^\\s*uniform\\b");
    private static final Pattern GL_FRAG_DATA_INDEX = Pattern.compile("\\bgl_FragData\\s*\\[\\s*(\\d+)\\s*\\]");
    private static final Pattern UNSUPPORTED_CLOUD_INPUT = Pattern.compile(
            "\\b(?:coord0|texcoord\\w*|lmcoord|lightmap|normal|mc_Entity|gl_Fog\\w*)\\b");
    private static final Pattern UNSUPPORTED_TEXTURE = Pattern.compile(
            "\\b(?:texture|texture2D|textureLod|texelFetch)\\s*\\(");
    private static final Pattern COMMENTS = Pattern.compile("(?s)/\\*.*?\\*/|//[^\\r\\n]*");
    private static final Map<String, Varying> SUPPORTED_VARYINGS = Map.ofEntries(
            Map.entry("color", new Varying("vec4", 0)),
            Map.entry("glcolor", new Varying("vec4", 0)),
            Map.entry("glColor", new Varying("vec4", 0)),
            Map.entry("gl_Color", new Varying("vec4", 0)),
            Map.entry("vertexDistance", new Varying("float", 1)));

    private static final String FRAGMENT_BRIDGE = """
            layout(location = 0) in vec4 veyraCloudColor;
            layout(location = 1) in float veyraCloudDistance;
            layout(location = 0) out vec4 veyraCloudFragColor;
            #define color veyraCloudColor
            #define glcolor veyraCloudColor
            #define glColor veyraCloudColor
            #define gl_Color veyraCloudColor
            #define vertexDistance veyraCloudDistance
            #define gl_FragColor veyraCloudFragColor
            """;

    private CloudShaderAdapter() {
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

        String uncommented = COMMENTS.matcher(adapted).replaceAll("");
        if (UNSUPPORTED_CLOUD_INPUT.matcher(uncommented).find()) {
            throw new IOException("gbuffers_clouds uses texture coordinates, normals, or fog inputs that VulkanMod does not provide for clouds.");
        }
        if (UNSUPPORTED_TEXTURE.matcher(uncommented).find()) {
            throw new IOException("gbuffers_clouds cannot sample textures because VulkanMod builds clouds from colored geometry.");
        }
        if (UNSUPPORTED_UNIFORM_DECLARATION.matcher(adapted).find()) {
            throw new IOException("gbuffers_clouds custom uniforms are not supported yet; use the provided cloud color input.");
        }

        Matcher varyingMatcher = VARYING_DECLARATION.matcher(adapted);
        StringBuffer withoutVaryings = new StringBuffer();
        while (varyingMatcher.find()) {
            String type = varyingMatcher.group(2);
            String name = varyingMatcher.group(3);
            Varying varying = SUPPORTED_VARYINGS.get(name);
            if (varying == null) {
                throw new IOException("gbuffers_clouds input '" + name + "' is not provided by Veyra's cloud bridge yet.");
            }
            if (!varying.type().equals(type)) {
                throw new IOException("gbuffers_clouds input '" + name + "' must use " + varying.type() + ".");
            }
            String location = varyingMatcher.group(1);
            if (location != null && Integer.parseInt(location) != varying.location()) {
                throw new IOException("gbuffers_clouds input '" + name + "' uses an unsupported location.");
            }
            varyingMatcher.appendReplacement(withoutVaryings, "");
        }
        varyingMatcher.appendTail(withoutVaryings);
        adapted = withoutVaryings.toString();
        if (UNSUPPORTED_INPUT_DECLARATION.matcher(adapted).find()) {
            throw new IOException("gbuffers_clouds declares inputs that are not provided by Veyra's cloud bridge yet.");
        }

        String outputName = "veyraCloudFragColor";
        Matcher outputMatcher = OUTPUT_DECLARATION.matcher(adapted);
        StringBuffer withoutOutputs = new StringBuffer();
        int outputCount = 0;
        while (outputMatcher.find()) {
            String location = outputMatcher.group(1);
            if (location != null && !location.equals("0")) {
                throw new IOException("gbuffers_clouds can write only color attachment 0.");
            }
            outputName = outputMatcher.group(2);
            outputCount++;
            outputMatcher.appendReplacement(withoutOutputs, "");
        }
        outputMatcher.appendTail(withoutOutputs);
        if (outputCount > 1) {
            throw new IOException("gbuffers_clouds can declare only one fragment output.");
        }
        adapted = withoutOutputs.toString();
        if (UNSUPPORTED_OUTPUT_DECLARATION.matcher(adapted).find()) {
            throw new IOException("gbuffers_clouds supports one vec4 fragment output.");
        }
        uncommented = COMMENTS.matcher(adapted).replaceAll("");
        Matcher fragDataMatcher = GL_FRAG_DATA_INDEX.matcher(uncommented);
        while (fragDataMatcher.find()) {
            if (!fragDataMatcher.group(1).equals("0")) {
                throw new IOException("gbuffers_clouds can write only color attachment 0.");
            }
        }

        adapted = adapted.replaceAll("\\bgl_FragData\\s*\\[\\s*0\\s*\\]", outputName);
        StringBuilder result = new StringBuilder("#version 460\n");
        for (String extension : extensions) {
            result.append(extension).append('\n');
        }
        result.append(FRAGMENT_BRIDGE.replace("veyraCloudFragColor;", outputName + ";"));
        return result.append(adapted).toString();
    }

    private record Varying(String type, int location) {
    }
}
