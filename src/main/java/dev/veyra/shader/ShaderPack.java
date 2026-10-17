package dev.veyra.shader;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public record ShaderPack(Path path, String name, String shaderEntry, String issue) {
    private static final int MAX_SHADER_BYTES = 1024 * 1024;
    private static final String FINAL_SHADER_SUFFIX = "shaders/final.fsh";

    public static ShaderPack inspect(Path path) {
        String name = displayName(path);
        try {
            String entry = Files.isDirectory(path) ? inspectDirectory(path) : inspectArchive(path);
            return new ShaderPack(path, name, entry, entry == null
                    ? "No shaders/final.fsh found; this pack has no supported final pass."
                    : "Supported final pass.");
        } catch (IOException | RuntimeException exception) {
            return new ShaderPack(path, name, null, "Could not read pack: " + exception.getMessage());
        }
    }

    public boolean supportsFinalPass() {
        return shaderEntry != null;
    }

    public String readFinalShader() throws IOException {
        if (!supportsFinalPass()) {
            throw new IOException(issue);
        }

        byte[] bytes;
        if (Files.isDirectory(path)) {
            Path shader = path.resolve(shaderEntry).normalize();
            if (!shader.startsWith(path.normalize())) {
                throw new IOException("Shader path escapes the pack directory.");
            }
            try (InputStream input = Files.newInputStream(shader)) {
                bytes = readBounded(input);
            }
        } else {
            try (ZipFile zip = new ZipFile(path.toFile())) {
                ZipEntry entry = zip.getEntry(shaderEntry);
                if (entry == null || entry.isDirectory()) {
                    throw new IOException("The final shader disappeared from the archive.");
                }
                try (InputStream input = zip.getInputStream(entry)) {
                    bytes = readBounded(input);
                }
            }
        }

        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static String inspectDirectory(Path path) throws IOException {
        Path shader = path.resolve("shaders/final.fsh");
        return Files.isRegularFile(shader) ? "shaders/final.fsh" : null;
    }

    private static String inspectArchive(Path path) throws IOException {
        try (ZipFile zip = new ZipFile(path.toFile())) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            return java.util.Collections.list(entries).stream()
                    .filter(entry -> !entry.isDirectory())
                    .map(ZipEntry::getName)
                    .filter(name -> name.equals(FINAL_SHADER_SUFFIX) || name.endsWith("/" + FINAL_SHADER_SUFFIX))
                    .min(Comparator.comparingInt(String::length))
                    .orElse(null);
        }
    }

    private static byte[] readBounded(InputStream input) throws IOException {
        byte[] bytes = input.readNBytes(MAX_SHADER_BYTES + 1);
        if (bytes.length > MAX_SHADER_BYTES) {
            throw new IOException("final.fsh exceeds the 1 MiB size limit.");
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
