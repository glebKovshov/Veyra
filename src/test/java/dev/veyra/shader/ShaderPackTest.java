package dev.veyra.shader;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class ShaderPackTest {
    @TempDir Path directory;

    @Test
    void readsDirectoryProgramsInNumericOrder() throws IOException {
        Path shaders = Files.createDirectories(directory.resolve("pack/shaders"));
        for (String file : new String[]{"composite10.fsh", "composite2.fsh", "prepare.fsh", "deferred.fsh", "final.fsh"}) {
            Files.writeString(shaders.resolve(file), "void main() {}\n");
        }
        ShaderPack pack = ShaderPack.inspect(shaders.getParent());
        assertTrue(pack.supportsShaderPrograms());
        assertEquals(java.util.List.of("prepare", "deferred", "composite2", "composite10", "final"),
                pack.readPrograms().stream().map(ShaderPack.Program::name).toList());
    }

    @Test
    void readsWrappedZipWithIncludesAndVertexStage() throws IOException {
        Path zip = directory.resolve("pack.zip");
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(zip))) {
            entry(out, "wrapper/shaders/final.fsh", "#include \"lib/shared.glsl\"\nvoid main() {}\n");
            entry(out, "wrapper/shaders/lib/shared.glsl", "float sharedValue;\n");
            entry(out, "wrapper/shaders/final.vsh", "void main() {}\n");
        }
        var programs = ShaderPack.inspect(zip).readPrograms();
        assertEquals(1, programs.size());
        assertTrue(programs.getFirst().source().contains("float sharedValue;"));
        assertTrue(programs.getFirst().vertexSource().isPresent());
    }

    @Test
    void readsAndExpandsCloudVertexProgram() throws IOException {
        Path zip = directory.resolve("clouds.zip");
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(zip))) {
            entry(out, "wrapper/shaders/gbuffers_clouds.fsh", "void main() { gl_FragColor = color; }\n");
            entry(out, "wrapper/shaders/gbuffers_clouds.vsh", "#include \"lib/cloud-vertex.glsl\"\n");
            entry(out, "wrapper/shaders/lib/cloud-vertex.glsl", "varying vec4 color;\nvoid main() { gl_Position = ftransform(); }\n");
        }

        ShaderPack pack = ShaderPack.inspect(zip);
        assertTrue(pack.readCloudVertex().orElseThrow().contains("varying vec4 color;"));
    }

    @Test
    void readsEyeBrightnessHalfLifeFromAnyGlslFileAndUsesIrisDefault() throws IOException {
        Path shaderRoot = Files.createDirectories(directory.resolve("smooth/shaders"));
        Path shaders = Files.createDirectories(shaderRoot.resolve("lib"));
        Files.writeString(shaderRoot.resolve("final.fsh"), "void main() {}\n");
        Files.writeString(shaders.resolve("lighting.glsl"), """
                // const float eyeBrightnessHalflife = 99.0;
                const float eyeBrightnessHalflife = 5.0;
                """);
        Path packPath = shaderRoot.getParent();
        assertEquals(5.0F, ShaderPack.inspect(packPath).readEyeBrightnessHalflife());

        Files.delete(shaders.resolve("lighting.glsl"));
        assertEquals(EyeBrightnessSmoother.DEFAULT_HALF_LIFE_TICKS,
                ShaderPack.inspect(packPath).readEyeBrightnessHalflife());
    }

    @Test
    void rejectsOversizedSource() throws IOException {
        Path shaders = Files.createDirectories(directory.resolve("large/shaders"));
        Files.writeString(shaders.resolve("final.fsh"), "x".repeat(1024 * 1024 + 1));
        assertThrows(IOException.class, () -> ShaderPack.inspect(shaders.getParent()).readPrograms());
    }

    @Test
    void repositoryRefreshDoesNotChangeInstalledSelection() throws IOException {
        Path shaders = Files.createDirectories(directory.resolve("shaderpacks/active/shaders"));
        Files.writeString(shaders.resolve("final.fsh"), "void main() {}\n");
        ShaderPackRepository repository = new ShaderPackRepository(directory);
        repository.scan();
        ShaderPack selected = repository.getPacks().getFirst();
        repository.select(selected);
        assertEquals("active", Files.readString(directory.resolve("veyra/selected-pack.txt")));
        Files.delete(shaders.resolve("final.fsh"));
        repository.refresh();
        assertSame(selected, repository.getSelected().orElseThrow());
        assertTrue(repository.findAvailable(selected.path()).isEmpty());
        repository.select(null);
        assertTrue(repository.getSelected().isEmpty());
        assertEquals("", Files.readString(directory.resolve("veyra/selected-pack.txt")));
    }

    @Test
    void repositoryRestoresSelectionAndPreservesItOnWriteFailure() throws IOException {
        Path shaders = Files.createDirectories(directory.resolve("shaderpacks/active/shaders"));
        Files.writeString(shaders.resolve("final.fsh"), "void main() {}\n");
        ShaderPackRepository repository = new ShaderPackRepository(directory);
        repository.scan();
        repository.select(repository.getPacks().getFirst());
        ShaderPackRepository restored = new ShaderPackRepository(directory);
        restored.scan();
        ShaderPack selected = restored.getSelected().orElseThrow();
        Path config = directory.resolve("veyra/selected-pack.txt");
        Files.delete(config);
        Files.createDirectory(config);
        Files.writeString(config.resolve("blocker"), "cannot replace this directory");
        assertThrows(IOException.class, () -> restored.select(null));
        assertSame(selected, restored.getSelected().orElseThrow());
        restored.clearSelection();
        assertTrue(restored.getSelected().isEmpty());
        try (var entries = Files.list(config.getParent())) {
            assertFalse(entries.anyMatch(path -> path.getFileName().toString().endsWith(".tmp")));
        }
    }

    private void entry(ZipOutputStream out, String path, String text) throws IOException {
        out.putNextEntry(new ZipEntry(path));
        out.write(text.getBytes(StandardCharsets.UTF_8));
        out.closeEntry();
    }
}
