package dev.veyra.shader;

import java.io.IOException;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EntityIdentifierUniformTest {
    private static final String SOURCE = """
            #version 120
            uniform int blockEntityId;
            uniform int entityId;
            uniform int currentRenderedItemId;
            void main() { gl_FragColor = vec4(float(blockEntityId + entityId + currentRenderedItemId)); }
            """;
    private static final String ENTITY_COLOR_SOURCE = """
            #version 120
            uniform vec4 entityColor;
            void main() { gl_FragColor = entityColor; }
            """;

    @Test
    void terrainAndCloudsReceiveIrisFallbackValues() throws IOException {
        String terrain = TerrainShaderAdapter.adaptFragment(SOURCE);
        assertTrue(terrain.contains("const int blockEntityId = -1;"));
        assertTrue(terrain.contains("const int entityId = 0;"));
        assertTrue(terrain.contains("const int currentRenderedItemId = -1;"));
        assertFalse(terrain.contains("uniform int blockEntityId"));
        String clouds = CloudShaderAdapter.adaptFragment(SOURCE);
        assertTrue(clouds.contains("const int blockEntityId = -1;"), clouds);
    }

    @Test
    void entityAttributeProgramsReceivePerDrawIdentifierConstants() throws IOException {
        String unmapped = EntityShaderAdapter.adaptFragment(SOURCE);
        assertTrue(unmapped.contains("const int blockEntityId = -1;"), unmapped);
        assertTrue(unmapped.contains("const int entityId = -1;"), unmapped);
        assertTrue(unmapped.contains("const int currentRenderedItemId = -1;"), unmapped);
        assertFalse(unmapped.contains("uniform int blockEntityId"), unmapped);

        String mapped = EntityShaderAdapter.adaptFragment(
                SOURCE, "gbuffers_entities", false, false, 12, 83, 44);
        assertTrue(mapped.contains("const int entityId = 12;"), mapped);
        assertTrue(mapped.contains("const int blockEntityId = 83;"), mapped);
        assertTrue(mapped.contains("const int currentRenderedItemId = 44;"), mapped);
        assertTrue(EntityShaderAdapter.usesEntityIdentifier(SOURCE, "gbuffers_entities", "entityId"));
        assertTrue(EntityShaderAdapter.usesEntityIdentifier(SOURCE, "gbuffers_block", "blockEntityId"));
        assertTrue(EntityShaderAdapter.usesEntityIdentifier(
                SOURCE, "gbuffers_entities", "currentRenderedItemId"));
        assertTrue(EntityShaderAdapter.usesEntityIdentifier(
                SOURCE, "gbuffers_hand", "currentRenderedItemId"));
        assertTrue(EntityShaderAdapter.usesEntityIdentifier(
                SOURCE, "gbuffers_textured", "currentRenderedItemId"));
        assertTrue(EntityShaderAdapter.usesEntityIdentifier(
                SOURCE, "shadow_entities", "currentRenderedItemId"));
        String shadow = EntityShaderAdapter.adaptFragment(
                SOURCE, "shadow_entities", false, false, -1, -1, 55);
        assertTrue(shadow.contains("const int currentRenderedItemId = 55;"), shadow);
        assertTrue(ShaderPackUniformAdapter.isBoundEntityIdentifier(
                "shadow_block", "int", "currentRenderedItemId"));
        assertFalse(ShaderPackUniformAdapter.isUnboundEntityIdentifier("gbuffers_block", "int", "blockEntityId"));
        assertFalse(ShaderPackUniformAdapter.isUnboundEntityIdentifier("gbuffers_hand_water", "int", "entityId"));
        assertTrue(ShaderPackUniformAdapter.isUnboundEntityIdentifier("gbuffers_basic", "int", "entityId"));
        assertTrue(ShaderPackUniformAdapter.isUnboundEntityIdentifier(
                "gbuffers_basic", "int", "currentRenderedItemId"));
        assertFalse(ShaderPackUniformAdapter.isUnboundEntityIdentifier(
                "gbuffers_entities", "int", "currentRenderedItemId"));
        assertFalse(ShaderPackUniformAdapter.isUnboundEntityIdentifier("gbuffers_basic", "float", "entityId"));
        assertEquals("const vec4 entityColor = vec4(0.0);\n",
                ShaderPackUniformAdapter.unboundEntityDeclaration("gbuffers_terrain", "vec4", "entityColor"));
        assertNull(ShaderPackUniformAdapter.unboundEntityDeclaration("gbuffers_entities", "vec4", "entityColor"));
    }

    @Test
    void entityColorUsesMinecraftOverlayForEntityPipelines() throws IOException {
        String entities = EntityShaderAdapter.adaptFragment(ENTITY_COLOR_SOURCE, "gbuffers_entities");
        assertTrue(entities.contains("#define entityColor overlayColor"), entities);
        assertTrue(entities.contains("in vec4 overlayColor;"), entities);
        assertFalse(entities.contains("uniform vec4 entityColor"), entities);
        assertFalse(entities.contains("const vec4 entityColor"), entities);

        String handFallback = EntityShaderAdapter.adaptFragment(ENTITY_COLOR_SOURCE, "gbuffers_textured");
        assertTrue(handFallback.contains("#define entityColor overlayColor"), handFallback);

        String itemEntity = EntityShaderAdapter.adaptFragment(ENTITY_COLOR_SOURCE, "gbuffers_entities",
                false, false, -1, -1, ItemIdMap.UNMAPPED, false);
        assertTrue(itemEntity.contains("const vec4 entityColor = vec4(0.0);"), itemEntity);
        assertFalse(itemEntity.contains("#define entityColor overlayColor"), itemEntity);
    }

    @Test
    void entityColorDefaultsToTransparentOnPipelinesWithoutEntityOverlay() throws IOException {
        String signText = EntityShaderAdapter.adaptTextFragment(ENTITY_COLOR_SOURCE, "gbuffers_textured");
        assertTrue(signText.contains("const vec4 entityColor = vec4(0.0);"), signText);
        assertFalse(signText.contains("#define entityColor overlayColor"), signText);

        String text = EntityShaderAdapter.adaptFragment(ENTITY_COLOR_SOURCE, "gbuffers_text");
        assertTrue(text.contains("const vec4 entityColor = vec4(0.0);"), text);
    }
}
