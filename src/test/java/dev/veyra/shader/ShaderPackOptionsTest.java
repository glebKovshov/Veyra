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
    @Test void unsupportedStagesFailExplicitlyUnlessTheirProgramIsDisabled() throws IOException {
        Path shader = Files.createDirectories(root.resolve("pack/shaders"));
        Files.writeString(shader.resolve("final.fsh"), "void main(){gl_FragColor=vec4(1.0);}\n");
        Files.writeString(shader.resolve("composite_a.csh"), "void main(){}\n");
        IOException error = assertThrows(IOException.class, () -> ShaderPack.inspect(shader.getParent()).validatePipelineCapabilities());
        assertTrue(error.getMessage().contains("composite_a.csh"));
        Files.writeString(shader.resolve("shaders.properties"), "program.composite.enabled=false\n");
        assertDoesNotThrow(() -> ShaderPack.inspect(shader.getParent()).validatePipelineCapabilities());
    }

    @Test void activeComputeStagesRespectProgramEnableProperties() throws IOException {
        Path shader = Files.createDirectories(root.resolve("compute/shaders"));
        Files.writeString(shader.resolve("shadowcomp.csh"), "void main() {}\n");
        Files.writeString(shader.resolve("composite.fsh"), "void main() {}\n");
        Files.writeString(shader.resolve("shaders.properties"), "program.shadowcomp.enabled=false\n");

        ShaderPack pack = ShaderPack.inspect(shader.getParent());
        assertTrue(pack.activeComputeStageFiles().isEmpty());

        Files.writeString(shader.resolve("shaders.properties"), "");
        assertEquals(java.util.List.of("shadowcomp.csh"), pack.configured(Map.of(), 0).activeComputeStageFiles());
    }

    @Test void shadowComputeStagesFollowIrisPassAndComponentOrderAndCanBeScoped() throws IOException {
        Path shader = Files.createDirectories(root.resolve("ordered-compute/shaders"));
        for (String file : java.util.List.of("shadowcomp10.csh", "shadowcomp2.csh", "shadowcomp1_a.csh",
                "shadowcomp_z.csh", "composite.csh", "shadowcomp1.csh", "shadowcomp_a.csh", "shadowcomp.csh",
                "final.fsh")) {
            Files.writeString(shader.resolve(file), "void main() {}\n");
        }

        ShaderPack pack = ShaderPack.inspect(shader.getParent());
        assertEquals(java.util.List.of("shadowcomp.csh", "shadowcomp_a.csh", "shadowcomp_z.csh",
                "shadowcomp1.csh", "shadowcomp1_a.csh", "shadowcomp2.csh", "shadowcomp10.csh"),
                pack.activeComputeStageFiles("shadowcomp"));
        assertFalse(pack.activeComputeStageFiles("shadowcomp").contains("composite.csh"));
        assertTrue(pack.activeComputeStageFiles().contains("composite.csh"));
    }

    @Test void terrainVertexStagesExecuteWhenTheirInterfacesAreSupported() throws IOException {
        Path shader = Files.createDirectories(root.resolve("geometry/shaders"));
        Files.writeString(shader.resolve("gbuffers_terrain.fsh"), "void main(){}\n");
        Files.writeString(shader.resolve("gbuffers_terrain.vsh"), "#version 120\nvoid main(){ gl_Position = ftransform(); }\n");
        assertDoesNotThrow(() -> ShaderPack.inspect(shader.getParent()).validatePipelineCapabilities());

        Files.writeString(shader.resolve("gbuffers_terrain.vsh"),
                "#version 120\nattribute vec3 mc_midBlock;\nvoid main(){}\n");
        IOException error = assertThrows(IOException.class,
                () -> ShaderPack.inspect(shader.getParent()).validatePipelineCapabilities());
        assertTrue(error.getMessage().contains("mc_midBlock"));

        Files.writeString(shader.resolve("shaders.properties"), "program.gbuffers_terrain.enabled=false\n");
        assertDoesNotThrow(() -> ShaderPack.inspect(shader.getParent()).validatePipelineCapabilities());

        Path cloud = Files.createDirectories(root.resolve("cloud/shaders"));
        Files.writeString(cloud.resolve("gbuffers_clouds.fsh"), "void main(){}\n");
        Files.writeString(cloud.resolve("gbuffers_clouds.vsh"), "void main(){}\n");
        assertDoesNotThrow(() -> ShaderPack.inspect(cloud.getParent()).validatePipelineCapabilities());
    }

    @Test void shadowVertexUsesTheSameFallbackProgramAsItsFragment() throws IOException {
        Path shader = Files.createDirectories(root.resolve("shadow-pair/shaders"));
        Files.writeString(shader.resolve("shadow.fsh"), "void main(){}\n");
        Files.writeString(shader.resolve("shadow.vsh"), "// base\nvoid main(){}\n");
        Files.writeString(shader.resolve("shadow_solid.vsh"), "// variant\nvoid main(){}\n");
        assertTrue(ShaderPack.inspect(shader.getParent()).readShadowVertex("shadow_solid").orElseThrow().contains("// base"));
        Files.writeString(shader.resolve("shadow_solid.fsh"), "void main(){}\n");
        assertTrue(ShaderPack.inspect(shader.getParent()).readShadowVertex("shadow_solid").orElseThrow().contains("// variant"));
    }

    @Test void shadowVertexUnsupportedInputsFailBeforeGpuAllocation() throws IOException {
        Path shader = Files.createDirectories(root.resolve("shadow-unsupported/shaders"));
        Files.writeString(shader.resolve("shadow.fsh"), "void main(){gl_FragColor=vec4(1.0);}\n");
        Files.writeString(shader.resolve("shadow.vsh"), "attribute vec3 missingShadowInput;\nvoid main(){gl_Position=vec4(missingShadowInput,1.0);}\n");
        IOException error = assertThrows(IOException.class, ShaderPack.inspect(shader.getParent())::validatePipelineCapabilities);
        assertTrue(error.getMessage().contains("missingShadowInput"));
    }
    @Test void glintVertexStagesExecuteWhenTheirFixedVaryingLinksAreSupported() throws IOException {
        Path shader = Files.createDirectories(root.resolve("glint/shaders"));
        Files.writeString(shader.resolve("gbuffers_armor_glint.vsh"), """
                #version 130
                flat out vec4 glColor;
                out vec2 texCoord;
                void main() {
                    gl_Position = ftransform();
                    glColor = gl_Color;
                    texCoord = gl_MultiTexCoord0.xy;
                }
                """);
        Files.writeString(shader.resolve("gbuffers_armor_glint.fsh"), """
                #version 130
                flat in vec4 glColor;
                in vec2 texCoord;
                void main() { gl_FragColor = glColor; }
                """);
        assertDoesNotThrow(() -> ShaderPack.inspect(shader.getParent()).validatePipelineCapabilities());

        Path unsupported = Files.createDirectories(root.resolve("unsupported-geometry/shaders"));
        Files.writeString(unsupported.resolve("gbuffers_entities.vsh"),
                "#version 120\nattribute vec4 at_tangent;\nvoid main(){gl_Position=vec4(at_tangent);}\n");
        Files.writeString(unsupported.resolve("gbuffers_entities.fsh"), "void main(){gl_FragColor=vec4(1.0);}\n");
        IOException error = assertThrows(IOException.class,
                () -> ShaderPack.inspect(unsupported.getParent()).validatePipelineCapabilities());
        assertTrue(error.getMessage().contains("gbuffers_entities.vsh"));
    }

    @Test void skyBasicVertexStagesLinkAcrossNativeSkyPipelineFormats() throws IOException {
        Path shader = Files.createDirectories(root.resolve("sky-basic/shaders"));
        Files.writeString(shader.resolve("gbuffers_skybasic.vsh"), """
                #version 120
                varying vec4 glColor;
                void main() {
                    gl_Position = ftransform();
                    glColor = gl_Color;
                }
                """);
        Path fragment = shader.resolve("gbuffers_skybasic.fsh");
        Files.writeString(fragment, """
                #version 120
                varying vec4 glColor;
                void main() { gl_FragColor = glColor; }
                """);
        assertDoesNotThrow(() -> ShaderPack.inspect(shader.getParent()).validatePipelineCapabilities());

        Files.writeString(fragment, """
                #version 120
                varying vec2 skyCoordinate;
                void main() { gl_FragColor = vec4(skyCoordinate, 0.0, 1.0); }
                """);
        IOException error = assertThrows(IOException.class,
                () -> ShaderPack.inspect(shader.getParent()).validatePipelineCapabilities());
        assertTrue(error.getMessage().contains("skyCoordinate"));
    }

    @Test void skyBasicVertexCanUseTheBasicFragmentFallback() throws IOException {
        Path shader = Files.createDirectories(root.resolve("sky-basic-fallback/shaders"));
        Files.writeString(shader.resolve("gbuffers_skybasic.vsh"), """
                #version 120
                varying vec4 color;
                void main() {
                    gl_Position = ftransform();
                    color = gl_Color;
                }
                """);
        Files.writeString(shader.resolve("gbuffers_basic.fsh"), """
                #version 120
                varying vec4 color;
                void main() { gl_FragColor = color; }
                """);
        assertDoesNotThrow(() -> ShaderPack.inspect(shader.getParent()).validatePipelineCapabilities());
    }

    @Test void weatherVertexStagesLinkToWeatherAndTexturedFallbackFragments() throws IOException {
        Path shader = Files.createDirectories(root.resolve("weather-fallback/shaders"));
        Files.writeString(shader.resolve("gbuffers_weather.vsh"), """
                #version 130
                varying vec2 texCoord;
                void main() {
                    gl_Position = ftransform();
                    texCoord = gl_MultiTexCoord0.xy;
                }
                """);
        Files.writeString(shader.resolve("gbuffers_textured_lit.fsh"), """
                #version 130
                varying vec2 texCoord;
                void main() { gl_FragColor = vec4(texCoord, 0.0, 1.0); }
                """);
        assertDoesNotThrow(() -> ShaderPack.inspect(shader.getParent()).validatePipelineCapabilities());

        Files.writeString(shader.resolve("gbuffers_weather.fsh"), """
                #version 130
                varying vec2 missingWeatherVarying;
                void main() { gl_FragColor = vec4(missingWeatherVarying, 0.0, 1.0); }
                """);
        IOException error = assertThrows(IOException.class,
                () -> ShaderPack.inspect(shader.getParent()).validatePipelineCapabilities());
        assertTrue(error.getMessage().contains("missingWeatherVarying"));
    }

    @Test void glintFragmentMrtIsValidatedEvenWithoutACustomVertexStage() throws IOException {
        Path shader = Files.createDirectories(root.resolve("glint-mrt/shaders"));
        Path fragment = shader.resolve("gbuffers_armor_glint.fsh");
        Files.writeString(fragment, """
                #version 120
                /* RENDERTARGETS: 0,4 */
                void main() {
                    gl_FragData[0] = vec4(0.0, 1.0, 0.0, 1.0);
                    gl_FragData[1] = vec4(1.0, 0.0, 1.0, 1.0);
                }
                """);
        assertDoesNotThrow(() -> ShaderPack.inspect(shader.getParent()).validatePipelineCapabilities());

        Files.writeString(fragment, """
                #version 120
                /* RENDERTARGETS: 0,16 */
                void main() { gl_FragData[0] = vec4(1.0); }
                """);
        IOException error = assertThrows(IOException.class,
                () -> ShaderPack.inspect(shader.getParent()).validatePipelineCapabilities());
        assertTrue(error.getMessage().contains("at most 8 distinct colortex0..15"));
    }
    @Test void requiredIrisFeaturesAreNotSilentlyIgnored() throws IOException {
        Path shader = Files.createDirectories(root.resolve("pack/shaders"));
        Files.writeString(shader.resolve("final.fsh"), "void main(){gl_FragColor=vec4(1.0);}\n");
        Files.writeString(shader.resolve("shaders.properties"), "iris.features.required=SSBO CUSTOM_IMAGES\n");
        IOException error = assertThrows(IOException.class, () -> ShaderPack.inspect(shader.getParent()).validatePipelineCapabilities());
        assertTrue(error.getMessage().contains("SSBO CUSTOM_IMAGES"));
    }
    @Test void brokenSelectedPackDoesNotHideOtherDiscoveredPacks() throws IOException {
        Path shader = Files.createDirectories(root.resolve("shaderpacks/broken/shaders"));
        Files.writeString(shader.resolve("final.fsh"), "void main(){gl_FragColor=vec4(1.0);}\n");
        Files.writeString(shader.resolve("shaders.properties"), "screen=\\uBROKEN\n");
        Path valid = Files.createDirectories(root.resolve("shaderpacks/valid/shaders"));
        Files.writeString(valid.resolve("final.fsh"), "void main(){gl_FragColor=vec4(1.0);}\n");
        Files.createDirectories(root.resolve("veyra"));
        Files.writeString(root.resolve("veyra/selected-pack.txt"), "broken");
        ShaderPackRepository repository = new ShaderPackRepository(root);
        repository.scan();
        assertEquals(2, repository.getPacks().size());
        assertTrue(repository.getSelected().isEmpty());
        repository.select(repository.getPacks().stream().filter(pack -> pack.name().equals("valid")).findFirst().orElseThrow());
        assertEquals("valid", repository.getSelected().orElseThrow().name());
    }
    @Test void programConditionsAndProfileTogglesUseTheResolvedDimensionPath() throws IOException {
        Path shader = Files.createDirectories(root.resolve("pack/shaders/world0"));
        Files.writeString(shader.resolve("final.fsh"), "void main(){gl_FragColor=vec4(0.0);}\n");
        Files.writeString(shader.getParent().resolve("final.fsh"), "void main(){gl_FragColor=vec4(1.0);}\n");
        Files.writeString(shader.getParent().resolve("shaders.properties"),
                "program.final.enabled=false\nprogram.world0/final.enabled=true\nprofile.DISABLED=!program.world0/final\n");
        ShaderPack pack = ShaderPack.inspect(shader.getParent().getParent());
        assertEquals(1, pack.readPrograms().size());
        assertTrue(pack.configured(Map.of(), -1).readPrograms().isEmpty());
        Map<String, String> profile = pack.readOptions().profiles().get("DISABLED");
        assertEquals("false", profile.get("program.world0/final"));
        assertTrue(pack.configured(profile, 0).readPrograms().isEmpty());
    }
}
