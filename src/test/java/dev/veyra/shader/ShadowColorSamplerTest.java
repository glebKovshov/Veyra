package dev.veyra.shader;

import java.io.IOException;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ShadowColorSamplerTest {
    private static final String SOURCE = "uniform sampler2D shadowcolor0;\nvoid main(){gl_FragColor=texture2D(shadowcolor0,vec2(0.5));}\n";
    @Test void bindsBothNamesAndLeavesContiguousTerrainDescriptors() throws IOException {
        String terrain = TerrainShaderAdapter.adaptFragment(SOURCE, true);
        assertTrue(terrain.contains("layout(binding = 6) uniform sampler2D VeyraShadowColorSampler;"));
        assertTrue(terrain.contains("#define shadowcolor VeyraShadowColorSampler"));
        String alias = SOURCE.replace("shadowcolor0", "shadowcolor");
        assertTrue(ShadowColorSampler.used(alias));
        assertTrue(EntityShaderAdapter.adaptFragment(alias, "gbuffers_entities", false, true)
                .contains("#define shadowcolor0 VeyraShadowColorSampler"));
    }
    @Test void bindsTheSecondShadowAttachmentAsASeparateTerrainSampler() throws IOException {
        String source = "uniform sampler2D shadowcolor0;\nuniform sampler2D shadowcolor1;\n"
                + "void main(){gl_FragColor=texture2D(shadowcolor0,vec2(0.5))+texture2D(shadowcolor1,vec2(0.5));}\n";
        assertEquals(Set.of(0, 1), ShadowColorSampler.indices(source));
        String terrain = TerrainShaderAdapter.adaptFragment(source, true);
        assertTrue(terrain.contains("layout(binding = 6) uniform sampler2D VeyraShadowColorSampler;"));
        assertTrue(terrain.contains("layout(binding = 7) uniform sampler2D VeyraShadowColor1Sampler;"));
        assertTrue(terrain.contains("#define shadowcolor1 VeyraShadowColor1Sampler"));
    }
    @Test void rejectsMissingShadowAndAttachmentFeedback() {
        assertThrows(IOException.class, () -> TerrainShaderAdapter.adaptFragment(SOURCE, false));
        assertThrows(IOException.class, () -> EntityShaderAdapter.adaptFragment(SOURCE));
        assertThrows(IOException.class, () -> TerrainShaderAdapter.adaptFragment(SOURCE.replace("sampler2D", "sampler2DShadow"), true));
    }
    @Test void unusedDeclarationsAndCommentsDoNotReserveAResource() throws IOException {
        String unused = "uniform sampler2D shadowcolor0;\n// shadowcolor\nvoid main(){gl_FragColor=vec4(1);}\n";
        assertFalse(ShadowColorSampler.used(unused));
        assertFalse(TerrainShaderAdapter.adaptFragment(unused, false).contains(ShadowColorSampler.NAME));
    }
    @Test void preventsNativeSlotCollisionWithSixWaterInputs() throws IOException {
        StringBuilder source = new StringBuilder(SOURCE);
        for (int i=1;i<=6;i++) source.insert(0, "uniform sampler2D colortex"+i+";\n");
        source.append("vec4 inputs(){return ");
        for (int i=1;i<=6;i++) source.append(i==1?"":"+").append("texture2D(colortex").append(i).append(",vec2(0.5))");
        source.append(";}\n");
        String adapted = TerrainShaderAdapter.adaptFragment(source.toString(), true, true);
        assertTrue(adapted.contains("layout(binding = 12) uniform sampler2D VeyraGeometryColor6Sampler;"));
    }
}
