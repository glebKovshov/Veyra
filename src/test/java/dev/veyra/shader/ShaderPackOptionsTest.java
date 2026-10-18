package dev.veyra.shader;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class ShaderPackOptionsTest {
    @TempDir Path root;
    private final String source = "#define QUALITY 2 // Quality [0 1 2 3]\n//#define BLOOM // Bloom\n#ifdef BLOOM\n#endif\nconst float shadowDistance = 128.0; // [64.0 128.0 256.0]\n";
    @Test void discoversOptionsProfilesScreensAndLabels() throws IOException {
        var options = ShaderPackOptions.parse(Map.of("common", source),
                "profile.LOW=QUALITY=0 !BLOOM shadowDistance=64.0\nprofile.HIGH=profile.LOW QUALITY=3 BLOOM\nscreen=QUALITY [LIGHTING] <profile>\nscreen.LIGHTING=BLOOM shadowDistance\nsliders=QUALITY shadowDistance\n", "option.QUALITY=Quality\n");
        assertEquals(3, options.options().size());
        assertEquals("false", options.options().get("BLOOM").defaultValue());
        assertEquals("3", options.profiles().get("HIGH").get("QUALITY"));
        assertEquals("true", options.profiles().get("HIGH").get("BLOOM"));
        assertEquals("Quality", options.label("option.QUALITY", "fallback"));
        assertTrue(options.sliders().contains("QUALITY"));
        assertTrue(options.screen("").contains("[LIGHTING]"));
    }
    @Test void rejectsInjectionAndUnknownValues() throws IOException {
        var options = ShaderPackOptions.parse(Map.of("s", source), "", "");
        assertThrows(IOException.class, () -> options.validate(Map.of("QUALITY", "99")));
        assertThrows(IOException.class, () -> options.validate(Map.of("QUALITY", "1\n#error injected")));
        assertThrows(IOException.class, () -> options.validate(Map.of("UNKNOWN", "1")));
        assertTrue(options.validate(Map.of("QUALITY", "2")).isEmpty());
    }
    @Test void appliesOnlyActualDeclarations() {
        String decorated = "/*\n#define QUALITY 9\n*/\n" + source;
        String result = ShaderPackOptions.apply(decorated, Map.of("QUALITY", "3", "BLOOM", "true", "shadowDistance", "256.0"));
        assertTrue(result.contains("/*\n#define QUALITY 9\n*/"));
        assertTrue(result.contains("#define QUALITY 3"));
        assertFalse(result.contains("//#define BLOOM"));
        assertTrue(result.contains("shadowDistance = 256.0;"));
    }
    @Test void conflictingOptionsAreNotSilentlyOverridden() throws IOException {
        var options = ShaderPackOptions.parse(Map.of("a", "#define QUALITY 1 // [1 2]\n", "b", "#define QUALITY 2 // [1 2]\n"), "", "");
        assertFalse(options.options().containsKey("QUALITY"));
        assertFalse(options.diagnostics().isEmpty());
    }
    @Test void profileCyclesAreBoundedAndReported() throws IOException {
        var options = ShaderPackOptions.parse(Map.of("a", source), "profile.A=profile.B QUALITY=1\nprofile.B=profile.A\n", "");
        assertTrue(options.diagnostics().stream().anyMatch(text -> text.contains("cycle")));
    }
    @Test void persistsPerPackWithoutEditingSourceAndRestoresConfiguration() throws IOException {
        Path shader = Files.createDirectories(root.resolve("shaderpacks/pack/shaders"));
        Files.writeString(shader.resolve("final.fsh"), source + "void main(){gl_FragColor=vec4(QUALITY);}\n");
        ShaderPackRepository repository = new ShaderPackRepository(root); repository.scan();
        ShaderPack pack = repository.getPacks().getFirst();
        repository.select(pack.configured(Map.of("QUALITY", "3"), 0));
        ShaderPackRepository restored = new ShaderPackRepository(root); restored.scan();
        ShaderPack selected = restored.getSelected().orElseThrow();
        assertEquals("3", selected.configuration().options().get("QUALITY"));
        assertTrue(selected.readPrograms().getFirst().source().contains("vec4(3)"));
        assertTrue(Files.readString(shader.resolve("final.fsh")).contains("#define QUALITY 2"));
        assertTrue(repository.loadConfiguration(pack).configuration().options().containsKey("QUALITY"));
    }
    @Test void dimensionProgramsOverrideRootAndResolveRelativeIncludes() throws IOException {
        Path shader = Files.createDirectories(root.resolve("pack/shaders/world0"));
        Files.writeString(shader.resolve("final.fsh"), "#include \"color.glsl\"\nvoid main(){gl_FragColor=chosen;}\n");
        Files.writeString(shader.resolve("color.glsl"), "const vec4 chosen=vec4(0.25);\n");
        Path base = shader.getParent();
        Files.writeString(base.resolve("final.fsh"), "void main(){gl_FragColor=vec4(1.0);}\n");
        ShaderPack pack = ShaderPack.inspect(base.getParent());
        assertTrue(pack.supportsShaderPrograms());
        assertTrue(pack.readPrograms().getFirst().source().contains("vec4(0.25)"));
        assertTrue(pack.configured(Map.of(), -1).readPrograms().getFirst().source().contains("vec4(1.0)"));
    }
    @Test void honorsConditionalProgramsAndRefreshesSourceSnapshots() throws IOException {
        Path shader = Files.createDirectories(root.resolve("pack/shaders"));
        Files.writeString(shader.resolve("final.fsh"), source + "void main(){gl_FragColor=vec4(QUALITY);}\n");
        Files.writeString(shader.resolve("shaders.properties"), "program.final.enabled=QUALITY > 1\n");
        ShaderPack pack = ShaderPack.inspect(shader.getParent());
        assertEquals(1, pack.readPrograms().size());
        assertTrue(pack.configured(Map.of("QUALITY", "0"), 0).readPrograms().isEmpty());
        Files.writeString(shader.resolve("final.fsh"), "void main(){gl_FragColor=vec4(0.5);}\n");
        assertTrue(pack.readPrograms().getFirst().source().contains("vec4(2)"));
        Files.writeString(shader.resolve("shaders.properties"), "");
        assertTrue(ShaderPack.inspect(shader.getParent()).readPrograms().getFirst().source().contains("vec4(0.5)"));
    }
    @Test void optionReplacementPreservesBlockCommentBoundaries() {
        String text = "#define QUALITY 2 /* [1 2 3]\n#define HIDDEN 9\n*/\n";
        String changed = ShaderPackOptions.apply(text, Map.of("QUALITY", "3", "HIDDEN", "0"));
        assertEquals(text.replace("QUALITY 2", "QUALITY 3"), changed);
    }
    @Test void screenColumnsAreBoundedAndInherited() throws IOException {
        var options = ShaderPackOptions.parse(Map.of("s", source),
                "screen.columns=3\nscreen.WATER.columns=99\nscreen.SKY.columns=invalid\n", "");
        assertEquals(3, options.columns(""));
        assertEquals(3, options.columns("LIGHTING"));
        assertEquals(4, options.columns("WATER"));
        assertEquals(2, options.columns("SKY"));
    }
}
