package dev.veyra.shader;

import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IrisProjectionMatricesTest {
    @Test
    void movesCameraBobbingIntoModelViewWithoutMovingGeometry() {
        Matrix4f projection = new Matrix4f().setPerspective(1.1F, 1.7F, 0.05F, 300.0F, true)
                .scale(1.0F, -1.0F, 1.0F);
        Matrix4f camera = new Matrix4f().rotateY(0.7F).rotateX(-0.2F);
        for (float phase : new float[]{-0.04F, 0.0F, 0.06F}) {
            Matrix4f bob = new Matrix4f().translate(phase, phase * 0.5F, 0.0F).rotateZ(phase);
            Matrix4f renderedProjection = new Matrix4f(projection).mul(bob);
            Matrix4f effects = IrisProjectionMatrices.viewEffects(renderedProjection, projection);
            Matrix4f irisView = new Matrix4f(effects).mul(camera);
            assertTrue(new Matrix4f(projection).mul(irisView)
                    .equals(new Matrix4f(renderedProjection).mul(camera), 0.00001F));

            Vector4f world = new Vector4f(1.0F, 2.0F, -15.0F, 1.0F);
            Matrix4f irisProjection = IrisProjectionMatrices.fromVulkan(projection);
            Vector4f screen = irisProjection.transform(irisView.transform(new Vector4f(world)));
            screen.div(screen.w);
            Vector4f reconstructed = new Matrix4f(irisProjection).invert().transform(screen);
            reconstructed.div(reconstructed.w);
            new Matrix4f(irisView).invert().transform(reconstructed);
            assertTrue(reconstructed.equals(world, 0.001F), "phase=" + phase + " world=" + reconstructed);

            Matrix4f entityView = new Matrix4f(camera).translate(3.0F, 0.0F, -5.0F);
            Matrix4f entityProjection = new Matrix4f(renderedProjection).mul(new Matrix4f(effects).invert());
            assertTrue(entityProjection.mul(new Matrix4f(effects).mul(entityView))
                    .equals(new Matrix4f(renderedProjection).mul(entityView), 0.0001F));
        }
    }

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
