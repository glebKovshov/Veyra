package dev.veyra.shader;

import java.io.IOException;
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
        assertThrows(IOException.class, () -> TerrainShaderAdapter.adaptFragment(source.toString(), true, true));
        String five = source.toString().replace("uniform sampler2D colortex6;\n", "").replace("+texture2D(colortex6,vec2(0.5))", "");
        String adapted = TerrainShaderAdapter.adaptFragment(five, true, true);
        assertTrue(adapted.contains("layout(binding = 11) uniform sampler2D VeyraGeometryColor5Sampler;"));
    }
}
