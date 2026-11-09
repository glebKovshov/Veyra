package dev.veyra.vulkan;

import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.textures.*;
import dev.veyra.mixin.accessor.LegacyGpuTextureSamplerAccessor;
import java.lang.reflect.Proxy;
import java.util.OptionalDouble;

/** CPU-only contract check: sampling state reaches the texture before it is bound. */
public final class LegacySamplerCheck {
    public static void main(String[] args) {
        dev.veyra.shader.LegacyPostInputCheck.check();
        assert LegacyAcquisition.succeeded(0);
        assert LegacyAcquisition.succeeded(1000001003);
        assert !LegacyAcquisition.succeeded(-1000001004);
        assert !LegacyAcquisition.succeeded(-1000000000);
        assert !LegacyAcquisition.succeeded(2);
        Texture texture = new Texture();
        View view = new View(texture);
        var expected = LegacySampler.getSampler(AddressMode.REPEAT, AddressMode.CLAMP_TO_EDGE,
                FilterMode.LINEAR, FilterMode.NEAREST, true);
        int[] calls = {0};
        RenderPass pass = (RenderPass) Proxy.newProxyInstance(RenderPass.class.getClassLoader(),
                new Class<?>[]{RenderPass.class}, (proxy, method, values) -> {
                    if (!method.getName().equals("bindSampler")) throw new AssertionError(method);
                    assert values[0].equals("test");
                    if (calls[0]++ == 0) {
                        assert values[1] == view;
                        assert LegacySampler.from(texture).equals(expected);
                    } else assert values[1] == null;
                    return null;
                });
        LegacySampler.bind(pass, "test", view, expected);
        LegacySampler.bind(pass, "test", null, null);
        assert calls[0] == 2;
        LegacySampler.getClampToEdge(FilterMode.NEAREST).apply(texture);
        assert texture.veyra$getAddressModeU() == AddressMode.CLAMP_TO_EDGE;
        assert !texture.veyra$getUseMipmaps();
        assert texture.veyra$getMinFilter() == FilterMode.NEAREST;
        expected.close();
        assert !texture.isClosed();
        assert !LegacySampler.create(AddressMode.REPEAT, AddressMode.REPEAT, FilterMode.LINEAR,
                FilterMode.LINEAR, 1, OptionalDouble.empty()).mipmaps();
        try {
            LegacySampler.create(AddressMode.REPEAT, AddressMode.REPEAT, FilterMode.LINEAR,
                    FilterMode.LINEAR, 1, OptionalDouble.of(2));
            throw new AssertionError("Unsupported LOD was accepted");
        } catch (IllegalArgumentException expectedFailure) { }
        System.out.println("Legacy texture sampling checks passed");
    }

    private static final class Texture extends GpuTexture implements LegacyGpuTextureSamplerAccessor {
        private boolean closed;
        Texture() { super(USAGE_TEXTURE_BINDING, "test", TextureFormat.RGBA8, 4, 4, 1, 3); }
        @Override public void close() { closed = true; }
        @Override public boolean isClosed() { return closed; }
        @Override public AddressMode veyra$getAddressModeU() { return addressModeU; }
        @Override public AddressMode veyra$getAddressModeV() { return addressModeV; }
        @Override public FilterMode veyra$getMinFilter() { return minFilter; }
        @Override public FilterMode veyra$getMagFilter() { return magFilter; }
        @Override public boolean veyra$getUseMipmaps() { return useMipmaps; }
    }

    private static final class View extends GpuTextureView {
        private boolean closed;
        View(GpuTexture texture) { super(texture, 0, 3); }
        @Override public void close() { closed = true; }
        @Override public boolean isClosed() { return closed; }
    }
}
