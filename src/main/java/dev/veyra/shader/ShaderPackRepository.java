package dev.veyra.shader;

import dev.veyra.VeyraClient;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

public final class ShaderPackRepository {
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
            Files.createDirectories(directory);
            try (Stream<Path> entries = Files.list(directory)) {
                packs = entries
                        .filter(path -> Files.isDirectory(path) || path.getFileName().toString().toLowerCase(java.util.Locale.ROOT).endsWith(".zip"))
                        .map(ShaderPack::inspect)
                        .sorted(Comparator.comparing(ShaderPack::name, String.CASE_INSENSITIVE_ORDER))
                        .toList();
            }
            String selectedName = Files.exists(configFile) ? Files.readString(configFile, StandardCharsets.UTF_8).trim() : "";
            selected = packs.stream()
                    .filter(ShaderPack::supportsShaderPrograms)
                    .filter(pack -> pack.path().getFileName().toString().equals(selectedName))
                    .findFirst()
                    .orElse(null);
        } catch (IOException exception) {
            packs = List.of();
            selected = null;
            VeyraClient.LOGGER.error("Could not scan shader pack directory {}", directory, exception);
        }
    }

    public List<ShaderPack> getPacks() {
        return packs;
    }

    public Optional<ShaderPack> getSelected() {
        return Optional.ofNullable(selected);
    }

    public Path getDirectory() {
        return directory;
    }

    public void select(ShaderPack pack) throws IOException {
        if (pack != null && (!pack.supportsShaderPrograms() || !packs.contains(pack))) {
            throw new IOException("This pack is not available or contains no supported shader program.");
        }
        Files.createDirectories(configFile.getParent());
        Files.writeString(configFile, pack == null ? "" : pack.path().getFileName().toString(), StandardCharsets.UTF_8);
        selected = pack;
    }
}
