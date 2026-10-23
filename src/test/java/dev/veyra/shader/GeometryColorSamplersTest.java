package dev.veyra.shader;

import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.util.Map;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class GeometryColorSamplersTest {
    @Test void deduplicatesLegacyAndModernAliasesInTheWaterBridge() throws IOException {
        String source = "uniform sampler2D gaux2;\nuniform sampler2D colortex5;\n"
                + "void main(){gl_FragColor=texture2D(gaux2,vec2(0.5))+texture2D(colortex5,vec2(0.5));}\n";
        var inputs = GeometryColorSamplers.read(source);
        assertEquals(Map.of("gaux2", 5, "colortex5", 5), inputs);
        assertEquals(Set.of(5), GeometryColorSamplers.indices(inputs));
        assertThrows(IOException.class, () -> TerrainShaderAdapter.adaptFragment(source, false));
        String adapted = TerrainShaderAdapter.adaptFragment(source, false, true);
        assertTrue(adapted.contains("layout(binding = 5) uniform sampler2D VeyraGeometryColor5Sampler;"));
        assertEquals(1, adapted.split("uniform sampler2D VeyraGeometryColor5Sampler;", -1).length - 1);
        assertTrue(adapted.contains("#define gaux2 VeyraGeometryColor5Sampler"));
        assertTrue(adapted.contains("#define colortex5 VeyraGeometryColor5Sampler"));
    }

    @Test void acceptsAttachmentFeedbackForTheRuntimeSnapshotBridge() throws IOException {
        String source = "/* RENDERTARGETS:0,5 */\nuniform sampler2D gaux2;\n"
                + "void main(){gl_FragColor=texture2D(gaux2,vec2(0.5));}\n";
        var inputs = GeometryColorSamplers.read(source);
        assertEquals(Map.of("gaux2", 5), inputs);
        assertEquals(Set.of(5), GeometryColorSamplers.indices(inputs));
    }

    @Test void adaptsEntityAttachmentFeedbackToASnapshotSampler() throws IOException {
        String source = "/* RENDERTARGETS:0,4 */\nuniform sampler2D colortex4;\n"
                + "void main(){vec4 c=texture2D(colortex4,vec2(0.5));"
                + "gl_FragData[0]=c;gl_FragData[1]=c;}\n";
        assertThrows(IOException.class, () -> EntityShaderAdapter.adaptFragment(
                source, "gbuffers_entities", false, false));
        String adapted = EntityShaderAdapter.adaptGeometryFragment(source, "gbuffers_entities",
                false, false, -1, -1, ItemIdMap.UNMAPPED, true, false);
        assertTrue(adapted.contains("uniform sampler2D VeyraGeometryColor4Sampler;"));
        assertTrue(adapted.contains("#define colortex4 VeyraGeometryColor4Sampler"));
        assertTrue(adapted.contains("veyraEntitySample(colortex4"));
    }

    @Test void ignoresUnusedAndCommentedColorInputs() throws IOException {
        assertTrue(GeometryColorSamplers.read("// uniform sampler2D colortex5;\n"
                + "uniform sampler2D colortex16;\nvoid main(){gl_FragColor=vec4(1.0);}\n").isEmpty());
    }

    @Test void configuredCustomTexturesTakePrecedenceOverLegacyColorAliases() throws IOException {
        String source = "uniform sampler2D gaux4;\n"
                + "void main(){gl_FragColor=texture2D(gaux4,vec2(0.5));}\n";
        assertEquals(Map.of("gaux4", 7), GeometryColorSamplers.read(source));
        assertTrue(GeometryColorSamplers.read(source, Set.of("gaux4")).isEmpty());

        String sceneAlias = "uniform sampler2D gcolor;\n"
                + "void main(){gl_FragColor=texture2D(gcolor,vec2(0.5));}\n";
        assertThrows(IOException.class, () -> GeometryColorSamplers.read(sceneAlias));
        assertTrue(GeometryColorSamplers.read(sceneAlias, Set.of("gcolor")).isEmpty());
    }

    @Test void rejectsSceneFeedbackInvalidTypesAndOutOfRangeInputs() {
        for (String declaration : new String[]{"sampler2D colortex0", "sampler2D gcolor",
                "sampler2D colortex16", "samplerCube colortex5"}) {
            String name = declaration.substring(declaration.indexOf(' ') + 1);
            assertThrows(IOException.class, () -> GeometryColorSamplers.read("uniform " + declaration
                    + ";\nvoid main(){gl_FragColor=texture(" + name + ",vec2(0.5));}\n"));
        }
    }

    @Test void acceptsAllFifteenShaderPackColorTargets() throws IOException {
        StringBuilder declarations = new StringBuilder(), sum = new StringBuilder("vec4(0.0)");
        for (int index = 1; index <= 15; index++) {
            declarations.append("uniform sampler2D colortex").append(index).append(";\n");
            sum.append("+texture2D(colortex").append(index).append(",vec2(0.5))");
        }
        String source = declarations + "void main(){gl_FragColor=" + sum + ";}\n";
        assertEquals(15, GeometryColorSamplers.read(source).size());
    }

    @Test void appendsColorBindingsAfterOptionalDepthNoiseAndShadow() throws IOException {
        String source = "uniform sampler2D depthtex1;\nuniform sampler2D noisetex;\n"
                + "uniform sampler2D colortex5;\nuniform sampler2D gaux1;\n"
                + "void main(){gl_FragColor=texture2D(depthtex1,vec2(0.5))+texture2D(noisetex,vec2(0.5))"
                + "+texture2D(colortex5,vec2(0.5))+texture2D(gaux1,vec2(0.5));}\n";
        String adapted = TerrainShaderAdapter.adaptFragment(source, true, true);
        assertTrue(adapted.contains("layout(binding = 8) uniform sampler2D VeyraGeometryColor4Sampler;"));
        assertTrue(adapted.contains("layout(binding = 9) uniform sampler2D VeyraGeometryColor5Sampler;"));
    }

    @Test void unusedNoiseDoesNotShiftRuntimeColorBindings() throws IOException {
        String source = "uniform sampler2D noisetex;\nuniform sampler2D colortex5;\n"
                + "void main(){gl_FragColor=texture2D(colortex5,vec2(0.5));}\n";
        assertFalse(TerrainShaderAdapter.usesNoiseSampler(source));
        String adapted = TerrainShaderAdapter.adaptFragment(source, false, true);
        assertFalse(adapted.contains("VeyraNoiseSampler"));
        assertTrue(adapted.contains("layout(binding = 5) uniform sampler2D VeyraGeometryColor5Sampler;"));
    }

    @Test void handlesPreprocessorBlankLinesWithoutQuadraticSearch() {
        String source = "uniform sampler2D colortex5;\n" + "\n".repeat(60_000)
                + "void main(){gl_FragColor=texture2D(colortex5,vec2(0.5));}\n";
        assertTimeoutPreemptively(java.time.Duration.ofSeconds(5), () -> {
            assertEquals(Map.of("colortex5", 5), GeometryColorSamplers.read(source));
            assertTrue(TerrainShaderAdapter.adaptFragment(source, false, true).contains("VeyraGeometryColor5Sampler"));
        });
    }
}
