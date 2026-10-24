package dev.veyra.shader;

import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class IrisShadowMatricesTest {
    @Test
    void keepsShaderPackDepthInIrisSpaceAndMapsRenderDepthToVulkanRange() {
        Matrix4f iris = IrisShadowMatrices.createProjection(192.0F, -100.05F, 156.0F);
        Matrix4f vulkan = IrisShadowMatrices.createVulkanProjection(192.0F, -100.05F, 156.0F);

        assertEquals(iris.m22() * 0.5F, vulkan.m22(), 0.000001F);
        assertEquals(iris.m32() * 0.5F + 0.5F, vulkan.m32(), 0.000001F);
    }
}
