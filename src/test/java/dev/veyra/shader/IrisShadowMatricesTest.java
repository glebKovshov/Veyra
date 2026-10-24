package dev.veyra.shader;

import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IrisShadowMatricesTest {
    @Test
    void matchesIrisDawnShadowViewAndCameraSnap() {
        var view = IrisShadowMatrices.createView(0.03451777F, 2.0F, 0.0F,
                new Vec3(0.646045982837677F, 82.53274536132812F, -514.0264282226562F));
        Matrix4f expected = new Matrix4f(
                0.21545040607452393F, 5.820481518981069E-8F, 0.9765146970748901F, 0.0F,
                -0.9765147466795349F, 1.2841844920785661E-8F, 0.21545039117336273F, 0.0F,
                0.0F, -0.9999999403953552F, 5.960464477539063E-8F, 0.0F,
                0.38002151250839233F, 1.0264281034469604F, -0.4463119506836F, 1.0F);

        assertTrue(expected.equals(view.modelView(), 0.0005F),
                () -> "expected=" + expected + " actual=" + view.modelView());
    }

    @Test
    void keepsShaderPackDepthInIrisSpaceAndMapsRenderDepthToVulkanRange() {
        Matrix4f iris = IrisShadowMatrices.createProjection(192.0F, -100.05F, 156.0F);
        Matrix4f vulkan = IrisShadowMatrices.createVulkanProjection(192.0F, -100.05F, 156.0F);

        assertEquals(iris.m22() * 0.5F, vulkan.m22(), 0.000001F);
        assertEquals(iris.m32() * 0.5F + 0.5F, vulkan.m32(), 0.000001F);
    }
}
