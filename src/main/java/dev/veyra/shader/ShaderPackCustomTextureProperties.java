package dev.veyra.shader;

import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

/** Parsed Iris {@code customTexture.*} aliases from the active shader-pack properties. */
public record ShaderPackCustomTextureProperties(Map<String, String> textures) {
    private static final String BLOCK_ATLAS = "minecraft:textures/atlas/blocks.png";

    public ShaderPackCustomTextureProperties {
        textures = Collections.unmodifiableMap(new LinkedHashMap<>(textures));
    }

    public static ShaderPackCustomTextureProperties parse(String source) throws IOException {
        Properties properties = ShaderPackOptions.loadProperties(source);
        Map<String, String> textures = new LinkedHashMap<>();
        for (String key : properties.stringPropertyNames().stream().sorted().toList()) {
            if (!key.startsWith("customTexture.")) continue;
            String sampler = key.substring("customTexture.".length());
            if (!sampler.matches("[A-Za-z_][A-Za-z0-9_]*")) {
                throw new IOException("Invalid custom texture sampler name: " + key);
            }
            String texture = properties.getProperty(key).strip();
            if (texture.isEmpty()) {
                throw new IOException("Custom texture " + key + " has an empty resource path.");
            }
            textures.put(sampler, texture);
        }
        return new ShaderPackCustomTextureProperties(textures);
    }

    public boolean usesMinecraftBlockAtlas(String sampler) {
        return BLOCK_ATLAS.equals(textures.get(sampler));
    }
}
