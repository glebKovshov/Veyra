package dev.veyra.shader;

import dev.veyra.vulkan.VeyraNoiseTexture;
import dev.veyra.vulkan.VeyraCloudTexture;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Adapts the supported scene, noise, cloud-texture, and varying inputs for Iris cloud programs. */
public final class CloudShaderAdapter {
    private static final Pattern VERSION_DIRECTIVE = Pattern.compile("(?m)^[\\t ]*#version[^\\r\\n]*(?:\\r?\\n|$)");
    private static final Pattern EXTENSION_DIRECTIVE = Pattern.compile("(?m)^[\\t ]*#extension[^\\r\\n]*(?:\\r?\\n|$)");
    private static final Pattern PRECISION_DECLARATION = Pattern.compile("(?m)^[\\t ]*precision\\s+(?:lowp|mediump|highp)\\s+\\w+\\s*;[\\t ]*(?:\\r?\\n|$)");
    private static final Pattern VARYING_DECLARATION = Pattern.compile(
            "(?m)^[\\t ]*(?:layout\\s*\\(\\s*location\\s*=\\s*(\\d+)\\s*\\)\\s*)?(?:varying|in)\\s+(?:(?:flat|smooth|noperspective)\\s+)?([A-Za-z_][A-Za-z0-9_]*)\\s+([A-Za-z_][A-Za-z0-9_]*(?:\\s*,\\s*[A-Za-z_][A-Za-z0-9_]*)*)\\s*;[\\t ]*(?://[^\\r\\n]*)?(?:\\r?\\n|$)");
    private static final Pattern UNSUPPORTED_INPUT_DECLARATION = Pattern.compile(
            "(?m)^[\\t ]*(?:layout\\s*\\([^)]*\\)\\s*)?(?:varying|in)\\b[^\\r\\n]*;?");
    private static final Pattern OUTPUT_DECLARATION = Pattern.compile(
            "(?m)^[\\t ]*(?:layout\\s*\\(\\s*location\\s*=\\s*(\\d+)\\s*\\)\\s*)?out\\s+vec4\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*;[\\t ]*(?://[^\\r\\n]*)?(?:\\r?\\n|$)");
    private static final Pattern UNSUPPORTED_OUTPUT_DECLARATION = Pattern.compile(
            "(?m)^[\\t ]*(?:layout\\s*\\([^)]*\\)\\s*)?out\\s+(?!vec4\\s+[A-Za-z_][A-Za-z0-9_]*\\s*;)[^\\r\\n]*;");
    private static final Pattern UNSUPPORTED_UNIFORM_DECLARATION = Pattern.compile("(?m)^[\\t ]*uniform\\b");
    private static final Pattern GL_FRAG_DATA_INDEX = Pattern.compile("\\bgl_FragData\\s*\\[\\s*(\\d+)\\s*\\]");
    private static final Pattern SAMPLER_DECLARATION = Pattern.compile(
            "(?m)^[\\t ]*(?:layout\\s*\\([^)]*\\)\\s*)?uniform\\s+(?:(?:lowp|mediump|highp)\\s+)?(sampler\\w+)\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*;[^\\r\\n]*(?:\\r?\\n|$)");
    private static final Pattern TEXTURE_SAMPLE = Pattern.compile(
            "\\b(texture[A-Za-z0-9_]*|texelFetch)\\s*\\(\\s*([A-Za-z_][A-Za-z0-9_]*)\\s*,");
    private static final Pattern UNSUPPORTED_TEXTURE = Pattern.compile(
            "\\b(?:texture|texture2D|textureLod|texelFetch)\\s*\\(");
    private static final Pattern LEGACY_TEXTURE_2D = Pattern.compile("\\btexture2D\\b");
    private static final Set<String> SCENE_COLOR_SAMPLERS = Set.of("colortex0", "gcolor", "Sampler0", "InSampler");
    private static final Set<String> SUPPORTED_TEXTURE_FUNCTIONS = Set.of(
            "texture", "texture2D", "textureLod", "texture2DLod", "textureSize", "texelFetch");
    private static final String CLOUD_SCENE_SAMPLER_NAME = "VeyraCloudSceneSampler";
    private static final String NOISE_SAMPLER = "noisetex";
    private static final int DEFAULT_FIRST_IMAGE_BINDING = 2;
    private static final Pattern UNSUPPORTED_CLOUD_INPUT = Pattern.compile(
            "(?i)\\b(?:coord0|lmcoord|lightmap|mc_Entity|gl_Fog\\w*)\\b");
    private static final Pattern CLOUD_VERTEX_OUTPUT_DECLARATION = Pattern.compile(
            "(?m)^[\\t ]*(?:layout\\s*\\(\\s*location\\s*=\\s*(\\d+)\\s*\\)\\s*)?(?:varying|out)\\s+(?:(flat|smooth|noperspective)\\s+)?([A-Za-z_][A-Za-z0-9_]*)\\s+([A-Za-z_][A-Za-z0-9_]*(?:\\s*,\\s*[A-Za-z_][A-Za-z0-9_]*)*)\\s*;[\\t ]*(?://[^\\r\\n]*)?(?:\\r?\\n|$)");
    private static final Pattern UNSUPPORTED_CLOUD_VERTEX_OUTPUT = Pattern.compile(
            "(?m)^[\\t ]*(?:layout\\s*\\([^)]*\\)\\s*)?(?:varying|out)\\b[^\\r\\n]*;?");
    private static final Pattern CLOUD_VERTEX_INPUT_DECLARATION = Pattern.compile(
            "(?m)^[\\t ]*(?:layout\\s*\\([^)]*\\)\\s*)?(?:attribute|in)\\b[^\\r\\n]*;?");
    private static final Pattern UNSUPPORTED_CLOUD_VERTEX_INPUT = Pattern.compile(
            "(?i)\\b(?:coord0|lmcoord|lightmap|mc_Entity|gl_MultiTexCoord(?!0)\\w*|gl_TextureMatrix(?!\\s*\\[\\s*0\\s*\\])|gl_ProjectionMatrix|gl_Fog\\w*|gl_VertexID|gl_InstanceID)\\b");
    private static final Pattern VERTEX_MAIN_FUNCTION = Pattern.compile(
            "\\bvoid\\s+main\\s*\\(\\s*\\)\\s*\\{");
    private static final Pattern COMMENTS = Pattern.compile("(?s)/\\*.*?\\*/|//[^\\r\\n]*");
    private static final Map<String, Varying> SUPPORTED_VARYINGS = Map.ofEntries(
            Map.entry("color", new Varying("vec4", 0)),
            Map.entry("glcolor", new Varying("vec4", 0)),
            Map.entry("glColor", new Varying("vec4", 0)),
            Map.entry("gl_Color", new Varying("vec4", 0)),
            Map.entry("vertexDistance", new Varying("float", 1)),
            Map.entry("texCoord", new Varying("vec2", 2)),
            Map.entry("texcoord", new Varying("vec2", 8)),
            Map.entry("normal", new Varying("vec3", 3)),
            Map.entry("sunVec", new Varying("vec3", 4)),
            Map.entry("upVec", new Varying("vec3", 5)),
            Map.entry("eastVec", new Varying("vec3", 6)),
            Map.entry("dist", new Varying("float", 7)));

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

    public static boolean isExtendedVarying(String name) {
        Varying varying = SUPPORTED_VARYINGS.get(name);
        return varying != null && varying.location() > 1;
    }

    public static String adaptFragment(String source) throws IOException {
        return adapt(source).source();
    }

    static AdaptedFragment adaptFragment(String source,
                                         List<ShaderPackUniformAdapter.UniformField> fields,
                                         Set<String> customImageSamplers) throws IOException {
        return adapt(source, fields, DEFAULT_FIRST_IMAGE_BINDING, customImageSamplers);
    }

    public static String adaptVertex(String vulkanModSource, String shaderPackSource) throws IOException {
        return adaptVertexWithMetadata(vulkanModSource, shaderPackSource).source();
    }

    /** Adapts the supported Iris cloud attributes and reports the vertex inputs it needs. */
    public static AdaptedVertex adaptVertexWithMetadata(String vulkanModSource, String shaderPackSource)
            throws IOException {
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
        source = removeUnreachableCloudFunctions(source);
        source = UnusedSamplerDeclarations.removeUnused(source);
        String uncommented = maskComments(source);

        Matcher unsupportedVertexInput = UNSUPPORTED_CLOUD_VERTEX_INPUT.matcher(uncommented);
        if (unsupportedVertexInput.find()) {
            throw new IOException("gbuffers_clouds vertex input or transform '" + unsupportedVertexInput.group()
                    + "' is not provided by VulkanMod's cloud mesh.");
        }
        if (UNSUPPORTED_TEXTURE.matcher(uncommented).find()) {
            throw new IOException("gbuffers_clouds vertex shader cannot sample textures because VulkanMod's cloud mesh has no texture coordinates.");
        }
        if (!Pattern.compile("\\bgl_Position(?:\\s*\\.\\s*[xyzwrgba]{1,4})?\\s*=").matcher(uncommented).find()
                && !Pattern.compile("\\bftransform\\s*\\(").matcher(uncommented).find()) {
            throw new IOException("gbuffers_clouds vertex shader must assign gl_Position or call ftransform().");
        }

        boolean usesTextureCoordinates = Pattern.compile("\\bgl_MultiTexCoord0\\b").matcher(uncommented).find();
        boolean usesNormal = Pattern.compile("\\bgl_Normal\\b").matcher(uncommented).find();
        boolean usesNormalMatrix = Pattern.compile("\\bgl_NormalMatrix\\b").matcher(uncommented).find();
        boolean usesTextureMatrix = Pattern.compile("\\bgl_TextureMatrix\\s*\\[\\s*0\\s*\\]")
                .matcher(uncommented).find();
        if (usesTextureMatrix) {
            source = Pattern.compile("\\bgl_TextureMatrix\\s*\\[\\s*0\\s*\\]")
                    .matcher(source).replaceAll("textureMatrix");
            if (!Pattern.compile("(?m)^\\s*uniform\\s+mat4\\s+textureMatrix\\s*;")
                    .matcher(maskComments(source)).find()) {
                Matcher main = VERTEX_MAIN_FUNCTION.matcher(maskComments(source));
                if (!main.find()) {
                    throw new IOException("gbuffers_clouds vertex shader has no main function.");
                }
                source = source.substring(0, main.start()) + "uniform mat4 textureMatrix;\n"
                        + source.substring(main.start());
            }
            uncommented = maskComments(source);
        }
        boolean usesModelViewMatrix = Pattern.compile("\\bgl_ModelViewMatrix\\b")
                .matcher(uncommented).find();
        if (usesModelViewMatrix) {
            source = Pattern.compile("\\bgl_ModelViewMatrix\\b").matcher(source).replaceAll("ModelViewMat");
            uncommented = maskComments(source);
        }
        IrisFogUniformAdapter.AdaptedShader fogUniforms =
                IrisFogUniformAdapter.extract(source, "gbuffers_clouds", true);
        source = fogUniforms.source();
        ShaderPackUniformAdapter.AdaptedShader shaderPackUniforms =
                ShaderPackUniformAdapter.extract(source, "gbuffers_clouds");
        source = shaderPackUniforms.source();
        uncommented = maskComments(source);
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
        if (usesModelViewMatrix) {
            ShaderPackUniformAdapter.UniformField modelViewField =
                    new ShaderPackUniformAdapter.UniformField("mat4", "ModelViewMat");
            ShaderPackUniformAdapter.UniformField previous = uniformFields.putIfAbsent(
                    modelViewField.name(), modelViewField);
            if (previous != null && !previous.equals(modelViewField)) {
                throw new IOException("gbuffers_clouds declares incompatible uniform buffer fields named '"
                        + modelViewField.name() + "'.");
            }
        }

        Matcher outputMatcher = CLOUD_VERTEX_OUTPUT_DECLARATION.matcher(uncommented);
        StringBuilder withoutOutputs = new StringBuilder(source.length());
        StringBuilder bridgeOutputs = new StringBuilder();
        StringBuilder bridgeMacros = new StringBuilder();
        int copiedThrough = 0;
        Set<String> producedVaryings = new LinkedHashSet<>();
        while (outputMatcher.find()) {
            String type = outputMatcher.group(3);
            String[] names = outputMatcher.group(4).split("\\s*,\\s*");
            String location = outputMatcher.group(1);
            if (location != null && names.length > 1) {
                throw new IOException("gbuffers_clouds comma-separated vertex outputs cannot specify an explicit location.");
            }
            String interpolation = outputMatcher.group(2);
            if (interpolation != null && !interpolation.equals("smooth")) {
                throw new IOException("gbuffers_clouds vertex outputs must use smooth interpolation.");
            }
            for (int index = 0; index < names.length; index++) {
                String name = names[index];
                Varying varying = SUPPORTED_VARYINGS.get(name);
                if (varying == null) {
                    throw new IOException("gbuffers_clouds vertex output '" + name
                            + "' is not provided by Veyra's cloud bridge yet.");
                }
                if (!varying.type().equals(type)) {
                    throw new IOException("gbuffers_clouds vertex output '" + name + "' must use "
                            + varying.type() + ".");
                }
                if (location != null && Integer.parseInt(location) != varying.location()) {
                    throw new IOException("gbuffers_clouds vertex output '" + name + "' uses an unsupported location.");
                }
                if (!producedVaryings.add(name)) {
                    throw new IOException("gbuffers_clouds declares vertex output '" + name + "' more than once.");
                }
                if (varying.location() > 1) {
                    String outputName = "veyraCloud" + Character.toUpperCase(name.charAt(0)) + name.substring(1);
                    bridgeOutputs.append("layout(location = ").append(varying.location()).append(") out ")
                            .append(type).append(' ').append(outputName).append(';').append('\n');
                    bridgeMacros.append("#define ").append(name).append(' ').append(outputName).append('\n');
                }
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
        StringBuilder bridge = new StringBuilder("""
                #define gl_Vertex vec4(Position, 1.0)
                #define gl_ModelViewProjectionMatrix MVP
                #define ftransform() (MVP * vec4(Position, 1.0))
                #define color vertexColor
                #define glcolor vertexColor
                #define glColor vertexColor
                #define gl_Color vertexColor
                """);
        if (usesTextureCoordinates) {
            bridge.append("layout(location = 2) in vec2 VeyraCloudTexCoord0;").append('\n')
                    .append("#define gl_MultiTexCoord0 vec4(VeyraCloudTexCoord0, 0.0, 1.0)").append('\n');
        }
        if (usesNormal) {
            bridge.append("layout(location = 3) in vec3 VeyraCloudNormal;").append('\n')
                    .append("#define gl_Normal VeyraCloudNormal").append('\n');
        }
        if (usesNormalMatrix) {
            bridge.append("#define gl_NormalMatrix transpose(inverse(mat3(gbufferModelView)))").append('\n');
        }
        bridge.append(fogUniforms.bridge()).append(bridgeOutputs).append(bridgeMacros)
                .append(shaderPackUniforms.macros());
        String withPackGlobals = withExtensions.substring(0, originalBaseMain.start()) + bridge + packGlobals
                + withExtensions.substring(originalBaseMain.start());
        MainFunction baseMain = findMain(withPackGlobals, "VulkanMod cloud");
        String defaultAssignments = "\n    vertexColor = Color * ColorModulator;"
                + "\n    vertexDistance = length(Position + ModelOffset);\n";
        String adapted = withPackGlobals.substring(0, baseMain.bodyStart() + 1) + defaultAssignments + packBody
                + withPackGlobals.substring(baseMain.bodyEnd());
        return new AdaptedVertex(adapted, List.copyOf(uniformFields.values()),
                usesTextureCoordinates || usesNormal, producedVaryings);
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

    /** Prunes stage-agnostic helper functions that the cloud vertex entrypoint cannot reach. */
    private static String removeUnreachableCloudFunctions(String source) {
        return UnreachableGlslFunctions.removeUnreachable(source, "main");
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
        return adapt(source, List.of());
    }

    public static AdaptedFragment adapt(String source,
                                        List<ShaderPackUniformAdapter.UniformField> additionalUniformFields)
            throws IOException {
        return adapt(source, additionalUniformFields, DEFAULT_FIRST_IMAGE_BINDING);
    }

    public static AdaptedFragment adapt(String source,
                                        List<ShaderPackUniformAdapter.UniformField> additionalUniformFields,
                                        int firstImageBinding) throws IOException {
        return adapt(source, additionalUniformFields, firstImageBinding, Set.of());
    }

    public static AdaptedFragment adapt(String source,
                                        List<ShaderPackUniformAdapter.UniformField> additionalUniformFields,
                                        int firstImageBinding, Set<String> customImageSamplers) throws IOException {
        if (firstImageBinding < 0) {
            throw new IllegalArgumentException("The first cloud image binding must not be negative.");
        }
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
        SceneColorInput sceneColorInput = sceneColorInput(adapted, customImageSamplers);
        adapted = sceneColorInput.source();
        ShaderPackUniformAdapter.AdaptedShader shaderPackUniforms =
                ShaderPackUniformAdapter.extract(adapted, "gbuffers_clouds");
        adapted = shaderPackUniforms.source();
        Map<String, Integer> samplerBindings = new java.util.LinkedHashMap<>();
        int nextImageBinding = firstImageBinding;
        if (!sceneColorInput.sceneColorSamplers().isEmpty()) {
            samplerBindings.put(CLOUD_SCENE_SAMPLER_NAME, nextImageBinding++);
        }
        if (sceneColorInput.usesNoiseSampler()) {
            samplerBindings.put(VeyraNoiseTexture.SAMPLER_NAME, nextImageBinding++);
        }
        if (sceneColorInput.usesCloudTexture()) {
            samplerBindings.put(VeyraCloudTexture.SAMPLER_NAME, nextImageBinding);
        }

        String uncommented = COMMENTS.matcher(adapted).replaceAll("");
        if (UNSUPPORTED_CLOUD_INPUT.matcher(uncommented).find()) {
            throw new IOException("gbuffers_clouds uses unsupported texture-coordinate, normal, entity-ID, or legacy fog inputs.");
        }
        Matcher textureSample = TEXTURE_SAMPLE.matcher(uncommented);
        while (textureSample.find()) {
            if (!SUPPORTED_TEXTURE_FUNCTIONS.contains(textureSample.group(1))) {
                throw new IOException("gbuffers_clouds uses an unsupported texture sampling function.");
            }
            if (!sceneColorInput.samplers().contains(textureSample.group(2))) {
                throw new IOException("gbuffers_clouds can sample only completed scene color, the Minecraft cloud texture, or noisetex through Veyra's cloud bridge.");
            }
        }
        if (UNSUPPORTED_UNIFORM_DECLARATION.matcher(adapted).find()) {
            throw new IOException("gbuffers_clouds custom uniforms are not supported yet; use the provided cloud color input.");
        }

        Matcher varyingMatcher = VARYING_DECLARATION.matcher(adapted);
        StringBuffer withoutVaryings = new StringBuffer();
        Set<String> varyings = new LinkedHashSet<>();
        while (varyingMatcher.find()) {
            String type = varyingMatcher.group(2);
            String[] names = varyingMatcher.group(3).split("\\s*,\\s*");
            String location = varyingMatcher.group(1);
            if (location != null && names.length > 1) {
                throw new IOException("gbuffers_clouds comma-separated inputs cannot specify an explicit location.");
            }
            for (String name : names) {
                Varying varying = SUPPORTED_VARYINGS.get(name);
                if (varying == null) {
                    throw new IOException("gbuffers_clouds input '" + name + "' is not provided by Veyra's cloud bridge yet.");
                }
                if (!varying.type().equals(type)) {
                    throw new IOException("gbuffers_clouds input '" + name + "' must use " + varying.type() + ".");
                }
                if (location != null && Integer.parseInt(location) != varying.location()) {
                    throw new IOException("gbuffers_clouds input '" + name + "' uses an unsupported location.");
                }
                varyings.add(name);
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
        for (String name : varyings) {
            Varying varying = SUPPORTED_VARYINGS.get(name);
            if (varying.location() <= 1) continue;
            String inputName = "veyraCloud" + Character.toUpperCase(name.charAt(0)) + name.substring(1);
            result.append("layout(location = ").append(varying.location()).append(") in ")
                    .append(varying.type()).append(' ').append(inputName).append(';').append('\n')
                    .append("#define ").append(name).append(' ').append(inputName).append('\n');
        }
        if (!sceneColorInput.sceneColorSamplers().isEmpty()) {
            result.append("layout(binding = ").append(samplerBindings.get(CLOUD_SCENE_SAMPLER_NAME))
                    .append(") uniform sampler2D ").append(CLOUD_SCENE_SAMPLER_NAME).append(";\n");
            for (String sampler : sceneColorInput.sceneColorSamplers()) {
                result.append("#define ").append(sampler).append(" VeyraCloudSceneSampler\n");
            }
        }
        if (sceneColorInput.usesNoiseSampler()) {
            result.append("layout(binding = ").append(samplerBindings.get(VeyraNoiseTexture.SAMPLER_NAME))
                    .append(") uniform sampler2D ").append(VeyraNoiseTexture.SAMPLER_NAME).append(";\n")
                    .append("#define ").append(NOISE_SAMPLER).append(' ').append(VeyraNoiseTexture.SAMPLER_NAME).append('\n');
        }
        if (sceneColorInput.usesCloudTexture()) {
            result.append("layout(binding = ").append(samplerBindings.get(VeyraCloudTexture.SAMPLER_NAME))
                    .append(") uniform sampler2D ").append(VeyraCloudTexture.SAMPLER_NAME).append(";\n");
            adapted = replaceSamplerArgument(adapted, "texture", VeyraCloudTexture.SAMPLER_NAME);
        }
        adapted = adapted.replaceAll("\\btexture2DLod\\b", "textureLod");
        adapted = LEGACY_TEXTURE_2D.matcher(adapted).replaceAll("texture");
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
        for (ShaderPackUniformAdapter.UniformField field : additionalUniformFields) {
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
        return new AdaptedFragment(result.append(adapted).toString(), List.copyOf(uniformFields.values()),
                !sceneColorInput.sceneColorSamplers().isEmpty(), sceneColorInput.usesNoiseSampler(),
                sceneColorInput.usesCloudTexture(), Set.copyOf(varyings), Map.copyOf(samplerBindings));
    }

    private static String replaceSamplerArgument(String source, String sampler, String replacement) {
        Pattern sample = Pattern.compile("(\\b(?:texture|texture2D|textureLod|texture2DLod|textureSize|texelFetch)\\s*\\(\\s*)"
                + Pattern.quote(sampler) + "(\\s*,)");
        return sample.matcher(source).replaceAll("$1" + replacement + "$2");
    }

    private static SceneColorInput sceneColorInput(String source, Set<String> customImageSamplers) throws IOException {
        source = UnusedSamplerDeclarations.removeUnused(source);
        String uncommented = maskComments(source);
        Matcher declarations = SAMPLER_DECLARATION.matcher(uncommented);
        Set<String> samplers = new LinkedHashSet<>(customImageSamplers);
        Set<String> sceneColorSamplers = new LinkedHashSet<>();
        boolean usesNoiseSampler = false;
        boolean usesCloudTexture = false;
        StringBuilder withoutSamplers = new StringBuilder(source);
        while (declarations.find()) {
            String type = declarations.group(1);
            String name = declarations.group(2);
            if (!SCENE_COLOR_SAMPLERS.contains(name) && !name.equals(NOISE_SAMPLER) && !name.equals("texture")
                    && !customImageSamplers.contains(name)) {
                throw new IOException("gbuffers_clouds active sampler '" + name
                        + "' is not supported; cloud shaders can sample scene color, the cloud texture, or noisetex.");
            }
            if (!type.equals("sampler2D") && !customImageSamplers.contains(name)) {
                throw new IOException("gbuffers_clouds sampler '" + name + "' must use sampler2D.");
            }
            if (!samplers.add(name)) {
                throw new IOException("gbuffers_clouds declares sampler '" + name + "' more than once.");
            }
            if (SCENE_COLOR_SAMPLERS.contains(name)) sceneColorSamplers.add(name);
            else if (name.equals(NOISE_SAMPLER)) usesNoiseSampler = true;
            else usesCloudTexture = true;
            for (int index = declarations.start(); index < declarations.end(); index++) {
                char character = withoutSamplers.charAt(index);
                if (character != '\r' && character != '\n') withoutSamplers.setCharAt(index, ' ');
            }
        }
        Matcher samples = TEXTURE_SAMPLE.matcher(maskComments(withoutSamplers.toString()));
        while (samples.find()) {
            if (!SUPPORTED_TEXTURE_FUNCTIONS.contains(samples.group(1))) {
                throw new IOException("gbuffers_clouds uses an unsupported texture sampling function.");
            }
            if (!samplers.contains(samples.group(2))) {
                throw new IOException("gbuffers_clouds can sample only completed scene color, the Minecraft cloud texture, or noisetex through Veyra's cloud bridge.");
            }
        }
        return new SceneColorInput(withoutSamplers.toString(), Set.copyOf(sceneColorSamplers),
                Set.copyOf(samplers), usesNoiseSampler, usesCloudTexture);
    }

    public record AdaptedFragment(String source, List<ShaderPackUniformAdapter.UniformField> uniformFields,
                                  boolean usesSceneColorSampler, boolean usesNoiseSampler,
                                  boolean usesCloudTexture, Set<String> varyings,
                                  Map<String, Integer> samplerBindings) {
    }

    public record AdaptedVertex(String source, List<ShaderPackUniformAdapter.UniformField> uniformFields,
                                boolean requiresExtendedVertexFormat, Set<String> varyings) {
    }

    private record SceneColorInput(String source, Set<String> sceneColorSamplers, Set<String> samplers,
                                   boolean usesNoiseSampler, boolean usesCloudTexture) { }

    private record Varying(String type, int location) {
    }

    private record MainFunction(int start, int bodyStart, int bodyEnd) {
    }
}
