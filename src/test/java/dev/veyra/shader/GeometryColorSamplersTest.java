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

    @Test void rejectsAttachmentFeedbackEvenWithSparseOutputMasks() {
        String source = "/* RENDERTARGETS:0,5 */\nuniform sampler2D gaux2;\n"
                + "void main(){gl_FragColor=texture2D(gaux2,vec2(0.5));}\n";
        IOException error = assertThrows(IOException.class, () -> GeometryColorSamplers.read(source));
        assertTrue(error.getMessage().contains("framebuffer attachment"));
    }

    @Test void ignoresUnusedAndCommentedColorInputs() throws IOException {
        assertTrue(GeometryColorSamplers.read("// uniform sampler2D colortex5;\n"
                + "uniform sampler2D colortex16;\nvoid main(){gl_FragColor=vec4(1.0);}\n").isEmpty());
    }

    @Test void rejectsSceneFeedbackInvalidTypesAndOutOfRangeInputs() {
        for (String declaration : new String[]{"sampler2D colortex0", "sampler2D gcolor",
                "sampler2D colortex16", "samplerCube colortex5"}) {
            String name = declaration.substring(declaration.indexOf(' ') + 1);
            assertThrows(IOException.class, () -> GeometryColorSamplers.read("uniform " + declaration
                    + ";\nvoid main(){gl_FragColor=texture(" + name + ",vec2(0.5));}\n"));
        }
    }

    @Test void limitsDistinctImagesRatherThanAliasCount() throws IOException {
        StringBuilder declarations = new StringBuilder(), sum = new StringBuilder("vec4(0.0)");
        for (int index = 1; index <= 7; index++) {
            declarations.append("uniform sampler2D colortex").append(index).append(";\n");
            sum.append("+texture2D(colortex").append(index).append(",vec2(0.5))");
            String source = declarations + "void main(){gl_FragColor=" + sum + ";}\n";
            if (index <= 6) assertEquals(index, GeometryColorSamplers.read(source).size());
            else assertTrue(assertThrows(IOException.class, () -> GeometryColorSamplers.read(source))
                    .getMessage().contains("six auxiliary"));
        }
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
}
