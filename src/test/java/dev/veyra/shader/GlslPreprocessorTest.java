package dev.veyra.shader;

import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;

class GlslPreprocessorTest {
    @Test void removesInactiveUnsupportedInterfaces() throws IOException {
        String source = "#version 120\n#define QUALITY 2\n#if QUALITY > 1\nvarying vec2 texcoord;\n#else\nuniform samplerCube unsupported;\n#endif\nvoid main(){gl_FragColor=vec4(QUALITY);}\n";
        String processed = GlslPreprocessor.process("test.fsh", source);
        assertFalse(processed.contains("unsupported"));
        assertTrue(processed.contains("vec4(2)"));
        assertDoesNotThrow(() -> TerrainShaderAdapter.adaptFragment(processed));
    }
    @Test void expandsFunctionMacrosAndKeepsRenderDirectives() throws IOException {
        String result = GlslPreprocessor.process("test", "#version 330\n#define COLOR(x) vec4(x)\n/* RENDERTARGETS:0,2 */\nvoid main(){gl_FragColor=COLOR(1.0);}\n");
        assertTrue(result.contains("RENDERTARGETS:0,2"));
        assertTrue(result.contains("vec4(1.0)"));
        assertFalse(result.contains("#define"));
    }
    @Test void hoistsOnlyActiveExtensions() throws IOException {
        String result = GlslPreprocessor.process("test", "#version 120\n#if 0\n#extension GL_NOT_REAL : require\n#endif\n#extension GL_ARB_shader_texture_lod : enable\nvoid main(){}\n");
        assertTrue(result.startsWith("#version 120\n#extension GL_ARB_shader_texture_lod : enable"));
        assertFalse(result.contains("GL_NOT_REAL"));
    }
    @Test void missingAndRecursiveIncludesRespectConditions() throws IOException {
        String source = ShaderIncludes.expand("test", "#if 0\n#include \"missing\"\n#endif\n#include \"guard\"\n", p -> p.equals("guard")
                ? Optional.of("#ifndef GUARD\n#define GUARD\n#include \"guard\"\n#endif\n") : Optional.empty());
        assertDoesNotThrow(() -> GlslPreprocessor.process("test", source));
        assertThrows(IOException.class, () -> GlslPreprocessor.process("test", "#error actual failure\n"));
    }
    @Test void rejectsRawIncludesWithoutAccessingTheHostFilesystem() {
        assertThrows(IOException.class, () -> GlslPreprocessor.process("test", "#include </etc/passwd>\n"));
    }
    @Test void protectsPropertyNamesAndSupportsConditionalsAndContinuations() throws IOException {
        String source = "#if QUALITY > 1\nscreen=QUALITY BLOOM \\\n EXPOSURE\n#else\nscreen=LOW\n#endif\n# human comment\n";
        String result = GlslPreprocessor.properties(source, Map.of("QUALITY", "2", "BLOOM", "true"));
        var properties = ShaderPackOptions.loadProperties(result);
        assertEquals("QUALITY BLOOM EXPOSURE", properties.getProperty("screen"));
        assertFalse(result.contains("screen=LOW"));
    }
    @Test void evaluatesVersionAndMinecraftMacros() throws IOException {
        String result = GlslPreprocessor.process("test", "#version 130\n#if MC_VERSION == 12111 && __VERSION__ == 130\nfloat supported;\n#else\n#error wrong version\n#endif\n");
        assertTrue(result.contains("float supported"));
    }
    @Test void enablesIrisShaderPackCompatibilityBranches() throws IOException {
        String result = GlslPreprocessor.process("test", "#ifdef IS_IRIS\nuniform int renderStage;\n#else\n#error Iris compatibility must be enabled\n#endif\n");
        String bound = IrisRenderStages.bindUniform(result, IrisRenderStages.forShadowTerrainProgram("shadow_solid"), "shadow_solid");
        assertTrue(bound.contains("const int renderStage = 8;"));
        assertFalse(bound.contains("uniform int renderStage;"));
        String terrainBound = IrisRenderStages.bindUniform(result, IrisRenderStages.forProgram("gbuffers_terrain"), "gbuffers_terrain");
        assertTrue(terrainBound.contains("const int renderStage = 8;"));
        String computeBound = IrisRenderStages.bindUniform(result, IrisRenderStages.computeStage(), "shadowcomp.csh");
        assertTrue(computeBound.contains("const int renderStage = 0;"));
        assertFalse(result.contains("#error"));
    }
    @Test void commentedDirectivesStayComments() throws IOException {
        String result = GlslPreprocessor.process("test", "/*\n#version 110\n*/\n#version 330\nvoid main(){}\n");
        assertTrue(result.startsWith("#version 330"));
    }
}
