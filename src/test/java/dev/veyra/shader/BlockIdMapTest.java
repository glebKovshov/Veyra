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

        assertEquals(8, map.id("minecraft:oak_log", Map.of("axis", "y"), java.util.Set.of("minecraft:logs")));
        assertEquals(13, map.id("minecraft:oak_log", Map.of("axis", "x"), java.util.Set.of("minecraft:logs")));
        assertEquals(BlockIdMap.UNMAPPED, map.id("minecraft:stone", Map.of()));
    }

    @Test
    void matchesCommaSeparatedPropertyValuesAndRetainsRepeatedIdLines() {
        BlockIdMap map = BlockIdMap.parse("""
                block.21 = minecraft:oak_door:facing=north,south:hinge=right
                block.22 = minecraft:stone
                block.22 = minecraft:dirt
                """);

        assertEquals(21, map.id("minecraft:oak_door", Map.of("facing", "south", "hinge", "right")));
        assertEquals(BlockIdMap.UNMAPPED, map.id("minecraft:oak_door", Map.of("facing", "east", "hinge", "right")));
        assertEquals(22, map.id("minecraft:stone", Map.of()));
        assertEquals(22, map.id("minecraft:dirt", Map.of()));
    }

    @Test
    void enablesCurrentIrisTagPropertiesMacro() throws Exception {
        String properties = GlslPreprocessor.properties("""
                #ifdef IRIS_TAG_SUPPORT
                block.31 = %minecraft:logs
                #endif
                """, Map.of());
        BlockIdMap map = BlockIdMap.parse(properties);

        assertEquals(31, map.id("minecraft:oak_log", Map.of(), java.util.Set.of("minecraft:logs")));
    }
}
