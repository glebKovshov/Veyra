package dev.veyra.vulkan;

import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.TextureFormat;
import java.lang.reflect.Proxy;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class VeyraTargetTexturesTest {
    @Test void createsSampleableColorAttachmentWithOneMipAndLayer() {
        int[] calls = {0};
        GpuDevice device = (GpuDevice) Proxy.newProxyInstance(GpuDevice.class.getClassLoader(),
                new Class<?>[]{GpuDevice.class}, (proxy, method, args) -> {
                    assertEquals("createTexture", method.getName());
                    assertArrayEquals(new Object[]{"target", TextureFormat.RGBA8, 64, 32, 1}, args);
                    calls[0]++;
                    return null;
                });
        VeyraTargetTextures.createColor(device, "target", null, 64, 32);
        assertEquals(1, calls[0]);
    }
}
