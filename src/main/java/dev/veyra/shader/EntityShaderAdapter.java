package dev.veyra.shader;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Bridges a small Iris entity-fragment interface to Minecraft's entity vertex outputs. */
public final class EntityShaderAdapter {
    public static final float MC_HAND_DEPTH = 0.125F;

    private static final Pattern VERSION_DIRECTIVE = Pattern.compile("(?m)^[\\t ]*#version[^\\r\\n]*(?:\\r?\\n|$)");
    private static final Pattern EXTENSION_DIRECTIVE = Pattern.compile("(?m)^[\\t ]*#extension[^\\r\\n]*(?:\\r?\\n|$)");
    private static final Pattern PRECISION_DECLARATION = Pattern.compile("(?m)^[\\t ]*precision\\s+(?:lowp|mediump|highp)\\s+\\w+\\s*;[\\t ]*(?:\\r?\\n|$)");
    private static final Pattern ALPHA_TEST_UNIFORM = Pattern.compile(
            "(?m)^[\\t ]*uniform\\s+(?:(?:lowp|mediump|highp)\\s+)?float\\s+alphaTestRef\\s*;[\\t ]*(?://[^\\r\\n]*)?(?:\\r?\\n|$)");
    private static final Pattern SAMPLER_DECLARATION = Pattern.compile(
            "(?m)^[\\t ]*uniform\\s+(?:(?:lowp|mediump|highp)\\s+)?(sampler\\w+)\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*;[\\t ]*(?://[^\\r\\n]*)?(?:\\r?\\n|$)");
    private static final Pattern UNSUPPORTED_UNIFORM_DECLARATION = Pattern.compile("(?m)^[\\t ]*uniform\\b");
    private static final Pattern ENTITY_IDENTIFIER_UNIFORM = Pattern.compile(
            "(?m)^[\\t ]*uniform\\s+(?:(?:lowp|mediump|highp)\\s+)?int\\s+(entityId|blockEntityId|currentRenderedItemId)\\s*;[\\t ]*(?://[^\\r\\n]*)?(?:\\r?\\n|$)");
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
            "\\b(?:mc_Entity|mc_midTexCoord|midTexCoord|tangent|at_tangent|gl_Fog\\w*)\\b");
    private static final Pattern UNSUPPORTED_TEXT_INPUT = Pattern.compile(
            "\\b(?:mc_Entity|mc_midTexCoord|midTexCoord|normal|viewNormal|tangent|at_tangent|lightMapColor|overlayColor|gl_Fog\\w*)\\b");
    private static final Pattern UNSUPPORTED_TEXTURE_FUNCTION = Pattern.compile(
            "\\btexture(?:Gather|Offset|Proj|QueryLod)\\w*\\s*\\(");
    private static final Pattern NOISETEX_REFERENCE = Pattern.compile("\\bnoisetex\\b");
    private static final Pattern SHADOWTEX_REFERENCE = Pattern.compile("\\bshadowtex0\\b");
    private static final Pattern NORMAL_VERTEX_ATTRIBUTE = Pattern.compile(
            "(?m)^[\\t ]*(?:layout\\s*\\([^)]*\\)\\s*)?in\\s+vec3\\s+Normal\\s*;");
    private static final Pattern NORMAL_VERTEX_OUTPUT = Pattern.compile(
            "(?m)^[\\t ]*(?:layout\\s*\\([^)]*\\)\\s*)?out\\s+vec3\\s+normal\\s*;");
    private static final Pattern LIGHTMAP_VERTEX_ATTRIBUTE = Pattern.compile(
            "(?m)^[\\t ]*(?:layout\\s*\\([^)]*\\)\\s*)?in\\s+ivec2\\s+UV2\\s*;");
    private static final Pattern ITEM_ENTITY_FLOAT_OVERLAY_ATTRIBUTE = Pattern.compile(
            "(?m)^([\\t ]*(?:layout\\s*\\([^)]*\\)\\s*)?in\\s+)vec2(\\s+UV1\\s*;)");
    private static final Pattern ITEM_ENTITY_INTEGER_OVERLAY_ATTRIBUTE = Pattern.compile(
            "(?m)^[\\t ]*(?:layout\\s*\\([^)]*\\)\\s*)?in\\s+ivec2\\s+UV1\\s*;");
    private static final Pattern ITEM_ENTITY_OVERLAY_ASSIGNMENT = Pattern.compile(
            "(?m)^([\\t ]*texCoord1\\s*=\\s*)UV1(\\s*;)");
    private static final Pattern LIGHTMAP_VERTEX_OUTPUT = Pattern.compile(
            "(?m)^[\\t ]*(?:layout\\s*\\([^)]*\\)\\s*)?out\\s+vec2\\s+lmcoord\\s*;");
    private static final Pattern VIEW_POSITION_VERTEX_ATTRIBUTE = Pattern.compile(
            "(?m)^[\\t ]*(?:layout\\s*\\([^)]*\\)\\s*)?in\\s+vec3\\s+Position\\s*;");
    private static final Pattern VIEW_POSITION_VERTEX_OUTPUT = Pattern.compile(
            "(?m)^[\\t ]*(?:layout\\s*\\([^)]*\\)\\s*)?out\\s+vec3\\s+viewPos\\s*;");
    private static final Pattern VIEW_NORMAL_VERTEX_OUTPUT = Pattern.compile(
            "(?m)^[\\t ]*(?:layout\\s*\\([^)]*\\)\\s*)?out\\s+vec3\\s+viewNormal\\s*;");
    private static final Pattern MAIN_FUNCTION = Pattern.compile("\\bvoid\\s+main\\s*\\(\\s*(?:void\\s*)?\\)\\s*\\{");
    private static final Pattern NORMAL_REFERENCE = Pattern.compile("\\bnormal\\b");
    private static final Pattern NORMAL_ASSIGNMENT = Pattern.compile("\\bnormal\\s*=");
    private static final Pattern VIEW_POSITION_ASSIGNMENT = Pattern.compile("\\bviewPos\\s*=");
    private static final Pattern VIEW_NORMAL_ASSIGNMENT = Pattern.compile("\\bviewNormal\\s*=");
    private static final Pattern GL_POSITION_ASSIGNMENT = Pattern.compile("\\bgl_Position\\s*=");
    private static final Pattern VIEW_POSITION_REFERENCE = Pattern.compile("\\bviewPos\\b");
    private static final Pattern VIEW_NORMAL_REFERENCE = Pattern.compile("\\bviewNormal\\b");
    private static final Pattern VIEW_POSITION_VARIABLE_DECLARATION = Pattern.compile(
            "\\b(?:bool|int|uint|float|double|[biu]?vec[234]|dvec[234]|[biu]?mat[234](?:x[234])?|dmat[234](?:x[234])?)\\s+viewPos\\b");
    private static final Pattern VIEW_NORMAL_VARIABLE_DECLARATION = Pattern.compile(
            "\\b(?:bool|int|uint|float|double|[biu]?vec[234]|dvec[234]|[biu]?mat[234](?:x[234])?|dmat[234](?:x[234])?)\\s+viewNormal\\b");
    private static final Pattern LIGHTMAP_REFERENCE = Pattern.compile("\\b(?:lmcoord|lightMapCoord|lmCoord)\\b");
    private static final Pattern LIGHTMAP_ASSIGNMENT = Pattern.compile("\\blmcoord\\s*=");
    private static final Map<String, Varying> SUPPORTED_VARYINGS = Map.ofEntries(
            Map.entry("texcoord", new Varying("vec2")),
            Map.entry("texCoord", new Varying("vec2")),
            Map.entry("texCoord0", new Varying("vec2")),
            Map.entry("color", new Varying("vec4")),
            Map.entry("glcolor", new Varying("vec4")),
            Map.entry("glColor", new Varying("vec4")),
            Map.entry("gl_Color", new Varying("vec4")),
            Map.entry("sphericalVertexDistance", new Varying("float")),
            Map.entry("cylindricalVertexDistance", new Varying("float")),
            Map.entry("lmcoord", new Varying("vec2")),
            Map.entry("lightMapCoord", new Varying("vec2")),
            Map.entry("lmCoord", new Varying("vec2")),
            Map.entry("lightMapColor", new Varying("vec4")),
            Map.entry("overlayColor", new Varying("vec4")),
            Map.entry("normal", new Varying("vec3")),
            Map.entry("viewPos", new Varying("vec3")),
            Map.entry("viewNormal", new Varying("vec3")));
    private static final Map<String, Varying> SUPPORTED_TEXT_VARYINGS = Map.ofEntries(
            Map.entry("texcoord", new Varying("vec2")),
            Map.entry("texCoord", new Varying("vec2")),
            Map.entry("texCoord0", new Varying("vec2")),
            Map.entry("glcolor", new Varying("vec4")),
            Map.entry("glColor", new Varying("vec4")),
            Map.entry("gl_Color", new Varying("vec4")),
            Map.entry("sphericalVertexDistance", new Varying("float")),
            Map.entry("cylindricalVertexDistance", new Varying("float")),
            Map.entry("lmcoord", new Varying("vec2")),
            Map.entry("lightMapCoord", new Varying("vec2")),
            Map.entry("lmCoord", new Varying("vec2")),
            Map.entry("viewPos", new Varying("vec3")));

    private EntityShaderAdapter() {
    }

    public static String adaptFragment(String source) throws IOException {
        return adaptFragment(source, "gbuffers_entities");
    }

    public static String adaptFragment(String source, String programName) throws IOException {
        return adaptFragment(source, programName, false, false);
    }

    public static String adaptFragment(String source, String programName, boolean handPass) throws IOException {
        return adaptFragment(source, programName, handPass, false);
    }

    public static String adaptFragment(String source, String programName, boolean handPass,
                                       boolean shadowSamplerAvailable) throws IOException {
        return adaptFragment(source, programName, false, handPass, shadowSamplerAvailable,
                -1, -1, ItemIdMap.UNMAPPED);
    }

    /** Adapts an entity fragment with per-render Iris entity and block-entity identifiers. */
    public static String adaptFragment(String source, String programName, boolean handPass,
                                       boolean shadowSamplerAvailable,
                                       int entityId, int blockEntityId) throws IOException {
        return adaptFragment(source, programName, false, handPass, shadowSamplerAvailable,
                entityId, blockEntityId, ItemIdMap.UNMAPPED);
    }

    /** Adapts an entity fragment with per-render entity, block-entity, and item identifiers. */
    public static String adaptFragment(String source, String programName, boolean handPass,
                                       boolean shadowSamplerAvailable, int entityId, int blockEntityId,
                                       int currentRenderedItemId) throws IOException {
        return adaptFragment(source, programName, false, handPass, shadowSamplerAvailable,
                entityId, blockEntityId, currentRenderedItemId);
    }

    /** Adapts an entity fragment while indicating whether its Minecraft vertex pipeline emits overlayColor. */
    public static String adaptFragment(String source, String programName, boolean handPass,
                                       boolean shadowSamplerAvailable, int entityId, int blockEntityId,
                                       int currentRenderedItemId, boolean entityColorAvailable) throws IOException {
        return adaptFragment(source, programName, false, handPass, shadowSamplerAvailable,
                entityId, blockEntityId, currentRenderedItemId, entityColorAvailable);
    }

    public static String adaptTextFragment(String source, String programName) throws IOException {
        return adaptTextFragment(source, programName, false);
    }

    public static String adaptTextFragment(String source, String programName,
                                          boolean shadowSamplerAvailable) throws IOException {
        return adaptTextFragment(source, programName, shadowSamplerAvailable, -1, -1);
    }

    /** Adapts sign text with the block entity identifier of the renderer that submitted it. */
    public static String adaptTextFragment(String source, String programName,
                                          boolean shadowSamplerAvailable,
                                          int entityId, int blockEntityId) throws IOException {
        return adaptFragment(source, programName, true, false, shadowSamplerAvailable,
                entityId, blockEntityId, ItemIdMap.UNMAPPED);
    }

    /** Adapts sign text with per-render block-entity and item identifiers. */
    public static String adaptTextFragment(String source, String programName,
                                          boolean shadowSamplerAvailable,
                                          int entityId, int blockEntityId,
                                          int currentRenderedItemId) throws IOException {
        return adaptFragment(source, programName, true, false, shadowSamplerAvailable,
                entityId, blockEntityId, currentRenderedItemId);
    }

    /** Whether the active program declares an Iris-bound entity identifier uniform. */
    public static boolean usesEntityIdentifier(String source, String programName, String name) {
        String uncommented = COMMENTS.matcher(source == null ? "" : source).replaceAll("");
        Matcher matcher = ENTITY_IDENTIFIER_UNIFORM.matcher(uncommented);
        while (matcher.find()) {
            if (matcher.group(1).equals(name)
                    && ShaderPackUniformAdapter.isBoundEntityIdentifier(programName, "int", name)) {
                return true;
            }
        }
        return false;
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

    public static boolean requiresLightmapInput(String source) {
        String uncommented = COMMENTS.matcher(source == null ? "" : source).replaceAll("");
        Matcher matcher = VARYING_DECLARATION.matcher(uncommented);
        while (matcher.find()) {
            if (matcher.group(3).equals("lmcoord") || matcher.group(3).equals("lightMapCoord")
                    || matcher.group(3).equals("lmCoord")) {
                return true;
            }
        }
        return false;
    }

    public static boolean requiresViewPositionInput(String source) {
        return requiresVarying(source, "viewPos");
    }

    public static boolean requiresViewNormalInput(String source) {
        return requiresVarying(source, "viewNormal");
    }

    private static boolean requiresVarying(String source, String name) {
        String uncommented = COMMENTS.matcher(source == null ? "" : source).replaceAll("");
        Matcher matcher = VARYING_DECLARATION.matcher(uncommented);
        while (matcher.find()) {
            if (matcher.group(3).equals(name)) {
                return true;
            }
        }
        return false;
    }

    public static String adaptVertexShader(String source, boolean shadowPass, String programName) throws IOException {
        return adaptVertexShader(source, shadowPass, programName, true, false);
    }

    /**
     * Minecraft 1.21.11's item-entity vertex shader declares UV1 as a float despite NEW_ENTITY supplying
     * an integer pair.
     */
    static String adaptItemEntityVertexFormat(String source, String programName) throws IOException {
        if (source == null || source.isBlank()) {
            throw new IOException(programName + " cannot adapt the item-entity vertex format because its VulkanMod vertex shader is empty.");
        }
        Matcher floatOverlay = ITEM_ENTITY_FLOAT_OVERLAY_ATTRIBUTE.matcher(source);
        if (!floatOverlay.find()) {
            if (ITEM_ENTITY_INTEGER_OVERLAY_ATTRIBUTE.matcher(source).find()) {
                return source;
            }
            throw new IOException(programName + " cannot adapt the item-entity vertex format because UV1's shader input changed.");
        }
        StringBuffer adapted = new StringBuffer();
        floatOverlay.appendReplacement(adapted,
                Matcher.quoteReplacement(floatOverlay.group(1) + "ivec2" + floatOverlay.group(2)));
        floatOverlay.appendTail(adapted);
        Matcher overlayAssignment = ITEM_ENTITY_OVERLAY_ASSIGNMENT.matcher(adapted.toString());
        if (!overlayAssignment.find()) {
            return adapted.toString();
        }
        return overlayAssignment.replaceAll(Matcher.quoteReplacement(overlayAssignment.group(1)
                + "vec2(UV1)" + overlayAssignment.group(2)));
    }

    public static String adaptVertexShader(String source, boolean shadowPass, String programName,
                                          boolean requiresNormal, boolean requiresLightmapCoord) throws IOException {
        return adaptVertexShader(source, shadowPass, programName,
                requiresNormal, requiresLightmapCoord, false, false);
    }

    public static String adaptVertexShader(String source, boolean shadowPass, String programName,
                                          boolean requiresNormal, boolean requiresLightmapCoord,
                                          boolean requiresViewPosition, boolean requiresViewNormal) throws IOException {
        return adaptVertexShader(source, shadowPass, programName,
                requiresNormal, requiresLightmapCoord, requiresViewPosition, requiresViewNormal, false);
    }

    public static String adaptVertexShader(String source, boolean shadowPass, String programName,
                                          boolean requiresNormal, boolean requiresLightmapCoord,
                                          boolean requiresViewPosition, boolean requiresViewNormal,
                                          boolean handPass) throws IOException {
        if (source == null || source.isBlank()) {
            List<String> required = new ArrayList<>();
            if (requiresNormal) required.add("normals");
            if (requiresLightmapCoord) required.add("lightmap coordinates");
            if (requiresViewPosition) required.add("view-space positions");
            if (requiresViewNormal) required.add("view-space normals");
            if (handPass) required.add("the first-person hand depth transform");
            String requiredInputs = String.join(", ", required);
            throw new IOException(programName + " requires " + requiredInputs
                    + ", but its VulkanMod vertex shader is empty.");
        }
        if (!requiresNormal && !requiresLightmapCoord && !requiresViewPosition && !requiresViewNormal && !handPass) {
            return source;
        }
        String uncommented = COMMENTS.matcher(source).replaceAll("");
        boolean hasNormalOutput = NORMAL_VERTEX_OUTPUT.matcher(uncommented).find();
        boolean hasNormalAssignment = NORMAL_ASSIGNMENT.matcher(uncommented).find();
        boolean addNormalOutput = requiresNormal && !hasNormalOutput;
        boolean addNormalAssignment = requiresNormal && !hasNormalAssignment;
        if (addNormalAssignment && !NORMAL_VERTEX_ATTRIBUTE.matcher(uncommented).find()) {
            throw new IOException(programName + " requires normals, but VulkanMod's vertex format does not provide them.");
        }
        boolean hasLightmapOutput = LIGHTMAP_VERTEX_OUTPUT.matcher(uncommented).find();
        boolean hasLightmapAssignment = LIGHTMAP_ASSIGNMENT.matcher(uncommented).find();
        boolean addLightmapOutput = requiresLightmapCoord && !hasLightmapOutput;
        boolean addLightmapAssignment = requiresLightmapCoord && !hasLightmapAssignment;
        if (addLightmapAssignment && !LIGHTMAP_VERTEX_ATTRIBUTE.matcher(uncommented).find()) {
            throw new IOException(programName + " requires lmcoord, but VulkanMod's vertex format does not provide UV2.");
        }
        boolean hasViewPositionOutput = VIEW_POSITION_VERTEX_OUTPUT.matcher(uncommented).find();
        boolean hasViewPositionAssignment = VIEW_POSITION_ASSIGNMENT.matcher(uncommented).find();
        boolean addViewPositionOutput = requiresViewPosition && !hasViewPositionOutput;
        boolean addViewPositionAssignment = requiresViewPosition && !hasViewPositionAssignment;
        if (addViewPositionAssignment && !VIEW_POSITION_VERTEX_ATTRIBUTE.matcher(uncommented).find()) {
            throw new IOException(programName + " requires viewPos, but VulkanMod's vertex format does not provide positions.");
        }
        boolean hasViewNormalOutput = VIEW_NORMAL_VERTEX_OUTPUT.matcher(uncommented).find();
        boolean hasViewNormalAssignment = VIEW_NORMAL_ASSIGNMENT.matcher(uncommented).find();
        boolean addViewNormalOutput = requiresViewNormal && !hasViewNormalOutput;
        boolean addViewNormalAssignment = requiresViewNormal && !hasViewNormalAssignment;
        if (addViewNormalAssignment && !NORMAL_VERTEX_ATTRIBUTE.matcher(uncommented).find()) {
            throw new IOException(programName + " requires viewNormal, but VulkanMod's vertex format does not provide normals.");
        }
        if (!addNormalOutput && !addNormalAssignment && !addLightmapOutput && !addLightmapAssignment
                && !addViewPositionOutput && !addViewPositionAssignment
                && !addViewNormalOutput && !addViewNormalAssignment) {
            if (!handPass) {
                return source;
            }
        }
        Matcher main = MAIN_FUNCTION.matcher(source);
        if (!main.find()) {
            throw new IOException(programName + (handPass
                    ? " requires the first-person hand depth transform, but its VulkanMod vertex shader has no main function."
                    : " requires additional vertex outputs, but its VulkanMod vertex shader has no main function."));
        }

        StringBuilder declarations = new StringBuilder();
        String inverseMatrix = shadowPass ? "shadowModelViewInverse" : "gbufferModelViewInverse";
        if (addNormalAssignment) {
            Pattern inverseMatrixUniform = Pattern.compile("(?m)^[\\t ]*uniform\\s+mat4\\s+"
                    + Pattern.quote(inverseMatrix) + "\\s*;");
            if (!inverseMatrixUniform.matcher(uncommented).find()) {
                declarations.append("uniform mat4 ").append(inverseMatrix).append(";\n");
            }
        }
        if (addNormalOutput) {
            declarations.append("out vec3 normal;\n");
        }
        if (addLightmapOutput) {
            declarations.append("out vec2 lmcoord;\n");
        }
        if (addViewPositionOutput) {
            declarations.append("out vec3 viewPos;\n");
        }
        if (addViewNormalOutput) {
            declarations.append("out vec3 viewNormal;\n");
        }
        String withDeclarations = source.substring(0, main.start()) + declarations + source.substring(main.start());
        Matcher adaptedMain = MAIN_FUNCTION.matcher(withDeclarations);
        if (!adaptedMain.find()) {
            throw new IOException(programName + " could not add its required vertex outputs.");
        }
        StringBuilder vertexAssignments = new StringBuilder();
        if (addNormalAssignment) {
            vertexAssignments.append("normal = normalize(mat3(").append(inverseMatrix)
                    .append(") * transpose(inverse(mat3(ModelViewMat))) * Normal);\n");
        }
        if (addLightmapAssignment) {
            vertexAssignments.append("lmcoord = (vec2(UV2) / 256.0) + vec2(0.03125);\n");
        }
        if (addViewPositionAssignment) {
            vertexAssignments.append("viewPos = (ModelViewMat * vec4(Position, 1.0)).xyz;\n");
        }
        if (addViewNormalAssignment) {
            vertexAssignments.append("viewNormal = normalize(transpose(inverse(mat3(ModelViewMat))) * Normal);\n");
        }
        String adapted = withDeclarations.substring(0, adaptedMain.end()) + vertexAssignments
                + withDeclarations.substring(adaptedMain.end());
        return handPass ? applyHandDepth(adapted, programName) : adapted;
    }

    private static String applyHandDepth(String source, String programName) throws IOException {
        String uncommented = maskComments(source);
        Matcher main = MAIN_FUNCTION.matcher(uncommented);
        if (!main.find()) {
            throw new IOException(programName + " cannot apply MC_HAND_DEPTH because VulkanMod's vertex shader has no main function.");
        }
        int mainStart = main.end() - 1;
        int mainEnd = findMainEnd(uncommented, mainStart, programName);
        Matcher assignment = GL_POSITION_ASSIGNMENT.matcher(uncommented);
        boolean assignsClipPosition = false;
        while (assignment.find()) {
            if (assignment.start() > mainStart && assignment.start() < mainEnd) {
                assignsClipPosition = true;
                break;
            }
        }
        if (!assignsClipPosition) {
            throw new IOException(programName + " cannot apply MC_HAND_DEPTH because VulkanMod's vertex main function does not assign gl_Position.");
        }
        String depthScale = "\n    gl_Position.z *= " + Float.toString(MC_HAND_DEPTH) + ";\n";
        return source.substring(0, mainEnd) + depthScale + source.substring(mainEnd);
    }

    private static int findMainEnd(String source, int mainStart, String programName) throws IOException {
        int braceDepth = 0;
        for (int index = mainStart; index < source.length(); index++) {
            char character = source.charAt(index);
            if (character == '{') {
                braceDepth++;
            } else if (character == '}' && --braceDepth == 0) {
                return index;
            }
        }
        throw new IOException(programName + " cannot apply MC_HAND_DEPTH because VulkanMod's vertex main function has unmatched braces.");
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

    private static String adaptFragment(String source, String programName, boolean textPipeline,
                                        boolean handPass, boolean shadowSamplerAvailable,
                                        int entityId, int blockEntityId,
                                        int currentRenderedItemId) throws IOException {
        return adaptFragment(source, programName, textPipeline, handPass, shadowSamplerAvailable,
                entityId, blockEntityId, currentRenderedItemId,
                !textPipeline && ShaderPackUniformAdapter.supportsEntityColorProgram(programName));
    }

    private static String adaptFragment(String source, String programName, boolean textPipeline,
                                        boolean handPass, boolean shadowSamplerAvailable,
                                        int entityId, int blockEntityId,
                                        int currentRenderedItemId, boolean entityColorAvailable) throws IOException {
        if (source == null || source.isBlank()) {
            throw new IOException(programName + " fragment shader is empty.");
        }
        if (source.length() > 1024 * 1024) {
            throw new IOException(programName + " fragment shader exceeds the 1 MiB size limit.");
        }

        String adapted = bindEntityIdentifiers(source.replace("\uFEFF", ""), programName,
                entityId, blockEntityId, currentRenderedItemId);
        adapted = VERSION_DIRECTIVE.matcher(adapted).replaceAll("");
        boolean usesNoiseSampler = usesNoiseSampler(adapted);
        List<String> extensions = new ArrayList<>();
        Matcher extensionMatcher = EXTENSION_DIRECTIVE.matcher(adapted);
        while (extensionMatcher.find()) {
            extensions.add(extensionMatcher.group().strip());
        }
        adapted = extensionMatcher.replaceAll("");
        adapted = PRECISION_DECLARATION.matcher(adapted).replaceAll("");
        adapted = ALPHA_TEST_UNIFORM.matcher(adapted).replaceAll("");
        adapted = UnusedSamplerDeclarations.removeUnused(adapted);
        boolean usesShadowSampler = usesShadowSampler(adapted);
        ShadowColorSampler.validate(source, shadowSamplerAvailable, 0);
        ShadowDepthSampler.validate(source, shadowSamplerAvailable, 0);
        if (usesShadowSampler && !shadowSamplerAvailable) {
            throw new IOException(programName + " samples shadowtex0, but this shader pack has no supported shadow pass.");
        }

        Matcher samplerMatcher = SAMPLER_DECLARATION.matcher(adapted);
        StringBuffer withoutSamplers = new StringBuffer();
        boolean usesLightmapSampler = false;
        while (samplerMatcher.find()) {
            String sampler = samplerMatcher.group(2);
            if (!sampler.equals("texture") && !sampler.equals("tex") && !sampler.equals("gtexture") && !sampler.equals("Sampler0")
                    && !sampler.equals("lightmap") && !sampler.equals("noisetex") && !sampler.equals("specular")
                    && !(((sampler.equals("shadowtex0") || sampler.equals("shadowtex1")) || (sampler.equals("shadowcolor0") || sampler.equals("shadowcolor"))) && shadowSamplerAvailable)) {
                throw new IOException(programName + " uses unsupported sampler '" + sampler + "'.");
            }
            String samplerType = samplerMatcher.group(1);
            if (!samplerType.equals("sampler2D")
                    && !((sampler.equals("shadowtex0") || sampler.equals("shadowtex1")) && shadowSamplerAvailable
                    && samplerType.equals("sampler2DShadow"))) {
                throw new IOException(programName + " sampler '" + sampler + "' must use sampler2D.");
            }
            if (sampler.equals("lightmap")) {
                usesLightmapSampler = true;
            }
            samplerMatcher.appendReplacement(withoutSamplers, "");
        }
        samplerMatcher.appendTail(withoutSamplers);
        // VulkanMod exposes its bound shadow depth image through texture slot 3.
        // Lower comparison sampler parameters to raw depth reads and perform the compare here.
        adapted = withoutSamplers.toString().replaceAll("\\bsampler2DShadow\\b", "sampler2D");
        if (!usesLightmapSampler) {
            usesLightmapSampler = Pattern.compile("\\blightmap\\b")
                    .matcher(COMMENTS.matcher(adapted).replaceAll("")).find();
        }
        IrisFogUniformAdapter.AdaptedShader fogUniforms = IrisFogUniformAdapter.extract(adapted, programName);
        adapted = fogUniforms.source();
        ShaderPackUniformAdapter.AdaptedShader shaderPackUniforms =
                ShaderPackUniformAdapter.extract(adapted, programName, entityColorAvailable);
        adapted = shaderPackUniforms.source();
        if (UNSUPPORTED_UNIFORM_DECLARATION.matcher(adapted).find()) {
            throw new IOException(programName + " has an unsupported uniform declaration.");
        }

        Matcher varyingMatcher = VARYING_DECLARATION.matcher(adapted);
        StringBuffer withoutVaryings = new StringBuffer();
        boolean suppliesNormal = false;
        boolean suppliesLegacyColor = false;
        boolean suppliesViewPosition = false;
        boolean suppliesViewNormal = false;
        boolean suppliesSphericalDistance = false;
        boolean suppliesCylindricalDistance = false;
        boolean suppliesLightmapCoord = false;
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
            if (name.equals("color")) {
                suppliesLegacyColor = true;
            }
            if (name.equals("viewPos")) {
                suppliesViewPosition = true;
            }
            if (name.equals("viewNormal")) {
                suppliesViewNormal = true;
            }
            if (name.equals("sphericalVertexDistance")) {
                suppliesSphericalDistance = true;
            }
            if (name.equals("cylindricalVertexDistance")) {
                suppliesCylindricalDistance = true;
            }
            if (name.equals("lmcoord") || name.equals("lightMapCoord") || name.equals("lmCoord")) {
                suppliesLightmapCoord = true;
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
        if (!suppliesViewPosition && VIEW_POSITION_REFERENCE.matcher(uncommented).find()
                && !VIEW_POSITION_VARIABLE_DECLARATION.matcher(uncommented).find()) {
            throw new IOException(programName + " uses 'viewPos' without declaring it as a fragment input.");
        }
        if (!suppliesViewNormal && VIEW_NORMAL_REFERENCE.matcher(uncommented).find()
                && !VIEW_NORMAL_VARIABLE_DECLARATION.matcher(uncommented).find()) {
            throw new IOException(programName + " uses 'viewNormal' without declaring it as a fragment input.");
        }
        if (!suppliesLightmapCoord && LIGHTMAP_REFERENCE.matcher(uncommented).find()) {
            throw new IOException(programName + " uses Iris lightmap coordinates without declaring lmcoord.");
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
                .replaceAll("\\btexture2DShadow\\s*\\(", "veyraEntityShadowCompare(")
                .replaceAll("\\bshadow2D\\s*\\(", "veyraEntityShadowSample(")
                .replaceAll("\\btexture2D\\s*\\(", "veyraEntitySample(")
                .replaceAll("\\btexture\\s*\\(", "veyraEntitySample(")
                .replaceAll("\\bgl_FragColor\\b", outputName)
                .replaceAll("\\btexcoord\\b", "texCoord0")
                .replaceAll("\\btexCoord\\b", "texCoord0")
                .replaceAll("\\blightMapCoord\\b", "lmcoord")
                .replaceAll("\\blmCoord\\b", "lmcoord")
                .replaceAll("\\bglcolor\\b|\\bglColor\\b|\\bgl_Color\\b", "veyraEntityColor");

        String bridge = textPipeline ? """
                in vec4 vertexColor;
                in vec2 texCoord0;
                __VEYRA_COLOR_ALIAS__
                __VEYRA_VIEW_POSITION_INPUT__
                __VEYRA_LIGHTMAP_INPUT__
                __VEYRA_SPHERICAL_DISTANCE__
                __VEYRA_CYLINDRICAL_DISTANCE__
                out vec4 veyraEntityFragColor;
                __VEYRA_FOG_BRIDGE__
                uniform sampler2D Sampler0;
                __VEYRA_SHADOW_SAMPLER__
                __VEYRA_NOISE_SAMPLER__
                __VEYRA_LIGHTMAP_SAMPLER__
                vec4 veyraEntitySample(sampler2D sourceSampler, vec2 sourceUv) {
                    return texture(sourceSampler, sourceUv);
                }
                vec4 veyraEntitySampleLod(sampler2D sourceSampler, vec2 sourceUv, float lod) {
                    return textureLod(sourceSampler, sourceUv, lod);
                }
                vec4 veyraEntitySampleGrad(sampler2D sourceSampler, vec2 sourceUv, vec2 dx, vec2 dy) {
                    return textureGrad(sourceSampler, sourceUv, dx, dy);
                }
                __VEYRA_SHADOW_FUNCTIONS__
                #define texture Sampler0
                #define tex Sampler0
                #define gtexture Sampler0
                #define veyraEntityColor vertexColor
                #ifndef ALPHA_CUTOUT
                #define ALPHA_CUTOUT 0.1
                #endif
                #define alphaTestRef ALPHA_CUTOUT
                """.replace("veyraEntityFragColor;", outputName + ";") : """
                __VEYRA_SPHERICAL_DISTANCE__
                __VEYRA_CYLINDRICAL_DISTANCE__
                __VEYRA_VIEW_POSITION_INPUT__
                #ifdef PER_FACE_LIGHTING
                in vec4 vertexPerFaceColorBack;
                in vec4 vertexPerFaceColorFront;
                #define veyraEntityColor (gl_FrontFacing ? vertexPerFaceColorFront : vertexPerFaceColorBack)
                #else
                in vec4 vertexColor;
                #define veyraEntityColor vertexColor
                #endif
                __VEYRA_COLOR_ALIAS__
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
                __VEYRA_VIEW_NORMAL_INPUT__
                __VEYRA_NORMAL_INPUT__
                __VEYRA_LIGHTMAP_INPUT__
                in vec2 texCoord0;
                out vec4 veyraEntityFragColor;
                __VEYRA_FOG_BRIDGE__
                uniform sampler2D Sampler0;
                __VEYRA_SHADOW_SAMPLER__
                __VEYRA_NOISE_SAMPLER__
                __VEYRA_LIGHTMAP_SAMPLER__
                vec4 veyraEntitySample(sampler2D sourceSampler, vec2 sourceUv) {
                    return texture(sourceSampler, sourceUv);
                }
                vec4 veyraEntitySampleLod(sampler2D sourceSampler, vec2 sourceUv, float lod) {
                    return textureLod(sourceSampler, sourceUv, lod);
                }
                vec4 veyraEntitySampleGrad(sampler2D sourceSampler, vec2 sourceUv, vec2 dx, vec2 dy) {
                    return textureGrad(sourceSampler, sourceUv, dx, dy);
                }
                __VEYRA_SHADOW_FUNCTIONS__
                #define texture Sampler0
                #define tex Sampler0
                #define gtexture Sampler0
                #ifndef ALPHA_CUTOUT
                #define ALPHA_CUTOUT 0.1
                #endif
                #define alphaTestRef ALPHA_CUTOUT
                """.replace("veyraEntityFragColor;", outputName + ";");
        bridge = bridge.replace("__VEYRA_NORMAL_INPUT__", suppliesNormal ? "in vec3 normal;\n" : "")
                .replace("__VEYRA_COLOR_ALIAS__", suppliesLegacyColor ? "#define color veyraEntityColor\n" : "")
                .replace("__VEYRA_VIEW_POSITION_INPUT__", suppliesViewPosition ? "in vec3 viewPos;\n" : "")
                .replace("__VEYRA_VIEW_NORMAL_INPUT__", suppliesViewNormal ? "in vec3 viewNormal;\n" : "")
                .replace("__VEYRA_LIGHTMAP_INPUT__", suppliesLightmapCoord ? "in vec2 lmcoord;\n" : "")
                .replace("__VEYRA_LIGHTMAP_SAMPLER__", usesLightmapSampler
                        ? "uniform sampler2D Sampler2;\n#define lightmap Sampler2\n" : "")
                .replace("__VEYRA_NOISE_SAMPLER__", usesNoiseSampler
                        ? "uniform sampler2D VeyraNoiseSampler;\n#define noisetex VeyraNoiseSampler\n" : "")
                .replace("__VEYRA_SHADOW_SAMPLER__", usesShadowSampler
                        ? "uniform sampler2D Sampler3;\n#define shadowtex0 Sampler3\n" : "")
                .replace("__VEYRA_SHADOW_FUNCTIONS__", usesShadowSampler || ShadowDepthSampler.used(source) ? """
                        float veyraEntityShadowCompare(sampler2D sourceSampler, vec3 sourceCoord) {
                            return step(sourceCoord.z - 0.0005, texture(sourceSampler, sourceCoord.xy).r);
                        }
                        vec4 veyraEntityShadowSample(sampler2D sourceSampler, vec3 sourceCoord) {
                            return vec4(veyraEntityShadowCompare(sourceSampler, sourceCoord));
                        }
                        """ : "")
                .replace("__VEYRA_SPHERICAL_DISTANCE__",
                        suppliesSphericalDistance ? "in float sphericalVertexDistance;\n" : "")
                .replace("__VEYRA_CYLINDRICAL_DISTANCE__",
                        suppliesCylindricalDistance ? "in float cylindricalVertexDistance;\n" : "")
                .replace("__VEYRA_FOG_BRIDGE__", fogUniforms.bridge());
        if (TerrainShaderAdapter.usesSpecularSampler(source)) {
            bridge = "uniform sampler2D VeyraSpecularSampler;\n#define specular VeyraSpecularSampler\n" + bridge;
        }
        if (ShadowDepthSampler.used(source)) {
            bridge = "uniform sampler2D " + ShadowDepthSampler.NAME + ";\n#define shadowtex1 " + ShadowDepthSampler.NAME + "\n" + bridge;
        }
        if (ShadowColorSampler.used(source)) {
            bridge = "uniform sampler2D " + ShadowColorSampler.NAME + ";\n#define shadowcolor0 " + ShadowColorSampler.NAME + "\n#define shadowcolor " + ShadowColorSampler.NAME + "\n" + bridge;
        }
        StringBuilder result = new StringBuilder("#version 460\n");
        for (String extension : extensions) {
            result.append(extension).append('\n');
        }
        if (handPass) {
            result.append("#define MC_HAND_DEPTH ").append(Float.toString(MC_HAND_DEPTH)).append('\n');
        }
        result.append(bridge);
        result.append(shaderPackUniforms.declarations());
        result.append(shaderPackUniforms.macros());
        return result.append(adapted).toString();
    }

    private static String bindEntityIdentifiers(String source, String programName,
                                                int entityId, int blockEntityId,
                                                int currentRenderedItemId) {
        Matcher matcher = ENTITY_IDENTIFIER_UNIFORM.matcher(source);
        StringBuffer bound = new StringBuffer();
        while (matcher.find()) {
            String name = matcher.group(1);
            if (!ShaderPackUniformAdapter.isBoundEntityIdentifier(programName, "int", name)) {
                matcher.appendReplacement(bound, Matcher.quoteReplacement(matcher.group()));
                continue;
            }
            int value = switch (name) {
                case "entityId" -> entityId;
                case "blockEntityId" -> blockEntityId;
                case "currentRenderedItemId" -> currentRenderedItemId;
                default -> throw new IllegalStateException("Unexpected entity identifier " + name);
            };
            matcher.appendReplacement(bound, Matcher.quoteReplacement(
                    "const int " + name + " = " + value + ";\n"));
        }
        matcher.appendTail(bound);
        return bound.toString();
    }

    public static boolean usesNoiseSampler(String source) {
        return NOISETEX_REFERENCE.matcher(COMMENTS.matcher(source == null ? "" : source).replaceAll(""))
                .find();
    }

    public static boolean usesShadowSampler(String source) {
        return SHADOWTEX_REFERENCE.matcher(COMMENTS.matcher(source == null ? "" : source).replaceAll(""))
                .find();
    }

    private record Varying(String type) {
    }
}
