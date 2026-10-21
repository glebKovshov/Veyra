package dev.veyra.shader;

import org.junit.jupiter.api.Test;
import java.io.IOException;
import static org.junit.jupiter.api.Assertions.*;

class ShaderPackImageBindingsTest {
    private static final String PROPERTIES = "image.voxel_img = voxel_sampler red_integer r16ui unsigned_int true false 4 4 4";

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
