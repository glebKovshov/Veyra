package dev.veyra.shader;

import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

/** Parsed Iris custom textures and OptiFine-style program-scoped {@code texture.*} inputs. */
public record ShaderPackCustomTextureProperties(
        Map<String, String> textures,
        Map<String, Map<String, String>> scopedTextures) {
    private static final String BLOCK_ATLAS = "minecraft:textures/atlas/blocks.png";
    private static final Set<String> PROGRAM_STAGES = Set.of(
            "begin", "gbuffers", "prepare", "deferred", "composite", "final");

    public ShaderPackCustomTextureProperties {
        textures = Collections.unmodifiableMap(new LinkedHashMap<>(textures));
        Map<String, Map<String, String>> scoped = new LinkedHashMap<>();
        scopedTextures.forEach((program, values) -> scoped.put(program,
                Collections.unmodifiableMap(new LinkedHashMap<>(values))));
        scopedTextures = Collections.unmodifiableMap(scoped);
    }

    public static ShaderPackCustomTextureProperties parse(String source) throws IOException {
        Properties properties = ShaderPackOptions.loadProperties(source);
        Map<String, String> textures = new LinkedHashMap<>();
        Map<String, Map<String, String>> scopedTextures = new LinkedHashMap<>();
        for (String key : properties.stringPropertyNames().stream().sorted().toList()) {
            String scope = null;
            String sampler;
            if (key.startsWith("customTexture.")) {
                sampler = key.substring("customTexture.".length());
            } else if (key.startsWith("texture.")) {
                String[] parts = key.split("\\.", -1);
                if (parts.length != 3 || !parts[1].matches("[A-Za-z_][A-Za-z0-9_]*")) continue;
                scope = parts[1];
                sampler = parts[2];
            } else {
                continue;
            }
            if (!sampler.matches("[A-Za-z_][A-Za-z0-9_]*")) {
                throw new IOException("Invalid shader-pack texture sampler name: " + key);
            }
            String texture = properties.getProperty(key).strip();
            if (texture.isEmpty()) {
                throw new IOException("Shader-pack texture " + key + " has an empty resource path.");
            }
            if (scope == null) {
                textures.put(sampler, texture);
            } else {
                scopedTextures.computeIfAbsent(scope, ignored -> new LinkedHashMap<>()).put(sampler, texture);
            }
        }
        return new ShaderPackCustomTextureProperties(textures, scopedTextures);
    }

    public boolean usesMinecraftBlockAtlas(String sampler) {
        return BLOCK_ATLAS.equals(textures.get(sampler));
    }

    public boolean usesMinecraftBlockAtlas(String sampler, String programName) {
        return BLOCK_ATLAS.equals(texturesForProgram(programName).get(sampler));
    }

    public Map<String, String> texturesForProgram(String programName) {
        Map<String, String> result = new LinkedHashMap<>(textures);
        String stage = programStage(programName);
        result.putAll(scopedTextures.getOrDefault(stage, Map.of()));
        result.putAll(scopedTextures.getOrDefault(programName, Map.of()));
        return Collections.unmodifiableMap(result);
    }

    public Set<String> samplersForProgram(String programName) {
        return texturesForProgram(programName).keySet();
    }

    public Map<String, String> allTextures() {
        Map<String, String> result = new LinkedHashMap<>(textures);
        scopedTextures.values().forEach(result::putAll);
        return Collections.unmodifiableMap(result);
    }

    public Set<String> allTexturePaths() {
        Set<String> paths = new java.util.LinkedHashSet<>(textures.values());
        scopedTextures.values().forEach(values -> paths.addAll(values.values()));
        return Collections.unmodifiableSet(paths);
    }

    public static boolean isMinecraftBlockAtlas(String path) {
        return BLOCK_ATLAS.equals(path);
    }

    private static String programStage(String programName) {
        if (programName.startsWith("shadow_")) return "gbuffers";
        for (String stage : PROGRAM_STAGES) {
            if (programName.equals(stage) || programName.startsWith(stage + "_")
                    || programName.matches(stage + "[0-9]+.*")) {
                return stage;
            }
        }
        return programName;
    }
}
