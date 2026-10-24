package dev.veyra.shader;

import java.io.IOException;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IrisRenderStagesTest {
    @Test
    void exposesIrisRenderStageMacrosWithIrisOrdinals() throws IOException {
        var macros = IrisRenderStages.macros();
        assertEquals(24, macros.size());
        assertEquals("0", macros.get("MC_RENDER_STAGE_NONE"));
        assertEquals("1", macros.get("MC_RENDER_STAGE_SKY"));
        assertEquals("2", macros.get("MC_RENDER_STAGE_SUNSET"));
        assertEquals("5", macros.get("MC_RENDER_STAGE_MOON"));
        assertEquals("6", macros.get("MC_RENDER_STAGE_STARS"));
        assertEquals("23", macros.get("MC_RENDER_STAGE_HAND_TRANSLUCENT"));
        assertEquals("20", macros.get("MC_RENDER_STAGE_CLOUDS"));

        String processed = GlslPreprocessor.process("render-stage.glsl", """
                #if MC_RENDER_STAGE_MOON == 5 && MC_RENDER_STAGE_STARS == 6
                const int irisStagesAvailable = 1;
                #else
                #error Iris render-stage macros have incorrect values
                #endif
                """);
        assertTrue(processed.contains("irisStagesAvailable"));
    }

    @Test
    void mapsTheThreeSkyFormatsAndPipelinesToIrisStages() {
        assertEquals(1, IrisRenderStages.forSkyFormat(BasicVertexShaderAdapter.Format.SKY));
        assertEquals(6, IrisRenderStages.forSkyFormat(BasicVertexShaderAdapter.Format.STARS));
        assertEquals(2, IrisRenderStages.forSkyFormat(BasicVertexShaderAdapter.Format.SUNRISE_SUNSET));
        assertEquals(12, IrisRenderStages.forSkyFormat(BasicVertexShaderAdapter.Format.SIGN_TEXT));
        assertEquals(1, IrisRenderStages.forSkyPipeline("pipeline/sky"));
        assertEquals(6, IrisRenderStages.forSkyPipeline("pipeline/stars"));
        assertEquals(2, IrisRenderStages.forSkyPipeline("pipeline/sunrise_sunset"));
        assertNull(IrisRenderStages.forSkyFormat(BasicVertexShaderAdapter.Format.LINE));
        assertNull(IrisRenderStages.forSkyPipeline("pipeline/other"));
    }

    @Test
    void mapsEntityHandBlockAndDamageProgramsToTheirIrisStages() {
        assertEquals(8, IrisRenderStages.forProgram("gbuffers_terrain"));
        assertEquals(11, IrisRenderStages.forProgram("gbuffers_entities_translucent"));
        assertEquals(11, IrisRenderStages.forProgram("shadow_entities"));
        assertEquals(11, IrisRenderStages.forProgram("gbuffers_spidereyes"));
        assertEquals(12, IrisRenderStages.forProgram("gbuffers_block"));
        assertEquals(12, IrisRenderStages.forProgram("gbuffers_block_translucent"));
        assertEquals(12, IrisRenderStages.forProgram("shadow_block"));
        assertEquals(12, IrisRenderStages.forProgram("gbuffers_text"));
        assertEquals(13, IrisRenderStages.forProgram("gbuffers_damagedblock"));
        assertEquals(16, IrisRenderStages.forProgram("gbuffers_hand"));
        assertEquals(23, IrisRenderStages.forProgram("gbuffers_hand_water"));
        assertEquals(20, IrisRenderStages.forProgram("gbuffers_clouds"));
        assertNull(IrisRenderStages.forProgram("gbuffers_weather"));
    }

    @Test
    void specializesCelestialAndEndSkyPipelinesForTheirDrawStages() {
        assertEquals(4, IrisRenderStages.sunStage());
        assertEquals(5, IrisRenderStages.moonStage());
        assertEquals(7, IrisRenderStages.voidStage());
        assertEquals(java.util.List.of(4, 5, 7), IrisRenderStages.skyTexturedStages("pipeline/celestial"));
        assertEquals(java.util.List.of(7), IrisRenderStages.skyTexturedStages("pipeline/end_sky"));
        assertTrue(IrisRenderStages.hasRenderStageUniform("uniform int renderStage;"));
        assertFalse(IrisRenderStages.hasRenderStageUniform("// uniform int renderStage;\n"));
    }

    @Test
    void bindsOnlyAnIntegerRenderStageUniform() throws IOException {
        String source = "uniform int renderStage;\nvoid main() { int stage = renderStage; }\n";
        String bound = IrisRenderStages.bindUniform(source, 6, "gbuffers_skybasic");
        assertTrue(bound.contains("const int renderStage = 6;"));
        assertFalse(bound.contains("uniform int renderStage"));
        assertEquals(source, IrisRenderStages.bindUniform(source, null, "gbuffers_line"));

        IOException wrongType = assertThrows(IOException.class, () -> IrisRenderStages.bindUniform(
                "uniform float renderStage;\nvoid main() { float stage = renderStage; }\n",
                1, "gbuffers_skybasic"));
        assertTrue(wrongType.getMessage().contains("must have type int"));
    }
}
