package dev.veyra.shader;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IrisFogUniformAdapterTest {
    @Test
    void renamesPackFogFunctionWhenAdapterInjectsTheFogUniformBlock() throws Exception {
        IrisFogUniformAdapter.AdaptedShader adapted = IrisFogUniformAdapter.extract("""
                uniform vec3 fogColor;
                void Fog(inout vec3 color) {
                    color = mix(color, fogColor, 0.5);
                }
                void main() {
                    vec3 color = vec3(1.0);
                    Fog(color);
                }
                """, "post-processing");

        assertTrue(adapted.bridge().contains("layout(std140) uniform Fog {"), adapted.bridge());
        assertTrue(adapted.bridge().contains("#define fogColor (FogColor.rgb)"), adapted.bridge());
        assertTrue(adapted.source().contains("void veyraShaderPackFog(inout vec3 color)"), adapted.source());
        assertTrue(adapted.source().contains("veyraShaderPackFog(color);"), adapted.source());
        assertFalse(adapted.source().contains("void Fog("), adapted.source());
    }

    @Test
    void renamesPackFogFunctionWhenTerrainBridgeProvidesTheFogBlockWithoutFogUniformUsage() throws Exception {
        IrisFogUniformAdapter.AdaptedShader adapted = IrisFogUniformAdapter.extract("""
                void Fog(inout vec3 color) { color *= 0.5; }
                void main() { vec3 color = vec3(1.0); Fog(color); }
                """, "gbuffers_terrain", true);

        assertTrue(adapted.source().contains("void veyraShaderPackFog(inout vec3 color)"), adapted.source());
        assertTrue(adapted.source().contains("veyraShaderPackFog(color);"), adapted.source());
        assertFalse(adapted.source().contains("void Fog("), adapted.source());
        assertTrue(adapted.bridge().isEmpty());
    }

    @Test
    void renamesLocalFogUniformNameBeforeAddingItsMacroAlias() throws Exception {
        IrisFogUniformAdapter.AdaptedShader adapted = IrisFogUniformAdapter.extract("""
                uniform vec3 fogColor;
                void applyNetherFog(inout vec3 color) {
                    vec3 fogColor = vec3(0.2, 0.3, 0.4);
                    color = mix(color, fogColor, 0.5);
                }
                void main() {
                    vec3 color = vec3(1.0);
                    applyNetherFog(color);
                    color += fogColor;
                }
                """, "gbuffers_entities_translucent");

        assertTrue(adapted.bridge().contains("#define fogColor (FogColor.rgb)"), adapted.bridge());
        assertTrue(adapted.source().contains("vec3 veyraLocalFogColor = vec3(0.2, 0.3, 0.4);"), adapted.source());
        assertTrue(adapted.source().contains("color = mix(color, veyraLocalFogColor, 0.5);"), adapted.source());
        assertTrue(adapted.source().contains("color += fogColor;"), adapted.source());
    }
}
