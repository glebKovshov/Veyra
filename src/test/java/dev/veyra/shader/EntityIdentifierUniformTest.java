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
    void terrainAndCloudsReceiveIrisUnboundZero() throws IOException {
        String terrain = TerrainShaderAdapter.adaptFragment(SOURCE);
        assertTrue(terrain.contains("const int blockEntityId = 0;"));
        assertTrue(terrain.contains("const int entityId = 0;"));
        assertFalse(terrain.contains("uniform int blockEntityId"));
        String clouds = CloudShaderAdapter.adaptFragment(SOURCE);
        assertTrue(clouds.contains("const int blockEntityId = 0;"), clouds);
    }

    @Test
    void entityAttributeProgramsStillRequirePerDrawIdentifiers() {
        IOException error = assertThrows(IOException.class, () -> EntityShaderAdapter.adaptFragment(SOURCE));
        assertTrue(error.getMessage().contains("EntityId"), error.getMessage());
        assertFalse(ShaderPackUniformAdapter.isUnboundEntityIdentifier("gbuffers_block", "int", "blockEntityId"));
        assertFalse(ShaderPackUniformAdapter.isUnboundEntityIdentifier("gbuffers_hand_water", "int", "entityId"));
        assertTrue(ShaderPackUniformAdapter.isUnboundEntityIdentifier("gbuffers_basic", "int", "entityId"));
        assertFalse(ShaderPackUniformAdapter.isUnboundEntityIdentifier("gbuffers_basic", "float", "entityId"));
    }
}
