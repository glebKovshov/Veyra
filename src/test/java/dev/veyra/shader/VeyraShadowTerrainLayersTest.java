package dev.veyra.shader;

import net.vulkanmod.render.vertex.TerrainRenderType;
import org.junit.jupiter.api.Test;
import java.util.EnumSet;
import static org.junit.jupiter.api.Assertions.*;

class VeyraShadowTerrainLayersTest {
    @Test void drawsEachOpaqueBackendLayerBeforeTakingTheDepthSnapshot() {
        var layers = VeyraShadowRenderer.OPAQUE_TERRAIN_LAYERS;
        assertEquals(EnumSet.complementOf(EnumSet.of(TerrainRenderType.TRANSLUCENT)), EnumSet.copyOf(layers));
        assertEquals(layers.size(), EnumSet.copyOf(layers).size());
        assertFalse(layers.contains(TerrainRenderType.TRANSLUCENT));
    }
}
