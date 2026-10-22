package dev.veyra.shader;

import dev.veyra.vulkan.VeyraDeviceFeatures;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import static org.junit.jupiter.api.Assertions.*;

class ShaderPackImageBindingsTest {
    private static final String PROPERTIES = "image.voxel_img = voxel_sampler red_integer r16ui unsigned_int true false 4 4 4";
    private boolean previousVertexStores;
    private boolean previousExtendedImageFormats;

    @BeforeEach
    void enableRequiredFeaturesForCapabilityTests() {
        previousVertexStores = VeyraDeviceFeatures.vertexPipelineStoresAndAtomicsEnabled;
        previousExtendedImageFormats = VeyraDeviceFeatures.shaderStorageImageExtendedFormatsEnabled;
        VeyraDeviceFeatures.vertexPipelineStoresAndAtomicsEnabled = true;
        VeyraDeviceFeatures.shaderStorageImageExtendedFormatsEnabled = true;
    }

    @AfterEach
    void restoreDeviceFeatures() {
        VeyraDeviceFeatures.vertexPipelineStoresAndAtomicsEnabled = previousVertexStores;
        VeyraDeviceFeatures.shaderStorageImageExtendedFormatsEnabled = previousExtendedImageFormats;
    }

    @Test void runtimeCustomImageFixturePassesCapabilitiesWithAnIsolatedFeatureSnapshot() throws Exception {
        var ordinary = ShaderPack.inspect(java.nio.file.Path.of("examples/custom-image-compute"));
        assertTrue(ordinary.readImageProperties().images().isEmpty());
        var runtime = ordinary.withRuntimeFeatures();
        assertFalse(ordinary.configuration().customImages());
        assertTrue(runtime.configuration().customImages());
        assertEquals(2, runtime.readImageProperties().images().size());
        assertDoesNotThrow(runtime::validatePipelineCapabilities);
        assertTrue(runtime.readStage("gbuffers_terrain.fsh").orElseThrow().contains("floodfill_sampler"));
    }

    @Test void rejectsShadowStorageImageWritesBeforePipelineCreationWhenVertexStoresAreUnavailable() throws Exception {
        var runtime = ShaderPack.inspect(java.nio.file.Path.of("examples/basic-custom-image")).withRuntimeFeatures();
        VeyraDeviceFeatures.vertexPipelineStoresAndAtomicsEnabled = false;

        IOException error = assertThrows(IOException.class, runtime::validatePipelineCapabilities);

        assertTrue(error.getMessage().contains("writable storage image 'voxel_img'"), error.getMessage());
        assertTrue(error.getMessage().contains("vertexPipelineStoresAndAtomics is unavailable"), error.getMessage());
    }

    @Test void rejectsShadowComputeBeforePipelineCreationWhenExtendedImageFormatsAreUnavailable() throws Exception {
        var runtime = ShaderPack.inspect(java.nio.file.Path.of("examples/custom-image-compute")).withRuntimeFeatures();
        VeyraDeviceFeatures.shaderStorageImageExtendedFormatsEnabled = false;

        IOException error = assertThrows(IOException.class, runtime::validatePipelineCapabilities);

        assertTrue(error.getMessage().contains("vertexPipelineStoresAndAtomics and shaderStorageImageExtendedFormats are required"),
                error.getMessage());
    }

    @Test void compositePostPassCanConsumeTheSharedComputeImages() throws Exception {
        var pack = ShaderPack.inspect(java.nio.file.Path.of("examples/composite-custom-image")).withRuntimeFeatures();
        assertDoesNotThrow(pack::validatePipelineCapabilities);
        var composite = pack.readPrograms().stream().filter(program -> program.name().equals("composite"))
                .findFirst().orElseThrow();
        var prepared = ShaderPackImageBindings.prepare("composite.fsh", composite.source(), pack.readImageProperties());
        assertEquals("floodfill_sampler", prepared.resources().getFirst().name());
        String nativeSource = ShaderPackImageBindings.decorateNativeSamplers("#version 450\nvoid main(){}", prepared);
        assertTrue(nativeSource.contains("#define sampler2D usampler3D\nuniform sampler2D floodfill_sampler;"));
    }

    @Test void sharesBindingsAcrossVertexAndFragmentWhilePreservingNativeSamplers() throws Exception {
        var properties = ShaderPackImageProperties.parse(PROPERTIES);
        var vertex = ShaderPackImageBindings.prepare("shadow.vsh", """
                #version 430
                writeonly uniform uimage3D voxel_img;
                void main(){ imageStore(voxel_img, ivec3(0), uvec4(1)); gl_Position=vec4(0); }
                """, properties);
        var fragment = ShaderPackImageBindings.prepare("gbuffers_terrain.fsh", """
                #version 430
                uniform sampler2D tex;
                uniform usampler3D voxel_sampler;
                readonly uniform uimage3D voxel_img;
                void main(){ vec4 c=texture(tex,vec2(0))+vec4(texelFetch(voxel_sampler,ivec3(0),0)+imageLoad(voxel_img,ivec3(0))); }
                """, properties);
        var plan = ShaderPackImageBindings.plan(7, vertex, fragment);
        assertEquals(2, plan.resources().size());
        assertEquals(7, plan.resources().get("voxel_img").binding());
        assertEquals(8, plan.resources().get("voxel_sampler").binding());
        assertTrue(fragment.source().contains("uniform sampler2D tex;"));
        assertFalse(fragment.source().contains("uniform usampler3D voxel_sampler;"));
        assertTrue(plan.decorate(vertex.source(), vertex).contains("binding = 7, r16ui) writeonly uniform uimage3D voxel_img;"));
        assertTrue(plan.decorate(fragment.source(), fragment).contains("binding = 7, r16ui) readonly uniform uimage3D voxel_img;"));
    }

    @Test void rejectsFloatSamplerForIntegerImageAndRemovesUnusedCustomUniforms() throws Exception {
        var properties = ShaderPackImageProperties.parse(PROPERTIES);
        IOException error = assertThrows(IOException.class, () -> ShaderPackImageBindings.prepare("terrain.fsh",
                "uniform sampler3D voxel_sampler;\nvoid main(){vec4 c=texture(voxel_sampler,vec3(0));}", properties));
        assertTrue(error.getMessage().contains("usampler3D"));
        var unused = ShaderPackImageBindings.prepare("terrain.fsh",
                "uniform usampler3D voxel_sampler;\nvoid main(){}", properties);
        assertTrue(unused.resources().isEmpty());
    }

    @Test void insertsImageDeclarationsAfterVersionExtensionsAndComments() throws Exception {
        var stage = ShaderPackImageBindings.prepare("terrain.fsh", """
                uniform usampler3D voxel_sampler;
                void main(){uvec4 c=texelFetch(voxel_sampler,ivec3(0),0);}
                """, ShaderPackImageProperties.parse(PROPERTIES));
        String adapted = "#version 450\n// keep extensions first\n#extension GL_ARB_gpu_shader_int64 : enable\nvoid main(){}\n";
        String decorated = ShaderPackImageBindings.plan(3, stage).decorate(adapted, stage);
        assertTrue(decorated.indexOf("#extension") < decorated.indexOf("layout(set"));
        assertTrue(decorated.indexOf("layout(set") < decorated.indexOf("void main"));
    }

    @Test void emitsNativeSamplerPlaceholdersAndRestoresStorageImageDeclarations() throws Exception {
        var properties = ShaderPackImageProperties.parse(PROPERTIES);
        var sampler = ShaderPackImageBindings.prepare("gbuffers_basic.fsh", """
                #version 120
                uniform usampler3D voxel_sampler;
                void main(){uvec4 c=texelFetch(voxel_sampler,ivec3(0),0);}
                """, properties);
        String nativeSource = ShaderPackImageBindings.decorateNativeSamplers(
                "#version 460\n#extension GL_EXT_shader_explicit_arithmetic_types_int64 : enable\nvoid main(){}\n", sampler);
        assertTrue(nativeSource.contains("#define sampler2D usampler3D\nuniform sampler2D voxel_sampler;\n#undef sampler2D"));
        assertTrue(nativeSource.indexOf("#extension") < nativeSource.indexOf("uniform sampler2D"));
        assertFalse(nativeSource.contains("layout(set"));

        var storage = ShaderPackImageBindings.prepare("gbuffers_basic.fsh", """
                coherent uniform uimage3D voxel_img;
                void main(){imageStore(voxel_img,ivec3(0),uvec4(1));}
                """, properties);
        String storagePlaceholder = ShaderPackImageBindings.decorateNativeSamplers(
                "#version 460\nvoid main(){}", storage);
        assertTrue(storagePlaceholder.contains("uniform sampler2D voxel_img;"));
        var restored = VeyraNativeImageBindings.restoreStorageImageDeclarations(
                "layout(binding = 4) uniform sampler2D voxel_img;\n",
                java.util.Map.of("voxel_img", storage.resources().getFirst()),
                java.util.Map.of("voxel_img", 4));
        assertEquals("layout(set = 0, binding = 4, r16ui) coherent uniform uimage3D voxel_img;\n",
                restored.source());
        assertEquals(java.util.Set.of("voxel_img"), restored.names());
    }

    @Test void keepsSharedImagesLinkedThroughEntityAndTexturedFragmentAdapters() throws Exception {
        var properties = ShaderPackImageProperties.parse(PROPERTIES);
        String packFragment = """
                #version 120
                uniform usampler3D voxel_sampler;
                void main(){
                    uint value=texelFetch(voxel_sampler,ivec3(0),0).r;
                    gl_FragColor=value==5u?vec4(0,1,0,1):vec4(1,0,0,1);
                }
                """;
        var stage = ShaderPackImageBindings.prepare("gbuffers_entities.fsh", packFragment, properties);
        assertEquals("voxel_sampler", stage.resources().getFirst().name());
        String entity = EntityShaderAdapter.adaptGeometryFragment(stage.source(), "gbuffers_entities",
                false, true, -1, -1, ItemIdMap.UNMAPPED, true, false, java.util.Map.of());
        String entityNative = ShaderPackImageBindings.decorateNativeSamplers(entity, stage);
        assertTrue(entityNative.contains("#define sampler2D usampler3D\nuniform sampler2D voxel_sampler;"));

        String textured = TextureColorDistanceShaderAdapter.adaptFragment(
                stage.source(), "gbuffers_textured", false);
        String texturedNative = ShaderPackImageBindings.decorateNativeSamplers(textured, stage);
        assertTrue(texturedNative.contains("#define sampler2D usampler3D\nuniform sampler2D voxel_sampler;"));
        assertFalse(stage.source().contains("uniform usampler3D voxel_sampler;"));
    }

    @Test void ignoresUnusedImageDeclarationsButRejectsActiveMissingResources() throws Exception {
        var properties = ShaderPackImageProperties.parse("");
        var unused = ShaderPackImageBindings.prepare("shadow.vsh",
                "writeonly uniform uimage3D unused_img;\nvoid main(){gl_Position=vec4(0);}", properties);
        assertTrue(unused.resources().isEmpty());
        IOException error = assertThrows(IOException.class, () -> ShaderPackImageBindings.prepare("shadow.vsh",
                "writeonly uniform uimage3D missing_img;\nvoid main(){imageStore(missing_img,ivec3(0),uvec4(1));gl_Position=vec4(0);}", properties));
        assertTrue(error.getMessage().contains("image.missing_img"));
    }
}
