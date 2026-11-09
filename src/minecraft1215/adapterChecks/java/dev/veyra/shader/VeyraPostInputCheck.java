package dev.veyra.shader;

import com.mojang.blaze3d.framegraph.FramePass;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.resource.ResourceHandle;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.TextureFormat;
import com.mojang.blaze3d.textures.FilterMode;
import dev.veyra.vulkan.LegacyTextureView;
import net.minecraft.client.renderer.PostPass;
import net.minecraft.resources.ResourceLocation;
import java.lang.reflect.Proxy;
import java.util.Map;

public final class VeyraPostInputCheck {
    public static void run() {
        Texture texture = new Texture();
        LegacyTextureView view = new LegacyTextureView(texture, 0, 4);
        VeyraPostInput input = new VeyraPostInput() {
            public String samplerName() { return "Color"; }
            public LegacyTextureView texture(Map<ResourceLocation, ResourceHandle<RenderTarget>> targets) { return view; }
            public void addToPass(FramePass pass, Map<ResourceLocation, ResourceHandle<RenderTarget>> targets) { }
            public boolean bilinear() { return true; }
            public boolean mipmaps() { return true; }
        };
        int[] calls = {0};
        RenderPass pass = (RenderPass) Proxy.newProxyInstance(RenderPass.class.getClassLoader(),
                new Class<?>[]{RenderPass.class}, (proxy, method, values) -> {
                    if (calls[0]++ == 0) {
                        assert method.getName().equals("bindSampler");
                        assert values[0].equals("ColorSampler") && values[1] == texture;
                        assert texture.linearMipmaps();
                    } else {
                        assert method.getName().equals("setUniform") && values[0].equals("ColorSize");
                        assert java.util.Arrays.equals((float[]) values[1], new float[]{32, 16});
                    }
                    return null;
                });
        assert VeyraPostInput.samplerName(input).equals("Color");
        assert VeyraPostInput.texture(input, Map.of()) == view;
        assert VeyraPostInput.isBilinear(input);
        input.bindTo(pass, Map.of());
        assert calls[0] == 2 && !texture.isClosed();
        view.close();
        try { input.bindTo(pass, Map.of()); throw new AssertionError("Closed post texture was bound"); }
        catch (IllegalStateException expected) { }
        assert calls[0] == 2 && !texture.isClosed();
        var nativeInput = new PostPass.TargetInput("Depth", ResourceLocation.withDefaultNamespace("missing"), true, false);
        assert VeyraPostInput.samplerName(nativeInput).equals("Depth") && !VeyraPostInput.isBilinear(nativeInput);
        try { VeyraPostInput.texture(nativeInput, Map.of()); throw new AssertionError("Missing target was accepted"); }
        catch (IllegalStateException expected) { }
        System.out.println("Legacy post-input native binding checks passed");
    }
    private static final class Texture extends GpuTexture {
        private boolean closed;
        Texture() { super("post fixture", TextureFormat.RGBA8, 32, 16, 4); }
        boolean linearMipmaps() { return minFilter == FilterMode.LINEAR && magFilter == FilterMode.LINEAR && useMipmaps; }
        public boolean isClosed() { return closed; }
        public void close() { closed = true; }
    }
}
