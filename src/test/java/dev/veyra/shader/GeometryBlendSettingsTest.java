package dev.veyra.shader;

import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.util.Properties;
import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.vulkan.VK10.*;

class GeometryBlendSettingsTest {
    @Test void inheritsMinecraftStateWhenNoOverrideExists() throws IOException {
        assertNull(GeometryBlendSettings.read(new Properties(), "gbuffers_terrain").forTarget(0));
    }
    @Test void bufferOverrideWinsOverProgramGlobalWithoutChangingOtherTargets() throws IOException {
        var settings = GeometryBlendSettings.read(ShaderPackOptions.loadProperties(
                "blend.gbuffers_water=SRC_ALPHA ONE_MINUS_SRC_ALPHA ONE ZERO\nblend.gbuffers_water.colortex4=off\n"), "gbuffers_water");
        assertTrue(settings.forTarget(0).enabled());
        assertEquals(VK_BLEND_FACTOR_SRC_ALPHA, settings.forTarget(0).sourceColor());
        assertEquals(VK_BLEND_FACTOR_ONE_MINUS_SRC_ALPHA, settings.forTarget(0).destinationColor());
        assertEquals(VK_BLEND_FACTOR_ONE, settings.forTarget(0).sourceAlpha());
        assertEquals(VK_BLEND_FACTOR_ZERO, settings.forTarget(0).destinationAlpha());
        assertFalse(settings.forTarget(4).enabled());
        assertTrue(settings.forTarget(5).enabled());
    }
    @Test void mapsLegacyNamesToPhysicalBuffers() throws IOException {
        var settings = GeometryBlendSettings.read(ShaderPackOptions.loadProperties(
                "blend.gbuffers_terrain.gaux1=off\nblend.gbuffers_terrain.gnormal=ONE ZERO ONE ZERO\n"), "gbuffers_terrain");
        assertFalse(settings.forTarget(4).enabled());
        assertTrue(settings.forTarget(2).enabled());
        assertNull(settings.forTarget(1));
    }
    @Test void doesNotConfuseProgramNamePrefixes() throws IOException {
        var settings = GeometryBlendSettings.read(ShaderPackOptions.loadProperties(
                "blend.gbuffers_terrain_solid=off\n"), "gbuffers_terrain");
        assertNull(settings.forTarget(0));
    }
    @Test void rejectsMalformedAndConflictingProperties() {
        for (String property : new String[]{"blend.gbuffers_water=SRC_ALPHA ZERO", "blend.gbuffers_water=INVALID ZERO ONE ZERO",
                "blend.gbuffers_water.colortex16=off", "blend.gbuffers_water.colortex4=off\nblend.gbuffers_water.gaux1=off"})
            assertThrows(IOException.class, () -> GeometryBlendSettings.read(ShaderPackOptions.loadProperties(property), "gbuffers_water"));
    }
}
