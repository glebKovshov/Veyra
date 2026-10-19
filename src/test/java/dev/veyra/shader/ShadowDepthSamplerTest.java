package dev.veyra.shader;

import java.io.IOException;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ShadowDepthSamplerTest {
    private static final String SOURCE = "uniform sampler2D shadowtex1;\nvoid main(){gl_FragColor=texture2D(shadowtex1,vec2(0.5));}\n";
    @Test void bindsASeparateSamplerAndRejectsFeedback() throws IOException {
        String terrain = TerrainShaderAdapter.adaptFragment(SOURCE, true);
        assertTrue(terrain.contains("layout(binding = 6) uniform sampler2D VeyraOpaqueShadowSampler;"));
        assertTrue(terrain.contains("#define shadowtex1 VeyraOpaqueShadowSampler"));
        assertThrows(IOException.class, () -> TerrainShaderAdapter.adaptFragment(SOURCE, false));
        assertThrows(IOException.class, () -> EntityShaderAdapter.adaptFragment(SOURCE));
    }
    @Test void supportsComparisonHelperWhenOnlyShadowtex1IsRead() throws IOException {
        String source = "uniform sampler2DShadow shadowtex1;\nfloat sampleDepth(sampler2DShadow s){return texture2DShadow(s,vec3(0.5));}\nvoid main(){gl_FragColor=vec4(sampleDepth(shadowtex1));}\n";
        String entity = EntityShaderAdapter.adaptFragment(source, "gbuffers_entities", false, true);
        assertTrue(entity.contains("float veyraEntityShadowCompare("));
        assertFalse(entity.contains("sampler2DShadow"));
        assertFalse(TerrainShaderAdapter.adaptFragment(source, true).contains("sampler2DShadow"));
    }
    @Test void ignoresUnusedDeclarationsAndRejectsWrongSamplerTypes() throws IOException {
        String unused = "uniform sampler2D shadowtex1;\n// shadowtex1\nvoid main(){gl_FragColor=vec4(1);}\n";
        assertFalse(ShadowDepthSampler.used(unused));
        assertFalse(TerrainShaderAdapter.adaptFragment(unused, false).contains(ShadowDepthSampler.NAME));
        assertThrows(IOException.class, () -> TerrainShaderAdapter.adaptFragment(SOURCE.replace("sampler2D", "samplerCube"), true));
    }
    @Test void budgetsSharedSlotsWithShadowColor() throws IOException {
        ShadowDepthSampler.validate(SOURCE, true, 5);
        assertThrows(IOException.class, () -> ShadowDepthSampler.validate(SOURCE, true, 6));
        String both = SOURCE + "uniform sampler2D shadowcolor0;\nvec4 color(){return texture2D(shadowcolor0,vec2(0.5));}\n";
        ShadowDepthSampler.validate(both, true, 4);
        assertThrows(IOException.class, () -> ShadowDepthSampler.validate(both, true, 5));
    }
}
