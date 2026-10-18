package dev.veyra.shader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

public final class ShaderPackRepository {
    private static final Logger LOGGER = LoggerFactory.getLogger("veyra");
    private final Path directory;
    private final Path configFile;
    private final ShaderOptionStore options;
    private int dimension;
    private List<ShaderPack> packs = List.of();
    private ShaderPack selected;

    public ShaderPackRepository(Path gameDirectory) {
        this.directory = gameDirectory.resolve("shaderpacks");
        this.configFile = gameDirectory.resolve("veyra/selected-pack.txt");
        this.options = new ShaderOptionStore(gameDirectory.resolve("veyra/options"));
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
            if (selected != null) selected = loadConfiguration(selected);
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
                    .map(path -> ShaderPack.inspect(path, new ShaderPackConfiguration(java.util.Map.of(), dimension)))
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

    public ShaderPack loadConfiguration(ShaderPack pack) throws IOException {
        java.util.Map<String, String> saved = options.read(pack.path());
        ShaderPackOptions specification = pack.readOptions();
        java.util.Map<String, String> valid = new java.util.LinkedHashMap<>();
        saved.forEach((name, value) -> {
            ShaderPackOptions.Option option = specification.options().get(name);
            if (option != null && option.values().contains(value)) valid.put(name, value);
            else LOGGER.warn("Ignoring stale/invalid saved option {} for {}", name, pack.name());
        });
        return pack.configured(specification.validate(valid), dimension);
    }

    public void setDimension(int dimension) {
        new ShaderPackConfiguration(java.util.Map.of(), dimension); // Validate before changing repository state.
        this.dimension = dimension;
    }

    public void select(ShaderPack pack) throws IOException {
        if (pack != null && (!pack.supportsShaderPrograms() || findAvailable(pack.path()).isEmpty())) {
            throw new IOException("This pack is not available or contains no supported shader program.");
        }
        java.util.Map<String, String> before = pack == null ? java.util.Map.of() : options.read(pack.path());
        if (pack != null) options.write(pack.path(), pack.configuration().options());
        try {
            AtomicFiles.write(configFile, pack == null ? "" : pack.path().getFileName().toString());
        } catch (IOException exception) {
            if (pack != null) {
                try { options.write(pack.path(), before); }
                catch (IOException rollback) { exception.addSuppressed(rollback); }
            }
            throw exception;
        }
        selected = pack;
    }
}
