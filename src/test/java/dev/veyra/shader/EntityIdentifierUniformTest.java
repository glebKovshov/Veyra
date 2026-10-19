package dev.veyra.shader;

import java.io.IOException;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EntityIdentifierUniformTest {
    private static final String SOURCE = """
            #version 120
            uniform int blockEntityId;
            uniform int entityId;
            void main() { gl_FragColor = vec4(float(blockEntityId + entityId)); }
            """;

    @Test
    void terrainAndCloudsReceiveIrisFallbackValues() throws IOException {
        String terrain = TerrainShaderAdapter.adaptFragment(SOURCE);
        assertTrue(terrain.contains("const int blockEntityId = -1;"));
        assertTrue(terrain.contains("const int entityId = 0;"));
        assertFalse(terrain.contains("uniform int blockEntityId"));
        String clouds = CloudShaderAdapter.adaptFragment(SOURCE);
        assertTrue(clouds.contains("const int blockEntityId = -1;"), clouds);
    }

    @Test
    void entityAttributeProgramsReceivePerDrawIdentifierConstants() throws IOException {
        String unmapped = EntityShaderAdapter.adaptFragment(SOURCE);
        assertTrue(unmapped.contains("const int blockEntityId = -1;"), unmapped);
        assertTrue(unmapped.contains("const int entityId = -1;"), unmapped);
        assertFalse(unmapped.contains("uniform int blockEntityId"), unmapped);

        String mapped = EntityShaderAdapter.adaptFragment(
                SOURCE, "gbuffers_entities", false, false, 12, 83);
        assertTrue(mapped.contains("const int entityId = 12;"), mapped);
        assertTrue(mapped.contains("const int blockEntityId = 83;"), mapped);
        assertTrue(EntityShaderAdapter.usesEntityIdentifier(SOURCE, "gbuffers_entities", "entityId"));
        assertTrue(EntityShaderAdapter.usesEntityIdentifier(SOURCE, "gbuffers_block", "blockEntityId"));
        assertFalse(ShaderPackUniformAdapter.isUnboundEntityIdentifier("gbuffers_block", "int", "blockEntityId"));
        assertFalse(ShaderPackUniformAdapter.isUnboundEntityIdentifier("gbuffers_hand_water", "int", "entityId"));
        assertTrue(ShaderPackUniformAdapter.isUnboundEntityIdentifier("gbuffers_basic", "int", "entityId"));
        assertFalse(ShaderPackUniformAdapter.isUnboundEntityIdentifier("gbuffers_basic", "float", "entityId"));
        assertEquals("const vec4 entityColor = vec4(0.0);\n",
                ShaderPackUniformAdapter.unboundEntityDeclaration("gbuffers_terrain", "vec4", "entityColor"));
        assertNull(ShaderPackUniformAdapter.unboundEntityDeclaration("gbuffers_entities", "vec4", "entityColor"));
    }
}
