package dev.veyra.shader;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShaderPackComputeStagesTest {
    @Test
    void discoversCompositeComputeComponentsInIrisOrder() throws Exception {
        ShaderPack pack = ShaderPack.inspect(Path.of("examples/composite-custom-image")).withRuntimeFeatures();

        assertEquals(List.of("composite.csh", "composite_a.csh", "composite1.csh"),
                pack.activeComputeStageFiles("composite"));
        assertEquals(List.of("shadowcomp.csh", "prepare.csh", "deferred.csh", "deferred2.csh", "composite.csh",
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

    @Test
    void rejectsComputeStagesWithoutAnImageOrAllocatedStorageBuffer(@TempDir Path root) throws Exception {
        Path shaders = Files.createDirectories(root.resolve("shaders"));
        Files.writeString(shaders.resolve("shaders.properties"), "iris.features.required=COMPUTE_SHADERS\n");
        Files.writeString(shaders.resolve("composite.csh"), """
                #version 430
                layout(local_size_x=1, local_size_y=1, local_size_z=1) in;
                void main() { }
                """);

        var pack = ShaderPack.inspect(root).withRuntimeFeatures();

        var error = assertThrows(java.io.IOException.class, pack::validatePipelineCapabilities);
        assertTrue(error.getMessage().contains("must use an active storage image or an allocated bufferObject SSBO"),
                error.getMessage());
    }

    @Test
    void acceptsComputeOnlyPacksThatUseBufferObjectsWithoutCustomImages() throws Exception {
        var pack = ShaderPack.inspect(Path.of("examples/compute-ssbo-only")).withRuntimeFeatures();

        assertTrue(pack.activeComputeStageFiles().contains("deferred.csh"));
        assertDoesNotThrow(pack::validatePipelineCapabilities);
    }

    @Test
    void acceptsBeginComputeStagesThatRunBeforeShadowRendering() throws Exception {
        var pack = ShaderPack.inspect(Path.of("examples/compute-begin-stage")).withRuntimeFeatures();

        assertEquals(List.of("begin.csh"), pack.activeComputeStageFiles());
        assertEquals(List.of("begin.csh"), pack.activeComputeStageFiles("begin"));
        assertDoesNotThrow(pack::validatePipelineCapabilities);
    }

    @Test
    void discoversBeginFragmentProgramBeforeTheShadowPass() throws Exception {
        var pack = ShaderPack.inspect(Path.of("examples/begin-fragment-stage")).withRuntimeFeatures();

        assertEquals(List.of("begin", "final"),
                pack.readPrograms().stream().map(ShaderPack.Program::name).toList());
        assertTrue(pack.hasPostProcessing());
        assertDoesNotThrow(pack::validatePipelineCapabilities);
    }

    @Test
    void acceptsSetupComputeStagesThatRunWhenThePipelineIsInitialized() throws Exception {
        var pack = ShaderPack.inspect(Path.of("examples/compute-setup-stage")).withRuntimeFeatures();

        assertEquals(List.of("setup.csh"), pack.activeComputeStageFiles());
        assertEquals(List.of("setup.csh"), pack.activeComputeStageFiles("setup"));
        assertDoesNotThrow(pack::validatePipelineCapabilities);
    }
}
