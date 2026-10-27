package dev.veyra.shader;

import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IrisProjectionMatricesTest {
    @Test
    void convertsVulkanClipCoordinatesAndInvertsBackToViewSpace() {
        Matrix4f vulkan = new Matrix4f().setPerspective(1.0F, 1.7F, 0.05F, 300.0F, true)
                .scale(1.0F, -1.0F, 1.0F);
        Matrix4f iris = IrisProjectionMatrices.fromVulkan(vulkan);
        Vector4f position = new Vector4f(1.0F, 2.0F, -15.0F, 1.0F);
        Vector4f nativeClip = vulkan.transform(new Vector4f(position));
        Vector4f packClip = iris.transform(new Vector4f(position));
        assertEquals(nativeClip.x, packClip.x, 0.00001F);
        assertEquals(-nativeClip.y, packClip.y, 0.00001F);
        assertEquals(nativeClip.z * 2.0F - nativeClip.w, packClip.z, 0.00001F);
        assertEquals(nativeClip.w, packClip.w, 0.00001F);
        assertTrue(new Matrix4f(iris).invert().transform(packClip).equals(position, 0.0001F));
    }

    @Test
    void keepsInfiniteProjectionSkyRaysForwardAndNearbyDepthAccurate() {
        Matrix4f vulkan = new Matrix4f().setPerspective(1.0F, 1.7F, 0.05F, 300.0F, true)
                .m22(-0.999999F).m32(-0.04999995F);
        Matrix4f inverse = IrisProjectionMatrices.fromVulkan(vulkan).invert();
        Vector4f sky = inverse.transform(new Vector4f(0.5F, 0.5F, 1.0F, 1.0F));
        assertTrue(sky.w > 0.0F);
        Vector4f position = new Vector4f(1.0F, 2.0F, -32.0F, 1.0F);
        Vector4f nativeClip = vulkan.transform(new Vector4f(position));
        Vector4f glClip = new Vector4f(nativeClip.x, -nativeClip.y,
                nativeClip.z * 2.0F - nativeClip.w, nativeClip.w);
        Vector4f reconstructed = inverse.transform(glClip);
        reconstructed.div(reconstructed.w);
        assertTrue(reconstructed.equals(position, 0.05F), "reconstructed=" + reconstructed);

        Vector4f distant = new Vector4f(2.0F, -3.0F, -128.0F, 1.0F);
        nativeClip = vulkan.transform(new Vector4f(distant));
        glClip.set(nativeClip.x, -nativeClip.y, nativeClip.z * 2.0F - nativeClip.w, nativeClip.w);
        reconstructed = inverse.transform(glClip);
        reconstructed.div(reconstructed.w);
        assertEquals(distant.z, reconstructed.z, 1.0F);
    }

    @Test
    void keepsSkyRayForwardAcrossFieldOfViewAndAspectRatios() {
        for (float fov : new float[]{0.6F, 1.0F, 1.7F}) {
            for (float aspect : new float[]{0.75F, 1.0F, 1.777778F, 3.0F}) {
                Matrix4f projection = new Matrix4f().setPerspective(fov, aspect, 0.05F, 300.0F, true)
                        .m22(-0.999999F).m32(-0.04999995F);
                Vector4f sky = IrisProjectionMatrices.fromVulkan(projection).invert()
                        .transform(new Vector4f(0.5F, 0.5F, 1.0F, 1.0F));
                assertTrue(sky.w > 0.0F, "fov=" + fov + " aspect=" + aspect + " w=" + sky.w);
            }
        }
    }

    @Test
    void leavesOtherDepthConventionsUntouched() {
        Matrix4f reversed = new Matrix4f().setPerspective(1.0F, 1.7F, 0.05F, 300.0F, true)
                .m22(0.1F);
        Matrix4f expected = new Matrix4f().m11(-1.0F).m22(2.0F).m32(-1.0F).mul(reversed);
        assertTrue(expected.equals(IrisProjectionMatrices.fromVulkan(reversed), 0.0F));
    }
}
