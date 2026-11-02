package dev.veyra.shader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class ShaderPackFeatureFlagsTest {
    @TempDir Path root;

    private ShaderPack pack(String properties, String fragment) throws IOException {
        Path shaders = Files.createDirectories(root.resolve("shaders"));
        Files.writeString(shaders.resolve("shaders.properties"), properties);
        Files.writeString(shaders.resolve("final.fsh"), fragment);
        return ShaderPack.inspect(root);
    }

    @Test void propertiesCanDiscoverSsboAndEnableItsOptionalShaderBranch() throws IOException {
        ShaderPack pack = pack("""
                #ifdef IRIS_FEATURE_SSBO
                iris.features.optional=SSBO UNKNOWN_FEATURE
                #endif
                """, """
                #version 430
                #ifndef IRIS_FEATURE_SSBO
                #error missing optional SSBO feature
                #endif
                #ifdef IRIS_FEATURE_UNKNOWN_FEATURE
                #error unsupported optional feature leaked into GLSL
                #endif
                void main() { gl_FragColor=vec4(1.0); }
                """);
        assertTrue(pack.readStage("final.fsh").orElseThrow().contains("void main()"));
        assertDoesNotThrow(pack::validatePipelineCapabilities);
    }

    @Test void requiredFlagsAreCaseInsensitiveAndDoNotImplicitlyDefineShaderMacros() throws IOException {
        ShaderPack pack = pack("iris.features.required=ssbo\n", """
                #version 430
                #ifdef IRIS_FEATURE_SSBO
                #error required-only flags are not GLSL macros in Iris
                #endif
                void main() { gl_FragColor=vec4(1.0); }
                """);
        assertDoesNotThrow(pack::validatePipelineCapabilities);
    }

    @Test void unknownRequiredFlagIsRejectedEvenWhenOptionalFlagsAreUsable() throws IOException {
        ShaderPack pack = pack("iris.features.required=SSBO UNKNOWN_FEATURE\n"
                        + "iris.features.optional=SSBO\n",
                "void main() { gl_FragColor=vec4(1.0); }\n");
        IOException error = assertThrows(IOException.class, pack::validatePipelineCapabilities);
        assertTrue(error.getMessage().contains("UNKNOWN_FEATURE"));
        assertFalse(error.getMessage().contains("SSBO"));
    }

    @Test void featureOnSnapshotUsesOptionalMacrosInComputeAndFragmentStages() throws IOException {
        ShaderPack ordinary = pack("""
                #ifdef IRIS_FEATURE_COMPUTE_SHADERS
                iris.features.optional=COMPUTE_SHADERS SSBO
                program.composite.enabled=true
                #else
                program.composite.enabled=false
                #endif
                """, """
                #version 430
                #ifdef IRIS_FEATURE_COMPUTE_SHADERS
                const float runtimeBranch=1.0;
                #else
                const float fallbackBranch=0.0;
                #endif
                void main() { gl_FragColor=vec4(1.0); }
                """);
        Files.writeString(root.resolve("shaders/composite.csh"), """
                #version 430
                #ifndef IRIS_FEATURE_SSBO
                #error missing SSBO in compute preprocessing
                #endif
                #ifndef IRIS_FEATURE_COMPUTE_SHADERS
                #error missing compute feature
                #endif
                layout(local_size_x=1) in;
                void main() {}
                """);
        assertTrue(ordinary.activeComputeStageFiles().isEmpty());
        assertTrue(ordinary.readStage("final.fsh").orElseThrow().contains("fallbackBranch"));
        ShaderPack runtime = ordinary.withRuntimeFeatures();
        assertEquals(List.of("composite.csh"), runtime.activeComputeStageFiles());
        assertTrue(runtime.readComputeStage("composite.csh").orElseThrow().contains("void main()"));
        assertTrue(runtime.readStage("final.fsh").orElseThrow().contains("runtimeBranch"));
        assertTrue(ordinary.readStage("final.fsh").orElseThrow().contains("fallbackBranch"));
    }

    @Test void optionalFlagCacheDoesNotLeakAcrossOptionSelections() throws IOException {
        ShaderPack enabled = pack("""
                #ifdef USE_SSBO
                iris.features.optional=SSBO
                #endif
                """, """
                #version 430
                #define USE_SSBO
                #ifdef USE_SSBO
                const float optionEnabled=1.0;
                #endif
                #ifdef IRIS_FEATURE_SSBO
                const float ssboBranch=1.0;
                #else
                const float fallbackBranch=0.0;
                #endif
                void main() { gl_FragColor=vec4(1.0); }
                """);
        assertTrue(enabled.readStage("final.fsh").orElseThrow().contains("ssboBranch"));
        ShaderPack disabled = enabled.configured(Map.of("USE_SSBO", "false"), 0);
        String source = disabled.readStage("final.fsh").orElseThrow();
        assertTrue(source.contains("fallbackBranch"));
        assertFalse(source.contains("ssboBranch"));
        assertTrue(enabled.readStage("final.fsh").orElseThrow().contains("ssboBranch"));
    }
}
