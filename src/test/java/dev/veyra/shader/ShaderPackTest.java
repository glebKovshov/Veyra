package dev.veyra.shader;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.lwjgl.vulkan.VK10;

import static org.junit.jupiter.api.Assertions.*;

class ShaderPackTest {
    @TempDir Path directory;

    @Test
    void acceptsShadowMrtBeforeAllocationEvenWithoutVertexStage() throws IOException {
        Path shaders = Files.createDirectories(directory.resolve("shadow-mrt/shaders"));
        Files.writeString(shaders.resolve("final.fsh"), "void main() { gl_FragColor = vec4(1.0); }\n");
        Files.writeString(shaders.resolve("shadow.fsh"),
                "/* DRAWBUFFERS:01 */\nvoid main() { gl_FragData[0] = vec4(1.0); gl_FragData[1] = vec4(0.0); }\n");
        ShaderPack pack = ShaderPack.inspect(shaders.getParent());
        assertDoesNotThrow(pack::validatePipelineCapabilities);
        assertEquals(java.util.Set.of(0, 1), pack.readShadowColorTargetIndices());
    }

    @Test
    void rejectsHigherShadowColorTargetsUntilTheIrisFeatureIsAdvertised() throws IOException {
        Path shaders = Files.createDirectories(directory.resolve("shadow-higher/shaders"));
        Files.writeString(shaders.resolve("final.fsh"), "void main() { gl_FragColor = vec4(1.0); }\n");
        Files.writeString(shaders.resolve("shadow.fsh"),
                "/* DRAWBUFFERS:012 */\nvoid main() { gl_FragData[0] = vec4(1.0); gl_FragData[1] = vec4(0.0); gl_FragData[2] = vec4(0.0); }\n");
        ShaderPack pack = ShaderPack.inspect(shaders.getParent());
        IOException error = assertThrows(IOException.class, pack::readShadowColorTargetIndices);
        assertTrue(error.getMessage().contains("HIGHER_SHADOWCOLOR"), error.getMessage());
    }

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
    void readsEveryColorTargetFormatFromOneMultilineCommentBlock() throws IOException {
        Path shaders = Files.createDirectories(directory.resolve("formats/shaders"));
        Path lib = Files.createDirectories(shaders.resolve("lib"));
        Files.writeString(shaders.resolve("final.fsh"), "#include \"/lib/pipelineSettings.glsl\"\nvoid main() {}\n");
        Files.writeString(lib.resolve("pipelineSettings.glsl"), """
                /*
                const int colortex0Format = R11F_G11F_B10F;
                const int colortex1Format = RGB8_SNORM; // signed normal data
                const int colortex2Format = RGB16F;
                const int colortex3Format = RGBA8;
                const int colortex4Format = RGBA8_SNORM;
                const int colortex6Format = RGB8;
                const int colortex7Format = RGBA16F;
                const int colortex13Format = R8;
                */
                """);

        var settings = ShaderPack.inspect(shaders.getParent())
                .readColorTargetSettings(640, 360, 16_384);

        assertEquals(VK10.VK_FORMAT_B10G11R11_UFLOAT_PACK32, settings.get(0).format());
        assertEquals(VK10.VK_FORMAT_R8G8B8_SNORM, settings.get(1).format());
        assertEquals(VK10.VK_FORMAT_R16G16B16_SFLOAT, settings.get(2).format());
        assertEquals(VK10.VK_FORMAT_R8G8B8A8_UNORM, settings.get(3).format());
        assertEquals(VK10.VK_FORMAT_R8G8B8A8_SNORM, settings.get(4).format());
        assertEquals(VK10.VK_FORMAT_R8G8B8_UNORM, settings.get(6).format());
        assertEquals(VK10.VK_FORMAT_R16G16B16A16_SFLOAT, settings.get(7).format());
        assertEquals(VK10.VK_FORMAT_R8_UNORM, settings.get(13).format());
        assertEquals(3, ColorTargetSettings.formatSize(settings.get(1).format()));
        assertEquals(6, ColorTargetSettings.formatSize(settings.get(2).format()));
        assertEquals(4, ColorTargetSettings.formatSize(settings.get(0).format()));
        assertEquals("R11F_G11F_B10F", ColorTargetSettings.formatName(settings.get(0).format()));
        assertEquals("RGBA16F", ColorTargetSettings.formatName(settings.get(7).format()));
    }

    @Test
    void ignoresOptifineOnlyEntitiesGlowingProgramLikeIris() throws IOException {
        Path shaders = Files.createDirectories(directory.resolve("glowing/shaders"));
        Files.writeString(shaders.resolve("gbuffers_entities.fsh"), "void main() { gl_FragColor = vec4(1.0); }\n");
        Files.writeString(shaders.resolve("gbuffers_entities_glowing.fsh"), "void main() { gl_FragColor = vec4(1.0); }\n");
        Files.writeString(shaders.resolve("gbuffers_entities_glowing.vsh"), ""
                + "uniform int entityId;\nvoid main() { gl_Position = vec4(float(entityId)); }\n");

        ShaderPack pack = ShaderPack.inspect(shaders.getParent());
        assertTrue(pack.supportsShaderPrograms());
        assertDoesNotThrow(pack::validatePipelineCapabilities);
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
