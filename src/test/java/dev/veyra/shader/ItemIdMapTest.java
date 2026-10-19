package dev.veyra.shader;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class ItemIdMapTest {
    @Test
    void parsesNamespacesContinuationsAndKeepsFirstId() {
        ItemIdMap map = ItemIdMap.parse("""
                # comment
                item.44002=torch \
                    minecraft:lantern
                item.45000=modid:glow_stick torch
                item.bad=stone
                """);
        assertEquals(44002, map.id("minecraft:torch"));
        assertEquals(44002, map.id("minecraft:lantern"));
        assertEquals(45000, map.id("modid:glow_stick"));
        assertEquals(ItemIdMap.UNMAPPED, map.id("minecraft:stone"));
        assertEquals(3, map.size());
    }

    @Test
    void readsPreprocessedItemPropertiesFromPack(@TempDir Path pack) throws Exception {
        Path shaders = Files.createDirectories(pack.resolve("shaders"));
        Files.writeString(shaders.resolve("gbuffers_terrain.fsh"), "#version 120\nvoid main(){gl_FragColor=vec4(1);}\n");
        Files.writeString(shaders.resolve("item.properties"), """
                #ifdef MC_VERSION
                item.10=torch
                #endif
                item.11=lantern
                """);
        ItemIdMap map = ShaderPack.inspect(pack).readItemIds();
        assertEquals(11, map.id("minecraft:lantern"));
        assertEquals(10, map.id("minecraft:torch"));
    }
}
