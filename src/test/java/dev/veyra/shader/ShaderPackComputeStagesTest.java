package dev.veyra.shader;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
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

    @Test
    void discoversShadowComputeComponentsInIrisOrderAndKeepsNumberedShadowNamesUnsupported(@TempDir Path root)
            throws Exception {
        Path shaders = Files.createDirectories(root.resolve("shaders"));
        for (String file : List.of("setup.csh", "begin.csh", "shadow.csh", "shadow_a.csh",
                "shadow_c.csh", "shadowcomp.csh", "prepare.csh", "shadow1.csh")) {
            Files.writeString(shaders.resolve(file), "void main() {}\n");
        }
        Files.writeString(shaders.resolve("shadow.fsh"), "#version 120\nvoid main() { gl_FragColor=vec4(0.0); }\n");

        ShaderPack pack = ShaderPack.inspect(root).withRuntimeFeatures();

        assertEquals(List.of("setup.csh", "shadow.csh", "shadow_a.csh", "begin.csh",
                        "shadowcomp.csh", "prepare.csh", "shadow1.csh"), pack.activeComputeStageFiles());
        assertEquals(List.of("shadow.csh", "shadow_a.csh"), pack.activeComputeStageFiles("shadow"));
    }

    @Test
    void acceptsPreShadowComputeFixtureWithOrderedComponents() throws Exception {
        var pack = ShaderPack.inspect(Path.of("examples/compute-shadow-stage")).withRuntimeFeatures();

        assertEquals(List.of("shadow.csh", "shadow_a.csh"), pack.activeComputeStageFiles("shadow"));
        assertEquals(32, pack.readShadowSettings().resolution());
        assertDoesNotThrow(pack::validatePipelineCapabilities);
    }

    @Test
    void readsIrisShadowClipPlanesAndTheirDefaults(@TempDir Path root) throws Exception {
        Path shaders = Files.createDirectories(root.resolve("shaders"));
        Files.writeString(shaders.resolve("shadow.fsh"), """
                #version 120
                const float shadowNearPlane = -32.0;
                const float shadowFarPlane = 256.0;
                void main() {}
                """);
        Path defaultRoot = Files.createDirectories(root.resolve("defaults"));
        Path defaultShaders = Files.createDirectories(defaultRoot.resolve("shaders"));
        Files.writeString(defaultShaders.resolve("shadow.fsh"), "void main() {}\n");

        var configured = ShaderPack.inspect(root).readShadowSettings();
        var defaults = ShaderPack.inspect(defaultRoot).readShadowSettings();

        assertEquals(-32.0F, configured.nearPlane(), 0.0F);
        assertEquals(256.0F, configured.farPlane(), 0.0F);
        assertEquals(ShadowSettings.DEFAULT_NEAR_PLANE, defaults.nearPlane(), 0.0F);
        assertEquals(ShadowSettings.DEFAULT_FAR_PLANE, defaults.farPlane(), 0.0F);
    }

    @Test
    void readsIrisShadowEntityTogglesHonouringPropertyConditions(@TempDir Path root) throws Exception {
        Path shaders = Files.createDirectories(root.resolve("shaders"));
        Files.writeString(shaders.resolve("shadow.fsh"), "void main() {}\n");
        Files.writeString(shaders.resolve("shaders.properties"), """
                shadowBlockEntities = false
                #if 0
                shadowEntities = false
                #endif
                """);
        Path defaultRoot = Files.createDirectories(root.resolve("defaults"));
        Files.writeString(Files.createDirectories(defaultRoot.resolve("shaders")).resolve("shadow.fsh"),
                "void main() {}\n");

        var configured = ShaderPack.inspect(root).readShadowSettings();
        var defaults = ShaderPack.inspect(defaultRoot).readShadowSettings();

        org.junit.jupiter.api.Assertions.assertFalse(configured.shadowBlockEntities());
        org.junit.jupiter.api.Assertions.assertTrue(configured.shadowEntities());
        org.junit.jupiter.api.Assertions.assertTrue(defaults.shadowBlockEntities());
        org.junit.jupiter.api.Assertions.assertTrue(defaults.shadowEntities());
    }

    @Test
    void loadsShadowLetterComponentsWithoutSuffixlessComputeAndStopsAtFirstGap(@TempDir Path root)
            throws Exception {
        Path shaders = Files.createDirectories(root.resolve("shaders"));
        for (String file : List.of("shadow_a.csh", "shadow_b.csh", "shadow_d.csh")) {
            Files.writeString(shaders.resolve(file), "void main() {}\n");
        }
        Files.writeString(shaders.resolve("shadow.fsh"), "void main() {}\n");
        var pack = ShaderPack.inspect(root).withRuntimeFeatures();

        assertEquals(List.of("shadow_a.csh", "shadow_b.csh"), pack.activeComputeStageFiles("shadow"));
    }

    @Test
    void rejectsPreShadowComputeWithoutAnActiveShadowPass(@TempDir Path root) throws Exception {
        Path shaders = Files.createDirectories(root.resolve("shaders"));
        Files.writeString(shaders.resolve("shaders.properties"), "iris.features.required=COMPUTE_SHADERS SSBO\nbufferObject.0=16\n");
        Files.writeString(shaders.resolve("composite.fsh"), "#version 120\nvoid main() { gl_FragColor=vec4(0.0); }\n");
        Files.writeString(shaders.resolve("shadow.csh"), """
                #version 430
                layout(local_size_x=1, local_size_y=1, local_size_z=1) in;
                const ivec3 workGroups=ivec3(1,1,1);
                layout(std430, binding=0) buffer VeyraState { uint value; } state;
                void main() { state.value=42u; }
                """);

        var pack = ShaderPack.inspect(root).withRuntimeFeatures();

        IOException error = assertThrows(IOException.class, pack::validatePipelineCapabilities);
        assertTrue(error.getMessage().contains("shadow.csh requires an active shadow pass"), error.getMessage());
    }

    @Test
    void rejectsNumberedShadowComputeNamesThatIrisDoesNotLoad(@TempDir Path root) throws Exception {
        Path shaders = Files.createDirectories(root.resolve("shaders"));
        Files.writeString(shaders.resolve("shaders.properties"), "iris.features.required=COMPUTE_SHADERS SSBO\nbufferObject.0=16\n");
        Files.writeString(shaders.resolve("composite.fsh"), "#version 120\nvoid main() { gl_FragColor=vec4(0.0); }\n");
        Files.writeString(shaders.resolve("shadow1.csh"), """
                #version 430
                layout(local_size_x=1, local_size_y=1, local_size_z=1) in;
                const ivec3 workGroups=ivec3(1,1,1);
                layout(std430, binding=0) buffer VeyraState { uint value; } state;
                void main() { state.value=42u; }
                """);

        var pack = ShaderPack.inspect(root).withRuntimeFeatures();

        assertTrue(pack.activeComputeStageFiles("shadow").isEmpty());
        IOException error = assertThrows(IOException.class, pack::validatePipelineCapabilities);
        assertTrue(error.getMessage().contains("Compute dispatch timing for shadow1.csh is not implemented yet"),
                error.getMessage());
    }
}
