package dev.veyra.shader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EntityIdMapTest {
    @Test
    void parsesNamespacedAndMinecraftEntityIdsAndKeepsTheLastDuplicateMapping() {
        EntityIdMap map = EntityIdMap.parse("""
                # entity map
                entity.12 = zombie minecraft:skeleton
                entity.31 = minecraft:zombie example:custom_mob
                """);

        assertEquals(31, map.id("minecraft:zombie"));
        assertEquals(12, map.id("skeleton"));
        assertEquals(31, map.id("example:custom_mob"));
        assertEquals(EntityIdMap.UNMAPPED, map.id("minecraft:creeper"));
        assertTrue(map.ids().containsAll(java.util.Set.of(12, 31)));
    }

    @Test
    void joinsBackslashContinuedPropertyValues() {
        EntityIdMap map = EntityIdMap.parse(String.join("\n", "entity.4 = zombie \\", "skeleton"));

        assertEquals(4, map.id("minecraft:zombie"));
        assertEquals(4, map.id("minecraft:skeleton"));
    }

    @Test
    void readsEntityAndBlockPropertiesAfterOptionPreprocessing(@TempDir Path pack) throws Exception {
        Path shaders = Files.createDirectories(pack.resolve("shaders"));
        Files.writeString(shaders.resolve("gbuffers_terrain.fsh"),
                "#version 120\nvoid main(){gl_FragColor=vec4(1);}\n");
        Files.writeString(shaders.resolve("entity.properties"), """
                #ifdef MC_VERSION
                entity.6 = zombie
                #endif
                """);
        Files.writeString(shaders.resolve("block.properties"), """
                #if MC_VERSION >= 12111
                block.9 = oak_stairs:half=bottom
                #endif
                """);

        ShaderPack shaderPack = ShaderPack.inspect(pack);
        assertEquals(6, shaderPack.readEntityIds().id("minecraft:zombie"));
        assertEquals(9, shaderPack.readBlockIds().id("minecraft:oak_stairs", Map.of("half", "bottom")));
    }
}
