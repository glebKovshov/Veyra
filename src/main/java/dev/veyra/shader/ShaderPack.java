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
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public record ShaderPack(Path path, String name, String shaderDirectory, String issue) {
    private static final int MAX_SHADER_BYTES = 1024 * 1024;
    private static final int MAX_PASS_SUFFIX = 99;
    private static final List<String> MULTI_PASS_PROGRAMS = List.of("prepare", "deferred", "composite");

    public record Program(String name, String source) {
    }

    public static ShaderPack inspect(Path path) {
        String name = displayName(path);
        try {
            String shaderDirectory = Files.isDirectory(path) ? inspectDirectory(path) : inspectArchive(path);
            return new ShaderPack(path, name, shaderDirectory, shaderDirectory == null
                    ? "No supported prepare, deferred, composite, or final fragment passes were found."
                    : "Post-processing passes are available.");
        } catch (IOException | RuntimeException exception) {
            return new ShaderPack(path, name, null, "Could not read pack: " + exception.getMessage());
        }
    }

    public boolean supportsPostProcessing() {
        return shaderDirectory != null;
    }

    public List<Program> readPrograms() throws IOException {
        if (!supportsPostProcessing()) {
            throw new IOException(issue);
        }

        List<Program> programs = new ArrayList<>();
        Set<String> availableFiles = listShaderFiles();
        for (String prefix : MULTI_PASS_PROGRAMS) {
            for (int suffix = 0; suffix <= MAX_PASS_SUFFIX; suffix++) {
                String programName = suffix == 0 ? prefix : prefix + suffix;
                String fileName = programName + ".fsh";
                if (availableFiles.contains(fileName)) {
                    readShader(fileName).ifPresent(source -> programs.add(new Program(programName, source)));
                }
            }
        }
        if (availableFiles.contains("final.fsh")) {
            readShader("final.fsh").ifPresent(source -> programs.add(new Program("final", source)));
        }

        if (programs.isEmpty()) {
            throw new IOException("The pack no longer contains a supported fragment pass.");
        }
        return List.copyOf(programs);
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
            Path packRoot = path.toAbsolutePath().normalize();
            Path shader = packRoot.resolve(shaderDirectory).resolve(fileName).normalize();
            if (!shader.startsWith(packRoot)) {
                throw new IOException("Shader path escapes the pack directory.");
            }
            if (!Files.isRegularFile(shader)) {
                return Optional.empty();
            }
            try (InputStream input = Files.newInputStream(shader)) {
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

    private static String inspectDirectory(Path path) throws IOException {
        Path shaders = path.resolve("shaders");
        if (!Files.isDirectory(shaders)) {
            return null;
        }
        try (var entries = Files.list(shaders)) {
            return entries.filter(Files::isRegularFile)
                    .map(entry -> entry.getFileName().toString())
                    .anyMatch(ShaderPack::isSupportedProgramFile) ? "shaders" : null;
        }
    }

    private static String inspectArchive(Path path) throws IOException {
        try (ZipFile zip = new ZipFile(path.toFile())) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            return Collections.list(entries).stream()
                    .filter(entry -> !entry.isDirectory())
                    .map(ZipEntry::getName)
                    .filter(ShaderPack::isSupportedArchiveProgramPath)
                    .map(entryName -> entryName.substring(0, entryName.lastIndexOf("shaders/") + 7))
                    .min((left, right) -> Integer.compare(left.length(), right.length()))
                    .orElse(null);
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
