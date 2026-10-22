package dev.veyra.shader;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShaderPackCustomTexturePropertiesTest {
    @Test
    void recognizesTheDynamicBlockAtlasAlias() throws Exception {
        ShaderPackCustomTextureProperties properties = ShaderPackCustomTextureProperties.parse("""
                customTexture.textureAtlas = minecraft:textures/atlas/blocks.png
                customTexture.cloudWaterTex = lib/textures/cloud-water.png
                """);

        assertEquals("minecraft:textures/atlas/blocks.png", properties.textures().get("textureAtlas"));
        assertTrue(properties.usesMinecraftBlockAtlas("textureAtlas"));
        assertFalse(properties.usesMinecraftBlockAtlas("cloudWaterTex"));
    }

    @Test
    void rejectsInvalidCustomTextureAliasesAndEmptyPaths() {
        assertThrows(java.io.IOException.class, () -> ShaderPackCustomTextureProperties.parse(
                "customTexture.bad.alias = minecraft:textures/atlas/blocks.png"));
        assertThrows(java.io.IOException.class, () -> ShaderPackCustomTextureProperties.parse(
                "customTexture.textureAtlas =   "));
    }
}
