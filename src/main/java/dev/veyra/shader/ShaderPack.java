package dev.veyra.shader;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
    private static final Pattern INCLUDE_DIRECTIVE = Pattern.compile(
            "(?m)^[\\t ]*#include[\\t ]+(?:\"([^\"]+)\"|<([^>]+)>)[\\t ]*(?:\\r?\\n|$)");

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
            return hasPostProcessing || !terrainPrograms.isEmpty() || hasCloudFragment || !entityPrograms.isEmpty()
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
        return fileName.equals("gbuffers_clouds.fsh")
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
