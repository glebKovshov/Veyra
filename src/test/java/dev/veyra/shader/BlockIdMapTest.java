package dev.veyra.shader;

import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BlockIdMapTest {
    @Test
    void matchesDirectBlocksAndPartialStatePredicatesInSourceOrder() {
        BlockIdMap map = BlockIdMap.parse("""
                block.7 = oak_stairs:facing=east
                block.9 = minecraft:oak_stairs:half=bottom
                block.12 = example:machine:active=true
                """);

        assertEquals(7, map.id("minecraft:oak_stairs", Map.of("facing", "east", "half", "top")));
        assertEquals(9, map.id("oak_stairs", Map.of("facing", "west", "half", "bottom")));
        assertEquals(12, map.id("example:machine", Map.of("active", "true")));
        assertEquals(BlockIdMap.UNMAPPED,
                map.id("minecraft:oak_stairs", Map.of("facing", "west", "half", "top")));
    }

    @Test
    void parsesOrderedUnfilteredAndTagSelectors() {
        BlockIdMap map = BlockIdMap.parse("""
                block.3 = %minecraft:logs
                block.8 = minecraft:oak_log:axis=y
                block.13 = minecraft:oak_log
                """);

        assertEquals(3, map.id("minecraft:oak_log", Map.of("axis", "y"), java.util.Set.of("minecraft:logs")));
        assertEquals(BlockIdMap.UNMAPPED, map.id("minecraft:stone", Map.of()));
    }
}
