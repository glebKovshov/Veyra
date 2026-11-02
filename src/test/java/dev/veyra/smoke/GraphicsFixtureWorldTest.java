package dev.veyra.smoke;

import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import static org.junit.jupiter.api.Assertions.*;

class GraphicsFixtureWorldTest {
    @TempDir Path directory;

    @Test
    void minecraftReadsFlatAndNaturalFixturesWithTheSameSeedAndUnattendedOptions() throws Exception {
        Path archive = directory.resolve("fixture.zip");
        try (var zip = new ZipOutputStream(Files.newOutputStream(archive))) {
            zip.putNextEntry(new ZipEntry("shaders/final.fsh"));
            zip.write("void main(){gl_FragColor=vec4(1);}".getBytes(StandardCharsets.UTF_8));
        }
        String python = System.getProperty("os.name").startsWith("Windows") ? "python" : "python3";
        for (boolean natural : new boolean[]{false, true}) {
            String fixture = natural ? "third-party-natural-world" : "third-party-shaderpack";
            Path run = directory.resolve(fixture);
            Process process = new ProcessBuilder(python, "scripts/prepare_graphics_smoke.py",
                    "--fixture", fixture, "--root", run.toString(), "--shaderpack-archive", archive.toString(),
                    "--shaderpack-name", "fixture", "--profile", "__DEFAULT__").redirectErrorStream(true).start();
            try {
                assertTrue(process.waitFor(30, TimeUnit.SECONDS), "Fixture generation timed out");
                assertEquals(0, process.exitValue(), new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8));
            } finally {
                if (process.isAlive()) process.destroyForcibly();
            }
            var data = NbtIo.readCompressed(run.resolve("saves/VeyraValidation/level.dat"), NbtAccounter.unlimitedHeap())
                    .getCompound("Data").orElseThrow();
            var world = data.getCompound("WorldGenSettings").orElseThrow();
            assertEquals(1L, world.getLong("seed").orElseThrow());
            assertEquals(natural, world.getBoolean("generate_features").orElseThrow());
            assertEquals(natural ? 200 : -59, data.getInt("SpawnY").orElseThrow());
            var generator = world.getCompound("dimensions").orElseThrow().getCompound("minecraft:overworld")
                    .orElseThrow().getCompound("generator").orElseThrow();
            assertEquals(natural ? "minecraft:noise" : "minecraft:flat", generator.getString("type").orElseThrow());
            if (natural) {
                assertEquals("minecraft:overworld", generator.getString("settings").orElseThrow());
                assertEquals("minecraft:overworld", generator.getCompound("biome_source").orElseThrow()
                        .getString("preset").orElseThrow());
            }
            assertEquals(fixture, Files.readString(run.resolve("veyra/graphics-fixture.txt")));
            String options = Files.readString(run.resolve("options.txt"));
            assertTrue(options.lines().anyMatch("soundCategory_music:0.0"::equals));
            assertTrue(options.lines().anyMatch("soundCategory_master:0.0"::equals));
            assertTrue(options.lines().anyMatch("pauseOnLostFocus:false"::equals));
        }
    }
}
