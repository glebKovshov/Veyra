package dev.veyra.shader;

import org.joml.Matrix4f;

/** Iris-compatible shadow uniforms and the zero-to-one projection used for Vulkan drawing. */
final class IrisShadowMatrices {
    private IrisShadowMatrices() { }

    static Matrix4f createProjection(float halfPlaneLength, float nearPlane, float farPlane) {
        return new Matrix4f().setOrthoSymmetric(halfPlaneLength * 2.0F, halfPlaneLength * 2.0F,
                nearPlane, farPlane);
    }

    static Matrix4f createVulkanProjection(float halfPlaneLength, float nearPlane, float farPlane) {
        return new Matrix4f().setOrthoSymmetric(halfPlaneLength * 2.0F, halfPlaneLength * 2.0F,
                nearPlane, farPlane, true);
    }
}
