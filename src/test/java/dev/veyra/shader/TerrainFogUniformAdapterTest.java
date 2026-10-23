package dev.veyra.shader;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TerrainFogUniformAdapterTest {
    @Test
    void renamesLocalFogColorThatWouldBeCapturedByTheIrisUniformMacro() throws Exception {
        String adapted = TerrainShaderAdapter.adaptFragment("""
                #version 120
                uniform vec3 fogColor;
                void applyNetherFog(inout vec3 color) {
                    float fog = 0.5;
                    vec3 fogColor = vec3(0.2, 0.3, 0.4);
                    color = mix(color, fogColor, fog);
                }
                void main() {
                    vec3 color = vec3(1.0);
                    applyNetherFog(color);
                    gl_FragColor = vec4(color + fogColor, 1.0);
                }
                """);

        assertTrue(adapted.contains("#define fogColor (FogColor.rgb)"), adapted);
        assertTrue(adapted.contains("vec3 veyraLocalFogColor = vec3(0.2, 0.3, 0.4);"), adapted);
        assertTrue(adapted.contains("color = mix(color, veyraLocalFogColor, fog);"), adapted);
        assertTrue(adapted.contains("vec4(color + fogColor, 1.0)"), adapted);
        assertFalse(adapted.contains("vec3 fogColor ="), adapted);
    }
}
