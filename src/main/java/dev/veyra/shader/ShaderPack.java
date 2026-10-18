package dev.veyra.shader;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.lwjgl.vulkan.VK10;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public record ShaderPack(Path path, String name, String shaderDirectory,
                         boolean hasPostProcessing, Set<String> terrainPrograms,
                         boolean hasCloudFragment, Set<String> entityPrograms, Set<String> particlePrograms,
                         Set<String> weatherPrograms,
                         boolean hasBeaconBeamFragment, boolean hasLightningFragment,
                         boolean hasBasicFragment, boolean hasLineFragment, boolean hasGlintFragment,
                         boolean hasSkyBasicFragment, boolean hasSkyTexturedFragment,
                         boolean hasTexturedFragment, boolean hasTexturedLitFragment,
                         boolean hasHandFragment, boolean hasHandWaterFragment,
                         boolean hasDamagedBlockFragment, Set<String> blockPrograms, String issue) {
    private static final int MAX_SHADER_BYTES = 1024 * 1024;
    private static final int MAX_EXPANDED_SHADER_CHARS = 4 * 1024 * 1024;
    private static final int MAX_INCLUDE_DEPTH = 32;
    private static final int MAX_PASS_SUFFIX = 99;
    private static final List<String> MULTI_PASS_PROGRAMS = List.of("prepare", "deferred", "composite");
    private static final List<String> SHADOW_PROGRAMS = List.of(
            "shadow", "shadow_solid", "shadow_cutout", "shadow_water", "shadow_entities", "shadow_block");
    private static final List<String> TERRAIN_PROGRAMS = List.of(
            "gbuffers_terrain", "gbuffers_terrain_solid", "gbuffers_terrain_cutout", "gbuffers_water");
    private static final List<String> ENTITY_PROGRAMS = List.of(
            "gbuffers_entities", "gbuffers_entities_translucent", "gbuffers_spidereyes");
    private static final List<String> PARTICLE_PROGRAMS = List.of(
            "gbuffers_particles", "gbuffers_particles_translucent");
    private static final List<String> WEATHER_PROGRAMS = List.of("gbuffers_weather");
    private static final List<String> BLOCK_PROGRAMS = List.of("gbuffers_block", "gbuffers_block_translucent");
    private static final String BEACON_BEAM_PROGRAM = "gbuffers_beaconbeam";
    private static final String LIGHTNING_PROGRAM = "gbuffers_lightning";
    private static final String BASIC_PROGRAM = "gbuffers_basic";
    private static final String LINE_PROGRAM = "gbuffers_line";
    private static final String GLINT_PROGRAM = "gbuffers_armor_glint";
    private static final String SKY_BASIC_PROGRAM = "gbuffers_skybasic";
    private static final String SKY_TEXTURED_PROGRAM = "gbuffers_skytextured";
    private static final String TEXTURED_PROGRAM = "gbuffers_textured";
    private static final String TEXTURED_LIT_PROGRAM = "gbuffers_textured_lit";
    private static final String HAND_PROGRAM = "gbuffers_hand";
    private static final String HAND_WATER_PROGRAM = "gbuffers_hand_water";
    private static final String DAMAGED_BLOCK_PROGRAM = "gbuffers_damagedblock";
    private static final int MAX_COLOR_TARGET_INDEX = 15;
    private static final Pattern INCLUDE_DIRECTIVE = Pattern.compile(
            "(?m)^[\\t ]*#include[\\t ]+(?:\"([^\"]+)\"|<([^>]+)>)[\\t ]*(?:\\r?\\n|$)");
    private static final Pattern GLSL_COMMENTS = Pattern.compile("(?s)/\\*.*?\\*/|//[^\\r\\n]*");
    private static final Pattern COLOR_TARGET_CLEAR = Pattern.compile(
            "(?m)\\bconst\\s+bool\\s+colortex(\\d+)Clear\\s*=\\s*(true|false)\\s*;");
    private static final Pattern COLOR_TARGET_CLEAR_COLOR = Pattern.compile(
            "(?is)\\bconst\\s+vec4\\s+colortex(\\d+)ClearColor\\s*=\\s*vec4\\s*\\(([^)]*)\\)\\s*;");
    private static final Pattern COLOR_TARGET_FORMAT = Pattern.compile(
            "(?m)\\bconst\\s+int\\s+colortex(\\d+)Format\\s*=\\s*([A-Za-z_][A-Za-z0-9_]*)\\s*;");
    private static final Pattern COLOR_TARGET_FORMAT_BLOCK_COMMENT = Pattern.compile(
            "(?is)/\\*\\s*const\\s+int\\s+colortex(\\d+)Format\\s*=\\s*([A-Za-z_][A-Za-z0-9_]*)\\s*;\\s*\\*/");
    private static final Pattern COLOR_TARGET_SIZE_PROPERTY = Pattern.compile("^size\\.buffer\\.colortex(\\d+)$");
    private static final Map<String, Integer> COLOR_TARGET_FORMATS = Map.of(
            "R8", VK10.VK_FORMAT_R8_UNORM,
            "RGBA8", VK10.VK_FORMAT_R8G8B8A8_UNORM,
            "R16F", VK10.VK_FORMAT_R16_SFLOAT,
            "RGBA16F", VK10.VK_FORMAT_R16G16B16A16_SFLOAT,
            "R32F", VK10.VK_FORMAT_R32_SFLOAT);
    private static final Pattern FLOAT_LITERAL = Pattern.compile(
            "([+-]?(?:(?:\\d+(?:\\.\\d*)?)|(?:\\.\\d+))(?:[eE][+-]?\\d+)?)[fF]?");
    private static final Pattern SHADOW_SETTING = Pattern.compile(
            "\\bconst\\s+(int|float)\\s+(shadowMapResolution|shadowDistance|shadowIntervalSize|sunPathRotation|shadowDistanceRenderMul|entityShadowDistanceMul)\\s*=\\s*([^;]+);");
    private static final Pattern INTEGER_LITERAL = Pattern.compile("[+-]?\\d+");
    private static final String DEFAULT_SHADOW_FRAGMENT = """
            #version 120
            varying vec2 texcoord;
            varying vec4 glcolor;
            void main() {
                float alpha = texture2D(texture, texcoord).a * glcolor.a;
                if (alpha < alphaTestRef) {
                    discard;
                }
                gl_FragColor = vec4(1.0);
            }
            """;

    public record Program(String name, String source) {
    }

    private record Inspection(String shaderDirectory, boolean hasPostProcessing,
                              Set<String> terrainPrograms, boolean hasCloudFragment,
                              Set<String> entityPrograms, Set<String> particlePrograms, Set<String> weatherPrograms,
                              boolean hasBeaconBeamFragment, boolean hasLightningFragment,
                              boolean hasBasicFragment, boolean hasLineFragment, boolean hasGlintFragment,
                              boolean hasSkyBasicFragment, boolean hasSkyTexturedFragment,
                              boolean hasTexturedFragment, boolean hasTexturedLitFragment,
                              boolean hasHandFragment, boolean hasHandWaterFragment,
                              boolean hasDamagedBlockFragment, Set<String> blockPrograms) {
    }

    public static ShaderPack inspect(Path path) {
        String name = displayName(path);
        try {
            Inspection inspection = Files.isDirectory(path) ? inspectDirectory(path) : inspectArchive(path);
            return new ShaderPack(path, name,
                    inspection == null ? null : inspection.shaderDirectory(),
                    inspection != null && inspection.hasPostProcessing(),
                    inspection == null ? Set.of() : inspection.terrainPrograms(),
                    inspection != null && inspection.hasCloudFragment(),
                    inspection == null ? Set.of() : inspection.entityPrograms(),
                    inspection == null ? Set.of() : inspection.particlePrograms(),
                    inspection == null ? Set.of() : inspection.weatherPrograms(),
                    inspection != null && inspection.hasBeaconBeamFragment(),
                    inspection != null && inspection.hasLightningFragment(),
                    inspection != null && inspection.hasBasicFragment(),
                    inspection != null && inspection.hasLineFragment(),
                    inspection != null && inspection.hasGlintFragment(),
                    inspection != null && inspection.hasSkyBasicFragment(),
                    inspection != null && inspection.hasSkyTexturedFragment(),
                    inspection != null && inspection.hasTexturedFragment(),
                    inspection != null && inspection.hasTexturedLitFragment(),
                    inspection != null && inspection.hasHandFragment(),
                    inspection != null && inspection.hasHandWaterFragment(),
                    inspection != null && inspection.hasDamagedBlockFragment(),
                    inspection == null ? Set.of() : inspection.blockPrograms(),
                    inspection == null
                            ? "No supported terrain, clouds, sky, entities, block entities, hand, damaged-block, particles, weather, beacon beam, lightning, basic, line, glint, textured, prepare, deferred, composite, or final fragment programs were found."
                            : "Supported terrain, clouds, sky, entities, block entities, hand, damaged-block, particles, weather, beacon beam, lightning, basic, line, glint, textured, and/or post-processing fragment programs are available.");
        } catch (IOException | RuntimeException exception) {
            return new ShaderPack(path, name, null, false, Set.of(), false, Set.of(), Set.of(), Set.of(), false, false,
                    false, false, false, false, false, false, false, false, false, false,
                    Set.of(),
                    "Could not read pack: " + exception.getMessage());
        }
    }

    public boolean supportsShaderPrograms() {
        return shaderDirectory != null;
    }

    public boolean hasTerrainFragment() {
        return !terrainPrograms.isEmpty();
    }

    public boolean hasShadowFragment() {
        try {
            for (String programName : SHADOW_PROGRAMS) {
                if (readShader(programName + ".fsh").isPresent()) {
                    return true;
                }
            }
            return false;
        } catch (IOException exception) {
            return false;
        }
    }

    public Optional<String> readShadowFragment() throws IOException {
        return readShadowFragment("shadow");
    }

    public Optional<String> readShadowFragment(String programName) throws IOException {
        if (!supportsShaderPrograms()) {
            return Optional.empty();
        }
        if (!SHADOW_PROGRAMS.contains(programName)) {
            throw new IOException("Unsupported Iris shadow program '" + programName + "'.");
        }
        String fileName = programName + ".fsh";
        Optional<String> source = readShader(fileName);
        if (source.isEmpty() && !programName.equals("shadow")) {
            fileName = "shadow.fsh";
            source = readShader(fileName);
        }
        if (source.isEmpty()) {
            return hasShadowFragment() ? Optional.of(DEFAULT_SHADOW_FRAGMENT) : Optional.empty();
        }
        return Optional.of(expandIncludes(fileName, source.get(), new LinkedHashSet<>(), 0));
    }

    public boolean hasShadowWaterFragment() {
        try {
            return readShader("shadow_water.fsh").isPresent();
        } catch (IOException exception) {
            return false;
        }
    }

    public Optional<String> readShadowWaterFragment() throws IOException {
        return readShadowFragment("shadow_water");
    }

    public boolean hasDamagedBlockPipeline() {
        return hasDamagedBlockFragment || terrainPrograms.contains("gbuffers_terrain");
    }

    public Optional<String> readDamagedBlockFragment() throws IOException {
        return hasDamagedBlockFragment
                ? readColorDistanceFragment(DAMAGED_BLOCK_PROGRAM, true)
                : readTerrainFragment("gbuffers_terrain");
    }

    public List<Program> readPrograms() throws IOException {
        if (!supportsShaderPrograms()) {
            throw new IOException(issue);
        }
        if (!hasPostProcessing) {
            return List.of();
        }

        List<Program> programs = new ArrayList<>();
        Set<String> availableFiles = listShaderFiles();
        for (String prefix : MULTI_PASS_PROGRAMS) {
            for (int suffix = 0; suffix <= MAX_PASS_SUFFIX; suffix++) {
                String programName = suffix == 0 ? prefix : prefix + suffix;
                String fileName = programName + ".fsh";
                if (availableFiles.contains(fileName)) {
                    Optional<String> source = readShader(fileName);
                    if (source.isPresent()) {
                        programs.add(new Program(programName, expandIncludes(
                                fileName, source.get(), new LinkedHashSet<>(), 0)));
                    }
                }
            }
        }
        if (availableFiles.contains("final.fsh")) {
            Optional<String> source = readShader("final.fsh");
            if (source.isPresent()) {
                programs.add(new Program("final", expandIncludes(
                        "final.fsh", source.get(), new LinkedHashSet<>(), 0)));
            }
        }

        if (programs.isEmpty()) {
            throw new IOException("The pack no longer contains a supported fragment pass.");
        }
        return List.copyOf(programs);
    }

    public Map<Integer, ColorTargetSettings> readColorTargetSettings(
            int viewportWidth, int viewportHeight, int maxTextureSize) throws IOException {
        Map<Integer, MutableColorTargetSettings> parsed = new HashMap<>();
        for (String fileName : listShaderFiles().stream().sorted().toList()) {
            if (!isGlslSource(fileName)) {
                continue;
            }
            Optional<String> source = readShader(fileName);
            if (source.isEmpty()) {
                continue;
            }
            String expanded = expandIncludes(fileName, source.get(), new LinkedHashSet<>(), 0);
            parseColorTargetSettings(GLSL_COMMENTS.matcher(expanded).replaceAll(""), parsed, fileName);
            parseCommentedColorTargetFormats(expanded, parsed, fileName);
        }

        Optional<String> propertiesSource = readShader("shaders.properties");
        if (propertiesSource.isPresent()) {
            Properties properties = new Properties();
            try (StringReader reader = new StringReader(propertiesSource.get())) {
                properties.load(reader);
            } catch (IllegalArgumentException exception) {
                throw new IOException("Could not parse shaders.properties: " + exception.getMessage(), exception);
            }
            for (String key : properties.stringPropertyNames()) {
                Matcher sizeMatcher = COLOR_TARGET_SIZE_PROPERTY.matcher(key.trim());
                if (!sizeMatcher.matches()) {
                    continue;
                }
                int index = Integer.parseInt(sizeMatcher.group(1));
                if (index < 1 || index > MAX_COLOR_TARGET_INDEX) {
                    continue;
                }
                int[] size = parseColorTargetSize(properties.getProperty(key), index,
                        viewportWidth, viewportHeight, maxTextureSize);
                MutableColorTargetSettings target = parsed.computeIfAbsent(index,
                        ignored -> new MutableColorTargetSettings());
                target.width = size[0];
                target.height = size[1];
            }
        }

        Map<Integer, ColorTargetSettings> settings = new HashMap<>();
        parsed.forEach((index, value) -> {
            ColorTargetSettings defaults = ColorTargetSettings.defaults(index, viewportWidth, viewportHeight);
            settings.put(index, new ColorTargetSettings(
                    value.clearEachFrame == null || value.clearEachFrame,
                    value.clearColor == null ? defaults.clearColor() : value.clearColor,
                    value.width == null ? defaults.width() : value.width,
                    value.height == null ? defaults.height() : value.height,
                    value.format == null ? defaults.format() : value.format));
        });
        for (Map.Entry<Integer, ColorTargetSettings> entry : settings.entrySet()) {
            ColorTargetSettings target = entry.getValue();
            long byteSize = (long) target.width() * target.height() * ColorTargetSettings.formatSize(target.format());
            if (byteSize > Integer.MAX_VALUE) {
                throw new IOException("colortex" + entry.getKey()
                        + " is too large for VulkanMod's texture allocation limits.");
            }
        }
        return Map.copyOf(settings);
    }

    public ShadowSettings readShadowSettings() throws IOException {
        Map<String, String> values = new HashMap<>();
        for (String fileName : listShaderSourceFiles()) {
            Optional<String> source = readShader(fileName);
            if (source.isEmpty()) {
                continue;
            }
            String uncommented = GLSL_COMMENTS.matcher(source.get()).replaceAll("");
            Matcher matcher = SHADOW_SETTING.matcher(uncommented);
            while (matcher.find()) {
                values.putIfAbsent(matcher.group(2), matcher.group(3).trim());
            }
        }

        int resolution = values.containsKey("shadowMapResolution")
                ? parseShadowResolution(values.get("shadowMapResolution"))
                : ShadowSettings.DEFAULT_RESOLUTION;
        float distance = parseShadowFloat(values, "shadowDistance", ShadowSettings.DEFAULT_DISTANCE);
        float intervalSize = parseShadowFloat(values, "shadowIntervalSize", ShadowSettings.DEFAULT_INTERVAL_SIZE);
        float sunPathRotation = parseShadowFloat(values, "sunPathRotation", ShadowSettings.DEFAULT_SUN_PATH_ROTATION);
        float distanceRenderMultiplier = parseShadowFloat(values, "shadowDistanceRenderMul",
                ShadowSettings.DEFAULT_DISTANCE_RENDER_MULTIPLIER);
        float entityShadowDistanceMultiplier = parseShadowFloat(values, "entityShadowDistanceMul",
                ShadowSettings.DEFAULT_ENTITY_SHADOW_DISTANCE_MULTIPLIER);
        boolean shadowTranslucent = readBooleanProperty("shadowTranslucent", false);
        try {
            return new ShadowSettings(resolution, distance, intervalSize, sunPathRotation, distanceRenderMultiplier,
                    entityShadowDistanceMultiplier, shadowTranslucent);
        } catch (IllegalArgumentException exception) {
            throw new IOException("Invalid Iris shadow setting: " + exception.getMessage(), exception);
        }
    }

    private boolean readBooleanProperty(String propertyName, boolean defaultValue) throws IOException {
        Optional<String> source = readShader("shaders.properties");
        if (source.isEmpty()) {
            return defaultValue;
        }
        Properties properties = new Properties();
        try (StringReader reader = new StringReader(source.get())) {
            properties.load(reader);
        } catch (IllegalArgumentException exception) {
            throw new IOException("Could not parse shaders.properties: " + exception.getMessage(), exception);
        }
        String value = properties.getProperty(propertyName);
        if (value == null) {
            return defaultValue;
        }
        String normalized = value.trim();
        if (normalized.equalsIgnoreCase("true")) {
            return true;
        }
        if (normalized.equalsIgnoreCase("false")) {
            return false;
        }
        throw new IOException(propertyName + " in shaders.properties must be true or false.");
    }

    private static int parseShadowResolution(String value) throws IOException {
        if (!INTEGER_LITERAL.matcher(value).matches()) {
            throw new IOException("shadowMapResolution must be an integer literal.");
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            throw new IOException("shadowMapResolution is outside the supported integer range.", exception);
        }
    }

    private static float parseShadowFloat(Map<String, String> values, String name, float defaultValue)
            throws IOException {
        String value = values.get(name);
        if (value == null) {
            return defaultValue;
        }
        Matcher matcher = FLOAT_LITERAL.matcher(value);
        if (!matcher.matches()) {
            throw new IOException(name + " must be a numeric GLSL literal.");
        }
        float parsed = Float.parseFloat(matcher.group(1));
        if (!Float.isFinite(parsed)) {
            throw new IOException(name + " must be finite.");
        }
        return parsed;
    }

    private static void parseColorTargetSettings(String source,
                                                 Map<Integer, MutableColorTargetSettings> settings,
                                                 String sourceFile) throws IOException {
        Matcher clearMatcher = COLOR_TARGET_CLEAR.matcher(source);
        while (clearMatcher.find()) {
            int index = Integer.parseInt(clearMatcher.group(1));
            if (index < 1 || index > MAX_COLOR_TARGET_INDEX) {
                continue;
            }
            boolean clear = Boolean.parseBoolean(clearMatcher.group(2));
            MutableColorTargetSettings target = settings.computeIfAbsent(index,
                    ignored -> new MutableColorTargetSettings());
            if (target.clearEachFrame != null && target.clearEachFrame != clear) {
                throw new IOException("Shader pack declares conflicting colortex" + index + "Clear values in "
                        + sourceFile + ".");
            }
            target.clearEachFrame = clear;
        }

        Matcher colorMatcher = COLOR_TARGET_CLEAR_COLOR.matcher(source);
        while (colorMatcher.find()) {
            int index = Integer.parseInt(colorMatcher.group(1));
            if (index < 1 || index > MAX_COLOR_TARGET_INDEX) {
                continue;
            }
            int color = parseClearColor(colorMatcher.group(2), index, sourceFile);
            MutableColorTargetSettings target = settings.computeIfAbsent(index,
                    ignored -> new MutableColorTargetSettings());
            if (target.clearColor != null && target.clearColor != color) {
                throw new IOException("Shader pack declares conflicting colortex" + index + "ClearColor values in "
                        + sourceFile + ".");
            }
            target.clearColor = color;
        }

        Matcher formatMatcher = COLOR_TARGET_FORMAT.matcher(source);
        while (formatMatcher.find()) {
            setColorTargetFormat(settings, Integer.parseInt(formatMatcher.group(1)),
                    formatMatcher.group(2), sourceFile);
        }
    }

    private static void parseCommentedColorTargetFormats(String source,
                                                        Map<Integer, MutableColorTargetSettings> settings,
                                                        String sourceFile) throws IOException {
        String withoutLineComments = source.replaceAll("(?m)//[^\\r\\n]*", "");
        Matcher matcher = COLOR_TARGET_FORMAT_BLOCK_COMMENT.matcher(withoutLineComments);
        while (matcher.find()) {
            setColorTargetFormat(settings, Integer.parseInt(matcher.group(1)), matcher.group(2), sourceFile);
        }
    }

    private static void setColorTargetFormat(Map<Integer, MutableColorTargetSettings> settings,
                                             int index, String declaredFormat, String sourceFile) throws IOException {
        if (index < 1 || index > MAX_COLOR_TARGET_INDEX) {
            return;
        }
        String formatName = declaredFormat.toUpperCase(java.util.Locale.ROOT);
        Integer format = COLOR_TARGET_FORMATS.get(formatName);
        if (format == null) {
            throw new IOException("Unsupported colortex" + index + "Format '" + formatName
                    + "' in " + sourceFile + ". Supported formats: "
                    + String.join(", ", COLOR_TARGET_FORMATS.keySet().stream().sorted().toList()) + ".");
        }
        MutableColorTargetSettings target = settings.computeIfAbsent(index,
                ignored -> new MutableColorTargetSettings());
        if (target.format != null && target.format != format) {
            throw new IOException("Shader pack declares conflicting colortex" + index + "Format values in "
                    + sourceFile + ".");
        }
        target.format = format;
    }

    private static int[] parseColorTargetSize(String value, int index, int viewportWidth,
                                              int viewportHeight, int maxTextureSize) throws IOException {
        String[] dimensions = value.trim().split("\\s+");
        if (dimensions.length != 2) {
            throw new IOException("size.buffer.colortex" + index + " in shaders.properties must contain width and height.");
        }
        return new int[]{
                parseColorTargetDimension(dimensions[0], viewportWidth, maxTextureSize, index, "width"),
                parseColorTargetDimension(dimensions[1], viewportHeight, maxTextureSize, index, "height")};
    }

    private static int parseColorTargetDimension(String value, int viewportDimension, int maxTextureSize,
                                                 int index, String axis) throws IOException {
        try {
            boolean relative = value.contains(".") || value.contains("e") || value.contains("E");
            double dimension = relative
                    ? Math.max(1L, Math.round(Double.parseDouble(value) * viewportDimension))
                    : Long.parseLong(value);
            if (!Double.isFinite(dimension) || dimension < 1 || dimension > maxTextureSize) {
                throw new IOException("size.buffer.colortex" + index + " " + axis
                        + " must resolve to a positive value no larger than " + maxTextureSize + ".");
            }
            return (int) dimension;
        } catch (NumberFormatException exception) {
            throw new IOException("size.buffer.colortex" + index + " has an invalid " + axis + " value '" + value + "'.",
                    exception);
        }
    }

    private static int parseClearColor(String componentsText, int index, String sourceFile) throws IOException {
        String[] components = componentsText.split(",");
        if (components.length != 1 && components.length != 4) {
            throw new IOException("colortex" + index + "ClearColor in " + sourceFile
                    + " must use vec4(value) or vec4(red, green, blue, alpha).");
        }

        int[] channels = new int[4];
        for (int component = 0; component < components.length; component++) {
            Matcher matcher = FLOAT_LITERAL.matcher(components[component].trim());
            if (!matcher.matches()) {
                throw new IOException("colortex" + index + "ClearColor in " + sourceFile
                        + " must use numeric float literals.");
            }
            float value = Float.parseFloat(matcher.group(1));
            if (!Float.isFinite(value)) {
                throw new IOException("colortex" + index + "ClearColor in " + sourceFile
                        + " contains a non-finite value.");
            }
            int channel = Math.round(Math.clamp(value, 0.0F, 1.0F) * 255.0F);
            if (components.length == 1) {
                java.util.Arrays.fill(channels, channel);
            } else {
                channels[component] = channel;
            }
        }
        return channels[3] << 24 | channels[0] << 16 | channels[1] << 8 | channels[2];
    }

    private static boolean isGlslSource(String fileName) {
        String lower = fileName.toLowerCase(java.util.Locale.ROOT);
        return lower.endsWith(".fsh") || lower.endsWith(".vsh") || lower.endsWith(".gsh")
                || lower.endsWith(".glsl") || lower.endsWith(".csh");
    }

    private static final class MutableColorTargetSettings {
        private Boolean clearEachFrame;
        private Integer clearColor;
        private Integer width;
        private Integer height;
        private Integer format;
    }

    public Optional<String> readTerrainFragment() throws IOException {
        return readTerrainFragment("gbuffers_terrain");
    }

    public Optional<String> readTerrainFragment(String programName) throws IOException {
        if (!supportsShaderPrograms() || !terrainPrograms.contains(programName)) {
            return Optional.empty();
        }
        String fileName = programName + ".fsh";
        Optional<String> source = readShader(fileName);
        if (source.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(expandIncludes(fileName, source.get(), new LinkedHashSet<>(), 0));
    }

    public Optional<String> readCloudFragment() throws IOException {
        if (!supportsShaderPrograms() || !hasCloudFragment) {
            return Optional.empty();
        }
        String fileName = "gbuffers_clouds.fsh";
        Optional<String> source = readShader(fileName);
        if (source.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(expandIncludes(fileName, source.get(), new LinkedHashSet<>(), 0));
    }

    public boolean hasEntityFragment() {
        return !entityPrograms.isEmpty() || hasTexturedLitFragment || hasTexturedFragment;
    }

    public boolean supportsHandPipeline() {
        return hasHandFragment || hasHandWaterFragment || hasTexturedLitFragment || hasTexturedFragment;
    }

    public boolean supportsBlockEntityPipeline() {
        return !blockPrograms.isEmpty() || terrainPrograms.contains("gbuffers_terrain")
                || hasTexturedLitFragment || hasTexturedFragment;
    }

    public Optional<String> readBlockFragment(String programName) throws IOException {
        if (!supportsShaderPrograms()) {
            return Optional.empty();
        }
        boolean supported = blockPrograms.contains(programName)
                || terrainPrograms.contains(programName)
                || TEXTURED_LIT_PROGRAM.equals(programName) && hasTexturedLitFragment
                || TEXTURED_PROGRAM.equals(programName) && hasTexturedFragment;
        if (!supported) {
            return Optional.empty();
        }
        String fileName = programName + ".fsh";
        Optional<String> source = readShader(fileName);
        if (source.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(expandIncludes(fileName, source.get(), new LinkedHashSet<>(), 0));
    }

    public Optional<String> readHandFragment(String programName) throws IOException {
        if (!supportsShaderPrograms()
                || (!HAND_PROGRAM.equals(programName) && !HAND_WATER_PROGRAM.equals(programName)
                && !hasGeometryFallbackFragment(programName))) {
            return Optional.empty();
        }
        String fileName = programName + ".fsh";
        Optional<String> source = readShader(fileName);
        if (source.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(expandIncludes(fileName, source.get(), new LinkedHashSet<>(), 0));
    }

    public Optional<String> readEntityFragment(String programName) throws IOException {
        if (!supportsShaderPrograms()
                || (!entityPrograms.contains(programName) && !hasGeometryFallbackFragment(programName))) {
            return Optional.empty();
        }
        String fileName = programName + ".fsh";
        Optional<String> source = readShader(fileName);
        if (source.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(expandIncludes(fileName, source.get(), new LinkedHashSet<>(), 0));
    }

    public boolean hasParticleFragment() {
        return !particlePrograms.isEmpty() || hasTexturedLitFragment || hasTexturedFragment;
    }

    public Optional<String> readParticleFragment(String programName) throws IOException {
        if (!supportsShaderPrograms()
                || (!particlePrograms.contains(programName) && !hasGeometryFallbackFragment(programName))) {
            return Optional.empty();
        }
        String fileName = programName + ".fsh";
        Optional<String> source = readShader(fileName);
        if (source.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(expandIncludes(fileName, source.get(), new LinkedHashSet<>(), 0));
    }

    public boolean hasWeatherFragment() {
        return !weatherPrograms.isEmpty() || hasTexturedLitFragment || hasTexturedFragment;
    }

    public Optional<String> readWeatherFragment(String programName) throws IOException {
        if (!supportsShaderPrograms()
                || (!weatherPrograms.contains(programName) && !hasGeometryFallbackFragment(programName))) {
            return Optional.empty();
        }
        String fileName = programName + ".fsh";
        Optional<String> source = readShader(fileName);
        if (source.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(expandIncludes(fileName, source.get(), new LinkedHashSet<>(), 0));
    }

    public Optional<String> readBeaconBeamFragment() throws IOException {
        if (!supportsShaderPrograms() || !hasBeaconBeamFragment) {
            return Optional.empty();
        }
        String fileName = BEACON_BEAM_PROGRAM + ".fsh";
        Optional<String> source = readShader(fileName);
        if (source.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(expandIncludes(fileName, source.get(), new LinkedHashSet<>(), 0));
    }

    public Optional<String> readLightningFragment() throws IOException {
        if (!supportsShaderPrograms() || !hasLightningFragment) {
            return Optional.empty();
        }
        String fileName = LIGHTNING_PROGRAM + ".fsh";
        Optional<String> source = readShader(fileName);
        if (source.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(expandIncludes(fileName, source.get(), new LinkedHashSet<>(), 0));
    }

    public Optional<String> readBasicFragment() throws IOException {
        return readColorDistanceFragment(BASIC_PROGRAM, hasBasicFragment);
    }

    public Optional<String> readLineFragment() throws IOException {
        return readColorDistanceFragment(LINE_PROGRAM, hasLineFragment);
    }

    public Optional<String> readGlintFragment() throws IOException {
        return hasGlintFragment
                ? readColorDistanceFragment(GLINT_PROGRAM, true)
                : readTexturedFragment();
    }

    public Optional<String> readSkyBasicFragment() throws IOException {
        return hasSkyBasicFragment
                ? readColorDistanceFragment(SKY_BASIC_PROGRAM, true)
                : readBasicFragment();
    }

    public Optional<String> readSkyTexturedFragment() throws IOException {
        return hasSkyTexturedFragment
                ? readColorDistanceFragment(SKY_TEXTURED_PROGRAM, true)
                : readTexturedFragment();
    }

    public Optional<String> readTexturedFragment() throws IOException {
        return readColorDistanceFragment(TEXTURED_PROGRAM, hasTexturedFragment);
    }

    private boolean hasGeometryFallbackFragment(String programName) {
        return (TEXTURED_LIT_PROGRAM.equals(programName) && hasTexturedLitFragment)
                || (TEXTURED_PROGRAM.equals(programName) && hasTexturedFragment);
    }

    private Optional<String> readColorDistanceFragment(String programName, boolean present) throws IOException {
        if (!supportsShaderPrograms() || !present) {
            return Optional.empty();
        }
        String fileName = programName + ".fsh";
        Optional<String> source = readShader(fileName);
        if (source.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(expandIncludes(fileName, source.get(), new LinkedHashSet<>(), 0));
    }

    private Set<String> listShaderFiles() throws IOException {
        if (Files.isDirectory(path)) {
            Path shaders = path.resolve(shaderDirectory);
            try (var entries = Files.list(shaders)) {
                return entries.filter(Files::isRegularFile)
                        .map(entry -> entry.getFileName().toString())
                        .collect(java.util.stream.Collectors.toUnmodifiableSet());
            }
        }

        String prefix = shaderDirectory + "/";
        Set<String> files = new HashSet<>();
        try (ZipFile zip = new ZipFile(path.toFile())) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (!entry.isDirectory() && entry.getName().startsWith(prefix)) {
                    String fileName = entry.getName().substring(prefix.length());
                    if (!fileName.contains("/")) {
                        files.add(fileName);
                    }
                }
            }
        }
        return Set.copyOf(files);
    }

    private List<String> listShaderSourceFiles() throws IOException {
        if (Files.isDirectory(path)) {
            Path shaders = path.resolve(shaderDirectory);
            try (var entries = Files.walk(shaders)) {
                return entries.filter(Files::isRegularFile)
                        .map(shaders::relativize)
                        .map(relative -> relative.toString().replace('\\', '/'))
                        .filter(ShaderPack::isGlslSource)
                        .sorted()
                        .toList();
            }
        }

        String prefix = shaderDirectory + "/";
        try (ZipFile zip = new ZipFile(path.toFile())) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            List<String> files = new ArrayList<>();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (entry.isDirectory() || !entry.getName().startsWith(prefix)) {
                    continue;
                }
                String fileName = entry.getName().substring(prefix.length());
                if (isGlslSource(fileName)) {
                    files.add(fileName);
                }
            }
            return files.stream().sorted().toList();
        }
    }

    private Optional<String> readShader(String fileName) throws IOException {
        byte[] bytes;
        if (Files.isDirectory(path)) {
            Path packRoot = path.toRealPath();
            Path shaderRoot = packRoot.resolve(shaderDirectory).normalize();
            if (!shaderRoot.startsWith(packRoot)) {
                throw new IOException("Shader directory escapes the pack directory.");
            }
            Path realShaderRoot = shaderRoot.toRealPath();
            if (!realShaderRoot.startsWith(packRoot)) {
                throw new IOException("Shader directory resolves outside the pack directory.");
            }
            Path shader = realShaderRoot.resolve(fileName).normalize();
            if (!shader.startsWith(realShaderRoot)) {
                throw new IOException("Shader path escapes the shaders directory.");
            }
            if (!Files.exists(shader)) {
                return Optional.empty();
            }
            Path realShader = shader.toRealPath();
            if (!realShader.startsWith(realShaderRoot)) {
                throw new IOException("Shader path resolves outside the shaders directory.");
            }
            if (!Files.isRegularFile(realShader)) {
                return Optional.empty();
            }
            try (InputStream input = Files.newInputStream(realShader)) {
                bytes = readBounded(input);
            }
        } else {
            try (ZipFile zip = new ZipFile(path.toFile())) {
                ZipEntry entry = zip.getEntry(shaderDirectory + "/" + fileName);
                if (entry == null || entry.isDirectory()) {
                    return Optional.empty();
                }
                try (InputStream input = zip.getInputStream(entry)) {
                    bytes = readBounded(input);
                }
            }
        }
        return Optional.of(new String(bytes, StandardCharsets.UTF_8));
    }

    private String expandIncludes(String fileName, String source, Set<String> includeStack, int depth)
            throws IOException {
        if (depth > MAX_INCLUDE_DEPTH) {
            throw new IOException("Shader includes exceed the maximum nesting depth of " + MAX_INCLUDE_DEPTH + ".");
        }
        if (!includeStack.add(fileName)) {
            throw new IOException("Shader include cycle detected at " + fileName + ".");
        }

        try {
            Matcher matcher = INCLUDE_DIRECTIVE.matcher(source);
            StringBuilder expanded = new StringBuilder(source.length());
            int copiedThrough = 0;
            while (matcher.find()) {
                expanded.append(source, copiedThrough, matcher.start());
                String requestedPath = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
                String includePath = resolveIncludePath(fileName, requestedPath);
                String includeSource = readShader(includePath)
                        .orElseThrow(() -> new IOException("Shader include was not found: " + includePath));
                String expandedInclude = expandIncludes(includePath, includeSource, includeStack, depth + 1);
                expanded.append(expandedInclude);
                if (matcher.group().endsWith("\n") && !expandedInclude.endsWith("\n")) {
                    expanded.append('\n');
                }
                copiedThrough = matcher.end();
                if (expanded.length() > MAX_EXPANDED_SHADER_CHARS) {
                    throw new IOException("Expanded shader exceeds the 4 MiB size limit.");
                }
            }
            expanded.append(source, copiedThrough, source.length());
            if (expanded.length() > MAX_EXPANDED_SHADER_CHARS) {
                throw new IOException("Expanded shader exceeds the 4 MiB size limit.");
            }
            return expanded.toString();
        } finally {
            includeStack.remove(fileName);
        }
    }

    private static String resolveIncludePath(String includingFile, String requestedPath) throws IOException {
        String portablePath = requestedPath.replace('\\', '/');
        boolean rootRelative = portablePath.startsWith("/");
        List<String> segments = new ArrayList<>();
        if (!rootRelative) {
            String parent = includingFile.contains("/")
                    ? includingFile.substring(0, includingFile.lastIndexOf('/')) : "";
            for (String segment : parent.split("/")) {
                if (!segment.isEmpty()) {
                    segments.add(segment);
                }
            }
        }
        for (String segment : portablePath.split("/")) {
            if (segment.isEmpty() || segment.equals(".")) {
                continue;
            }
            if (segment.equals("..")) {
                if (segments.isEmpty()) {
                    throw new IOException("Shader include escapes the shaders directory: " + requestedPath);
                }
                segments.remove(segments.size() - 1);
            } else {
                segments.add(segment);
            }
        }
        if (segments.isEmpty()) {
            throw new IOException("Shader include path is empty: " + requestedPath);
        }
        return String.join("/", segments);
    }

    private static Inspection inspectDirectory(Path path) throws IOException {
        Path shaders = path.resolve("shaders");
        if (!Files.isDirectory(shaders)) {
            return null;
        }
        try (var entries = Files.list(shaders)) {
            Set<String> files = entries.filter(Files::isRegularFile)
                    .map(entry -> entry.getFileName().toString())
                    .collect(java.util.stream.Collectors.toUnmodifiableSet());
            boolean hasPostProcessing = files.stream().anyMatch(ShaderPack::isPostProcessingProgramFile);
            Set<String> terrainPrograms = files.stream()
                    .filter(ShaderPack::isSupportedTerrainProgramFile)
                    .map(fileName -> fileName.substring(0, fileName.length() - 4))
                    .collect(java.util.stream.Collectors.toUnmodifiableSet());
            Set<String> blockPrograms = files.stream()
                    .filter(ShaderPack::isSupportedBlockProgramFile)
                    .map(fileName -> fileName.substring(0, fileName.length() - 4))
                    .collect(java.util.stream.Collectors.toUnmodifiableSet());
            boolean hasCloudFragment = files.contains("gbuffers_clouds.fsh");
            boolean hasBeaconBeamFragment = files.contains(BEACON_BEAM_PROGRAM + ".fsh");
            boolean hasLightningFragment = files.contains(LIGHTNING_PROGRAM + ".fsh");
            boolean hasBasicFragment = files.contains(BASIC_PROGRAM + ".fsh");
            boolean hasLineFragment = files.contains(LINE_PROGRAM + ".fsh");
            boolean hasGlintFragment = files.contains(GLINT_PROGRAM + ".fsh");
            boolean hasSkyBasicFragment = files.contains(SKY_BASIC_PROGRAM + ".fsh");
            boolean hasSkyTexturedFragment = files.contains(SKY_TEXTURED_PROGRAM + ".fsh");
            boolean hasTexturedFragment = files.contains(TEXTURED_PROGRAM + ".fsh");
            boolean hasTexturedLitFragment = files.contains(TEXTURED_LIT_PROGRAM + ".fsh");
            boolean hasHandFragment = files.contains(HAND_PROGRAM + ".fsh");
            boolean hasHandWaterFragment = files.contains(HAND_WATER_PROGRAM + ".fsh");
            boolean hasDamagedBlockFragment = files.contains(DAMAGED_BLOCK_PROGRAM + ".fsh");
            Set<String> entityPrograms = files.stream()
                    .filter(ShaderPack::isSupportedEntityProgramFile)
                    .map(fileName -> fileName.substring(0, fileName.length() - 4))
                    .collect(java.util.stream.Collectors.toUnmodifiableSet());
            Set<String> particlePrograms = files.stream()
                    .filter(ShaderPack::isSupportedParticleProgramFile)
                    .map(fileName -> fileName.substring(0, fileName.length() - 4))
                    .collect(java.util.stream.Collectors.toUnmodifiableSet());
            Set<String> weatherPrograms = files.stream()
                    .filter(ShaderPack::isSupportedWeatherProgramFile)
                    .map(fileName -> fileName.substring(0, fileName.length() - 4))
                    .collect(java.util.stream.Collectors.toUnmodifiableSet());
            return hasPostProcessing || hasShadowProgram(files)
                    || !terrainPrograms.isEmpty() || hasCloudFragment || !entityPrograms.isEmpty()
                    || !particlePrograms.isEmpty() || !weatherPrograms.isEmpty() || hasBeaconBeamFragment
                    || hasLightningFragment || hasBasicFragment || hasLineFragment || hasGlintFragment
                    || hasSkyBasicFragment || hasSkyTexturedFragment || hasTexturedFragment || hasTexturedLitFragment
                    || hasHandFragment || hasHandWaterFragment || hasDamagedBlockFragment || !blockPrograms.isEmpty()
                    ? new Inspection("shaders", hasPostProcessing, terrainPrograms, hasCloudFragment,
                            entityPrograms, particlePrograms, weatherPrograms, hasBeaconBeamFragment, hasLightningFragment,
                            hasBasicFragment, hasLineFragment, hasGlintFragment,
                            hasSkyBasicFragment, hasSkyTexturedFragment, hasTexturedFragment, hasTexturedLitFragment,
                            hasHandFragment, hasHandWaterFragment, hasDamagedBlockFragment, blockPrograms) : null;
        }
    }

    private static Inspection inspectArchive(Path path) throws IOException {
        try (ZipFile zip = new ZipFile(path.toFile())) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            List<String> paths = Collections.list(entries).stream()
                    .filter(entry -> !entry.isDirectory())
                    .map(ZipEntry::getName)
                    .filter(ShaderPack::isSupportedArchiveProgramPath)
                    .map(entryName -> entryName.substring(0, entryName.lastIndexOf("shaders/") + 7))
                    .distinct()
                    .toList();
            String shaderDirectory = paths.stream()
                    .min((left, right) -> Integer.compare(left.length(), right.length()))
                    .orElse(null);
            if (shaderDirectory == null) {
                return null;
            }

            Set<String> files = new HashSet<>();
            Enumeration<? extends ZipEntry> allEntries = zip.entries();
            while (allEntries.hasMoreElements()) {
                ZipEntry entry = allEntries.nextElement();
                String prefix = shaderDirectory + "/";
                if (!entry.isDirectory() && entry.getName().startsWith(prefix)) {
                    String fileName = entry.getName().substring(prefix.length());
                    if (!fileName.contains("/") && isSupportedProgramFile(fileName)) {
                        files.add(fileName);
                    }
                }
            }
            boolean hasPostProcessing = files.stream().anyMatch(ShaderPack::isPostProcessingProgramFile);
            Set<String> terrainPrograms = files.stream()
                    .filter(ShaderPack::isSupportedTerrainProgramFile)
                    .map(fileName -> fileName.substring(0, fileName.length() - 4))
                    .collect(java.util.stream.Collectors.toUnmodifiableSet());
            Set<String> blockPrograms = files.stream()
                    .filter(ShaderPack::isSupportedBlockProgramFile)
                    .map(fileName -> fileName.substring(0, fileName.length() - 4))
                    .collect(java.util.stream.Collectors.toUnmodifiableSet());
            boolean hasCloudFragment = files.contains("gbuffers_clouds.fsh");
            boolean hasBeaconBeamFragment = files.contains(BEACON_BEAM_PROGRAM + ".fsh");
            boolean hasLightningFragment = files.contains(LIGHTNING_PROGRAM + ".fsh");
            boolean hasBasicFragment = files.contains(BASIC_PROGRAM + ".fsh");
            boolean hasLineFragment = files.contains(LINE_PROGRAM + ".fsh");
            boolean hasGlintFragment = files.contains(GLINT_PROGRAM + ".fsh");
            boolean hasSkyBasicFragment = files.contains(SKY_BASIC_PROGRAM + ".fsh");
            boolean hasSkyTexturedFragment = files.contains(SKY_TEXTURED_PROGRAM + ".fsh");
            boolean hasTexturedFragment = files.contains(TEXTURED_PROGRAM + ".fsh");
            boolean hasTexturedLitFragment = files.contains(TEXTURED_LIT_PROGRAM + ".fsh");
            boolean hasHandFragment = files.contains(HAND_PROGRAM + ".fsh");
            boolean hasHandWaterFragment = files.contains(HAND_WATER_PROGRAM + ".fsh");
            boolean hasDamagedBlockFragment = files.contains(DAMAGED_BLOCK_PROGRAM + ".fsh");
            Set<String> entityPrograms = files.stream()
                    .filter(ShaderPack::isSupportedEntityProgramFile)
                    .map(fileName -> fileName.substring(0, fileName.length() - 4))
                    .collect(java.util.stream.Collectors.toUnmodifiableSet());
            Set<String> particlePrograms = files.stream()
                    .filter(ShaderPack::isSupportedParticleProgramFile)
                    .map(fileName -> fileName.substring(0, fileName.length() - 4))
                    .collect(java.util.stream.Collectors.toUnmodifiableSet());
            Set<String> weatherPrograms = files.stream()
                    .filter(ShaderPack::isSupportedWeatherProgramFile)
                    .map(fileName -> fileName.substring(0, fileName.length() - 4))
                    .collect(java.util.stream.Collectors.toUnmodifiableSet());
            return new Inspection(shaderDirectory, hasPostProcessing, terrainPrograms, hasCloudFragment,
                    entityPrograms, particlePrograms, weatherPrograms, hasBeaconBeamFragment, hasLightningFragment,
                    hasBasicFragment, hasLineFragment, hasGlintFragment,
                    hasSkyBasicFragment, hasSkyTexturedFragment, hasTexturedFragment, hasTexturedLitFragment,
                    hasHandFragment, hasHandWaterFragment, hasDamagedBlockFragment, blockPrograms);
        }
    }

    private static boolean isSupportedArchiveProgramPath(String entryName) {
        int shadersIndex = entryName.lastIndexOf("shaders/");
        if (shadersIndex < 0 || entryName.indexOf('/', shadersIndex + 8) >= 0) {
            return false;
        }
        return isSupportedProgramFile(entryName.substring(shadersIndex + 8));
    }

    private static boolean isSupportedProgramFile(String fileName) {
        return isSupportedShadowProgramFile(fileName)
                || fileName.equals("gbuffers_clouds.fsh")
                || fileName.equals(BEACON_BEAM_PROGRAM + ".fsh")
                || fileName.equals(LIGHTNING_PROGRAM + ".fsh")
                || fileName.equals(BASIC_PROGRAM + ".fsh")
                || fileName.equals(LINE_PROGRAM + ".fsh")
                || fileName.equals(GLINT_PROGRAM + ".fsh")
                || fileName.equals(SKY_BASIC_PROGRAM + ".fsh")
                || fileName.equals(SKY_TEXTURED_PROGRAM + ".fsh")
                || fileName.equals(TEXTURED_PROGRAM + ".fsh")
                || fileName.equals(TEXTURED_LIT_PROGRAM + ".fsh")
                || fileName.equals(HAND_PROGRAM + ".fsh")
                || fileName.equals(HAND_WATER_PROGRAM + ".fsh")
                || fileName.equals(DAMAGED_BLOCK_PROGRAM + ".fsh")
                || isSupportedBlockProgramFile(fileName)
                || isSupportedTerrainProgramFile(fileName)
                || isSupportedEntityProgramFile(fileName)
                || isSupportedParticleProgramFile(fileName)
                || isSupportedWeatherProgramFile(fileName)
                || isPostProcessingProgramFile(fileName);
    }

    private static boolean hasShadowProgram(Set<String> files) {
        return SHADOW_PROGRAMS.stream().anyMatch(programName -> files.contains(programName + ".fsh"));
    }

    private static boolean isSupportedShadowProgramFile(String fileName) {
        return fileName.endsWith(".fsh")
                && SHADOW_PROGRAMS.contains(fileName.substring(0, fileName.length() - 4));
    }

    private static boolean isSupportedTerrainProgramFile(String fileName) {
        return fileName.endsWith(".fsh")
                && TERRAIN_PROGRAMS.contains(fileName.substring(0, fileName.length() - 4));
    }

    private static boolean isSupportedEntityProgramFile(String fileName) {
        return fileName.endsWith(".fsh")
                && ENTITY_PROGRAMS.contains(fileName.substring(0, fileName.length() - 4));
    }

    private static boolean isSupportedBlockProgramFile(String fileName) {
        return fileName.endsWith(".fsh")
                && BLOCK_PROGRAMS.contains(fileName.substring(0, fileName.length() - 4));
    }

    private static boolean isSupportedParticleProgramFile(String fileName) {
        return fileName.endsWith(".fsh")
                && PARTICLE_PROGRAMS.contains(fileName.substring(0, fileName.length() - 4));
    }

    private static boolean isSupportedWeatherProgramFile(String fileName) {
        return fileName.endsWith(".fsh")
                && WEATHER_PROGRAMS.contains(fileName.substring(0, fileName.length() - 4));
    }

    private static boolean isPostProcessingProgramFile(String fileName) {
        if (fileName.equals("final.fsh")) {
            return true;
        }
        for (String prefix : MULTI_PASS_PROGRAMS) {
            if (fileName.equals(prefix + ".fsh")) {
                return true;
            }
            for (int suffix = 1; suffix <= MAX_PASS_SUFFIX; suffix++) {
                if (fileName.equals(prefix + suffix + ".fsh")) {
                    return true;
                }
            }
        }
        return false;
    }

    private static byte[] readBounded(InputStream input) throws IOException {
        byte[] bytes = input.readNBytes(MAX_SHADER_BYTES + 1);
        if (bytes.length > MAX_SHADER_BYTES) {
            throw new IOException("Shader file exceeds the 1 MiB size limit.");
        }
        return bytes;
    }

    private static String displayName(Path path) {
        String name = path.getFileName().toString();
        if (name.toLowerCase(java.util.Locale.ROOT).endsWith(".zip")) {
            return name.substring(0, name.length() - 4);
        }
        return name;
    }
}
