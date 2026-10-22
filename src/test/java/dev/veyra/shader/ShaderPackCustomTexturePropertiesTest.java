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
    void resolvesProgramScopedLegacyTextureInputsWithoutLeakingThemAcrossStages() throws Exception {
        ShaderPackCustomTextureProperties properties = ShaderPackCustomTextureProperties.parse("""
                customTexture.textureAtlas = minecraft:textures/atlas/blocks.png
                texture.gbuffers.gaux4 = lib/textures/cloud-water.png
                texture.deferred.colortex3 = lib/textures/cloud-water.png
                """);

        assertEquals("lib/textures/cloud-water.png",
                properties.texturesForProgram("gbuffers_terrain").get("gaux4"));
        assertTrue(properties.texturesForProgram("gbuffers_water").containsKey("textureAtlas"));
        assertEquals("lib/textures/cloud-water.png",
                properties.texturesForProgram("shadow_solid").get("gaux4"));
        assertEquals("lib/textures/cloud-water.png",
                properties.texturesForProgram("deferred1").get("colortex3"));
        assertTrue(properties.samplersForProgram("gbuffers_terrain").contains("gaux4"));
        assertTrue(properties.allTexturePaths().contains("lib/textures/cloud-water.png"));
    }

    @Test
    void resolvesLogicalProgramNamesFromVeyraFragmentShaderIdentifiers() {
        String hash = "a".repeat(64);
        assertEquals("gbuffers_entities",
                ShaderPackCustomTextureProperties.programNameForFragmentShaderPath(
                        "shaderpack/entities/gbuffers_entities/" + hash));
        assertEquals("shadow_entities",
                ShaderPackCustomTextureProperties.programNameForFragmentShaderPath(
                        "shaderpack/shadow_entities/" + hash));
        assertEquals("composite0",
                ShaderPackCustomTextureProperties.programNameForFragmentShaderPath(
                        "shaderpack/composite0_" + hash));
        assertEquals(null, ShaderPackCustomTextureProperties.programNameForFragmentShaderPath(
                "shaderpack/entities/" + hash));
        assertEquals(null, ShaderPackCustomTextureProperties.programNameForFragmentShaderPath("core/entity"));
    }

    @Test
    void rejectsInvalidCustomTextureAliasesAndEmptyPaths() {
        assertThrows(java.io.IOException.class, () -> ShaderPackCustomTextureProperties.parse(
                "customTexture.bad.alias = minecraft:textures/atlas/blocks.png"));
        assertThrows(java.io.IOException.class, () -> ShaderPackCustomTextureProperties.parse(
                "customTexture.textureAtlas =   "));
    }
}
