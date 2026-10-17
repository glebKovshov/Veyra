package dev.veyra.shader;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Adapts Iris color and fog-distance inputs to Minecraft's position-color vertex stages. */
public final class ColorDistanceShaderAdapter {
    private static final Pattern VERSION_DIRECTIVE = Pattern.compile("(?m)^[\\t ]*#version[^\\r\\n]*(?:\\r?\\n|$)");
    private static final Pattern EXTENSION_DIRECTIVE = Pattern.compile("(?m)^[\\t ]*#extension[^\\r\\n]*(?:\\r?\\n|$)");
    private static final Pattern PRECISION_DECLARATION = Pattern.compile(
            "(?m)^[\\t ]*precision\\s+(?:lowp|mediump|highp)\\s+\\w+\\s*;[\\t ]*(?:\\r?\\n|$)");
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
    private static final Pattern UNSUPPORTED_TEXTURE = Pattern.compile(
            "\\b(?:texture|texture2D|textureLod|texelFetch)\\s*\\(");
    private static final Pattern UNSUPPORTED_UNIFORM = Pattern.compile("(?m)^\\s*uniform\\b");
    private static final Map<String, String> INPUT_TYPES = Map.ofEntries(
            Map.entry("color", "vec4"),
            Map.entry("glcolor", "vec4"),
            Map.entry("glColor", "vec4"),
            Map.entry("gl_Color", "vec4"),
            Map.entry("vertexColor", "vec4"),
            Map.entry("sphericalVertexDistance", "float"),
            Map.entry("cylindricalVertexDistance", "float"));
    private static final Map<String, String> DISTANCE_INPUT_LOCATIONS = Map.of(
            "sphericalVertexDistance", "0",
            "cylindricalVertexDistance", "1");

    private ColorDistanceShaderAdapter() {
    }

    public static String adaptFragment(String source, String programName, boolean flatVertexColor) throws IOException {
        return adaptFragment(source, programName, flatVertexColor, true, true);
    }

    public static String adaptFragment(String source, String programName, boolean flatVertexColor,
                                       boolean hasVertexColor, boolean hasVertexDistances) throws IOException {
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
        adapted = PRECISION_DECLARATION.matcher(extensionMatcher.replaceAll("")).replaceAll("");
        if (UNSUPPORTED_UNIFORM.matcher(adapted).find()) {
            throw new IOException(programName + " does not expose samplers or custom uniforms yet.");
        }

        Matcher varyingMatcher = VARYING_DECLARATION.matcher(adapted);
        StringBuffer withoutVaryings = new StringBuffer();
        while (varyingMatcher.find()) {
            String type = varyingMatcher.group(2);
            String name = varyingMatcher.group(3);
            String expectedType = INPUT_TYPES.get(name);
            if (expectedType == null) {
                throw new IOException(programName + " input '" + name + "' is not provided by Veyra's color-distance bridge yet.");
            }
            boolean distanceInput = DISTANCE_INPUT_LOCATIONS.containsKey(name);
            if (distanceInput && !hasVertexDistances) {
                throw new IOException(programName + " does not receive vertex distances from Minecraft's vertex shader.");
            }
            if (!distanceInput && !hasVertexColor) {
                throw new IOException(programName + " does not receive vertex color from Minecraft's vertex shader.");
            }
            if (!expectedType.equals(type)) {
                throw new IOException(programName + " input '" + name + "' must use " + expectedType + ".");
            }
            String location = varyingMatcher.group(1);
            String expectedLocation = distanceInput
                    ? DISTANCE_INPUT_LOCATIONS.get(name)
                    : hasVertexDistances ? "2" : "0";
            if (location != null && !location.equals(expectedLocation)) {
                throw new IOException(programName + " input '" + name + "' uses an unsupported location.");
            }
            varyingMatcher.appendReplacement(withoutVaryings, "");
        }
        varyingMatcher.appendTail(withoutVaryings);
        adapted = withoutVaryings.toString();
        if (UNSUPPORTED_INPUT_DECLARATION.matcher(adapted).find()) {
            throw new IOException(programName + " declares inputs that are not provided by Veyra's color-distance bridge yet.");
        }

        String outputName = "veyraColorDistanceFragColor";
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
        if (UNSUPPORTED_TEXTURE.matcher(uncommented).find()) {
            throw new IOException(programName + " cannot sample textures because its vertex bridge has no UVs.");
        }
        validateTargetComments(source, programName);
        Matcher fragDataMatcher = GL_FRAG_DATA_INDEX.matcher(uncommented);
        while (fragDataMatcher.find()) {
            if (!fragDataMatcher.group(1).equals("0")) {
                throw new IOException(programName + " can write only color attachment 0.");
            }
        }

        adapted = adapted
                .replaceAll("\\bgl_FragData\\s*\\[\\s*0\\s*\\]", outputName)
                .replaceAll("\\bgl_FragColor\\b", outputName)
                .replaceAll("\\bcolor\\b|\\bglcolor\\b|\\bglColor\\b|\\bgl_Color\\b", "vertexColor");

        String distanceInputs = hasVertexDistances ? """
                in float sphericalVertexDistance;
                in float cylindricalVertexDistance;
                """ : "";
        String colorInput = hasVertexColor
                ? (flatVertexColor ? "flat " : "") + "in vec4 vertexColor;\n"
                : "";
        String bridge = """
                %s%s
                out vec4 %s;
                """.formatted(distanceInputs, colorInput, outputName);
        StringBuilder result = new StringBuilder("#version 460\n");
        for (String extension : extensions) {
            result.append(extension).append('\n');
        }
        result.append(bridge);
        return result.append(adapted).toString();
    }

    private static void validateTargetComments(String source, String programName) throws IOException {
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
    }
}
