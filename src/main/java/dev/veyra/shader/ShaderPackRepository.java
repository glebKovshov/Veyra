package dev.veyra.shader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

public final class ShaderPackRepository {
    private static final Logger LOGGER = LoggerFactory.getLogger("veyra");
    private final Path directory;
    private final Path configFile;
    private List<ShaderPack> packs = List.of();
    private ShaderPack selected;

    public ShaderPackRepository(Path gameDirectory) {
        this.directory = gameDirectory.resolve("shaderpacks");
        this.configFile = gameDirectory.resolve("veyra/selected-pack.txt");
    }

    public void scan() {
        try {
            refresh();
            String selectedName = Files.exists(configFile) ? Files.readString(configFile, StandardCharsets.UTF_8).trim() : "";
            selected = packs.stream()
                    .filter(ShaderPack::supportsShaderPrograms)
                    .filter(pack -> pack.path().getFileName().toString().equals(selectedName))
                    .findFirst()
                    .orElse(null);
        } catch (IOException exception) {
            packs = List.of();
            selected = null;
            LOGGER.error("Could not scan shader pack directory {}", directory, exception);
        }
    }

    /** Refresh discovery without changing the pack whose pipelines are currently installed. */
    public void refresh() throws IOException {
        Files.createDirectories(directory);
        try (Stream<Path> entries = Files.list(directory)) {
            List<ShaderPack> discovered = entries
                    .filter(path -> Files.isDirectory(path) || path.getFileName().toString().toLowerCase(java.util.Locale.ROOT).endsWith(".zip"))
                    .map(ShaderPack::inspect)
                    .sorted(Comparator.comparing(ShaderPack::name, String.CASE_INSENSITIVE_ORDER))
                    .toList();
            packs = discovered;
        }
    }

    public Optional<ShaderPack> findAvailable(Path path) {
        return packs.stream().filter(ShaderPack::supportsShaderPrograms)
                .filter(pack -> pack.path().equals(path)).findFirst();
    }

    public List<ShaderPack> getPacks() {
        return packs;
    }

    public Optional<ShaderPack> getSelected() {
        return Optional.ofNullable(selected);
    }

    void clearSelection() {
        selected = null;
    }

    public Path getDirectory() {
        return directory;
    }

    public void select(ShaderPack pack) throws IOException {
        if (pack != null && (!pack.supportsShaderPrograms() || !packs.contains(pack))) {
            throw new IOException("This pack is not available or contains no supported shader program.");
        }
        Files.createDirectories(configFile.getParent());
        Path temporary = Files.createTempFile(configFile.getParent(), "selected-pack-", ".tmp");
        try {
            Files.writeString(temporary, pack == null ? "" : pack.path().getFileName().toString(), StandardCharsets.UTF_8);
            try {
                Files.move(temporary, configFile, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, configFile, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
        selected = pack;
    }
}
