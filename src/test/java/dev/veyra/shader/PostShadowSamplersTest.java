package dev.veyra.shader;

import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class PostShadowSamplersTest {
    @Test void bindsDistinctDepthImagesAndDeduplicatesColorAliases() throws IOException {
        var adapted = PostShadowSamplers.adapt("""
                uniform sampler2D shadowtex0;
                uniform sampler2D shadowtex1;
                uniform sampler2D shadowcolor0;
                uniform sampler2D shadowcolor1;
                uniform sampler2D shadowcolor;
                void main(){gl_FragColor=texture2D(shadowtex0,vec2(0))+texture2D(shadowtex1,vec2(0))+texture2D(shadowcolor0,vec2(0))+texture2D(shadowcolor1,vec2(0))+texture2D(shadowcolor,vec2(0));}
                """, true);
        assertEquals(4, adapted.inputs().size());
        String bridge = PostShadowSamplers.bridge(adapted.inputs());
        assertTrue(bridge.contains("#define shadowtex0 VeyraShadowDepthSampler"));
        assertTrue(bridge.contains("#define shadowtex1 VeyraShadowOpaqueDepthSampler"));
        assertTrue(bridge.contains("vec4 veyraSample(sampler3D s, vec3 uv)"));
        assertTrue(bridge.contains("#define shadowcolor VeyraShadowColorSampler"));
        assertTrue(bridge.contains("#define shadowcolor1 VeyraShadowColor1Sampler"));
        assertTrue(PostShadowSamplers.Input.COLOR.bilinear());
        assertFalse(PostShadowSamplers.Input.OPAQUE_DEPTH.bilinear());
        assertTrue(PostShadowSamplers.bilinear("VeyraShadowColor"));
        assertFalse(PostShadowSamplers.bilinear("VeyraShadowOpaqueDepth"));
        assertThrows(UnsupportedOperationException.class, () -> adapted.inputs().clear());
    }
    @Test void ignoresUnusedDeclarationsCommentsAndUnrelatedShadowVariables() throws IOException {
        var adapted = PostShadowSamplers.adapt("""
                uniform sampler2D shadowtex1;
                uniform sampler2D noisetex;
                uniform mat4 shadowProjection;
                // shadowcolor2
                void main(){float shadow=shadowProjection[0][0];gl_FragColor=vec4(shadow);}
                """, false);
        assertTrue(adapted.inputs().isEmpty());
        assertTrue(adapted.source().contains("uniform mat4 shadowProjection"));
        assertFalse(adapted.source().contains("noisetex"));
    }
    @Test void comparisonUniformsAndHelperParametersKeepFloatResults() throws IOException {
        var adapted = PostShadowSamplers.adapt("""
                uniform sampler2DShadow shadowtex1;
                float compare(sampler2DShadow depthMap){return texture(depthMap,vec3(0.5));}
                void main(){float a=texture(shadowtex1,vec3(0.5));gl_FragColor=vec4(a+compare(shadowtex1));}
                """, true);
        assertTrue(adapted.source().contains("float a=veyraPostShadowCompare(shadowtex1,"));
        assertTrue(adapted.source().contains("return veyraPostShadowCompare(depthMap,"));
        assertFalse(adapted.source().contains("sampler2DShadow"));
        String bridge = PostShadowSamplers.bridge(adapted.inputs());
        assertTrue(bridge.contains("step(uv.z, texture(s,uv.xy).r)"));
        assertTrue(bridge.contains("step(uv.z,texture(s,uv.xy,bias).r)"));
        assertTrue(bridge.contains("step(uv.z,textureLod(s,uv.xy,bias).r)"));
    }
    @Test void rejectsUnavailableUnknownDuplicateAndInvalidInputs() {
        String source="uniform sampler2D shadowtex1;\nvoid main(){gl_FragColor=texture2D(shadowtex1,vec2(0));}\n";
        assertThrows(IOException.class, () -> PostShadowSamplers.adapt(source, false));
        assertThrows(IOException.class, () -> PostShadowSamplers.adapt(source.replace("shadowtex1","shadowtex2"), true));
        assertThrows(IOException.class, () -> PostShadowSamplers.adapt(source.replace("shadowtex1","shadow"), true));
        assertThrows(IOException.class, () -> PostShadowSamplers.adapt(source.replace("sampler2D","samplerCube"), true));
        assertThrows(IOException.class, () -> PostShadowSamplers.adapt("uniform sampler2D shadowtex1;\n"+source, true));
        assertThrows(IOException.class, () -> PostShadowSamplers.adapt(source.replace("sampler2D","sampler2DShadow").replace("shadowtex1","shadowcolor0"), true));
    }
    @Test void allowsUnavailablePassInputsWhenTheRendererProvidesANeutralFallback() throws IOException {
        String source="uniform sampler2D shadowtex1;\nvoid main(){gl_FragColor=texture2D(shadowtex1,vec2(0));}\n";
        var adapted = PostShadowSamplers.adapt(source, false, true);
        assertEquals(Set.of(PostShadowSamplers.Input.OPAQUE_DEPTH), adapted.inputs());
        assertTrue(adapted.source().contains("veyraSample(shadowtex1"));
    }
    @Test void enforcesCombinedNativeDescriptorBudgetAfterAliasDeduplication() throws IOException {
        var inputs = Set.of(PostShadowSamplers.Input.DEPTH,PostShadowSamplers.Input.OPAQUE_DEPTH,PostShadowSamplers.Input.COLOR);
        PostShadowSamplers.validateBudget(inputs,9,"composite_colortex7",12);
        IOException failure = assertThrows(IOException.class,
                () -> PostShadowSamplers.validateBudget(inputs,10,"composite_colortex7",12));
        assertTrue(failure.getMessage().contains("composite_colortex7 needs 13 texture descriptors"));
    }

    @Test void handlesExpandedBlankRegionsBeforeSamplerDeclarations() {
        assertTimeoutPreemptively(java.time.Duration.ofSeconds(2), () -> {
            var adapted = PostShadowSamplers.adapt("\n".repeat(60000)
                    + "uniform sampler2D shadowtex1;\nvoid main(){gl_FragColor=texture2D(shadowtex1,vec2(0));}\n", true);
            assertEquals(Set.of(PostShadowSamplers.Input.OPAQUE_DEPTH), adapted.inputs());
        });
    }
}
