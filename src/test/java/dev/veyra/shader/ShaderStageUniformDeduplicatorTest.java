package dev.veyra.shader;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ShaderStageUniformDeduplicatorTest {
    @Test
    void removesRepeatedLegacyUniformsFromFragmentAndPreservesSamplerDeclarations() throws Exception {
        String vertex = "uniform int worldTime;\nuniform vec3 cameraPosition;\nuniform sampler2D Sampler0;\n";
        String fragment = "uniform int worldTime;\nuniform vec3 cameraPosition;\n"
                + "uniform float rainFactor;\nuniform sampler2D Sampler0;\n";

        String result = ShaderStageUniformDeduplicator.removeFragmentDuplicates(vertex, fragment, "shadow_entities");

        assertFalse(result.contains("uniform int worldTime;"), result);
        assertFalse(result.contains("uniform vec3 cameraPosition;"), result);
        assertTrue(result.contains("uniform float rainFactor;"), result);
        assertTrue(result.contains("uniform sampler2D Sampler0;"), result);
        assertEquals(fragment.lines().count(), result.lines().count());
    }

    @Test
    void rejectsTheSameUniformNameWithIncompatibleStageTypes() {
        assertThrows(java.io.IOException.class, () -> ShaderStageUniformDeduplicator.removeFragmentDuplicates(
                "uniform int worldTime;\n", "uniform float worldTime;\n", "shadow_entities"));
    }

    @Test
    void ignoresUniformTextInsideComments() throws Exception {
        String fragment = "/* uniform int worldTime; */\n// uniform int worldTime;\nvoid main() {}\n";

        assertEquals(fragment, ShaderStageUniformDeduplicator.removeFragmentDuplicates(
                "uniform int worldTime;\n", fragment, "shadow_entities"));
    }
}
