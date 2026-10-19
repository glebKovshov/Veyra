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
    private static final Pattern CLOUD_VERTEX_OUTPUT_DECLARATION = Pattern.compile(
            "(?m)^[\\t ]*(?:layout\\s*\\(\\s*location\\s*=\\s*(\\d+)\\s*\\)\\s*)?(?:varying|out)\\s+(?:(flat|smooth|noperspective)\\s+)?([A-Za-z_][A-Za-z0-9_]*)\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*;[\\t ]*(?://[^\\r\\n]*)?(?:\\r?\\n|$)");
    private static final Pattern UNSUPPORTED_CLOUD_VERTEX_OUTPUT = Pattern.compile(
            "(?m)^[\\t ]*(?:layout\\s*\\([^)]*\\)\\s*)?(?:varying|out)\\b[^\\r\\n]*;?");
    private static final Pattern CLOUD_VERTEX_INPUT_DECLARATION = Pattern.compile(
            "(?m)^[\\t ]*(?:layout\\s*\\([^)]*\\)\\s*)?(?:attribute|in)\\b[^\\r\\n]*;?");
    private static final Pattern UNSUPPORTED_CLOUD_VERTEX_INPUT = Pattern.compile(
            "\\b(?:coord0|texcoord\\w*|lmcoord|lightmap|normal|mc_Entity|gl_Normal|gl_NormalMatrix|gl_MultiTexCoord\\w*|gl_TextureMatrix|gl_ModelViewMatrix|gl_ProjectionMatrix|gl_Fog\\w*|gl_VertexID|gl_InstanceID)\\b");
    private static final Pattern VERTEX_MAIN_FUNCTION = Pattern.compile(
            "\\bvoid\\s+main\\s*\\(\\s*\\)\\s*\\{");
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
        return adapt(source).source();
    }

    /**
     * Adapts the limited cloud vertex inputs available from VulkanMod's position/color mesh.
     * The pack can transform positions and produce the cloud color/distance varyings, but it
     * cannot invent texture coordinates, normals, or other vertex attributes.
     */
    public static String adaptVertex(String vulkanModSource, String shaderPackSource) throws IOException {
        if (vulkanModSource == null || shaderPackSource == null || shaderPackSource.isBlank()) {
            throw new IOException("gbuffers_clouds vertex shader is empty.");
        }
        if (shaderPackSource.length() > 1024 * 1024) {
            throw new IOException("gbuffers_clouds vertex shader exceeds the 1 MiB size limit.");
        }

        String source = shaderPackSource.replace("\uFEFF", "");
        source = VERSION_DIRECTIVE.matcher(source).replaceAll("");
        List<String> extensions = new ArrayList<>();
        Matcher extensionMatcher = EXTENSION_DIRECTIVE.matcher(source);
        while (extensionMatcher.find()) {
            extensions.add(extensionMatcher.group().strip());
        }
        source = extensionMatcher.replaceAll("");
        source = PRECISION_DECLARATION.matcher(source).replaceAll("");
        String uncommented = maskComments(source);

        if (UNSUPPORTED_CLOUD_VERTEX_INPUT.matcher(uncommented).find()) {
            throw new IOException("gbuffers_clouds vertex shader uses attributes or transforms that VulkanMod does not provide for clouds.");
        }
        if (UNSUPPORTED_TEXTURE.matcher(uncommented).find()) {
            throw new IOException("gbuffers_clouds vertex shader cannot sample textures because VulkanMod's cloud mesh has no texture coordinates.");
        }
        if (UNSUPPORTED_UNIFORM_DECLARATION.matcher(uncommented).find()) {
            throw new IOException("gbuffers_clouds vertex shader custom uniforms are not supported yet.");
        }
        if (!Pattern.compile("\\bgl_Position(?:\\s*\\.\\s*[xyzwrgba]{1,4})?\\s*=").matcher(uncommented).find()
                && !Pattern.compile("\\bftransform\\s*\\(").matcher(uncommented).find()) {
            throw new IOException("gbuffers_clouds vertex shader must assign gl_Position or call ftransform().");
        }

        Matcher outputMatcher = CLOUD_VERTEX_OUTPUT_DECLARATION.matcher(uncommented);
        StringBuilder withoutOutputs = new StringBuilder(source.length());
        int copiedThrough = 0;
        while (outputMatcher.find()) {
            String type = outputMatcher.group(3);
            String name = outputMatcher.group(4);
            int expectedLocation;
            if (name.equals("color") || name.equals("glcolor") || name.equals("glColor")) {
                expectedLocation = 0;
                if (!type.equals("vec4")) {
                    throw new IOException("gbuffers_clouds vertex output '" + name + "' must use vec4.");
                }
            } else if (name.equals("vertexDistance")) {
                expectedLocation = 1;
                if (!type.equals("float")) {
                    throw new IOException("gbuffers_clouds vertex output 'vertexDistance' must use float.");
                }
            } else {
                throw new IOException("gbuffers_clouds vertex output '" + name + "' is not provided by Veyra's cloud bridge yet.");
            }
            String location = outputMatcher.group(1);
            if (location != null && Integer.parseInt(location) != expectedLocation) {
                throw new IOException("gbuffers_clouds vertex output '" + name + "' uses an unsupported location.");
            }
            String interpolation = outputMatcher.group(2);
            if (interpolation != null && !interpolation.equals("smooth")) {
                throw new IOException("gbuffers_clouds vertex outputs must use smooth interpolation.");
            }
            withoutOutputs.append(source, copiedThrough, outputMatcher.start());
            copiedThrough = outputMatcher.end();
        }
        withoutOutputs.append(source, copiedThrough, source.length());
        source = withoutOutputs.toString();
        uncommented = maskComments(source);
        if (UNSUPPORTED_CLOUD_VERTEX_OUTPUT.matcher(uncommented).find()) {
            throw new IOException("gbuffers_clouds vertex shader declares outputs that are not provided by Veyra's cloud bridge yet.");
        }
        if (CLOUD_VERTEX_INPUT_DECLARATION.matcher(uncommented).find()) {
            throw new IOException("gbuffers_clouds vertex shader declares inputs that VulkanMod's cloud vertex format does not provide.");
        }

        MainFunction packMain = findMain(source, "gbuffers_clouds");
        String packBody = source.substring(packMain.bodyStart() + 1, packMain.bodyEnd());
        String packGlobals = source.substring(0, packMain.start()) + source.substring(packMain.bodyEnd() + 1);

        String baseSource = vulkanModSource;
        Matcher baseVersion = VERSION_DIRECTIVE.matcher(baseSource);
        if (!baseVersion.find()) {
            throw new IOException("VulkanMod's cloud vertex shader has no #version directive.");
        }
        StringBuilder extensionsPreamble = new StringBuilder();
        for (String extension : extensions) {
            extensionsPreamble.append(extension).append('\n');
        }
        String withExtensions = baseSource.substring(0, baseVersion.end()) + extensionsPreamble
                + baseSource.substring(baseVersion.end());
        MainFunction originalBaseMain = findMain(withExtensions, "VulkanMod cloud");
        String bridge = """
                #define gl_Vertex vec4(Position, 1.0)
                #define gl_Color (Color * ColorModulator)
                #define gl_ModelViewProjectionMatrix MVP
                #define ftransform() (MVP * vec4(Position, 1.0))
                #define color vertexColor
                #define glcolor vertexColor
                #define glColor vertexColor
                """;
        String withPackGlobals = withExtensions.substring(0, originalBaseMain.start()) + bridge + packGlobals
                + withExtensions.substring(originalBaseMain.start());
        MainFunction baseMain = findMain(withPackGlobals, "VulkanMod cloud");
        String defaultAssignments = "\n    vertexColor = Color * ColorModulator;"
                + "\n    vertexDistance = length(Position + ModelOffset);\n";
        return withPackGlobals.substring(0, baseMain.bodyStart() + 1) + defaultAssignments + packBody
                + withPackGlobals.substring(baseMain.bodyEnd());
    }

    private static MainFunction findMain(String source, String programName) throws IOException {
        String uncommented = maskComments(source);
        Matcher main = VERTEX_MAIN_FUNCTION.matcher(uncommented);
        if (!main.find()) {
            throw new IOException(programName + " vertex shader has no main function.");
        }
        int openBrace = main.end() - 1;
        int depth = 0;
        for (int index = openBrace; index < uncommented.length(); index++) {
            char character = uncommented.charAt(index);
            if (character == '{') {
                depth++;
            } else if (character == '}' && --depth == 0) {
                return new MainFunction(main.start(), openBrace, index);
            }
        }
        throw new IOException(programName + " vertex shader main function has unmatched braces.");
    }

    private static String maskComments(String source) {
        StringBuilder masked = new StringBuilder(source);
        Matcher comments = COMMENTS.matcher(source);
        while (comments.find()) {
            for (int index = comments.start(); index < comments.end(); index++) {
                char character = masked.charAt(index);
                if (character != '\n' && character != '\r') {
                    masked.setCharAt(index, ' ');
                }
            }
        }
        return masked.toString();
    }

    public static AdaptedFragment adapt(String source) throws IOException {
        String adapted = VERSION_DIRECTIVE.matcher(source).replaceAll("");
        List<String> extensions = new ArrayList<>();
        Matcher extensionMatcher = EXTENSION_DIRECTIVE.matcher(adapted);
        while (extensionMatcher.find()) {
            extensions.add(extensionMatcher.group().strip());
        }
        adapted = extensionMatcher.replaceAll("");
        adapted = PRECISION_DECLARATION.matcher(adapted).replaceAll("");
        IrisFogUniformAdapter.AdaptedShader fogUniforms =
                IrisFogUniformAdapter.extract(adapted, "gbuffers_clouds", true);
        adapted = fogUniforms.source();
        ShaderPackUniformAdapter.AdaptedShader shaderPackUniforms =
                ShaderPackUniformAdapter.extract(adapted, "gbuffers_clouds");
        adapted = shaderPackUniforms.source();

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
        Map<String, ShaderPackUniformAdapter.UniformField> uniformFields = new java.util.LinkedHashMap<>();
        for (ShaderPackUniformAdapter.UniformField field : fogUniforms.fields()) {
            uniformFields.put(field.name(), field);
        }
        for (ShaderPackUniformAdapter.UniformField field : shaderPackUniforms.fields()) {
            ShaderPackUniformAdapter.UniformField previous = uniformFields.putIfAbsent(field.name(), field);
            if (previous != null && !previous.equals(field)) {
                throw new IOException("gbuffers_clouds declares incompatible uniform buffer fields named '"
                        + field.name() + "'.");
            }
        }
        if (!uniformFields.isEmpty()) {
            result.append("layout(binding = 1) uniform VeyraCloudUniformBuffer {\n")
                    .append("    vec4 ColorModulator;\n")
                    .append("    float FogCloudsEnd;\n");
            for (ShaderPackUniformAdapter.UniformField field : uniformFields.values()) {
                result.append("    ").append(field.type()).append(' ').append(field.name()).append(";\n");
            }
            result.append("};\n");
        }
        result.append(fogUniforms.bridge());
        result.append(shaderPackUniforms.macros());
        return new AdaptedFragment(result.append(adapted).toString(), List.copyOf(uniformFields.values()));
    }

    public record AdaptedFragment(String source, List<ShaderPackUniformAdapter.UniformField> uniformFields) {
    }

    private record Varying(String type, int location) {
    }

    private record MainFunction(int start, int bodyStart, int bodyEnd) {
    }
}
