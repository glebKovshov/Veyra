package dev.veyra.shader;

import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Iris-compatible shadow uniforms and the zero-to-one projection used for Vulkan drawing. */
final class IrisShadowMatrices {
    record View(Matrix4f modelView, float yaw, float pitch) { }

    private IrisShadowMatrices() { }

    static View createView(float shadowAngle, float intervalSize, float sunPathRotation, Vec3 cameraPosition) {
        float skyAngle = shadowAngle < 0.25F ? shadowAngle + 0.75F : shadowAngle - 0.25F;
        Matrix4f modelView = new Matrix4f()
                .rotateX((float) Math.toRadians(90.0F))
                .rotateZ((float) Math.toRadians(skyAngle * -360.0F))
                .rotateX((float) Math.toRadians(sunPathRotation));
        if (intervalSize != 0.0F) {
            float halfInterval = intervalSize / 2.0F;
            modelView.translate(
                    (float) cameraPosition.x % intervalSize - halfInterval,
                    (float) cameraPosition.y % intervalSize - halfInterval,
                    (float) cameraPosition.z % intervalSize - halfInterval);
        }
        Vector3f viewDirection = new Matrix4f(modelView).invert()
                .transformDirection(0.0F, 0.0F, -1.0F, new Vector3f()).normalize();
        float yaw = (float) Math.toDegrees(Math.atan2(-viewDirection.x, viewDirection.z));
        float pitch = (float) Math.toDegrees(-Math.asin(viewDirection.y));
        return new View(modelView, yaw, pitch);
    }

    static Matrix4f createProjection(float halfPlaneLength, float nearPlane, float farPlane) {
        return new Matrix4f().setOrthoSymmetric(halfPlaneLength * 2.0F, halfPlaneLength * 2.0F,
                nearPlane, farPlane);
    }

    static Matrix4f createVulkanProjection(float halfPlaneLength, float nearPlane, float farPlane) {
        return new Matrix4f().setOrthoSymmetric(halfPlaneLength * 2.0F, halfPlaneLength * 2.0F,
                nearPlane, farPlane, true).scale(1.0F, -1.0F, 1.0F);
    }
}
