package dev.veyra.shader;

import com.mojang.blaze3d.framegraph.FramePass;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.resource.ResourceHandle;
import com.mojang.blaze3d.textures.GpuTextureView;
import net.minecraft.client.renderer.PostPass;
import net.minecraft.resources.ResourceLocation;
import java.util.Map;

public final class LegacyPostInputCheck {
    public static void check() {
        var target = ResourceLocation.withDefaultNamespace("test");
        assert VeyraPostInput.isBilinear(new PostPass.TargetInput("color", target, false, true));
        assert !VeyraPostInput.isBilinear(new PostPass.TargetInput("depth", target, true, false));
        assert VeyraPostInput.isBilinear(new Custom(true));
        assert !VeyraPostInput.isBilinear(new Custom(false));
        assert !VeyraPostInput.isBilinear(new Plain());
    }

    private static class Plain implements PostPass.Input {
        @Override public void addToPass(FramePass pass, Map<ResourceLocation, ResourceHandle<RenderTarget>> targets) { }
        @Override public GpuTextureView texture(Map<ResourceLocation, ResourceHandle<RenderTarget>> targets) { return null; }
        @Override public String samplerName() { return "test"; }
    }

    private static final class Custom extends Plain implements VeyraPostInput {
        private final boolean linear;
        Custom(boolean linear) { this.linear = linear; }
        @Override public boolean bilinear() { return linear; }
    }
}
