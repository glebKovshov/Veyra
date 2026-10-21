package dev.veyra.mixin;

import org.junit.jupiter.api.Test;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VulkanCloudRendererMixinTest {
    @Test
    void remapsMinecraftInvokeWithoutRemappingVulkanModMethodSelector() {
        Method hook = Arrays.stream(VulkanCloudRendererMixin.class.getDeclaredMethods())
                .filter(method -> method.getName().equals("veyra$selectCloudVertexFormat"))
                .findFirst()
                .orElseThrow();
        ModifyArg injector = hook.getAnnotation(ModifyArg.class);
        At invocation = injector.at();

        assertFalse(injector.remap());
        assertTrue(invocation.remap());
        assertTrue(invocation.target().contains("Tesselator;begin"), invocation.target());
    }
}
