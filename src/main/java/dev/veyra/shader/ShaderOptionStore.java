package dev.veyra.shader;

import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

final class ShaderOptionStore {
    private final Path directory;
    ShaderOptionStore(Path directory) { this.directory = directory; }

    private Path file(Path pack) {
        try {
            byte[] name = pack.getFileName().toString().getBytes(StandardCharsets.UTF_8);
            return directory.resolve(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(name)) + ".properties");
        } catch (NoSuchAlgorithmException exception) { throw new IllegalStateException(exception); }
    }
    Map<String, String> read(Path pack) throws IOException {
        Path file = file(pack);
        if (!Files.exists(file)) return Map.of();
        if (Files.size(file) > 128 * 1024) throw new IOException("Saved shader options exceed 128 KiB.");
        Properties properties = ShaderPackOptions.loadProperties(Files.readString(file, StandardCharsets.UTF_8));
        Map<String, String> values = new LinkedHashMap<>();
        for (String name : properties.stringPropertyNames()) values.put(name, properties.getProperty(name));
        return Map.copyOf(values);
    }
    void write(Path pack, Map<String, String> values) throws IOException {
        Properties properties = new Properties();
        properties.putAll(values);
        StringWriter writer = new StringWriter();
        properties.store(writer, "Veyra shaderpack options; source pack files are never modified");
        AtomicFiles.write(file(pack), writer.toString());
    }
}
