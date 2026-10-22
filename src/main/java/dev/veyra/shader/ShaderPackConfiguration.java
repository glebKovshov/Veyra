package dev.veyra.shader;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/** Immutable user selection plus bounded source snapshots for one pack load/reload. */
public final class ShaderPackConfiguration {
    private final Map<String, String> options;
    private final int dimension;
    private final boolean customImages;
    private final Map<String, Optional<String>> sources = new HashMap<>();
    private final Map<String, String> processed = new HashMap<>();
    private int sourceChars;
    private int processedChars;
    private ShaderPackOptions definitions;
    private ShaderPackCustomTextureProperties customTextureProperties;
    private final Map<String, Boolean> programs = new HashMap<>();

    public ShaderPackConfiguration(Map<String, String> options, int dimension) {
        this(options, dimension, false);
    }
    ShaderPackConfiguration(Map<String, String> options, int dimension, boolean customImages) {
        this.options = Map.copyOf(options);
        this.dimension = dimension;
        this.customImages = customImages;
        if (dimension != -1 && dimension != 0 && dimension != 1) throw new IllegalArgumentException("Unknown shader dimension " + dimension);
    }
    public Map<String, String> options() { return options; }
    public int dimension() { return dimension; }
    boolean customImages() { return customImages; }
    Optional<String> source(String name) { return sources.get(name); }
    void cacheSource(String name, Optional<String> source) {
        int chars = source.map(String::length).orElse(0);
        if (sources.size() < 8192 && chars <= 16 * 1024 * 1024 - sourceChars) {
            sources.put(name, source); sourceChars += chars;
        }
    }
    String processed(String name) { return processed.get(name); }
    void cacheProcessed(String name, String source) {
        if (source.length() <= 16 * 1024 * 1024 - processedChars) {
            processed.put(name, source); processedChars += source.length();
        }
    }
    Boolean enabled(String program) { return programs.get(program); }
    void cacheEnabled(String program, boolean enabled) { programs.put(program, enabled); }
    ShaderPackOptions definitions() { return definitions; }
    void cacheDefinitions(ShaderPackOptions options) { definitions = options; }
    ShaderPackCustomTextureProperties customTextureProperties() { return customTextureProperties; }
    void cacheCustomTextureProperties(ShaderPackCustomTextureProperties properties) { customTextureProperties = properties; }
}
