package dev.veyra.shader;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShaderPackComputeStagesTest {
    @Test
    void discoversCompositeComputeComponentsInIrisOrder() throws Exception {
        ShaderPack pack = ShaderPack.inspect(Path.of("examples/composite-custom-image")).withRuntimeFeatures();

        assertEquals(List.of("composite.csh", "composite_a.csh", "composite1.csh"),
                pack.activeComputeStageFiles("composite"));
        assertEquals(List.of("shadowcomp.csh", "prepare.csh", "deferred.csh", "composite.csh",
                        "composite_a.csh", "composite1.csh"),
                pack.activeComputeStageFiles());
        assertTrue(pack.hasPostProcessing());
    }

    @Test
    void computeOnlyPostProgramMakesThePackRenderable(@TempDir Path root) throws Exception {
        Path shaders = Files.createDirectories(root.resolve("shaders"));
        Files.writeString(shaders.resolve("composite2.csh"), """
                #version 430
                layout(local_size_x=1, local_size_y=1, local_size_z=1) in;
                const ivec3 workGroups=ivec3(1,1,1);
                void main() {}
                """);

        ShaderPack pack = ShaderPack.inspect(root);

        assertTrue(pack.hasPostProcessing());
        assertTrue(pack.supportsShaderPrograms());
        assertTrue(pack.readPrograms().isEmpty());
        assertTrue(pack.activeComputeStageFiles().contains("composite2.csh"));
    }
}
