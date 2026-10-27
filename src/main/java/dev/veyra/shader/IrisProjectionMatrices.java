package dev.veyra.shader;

import org.joml.Matrix4f;
import org.joml.Matrix4fc;

final class IrisProjectionMatrices {
    private IrisProjectionMatrices() { }

    static Matrix4f fromVulkan(Matrix4fc projection) {
        Matrix4f finite = new Matrix4f(projection);
        // VulkanMod's infinite projection places depth 1 just beyond infinity, reversing sky rays.
        if (finite.m23() == -1.0F && finite.m33() == 0.0F && finite.m32() < 0.0F
                && finite.m22() >= -1.0F && finite.m22() < -0.9999F) {
            finite.m22(Math.nextDown(-1.0F));
        }
        return new Matrix4f().m11(-1.0F).m22(2.0F).m32(-1.0F).mul(finite);
    }
}
