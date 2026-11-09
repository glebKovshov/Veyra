package dev.veyra.shader;

import com.mojang.blaze3d.framegraph.FrameGraphBuilder;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.resource.ResourceHandle;
import java.util.Map;
import net.minecraft.client.renderer.PostPass;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;

final class VeyraPostProjection {
    private VeyraPostProjection() { }

    static Matrix4f frameInfo(int width, int height) {
        if (width <= 0 || height <= 0) throw new IllegalArgumentException("Invalid post-processing dimensions");
        // Match PostChain.addToFrame; each queued frame owns its projection snapshot.
        return new Matrix4f().setOrtho(0, width, 0, height, 0.1F, 1000.0F);
    }

    static void addToFrame(PostPass pass, FrameGraphBuilder graph,
                           Map<ResourceLocation, ResourceHandle<RenderTarget>> targets, Matrix4f frameInfo) {
        pass.addToFrame(graph, targets, frameInfo, renderPass -> { });
    }

    static void reset() { }
}
